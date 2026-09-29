-- EH8S canonical schema: runs every schema file in dependency order.
--
-- canonical_sql/ holds exactly one file per MariaDB schema, mirroring the JOOQ
-- packages in 1.backend/eh8s/src/main/java/com/eh8s/eh8s/database/jooq/<schema>/.
-- Each file is idempotent (CREATE ... IF NOT EXISTS plus guarded ALTERs), so
-- running it again on an existing database is safe.
--
-- Order respects cross-schema foreign keys: eh8s (hub) first; eh8s_academy,
-- eh8s_catalog, eh8s_ops and eh8s_role next (each depends only on eh8s);
-- eh8s_onchain last (depends on eh8s.pending_claim and
-- eh8s_academy.academy_subscription).
--
-- No demo data: users, bands, venues and payments are created in the app with
-- DevNet wallets. Seed rows here are product catalog only (instruments, levels,
-- plans, contract types, SPP weights, geo tiers, the 12 ops agents).

SOURCE eh8s/eh8s.sql;
SOURCE eh8s_academy/eh8s_academy.sql;
SOURCE eh8s_catalog/eh8s_catalog.sql;
SOURCE eh8s_ops/eh8s_ops.sql;
SOURCE eh8s_role/eh8s_role.sql;
SOURCE eh8s_onchain/eh8s_onchain.sql;
