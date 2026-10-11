# 0.database

Day zero: no domain yet. Set real names in `.env` when you know them (copy from `.env.example`).

```bash
python 0.database/scripts/ensure_db.py   # create container if missing, start if stopped
python 0.database/scripts/setup_db.py    # ensure + load canonical_sql/  (judge / live demo path)
python 0.database/scripts/drop_db.py     # wipe DB — only with human OK
```

**Hackathon / production demo:** run `setup_db.py` only. **Never** run `scripts/load_mocks.py`. Demo users, bands, and payments are created in the app with **DevNet wallets**. `mocks/` is leftover fixture SQL and is not part of the judge path.

Lookup rows in `canonical_sql` (instruments, academy plans, geo tiers, owner bootstrap wallet) are product config, not fake money.

| Config (`.env`) | Role |
|---|---|
| `MARIADB_DATABASE` | Real database name |
| `CONTAINER_NAME` | Docker container name |
| `HOST_PORT` | Host port (default **3306**) |

Put DDL in `canonical_sql/` — one new numbered file per DB ticket (`NN_<slug>.sql`), never pasted into `00_run_all.sql` directly. `00_run_all.sql` is a thin `SOURCE` list, not the DDL itself — it only ever gains one new `SOURCE NN_<slug>.sql;` line per ticket (`.ai/rules/database-ddl-files.md`). JOOQ reads the **live** DB after setup. Do **not** treat `1.backend/.../src/main/resources` as the schema source of truth.

## Visual schema (dbdiagram.io)

DBML lives under **`3.diagrams/0.database/dbdiagram/`** (moved — not duplicated here).

| File | Purpose |
|---|---|
| [`../3.diagrams/0.database/dbdiagram/schema.dbml`](../3.diagrams/0.database/dbdiagram/schema.dbml) | Paste-ready DBML for [dbdiagram.io](https://dbdiagram.io) |

**Rule:** every `canonical_sql/` change updates that `schema.dbml` in the same checklist item (`.ai/rules/dbdiagram.md` · `.ai/rules/diagrams.md`).
