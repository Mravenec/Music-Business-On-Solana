-- EH8S through catalog/geo, agents/owner, and Solana chain_config (Epic 7).
-- Domain wallet/PDA columns stay nullable; chain_config is the client settings row.

CREATE DATABASE IF NOT EXISTS eh8s
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE eh8s;

CREATE TABLE IF NOT EXISTS app_meta (
  meta_key VARCHAR(64) NOT NULL,
  meta_value VARCHAR(255) NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (meta_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO app_meta (meta_key, meta_value) VALUES
  ('module_name', 'eh8s'),
  ('product', 'Enigma H8 Studios')
ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value);

-- ──────────────────────────────────────────────────────────
-- From (epic 28 layout): eh8s/02_academy_core.sql
-- ──────────────────────────────────────────────────────────
-- Epic 28: relocated from canonical_sql/02_academy.sql into eh8s's own
-- folder (the hub-schema part of that ticket — instructor_profile/
-- academy_plan/academy_subscription/enigma_evaluation moved to
-- eh8s_academy/01_academy.sql). No mock/demo data here — instrument and
-- enigma_level are real product catalog. See MIGRATION_MAP.md.
CREATE TABLE IF NOT EXISTS instrument (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(80) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_instrument_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS enigma_level (
  id BIGINT NOT NULL AUTO_INCREMENT,
  level_number TINYINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  duration_note VARCHAR(120) NOT NULL,
  milestone VARCHAR(160) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_enigma_level_number (level_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS account (
  id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(160) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  role VARCHAR(32) NOT NULL,
  country_code CHAR(3) NULL,
  wallet_pubkey VARCHAR(44) NULL,
  password_hash VARCHAR(72) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_account_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS musician_profile (
  id BIGINT NOT NULL AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  instrument_id BIGINT NOT NULL,
  enigma_level_id BIGINT NOT NULL,
  enigma_score TINYINT NOT NULL DEFAULT 0,
  musician_profile_pda VARCHAR(44) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_musician_account (account_id),
  CONSTRAINT fk_musician_account FOREIGN KEY (account_id) REFERENCES account (id),
  CONSTRAINT fk_musician_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id),
  CONSTRAINT fk_musician_level FOREIGN KEY (enigma_level_id) REFERENCES enigma_level (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO instrument (code, name) VALUES
  ('guitar', 'Guitar'),
  ('drums', 'Drums'),
  ('piano', 'Piano / Keyboard'),
  ('voice', 'Voice'),
  ('bass', 'Electric Bass'),
  ('wind', 'Wind'),
  ('strings', 'Strings'),
  ('production', 'Production / DJ')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO enigma_level (level_number, name, duration_note, milestone) VALUES
  (0, 'Orientation Enigma', '1 week, free', 'Personalized IA plan'),
  (1, 'Foundations', '2-3 months', 'Play three simple songs'),
  (2, 'Builder', '3-4 months', 'First group rehearsal'),
  (3, 'Ensemble', '3-4 months', 'First internal mini-show'),
  (4, 'Stage', '2-3 months', 'Real venue performance'),
  (5, 'Professional EH8S', 'Ongoing', 'Paid concerts; eligible to teach')
ON DUPLICATE KEY UPDATE name = VALUES(name), duration_note = VALUES(duration_note), milestone = VALUES(milestone);

-- ──────────────────────────────────────────────────────────
-- From (epic 28 layout): eh8s/03_bands_spp.sql
-- ──────────────────────────────────────────────────────────
-- Epic 28: relocated from canonical_sql/03_bands_spp.sql into eh8s's own
-- folder; mock seed rows (band/musician_profile/band_member/rehearsal_*/
-- spp_cycle/spp_member_score — the demo "Noche Oscura" band scenario) moved
-- to 0.database/mocks/eh8s.sql. spp_variable catalog INSERT stays — real SPP
-- scoring weights the app's own logic depends on. See MIGRATION_MAP.md.
CREATE TABLE IF NOT EXISTS spp_variable (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(80) NOT NULL,
  weight_bps INT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_spp_variable_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS band (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(120) NOT NULL,
  band_type VARCHAR(16) NOT NULL,
  spp_enabled TINYINT(1) NOT NULL DEFAULT 0,
  geo_code VARCHAR(16) NULL,
  band_vault_pda VARCHAR(44) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_band_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS band_member (
  id BIGINT NOT NULL AUTO_INCREMENT,
  band_id BIGINT NOT NULL,
  musician_profile_id BIGINT NOT NULL,
  role_in_band VARCHAR(80) NOT NULL,
  joined_at DATE NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_band_member (band_id, musician_profile_id),
  CONSTRAINT fk_member_band FOREIGN KEY (band_id) REFERENCES band (id),
  CONSTRAINT fk_member_musician FOREIGN KEY (musician_profile_id) REFERENCES musician_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rehearsal_session (
  id BIGINT NOT NULL AUTO_INCREMENT,
  band_id BIGINT NOT NULL,
  scheduled_at DATETIME NOT NULL,
  notes VARCHAR(240) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_rehearsal_band FOREIGN KEY (band_id) REFERENCES band (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rehearsal_checkin (
  id BIGINT NOT NULL AUTO_INCREMENT,
  rehearsal_session_id BIGINT NOT NULL,
  musician_profile_id BIGINT NOT NULL,
  arrived_at DATETIME NOT NULL,
  late_minutes INT NOT NULL DEFAULT 0,
  attendance_points INT NOT NULL DEFAULT 0,
  punctuality_points INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_checkin (rehearsal_session_id, musician_profile_id),
  CONSTRAINT fk_checkin_session FOREIGN KEY (rehearsal_session_id) REFERENCES rehearsal_session (id),
  CONSTRAINT fk_checkin_musician FOREIGN KEY (musician_profile_id) REFERENCES musician_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS spp_cycle (
  id BIGINT NOT NULL AUTO_INCREMENT,
  band_id BIGINT NOT NULL,
  code VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  started_at DATE NOT NULL,
  closed_at DATE NULL,
  intended_instruction VARCHAR(64) NOT NULL DEFAULT 'update_spp_weights',
  PRIMARY KEY (id),
  UNIQUE KEY uk_spp_cycle_band_code (band_id, code),
  CONSTRAINT fk_cycle_band FOREIGN KEY (band_id) REFERENCES band (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS spp_member_score (
  id BIGINT NOT NULL AUTO_INCREMENT,
  spp_cycle_id BIGINT NOT NULL,
  musician_profile_id BIGINT NOT NULL,
  attendance_points INT NOT NULL DEFAULT 0,
  punctuality_points INT NOT NULL DEFAULT 0,
  creative_points INT NOT NULL DEFAULT 0,
  skill_points INT NOT NULL DEFAULT 0,
  concert_points INT NOT NULL DEFAULT 0,
  total_points INT NOT NULL DEFAULT 0,
  share_bps INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_spp_score (spp_cycle_id, musician_profile_id),
  CONSTRAINT fk_score_cycle FOREIGN KEY (spp_cycle_id) REFERENCES spp_cycle (id),
  CONSTRAINT fk_score_musician FOREIGN KEY (musician_profile_id) REFERENCES musician_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO spp_variable (code, name, weight_bps) VALUES
  ('attendance', 'Rehearsal attendance', 4000),
  ('punctuality', 'Punctuality', 1500),
  ('creative', 'Creative contribution', 2000),
  ('skill', 'Skill progress', 1500),
  ('concert', 'Concert participation', 1000)
ON DUPLICATE KEY UPDATE name = VALUES(name), weight_bps = VALUES(weight_bps);

-- ──────────────────────────────────────────────────────────
-- From (epic 28 layout): eh8s/04_venues_concerts.sql
-- ──────────────────────────────────────────────────────────
-- Epic 28: relocated from canonical_sql/04_venues_concerts.sql into eh8s's
-- own folder; mock seed rows (venue/booking/concert/concert_expense — the
-- demo "Bar La Cueva"/"Noche Oscura" scenario) moved to
-- 0.database/mocks/eh8s.sql. contract_type catalog INSERT stays — real
-- product taxonomy, not demo data. See MIGRATION_MAP.md.
CREATE TABLE IF NOT EXISTS contract_type (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(400) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_contract_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS venue (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(120) NOT NULL,
  city VARCHAR(80) NOT NULL,
  country_code CHAR(3) NOT NULL,
  capacity INT NOT NULL,
  suggested_ticket_usdc DECIMAL(12,2) NOT NULL,
  latitude DECIMAL(10,7) NOT NULL,
  longitude DECIMAL(10,7) NOT NULL,
  pin_status VARCHAR(16) NOT NULL,
  eh8s_rating TINYINT NOT NULL DEFAULT 5,
  sound_included TINYINT(1) NOT NULL DEFAULT 1,
  preferred_genres VARCHAR(160) NULL,
  contract_type_id BIGINT NOT NULL,
  venue_listing_pda VARCHAR(44) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_venue_code (code),
  CONSTRAINT fk_venue_contract FOREIGN KEY (contract_type_id) REFERENCES contract_type (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS booking (
  id BIGINT NOT NULL AUTO_INCREMENT,
  venue_id BIGINT NOT NULL,
  band_id BIGINT NOT NULL,
  show_date DATE NOT NULL,
  pipeline_week TINYINT NOT NULL,
  status VARCHAR(24) NOT NULL,
  venue_access_token_pda VARCHAR(44) NULL,
  contract_hash VARCHAR(64) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_booking_slot (venue_id, band_id, show_date),
  CONSTRAINT fk_booking_venue FOREIGN KEY (venue_id) REFERENCES venue (id),
  CONSTRAINT fk_booking_band FOREIGN KEY (band_id) REFERENCES band (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS concert (
  id BIGINT NOT NULL AUTO_INCREMENT,
  booking_id BIGINT NOT NULL,
  venue_id BIGINT NOT NULL,
  band_id BIGINT NOT NULL,
  spp_cycle_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  concert_settlement_pda VARCHAR(44) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_concert_booking (booking_id),
  CONSTRAINT fk_concert_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
  CONSTRAINT fk_concert_venue FOREIGN KEY (venue_id) REFERENCES venue (id),
  CONSTRAINT fk_concert_band FOREIGN KEY (band_id) REFERENCES band (id),
  CONSTRAINT fk_concert_cycle FOREIGN KEY (spp_cycle_id) REFERENCES spp_cycle (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS concert_expense (
  id BIGINT NOT NULL AUTO_INCREMENT,
  concert_id BIGINT NOT NULL,
  label VARCHAR(80) NOT NULL,
  amount_usdc DECIMAL(12,2) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_expense_concert FOREIGN KEY (concert_id) REFERENCES concert (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS concert_settlement (
  id BIGINT NOT NULL AUTO_INCREMENT,
  concert_id BIGINT NOT NULL,
  gross_usdc DECIMAL(12,2) NOT NULL,
  expenses_usdc DECIMAL(12,2) NOT NULL,
  net_usdc DECIMAL(12,2) NOT NULL,
  eh8s_fee_usdc DECIMAL(12,2) NOT NULL,
  band_pool_usdc DECIMAL(12,2) NOT NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_settlement_concert (concert_id),
  CONSTRAINT fk_settlement_concert FOREIGN KEY (concert_id) REFERENCES concert (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pending_claim (
  id BIGINT NOT NULL AUTO_INCREMENT,
  concert_settlement_id BIGINT NOT NULL,
  musician_profile_id BIGINT NOT NULL,
  share_bps INT NOT NULL,
  amount_usdc DECIMAL(12,2) NOT NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_claim_settlement_musician (concert_settlement_id, musician_profile_id),
  CONSTRAINT fk_claim_settlement FOREIGN KEY (concert_settlement_id) REFERENCES concert_settlement (id),  CONSTRAINT fk_claim_musician FOREIGN KEY (musician_profile_id) REFERENCES musician_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO contract_type (code, name, description) VALUES
  ('fixed_guarantee', 'Fixed guarantee', 'Venue pays an agreed amount regardless of attendance'),
  ('door', 'Door deal', 'Band receives 70-80 percent of net door without a floor'),
  ('versus', 'Versus', 'Band receives the greater of a guarantee or a door percentage'),
  ('eh8s_promoter', 'EH8S promoter', 'EH8S rents the venue and sells tickets; band takes a share of net')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- ──────────────────────────────────────────────────────────
-- From (epic 28 layout): eh8s/05_product_shell.sql
-- ──────────────────────────────────────────────────────────
-- Epic 26: pin the active schema explicitly (eh8s_ops interleaves in the SOURCE order below).
-- Product shell: wallet session columns on existing DBs (CREATE IF NOT EXISTS does not alter).
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account' AND COLUMN_NAME = 'last_seen_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE account ADD COLUMN last_seen_at DATETIME NULL AFTER wallet_pubkey',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account' AND INDEX_NAME = 'uk_account_wallet'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE account ADD UNIQUE KEY uk_account_wallet (wallet_pubkey)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ──────────────────────────────────────────────────────────
-- From (epic 28 layout): eh8s/06_wallet_devnet_pay_claim.sql
-- ──────────────────────────────────────────────────────────
-- Epic 14 wallet DevNet pay: pending_claim signature columns (idempotent).
-- Epic 28: relocated from canonical_sql/11_wallet_devnet_pay.sql into eh8s's
-- own folder (the devnet_pay_marker CREATE + academy_subscription ALTER from
-- that same ticket moved to eh8s_onchain/02_wallet_devnet_pay.sql and
-- eh8s_academy/03_wallet_devnet_pay.sql respectively).
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pending_claim' AND COLUMN_NAME = 'tx_signature'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE pending_claim ADD COLUMN tx_signature VARCHAR(128) NULL AFTER status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pending_claim' AND COLUMN_NAME = 'claimed_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE pending_claim ADD COLUMN claimed_at DATETIME NULL AFTER tx_signature',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ──────────────────────────────────────────────────────────
-- From (epic 28 layout): eh8s/07_role_platform_account.sql
-- ──────────────────────────────────────────────────────────
-- Epic 18: platform owner wallet, session geo on account.
-- Epic 28: relocated from canonical_sql/16_role_platform_shell.sql into
-- eh8s's own folder (the chain_config part of that same ticket moved to
-- eh8s_onchain/04_role_platform_chain.sql instead). The owner account
-- INSERT stays — bootstrap data the platform's role system depends on,
-- not a demo scenario. See MIGRATION_MAP.md.
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account' AND COLUMN_NAME = 'last_lat'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE account ADD COLUMN last_lat DECIMAL(10,7) NULL AFTER last_seen_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account' AND COLUMN_NAME = 'last_lng'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE account ADD COLUMN last_lng DECIMAL(10,7) NULL AFTER last_lat',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account' AND COLUMN_NAME = 'last_geo_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE account ADD COLUMN last_geo_at DATETIME NULL AFTER last_lng',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO account (email, display_name, role, country_code, wallet_pubkey, last_seen_at)
VALUES (
  'owner@eh8s.local',
  'EH8S Platform Owner',
  'owner',
  'USA',
  '7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC',
  UTC_TIMESTAMP()
)
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  role = 'owner',
  wallet_pubkey = VALUES(wallet_pubkey);

-- ──────────────────────────────────────────────────────────
-- From (epic 28 layout): eh8s/08_settle_claim_onchain_devnet.sql
-- ──────────────────────────────────────────────────────────
-- Epic 26: pin the active schema explicitly (eh8s_ops interleaves in the SOURCE order below).
-- Epic 22: settle_concert / claim_royalties on-chain linkage (idempotent for existing DBs).
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'concert' AND COLUMN_NAME = 'concert_settlement_pda'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE concert ADD COLUMN concert_settlement_pda VARCHAR(44) NULL AFTER status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'concert_settlement' AND COLUMN_NAME = 'settle_tx_signature'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE concert_settlement ADD COLUMN settle_tx_signature VARCHAR(128) NULL AFTER status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'concert_settlement' AND COLUMN_NAME = 'settled_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE concert_settlement ADD COLUMN settled_at DATETIME NULL AFTER settle_tx_signature',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'concert_settlement' AND COLUMN_NAME = 'on_chain_status'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE concert_settlement ADD COLUMN on_chain_status VARCHAR(16) NULL AFTER settled_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'concert_settlement' AND INDEX_NAME = 'uk_settlement_tx'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE concert_settlement ADD UNIQUE KEY uk_settlement_tx (settle_tx_signature)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pending_claim' AND COLUMN_NAME = 'claim_pda'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE pending_claim ADD COLUMN claim_pda VARCHAR(44) NULL AFTER status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pending_claim' AND COLUMN_NAME = 'claimer_wallet_pubkey'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE pending_claim ADD COLUMN claimer_wallet_pubkey VARCHAR(44) NULL AFTER claimed_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pending_claim' AND COLUMN_NAME = 'on_chain_status'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE pending_claim ADD COLUMN on_chain_status VARCHAR(16) NULL AFTER claimer_wallet_pubkey',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pending_claim' AND INDEX_NAME = 'uk_claim_tx'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE pending_claim ADD UNIQUE KEY uk_claim_tx (tx_signature)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pending_claim' AND INDEX_NAME = 'uk_claim_pda'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE pending_claim ADD UNIQUE KEY uk_claim_pda (claim_pda)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Epic 30: BCrypt password_hash for JWT login (nullable — wallet-only accounts).
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'account' AND COLUMN_NAME = 'password_hash'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE account ADD COLUMN password_hash VARCHAR(72) NULL AFTER last_geo_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Epic 35: on-chain BandVault (create_band / update_spp_weights) activation + weight sync.
-- band_vault_pda already exists; these record the DevNet signatures and the synced weights
-- ({musicianProfileId, wallet, bps} in vault member order) used for the SPP split.
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'band' AND COLUMN_NAME = 'vault_tx_signature'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE band ADD COLUMN vault_tx_signature VARCHAR(100) NULL AFTER band_vault_pda',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'band' AND COLUMN_NAME = 'vault_activated_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE band ADD COLUMN vault_activated_at DATETIME NULL AFTER vault_tx_signature',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'band' AND COLUMN_NAME = 'spp_weights_json'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE band ADD COLUMN spp_weights_json VARCHAR(2048) NULL AFTER vault_activated_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'band' AND COLUMN_NAME = 'weights_tx_signature'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE band ADD COLUMN weights_tx_signature VARCHAR(100) NULL AFTER spp_weights_json',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'band' AND COLUMN_NAME = 'weights_synced_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE band ADD COLUMN weights_synced_at DATETIME NULL AFTER weights_tx_signature',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'band' AND INDEX_NAME = 'uk_band_vault_pda'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE band ADD UNIQUE KEY uk_band_vault_pda (band_vault_pda)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'band' AND INDEX_NAME = 'uk_band_vault_tx'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE band ADD UNIQUE KEY uk_band_vault_tx (vault_tx_signature)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------
-- Epic 40: program v0.6.0 - on-chain MusicianProfile carries a country (ISO-3166 alpha-3) and
-- the Enigma level is agent-only (update_musician_level). One row per verified level change.
-- ----------------------------------------------------------
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'musician_profile' AND COLUMN_NAME = 'country_code'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE musician_profile ADD COLUMN country_code CHAR(3) NULL AFTER enigma_score',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS musician_level_change (
  id BIGINT NOT NULL AUTO_INCREMENT,
  musician_profile_id BIGINT NOT NULL,
  agent_wallet_pubkey VARCHAR(44) NOT NULL,
  old_level TINYINT NOT NULL,
  new_level TINYINT NOT NULL,
  tx_signature VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_level_change_tx (tx_signature),
  KEY idx_level_change_musician (musician_profile_id),
  CONSTRAINT fk_level_change_musician FOREIGN KEY (musician_profile_id) REFERENCES musician_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------
-- Epic 41: program v0.7.0 - VenueListing (register_venue / approve_venue by a STAGE agent) and
-- VenueAccessToken escrow (propose_booking -> confirm_booking -> settle_booking by a VAULT agent,
-- or cancel_booking by the venue while proposed). Each step stores its verified signature.
-- ----------------------------------------------------------
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'venue' AND COLUMN_NAME = 'wallet_pubkey'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE venue ADD COLUMN wallet_pubkey VARCHAR(44) NULL AFTER venue_listing_pda',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'venue' AND COLUMN_NAME = 'listing_status'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE venue ADD COLUMN listing_status VARCHAR(16) NULL AFTER wallet_pubkey',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'venue' AND COLUMN_NAME = 'register_tx_signature'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE venue ADD COLUMN register_tx_signature VARCHAR(128) NULL AFTER listing_status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'venue' AND COLUMN_NAME = 'approve_tx_signature'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE venue ADD COLUMN approve_tx_signature VARCHAR(128) NULL AFTER register_tx_signature',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'venue' AND COLUMN_NAME = 'approved_by_wallet'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE venue ADD COLUMN approved_by_wallet VARCHAR(44) NULL AFTER approve_tx_signature',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'venue' AND INDEX_NAME = 'uk_venue_register_tx'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE venue ADD UNIQUE KEY uk_venue_register_tx (register_tx_signature)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'venue' AND INDEX_NAME = 'uk_venue_approve_tx'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE venue ADD UNIQUE KEY uk_venue_approve_tx (approve_tx_signature)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking' AND COLUMN_NAME = 'onchain_concert_id'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE booking ADD COLUMN onchain_concert_id BIGINT NULL AFTER contract_hash',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking' AND COLUMN_NAME = 'gross_usdc'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE booking ADD COLUMN gross_usdc DECIMAL(12,2) NULL AFTER onchain_concert_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking' AND COLUMN_NAME = 'escrow_pda'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE booking ADD COLUMN escrow_pda VARCHAR(44) NULL AFTER gross_usdc',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking' AND COLUMN_NAME = 'contract_text'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE booking ADD COLUMN contract_text TEXT NULL AFTER escrow_pda',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking' AND COLUMN_NAME = 'onchain_status'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE booking ADD COLUMN onchain_status VARCHAR(16) NULL AFTER contract_text',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking' AND COLUMN_NAME = 'propose_tx_signature'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE booking ADD COLUMN propose_tx_signature VARCHAR(128) NULL AFTER onchain_status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking' AND COLUMN_NAME = 'confirm_tx_signature'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE booking ADD COLUMN confirm_tx_signature VARCHAR(128) NULL AFTER propose_tx_signature',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking' AND COLUMN_NAME = 'cancel_tx_signature'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE booking ADD COLUMN cancel_tx_signature VARCHAR(128) NULL AFTER confirm_tx_signature',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking' AND INDEX_NAME = 'uk_booking_onchain_concert'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE booking ADD UNIQUE KEY uk_booking_onchain_concert (venue_id, onchain_concert_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking' AND INDEX_NAME = 'uk_booking_access_pda'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE booking ADD UNIQUE KEY uk_booking_access_pda (venue_access_token_pda)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking' AND INDEX_NAME = 'uk_booking_propose_tx'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE booking ADD UNIQUE KEY uk_booking_propose_tx (propose_tx_signature)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking' AND INDEX_NAME = 'uk_booking_confirm_tx'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE booking ADD UNIQUE KEY uk_booking_confirm_tx (confirm_tx_signature)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking' AND INDEX_NAME = 'uk_booking_cancel_tx'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE booking ADD UNIQUE KEY uk_booking_cancel_tx (cancel_tx_signature)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'concert_settlement' AND COLUMN_NAME = 'settled_via'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE concert_settlement ADD COLUMN settled_via VARCHAR(24) NULL AFTER on_chain_status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'concert_settlement' AND COLUMN_NAME = 'settled_by_wallet'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE concert_settlement ADD COLUMN settled_by_wallet VARCHAR(44) NULL AFTER settled_via',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Epic 45: SPP inputs (creative ratings, skill delta, concert minutes) + Stage Map pins

CREATE TABLE IF NOT EXISTS creative_rating (
  id BIGINT NOT NULL AUTO_INCREMENT,
  rehearsal_session_id BIGINT NOT NULL,
  rater_profile_id BIGINT NOT NULL,
  ratee_profile_id BIGINT NOT NULL,
  score TINYINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_creative_rating (rehearsal_session_id, rater_profile_id, ratee_profile_id),
  CONSTRAINT ck_creative_rating_score CHECK (score BETWEEN 1 AND 5),
  CONSTRAINT ck_creative_rating_self CHECK (rater_profile_id <> ratee_profile_id),
  CONSTRAINT fk_creative_rating_session FOREIGN KEY (rehearsal_session_id) REFERENCES rehearsal_session (id),
  CONSTRAINT fk_creative_rating_rater FOREIGN KEY (rater_profile_id) REFERENCES musician_profile (id),
  CONSTRAINT fk_creative_rating_ratee FOREIGN KEY (ratee_profile_id) REFERENCES musician_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS concert_set_checkin (
  id BIGINT NOT NULL AUTO_INCREMENT,
  concert_id BIGINT NOT NULL,
  musician_profile_id BIGINT NOT NULL,
  set_started_at DATETIME NOT NULL,
  set_ended_at DATETIME NULL,
  minutes_played INT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_concert_set_checkin (concert_id, musician_profile_id),
  CONSTRAINT fk_set_checkin_concert FOREIGN KEY (concert_id) REFERENCES concert (id),
  CONSTRAINT fk_set_checkin_musician FOREIGN KEY (musician_profile_id) REFERENCES musician_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS venue_availability (
  id BIGINT NOT NULL AUTO_INCREMENT,
  venue_id BIGINT NOT NULL,
  available_date DATE NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_venue_availability (venue_id, available_date),
  CONSTRAINT fk_availability_venue FOREIGN KEY (venue_id) REFERENCES venue (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'concert' AND COLUMN_NAME = 'show_minutes'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE concert ADD COLUMN show_minutes INT NOT NULL DEFAULT 60 AFTER status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'spp_member_score' AND COLUMN_NAME = 'enigma_level_start'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE spp_member_score ADD COLUMN enigma_level_start TINYINT NULL AFTER share_bps',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'spp_member_score' AND COLUMN_NAME = 'enigma_level_end'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE spp_member_score ADD COLUMN enigma_level_end TINYINT NULL AFTER enigma_level_start',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'venue' AND COLUMN_NAME = 'is_partner'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE venue ADD COLUMN is_partner TINYINT(1) NOT NULL DEFAULT 0 AFTER pin_status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE venue SET is_partner = 1 WHERE pin_status = 'partner' AND is_partner = 0;

-- Epic 46: live agents. HARMONY ranks candidate musicians for a band (instrument gap, level
-- proximity, country, genre overlap); ATLAS projects tour income from contract_type.band_share_bps.
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'band' AND COLUMN_NAME = 'genres'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE band ADD COLUMN genres VARCHAR(160) NULL AFTER geo_code',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'musician_profile' AND COLUMN_NAME = 'genres'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE musician_profile ADD COLUMN genres VARCHAR(160) NULL AFTER country_code',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contract_type' AND COLUMN_NAME = 'band_share_bps'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE contract_type ADD COLUMN band_share_bps INT NULL AFTER description',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Share of projected gross (capacity x ticket x fill) that reaches the band per contract type.
UPDATE contract_type SET band_share_bps = 5000 WHERE code = 'fixed_guarantee' AND band_share_bps IS NULL;
UPDATE contract_type SET band_share_bps = 7500 WHERE code = 'door' AND band_share_bps IS NULL;
UPDATE contract_type SET band_share_bps = 8000 WHERE code = 'versus' AND band_share_bps IS NULL;
UPDATE contract_type SET band_share_bps = 6000 WHERE code = 'eh8s_promoter' AND band_share_bps IS NULL;

CREATE TABLE IF NOT EXISTS band_match_suggestion (
  id BIGINT NOT NULL AUTO_INCREMENT,
  band_id BIGINT NOT NULL,
  musician_profile_id BIGINT NOT NULL,
  score TINYINT NOT NULL,
  instrument_points TINYINT NOT NULL DEFAULT 0,
  level_points TINYINT NOT NULL DEFAULT 0,
  country_points TINYINT NOT NULL DEFAULT 0,
  genre_points TINYINT NOT NULL DEFAULT 0,
  reason VARCHAR(400) NOT NULL,
  ai_rationale VARCHAR(1000) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'suggested',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_band_match (band_id, musician_profile_id),
  CONSTRAINT ck_band_match_score CHECK (score BETWEEN 0 AND 100),
  CONSTRAINT fk_band_match_band FOREIGN KEY (band_id) REFERENCES band (id),
  CONSTRAINT fk_band_match_musician FOREIGN KEY (musician_profile_id) REFERENCES musician_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Epic 69: owner-managed partners. share_bps is their slice of recorded studio fees.
-- Allocations are the books for one month and one source. They do not move USDC.
CREATE TABLE IF NOT EXISTS studio_partner (
  id BIGINT NOT NULL AUTO_INCREMENT,
  wallet_pubkey VARCHAR(44) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  share_bps INT NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_studio_partner_wallet (wallet_pubkey),
  CONSTRAINT ck_studio_partner_share CHECK (share_bps BETWEEN 1 AND 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS studio_partner_allocation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  studio_partner_id BIGINT NOT NULL,
  year_num SMALLINT NOT NULL,
  month_num TINYINT NOT NULL,
  source_code VARCHAR(32) NOT NULL,
  amount_usdc DECIMAL(12,2) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_partner_allocation (studio_partner_id, year_num, month_num, source_code),
  CONSTRAINT ck_partner_allocation_month CHECK (month_num BETWEEN 1 AND 12),
  CONSTRAINT ck_partner_allocation_source CHECK (source_code IN ('show_fee', 'academy', 'sync')),
  CONSTRAINT fk_partner_allocation FOREIGN KEY (studio_partner_id) REFERENCES studio_partner (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
