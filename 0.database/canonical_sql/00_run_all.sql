-- EH8S canonical schema — thin SOURCE orchestrator.
-- Epic 29: canonical_sql/ holds exactly ONE file per real MariaDB schema
-- (eh8s/eh8s.sql, eh8s_academy/eh8s_academy.sql, eh8s_catalog/eh8s_catalog.sql,
-- eh8s_onchain/eh8s_onchain.sql, eh8s_ops/eh8s_ops.sql, eh8s_role/eh8s_role.sql),
-- mirroring 1.backend/eh8s/.../database/jooq/<schema>/ 1:1 — matching
-- odsProject's real convention (one flat file per schema, verified zero
-- per-ticket ALTER history in any of its 19 schema files). Epic 28's
-- per-ticket-file-per-schema-folder layout is now consolidated into this —
-- see .ai/rules/database-multi-schema.md and MIGRATION_MAP.md.
--
-- A schema's file is edited IN PLACE for every future ticket, using the
-- idempotent-ALTER pattern (.ai/rules/database-ddl-files.md) — never a new
-- numbered file. Never paste DDL here in 00_run_all.sql itself.
--
-- SOURCE order below respects cross-schema FK dependencies: eh8s (hub) first;
-- eh8s_academy/eh8s_catalog/eh8s_ops/eh8s_role next (each only depends on
-- eh8s); eh8s_onchain last (depends on BOTH eh8s.pending_claim and
-- eh8s_academy.academy_subscription). See MIGRATION_MAP.md for the full
-- cross-schema FK map.
--
-- Mocks/demo data are NOT here — see 0.database/mocks/mocks.sql +
-- scripts/load_mocks.py. Production only ever runs this file.

SOURCE eh8s/eh8s.sql;
SOURCE eh8s_academy/eh8s_academy.sql;
SOURCE eh8s_catalog/eh8s_catalog.sql;
SOURCE eh8s_ops/eh8s_ops.sql;
SOURCE eh8s_role/eh8s_role.sql;
SOURCE eh8s_onchain/eh8s_onchain.sql;
SOURCE 22_onchain_studio_role_partner_share_mirrors.sql;
SOURCE 23_lesson_content_hash_storage_ref.sql;
