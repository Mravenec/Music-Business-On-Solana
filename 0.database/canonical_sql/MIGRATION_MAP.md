# canonical_sql migration map

`00_run_all.sql` used to be one ~1,477-line script holding the DDL/seed data for
all 24 epics. This document is the historical record of that retrofit: it says
which file owns which tables/columns/indexes and why, so future epics never have
to re-derive this by reading the old monolith's git history again.

Going forward: **one growing file per schema, edited in place** — see
`.ai/rules/database-ddl-files.md` and `.ai/rules/database-multi-schema.md`.
A ticket that changes an existing schema's table appends an idempotent
`ALTER TABLE` block to that schema's one file (see the pattern used
throughout the file below) — it does **not** get a new numbered file the way
tickets did before epic 29 (see "File layout (epic 29)" below for why that
changed).

## File layout (epic 29 — current)

`canonical_sql/` holds **one folder per MariaDB schema**, each with
**exactly one `.sql` file** (`eh8s/eh8s.sql`, `eh8s_academy/eh8s_academy.sql`,
`eh8s_catalog/eh8s_catalog.sql`, `eh8s_onchain/eh8s_onchain.sql`,
`eh8s_ops/eh8s_ops.sql`, `eh8s_role/eh8s_role.sql`), mirroring
`1.backend/eh8s/.../database/jooq/<schema>/` 1:1. This consolidates epic 28's
folder-per-schema-with-per-ticket-files layout (below) into the stricter
shape actually verified in `odsProject`'s real
`0.database/propuesta_actual/`: 19 real schemas, 19 flat files, and a `grep`
for the idempotent-`ALTER` signature (`information_schema.COLUMNS` / `ADD
COLUMN`) across every one of them returns **zero hits** — no schema file
there has ever carried per-ticket history. A ticket touching more than one
schema edits that many files (its schemas' existing ones), never a single
file switching `USE` mid-way.

Mocks/demo data likewise consolidated from 3 per-schema files
(`mocks/eh8s.sql`, `mocks/eh8s_catalog.sql`, `mocks/eh8s_ops.sql`) into a
single `mocks/mocks.sql`, `USE <schema>;` per section — matching
`odsProject`'s own single `21. ods_mocks.sql`. See `0.database/mocks/README.md`
and `.ai/rules/database-mocks-separation.md`.

## Folder layout (epic 28 — superseded by epic 29 above, kept for provenance)

`canonical_sql/` was organized as **one folder per MariaDB schema**
(`eh8s/`, `eh8s_academy/`, `eh8s_catalog/`, `eh8s_onchain/`, `eh8s_role/`,
`eh8s_ops/`), mirroring `1.backend/eh8s/.../database/jooq/<schema>/` 1:1 —
matching `odsProject`'s own read-only-reference convention of a clear,
dedicated DDL location per real database. Each schema's own folder held its
per-ticket files, numbered locally (`eh8s/01_foundation.sql`,
`eh8s/02_academy_core.sql`, …) — the ticket-file discipline from epic 25
(new file per ticket, idempotent `ALTER`, never edit a landed file) applied,
just correctly scoped under the schema it actually touches instead
of at the flat `canonical_sql/` root. A ticket that touched more than one
schema got **one file per schema** (e.g. epic 14's wallet/DevNet-pay ticket
was `eh8s_onchain/02_wallet_devnet_pay.sql` +
`eh8s/06_wallet_devnet_pay_claim.sql` +
`eh8s_academy/03_wallet_devnet_pay.sql`) instead of one file switching
schema mid-file. Epic 29 re-checked `odsProject`'s actual files (not just its
folder names) and found this per-ticket-file discipline wasn't what the real
reference project does once multi-schema — so it consolidated each folder's
files into the one-file-per-schema shape above. This section is kept because
the schema-map and file→epic tables below still describe DDL provenance in
terms of these (now-merged) file names.

## Schema map (epics 26 + 27)

Six physical MariaDB schemas on the same server, per the odsProject-pattern
JOOQ multi-schema config (`.ai/rules/database-multi-schema.md` once ported to
the neutral base; see `.docs/journey/26-multi-schema-ops.md` and
`.docs/journey/27-multi-schema-domains.md` for the full rationale). `eh8s` is
the hub — tables referenced from many other domains (`account`,
`musician_profile`, `band`, `venue`, `booking`, `concert`, …). Every satellite
schema has minimal, almost entirely one-directional outbound FKs back into the
hub — verified against `information_schema.KEY_COLUMN_USAGE`, not guessed.
Several files explicitly switch schema **mid-file** (`USE <schema>;` more than
once) rather than being split/renumbered, since a single historical ticket
file sometimes touches tables that now belong to more than one schema — no
file ever relies on whatever the previously-`SOURCE`d file left active:

| Schema | File (epic 29) | Was (epic 28) | Tables |
|---|---|---|---|
| `eh8s` (hub, unchanged name) | `eh8s/eh8s.sql` | 8 files (`01_foundation` … `08_settle_claim_onchain_devnet`) | `app_meta`, `account`, `musician_profile`, `instrument`, `enigma_level`, `band`, `band_member`, `spp_variable`, `spp_cycle`, `spp_member_score`, `rehearsal_session`, `rehearsal_checkin`, `contract_type`, `venue`, `booking`, `concert`, `concert_expense`, `concert_settlement`, `pending_claim` (19) |
| `eh8s_academy` | `eh8s_academy/eh8s_academy.sql` | 4 files | `instructor_profile`, `academy_plan`, `academy_subscription`, `enigma_evaluation` (4) |
| `eh8s_catalog` | `eh8s_catalog/eh8s_catalog.sql` | 2 files | `royalty_type`, `track`, `royalty_split`, `royalty_deposit`, `channel_format`, `channel_piece`, `geo_tier`, `geo_region`, `geographic_subscription`, `tour_plan` (10) |
| `eh8s_onchain` | `eh8s_onchain/eh8s_onchain.sql` | 5 files | `chain_config`, `devnet_pay_marker` (2) |
| `eh8s_role` | `eh8s_role/eh8s_role.sql` | 1 file | `account_role_application`, `account_role` (2) |
| `eh8s_ops` | `eh8s_ops/eh8s_ops.sql` | 5 files | `ops_agent`, `owner_decision`, `agent_event_log`, `slack_delivery_log` (4) |

19+4+10+2+2+4 = 41 ✓.

Cross-schema foreign keys (every one, both epics):

| From | To |
|---|---|
| `eh8s_ops.owner_decision.related_booking_id` | `eh8s.booking (id)` |
| `eh8s_academy.instructor_profile.account_id` | `eh8s.account (id)` |
| `eh8s_academy.academy_subscription.musician_profile_id` | `eh8s.musician_profile (id)` |
| `eh8s_academy.enigma_evaluation.musician_profile_id` | `eh8s.musician_profile (id)` |
| `eh8s_catalog.track.band_id` | `eh8s.band (id)` |
| `eh8s_catalog.royalty_split.musician_profile_id` | `eh8s.musician_profile (id)` |
| `eh8s_catalog.geographic_subscription.band_id` | `eh8s.band (id)` |
| `eh8s_catalog.tour_plan.band_id` | `eh8s.band (id)` |
| `eh8s_onchain.devnet_pay_marker.related_claim_id` | `eh8s.pending_claim (id)` |
| `eh8s_onchain.devnet_pay_marker.related_subscription_id` | `eh8s_academy.academy_subscription (id)` (the one satellite-to-satellite reference) |
| `eh8s_role.account_role_application.account_id` | `eh8s.account (id)` |
| `eh8s_role.account_role.account_id` | `eh8s.account (id)` |

No schema outside `eh8s_onchain` references `eh8s_academy`, `eh8s_catalog`, or
`eh8s_role` — those three are true leaves, same shape as odsProject's own
per-goal schemas.

## Catalog (ships to production) vs. mock (dev/test only)

Audited every seed `INSERT`/`SELECT` statement across every file — not
assumed. See `0.database/mocks/README.md` for the general rule.

| Data | Verdict | Why |
|---|---|---|
| `instrument`, `enigma_level`, `academy_plan`, `contract_type`, `royalty_type`, `channel_format`, `geo_tier`, `geo_region`, `spp_variable` | Catalog — stays in `canonical_sql/` | Fixed product vocabulary/config the app's own logic depends on |
| `ops_agent` (12 named agents) | Catalog — stays | The real agent roster the ops/semaphore feature is built around |
| `chain_config` (1 devnet row) | Catalog — stays | `ChainConfigService` requires at least one `is_active=1` row to function |
| `account` (platform owner, `owner@eh8s.local`) | Catalog — stays | The real owner account the platform's role system depends on |
| `account_role` backfill (`eh8s_role/01_role_apply_approve.sql`) | Stays (not a seed at all) | `INSERT…SELECT` migrating existing `account.role` values — data-migration logic |
| `venue`, `booking`, `concert`, `concert_expense` | Mock — `mocks/mocks.sql` (`eh8s` section) | Specific fake venues/booking tied together for the demo flow |
| `band`, `account` (jorge/valeria/marco/sofia), `musician_profile`, `band_member`, `rehearsal_session`, `rehearsal_checkin`, `spp_cycle`, `spp_member_score` | Mock — `mocks/mocks.sql` (`eh8s` section) | The entire "Noche Oscura" fake band scenario |
| `track`, `royalty_split`, `channel_piece`, `geographic_subscription`, `tour_plan` | Mock — `mocks/mocks.sql` (`eh8s_catalog` section) | Tied to the same fake band/catalog demo scenario |
| `owner_decision` (1 row) | Mock — `mocks/mocks.sql` (`eh8s_ops` section) | A specific fake pending decision tied to the fake booking |

(Epic 29 consolidated `mocks/eh8s.sql` + `mocks/eh8s_catalog.sql` +
`mocks/eh8s_ops.sql` into the single `mocks/mocks.sql` above — same rows,
same dependency order, one file.)

## Epic 28 → epic 29 cross-reference

Every one of epic 28's per-ticket files (below) was merged, in the same
order it ran in `00_run_all.sql`, into its schema's single file — pure
concatenation, verified statement-for-statement identical (comments/blank
lines/repeated `USE` statements aside):

| Schema | Epic 28 files (now merged, no longer exist) | Epic 29 file |
|---|---|---|
| `eh8s` | `01_foundation.sql`, `02_academy_core.sql`, `03_bands_spp.sql`, `04_venues_concerts.sql`, `05_product_shell.sql`, `06_wallet_devnet_pay_claim.sql`, `07_role_platform_account.sql`, `08_settle_claim_onchain_devnet.sql` | `eh8s/eh8s.sql` |
| `eh8s_academy` | `01_academy.sql`, `02_academy_product.sql`, `03_wallet_devnet_pay.sql`, `04_academy_onchain_devnet.sql` | `eh8s_academy/eh8s_academy.sql` |
| `eh8s_catalog` | `01_catalog_geo.sql`, `02_royalty_geo_onchain_devnet.sql` | `eh8s_catalog/eh8s_catalog.sql` |
| `eh8s_onchain` | `01_solana_config.sql`, `02_wallet_devnet_pay.sql`, `03_anchor_devnet.sql`, `04_role_platform_chain.sql`, `05_anchor_program_devnet.sql` | `eh8s_onchain/eh8s_onchain.sql` |
| `eh8s_ops` | `01_ops_owner.sql`, `02_owner_product.sql`, `03_agent_events.sql`, `04_slack_bridge.sql`, `05_slack_owner_dirigent.sql` | `eh8s_ops/eh8s_ops.sql` |
| `eh8s_role` | `01_role_apply_approve.sql` | `eh8s_role/eh8s_role.sql` |

`00_run_all.sql`'s `SOURCE` order collapsed from 25 lines to 6, respecting
the same cross-schema FK dependencies the interleaved order protected:
`eh8s` (hub) first; `eh8s_academy`/`eh8s_catalog`/`eh8s_ops`/`eh8s_role` next
(each only depends on `eh8s`); `eh8s_onchain` last (the one schema depending
on both `eh8s.pending_claim` and `eh8s_academy.academy_subscription` — see
the cross-schema FK table above).

## File → epic map (historical — pre-epic-28 flat file names)

Load order follows dependency order (a file can only be sourced after every file
whose tables it references via FK), which is not always ascending epic number —
e.g. epic 13's file loads before epic 8's, and epic 21's file loads before
epic 15's, because of when those epics actually landed relative to each other in
the original monolith. **These flat filenames no longer exist** (epic 28
relocated every one of them into its schema's folder, see the schema map
above) — this table is kept as the historical record of which epic each
table/column/index came from; for where something lives *today*, use the
schema map, not this table. Old flat name → new path, for cross-reference:

| Old (flat) | New (epic 28) |
|---|---|
| `01_foundation.sql` | `eh8s/01_foundation.sql` |
| `02_academy.sql` | `eh8s/02_academy_core.sql` + `eh8s_academy/01_academy.sql` |
| `03_bands_spp.sql` | `eh8s/03_bands_spp.sql` |
| `04_venues_concerts.sql` | `eh8s/04_venues_concerts.sql` |
| `05_catalog_geo.sql` | `eh8s_catalog/01_catalog_geo.sql` |
| `06_ops_owner.sql` | `eh8s_ops/01_ops_owner.sql` |
| `07_owner_product.sql` | `eh8s_ops/02_owner_product.sql` |
| `08_solana_config.sql` | `eh8s_onchain/01_solana_config.sql` |
| `09_product_shell.sql` | `eh8s/05_product_shell.sql` |
| `10_academy_product.sql` | `eh8s_academy/02_academy_product.sql` |
| `11_wallet_devnet_pay.sql` | `eh8s_onchain/02_wallet_devnet_pay.sql` + `eh8s/06_wallet_devnet_pay_claim.sql` + `eh8s_academy/03_wallet_devnet_pay.sql` |
| `12_academy_onchain_devnet.sql` | `eh8s_academy/04_academy_onchain_devnet.sql` |
| `13_agent_events.sql` | `eh8s_ops/03_agent_events.sql` |
| `14_slack_bridge.sql` | `eh8s_ops/04_slack_bridge.sql` |
| `15_anchor_devnet.sql` | `eh8s_onchain/03_anchor_devnet.sql` |
| `16_role_platform_shell.sql` | `eh8s/07_role_platform_account.sql` + `eh8s_onchain/04_role_platform_chain.sql` |
| `17_role_apply_approve.sql` | `eh8s_role/01_role_apply_approve.sql` |
| `18_anchor_program_devnet.sql` | `eh8s_onchain/05_anchor_program_devnet.sql` |
| `19_settle_claim_onchain_devnet.sql` | `eh8s/08_settle_claim_onchain_devnet.sql` |
| `20_royalty_geo_onchain_devnet.sql` | `eh8s_catalog/02_royalty_geo_onchain_devnet.sql` |
| `21_slack_owner_dirigent.sql` | `eh8s_ops/05_slack_owner_dirigent.sql` |

| File | Epic | Tables / DDL owned |
|---|---|---|
| `01_foundation.sql` | 1 | `account`, base identity/auth tables |
| `02_academy.sql` | 2 | `academy_subscription`, academy catalog tables |
| `03_bands_spp.sql` | 3 | `band`, `musician_profile`, `spp_cycle` and related SPP tables |
| `04_venues_concerts.sql` | 4 | `contract_type`, `venue`, `booking`, `concert`, `concert_expense`, `concert_settlement`, `pending_claim` |
| `05_catalog_geo.sql` | 5 | `royalty_type`, `track`, `royalty_split`, `royalty_deposit`, `channel_format`, `channel_piece`, `geo_tier`, `geo_region`, `geographic_subscription`, `tour_plan` |
| `06_ops_owner.sql` | 6 | `ops_agent`, `owner_decision` |
| `07_owner_product.sql` | 13 | Owner/product columns added to tables from earlier epics (`owner_decision.reject_reason`/`resolved_at`, `ops_agent.task_status`) |
| `08_solana_config.sql` | 7 | `chain_config` (BagsCreatorFund dual-network client settings) |
| `09_product_shell.sql` | 8 | Product-shell wallet session columns on `account` |
| `10_academy_product.sql` | 9 | `academy_subscription` on-chain intent column (`intended_instruction`) |
| `11_wallet_devnet_pay.sql` | 14 | Wallet/devnet payment tables |
| `12_academy_onchain_devnet.sql` | 21 | Academy on-chain devnet linkage |
| `13_agent_events.sql` | 15 | `agent_event_log` |
| `14_slack_bridge.sql` | 16 | `slack_delivery_log` base table (owner-scope columns below belong to epic 24, not this file — see note) |
| `15_anchor_devnet.sql` | 17 | Anchor devnet program tables |
| `16_role_platform_shell.sql` | 18 | Role/platform shell tables |
| `17_role_apply_approve.sql` | 19 | Role apply/approve flow |
| `18_anchor_program_devnet.sql` | 20 | Anchor program devnet tables |
| `19_settle_claim_onchain_devnet.sql` | 22 | `concert.concert_settlement_pda`; `concert_settlement.settle_tx_signature`/`settled_at`/`on_chain_status` + `uk_settlement_tx`; `pending_claim.claim_pda`/`tx_signature`/`claimed_at`/`claimer_wallet_pubkey`/`on_chain_status` + `uk_claim_tx`/`uk_claim_pda` |
| `20_royalty_geo_onchain_devnet.sql` | 23 | `royalty_deposit.royalty_pool_pda`/`deposit_tx_signature`/`deposited_at`/`payer_wallet_pubkey`/`on_chain_status` + `uk_deposit_tx`/`uk_deposit_pool_pda`; `geographic_subscription.pay_tx_signature`/`paid_at`/`payer_wallet_pubkey`/`on_chain_status` + `uk_geosub_pda`/`uk_geosub_tx` |
| `21_slack_owner_dirigent.sql` | 24 | `slack_delivery_log.owner_wallet_pubkey`/`requester_wallet_pubkey`/`bridge_mode` + `idx_slack_owner_wallet`, backfill UPDATE |

Epics with no DDL gap in the original file (no schema change of their own — either
pure application logic or covered entirely by a sibling epic's file): **11, 12**.

## Retrofit notes: where later epics had folded into earlier epics' base tables

The original monolith wasn't always additive-only. In four cases a later epic's
columns/indexes had been merged directly into an earlier epic's `CREATE TABLE`
instead of arriving through a clean `ALTER TABLE`. Splitting the file correctly
meant giving each epic back its own DDL rather than mechanically cutting at
comment boundaries:

- **`royalty_deposit` / `geographic_subscription`** (epic 5 base tables): epic 23's
  on-chain columns (`royalty_pool_pda`, `deposit_tx_signature`, …,
  `pay_tx_signature`, `payer_wallet_pubkey`, …) had been baked into the epic-5
  `CREATE TABLE`. Fixed by stripping them from `05_catalog_geo.sql` and writing a
  new idempotent `ALTER TABLE` block for them (with their unique keys) in
  `20_royalty_geo_onchain_devnet.sql`.
- **`ops_agent` / `owner_decision`** (epic 6 base tables): epic 13's
  `task_status` / `reject_reason` / `resolved_at` columns had been folded in.
  Fixed the same way in `06_ops_owner.sql` (stripped) /
  `07_owner_product.sql` (adds them back).
- **`chain_config`** (epic 7 base table): `owner_wallet_pubkey` /
  `protocol_fee_bps` belonged to a later epic; stripped from
  `08_solana_config.sql`.
- **`slack_delivery_log`** (epic 16 base table): epic 24's
  `owner_wallet_pubkey` / `requester_wallet_pubkey` / `bridge_mode` columns and
  `idx_slack_owner_wallet` key had been folded into the epic-16 `CREATE TABLE`.
  Fixed by stripping them from `14_slack_bridge.sql` and restoring epic 24's own
  `ALTER TABLE` block (with backfill) in `21_slack_owner_dirigent.sql`. Because
  the index is now added by a later `ALTER` instead of being declared inline on
  the base table, `SHOW CREATE TABLE` lists `idx_slack_owner_wallet` *after* the
  two foreign-key-generated keys instead of before them — a harmless
  ordering-only cosmetic difference versus the pre-retrofit schema (same columns,
  same keys, same constraints); it is unavoidable under the
  never-edit-a-landed-file convention and was accepted as-is.

Also fixed during the retrofit (bookkeeping, not schema content):
- Epic 9's block had no distinguishing comment header in the original file and had
  been silently absorbed into what looked like the epic-8 section; it now has its
  own file (`10_academy_product.sql`).
- Epic 23's and epic 24's boundary had drifted during an earlier merge conflict
  resolution in this repo's history; epic 24's own header comment (and its file
  boundary) was restored from `git log -S` archaeology on the original file.

## Verification performed (epic 25 retrofit)

All 21 files were loaded into a temporary database (`eh8s_verify_new`) via the new
`00_run_all.sql`, and the original monolith was loaded into a second temporary
database (`eh8s_verify_old`) from the pre-retrofit `00_run_all.sql` (git history).
`SHOW TABLES` and `SHOW CREATE TABLE` per table were compared between the two:
all 41 tables present in both, byte-for-byte identical column/index/constraint
definitions except the one cosmetic key-ordering note on `slack_delivery_log`
above. Both temporary databases were dropped after verification; the real `eh8s`
/ `eh8s_devnet` databases were never touched by this comparison.

## Verification performed (epic 29 consolidation)

Statement-level check (comments/blanks/repeated `USE` lines stripped): every
merged schema file's remaining lines compared, in order, against the
concatenation of its epic 28 source files — identical for all 6 schemas plus
the 3-into-1 mocks merge. See the epic's ticket history for the temp-DB
`SHOW CREATE TABLE` diff result (same discipline as epic 28's own
verification: expect zero structural differences, since this is a pure file
merge with no DDL change).
