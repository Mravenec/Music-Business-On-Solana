-- Epic 76 / APP-76-1: official lesson media integrity columns on lesson.
-- Adds nullable content_hash, storage_ref, key_id for encrypted host objects.
-- Does NOT drop or alter video_url / embed_url — legacy link lessons stay readable.
-- Load after eh8s_academy/eh8s_academy.sql so lesson already exists.

USE eh8s_academy;

-- SHA-256 hex of official ciphertext (NULL = legacy URL-only lesson).
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson' AND COLUMN_NAME = 'content_hash'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE lesson ADD COLUMN content_hash CHAR(64) NULL AFTER video_url',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Host object key / path (Bunny / Cloudflare / S3); rebuildable from storage.
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson' AND COLUMN_NAME = 'storage_ref'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE lesson ADD COLUMN storage_ref VARCHAR(600) NULL AFTER content_hash',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Encryption key identifier for the ciphertext at storage_ref.
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson' AND COLUMN_NAME = 'key_id'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE lesson ADD COLUMN key_id VARCHAR(64) NULL AFTER storage_ref',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
