CREATE DATABASE IF NOT EXISTS eh8s_onchain
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE eh8s_onchain;

-- BagsCreatorFund dual-network client settings. No on-chain program is deployed here.
CREATE TABLE IF NOT EXISTS chain_config (
  id BIGINT NOT NULL AUTO_INCREMENT,
  network VARCHAR(32) NOT NULL,
  rpc_url VARCHAR(512) NOT NULL,
  program_id_devnet VARCHAR(64) NOT NULL,
  program_id_mainnet VARCHAR(64) NULL,
  usdc_mint VARCHAR(64) NOT NULL,
  env_network_key VARCHAR(64) NOT NULL DEFAULT 'VITE_NETWORK',
  env_rpc_key VARCHAR(64) NOT NULL DEFAULT 'VITE_SOLANA_RPC',
  env_program_id_devnet_key VARCHAR(64) NOT NULL DEFAULT 'VITE_BCF_PROGRAM_ID_DEVNET',
  env_program_id_mainnet_key VARCHAR(64) NOT NULL DEFAULT 'VITE_BCF_PROGRAM_ID_MAINNET',
  bags_api_base VARCHAR(512) NULL,
  watcher_url VARCHAR(512) NULL,
  squads_multisig VARCHAR(64) NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chain_config_network (network)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO chain_config (
  network, rpc_url, program_id_devnet, program_id_mainnet, usdc_mint,
  env_network_key, env_rpc_key, env_program_id_devnet_key, env_program_id_mainnet_key,
  bags_api_base, watcher_url, squads_multisig, is_active
) VALUES (
  'devnet',
  'https://api.devnet.solana.com',
  'GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG',
  NULL,
  '4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU',
  'VITE_NETWORK',
  'VITE_SOLANA_RPC',
  'VITE_BCF_PROGRAM_ID_DEVNET',
  'VITE_BCF_PROGRAM_ID_MAINNET',
  'https://public-api-v2.bags.fm/api/v1',
  NULL,
  NULL,
  1
)
ON DUPLICATE KEY UPDATE
  rpc_url = VALUES(rpc_url),
  program_id_devnet = VALUES(program_id_devnet),
  usdc_mint = VALUES(usdc_mint),
  env_network_key = VALUES(env_network_key),
  env_rpc_key = VALUES(env_rpc_key),
  bags_api_base = VALUES(bags_api_base),
  is_active = VALUES(is_active);

-- ──────────────────────────────────────────────────────────
-- Seed rows below are product catalog / bootstrap data, not demo data.
CREATE TABLE IF NOT EXISTS devnet_pay_marker (
  id BIGINT NOT NULL AUTO_INCREMENT,
  kind VARCHAR(16) NOT NULL,
  wallet_pubkey VARCHAR(44) NOT NULL,
  amount_usdc DECIMAL(12,2) NULL,
  related_claim_id BIGINT NULL,
  related_subscription_id BIGINT NULL,
  tx_signature VARCHAR(128) NULL,
  status VARCHAR(16) NOT NULL,
  intended_instruction VARCHAR(64) NOT NULL,
  note VARCHAR(500) NULL,
  recorded_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_pay_marker_claim FOREIGN KEY (related_claim_id) REFERENCES eh8s.pending_claim (id),
  CONSTRAINT fk_pay_marker_subscription FOREIGN KEY (related_subscription_id) REFERENCES eh8s_academy.academy_subscription (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ──────────────────────────────────────────────────────────
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chain_config' AND COLUMN_NAME = 'anchor_program_name'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE chain_config ADD COLUMN anchor_program_name VARCHAR(80) NULL AFTER program_id_mainnet',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chain_config' AND COLUMN_NAME = 'anchor_scaffold_path'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE chain_config ADD COLUMN anchor_scaffold_path VARCHAR(240) NULL AFTER anchor_program_name',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chain_config' AND COLUMN_NAME = 'anchor_idl_version'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE chain_config ADD COLUMN anchor_idl_version VARCHAR(32) NULL AFTER anchor_scaffold_path',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE chain_config
SET anchor_program_name = COALESCE(anchor_program_name, 'eh8s_devnet'),
    anchor_scaffold_path = COALESCE(anchor_scaffold_path, '4.anchor/eh8s-devnet'),
    anchor_idl_version = COALESCE(anchor_idl_version, '0.1.0')
WHERE network = 'devnet' AND is_active = 1;

-- ──────────────────────────────────────────────────────────
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chain_config' AND COLUMN_NAME = 'owner_wallet_pubkey'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE chain_config ADD COLUMN owner_wallet_pubkey VARCHAR(44) NOT NULL DEFAULT ''7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC'' AFTER squads_multisig',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chain_config' AND COLUMN_NAME = 'protocol_fee_bps'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE chain_config ADD COLUMN protocol_fee_bps INT NOT NULL DEFAULT 1500 AFTER owner_wallet_pubkey',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE chain_config
SET owner_wallet_pubkey = '7QVKJjAD4AwZzgSNuDsF7ZSKS8PpYiRfGSvGu9h1DUuC',
    protocol_fee_bps = COALESCE(protocol_fee_bps, 1500)
WHERE network = 'devnet' AND is_active = 1;

-- ──────────────────────────────────────────────────────────
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chain_config' AND COLUMN_NAME = 'anchor_idl_hash'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE chain_config ADD COLUMN anchor_idl_hash VARCHAR(128) NULL AFTER anchor_idl_version',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chain_config' AND COLUMN_NAME = 'instruction_registry_json'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE chain_config ADD COLUMN instruction_registry_json MEDIUMTEXT NULL AFTER anchor_idl_hash',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chain_config' AND COLUMN_NAME = 'program_deployed_at'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE chain_config ADD COLUMN program_deployed_at DATETIME NULL AFTER instruction_registry_json',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Prefer Circle DevNet USDC mint for real DevNet wallet tests (override mainnet mint leftover).
UPDATE chain_config
SET usdc_mint = '4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU',
    program_id_devnet = 'GUY7oRUUFHdiNHqoJgUSBv6anqaftAdfhrTbaD5nMgzG',
    instruction_registry_json = COALESCE(
      instruction_registry_json,
      JSON_ARRAY(
        'initialize_config',
        'upsert_musician_profile',
        'subscribe_academy',
        'settle_concert',
        'claim_royalties',
        'deposit_royalties',
        'subscribe_geographic',
        'credit_pending_claim'
      )
    ),
    anchor_program_name = COALESCE(anchor_program_name, 'eh8s_devnet'),
    anchor_scaffold_path = COALESCE(anchor_scaffold_path, '4.anchor/eh8s-devnet'),
    anchor_idl_version = COALESCE(anchor_idl_version, '0.2.0')
WHERE network = 'devnet' AND is_active = 1;

-- ──────────────────────────────────────────────────────────
-- hardened program v0.3.0 first deployed to DevNet (slot 504604312),
-- config initialized (owner treasury ATA + vault ATA pinned on-chain).
-- credit_pending_claim removed; settle_concert(concert_id, gross) computes the fee on-chain.
-- ──────────────────────────────────────────────────────────
UPDATE chain_config
SET instruction_registry_json = JSON_ARRAY(
      'initialize_config',
      'upsert_musician_profile',
      'subscribe_academy',
      'settle_concert',
      'claim_royalties',
      'deposit_royalties',
      'subscribe_geographic'
    ),
    anchor_idl_version = '0.3.0',
    program_deployed_at = COALESCE(program_deployed_at, '2026-09-27 01:10:00')
WHERE network = 'devnet' AND is_active = 1;
-- ──────────────────────────────────────────────────────────
-- program v0.4.0 adds BandVault (create_band / update_spp_weights) and splits the
-- settle_concert(concert_id, gross, expenses) pool per member weight on-chain.
-- ──────────────────────────────────────────────────────────
UPDATE chain_config
SET instruction_registry_json = JSON_ARRAY(
      'initialize_config',
      'upsert_musician_profile',
      'subscribe_academy',
      'create_band',
      'update_spp_weights',
      'settle_concert',
      'claim_royalties',
      'deposit_royalties',
      'subscribe_geographic'
    ),
    anchor_idl_version = '0.4.0'
WHERE network = 'devnet' AND is_active = 1;
-- ──────────────────────────────────────────────────────────
-- program v0.5.0 moves protocol fees into the ["treasury"] PDA USDC ATA
-- (init_treasury) and adds owner-only withdraw_treasury. Each verified withdraw is one row.
-- ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS treasury_withdrawal (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner_wallet_pubkey VARCHAR(44) NOT NULL,
  amount_usdc DECIMAL(12,2) NOT NULL,
  tx_signature VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_treasury_withdrawal_tx (tx_signature)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE chain_config
SET instruction_registry_json = JSON_ARRAY(
      'initialize_config',
      'init_treasury',
      'withdraw_treasury',
      'upsert_musician_profile',
      'subscribe_academy',
      'create_band',
      'update_spp_weights',
      'settle_concert',
      'claim_royalties',
      'deposit_royalties',
      'subscribe_geographic'
    ),
    anchor_idl_version = '0.5.0'
WHERE network = 'devnet' AND is_active = 1;

-- ----------------------------------------------------------
-- program v0.6.0 adds authorize_agent (AgentAuthority PDA ["agent", wallet]) and the
-- agent-only update_musician_level; upsert_musician_profile(instrument, country) drops the level.
-- ----------------------------------------------------------
UPDATE chain_config
SET instruction_registry_json = JSON_ARRAY(
      'initialize_config',
      'init_treasury',
      'withdraw_treasury',
      'authorize_agent',
      'upsert_musician_profile',
      'update_musician_level',
      'subscribe_academy',
      'create_band',
      'update_spp_weights',
      'settle_concert',
      'claim_royalties',
      'deposit_royalties',
      'subscribe_geographic'
    ),
    anchor_idl_version = '0.6.0'
WHERE network = 'devnet' AND is_active = 1;

-- ----------------------------------------------------------
-- program v0.7.0 adds VenueListing (register_venue / approve_venue) and the
-- VenueAccessToken escrow (propose / confirm / cancel / settle_booking). settle_concert stays.
-- ----------------------------------------------------------
UPDATE chain_config
SET instruction_registry_json = JSON_ARRAY(
      'initialize_config',
      'init_treasury',
      'withdraw_treasury',
      'authorize_agent',
      'upsert_musician_profile',
      'update_musician_level',
      'subscribe_academy',
      'create_band',
      'update_spp_weights',
      'register_venue',
      'approve_venue',
      'propose_booking',
      'confirm_booking',
      'cancel_booking',
      'settle_booking',
      'settle_concert',
      'claim_royalties',
      'deposit_royalties',
      'subscribe_geographic'
    ),
    anchor_idl_version = '0.7.0'
WHERE network = 'devnet' AND is_active = 1;

-- ----------------------------------------------------------
-- program v0.8.0 - owner multisig governance. init_governance creates the
-- ["governance"] PDA (1..5 signers, threshold); after that withdraw_treasury and authorize_agent
-- only run through M-of-N proposals (propose -> approve_proposal -> execute_*_proposal).
-- Each verified transaction is one row; the signer set is replaced when a signer proposal executes.
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS governance (
  id BIGINT NOT NULL AUTO_INCREMENT,
  program_id VARCHAR(44) NOT NULL,
  governance_pda VARCHAR(44) NOT NULL,
  threshold TINYINT NOT NULL,
  epoch INT NOT NULL DEFAULT 0,
  initialized_by_wallet VARCHAR(44) NOT NULL,
  init_tx_signature VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_governance_pda (governance_pda),
  UNIQUE KEY uk_governance_init_tx (init_tx_signature)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS governance_signer (
  id BIGINT NOT NULL AUTO_INCREMENT,
  governance_id BIGINT NOT NULL,
  wallet_pubkey VARCHAR(44) NOT NULL,
  position TINYINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_governance_signer (governance_id, wallet_pubkey),
  CONSTRAINT fk_governance_signer_governance FOREIGN KEY (governance_id) REFERENCES governance (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- kind: withdraw | authorize_agent | update_signers · status: open | executed | stale
CREATE TABLE IF NOT EXISTS governance_proposal (
  id BIGINT NOT NULL AUTO_INCREMENT,
  governance_id BIGINT NOT NULL,
  onchain_proposal_id BIGINT NOT NULL,
  proposal_pda VARCHAR(44) NOT NULL,
  epoch INT NOT NULL,
  kind VARCHAR(24) NOT NULL,
  proposer_wallet VARCHAR(44) NOT NULL,
  amount_usdc DECIMAL(12,2) NULL,
  target_pubkey VARCHAR(44) NULL,
  permissions TINYINT NULL,
  new_signers_csv VARCHAR(240) NULL,
  new_threshold TINYINT NULL,
  status VARCHAR(16) NOT NULL,
  propose_tx_signature VARCHAR(128) NOT NULL,
  execute_tx_signature VARCHAR(128) NULL,
  executed_by_wallet VARCHAR(44) NULL,
  created_at DATETIME NOT NULL,
  executed_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_governance_proposal_onchain (governance_id, onchain_proposal_id),
  UNIQUE KEY uk_governance_proposal_pda (proposal_pda),
  UNIQUE KEY uk_governance_proposal_tx (propose_tx_signature),
  UNIQUE KEY uk_governance_proposal_execute_tx (execute_tx_signature),
  KEY idx_governance_proposal_status (status),
  CONSTRAINT fk_governance_proposal_governance FOREIGN KEY (governance_id) REFERENCES governance (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The proposer's approval is recorded with the propose transaction.
CREATE TABLE IF NOT EXISTS governance_approval (
  id BIGINT NOT NULL AUTO_INCREMENT,
  proposal_id BIGINT NOT NULL,
  signer_wallet VARCHAR(44) NOT NULL,
  tx_signature VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_governance_approval_signer (proposal_id, signer_wallet),
  KEY idx_governance_approval_tx (tx_signature),
  CONSTRAINT fk_governance_approval_proposal FOREIGN KEY (proposal_id) REFERENCES governance_proposal (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A withdraw executed through a proposal is also a treasury_withdrawal row (destination owner).
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treasury_withdrawal' AND COLUMN_NAME = 'proposal_id'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE treasury_withdrawal ADD COLUMN proposal_id BIGINT NULL AFTER tx_signature',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treasury_withdrawal'
    AND CONSTRAINT_NAME = 'fk_treasury_withdrawal_proposal'
);
SET @ddl := IF(@fk = 0,
  'ALTER TABLE treasury_withdrawal ADD CONSTRAINT fk_treasury_withdrawal_proposal FOREIGN KEY (proposal_id) REFERENCES governance_proposal (id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE chain_config
SET instruction_registry_json = JSON_ARRAY(
      'initialize_config',
      'init_treasury',
      'withdraw_treasury',
      'authorize_agent',
      'init_governance',
      'propose',
      'approve_proposal',
      'execute_withdraw_proposal',
      'execute_agent_proposal',
      'execute_signers_proposal',
      'upsert_musician_profile',
      'update_musician_level',
      'subscribe_academy',
      'create_band',
      'update_spp_weights',
      'register_venue',
      'approve_venue',
      'propose_booking',
      'confirm_booking',
      'cancel_booking',
      'settle_booking',
      'settle_concert',
      'claim_royalties',
      'deposit_royalties',
      'subscribe_geographic'
    ),
    anchor_idl_version = '0.8.0'
WHERE network = 'devnet' AND is_active = 1;

-- an authorize_agent proposal names the ops_agent it targets (agent_authority needs it).
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'governance_proposal' AND COLUMN_NAME = 'agent_code'
);
SET @ddl := IF(@col = 0,
  'ALTER TABLE governance_proposal ADD COLUMN agent_code VARCHAR(32) NULL AFTER permissions',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------
-- program v0.9.0 (DevNet slot 504852204) - subscribe_academy(plan_type, months) on
-- ["academy_sub", wallet] and subscribe_geographic(geo_code, tier, months) on
-- ["geo_sub", wallet, geo_code]. Price x months is checked on-chain; renewals extend expires_at.
-- ----------------------------------------------------------
UPDATE chain_config
SET anchor_idl_version = '0.9.0'
WHERE network = 'devnet' AND is_active = 1;
-- ----------------------------------------------------------
-- program v0.10.0 (DevNet slot 504863281) - create_royalty_pool, per-song
-- deposit_royalties(track_id, amount), pay_sync_license(track_id, deal_id, amount) 80/20.
-- ----------------------------------------------------------
UPDATE chain_config
SET instruction_registry_json = JSON_ARRAY(
      'initialize_config',
      'init_treasury',
      'withdraw_treasury',
      'authorize_agent',
      'init_governance',
      'propose',
      'approve_proposal',
      'execute_withdraw_proposal',
      'execute_agent_proposal',
      'execute_signers_proposal',
      'upsert_musician_profile',
      'update_musician_level',
      'subscribe_academy',
      'create_band',
      'update_spp_weights',
      'register_venue',
      'approve_venue',
      'propose_booking',
      'confirm_booking',
      'cancel_booking',
      'settle_booking',
      'settle_concert',
      'claim_royalties',
      'create_royalty_pool',
      'deposit_royalties',
      'pay_sync_license',
      'subscribe_geographic'
    ),
    anchor_idl_version = '0.10.0'
WHERE network = 'devnet' AND is_active = 1;