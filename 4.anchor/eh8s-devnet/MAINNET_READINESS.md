# Mainnet readiness checklist (EH8S)

**Status: Mainnet is not open.** Live money paths stay on **Solana DevNet**. Do not switch RPC/cluster, deploy a Mainnet program, or pin a Mainnet USDC mint unless a human explicitly approves that step in writing for that ticket.

This file is a gate list, not a launch claim. Items stay ☐ until a human (or a later epic that names that gate) marks them done with evidence.

---

## Current chain facts (honest)

| Fact | Value |
|---|---|
| Live network | **DevNet** |
| Program id | `GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG` |
| Binary on-chain today | **v0.10.0** (last upgrade epic 44, slot 504863281) |
| Source tip (landed epic 74) | **v0.11.0** — `grant_studio_role` / `revoke_studio_role` / `set_partner_share` in repo; **not deployed** |
| DevNet USDC mint (faucet) | `4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU` |
| Upgrade authority wallet | Outside the repo (see `COLOSSEUM_DISCLOSURE.md` hygiene). Upgrades are signed by that wallet, not by the program keypair |
| Config owner (principal) | `7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC` |
| Program data size after v0.10.0 | **900,000** bytes (after prior `program extend`) |
| v0.11.0 release `.so` (built) | ~**946,904** bytes — larger than 900,000 → **extend required before any DevNet upgrade** |

```text
# Inspect only (DevNet). Do not point --url at mainnet-beta for production work without human OK.
solana program show GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG --url devnet
```

---

## Gate A — Security audit

| # | Gate | Status | Notes |
|---|---|---|---|
| A1 | Independent review of Anchor money + power instructions (settle, claim, treasury, governance, studio role, partner share) | ☐ | Hackathon DevNet hardening + `tests-program` are not a substitute for a Mainnet audit |
| A2 | Threat model: stolen JWT vs stolen upgrade authority vs stolen principal key | ☐ | Epic 73 closed JWT spoof / invent-settle holes; upgrade authority remains the critical on-chain key |
| A3 | Findings closed or accepted in writing before Mainnet deploy | ☐ | No Mainnet until A1–A3 are real |

---

## Gate B — Keys and custody

| # | Gate | Status | Notes |
|---|---|---|---|
| B1 | Upgrade authority on cold / hardware or documented multisig practice | ☐ | Hot key was acceptable for DevNet contest; not for Mainnet |
| B2 | Principal / governance signers documented; 2-of-N path understood (`init_governance`) | ☐ | Deeper custody write-up is epic 77 (`OPS_KEY_CUSTODY` / multisig ops) — do not invent “done” here |
| B3 | JWT secret, Slack signing secret, Claude API key, storage keys only in env — never git | ☑ process | Repo hygiene (epic 49): no `.env` or wallet keypairs tracked |
| B4 | Program keypair stays untracked; upgrades use upgrade authority only | ☑ process | Same as disclosure hygiene |
| B5 | Rotation plan if any contest-era key is suspected exposed | ☐ | Human-owned |

---

## Gate C — Mint and token accounts

| # | Gate | Status | Notes |
|---|---|---|---|
| C1 | Mainnet USDC mint chosen and pinned in `initialize_config` (not the DevNet faucet mint) | ☐ | DevNet faucet mint must **not** be copied to Mainnet |
| C2 | Treasury PDA ATA + vault ATA created on Mainnet after config init | ☐ | Same pattern as DevNet `init_config` / `init_treasury` |
| C3 | Backend `chain_config` + FE Programs page show Mainnet only after human OK | ☐ | Today Programs UI: DevNet live, Mainnet not open (epic 66) |
| C4 | No product path still assumes faucet airdrops | ☐ | Judges and local smoke stay on DevNet until C3 |

---

## Gate D — Upgrade authority and program lifecycle

| # | Gate | Status | Notes |
|---|---|---|---|
| D1 | Human OK recorded before any `solana program deploy` / upgrade | ☐ | Agents must not upgrade without that OK |
| D2 | Upgrade authority key available only to the operator who approved D1 | ☐ | |
| D3 | Post-upgrade smoke: academy, booking settle, claim, treasury withdraw (or governance path) | ☐ | Prefer DevNet first |
| D4 | Optional: freeze or transfer upgrade authority after a trusted binary | ☐ | Product decision — not done |

---

## Gate E — Program extend for v0.11.0 (DevNet first)

v0.11.0 is **source + tests on `main` (epic 74)** and is **not** on-chain. The built `.so` (~946,904 bytes) does not fit the current **900,000**-byte program data account.

| # | Gate | Status | Notes |
|---|---|---|---|
| E1 | Human OK to extend DevNet program data | ☐ | Example: `solana program extend GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG 100000 --url devnet` (size subject to re-measure of the release `.so`) |
| E2 | Human OK to upgrade DevNet to v0.11.0 with upgrade-authority keypair | ☐ | `solana program deploy target/deploy/eh8s_devnet.so --program-id GUY7o… --url devnet` |
| E3 | Confirm studio-power ixs on Explorer; backend confirm paths green | ☐ | Until then, MariaDB / UI studio-power paths cannot get real on-chain confirms for grant/share |
| E4 | Re-run program tests + hackathon preflight after upgrade | ☐ | |
| E5 | Mainnet deploy of a reviewed binary (separate from E1–E4) | ☐ | **Not started. Do not open Mainnet in this epic.** |

Historical extends (DevNet, already done): v0.7.0 600000→750000; v0.8.0→830000 (slot 504844157); v0.10.0→900000 (slot 504863281).

---

## Gate F — Product trust prerequisites (landed; keep green)

These are done on the product side and must stay true before any Mainnet talk:

| # | Item | Epic | Status |
|---|---|---|---|
| F1 | Role review authorizes from JWT session only; body wallet ignored for authz | 73 | ☑ landed |
| F2 | Invent-settle refused; booking `settle_booking` is the show settle path | 73 + 75-2 | ☑ landed |
| F3 | Principal-only partner mutate in API; studio admin reads books / own earnings | 73 | ☑ landed |
| F4 | On-chain studio role + partner share instructions + confirm/UI (source) | 74 | ☑ landed (DevNet upgrade pending E1–E3) |
| F5 | Owner reconcile: confirmed money rows checked against RPC; orphans demoted | 75-1 | ☑ landed |
| F6 | Dual-settle leftovers removed; docs/smoke describe one settle path | 75-2 | ☑ landed |

---

## Explicit non-goals for APP-75-3

- Do **not** run `solana program deploy` / `extend` against **mainnet-beta**.
- Do **not** change live `chain_config` to Mainnet.
- Do **not** claim “live on Mainnet” in disclosure, README, or Programs UI.
- Do **not** treat this checklist as audit completion.

When a human later opens Mainnet, record the OK, the mint, the program id, the slot, and which gates flipped ☑ in the same change that flips the cluster.

---

## Related docs

- Disclosure (contest window truth): `COLOSSEUM_DISCLOSURE.md`
- Deploy / extend commands (DevNet): `README.md`
- Judge smoke: `HACKATHON_SMOKE.md` · `hackathon-smoke.ps1`
- Roadmap custody / payout design: epic 77 journeys (not required to fake-complete here)
