# dbdiagram.io visual twin

One file: **[schema.dbml](./schema.dbml)** — DBML for [dbdiagram.io](https://dbdiagram.io).

## Engineer (copy-paste)

1. Open [https://dbdiagram.io](https://dbdiagram.io)
2. Clear the editor (or new diagram)
3. Copy **all** of `schema.dbml` → paste
4. Review tables / FKs → make schema decisions

No local server required. Runtime DB remains MariaDB via `canonical_sql/`.

## Agents (mandatory)

Whenever you add or change DDL under `canonical_sql/`:

1. Update `schema.dbml` in the **same** checklist item / PR
2. Mirror tables, PKs, FKs, and important unique indexes
3. Do not invent tables that are not in SQL
4. Keep one paste-ready file (no split across many DBML files)

This folder was **moved** from `0.database/dbdiagram/` (not duplicated). Runtime SQL stays in `0.database/canonical_sql/`.

Rules: `.ai/rules/dbdiagram.md` · `.ai/rules/diagrams.md` · Skill: `.ai/skills/dbdiagram/SKILL.md`
