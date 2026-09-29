CREATE DATABASE IF NOT EXISTS eh8s_ops
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE eh8s_ops;

CREATE TABLE IF NOT EXISTS ops_agent (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(80) NOT NULL,
  role_summary VARCHAR(240) NOT NULL,
  semaphore VARCHAR(8) NOT NULL,
  on_chain_permission TINYINT(1) NOT NULL DEFAULT 0,
  wallet_pubkey VARCHAR(44) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_agent_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS owner_decision (
  id BIGINT NOT NULL AUTO_INCREMENT,
  ops_agent_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  semaphore VARCHAR(8) NOT NULL,
  payload VARCHAR(500) NOT NULL,
  status VARCHAR(16) NOT NULL,
  related_booking_id BIGINT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_decision_agent FOREIGN KEY (ops_agent_id) REFERENCES ops_agent (id),
  CONSTRAINT fk_decision_booking FOREIGN KEY (related_booking_id) REFERENCES eh8s.booking (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ──────────────────────────────────────────────────────────
-- Seed rows below are product catalog / bootstrap data, not demo data.
SET @eh8s_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ops_agent' AND COLUMN_NAME = 'task_status'
);
SET @eh8s_sql := IF(@eh8s_col = 0,
  'ALTER TABLE ops_agent ADD COLUMN task_status VARCHAR(32) NOT NULL DEFAULT ''idle'' AFTER semaphore',
  'SELECT 1');
PREPARE eh8s_stmt FROM @eh8s_sql; EXECUTE eh8s_stmt; DEALLOCATE PREPARE eh8s_stmt;

SET @eh8s_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'owner_decision' AND COLUMN_NAME = 'reject_reason'
);
SET @eh8s_sql := IF(@eh8s_col = 0,
  'ALTER TABLE owner_decision ADD COLUMN reject_reason VARCHAR(500) NULL AFTER status',
  'SELECT 1');
PREPARE eh8s_stmt FROM @eh8s_sql; EXECUTE eh8s_stmt; DEALLOCATE PREPARE eh8s_stmt;

SET @eh8s_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'owner_decision' AND COLUMN_NAME = 'resolved_at'
);
SET @eh8s_sql := IF(@eh8s_col = 0,
  'ALTER TABLE owner_decision ADD COLUMN resolved_at DATETIME NULL AFTER reject_reason',
  'SELECT 1');
PREPARE eh8s_stmt FROM @eh8s_sql; EXECUTE eh8s_stmt; DEALLOCATE PREPARE eh8s_stmt;

INSERT INTO ops_agent (code, name, role_summary, semaphore, on_chain_permission, wallet_pubkey) VALUES
  ('NEXUS', 'NEXUS', 'Pedagogical plans and Enigma level', 'green', 1, NULL),
  ('GENESIS', 'GENESIS', 'Onboarding and musician routing', 'green', 0, NULL),
  ('HARMONY', 'HARMONY', 'Band matching and SPP cycles', 'green', 1, NULL),
  ('STAGE', 'STAGE', 'Venue booking pipeline', 'yellow', 1, NULL),
  ('VAULT', 'VAULT', 'Concert settlement and treasury reports', 'green', 1, NULL),
  ('WAVE', 'WAVE', 'Distribution and royalty deposits', 'green', 1, NULL),
  ('PRISM', 'PRISM', 'Channel livestream and VOD', 'green', 0, NULL),
  ('FLUX', 'FLUX', 'Promotion campaigns', 'green', 0, NULL),
  ('SCOUT', 'SCOUT', 'Talent scouting', 'green', 0, NULL),
  ('SHIELD', 'SHIELD', 'Contracts and legal hashes', 'yellow', 0, NULL),
  ('ATLAS', 'ATLAS', 'Tour plans from geo subscriptions', 'green', 0, NULL),
  ('NEXUS-CEO', 'NEXUS-CEO', 'Owner semaphore and daily digest', 'red', 0, NULL)
ON DUPLICATE KEY UPDATE name = VALUES(name), role_summary = VALUES(role_summary), semaphore = VALUES(semaphore);

-- ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS agent_event_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  ops_agent_id BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  semaphore VARCHAR(8) NOT NULL,
  summary VARCHAR(240) NOT NULL,
  detail VARCHAR(1000) NOT NULL,
  owner_decision_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_event_agent FOREIGN KEY (ops_agent_id) REFERENCES ops_agent (id),
  CONSTRAINT fk_event_decision FOREIGN KEY (owner_decision_id) REFERENCES owner_decision (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS slack_delivery_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner_decision_id BIGINT NULL,
  agent_event_id BIGINT NULL,
  channel_hint VARCHAR(80) NULL,
  payload_preview VARCHAR(500) NOT NULL,
  status VARCHAR(32) NOT NULL,
  http_status INT NULL,
  error_message VARCHAR(500) NULL,
  delivered_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_slack_decision FOREIGN KEY (owner_decision_id) REFERENCES owner_decision (id),
  CONSTRAINT fk_slack_event FOREIGN KEY (agent_event_id) REFERENCES agent_event_log (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ──────────────────────────────────────────────────────────
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slack_delivery_log' AND COLUMN_NAME = 'owner_wallet_pubkey'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE slack_delivery_log ADD COLUMN owner_wallet_pubkey VARCHAR(44) NOT NULL DEFAULT ''7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC'' AFTER agent_event_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slack_delivery_log' AND COLUMN_NAME = 'requester_wallet_pubkey'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE slack_delivery_log ADD COLUMN requester_wallet_pubkey VARCHAR(44) NULL AFTER owner_wallet_pubkey',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slack_delivery_log' AND COLUMN_NAME = 'bridge_mode'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE slack_delivery_log ADD COLUMN bridge_mode VARCHAR(32) NOT NULL DEFAULT ''webhook'' AFTER requester_wallet_pubkey',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slack_delivery_log' AND INDEX_NAME = 'idx_slack_owner_wallet'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE slack_delivery_log ADD KEY idx_slack_owner_wallet (owner_wallet_pubkey)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE slack_delivery_log
SET owner_wallet_pubkey = '7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC'
WHERE owner_wallet_pubkey IS NULL OR owner_wallet_pubkey = '';

-- ----------------------------------------------------------
-- Claude owner digest (one row per owner-triggered run).
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS owner_digest (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner_wallet_pubkey VARCHAR(44) NOT NULL,
  model VARCHAR(80) NOT NULL,
  metrics_json LONGTEXT NOT NULL,
  body TEXT NOT NULL,
  input_tokens INT NULL,
  output_tokens INT NULL,
  slack_status VARCHAR(32) NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_owner_digest_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------
-- manual agent roster - AGT-01 NEXUS = Pedagogical (Enigma level, bit 0x01),
-- AGT-02 GENESIS = Onboarding. permission_bits = default on-chain bits for that agent
-- (0x01 pedagogical, 0x02 HARMONY, 0x04 STAGE, 0x08 VAULT, 0x10 WAVE).
-- agent_authority = one row per verified authorize_agent tx (latest row per wallet is live).
-- ----------------------------------------------------------
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ops_agent' AND COLUMN_NAME = 'permission_bits'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE ops_agent ADD COLUMN permission_bits TINYINT NOT NULL DEFAULT 0 AFTER on_chain_permission',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE ops_agent SET role_summary = 'Pedagogical plans and Enigma level', on_chain_permission = 1, permission_bits = 1
WHERE code = 'NEXUS';
UPDATE ops_agent SET role_summary = 'Onboarding and musician routing', on_chain_permission = 0, permission_bits = 0
WHERE code = 'GENESIS';
UPDATE ops_agent SET permission_bits = 2 WHERE code = 'HARMONY';
UPDATE ops_agent SET permission_bits = 4 WHERE code = 'STAGE';
UPDATE ops_agent SET permission_bits = 8 WHERE code = 'VAULT';
UPDATE ops_agent SET permission_bits = 16 WHERE code = 'WAVE';

CREATE TABLE IF NOT EXISTS agent_authority (
  id BIGINT NOT NULL AUTO_INCREMENT,
  ops_agent_id BIGINT NOT NULL,
  agent_wallet_pubkey VARCHAR(44) NOT NULL,
  permissions TINYINT NOT NULL,
  agent_authority_pda VARCHAR(44) NOT NULL,
  owner_wallet_pubkey VARCHAR(44) NOT NULL,
  tx_signature VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_authority_tx (tx_signature),
  KEY idx_agent_authority_wallet (agent_wallet_pubkey),
  CONSTRAINT fk_agent_authority_agent FOREIGN KEY (ops_agent_id) REFERENCES ops_agent (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------
-- two-way Slack approvals. answered_via = where the owner answered
-- ('web' inbox or 'slack' button); answered_by = Slack user (id / name) or owner wallet.
-- ----------------------------------------------------------
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'owner_decision' AND COLUMN_NAME = 'answered_via'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE owner_decision ADD COLUMN answered_via VARCHAR(16) NULL AFTER resolved_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'owner_decision' AND COLUMN_NAME = 'answered_by'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE owner_decision ADD COLUMN answered_by VARCHAR(80) NULL AFTER answered_via',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE owner_decision SET answered_via = 'web'
WHERE answered_via IS NULL AND status IN ('approved', 'rejected');
