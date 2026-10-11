-- Epic 74 / APP-74-1: MariaDB mirrors for future Anchor PDAs (studio_admin grant
-- and partner share windows). Cache only — does not deploy a program.
-- Does NOT alter or drop eh8s_role.* or eh8s.studio_partner (JWT/books paths stay).
-- Load after eh8s_onchain/eh8s_onchain.sql so the schema already exists.

USE eh8s_onchain;

-- Mirror of a future StudioRoleGrant PDA (grant/revoke studio_admin on-chain).
-- One row per confirmed grant or revoke signature; active when revoked_at IS NULL.
CREATE TABLE IF NOT EXISTS studio_role_grant (
  id BIGINT NOT NULL AUTO_INCREMENT,
  wallet_pubkey VARCHAR(44) NOT NULL,
  role VARCHAR(32) NOT NULL,
  granted_at DATETIME NOT NULL,
  revoked_at DATETIME NULL,
  tx_signature VARCHAR(128) NOT NULL,
  pda VARCHAR(44) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_studio_role_grant_pda (pda),
  UNIQUE KEY uk_studio_role_grant_tx (tx_signature),
  KEY idx_studio_role_grant_wallet (wallet_pubkey),
  KEY idx_studio_role_grant_active (wallet_pubkey, role, revoked_at),
  CONSTRAINT ck_studio_role_grant_role CHECK (role = 'studio_admin')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Mirror of a future PartnerShare PDA (signed share_bps + time window).
-- Books/reconcile read these windows after chain confirm; hub studio_partner stays.
CREATE TABLE IF NOT EXISTS partner_share (
  id BIGINT NOT NULL AUTO_INCREMENT,
  wallet_pubkey VARCHAR(44) NOT NULL,
  share_bps INT NOT NULL,
  started_at DATETIME NOT NULL,
  ended_at DATETIME NULL,
  tx_signature VARCHAR(128) NOT NULL,
  pda VARCHAR(44) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_partner_share_pda (pda),
  UNIQUE KEY uk_partner_share_tx (tx_signature),
  KEY idx_partner_share_wallet (wallet_pubkey),
  KEY idx_partner_share_window (wallet_pubkey, started_at, ended_at),
  CONSTRAINT ck_partner_share_bps CHECK (share_bps BETWEEN 1 AND 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
