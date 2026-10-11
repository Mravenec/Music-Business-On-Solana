# Ops checklist: legal entity and key custody (production)

English production checklist for EH8S. Companion to [`OPS_MULTISIG_2OFN.md`](./OPS_MULTISIG_2OFN.md) (2-of-N UI practice).

**No private keys, seed phrases, JWT secrets, database passwords, API tokens, or keypair JSON belong in this document or in git.**

This note never asks anyone to commit a keypair or paste a secret into the repo. Placeholders and env var **names** only.

---

## Goal

Before treating a network as production (or Mainnet), name the legal owner of the stack and prove every high-value key lives outside git — while keeping booking settle, claim, academy confirm, and studio-admin **read** paths working.

---

## 1. Legal entity

Complete before Mainnet or any public money path that is not a hackathon faucet.

| # | Check | Done |
|---|---|---|
| 1.1 | A legal entity (company / foundation / other) is named as the operator of production EH8S | ☐ |
| 1.2 | That entity is the contractual counterparty for venues, instructors, and partners (books and partner shares do not replace contracts) | ☐ |
| 1.3 | Who may hold upgrade authority, principal wallet, and cold governance signers is written in the entity’s internal policy (not in this repo) | ☐ |
| 1.4 | Partner USDC **payout** (separate instruction design; epic APP-77-2) is approved by the entity before any auto-move of treasury funds | ☐ |
| 1.5 | Incident contact and key-loss recovery steps exist offline (hardware + sealed recovery) — never as committed seed text | ☐ |

Do **not** put entity registration numbers, tax IDs, or director home addresses in git.

---

## 2. Program upgrade authority

On Solana, the **upgrade authority** wallet can replace the program binary and (for this stack) sign `initialize_config` / `init_treasury`. It is not the program keypair.

| # | Check | Done |
|---|---|---|
| 2.1 | Upgrade-authority key material lives only on hardware / sealed recovery — never under `4.anchor/`, never in `.env`, never in chat | ☐ |
| 2.2 | Production upgrades require explicit human OK (same posture as DevNet README: extend + deploy only with approval) | ☐ |
| 2.3 | Operators know how to verify authority without copying secrets: `solana program show <PROGRAM_ID> --url <cluster>` | ☐ |
| 2.4 | Day-to-day Owner UI work uses the **principal** (`config.owner`) and governance signers, not the upgrade-authority key on a hot laptop | ☐ |
| 2.5 | After production launch, plan to move upgrade authority to a multisig or time-locked process owned by the legal entity (document the plan offline; execute only with human OK) | ☐ |

Program id on DevNet (public address only): see [`README.md`](./README.md). Addresses are not secrets.

---

## 3. Program keypair policy (never commit)

The **program keypair** is the address key for the BPF loader account. After first deploy it **cannot** upgrade the program; upgrades need the upgrade-authority wallet.

| # | Rule | Why |
|---|---|---|
| 3.1 | **Never** `git add` / commit `target/deploy/*.json` keypair files, `id.json`, or any Solana keypair JSON | History keeps secrets forever |
| 3.2 | **Never** instruct teammates or agents to commit keypairs “for the judges” or “for CI” | CI uses public program id + built `.so`, not the keypair |
| 3.3 | Keep keypair files outside the product repo (local secure path or HSM); `.gitignore` already covers `.env` and build `target/` under Anchor | Untracking is hygiene; authority is still the upgrade wallet |
| 3.4 | If a keypair ever appeared in git history, rotate / treat as burned for new deployments; do not rewrite shared history without an explicit security process | Disclosure already notes untracking ≠ rewrite |

**Forbidden instruction (do not follow, do not write elsewhere):** “commit the program keypair so others can deploy.”

---

## 4. JWT secret

Spring reads `JWT_SECRET` into `eh8s.jwt.secret` (HS256). Local defaults exist only for development.

| # | Check | Done |
|---|---|---|
| 4.1 | Production `JWT_SECRET` is a long random value (32+ characters), set only in the host/orchestrator secret store or untracked env | ☐ |
| 4.2 | Real `JWT_SECRET` is **never** committed; only the placeholder pattern in `application.properties` / `.env.example` may live in git | ☐ |
| 4.3 | Production does **not** use the sample default `eh8s-dev-jwt-secret-change-me-32b` | ☐ |
| 4.4 | Rotate JWT secret on suspected leak; accept that active bearer tokens become invalid after rotation | ☐ |
| 4.5 | Health stays public; protected `/api/**` still require `Authorization: Bearer` (see JWT harness rules) | ☐ |

---

## 5. Storage and service keys

“Storage keys” here means credentials that unlock **persistent** systems and integrations (database, object/media hosts if used, Slack, AI). Treat every one like a production secret.

| # | Secret (env **name** only) | Production rule | Done |
|---|---|---|---|
| 5.1 | MariaDB / JDBC password (and any managed-DB admin URL) | Host secret store or untracked env; never commit real passwords; prefer least-privilege DB user for the app | ☐ |
| 5.2 | Object / media storage credentials (S3, R2, GCS, Azure, CDN signing keys — if the entity enables hosted video or assets) | Separate IAM keys per environment; no keys in React source or git | ☐ |
| 5.3 | `SLACK_SIGNING_SECRET`, `EH8S_SLACK_BOT_TOKEN`, webhook URLs | Env only (see `1.backend/eh8s/.env.example`); blank signing secret keeps interactivity refused | ☐ |
| 5.4 | `ANTHROPIC_API_KEY` (owner digest / NEXUS) | Env only; blank disables AI with 503 — never paste into docs | ☐ |
| 5.5 | Any other third-party token added later | Same rule: name in `.env.example`, value only outside git | ☐ |

Root `.gitignore` ignores `.env` / `.env.*` (examples under `0.database/` and `1.backend/eh8s/` remain allowed).

---

## 6. Hot vs cold summary

| Asset | Hot OK? | Cold / offline | In git? |
|---|---|---|---|
| Principal wallet (Owner inbox) | Yes, for day-to-day UI | Keep a recovery copy offline | **No** seed |
| Governance cold signers | No (approve on hardware) | Required for 2-of-N | **No** |
| Upgrade authority | No for daily use | Required for program upgrades | **No** |
| Program keypair JSON | N/A after deploy | Secure archive only | **Never commit** |
| `JWT_SECRET` | Process env / secret manager | Backup in entity vault | **No** real value |
| DB / storage / Slack / AI keys | Process env / secret manager | Entity vault | **No** real value |

---

## 7. What must not break

Completing this checklist must **not** pause:

- Booking settle / claim
- Academy confirm
- Studio admin **read** of partner books and own earnings
- Working money paths that do not call withdraw or studio-power mutators

Turning on governance or rotating JWT/DB credentials is a planned ops window — not a reason to invent settle or bypass on-chain share windows.

---

## Quick verify (read-only)

1. This file is English and lives under `4.anchor/eh8s-devnet/`.
2. Search the file for key material: no `PRIVATE`, no JSON secret arrays, no seed word lists, no real JWT/DB tokens.
3. Confirm the doc **forbids** committing keypairs (section 3) and never instructs `git add` of `*.json` keypairs.
4. Confirm upgrade authority, JWT, and storage/service keys each have their own checklist section.
5. Cross-read [`OPS_MULTISIG_2OFN.md`](./OPS_MULTISIG_2OFN.md) for 2-of-N UI labels; this file does not replace that practice note.

Do **not** deploy, upgrade, or open Mainnet from this ticket.
