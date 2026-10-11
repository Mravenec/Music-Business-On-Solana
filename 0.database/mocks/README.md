# Mocks — demo/test data, never production

```bash
python 0.database/scripts/load_mocks.py    # after setup_db.py — dev/test only
```

**One single file, `mocks.sql`**, for every schema's mock rows — matching
`odsProject`'s real `21. ods_mocks.sql` (not one file per schema, per epic
28's earlier shape; consolidated in epic 29). It's organized as one section
per schema, each opening with its own `USE <schema>;`, in dependency order
(`eh8s` hub first, then `eh8s_catalog`/`eh8s_ops` which reference its rows).
**Never** run against production — `00_run_all.sql` / `setup_db.py` never
touch this folder; production only ever gets real catalog data
(`canonical_sql/`).

## Catalog vs. mock — the rule

A table's seed row belongs in `canonical_sql/<schema>/` (ships to production)
when the app's own logic depends on it existing — pricing plans, scoring
weights, a fixed taxonomy, the real 12-agent ops roster, bootstrap config like
the one active `chain_config` row, the platform owner account.

A row belongs here in `mocks/` when it's a specific demo scenario built for
testing — the "Noche Oscura" fake band and everything hanging off it (its
musicians, rehearsals, SPP scores, booking, concert, royalty splits), the one
fake pending `owner_decision`. See `canonical_sql/MIGRATION_MAP.md`'s
catalog/mock table for the full audited list, and
`.ai/rules/database-mocks-separation.md` for the general rule (pattern:
`odsProject`'s own `load_mocks.py` — read-only reference, copy the mechanic,
never edit that project).

## Adding a new mock row

Add it to `mocks.sql`, under the section for whichever schema the table
lives in (create a new `USE <schema>;` section if that schema doesn't have
one yet, placed so it runs after any schema its rows reference). Same
idempotent style as `canonical_sql/` — `INSERT ... ON DUPLICATE KEY UPDATE`
or a guarded `INSERT ... SELECT ... WHERE NOT EXISTS` — `load_mocks.py` can
run more than once safely.
