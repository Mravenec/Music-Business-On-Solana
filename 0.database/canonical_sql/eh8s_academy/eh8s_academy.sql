-- Seed rows below are product catalog / bootstrap data, not demo data.
CREATE DATABASE IF NOT EXISTS eh8s_academy
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE eh8s_academy;

CREATE TABLE IF NOT EXISTS instructor_profile (
  id BIGINT NOT NULL AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_instructor_account (account_id),
  CONSTRAINT fk_instructor_account FOREIGN KEY (account_id) REFERENCES eh8s.account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS academy_plan (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(80) NOT NULL,
  usdc_monthly DECIMAL(12,2) NOT NULL,
  description VARCHAR(400) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_academy_plan_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS academy_subscription (
  id BIGINT NOT NULL AUTO_INCREMENT,
  musician_profile_id BIGINT NOT NULL,
  academy_plan_id BIGINT NOT NULL,
  instructor_profile_id BIGINT NULL,
  starts_at DATE NOT NULL,
  expires_at DATE NOT NULL,
  treasury_usdc DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  instructor_usdc DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  academy_subscription_pda VARCHAR(44) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_sub_musician FOREIGN KEY (musician_profile_id) REFERENCES eh8s.musician_profile (id),
  CONSTRAINT fk_sub_plan FOREIGN KEY (academy_plan_id) REFERENCES academy_plan (id),
  CONSTRAINT fk_sub_instructor FOREIGN KEY (instructor_profile_id) REFERENCES instructor_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS enigma_evaluation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  musician_profile_id BIGINT NOT NULL,
  week_start DATE NOT NULL,
  score TINYINT NOT NULL,
  notes VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_eval_musician FOREIGN KEY (musician_profile_id) REFERENCES eh8s.musician_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO academy_plan (code, name, usdc_monthly, description) VALUES
  ('basic', 'Basic', 30.00, '2 private lessons per week plus weekly Enigma Score'),
  ('band', 'Band', 55.00, 'Basic plus group rehearsals and SPP eligibility'),
  ('pro', 'Pro', 90.00, 'Band plus monthly mentoring, studio, and distribution')
ON DUPLICATE KEY UPDATE name = VALUES(name), usdc_monthly = VALUES(usdc_monthly), description = VALUES(description);

-- ──────────────────────────────────────────────────────────
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'academy_subscription' AND COLUMN_NAME = 'intended_instruction'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE academy_subscription ADD COLUMN intended_instruction VARCHAR(64) NOT NULL DEFAULT ''subscribe_academy'' AFTER academy_subscription_pda',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ──────────────────────────────────────────────────────────
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'academy_subscription' AND COLUMN_NAME = 'pay_tx_signature'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE academy_subscription ADD COLUMN pay_tx_signature VARCHAR(128) NULL AFTER intended_instruction',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'academy_subscription' AND COLUMN_NAME = 'paid_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE academy_subscription ADD COLUMN paid_at DATETIME NULL AFTER pay_tx_signature',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ──────────────────────────────────────────────────────────
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'academy_subscription' AND COLUMN_NAME = 'payer_wallet_pubkey'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE academy_subscription ADD COLUMN payer_wallet_pubkey VARCHAR(44) NULL AFTER paid_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'academy_subscription' AND COLUMN_NAME = 'on_chain_status'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE academy_subscription ADD COLUMN on_chain_status VARCHAR(16) NULL AFTER payer_wallet_pubkey',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'academy_subscription' AND INDEX_NAME = 'uk_academy_subscription_pda'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE academy_subscription ADD UNIQUE KEY uk_academy_subscription_pda (academy_subscription_pda)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'academy_subscription' AND INDEX_NAME = 'uk_academy_subscription_tx'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE academy_subscription ADD UNIQUE KEY uk_academy_subscription_tx (pay_tx_signature)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------
-- program v0.9.0 subscribe_academy(plan_type, months). One AcademySubscription PDA
-- ["academy_sub", wallet] per payer is renewed in place, so several rows share a PDA.
-- onchain_plan_type is the u8 the program prices (1 basic 30 · 2 band 55 · 3 pro 90 USDC).
-- months = months bought by this row; onchain_expires_at = expires_at read from the PDA after confirm.
-- ----------------------------------------------------------
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'academy_plan' AND COLUMN_NAME = 'onchain_plan_type'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE academy_plan ADD COLUMN onchain_plan_type TINYINT NULL AFTER usdc_monthly',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE academy_plan SET onchain_plan_type = 1 WHERE code = 'basic';
UPDATE academy_plan SET onchain_plan_type = 2 WHERE code = 'band';
UPDATE academy_plan SET onchain_plan_type = 3 WHERE code = 'pro';

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'academy_subscription' AND COLUMN_NAME = 'months'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE academy_subscription ADD COLUMN months TINYINT NOT NULL DEFAULT 1 AFTER expires_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'academy_subscription' AND COLUMN_NAME = 'onchain_expires_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE academy_subscription ADD COLUMN onchain_expires_at DATETIME NULL AFTER on_chain_status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'academy_subscription' AND INDEX_NAME = 'uk_academy_subscription_pda'
);
SET @ddl := IF(@idx > 0,
  'ALTER TABLE academy_subscription DROP INDEX uk_academy_subscription_pda',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'academy_subscription' AND INDEX_NAME = 'idx_academy_subscription_pda'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE academy_subscription ADD KEY idx_academy_subscription_pda (academy_subscription_pda)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------
-- NEXUS Score Enigma. A teacher or musician submits a recording link, rubric scores
-- (0..10 each) and notes; Claude returns score 0..100, strengths, errors, exercises and a
-- recommended level. source = manual (legacy weekly rows) or nexus_ai. applied_at is set once the
-- level recommendation was applied on-chain through the NEXUS level page.
-- ----------------------------------------------------------
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enigma_evaluation' AND COLUMN_NAME = 'evaluator_account_id'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE enigma_evaluation ADD COLUMN evaluator_account_id BIGINT NULL AFTER musician_profile_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enigma_evaluation' AND COLUMN_NAME = 'recording_url'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE enigma_evaluation ADD COLUMN recording_url VARCHAR(400) NULL AFTER notes',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enigma_evaluation' AND COLUMN_NAME = 'rubric_json'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE enigma_evaluation ADD COLUMN rubric_json VARCHAR(1000) NULL AFTER recording_url',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enigma_evaluation' AND COLUMN_NAME = 'strengths'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE enigma_evaluation ADD COLUMN strengths VARCHAR(1000) NULL AFTER rubric_json',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enigma_evaluation' AND COLUMN_NAME = 'errors_found'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE enigma_evaluation ADD COLUMN errors_found VARCHAR(1000) NULL AFTER strengths',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enigma_evaluation' AND COLUMN_NAME = 'exercises'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE enigma_evaluation ADD COLUMN exercises VARCHAR(1000) NULL AFTER errors_found',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enigma_evaluation' AND COLUMN_NAME = 'recommended_level'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE enigma_evaluation ADD COLUMN recommended_level TINYINT NULL AFTER exercises',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enigma_evaluation' AND COLUMN_NAME = 'source'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE enigma_evaluation ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT ''manual'' AFTER recommended_level',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enigma_evaluation' AND COLUMN_NAME = 'ai_model'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE enigma_evaluation ADD COLUMN ai_model VARCHAR(64) NULL AFTER source',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enigma_evaluation' AND COLUMN_NAME = 'applied_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE enigma_evaluation ADD COLUMN applied_at DATETIME NULL AFTER ai_model',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enigma_evaluation' AND CONSTRAINT_NAME = 'fk_eval_evaluator'
);
SET @ddl := IF(@fk = 0,
  'ALTER TABLE enigma_evaluation ADD CONSTRAINT fk_eval_evaluator FOREIGN KEY (evaluator_account_id) REFERENCES eh8s.account (id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------
-- academy video library. The owner publishes courses (course -> course_section ->
-- lesson). Lessons are links to hosted video (youtube | vimeo | bunny | cloudflare), stored as
-- the original URL plus the normalized embed URL; there is no upload. The API returns embed_url
-- only when the caller may watch (free preview, owner, active grant, or active paid plan with
-- Enigma level >= course.min_level). lesson_progress is per JWT account. course_access_grant
-- gives a wallet one course (course_id) or every course (course_id NULL) until expires_at.
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS course (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(160) NOT NULL,
  summary VARCHAR(1000) NULL,
  instrument_id BIGINT NULL,
  min_level TINYINT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'draft',
  author_account_id BIGINT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  published_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_course_status_sort (status, sort_order),
  CONSTRAINT fk_course_instrument FOREIGN KEY (instrument_id) REFERENCES eh8s.instrument (id),
  CONSTRAINT fk_course_author FOREIGN KEY (author_account_id) REFERENCES eh8s.account (id),
  CONSTRAINT ck_course_min_level CHECK (min_level BETWEEN 0 AND 5),
  CONSTRAINT ck_course_status CHECK (status IN ('draft', 'published', 'archived'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS course_section (
  id BIGINT NOT NULL AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  sort_order INT NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_course_section_order (course_id, sort_order),
  CONSTRAINT fk_course_section_course FOREIGN KEY (course_id) REFERENCES course (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lesson (
  id BIGINT NOT NULL AUTO_INCREMENT,
  section_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  description VARCHAR(2000) NULL,
  video_provider VARCHAR(16) NOT NULL,
  video_url VARCHAR(600) NOT NULL,
  embed_url VARCHAR(600) NOT NULL,
  duration_sec INT NULL,
  sort_order INT NOT NULL,
  is_free_preview TINYINT(1) NOT NULL DEFAULT 0,
  resources VARCHAR(2000) NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_lesson_order (section_id, sort_order),
  CONSTRAINT fk_lesson_section FOREIGN KEY (section_id) REFERENCES course_section (id),
  CONSTRAINT ck_lesson_provider CHECK (video_provider IN ('youtube', 'vimeo', 'bunny', 'cloudflare')),
  CONSTRAINT ck_lesson_duration CHECK (duration_sec IS NULL OR duration_sec >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lesson_progress (
  id BIGINT NOT NULL AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  lesson_id BIGINT NOT NULL,
  last_position_sec INT NOT NULL DEFAULT 0,
  completed_at DATETIME NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_lesson_progress (account_id, lesson_id),
  CONSTRAINT fk_lesson_progress_account FOREIGN KEY (account_id) REFERENCES eh8s.account (id),
  CONSTRAINT fk_lesson_progress_lesson FOREIGN KEY (lesson_id) REFERENCES lesson (id),
  CONSTRAINT ck_lesson_progress_position CHECK (last_position_sec >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS course_access_grant (
  id BIGINT NOT NULL AUTO_INCREMENT,
  course_id BIGINT NULL,
  course_scope BIGINT AS (IFNULL(course_id, 0)) PERSISTENT,
  wallet_pubkey VARCHAR(44) NOT NULL,
  granted_by_account_id BIGINT NOT NULL,
  note VARCHAR(200) NULL,
  expires_at DATETIME NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_access_grant (wallet_pubkey, course_scope),
  CONSTRAINT fk_course_access_course FOREIGN KEY (course_id) REFERENCES course (id),
  CONSTRAINT fk_course_access_granted_by FOREIGN KEY (granted_by_account_id) REFERENCES eh8s.account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------
-- teaching path. A level-5 instructor (or the owner) authors a course; instructors
-- cannot publish. course.review_status tracks the owner review: draft -> submitted ->
-- approved (published) or rejected (back to draft editing, review_note says why).
-- Owner-authored courses become 'approved' when the owner publishes them.
-- lesson.practice_prompt is the exercise a student records and sends to NEXUS;
-- enigma_evaluation.lesson_id links that NEXUS evaluation back to the lesson.
-- ----------------------------------------------------------
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND COLUMN_NAME = 'review_status'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE course ADD COLUMN review_status VARCHAR(16) NOT NULL DEFAULT ''draft'' AFTER status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND COLUMN_NAME = 'review_note'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE course ADD COLUMN review_note VARCHAR(500) NULL AFTER review_status',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND COLUMN_NAME = 'reviewed_by_account_id'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE course ADD COLUMN reviewed_by_account_id BIGINT NULL AFTER review_note',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND COLUMN_NAME = 'submitted_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE course ADD COLUMN submitted_at DATETIME NULL AFTER reviewed_by_account_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND COLUMN_NAME = 'reviewed_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE course ADD COLUMN reviewed_at DATETIME NULL AFTER submitted_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ck := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND CONSTRAINT_NAME = 'ck_course_review_status'
);
SET @ddl := IF(@ck = 0,
  'ALTER TABLE course ADD CONSTRAINT ck_course_review_status CHECK (review_status IN (''draft'', ''submitted'', ''approved'', ''rejected''))',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND CONSTRAINT_NAME = 'fk_course_reviewed_by'
);
SET @ddl := IF(@fk = 0,
  'ALTER TABLE course ADD CONSTRAINT fk_course_reviewed_by FOREIGN KEY (reviewed_by_account_id) REFERENCES eh8s.account (id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND INDEX_NAME = 'idx_course_review'
);
SET @ddl := IF(@idx = 0,
  'ALTER TABLE course ADD KEY idx_course_review (review_status, submitted_at)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE course SET review_status = 'approved' WHERE status = 'published' AND review_status = 'draft';

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lesson' AND COLUMN_NAME = 'practice_prompt'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE lesson ADD COLUMN practice_prompt VARCHAR(1000) NULL AFTER resources',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enigma_evaluation' AND COLUMN_NAME = 'lesson_id'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE enigma_evaluation ADD COLUMN lesson_id BIGINT NULL AFTER musician_profile_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enigma_evaluation' AND CONSTRAINT_NAME = 'fk_eval_lesson'
);
SET @ddl := IF(@fk = 0,
  'ALTER TABLE enigma_evaluation ADD CONSTRAINT fk_eval_lesson FOREIGN KEY (lesson_id) REFERENCES lesson (id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;