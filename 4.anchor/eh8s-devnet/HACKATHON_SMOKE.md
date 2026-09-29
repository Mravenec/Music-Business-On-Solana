# Hackathon smoke — three Solana DevNet wallets

English walkthrough. **No mocks.** Every paid step is a wallet signature on DevNet.

```powershell
# automated checks (stack + DevNet accounts); exit code 0 = pass
powershell -File 4.anchor/eh8s-devnet/hackathon-smoke.ps1 -Preflight
# preflight + the guided wallet steps below (waits for Enter after each)
powershell -File 4.anchor/eh8s-devnet/hackathon-smoke.ps1
```

## Money loop

| Step | Who signs | On-chain result |
|---|---|---|
| Academy subscribe | Musician B | **85% → treasury PDA ATA** + **15% → instructor ATA** |
| Activate band vault | Owner A | `create_band` → BandVault PDA `["band", id]`, equal member weights |
| Sync SPP weights (optional) | Owner A | `update_spp_weights` → weights from the latest closed SPP cycle |
| **Settle concert** | Venue C | net = gross − expenses; **fee → treasury PDA ATA**; **pool → vault**; each member `pending_claims_usdc` += pool × weight |
| **Claim royalties** | Each member | Vault → member ATA |
| **Withdraw treasury** | Owner A | `withdraw_treasury` → treasury PDA ATA → owner ATA |
| AI owner digest | — (backend) | Claude summary of the loop, stored in MariaDB; needs `ANTHROPIC_API_KEY` |
| Venue listing | Venue C, then Owner A (or STAGE agent) | `register_venue` → `VenueListing` PDA; `approve_venue` |
| **Booking escrow** | Venue C | `propose_booking` → gross USDC into the `VenueAccessToken` escrow; `cancel_booking` refunds before confirm |
| Contract hash | Owner A (or STAGE agent) | `confirm_booking` pins the SHA-256 of the contract text |
| **Settle from escrow** | Owner A (or VAULT agent) | `settle_booking`: expenses → venue, **fee → treasury PDA ATA**, **pool → vault**, member pending by weight |
| Subscriptions with expiry | Musician B | `subscribe_academy(plan, months)` / `subscribe_geographic(geo_code, tier, months)`; PDA stores `expires_at`, renewals extend it |
| **Per-song pool** | Owner A (or WAVE agent) | `create_royalty_pool` splits; `deposit_royalties` credits each member by the song split |
| **Sync license 80/20** | Any licensee wallet | `pay_sync_license`: **20% → treasury PDA ATA**, 80% → vault by the song splits, once per deal |
| Musician level (46) | Owner A (or NEXUS agent) | `update_musician_level` after a NEXUS Score Enigma |
| Owner multisig (optional, last) | Governance signers | `init_governance` → `propose` → `approve_proposal` → `execute_withdraw_proposal`; direct withdraw is refused afterwards |

The backend confirms each signature against the program id, instruction accounts, and amounts before it records the row. Agent decisions can be answered from Slack with signed Approve / Reject buttons.

What the academy plan buys (no extra signature): the video library. Paid lessons open with an active `AcademySubscription` plus the course's minimum Enigma level (or an owner grant); free previews are open to anyone signed in. Only musicians at Enigma level 5 with an approved instructor role can draft courses, and nothing reaches students until the owner approves it.

## DevNet accounts

| Account | Address |
|---|---|
| Program (v0.10.0, upgradeable) | `GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG` |
| `Eh8sConfig` PDA | `GgpAKj9FZppC8gckF6SzevirNbgkfhpEbqiz1GE6oKLF` |
| Treasury PDA `["treasury"]` | `DxGjk32dH8Jh5Xodx2syERsZJ6RPPaULxKsNmFmVWYWY` |
| Treasury USDC ATA | `9ptwWxWj5YQKP3pD5b4UEDksARBzkSQPtS3Kyvjnff4Z` |
| Vault USDC ATA | `U9mzuiHMVUsCw9BLKGibq55BPZuLaxipnvKFvn76Vjq` |
| USDC mint (Circle DevNet) | `4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU` |
| Governance PDA `["governance"]` (not initialized) | `3ZH4igyjF1jFPhyRTAczh1Za8bgjaTfXe2cB9v2v49XY` |
| AgentAuthority PDA | `["agent", agent_wallet]` — one per granted agent wallet |

## Preflight (automated)

`-Preflight` checks, without any wallet (25 checks, exit code 0 = pass):

1. Spring `/health` is `ok` and the Vite proxy reaches it (no Network Error).
2. Fourteen protected routes answer **401** without a Bearer token: owner treasury, owner digest, owner agents, governance, stage queue, vault settle queue, sync deals, NEXUS status, stage map pins, owner decisions, the course catalog, owner course authoring, teaching eligibility and the owner course review queue.
3. An unsigned Slack click to `/api/slack/interactions` answers **401** (that route is guarded by the Slack signature, not a Bearer token).
4. The program account is executable on DevNet.
5. The config PDA is owned by the program.
6. The treasury ATA's token owner is the treasury PDA and its mint is DevNet USDC.
7. The vault account exists.
8. The governance PDA is either absent (the judge path: the owner withdraws directly) or owned by the program.
9. One `getProgramAccounts` read groups the program's accounts by Anchor discriminator: exactly one `Eh8sConfig`, the number of `AgentAuthority` PDAs (0 = the owner signs every agent power itself), and counts for profiles, band vaults, subscriptions, venue listings, escrows, royalty pools and sync licenses. The public DevNet RPC rate-limits this method; the script retries once, or set `EH8S_RPC`.

## Wallets

| Label | Role | How you log in |
|---|---|---|
| **A — Owner** | Activates band vaults, approves venues, pins contracts, settles escrows, activates song pools, withdraws the treasury, runs the digest, publishes video courses and reviews instructor courses | Phantom on `7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC` (bootstrap owner). Not an applyable role. |
| **B — Musician** | Academy, band member, claims USDC | Second Phantom account. Apply as **musician**. Enroll so the MusicianProfile PDA exists before settle. |
| **C — Venue** | Books and settles shows | Third account. Apply as **venue**. Fund DevNet USDC for the net amount. |
| D — Musician (optional) | Second band member | Shows the weighted split across two claims. |

Fund every wallet with DevNet SOL (`solana airdrop`). B and C also need DevNet USDC from the Circle faucet.

## Guided steps and Explorer checks

| # | Step | Where | Explorer / UI check |
|---|---|---|---|
| 0 | Stack up, preflight | `setup_db.py` · `mvn spring-boot:run` · `npm run dev` | Preflight PASS |
| 1 | Owner connects | http://127.0.0.1:5173 | Lands in the owner inbox |
| 2–3 | Musician(s) and venue apply | `/apply` | Pending in owner inbox |
| 4 | Owner approves | `/owner/roles` | Roles active |
| 5 | Musician enrolls and pays academy | `/academy/enroll` · `/academy/plans/:id` | 85% to `9ptwWx…` · 15% to instructor · UI confirmed |
| 6 | Musician creates band and adds members | `/bands/new` · `/bands/:id/members/add` | Members listed |
| 7 | Owner activates vault (sync weights if an SPP cycle is closed) | `/bands/:id/vault/activate` · `/bands/:id/vault/sync` | `create_band` / `update_spp_weights` confirmed |
| 8 | Venue books and settles | `/stage-map/book` · `/stage-map/settle` | Preview shows gross · expenses · net · fee; fee to `9ptwWx…`, pool to `U9mzui…` |
| 9 | Each member claims | `/stage-map/claim` | Vault → member ATA per wallet |
| 10 | Owner withdraws the treasury | `/owner/treasury` → **Withdraw to my wallet** | `withdraw_treasury` confirmed; `/owner/treasury/activity` lists it |
| 11 | Owner runs the AI digest | `/owner/digest` → **Generate today's digest** | Latest digest shown (or "AI is off" without the key) |
| 12 | Venue lists on-chain; owner approves | `/stage-map/venues/:id` → **On-chain listing and escrow** → **Register on-chain**; Inbox → **Venues and bookings** → **Approve venue** | `register_venue` / `approve_venue` confirmed |
| 13 | Booking escrow → contract → settle | **Escrow with wallet** (venue); **Confirm and pin contract**; Inbox → **Escrows to settle** → **Settle from escrow** (owner) | Gross in escrow; contract SHA-256 on the token; fee to `9ptwWx…`, pool to `U9mzui…`, expenses to the venue |
| 14 | Subscriptions with expiry | `/academy/plans/:id` (**Months**) · `/catalog/reach` (**Region access**) → **Pay with wallet** | "Active until …" from the PDA `expires_at`; paying again adds months |
| 15 | Per-song pool + sync license | Track → **Activate song pool** → **Activate with wallet** → **Deposit royalties**; **Sync licenses** → **New sync deal** → **Pay with wallet**; `/catalog/credits` | Members credited by the song split; license 20% to `9ptwWx…`, 80% to members |
| 16 | NEXUS / HARMONY / ATLAS | `/academy/nexus` → **Request Score Enigma** → **Ask NEXUS**; band → **Find musicians** → **Run HARMONY**; band → **Tour routes** → **Plan a route** → **Generate route** | Stored Score Enigma (or "AI offline" without the key); ranked musicians; route inside the paid zone |
| 17 | Slack approvals | `/owner/decisions` → **Slack approvals** · **Answered decisions** | "Buttons are live" with `SLACK_SIGNING_SECRET`; a Slack click shows "Approved in Slack by …" |
| 18 | Owner publishes a video course; student watches | Inbox → **Courses** → **New course** → **Add section** → **+ Add lesson** (paste a YouTube / Vimeo / Bunny Stream / Cloudflare Stream link, tick **Free preview**) → **Publish**; **Course access** → **Grant access**; musician: Academy → **Video courses** → a lesson → **Mark complete and continue** | Free preview plays in the embedded player; a paid lesson is locked without a plan + level or a grant (no video link sent); course percentage rises |
| 19 | Level-5 instructor teaches; owner reviews | Academy → **Teach** (requirements checklist) → **My courses** → **New course** … lesson with **Practice exercise for NEXUS** → **Submit for review**; owner: **Courses** → **Review queue** → **Review** → **Send back with note** or **Approve and publish**; musician: lesson → **Send practice to NEXUS** | Course locked while in review; the note shows as "Changes requested: …"; approved course appears in Video courses; the NEXUS form shows the exercise |
| 20 | Optional, last: owner multisig | Inbox → **Governance** → **Set up signers** → **Sign setup**; **New proposal** → **Withdraw treasury** → **Sign proposal** → **Approve** → **Execute** | `init_governance` … `execute_withdraw_proposal`; afterwards step 10 is refused (`GovernanceActive`) |

Step 20 changes the shared DevNet program for everyone: once governance exists, the direct owner withdraw and direct agent grants stop working. Leave it out unless you want to show the multisig.

Environment switches (never committed): `ANTHROPIC_API_KEY` turns on the AI digest and NEXUS; `SLACK_SIGNING_SECRET` plus the Slack app's Interactivity Request URL (`https://<public tunnel>/api/slack/interactions`) turns on the Slack buttons; `EH8S_SLACK_WEBHOOK_URL` or the bot token delivers the messages.

## App

- UI: http://127.0.0.1:5173
- API: http://127.0.0.1:8080/health → `{"status":"ok","module":"eh8s"}`
- Explorer: https://explorer.solana.com/?cluster=devnet

Disconnect the wallet in Phantom between roles (or use separate browser profiles).

## Judge checklist

1. Academy pay has a confirmed signature and 85% lands in the treasury PDA ATA.
2. Band vault activation is a confirmed `create_band` signed by the owner.
3. Settle shows expenses, fee to the treasury PDA ATA, and pool to the vault.
4. Every band member can claim their weighted share on Explorer.
5. The owner withdraws the treasury to their own ATA from `/owner/treasury`.
6. The AI digest page shows a stored Claude summary when `ANTHROPIC_API_KEY` is set.
7. A venue listing is approved on-chain, a booking's gross sits in escrow, and **Settle from escrow** splits it (expenses, fee, pool).
8. Academy and zone subscriptions show an on-chain "Active until" date.
9. A song deposit credits every member by the song split, and a sync license sends 20% to the treasury PDA ATA.
10. NEXUS, HARMONY and ATLAS each write an agent event from a real action; Slack Approve / Reject answers a decision once.
11. The owner publishes a course from a video link; a student watches the free preview, a paid lesson stays locked without a plan and level, and completion is saved.
12. A musician below level 5 (or without the instructor role) cannot create a course; an eligible instructor's course reaches students only after the owner approves it.

## Contest disclosure

Pre-existing vs Crypto World's Fair window (14 Sep–12 Oct 2026): see [`COLOSSEUM_DISCLOSURE.md`](./COLOSSEUM_DISCLOSURE.md). Paste that into the Colosseum submission form.
