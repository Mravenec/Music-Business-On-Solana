# Design: partner USDC payout (separate from books)

English design note for EH8S DevNet / later Mainnet.
**No private keys, seed phrases, JWT secrets, or keypair files belong in this document or in git.**
**No deploy in this ticket.** Program source in this worktree may still be v0.10.0; on-chain partner windows land with epic 74 (`PartnerShare` / `PartnerBook`, program v0.11.0 source on `feature/74-signed-studio-power`).

| Field | Value |
|---|---|
| Ticket | APP-77-2 |
| Status | **Design accepted for deferral** — implement in a later ticket after v0.11.0 partner PDAs are on the working branch |
| Anchor code in this ticket | **None** (not small-and-safe here without merging epic 74 and expanding governance kinds) |

---

## Goal

Pay a partner in USDC from the studio treasury **only** when an on-chain `PartnerShare` window authorizes that wallet, via a dedicated instruction (or governance execute path).  
MariaDB **partner books** (allocations, share %, started/ended) remain an accounting and UI surface. **Editing books must never move USDC.**

---

## Three layers (do not collapse)

| Layer | Where | Moves USDC? | Role |
|---|---|---|---|
| Partner books | MariaDB `studio_partner`, `studio_partner_allocation` + Spring `StudioLedgerService` | **No** | Fee split records, owner/studio-admin reads, month/source totals |
| Partner window | Anchor `PartnerShare` `["partner_share", wallet]` + aggregate `PartnerBook` `["partner_book"]` (epic 74) | **No** by itself | Signed principal authority: `share_bps`, `[starts_at, ends_at)` (`ends_at = 0` open-ended; `share_bps = 0` removed) |
| Partner payout | **Future** dedicated ix / proposal execute (this design) | **Yes** | Treasury ATA → partner USDC ATA, gated on a live window + signatures |

Books may **mirror** a confirmed payout after RPC verification (same pattern as settle/claim reconcile). Books must not be an input that the program trusts, and the books HTTP API must not assemble or send a payout transfer.

---

## Non-goals (this design and any first implementation)

- Partner **auto-drain** of treasury (no continuous skim on every settle).
- Changing `withdraw_treasury` or `execute_withdraw_proposal` semantics for the principal / governance signers.
- Mixing payout into `settle_booking`, `settle_concert`, `claim_royalties`, academy/geo subscribe, or sync pay.
- Letting a studio admin or JWT session move treasury USDC.
- Deploying or upgrading the DevNet program from this ticket.

---

## Hard invariants

1. **Window required.** Any partner payout transfer requires the `PartnerShare` account for that wallet with `share_bps > 0` and `window_active_at(entry, clock)` true at execute time (reuse epic 74 helpers: `window_active_at`, overlapping bps already capped in `PartnerBook`).
2. **Books never transfer.** `StudioLedgerService` / partner mutate endpoints only write MariaDB. No SPL transfer CPI from the backend. A UI “pay partner” action (future) must call the wallet → Anchor payout path, not `POST` books.
3. **Treasury withdraw stays intact.** Keep:
   - `withdraw_treasury` — owner, pre-governance, destination = owner ATA
   - `execute_withdraw_proposal` — governance kind `PROPOSAL_WITHDRAW` (0), destination owned by a governance signer  
   Partner payout is a **separate** code path so operator mistakes in books cannot widen who may pull treasury.
4. **Destination = partner.** Token account mint = `config.usdc_mint`; token account owner = `partner_share.wallet` (not an arbitrary ATA).
5. **Amount > 0** and `treasury.amount >= amount` (`InsufficientTreasury`); reuse `treasury_out` helper — do not fork a second CPI.
6. **No program trust of off-chain allocation rows.** Month totals in MariaDB are evidence for humans choosing `amount_usdc`; the chain only enforces window + signer + destination + balance.

---

## Recommended instruction shape (later implementation)

### Name

`pay_partner_usdc(amount_usdc: u64)`

Prefer a **direct** instruction name that cannot be confused with books “pay” language in the UI.

### Accounts (sketch)

| Account | Notes |
|---|---|
| `config` | `["config"]` — mint, treasury ATA, owner |
| `partner_share` | `["partner_share", partner_wallet]` — must match wallet, live window |
| `partner_book` | `["partner_book"]` — optional consistency check that the same window is listed |
| `treasury_authority` | `["treasury"]` PDA signer |
| `treasury_usdc` | `config.treasury_usdc` |
| `partner_usdc` | ATA of `partner_share.wallet`, mint = config mint |
| `token_program` | SPL Token |
| `authority` | Signer — see authority rules below |
| `governance` | Unchecked; empty ⇒ pre-governance direct path; initialized ⇒ refuse direct path |

### Authority rules

| Governance | Who may call `pay_partner_usdc` |
|---|---|
| Off (`governance` account empty) | `config.owner` only (same posture as `withdraw_treasury` / `set_partner_share`) |
| On | **Refuse** the direct ix (`GovernanceActive`). Use a new proposal kind (below) |

Do **not** allow the partner wallet to self-pull treasury in v1. That would need an on-chain accrued balance PDA (like musician `pending_claims_usdc`). Accrual PDAs are a later phase; until then, payout is principal- or governance-approved with an explicit amount.

### Governance extension (when governance is on)

| Constant | Value | Meaning |
|---|---|---|
| `PROPOSAL_PARTNER_PAYOUT` | `3` | New kind beside withdraw (0), agent (1), signers (2) |

- `propose`: `amount_usdc > 0`, `target` = partner **wallet** (or partner USDC ATA — pick one and document; recommend **wallet** pubkey, resolve ATA at execute like other ATA constraints).
- `execute_partner_payout_proposal`: same readiness checks as other executes; load `PartnerShare` for that wallet; require live window; `treasury_out` → partner ATA; mark proposal executed.
- Leave kinds 0–2 and existing execute handlers **byte-compatible**; only add a branch / new execute ix.

### Errors (new)

| Error | When |
|---|---|
| `PartnerShareInactive` | Missing account, `share_bps == 0`, or outside `[starts_at, ends_at)` |
| `PartnerDestinationMismatch` | Destination ATA owner ≠ `partner_share.wallet` |
| (reuse) `ZeroAmount`, `InsufficientTreasury`, `GovernanceActive`, `Unauthorized` | Existing |

### What stays untouched

- `treasury_out` body
- `WithdrawTreasury` / `ExecuteWithdrawProposal` account metas and destination-is-signer rule for kind 0
- Booking escrow settle, vault `claim_royalties`, academy/geo/sync fee paths
- MariaDB books schema (optional later column `payout_sig` / `paid_at` is off-chain only)

---

## Why Anchor is not implemented in APP-77-2

| Reason | Detail |
|---|---|
| Missing dependency in this tree | This ticket branch starts from APP-75-1 (v0.10.0). `PartnerShare` / `set_partner_share` live on epic 74 (v0.11.0). Implementing payout without those PDAs would invent a parallel window or skip the acceptance criterion. |
| Not small once governance is considered | Safe production path needs `PROPOSAL_PARTNER_PAYOUT` + execute ix + tests alongside the direct owner path — larger than a one-file patch. |
| Blast radius | Any mistake in shared `propose` / `treasury_out` wiring risks breaking live treasury withdraw — ticket priority forbids that. |
| Journey / epic note | Prefer documentation before new payout code; do not ship partner auto-drain. |

**Acceptance for this ticket:** this design file under `4.anchor/eh8s-devnet/`, checklist complete, no program upgrade, no deploy.

---

## Off-chain / UI contract (future, not this ticket)

| Surface | Rule |
|---|---|
| Partner books UI | Continue: edit shares and allocations in MariaDB only; label that books do not send USDC |
| Studio power | Continues to set on-chain windows via `set_partner_share` (principal; refused after governance until a studio-power proposal kind exists — see APP-77-1 ops note) |
| Future “Pay partner USDC” | Owner (or governance proposal) screen; wallet signs `pay_partner_usdc` / execute; amount chosen from books as **human evidence**, not as a program argument derived from SQL |
| Backend | After confirmed signature: verify ix + `PartnerShare` accounts (epic 74/75 style), then mark allocation paid — never the reverse order |

---

## Test plan (when a later ticket implements)

1. **Regression (must pass unchanged):** existing `tests-program/tests/treasury.rs` withdraw + governance withdraw proposal; booking settle; `claim_royalties`.
2. **Window gate:** payout fails if no `PartnerShare`, if `share_bps == 0`, if before `starts_at` or at/after `ends_at` (open-ended `ends_at = 0` still allowed when started).
3. **Destination:** fails if ATA owned by a stranger; succeeds for partner ATA.
4. **Governance:** direct `pay_partner_usdc` fails when governance exists; proposal kind 3 execute succeeds after threshold; kind 0 withdraw still works for signer destinations.
5. **Books isolation:** no new Spring path that submits a payout tx from a books edit; unit test or review note that `savePartner` / allocation writes do not call Solana send.

---

## Operator summary

1. Set (or keep) an on-chain partner window with **Set partner share** / `set_partner_share` while governance allows it.  
2. Keep MariaDB books for month math and transparency — they never move money.  
3. When paying: use the **future** partner payout instruction (or governance proposal kind 3), not treasury “Withdraw to my wallet” unless the destination is intentionally a governance signer under kind 0.  
4. Do not implement or deploy that instruction from APP-77-2; land it only after v0.11.0 partner PDAs are on the branch and the test plan above is green.

---

## References

- Epic 74: `grant_studio_role`, `revoke_studio_role`, `set_partner_share`, `PartnerShare`, `PartnerBook` (v0.11.0 source).
- Epic 77 journey: partner payout designed only after on-chain shares exist; never “edit books = send money.”
- Ops multisig practice: `OPS_MULTISIG_2OFN.md` (APP-77-1) — treasury withdraw vs studio power labels.
- Live money paths to preserve: `withdraw_treasury`, `execute_withdraw_proposal`, `settle_booking`, `claim_royalties`.
