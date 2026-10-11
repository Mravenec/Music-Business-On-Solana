# EH8S — Enigma H8 Studios

Music studio network on **Solana DevNet**: academy tuition, venue settlement, and royalties move **real DevNet USDC** (faucet money). Phantom or Solflare signs every payment. There are no pretend balances and no SQL demo users for the judge path.

Program (DevNet): `GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG`  
USDC mint: `4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU`

## What judges should run

Deliverable folders: `0.database/` · `1.backend/eh8s/` · `2.frontend/eh8s/` · `3.diagrams/` · `4.anchor/eh8s-devnet/` · `5.submission/`

Guided money smoke (three wallets — owner, musician, venue):

```powershell
cd 4.anchor/eh8s-devnet
.\hackathon-smoke.ps1 -Preflight   # 25 automated checks: stack, JWT 401 gates (incl. courses + teaching), Slack signature gate, program, config, treasury PDA ATA, vault, governance PDA, agent authority PDAs
.\hackathon-smoke.ps1              # preflight + guided wallet steps
```

The guided run **pauses** and tells you which wallet to connect. It does not send fake transactions. Path: academy pay → owner activates the band vault → venue listing + booking escrow → **settle from escrow** (the only show-settle path) → each member claims → owner withdraws the treasury → owner AI digest → subscriptions with expiry → per-song royalty pool + sync license → NEXUS / HARMONY / ATLAS → Slack approvals → owner publishes a video course, a student watches → a level-5 instructor drafts a course and the owner reviews it → (optional, last) owner multisig. Steps and Explorer checks: [`4.anchor/eh8s-devnet/HACKATHON_SMOKE.md`](4.anchor/eh8s-devnet/HACKATHON_SMOKE.md).

**One settle path:** confirmed booking escrow → wallet signs `settle_booking` → `POST /api/bookings/{id}/settle/confirm` (UI: Inbox / Escrows to settle). There is no MariaDB-only invent-settle (`POST /api/concerts/{id}/settle` is removed).

Program tests (WSL, loads the built `.so`): `cd 4.anchor/eh8s-devnet/tests-program && SBF_OUT_DIR=$PWD/../target/deploy cargo test`.

## Money loops (DevNet only)

| Flow | Who pays | Who receives |
|---|---|---|
| Academy subscribe | Musician wallet | 85% treasury PDA / 15% instructor |
| Booking escrow + settle (only show settle) | Venue wallet escrows the gross; owner or VAULT agent settles `settle_booking` | Expenses → venue; fee → treasury PDA; pool → vault, split by SPP weight; members **claim** next |
| Treasury withdraw | Owner wallet signs | Treasury PDA → owner USDC ATA (`/owner/treasury`) |
| Per-song royalty deposit | Owner or WAVE agent | Vault, credited to each song member by the song's splits |
| Sync license | Licensee wallet | 20% treasury PDA / 80% song members |
| Academy plan / zone access | Musician wallet, 1–12 months | Treasury PDA (academy 85% / 15% instructor); on-chain `expires_at` |

Owner AI digest (`/owner/digest`): Claude summarizes the day's loop metrics and treasury balance when `ANTHROPIC_API_KEY` is set on the backend. The same key powers NEXUS (Score Enigma); HARMONY and ATLAS run without it. Agent decisions can be answered from Slack with signed Approve / Reject buttons when `SLACK_SIGNING_SECRET` is set. An optional native owner multisig (Governance PDA `3ZH4igyjF1jFPhyRTAczh1Za8bgjaTfXe2cB9v2v49XY`) replaces the direct treasury withdraw once it is set up.

Academy video library (`/academy/courses`): the owner builds courses from YouTube / Vimeo / Bunny Stream / Cloudflare Stream links at `/owner/courses`; paid lessons open with an active academy plan plus the course's minimum Enigma level (or an owner grant), and locked lessons never send the video link. Teaching is earned (`/academy/teach`): Enigma level 5 plus an approved instructor role lets a musician draft courses, and the owner approves each one from `/owner/courses/review` before students see it. Lesson practice exercises go to NEXUS.

Program v0.10.0 (DevNet). Agent wallets get on-chain powers through `AgentAuthority` PDAs (NEXUS levels, HARMONY vaults, STAGE venues, VAULT settlement, WAVE royalties).

Platform owner (bootstrap, not an applyable role): `7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC`

## Stack (never inverted)

MariaDB (`0.database/canonical_sql`) → JOOQ **POJOs** (no Java DTOs) → repository → service → controller → `.http` → React `services` → `hooks` → `pages`.

## Quick start

1. `python 0.database/scripts/setup_db.py` — **do not** run `load_mocks.py`
2. `cd 1.backend/eh8s` → `mvn spring-boot:run`
3. `cd 2.frontend/eh8s` → `npm run dev` → http://127.0.0.1:5173
4. Phantom → DevNet → fund SOL + DevNet USDC → walk the smoke script

Health must not show Network Error from the Vite origin.

## Contest note

Colosseum scores work in the **14 Sep–12 Oct 2026** window. Earlier engine work is pre-existing and **must be disclosed** on the submission form.

Submission kit (pitch and demo video scripts, form answers, weekly updates, logo, checklist): [`5.submission/`](5.submission/SUBMISSION_CHECKLIST.md)

Full timeline (pre-existing vs contest-window, Q&A for the form):  
[`4.anchor/eh8s-devnet/COLOSSEUM_DISCLOSURE.md`](4.anchor/eh8s-devnet/COLOSSEUM_DISCLOSURE.md)

This product uses DevNet faucet USDC, not Mainnet. Agent board process lives in `AGENTS.md` — not required for the demo.
