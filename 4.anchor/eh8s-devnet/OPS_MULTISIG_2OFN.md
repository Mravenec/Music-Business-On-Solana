# Ops practice: 2-of-N governance for treasury and studio power

English operator note for EH8S DevNet (and the same UI labels on a later network).
**No private keys, seed phrases, JWT secrets, or keypair files belong in this document or in git.**

This note matches the live owner UI labels under Vite (`/owner/governance`, `/owner/treasury`, `/owner/studio-power`).
Program behavior is described as implemented in `programs/eh8s_devnet` (governance v0.8.0+; studio role / partner share v0.11.0 source).

---

## Goal

Keep day-to-day booking, settle, claim, academy confirm, and studio-admin **read** paths working, while making **money movement** and **studio power mutations** require more than one trusted person.

Recommended production posture: **2-of-N** (commonly **2-of-3**).

| Role | Custody | Purpose |
|---|---|---|
| Principal wallet (`config.owner`) | Hot enough to run the Owner inbox; not the only copy of authority once governance is on | Signs `init_governance` once; remains a governance signer when listed |
| Cold signer A | Offline / hardware; never in repo | Second approval for treasury (and later studio) proposals |
| Cold signer B (optional spare) | Offline / hardware | Rotation and loss recovery; keeps N ≥ 3 so one cold key can be replaced |

Use **Approvals needed = 2** on **Set up signers**. Do not leave threshold at 1 in production.

---

## UI map (exact labels)

| Screen | Route | Kicker / title | Primary actions |
|---|---|---|---|
| Signer approvals | `/owner/governance` | **Governance** · **Signer approvals** | **Set up signers** · **Open** · **New proposal** |
| Set up signers | `/owner/governance/setup` | **Governance** · **Set up signers** | **Signer wallets (one per line)** · **Approvals needed** · **Sign setup** |
| New proposal | `/owner/governance/new` | **Governance** · **New proposal** | **Withdraw treasury USDC** · **Agent powers** · **Change signers** → **Choose** |
| One proposal | `/owner/governance/proposals/:id` | **Proposal #…** · card title | **Approve** · **Execute** |
| Treasury | `/owner/treasury` | **Treasury** | Before governance: **Withdraw to my wallet**. After: **Propose a withdrawal** |
| Studio power | `/owner/studio-power` | **Studio** · **Studio power** | **Grant studio admin** · **Set partner share** · **Revoke** / **Change** |
| Grant studio admin | `/owner/studio-power/grant` | **Studio power** · **Grant studio admin** | **Admin wallet** · **Sign grant** (`grant_studio_role`) |
| Revoke studio admin | `/owner/studio-power/revoke` | **Studio power** · **Revoke studio admin** | **Admin wallet** · **Sign revoke** (`revoke_studio_role`) |
| Set partner share | `/owner/studio-power/partner-share` | **Studio power** · **Set partner share** | **Sign partner share** (`set_partner_share`; **0%** ends a live share) |

Non-principal wallets see **Principal wallet only** on Governance and Studio power. Studio admins may read **Partner books**; they cannot open Treasury or mutate on-chain shares.

Proposal card titles use: `Withdraw {n} USDC`, `Grant {code} powers` / `Revoke {code} powers`, `New signer set: {t} of {n}`.

---

## Recommended order of operations

### 1. Finish studio power while governance is still off

While **Signer approvals** says *Governance is off. The owner wallet alone can withdraw and grant agent powers.*, the principal may still sign:

1. Inbox → **Studio power**
2. **Grant studio admin** → paste **Admin wallet** → **Sign grant**
3. **Set partner share** → wallet + percent → sign (`set_partner_share`)
4. Use **Revoke** / **Change** on the lists when needed

**Dual-control practice (human 2-of-N) even before on-chain proposals exist for studio kinds:**

- Person A prepares the wallet and share numbers (out of band).
- Person B confirms the same values against the partner agreement.
- Only then does the principal click **Sign grant** / **Sign revoke** / **Sign partner share**.
- Record who approved (ticket, signed note, or ops log) — never record seed material.

MariaDB / JWT partner forms do **not** move USDC and do **not** rewrite an on-chain partner window. Books follow what the chain confirmed.

### 2. Turn on 2-of-N governance once

1. Connect the principal wallet.
2. Inbox → **Governance** → **Signer approvals** → **Set up signers**.
3. Paste **Signer wallets (one per line)** — include the principal plus cold signers (up to 5).
4. Set **Approvals needed** to **2** (or higher, never above the wallet count).
5. Click **Sign setup** (`init_governance`).
6. Expect *Governance is on: 2 of N signers.*

`init_governance` is **once**. Later changes use **New proposal** → **Change signers**.

### 3. Move treasury only through proposals

After governance is on:

1. **Treasury** shows *Governance is on: withdrawals need signer approvals.* → **Propose a withdrawal**  
   (or **Signer approvals** → **New proposal** → **Withdraw treasury USDC** → **Choose**).
2. A governance signer opens the card → **Approve** until *{count} of {threshold} approvals · ready*.
3. Anyone may click **Execute** once the threshold is met.
4. Destination USDC must be a **signer's** config-mint account (`DestinationNotSigner` otherwise).

Direct **Withdraw to my wallet** and direct agent authorize are refused (`GovernanceActive`).

### 4. Studio power after governance (current program)

Once the governance PDA is initialized, on-chain **direct** paths refuse:

- `grant_studio_role` / `revoke_studio_role` / `set_partner_share` → `GovernanceActive`
- The **Studio power** buttons still appear for the principal, but DevNet signatures for those instructions fail while governance is active.

**Today’s proposal kinds** on **New proposal** are only:

1. **Withdraw treasury USDC**
2. **Agent powers**
3. **Change signers**

There is **no** governance proposal kind yet for grant/revoke `studio_admin` or `set_partner_share`. Operators must therefore:

| Need | Practice |
|---|---|
| Studio admins / partner shares already set | Leave them; they keep working for reads and books mirrors |
| Must change studio power **after** governance | Do **not** deploy ad-hoc bypasses. Either (a) open a **Change signers** path only if you must temporarily lower threshold / rotate (last resort), or (b) wait for a program + UI upgrade that adds studio proposal kinds and execute them like treasury withdraws |
| Must change studio power **before** that upgrade | Keep governance **off** until studio grants/shares are settled, then run **Sign setup** with **Approvals needed = 2** |

Long-term (epic 77 intent): studio power mutations go through the same 2-of-N **Approve** / **Execute** flow as treasury once those proposal kinds exist. Until then, treat step 1 (pre-governance dual control) as the cold-key practice for studio power.

---

## What must not break

Do **not** use governance setup or studio power changes as a reason to pause:

- Booking settle / claim
- Academy confirm
- Studio admin **read** of partner books and own earnings
- Working money paths that do not call withdraw or studio-power mutators

Turning governance on only blocks **direct** treasury withdraw, **direct** agent authorize, and (v0.11.0) **direct** studio-power mutators.

---

## Cold-key checklist (no secrets)

1. Cold signer seeds live only on hardware / sealed recovery — never in git, `.env`, chat, or this folder.
2. Upgrade-authority keypair stays off the product repo (see program README; upgrades need a human OK).
3. Principal Phantom (or similar) is a signer, not the sole production authority after **Sign setup**.
4. **Approvals needed** ≥ 2; verify the banner *{threshold} of {signers} signers must approve*.
5. Withdrawals use **Propose a withdrawal** → **Approve** → **Execute**, not a single hot wallet.
6. Studio admin grants and partner shares are dual-controlled and preferably finished **before** governance; after governance, expect `GovernanceActive` on direct studio mutators.
7. Partner **payout** of USDC is a separate design (epic APP-77-2) — editing books never sends money.
8. Legal entity / custody policy is APP-77-3 — this note is operational practice only.

---

## Quick verify (read-only)

1. Open `/owner/governance` — labels **Signer approvals**, **Set up signers** / **New proposal** match this note.
2. Open `/owner/treasury` — with governance off: **Withdraw to my wallet**; with governance on: **Propose a withdrawal**.
3. Open `/owner/studio-power` — **Grant studio admin**, **Set partner share**, **Revoke**, **Change**.
4. Confirm this file contains no key material: search for `secret`, `PRIVATE`, JSON key arrays, or seed words (should find none).

Do **not** deploy or upgrade the program from this ticket.
