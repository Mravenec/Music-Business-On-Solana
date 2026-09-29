# EH8S — Enigma H8 Studios

**A music studio network on Solana.** One app takes a musician from the first lesson to a paid show: the academy, the band, the venue and the payout all settle in USDC through one Anchor program. Every payment is signed in the user's own wallet (Phantom or Solflare). There are no custodial balances and no demo data.

| | |
|---|---|
| Network | Solana **DevNet** (faucet USDC, not Mainnet) |
| Program | [`GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG`](https://explorer.solana.com/address/GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG?cluster=devnet) · v0.10.0 · 27 instructions |
| USDC mint (Circle DevNet) | `4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU` |
| Platform owner | `7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC` |
| Hackathon | Colosseum Crypto World's Fair, 14 Sep – 12 Oct 2026 · [disclosure of prior work](4.anchor/eh8s-devnet/COLOSSEUM_DISCLOSURE.md) |

## The problem

Independent musicians are paid last and paid least: a band plays on Friday and waits weeks for a bank transfer that someone splits by hand. Learning, forming a band, booking venues and collecting royalties live in separate worlds, so nobody can pay the musician instantly and fairly.

## What EH8S does

| Role | What they do in the app | What happens on-chain |
|---|---|---|
| **Student** | Picks a plan ($30 / $55 / $90 a month, 1–12 months) and studies the *Enigma Method* video courses | `subscribe_academy`: 85% to the studio treasury PDA, 15% straight to the instructor; `expires_at` stored on-chain |
| **Musician** | Rises through Enigma levels 0–5, forms a band or plays solo, requests a slot at a venue | `update_musician_level` (only the owner or the NEXUS agent can sign it); `create_band` + `update_spp_weights` |
| **Venue** | Lists itself (capacity, ticket price, contract type), opens dates on the Stage Map, escrows each show | `register_venue`, `propose_booking` (gross into escrow), `confirm_booking` pins the SHA-256 of the contract |
| **Band** | Gets paid the night of the show; each member claims their own share | `settle_booking`: expenses back to the venue, 15% fee to the treasury PDA, the rest to the band vault, split by each member's **Shared Participation (SPP)** score; `claim_royalties` |
| **Artist** | Earns per-song royalties and sync licenses | `create_royalty_pool`, `deposit_royalties`, `pay_sync_license` (80% artists / 20% studio) |
| **Touring band** | Plans a tour through the zones it subscribes to (Local $5 → Global $99 a month) | `subscribe_geographic` with on-chain expiry; the ATLAS agent routes approved venues |
| **Owner** | Approves roles, courses and agent decisions (web or Slack); withdraws the treasury | `withdraw_treasury` (owner only), `authorize_agent` (bit-scoped `AgentAuthority` PDAs), optional native multisig (`init_governance`, `propose`, `approve_proposal`, `execute_*`) |

**AI agents.** Twelve agents support the studio's operations. NEXUS scores practice and recommends levels (Claude), HARMONY matches musicians to bands, ATLAS plans tour routes, and a Claude digest summarises the day for the owner. An agent can act on-chain only with the permission bits the owner signs into its `AgentAuthority` PDA.

## Repository map

```text
0.database/             MariaDB schema: one idempotent SQL file per schema (6 schemas) + setup scripts
1.backend/eh8s/         Spring Boot 3.5 · Java 21 · JOOQ · JWT: 32 controllers, 33 services, 28 repositories
                        service/solana/: instruction builders, PDA derivation, RPC client, transaction verifier
2.frontend/eh8s/        React 18 · Vite 5 · TypeScript · Solana wallet adapter: services → hooks → pages
3.diagrams/             Visual twins: dbdiagram.io (DBML), PlantUML (backend layers), Mermaid (frontend + money loop)
4.anchor/eh8s-devnet/   Anchor program (Rust), program tests, judge smoke script, DevNet addresses, disclosure
5.submission/           Logo (PNG + SVG)
```

## Architecture

```text
Phantom / Solflare ──signs──▶ Anchor program (DevNet) ──▶ USDC token accounts (treasury PDA, vault, escrow)
        │                                  ▲
        │ signed login (nonce)             │ the backend re-reads every signature from RPC and checks
        ▼                                  │ program id, accounts and amounts before recording it
React (services → hooks → pages) ──JWT──▶ Spring Boot (controller → service → repository) ──JOOQ──▶ MariaDB
```

Layers are never inverted: MariaDB → generated JOOQ POJOs (no hand-written DTOs) → repository → service → controller → React services → hooks → pages. Every layer is injected through its interface and documented with Javadoc.

## Security decisions

- **Wallet-signed login.** The server issues a one-time nonce; the wallet signs `Sign in to EH8S`; only a valid ed25519 signature returns a session. No signature, a reused nonce or another wallet's signature → 401.
- **JWT on every private API.** Only health, the sign-in endpoints and the Slack callback (checked with Slack's signature) are public. The preflight proves 14 protected routes return 401 without a token.
- **The server never moves money.** Every payment is a transaction the user signs. The backend verifies each confirmed signature against the program, accounts and amounts before it writes a row.
- **Owner-only and agent-scoped powers on-chain.** Treasury withdrawals, agent permissions and level changes are enforced by the program, not by the UI. An optional native multisig replaces the single owner once enabled.

## Verify it yourself

```powershell
# 1. Database (Docker) and schema
python 0.database/scripts/setup_db.py

# 2. Backend (http://127.0.0.1:8080)
cd 1.backend/eh8s; mvn test; mvn spring-boot:run

# 3. Frontend (http://127.0.0.1:5173)
cd 2.frontend/eh8s; npm install; npm run dev

# 4. Judge preflight: 25 automated checks (stack, JWT gates, Slack signature gate, program, config, treasury, vault, governance, agent PDAs)
cd 4.anchor/eh8s-devnet; .\hackathon-smoke.ps1 -Preflight

# 5. Guided money run with three DevNet wallets (owner, musician, venue); pauses for each signature
.\hackathon-smoke.ps1
```

| Test suite | Where | Count |
|---|---|---|
| Backend unit tests | `1.backend/eh8s/src/test/java/com/eh8s/eh8s/service` | 164 |
| HTTP contract files (401 without a token, 2xx with a signed session) | `1.backend/eh8s/src/test/java/com/eh8s/eh8s/http` | 34 |
| Program tests (load the built `.so`) | `4.anchor/eh8s-devnet/tests-program` | 59 |

Program tests: `cd 4.anchor/eh8s-devnet/tests-program && SBF_OUT_DIR=$PWD/../target/deploy cargo test` (Linux / WSL).

Step-by-step wallet walkthrough with Explorer checks: [`4.anchor/eh8s-devnet/HACKATHON_SMOKE.md`](4.anchor/eh8s-devnet/HACKATHON_SMOKE.md). Every instruction, account and error: [`4.anchor/eh8s-devnet/README.md`](4.anchor/eh8s-devnet/README.md).

## Configuration

Copy `0.database/.env.example` and `1.backend/eh8s/.env.example`. The defaults are for local development only; set your own `JWT_SECRET` and database password anywhere else. Optional: `ANTHROPIC_API_KEY` (NEXUS scoring and the owner digest) and `SLACK_SIGNING_SECRET` (Slack approval buttons).
