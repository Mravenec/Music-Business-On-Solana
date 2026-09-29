# 0.database — MariaDB schema

Six MariaDB schemas, one idempotent SQL file each. `canonical_sql/00_run_all.sql` loads them in foreign-key order.

| Schema | Owns |
|---|---|
| `eh8s` (hub) | accounts, musician profiles, Enigma levels, bands, SPP cycles and scores, venues, bookings, concerts, settlements, claims |
| `eh8s_academy` | academy plans and subscriptions, courses, sections, lessons, progress, instructors, evaluations |
| `eh8s_catalog` | tracks, royalty splits and deposits, sync licenses, geo tiers and regions, zone subscriptions, tour plans |
| `eh8s_onchain` | chain config, treasury withdrawals, governance, payment markers (every row carries its DevNet signature) |
| `eh8s_ops` | the 12 ops agents, agent events, owner decisions, Slack delivery log, agent authorities |
| `eh8s_role` | role grants and role applications (student, musician, instructor, venue) |

```bash
cp 0.database/.env.example 0.database/.env      # container name, port, database name
python 0.database/scripts/ensure_db.py          # create or start the MariaDB container (Docker)
python 0.database/scripts/setup_db.py           # ensure + load canonical_sql/00_run_all.sql
python 0.database/scripts/drop_db.py            # wipe the database (destructive)
```

There is no demo data. Seed rows are product catalog only (instruments, Enigma levels, academy plans, contract types, SPP weights, geo tiers, the ops agents and the platform owner). Users, bands, venues and payments are created in the app with DevNet wallets.

The JOOQ code in `1.backend/eh8s` is generated from this schema. The paste-ready diagram is [`3.diagrams/0.database/dbdiagram/schema.dbml`](../3.diagrams/0.database/dbdiagram/schema.dbml) (open it at [dbdiagram.io](https://dbdiagram.io)).
