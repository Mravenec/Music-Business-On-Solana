-- Epic 27: account_role/account_role_application live in their own schema
-- (eh8s_role), not the hub eh8s schema — see MIGRATION_MAP.md and
-- .docs/journey/27-multi-schema-domains.md.
CREATE DATABASE IF NOT EXISTS eh8s_role
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE eh8s_role;

-- Epic 19: role applications + multi-role membership (owner is not applyable).
CREATE TABLE IF NOT EXISTS account_role_application (
  id BIGINT NOT NULL AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  wallet_pubkey VARCHAR(44) NOT NULL,
  role VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  reason VARCHAR(500) NULL,
  reviewed_by_wallet VARCHAR(44) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_app_account_role (account_id, role),
  KEY idx_role_app_status (status),
  KEY idx_role_app_wallet (wallet_pubkey),
  CONSTRAINT fk_role_app_account FOREIGN KEY (account_id) REFERENCES eh8s.account (id),
  CONSTRAINT chk_role_app_role CHECK (role IN ('student', 'musician', 'instructor', 'venue')),
  CONSTRAINT chk_role_app_status CHECK (status IN ('pending', 'approved', 'rejected'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS account_role (
  id BIGINT NOT NULL AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  role VARCHAR(32) NOT NULL,
  granted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  application_id BIGINT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_account_role_member (account_id, role),
  CONSTRAINT fk_account_role_account FOREIGN KEY (account_id) REFERENCES eh8s.account (id),
  CONSTRAINT fk_account_role_application FOREIGN KEY (application_id) REFERENCES account_role_application (id),
  CONSTRAINT chk_account_role_role CHECK (role IN ('student', 'musician', 'instructor', 'venue'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Ensure unique wallet+role application (idempotent if index already present via CREATE).
SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account_role_application' AND INDEX_NAME = 'uk_role_app_wallet_role'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE account_role_application ADD UNIQUE KEY uk_role_app_wallet_role (wallet_pubkey, role)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Backfill multi-role membership from legacy account.role (never owner).
INSERT INTO account_role (account_id, role, granted_at)
SELECT a.id, a.role, UTC_TIMESTAMP()
FROM eh8s.account a
WHERE a.role IN ('student', 'musician', 'instructor', 'venue')
ON DUPLICATE KEY UPDATE role = VALUES(role);

-- Epic 71: studio_admin and partner are granted beside the principal wallet.
SET @c := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account_role_application' AND CONSTRAINT_NAME = 'chk_role_app_role'
);
SET @ddl := IF(@c = 1,
  'ALTER TABLE account_role_application DROP CONSTRAINT chk_role_app_role',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE account_role_application
  ADD CONSTRAINT chk_role_app_role
  CHECK (role IN ('student', 'musician', 'instructor', 'venue', 'studio_admin', 'partner'));

SET @c := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account_role' AND CONSTRAINT_NAME = 'chk_account_role_role'
);
SET @ddl := IF(@c = 1,
  'ALTER TABLE account_role DROP CONSTRAINT chk_account_role_role',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE account_role
  ADD CONSTRAINT chk_account_role_role
  CHECK (role IN ('student', 'musician', 'instructor', 'venue', 'studio_admin', 'partner'));
