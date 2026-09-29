-- Seed rows below are product catalog / bootstrap data, not demo data.
CREATE DATABASE IF NOT EXISTS eh8s_catalog
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE eh8s_catalog;

CREATE TABLE IF NOT EXISTS royalty_type (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(80) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_royalty_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS track (
  id BIGINT NOT NULL AUTO_INCREMENT,
  band_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  isrc VARCHAR(16) NULL,
  provider VARCHAR(32) NOT NULL DEFAULT 'distrokid',
  royalty_pool_pda VARCHAR(44) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_track_band FOREIGN KEY (band_id) REFERENCES eh8s.band (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS royalty_split (
  id BIGINT NOT NULL AUTO_INCREMENT,
  track_id BIGINT NOT NULL,
  royalty_type_id BIGINT NOT NULL,
  musician_profile_id BIGINT NULL,
  party VARCHAR(16) NOT NULL,
  share_bps INT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_split_track FOREIGN KEY (track_id) REFERENCES track (id),
  CONSTRAINT fk_split_type FOREIGN KEY (royalty_type_id) REFERENCES royalty_type (id),
  CONSTRAINT fk_split_musician FOREIGN KEY (musician_profile_id) REFERENCES eh8s.musician_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS royalty_deposit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  track_id BIGINT NOT NULL,
  royalty_type_id BIGINT NOT NULL,
  amount_usdc DECIMAL(12,2) NOT NULL,
  intended_instruction VARCHAR(64) NOT NULL DEFAULT 'deposit_royalties',
  PRIMARY KEY (id),
  CONSTRAINT fk_deposit_track FOREIGN KEY (track_id) REFERENCES track (id),
  CONSTRAINT fk_deposit_type FOREIGN KEY (royalty_type_id) REFERENCES royalty_type (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS channel_format (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(80) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_channel_format_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS channel_piece (
  id BIGINT NOT NULL AUTO_INCREMENT,
  channel_format_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  artist_share_bps INT NOT NULL,
  eh8s_share_bps INT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_piece_format FOREIGN KEY (channel_format_id) REFERENCES channel_format (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS geo_tier (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(80) NOT NULL,
  usdc_monthly DECIMAL(12,2) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_geo_tier_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS geo_region (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(80) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_geo_region_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS geographic_subscription (
  id BIGINT NOT NULL AUTO_INCREMENT,
  band_id BIGINT NOT NULL,
  geo_tier_id BIGINT NOT NULL,
  geo_region_id BIGINT NOT NULL,
  starts_at DATE NOT NULL,
  expires_at DATE NOT NULL,
  geographic_subscription_pda VARCHAR(44) NULL,
  intended_instruction VARCHAR(64) NOT NULL DEFAULT 'subscribe_geographic',
  PRIMARY KEY (id),
  CONSTRAINT fk_geosub_band FOREIGN KEY (band_id) REFERENCES eh8s.band (id),
  CONSTRAINT fk_geosub_tier FOREIGN KEY (geo_tier_id) REFERENCES geo_tier (id),
  CONSTRAINT fk_geosub_region FOREIGN KEY (geo_region_id) REFERENCES geo_region (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tour_plan (
  id BIGINT NOT NULL AUTO_INCREMENT,
  band_id BIGINT NOT NULL,
  geo_region_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  route_note VARCHAR(400) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_tour_band FOREIGN KEY (band_id) REFERENCES eh8s.band (id),
  CONSTRAINT fk_tour_region FOREIGN KEY (geo_region_id) REFERENCES geo_region (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO royalty_type (code, name) VALUES
  ('master', 'Master'),
  ('mechanical', 'Mechanical'),
  ('performance', 'Performance'),
  ('sync', 'Sync')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO channel_format (code, name) VALUES
  ('livestream', 'Livestream'),
  ('vod', 'VOD'),
  ('masterclass', 'Masterclass'),
  ('bts', 'Behind the scenes'),
  ('free_lesson', 'Free lesson'),
  ('podcast', 'Podcast')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO geo_tier (code, name, usdc_monthly) VALUES
  ('local', 'Local', 5.00),
  ('city', 'City', 15.00),
  ('country', 'Country', 35.00),
  ('regional', 'Regional', 65.00),
  ('global', 'Global', 99.00)
ON DUPLICATE KEY UPDATE name = VALUES(name), usdc_monthly = VALUES(usdc_monthly);

INSERT INTO geo_region (code, name) VALUES
  ('MEX', 'Mexico'),
  ('MEX-CDMX', 'Mexico City')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ──────────────────────────────────────────────────────────
-- Seed rows below are product catalog / bootstrap data, not demo data.
-- royalty pool + geographic subscription on-chain linkage (idempotent).
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'royalty_deposit' AND COLUMN_NAME = 'royalty_pool_pda'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE royalty_deposit ADD COLUMN royalty_pool_pda VARCHAR(44) NULL AFTER intended_instruction',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'royalty_deposit' AND COLUMN_NAME = 'deposit_tx_signature'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE royalty_deposit ADD COLUMN deposit_tx_signature VARCHAR(128) NULL AFTER royalty_pool_pda',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'royalty_deposit' AND COLUMN_NAME = 'deposited_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE royalty_deposit ADD COLUMN deposited_at DATETIME NULL AFTER deposit_tx_signature',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'royalty_deposit' AND COLUMN_NAME = 'payer_wallet_pubkey'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE royalty_deposit ADD COLUMN payer_wallet_pubkey VARCHAR(44) NULL AFTER deposited_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'royalty_deposit' AND COLUMN_NAME = 'on_chain_status'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE royalty_deposit ADD COLUMN on_chain_status VARCHAR(16) NULL AFTER payer_wallet_pubkey',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'royalty_deposit' AND INDEX_NAME = 'uk_deposit_tx'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE royalty_deposit ADD UNIQUE KEY uk_deposit_tx (deposit_tx_signature)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'royalty_deposit' AND INDEX_NAME = 'uk_deposit_pool_pda'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE royalty_deposit ADD UNIQUE KEY uk_deposit_pool_pda (royalty_pool_pda)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geographic_subscription' AND COLUMN_NAME = 'pay_tx_signature'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE geographic_subscription ADD COLUMN pay_tx_signature VARCHAR(128) NULL AFTER intended_instruction',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geographic_subscription' AND COLUMN_NAME = 'paid_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE geographic_subscription ADD COLUMN paid_at DATETIME NULL AFTER pay_tx_signature',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geographic_subscription' AND COLUMN_NAME = 'payer_wallet_pubkey'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE geographic_subscription ADD COLUMN payer_wallet_pubkey VARCHAR(44) NULL AFTER paid_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geographic_subscription' AND COLUMN_NAME = 'on_chain_status'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE geographic_subscription ADD COLUMN on_chain_status VARCHAR(16) NULL AFTER payer_wallet_pubkey',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geographic_subscription' AND INDEX_NAME = 'uk_geosub_pda'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE geographic_subscription ADD UNIQUE KEY uk_geosub_pda (geographic_subscription_pda)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geographic_subscription' AND INDEX_NAME = 'uk_geosub_tx'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE geographic_subscription ADD UNIQUE KEY uk_geosub_tx (pay_tx_signature)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------
-- program v0.9.0 subscribe_geographic(geo_code, tier, months). One GeographicSubscription
-- PDA ["geo_sub", wallet, geo_code] per payer and zone is renewed in place (rows share a PDA).
-- onchain_tier is the u8 the program prices (1 local 5 · 2 city 15 · 3 country 35 · 4 regional 65 ·
-- 5 global 99 USDC). onchain_geo_code is the zone seed (2..24 of A-Z 0-9 _, starts with two letters).
-- ----------------------------------------------------------
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geo_tier' AND COLUMN_NAME = 'onchain_tier'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE geo_tier ADD COLUMN onchain_tier TINYINT NULL AFTER usdc_monthly',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE geo_tier SET onchain_tier = 1 WHERE code = 'local';
UPDATE geo_tier SET onchain_tier = 2 WHERE code = 'city';
UPDATE geo_tier SET onchain_tier = 3 WHERE code = 'country';
UPDATE geo_tier SET onchain_tier = 4 WHERE code = 'regional';
UPDATE geo_tier SET onchain_tier = 5 WHERE code = 'global';

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geo_region' AND COLUMN_NAME = 'onchain_geo_code'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE geo_region ADD COLUMN onchain_geo_code VARCHAR(24) NULL AFTER name',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE geo_region SET onchain_geo_code = UPPER(REPLACE(code, '-', '_')) WHERE onchain_geo_code IS NULL;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geographic_subscription' AND COLUMN_NAME = 'months'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE geographic_subscription ADD COLUMN months TINYINT NOT NULL DEFAULT 1 AFTER expires_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geographic_subscription' AND COLUMN_NAME = 'geo_code'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE geographic_subscription ADD COLUMN geo_code VARCHAR(24) NULL AFTER months',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geographic_subscription' AND COLUMN_NAME = 'onchain_expires_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE geographic_subscription ADD COLUMN onchain_expires_at DATETIME NULL AFTER on_chain_status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geographic_subscription' AND INDEX_NAME = 'uk_geosub_pda'
);
SET @ddl := IF(@idx > 0,
  'ALTER TABLE geographic_subscription DROP INDEX uk_geosub_pda',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geographic_subscription' AND INDEX_NAME = 'idx_geosub_pda'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE geographic_subscription ADD KEY idx_geosub_pda (geographic_subscription_pda)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- ----------------------------------------------------------
-- program v0.10.0 per-song RoyaltyPool ["royalty", track_id LE] with member splits
-- (create_royalty_pool), per-song deposit_royalties(track_id, amount) by the owner or the WAVE
-- agent, and pay_sync_license(track_id, deal_id, amount): 20% treasury / 80% song splits, one
-- SyncLicense PDA ["sync", track_id LE, deal_id LE] per deal. Deposits of one song share its
-- pool PDA, so that index is no longer unique.
-- ----------------------------------------------------------
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'track' AND COLUMN_NAME = 'pool_splits_json'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE track ADD COLUMN pool_splits_json TEXT NULL AFTER royalty_pool_pda',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'track' AND COLUMN_NAME = 'pool_tx_signature'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE track ADD COLUMN pool_tx_signature VARCHAR(128) NULL AFTER pool_splits_json',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'track' AND COLUMN_NAME = 'pool_activated_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE track ADD COLUMN pool_activated_at DATETIME NULL AFTER pool_tx_signature',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'track' AND INDEX_NAME = 'uk_track_pool_tx'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE track ADD UNIQUE KEY uk_track_pool_tx (pool_tx_signature)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'royalty_deposit' AND INDEX_NAME = 'uk_deposit_pool_pda'
);
SET @ddl := IF(@idx > 0,
  'ALTER TABLE royalty_deposit DROP INDEX uk_deposit_pool_pda',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'royalty_deposit' AND INDEX_NAME = 'idx_deposit_pool_pda'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE royalty_deposit ADD KEY idx_deposit_pool_pda (royalty_pool_pda)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS sync_license_deal (
  id BIGINT NOT NULL AUTO_INCREMENT,
  track_id BIGINT NOT NULL,
  licensee_name VARCHAR(120) NOT NULL,
  use_description VARCHAR(255) NOT NULL,
  amount_usdc DECIMAL(12,2) NOT NULL,
  artist_usdc DECIMAL(12,2) NOT NULL,
  eh8s_usdc DECIMAL(12,2) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'proposed',
  licensee_wallet_pubkey VARCHAR(44) NULL,
  sync_license_pda VARCHAR(44) NULL,
  pay_tx_signature VARCHAR(128) NULL,
  paid_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sync_deal_pda (sync_license_pda),
  UNIQUE KEY uk_sync_deal_tx (pay_tx_signature),
  KEY idx_sync_deal_track (track_id),
  CONSTRAINT fk_sync_deal_track FOREIGN KEY (track_id) REFERENCES track (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------
-- ATLAS tour routes. A geo_region covers venues by country_code (and city when set), so
-- only venues inside the band's active geographic subscriptions are routed. ATLAS writes one
-- tour_plan (generated_by = atlas) plus ordered tour_plan_stop rows (nearest-neighbour legs).
-- ----------------------------------------------------------
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geo_region' AND COLUMN_NAME = 'country_code'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE geo_region ADD COLUMN country_code CHAR(3) NULL AFTER onchain_geo_code',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geo_region' AND COLUMN_NAME = 'city'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE geo_region ADD COLUMN city VARCHAR(80) NULL AFTER country_code',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE geo_region SET country_code = 'MEX' WHERE code IN ('MEX', 'MEX-CDMX') AND country_code IS NULL;
UPDATE geo_region SET city = 'Mexico City' WHERE code = 'MEX-CDMX' AND city IS NULL;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tour_plan' AND COLUMN_NAME = 'window_start'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE tour_plan ADD COLUMN window_start DATE NULL AFTER route_note',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tour_plan' AND COLUMN_NAME = 'window_end'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE tour_plan ADD COLUMN window_end DATE NULL AFTER window_start',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tour_plan' AND COLUMN_NAME = 'total_km'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE tour_plan ADD COLUMN total_km DECIMAL(10,1) NULL AFTER window_end',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tour_plan' AND COLUMN_NAME = 'projected_income_usdc'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE tour_plan ADD COLUMN projected_income_usdc DECIMAL(12,2) NULL AFTER total_km',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tour_plan' AND COLUMN_NAME = 'generated_by'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE tour_plan ADD COLUMN generated_by VARCHAR(16) NOT NULL DEFAULT ''manual'' AFTER projected_income_usdc',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tour_plan' AND COLUMN_NAME = 'created_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE tour_plan ADD COLUMN created_at DATETIME NULL AFTER generated_by',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS tour_plan_stop (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tour_plan_id BIGINT NOT NULL,
  stop_order TINYINT NOT NULL,
  venue_id BIGINT NOT NULL,
  show_date DATE NULL,
  leg_km DECIMAL(8,1) NOT NULL DEFAULT 0.0,
  projected_income_usdc DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tour_stop_order (tour_plan_id, stop_order),
  CONSTRAINT fk_tour_stop_plan FOREIGN KEY (tour_plan_id) REFERENCES tour_plan (id),
  CONSTRAINT fk_tour_stop_venue FOREIGN KEY (venue_id) REFERENCES eh8s.venue (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
