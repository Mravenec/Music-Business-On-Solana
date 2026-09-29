# Colosseum Crypto World's Fair — pre-existing vs contest-window work

**Contest:** Crypto World's Fair (Colosseum)  
**Judged window:** 14 Sep 2026 – 12 Oct 2026 (PT)  
**Product:** EH8S (Enigma H8 Studios)  
**Repo tip documented here:** `main` after Land 52 (27 Sep 2026), tagged locally `colosseum-worldsfair-2026`. Earlier tips: Land 48 `d4e4bf6` (27 Sep), Land 39 (26 Sep), Land 33 `5811700` (25 Sep).

Colosseum allows pre-existing code but **judges work done in the contest window** and requires **disclosure of prior development** on the submission form. Paste or summarize this file there.

Official FAQ: [colosseum.com/hackathon](https://colosseum.com/hackathon) · Rules: [Crypto World's Fair Hackathon Rules (PDF)](https://colosseum.com/legal/Crypto%20World's%20Fair%20Hackathon%20Rules.pdf)

---

## One-line disclosure (submission form)

EH8S began **6–8 Sep 2026**. Before **14 Sep**: full MariaDB / Spring Boot JOOQ / React studio app and Anchor DevNet program with academy / geo / deposit / claim USDC; `settle_concert` was PDA-only. **During Crypto World's Fair (from 14 Sep):** JWT auth, progressive-disclosure UI, no-mock Colosseum smoke and wallet-only money gates (epic 32), on-chain settle fee→owner + pool→vault + claim path without mocks (epic 33), and on 26 Sep: hardened Anchor accounts + Rust program tests + first real DevNet deploy (epic 34), BandVault + SPP-weighted split with expenses (epic 35), backend verification of every signature (epic 36), treasury PDA + owner withdraw (epic 37), Claude owner digest (epic 38), refreshed judge kit (epic 39); and on 27 Sep: agent authority PDAs + agent-only musician levels (epic 40), on-chain venue listings + booking escrow + VAULT settle (epic 41), native owner multisig (epic 42), academy / zone subscriptions with on-chain expiry (epic 43), per-song royalty pools + sync licensing 80/20 (epic 44), real SPP inputs + live Stage Map pins (epic 45), live NEXUS / HARMONY / ATLAS agents (epic 46), Slack two-way approvals (epic 47), judge kit refresh (epic 48), repo hygiene + submission kit (epic 49), academy video library (epic 50), earned teaching path with owner review and lesson practice to NEXUS (epic 51), final submission refresh (epic 52), backend layer conformance with generated JOOQ POJOs from the database to the React services and no Java DTOs (epic 53); program upgraded v0.6.0 → v0.10.0 on DevNet. Single git author. DevNet faucet USDC only — not Mainnet.

---

## Master split

| Pre-existing (before 14 Sep 2026) | Contest window (14 Sep 2026 → submission) |
|---|---|
| Neutral harness + private.board process | JWT auth landed (epic 30, 14 Sep) |
| MariaDB → Spring/JOOQ → React product (academy, bands, stage, catalog, owner) | Progressive disclosure UI (epic 31, 14 Sep) |
| Solana config, wallet adapter, pay markers (epic 14, 9 Sep) | JOOQ POJOs / no-DTO rule hardening (19 Sep) |
| Anchor program + DevNet program id `GUY7o…` (epics 17/20, ~9–10 Sep) | Colosseum no-mock judge path (epic 32, 24 Sep): refuse unsigned “intended”, wallet-only money UX, `hackathon-smoke.ps1` |
| Real DevNet USDC for academy / geo / deposit / claim (epics 21–23, ~9–10 Sep) | Closed settle profit cycle (epic 33, 25 Sep): settle moves fee→treasury + pool→vault + credits pending; claim fallback without SPP; FE settle accounts; smoke/docs/Mermaid |
| `settle_concert` as **PDA accounting only** (no USDC transfer) | Anchor hardening (epic 34, 26 Sep): every mint / treasury / vault / profile account constrained, `solana-program-test` suite, **first real DevNet deploy** (slot 504604312), Java + TS builders aligned |
| | BandVault + SPP split (epic 35, 26 Sep): `create_band` / `update_spp_weights`, `settle_concert(gross, expenses)` splits the pool by member weight; settlement expenses column |
| | Tx verification (epic 36, 26 Sep): backend decodes each signature and checks program id, accounts, and amounts before recording |
| | Treasury PDA + owner withdraw (epic 37, 26 Sep): `init_treasury` / `withdraw_treasury`, fees land in the treasury PDA ATA, `/owner/treasury` screen |
| | AI owner digest (epic 38, 26 Sep): Claude summary of loop metrics + treasury balance, stored in `owner_digest`, `/owner/digest` |
| | Submission kit (epic 39, 26 Sep): `hackathon-smoke.ps1 -Preflight`, refreshed smoke guide, money-loop Mermaid, this disclosure |
| | Agent authority (epic 40, 27 Sep, program v0.6.0): `authorize_agent` → `AgentAuthority` PDA with permission bits; `update_musician_level` by owner or NEXUS only; profiles start at level 0 |
| | Venue booking on-chain (epic 41, 27 Sep, v0.7.0): `register_venue` / `approve_venue`, `propose_booking` USDC escrow, `confirm_booking` contract SHA-256, `cancel_booking`, `settle_booking` by owner or VAULT agent |
| | Owner multisig (epic 42, 27 Sep, v0.8.0): `init_governance`, proposals, approvals, execute withdraw / agent / signer changes; direct paths refused once governance exists |
| | Subscriptions with expiry (epic 43, 27 Sep, v0.9.0): academy plan × months and per-zone geographic subscriptions with on-chain `expires_at` |
| | Per-song royalties (epic 44, 27 Sep, v0.10.0): `create_royalty_pool` splits, `deposit_royalties` by split, `pay_sync_license` 20% treasury / 80% members |
| | SPP inputs + Stage Map (epic 45, 27 Sep): creative ratings, set check-ins, level deltas feed the five SPP variables; pins from live availability and bookings; Request slot |
| | Live agents (epic 46, 27 Sep): NEXUS Score Enigma via Claude, HARMONY band matching, ATLAS tour routes inside paid zones; each writes an agent event |
| | Slack two-way (epic 47, 27 Sep): signed Block Kit Approve / Reject buttons answer owner decisions exactly once |
| | Submission kit refresh (epic 48, 27 Sep): preflight checks governance, agent authority and new protected routes; guided steps 12–18; this disclosure |
| | Repo hygiene + submission kit (epic 49, 27 Sep): program keypair untracked; `5.submission/` pitch and demo scripts, form answers, weekly updates, logo, checklist |
| | Academy video library (epic 50, 27 Sep): owner builds courses from YouTube / Vimeo / Bunny Stream / Cloudflare Stream links; students watch with saved progress; paid lessons need an active plan + minimum Enigma level or an owner grant, and locked lessons never send the video link |
| | Teaching path (epic 51, 27 Sep): only Enigma level 5 + approved instructor role can draft courses; submit → owner approves or sends back with a note; lesson practice exercises go to NEXUS and the evaluation links the lesson |
| | Final refresh (epic 52, 27 Sep): preflight covers the course and teaching routes (25 checks), guided steps 18–19, demo script academy shot, this disclosure, local tag |
| | Layer conformance (epic 53, 27 Sep): no Java DTOs; table-shaped bodies bind the generated JOOQ POJOs, services and repositories never receive request maps, on-chain services take typed values; the React services send the POJO JSON keys; 159 backend tests |
| | Further commits through **12 Oct 2026** also count — amend this table before submit |

Git volume at the epic 52 commit (27 Sep 2026, epics 49–51 included): **375** commits before the window (`git log --until=2026-09-13`), **265** from 14 Sep (`git log --since=2026-09-14`), one author. The Land 49–52 merges on `main` add a few more; rerun the two commands below at the tag for the exact figure.

---

## Repo hygiene (epic 49)

The DevNet **program** keypair (`4.anchor/eh8s-devnet/target/deploy/eh8s_devnet-keypair.json`) was committed on 9 Sep so every checkout deployed to the same address. Epic 49 stopped tracking it (`target/` is ignored); it is still in git history because history is never rewritten. It cannot change the deployed program: upgrades are signed by the upgrade-authority wallet (`YmTYQJifjP2DawdxDUYuNCZJ5to9ge5nNGaWGW2ozJU`), which has never been in the repo. No `.env` file, API key, Slack secret, JWT secret or wallet keypair is tracked.

---

## External integrations not built (gap analysis section 4)

DistroKid, Chartmetric, MoonPay / Transak / Bitso, Stripe Identity, DocuSign, Metaplex, Squads, Helius, Arweave / IPFS, Next.js and React Native stay out: each needs third-party accounts, contracts, or KYC. Three were replaced in-program:

| Named in the spec | Replaced by |
|---|---|
| Squads multisig | Native owner multisig in the EH8S program (`init_governance` → `propose` → `approve_proposal` → `execute_*`, epic 42) |
| Arweave contract storage | SHA-256 of the contract text pinned on the `VenueAccessToken` by `confirm_booking` (epic 41); the text stays in MariaDB |
| Helius RPC | Configurable RPC: `chain_config.rpc_url` feeds the backend verifier and the wallet UI, `EH8S_RPC` the smoke preflight; public DevNet RPC by default |

---

## Timeline answers (for the form)

| # | Question | Answer (from this git history) |
|---|---|---|
| 1 | When did EH8S development start? | ~**6 Sep 2026** harness day-zero; product `eh8s` scaffolds **8 Sep** |
| 2 | When did the current version start? | Continuous from **8 Sep**. Judge-facing baseline: `main` after **epic 52 (27 Sep)**, tag `colosseum-worldsfair-2026` |
| 3 | What existed before 14 Sep? | Full DB/API/UI, roles, Solana config, Anchor program, academy/geo/deposit/claim USDC; settle PDA-only. Tip: `14c4e12` (11 Sep) |
| 4 | Solana/Anchor before 14 Sep? | **Yes** — scaffold 9 Sep; instructions + program id 9–10 Sep. Settle did **not** transfer USDC yet |
| 5 | New work 14 Sep–12 Oct | JWT, progressive UI, no-DTO hardening, epic 32 smoke/gates, epic 33 settle USDC loop, epics 34–39 (hardened program + tests + first deploy, BandVault split with expenses, tx verification, treasury PDA withdraw, Claude digest, judge kit), epics 40–48 (agent authority, venue escrow, multisig, subscriptions with expiry, per-song royalties + sync, SPP inputs + Stage Map, live agents, Slack approvals, kit refresh), epics 49–52 (repo hygiene + submission kit, academy video library, earned teaching path with owner review, final refresh) (+ any later commits) |
| 6 | First wallet integration? | **9 Sep** (epic 14 markers; academy `sendTransaction` APP-21-3) |
| 7 | First USDC settle/claim? | **Claim** ~9–10 Sep (epic 22). **Settle that moves USDC** coded **25 Sep** (epic 33), live on DevNet **26 Sep** (epic 34 deploy); weighted split with expenses **26 Sep** (epic 35) |
| 8 | DevNet program deploy? | Program id `GUY7o…` reserved and documented by epic 20 (~10 Sep). **First real DevNet deploy 26 Sep 2026** (epic 34, slot 504604312); upgraded to v0.5.0 in epic 37 (treasury PDA), then v0.6.0 → v0.10.0 on **27 Sep** (epics 40–44) |
| 9 | `hackathon-smoke.ps1`? | **Created 24 Sep** (APP-32-1); extended **25 Sep** (APP-33-3); rewritten with `-Preflight` **26 Sep** (APP-39-1); governance / agent / route checks and steps 12–18 **27 Sep** (APP-48-1); course and teaching routes (25 checks) and steps 18–19, multisig moved to step 20, **27 Sep** (APP-52-1) |
| 10 | First real DevNet USDC signature? | **~9 Sep** academy subscribe (epic 21); judge path hardened **24 Sep** |
| 11 | Pre-window code modified in-window? | **Yes** — settle ix + FE accounts; money screens; React page splits; JWT; SQL for auth |
| 12 | Last big changes before window? | **11 Sep** SQL consolidation (26–29); JWT tickets started. Tip `14c4e12` |
| 13 | Who built what? | **One git author:** `mravenec` / `kcamposgo90@gmail.com`. Board “agents” are process roles |
| 14 | Commits before 14 Sep? | **Yes** (375; 265 from 14 Sep at the epic 52 commit) |
| 15 | Functional before 14 Sep? | **Yes** — run stack + DevNet academy/geo/claim; settle PDA-only |
| 16 | Version to submit by 12 Oct? | `main` at/after Land 52; local annotated tag `colosseum-worldsfair-2026` (push the tag before upload) |

---

## Evidence pointers

- Smoke: `4.anchor/eh8s-devnet/hackathon-smoke.ps1` · `HACKATHON_SMOKE.md`
- Money Mermaid: `3.diagrams/2.frontend/hackathon-money-loop.mmd`
- Commit history: one commit per ticket, dated; see the `git log` commands below
- Backend gates: `1.backend/eh8s/src/test/java/com/eh8s/eh8s/http/Courses.http` · `Teaching.http` (164 backend unit tests, 34 `.http` contract files)
- Program tests: `4.anchor/eh8s-devnet/tests-program` (`SBF_OUT_DIR=$PWD/../target/deploy cargo test`)
- Product README contest blurb: root `README.md`

To refresh dates before submit:

```bash
git log --until="2026-09-13" -1 --format="%h %ad %s" --date=short
git log --since="2026-09-14" --oneline
```
