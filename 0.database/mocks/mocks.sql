-- Demo/test fixture data for eh8s_harness. Loaded only by
-- scripts/load_mocks.py, never by canonical_sql/00_run_all.sql /
-- setup_db.py. Epic 29: consolidated from mocks/eh8s.sql,
-- mocks/eh8s_catalog.sql, mocks/eh8s_ops.sql into this single file,
-- matching odsProject's real convention (one mocks file for every
-- schema, USE <schema>; per section) — see
-- .ai/rules/database-mocks-separation.md. Sections run in
-- dependency order: eh8s (hub, creates the fake band/musicians)
-- first, then eh8s_catalog and eh8s_ops (reference eh8s's rows).

-- ══════════════════════════════════════════════════════════
-- 1. eh8s (hub) — "Noche Oscura" / "Semilla Norte" demo bands
-- ══════════════════════════════════════════════════════════
-- Demo data for eh8s (hub) — the "Noche Oscura" fake band scenario:
-- 4 fake musician accounts, 2 fake bands, rehearsals, one closed SPP cycle,
-- one fake venue booking/concert. Loaded only by scripts/load_mocks.py,
-- never by canonical_sql/00_run_all.sql. Depends on canonical_sql's own
-- catalog rows already being present (instrument, enigma_level,
-- contract_type) from setup_db.py.
USE eh8s;

INSERT INTO account (email, display_name, role, country_code, wallet_pubkey) VALUES
  ('jorge@eh8s.test', 'Jorge', 'musician', 'MEX', NULL),
  ('valeria@eh8s.test', 'Valeria', 'musician', 'MEX', NULL),
  ('marco@eh8s.test', 'Marco', 'musician', 'MEX', NULL),
  ('sofia@eh8s.test', 'Sofia', 'musician', 'MEX', NULL)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), role = VALUES(role);

INSERT INTO band (code, name, band_type, spp_enabled, geo_code, band_vault_pda) VALUES
  ('noche-oscura', 'Noche Oscura', 'active', 1, 'MEX', NULL),
  ('semilla-norte', 'Semilla Norte', 'seed', 0, 'MEX', NULL)
ON DUPLICATE KEY UPDATE name = VALUES(name), band_type = VALUES(band_type), spp_enabled = VALUES(spp_enabled);

INSERT INTO musician_profile (account_id, instrument_id, enigma_level_id, enigma_score, musician_profile_pda)
SELECT a.id, i.id, l.id, 88, NULL
FROM account a
JOIN instrument i ON i.code = 'guitar'
JOIN enigma_level l ON l.level_number = 4
WHERE a.email = 'jorge@eh8s.test'
ON DUPLICATE KEY UPDATE enigma_score = VALUES(enigma_score);

INSERT INTO musician_profile (account_id, instrument_id, enigma_level_id, enigma_score, musician_profile_pda)
SELECT a.id, i.id, l.id, 90, NULL
FROM account a
JOIN instrument i ON i.code = 'voice'
JOIN enigma_level l ON l.level_number = 4
WHERE a.email = 'valeria@eh8s.test'
ON DUPLICATE KEY UPDATE enigma_score = VALUES(enigma_score);

INSERT INTO musician_profile (account_id, instrument_id, enigma_level_id, enigma_score, musician_profile_pda)
SELECT a.id, i.id, l.id, 72, NULL
FROM account a
JOIN instrument i ON i.code = 'drums'
JOIN enigma_level l ON l.level_number = 4
WHERE a.email = 'marco@eh8s.test'
ON DUPLICATE KEY UPDATE enigma_score = VALUES(enigma_score);

INSERT INTO musician_profile (account_id, instrument_id, enigma_level_id, enigma_score, musician_profile_pda)
SELECT a.id, i.id, l.id, 70, NULL
FROM account a
JOIN instrument i ON i.code = 'bass'
JOIN enigma_level l ON l.level_number = 3
WHERE a.email = 'sofia@eh8s.test'
ON DUPLICATE KEY UPDATE enigma_score = VALUES(enigma_score);

INSERT INTO band_member (band_id, musician_profile_id, role_in_band, joined_at)
SELECT b.id, m.id, 'Guitar', '2026-01-15'
FROM band b
JOIN musician_profile m
JOIN account a ON a.id = m.account_id
WHERE b.code = 'noche-oscura' AND a.email = 'jorge@eh8s.test'
ON DUPLICATE KEY UPDATE role_in_band = VALUES(role_in_band);

INSERT INTO band_member (band_id, musician_profile_id, role_in_band, joined_at)
SELECT b.id, m.id, 'Voice', '2026-01-15'
FROM band b
JOIN musician_profile m
JOIN account a ON a.id = m.account_id
WHERE b.code = 'noche-oscura' AND a.email = 'valeria@eh8s.test'
ON DUPLICATE KEY UPDATE role_in_band = VALUES(role_in_band);

INSERT INTO band_member (band_id, musician_profile_id, role_in_band, joined_at)
SELECT b.id, m.id, 'Drums', '2026-01-20'
FROM band b
JOIN musician_profile m
JOIN account a ON a.id = m.account_id
WHERE b.code = 'noche-oscura' AND a.email = 'marco@eh8s.test'
ON DUPLICATE KEY UPDATE role_in_band = VALUES(role_in_band);

INSERT INTO band_member (band_id, musician_profile_id, role_in_band, joined_at)
SELECT b.id, m.id, 'Bass', '2026-04-01'
FROM band b
JOIN musician_profile m
JOIN account a ON a.id = m.account_id
WHERE b.code = 'noche-oscura' AND a.email = 'sofia@eh8s.test'
ON DUPLICATE KEY UPDATE role_in_band = VALUES(role_in_band);

INSERT INTO rehearsal_session (band_id, scheduled_at, notes)
SELECT b.id, '2026-05-10 18:00:00', 'Q2 cycle rehearsal'
FROM band b
WHERE b.code = 'noche-oscura'
  AND NOT EXISTS (
    SELECT 1 FROM rehearsal_session r
    WHERE r.band_id = b.id AND r.scheduled_at = '2026-05-10 18:00:00'
  );

INSERT INTO rehearsal_checkin (
  rehearsal_session_id, musician_profile_id, arrived_at, late_minutes, attendance_points, punctuality_points
)
SELECT r.id, m.id, '2026-05-10 17:55:00', 0, 10, 10
FROM rehearsal_session r
JOIN band b ON b.id = r.band_id
JOIN musician_profile m
JOIN account a ON a.id = m.account_id
WHERE b.code = 'noche-oscura'
  AND r.scheduled_at = '2026-05-10 18:00:00'
  AND a.email = 'jorge@eh8s.test'
ON DUPLICATE KEY UPDATE attendance_points = VALUES(attendance_points), punctuality_points = VALUES(punctuality_points);

INSERT INTO rehearsal_checkin (
  rehearsal_session_id, musician_profile_id, arrived_at, late_minutes, attendance_points, punctuality_points
)
SELECT r.id, m.id, '2026-05-10 18:22:00', 22, 10, 0
FROM rehearsal_session r
JOIN band b ON b.id = r.band_id
JOIN musician_profile m
JOIN account a ON a.id = m.account_id
WHERE b.code = 'noche-oscura'
  AND r.scheduled_at = '2026-05-10 18:00:00'
  AND a.email = 'marco@eh8s.test'
ON DUPLICATE KEY UPDATE late_minutes = VALUES(late_minutes), punctuality_points = VALUES(punctuality_points);

INSERT INTO spp_cycle (band_id, code, status, started_at, closed_at, intended_instruction)
SELECT b.id, 'Q2', 'closed', '2026-04-01', '2026-06-01', 'update_spp_weights'
FROM band b
WHERE b.code = 'noche-oscura'
ON DUPLICATE KEY UPDATE status = VALUES(status), intended_instruction = VALUES(intended_instruction);

INSERT INTO spp_member_score (
  spp_cycle_id, musician_profile_id,
  attendance_points, punctuality_points, creative_points, skill_points, concert_points,
  total_points, share_bps
)
SELECT c.id, m.id, 38, 14, 24, 12, 7, 95, 3800
FROM spp_cycle c
JOIN band b ON b.id = c.band_id
JOIN musician_profile m
JOIN account a ON a.id = m.account_id
WHERE b.code = 'noche-oscura' AND c.code = 'Q2' AND a.email = 'jorge@eh8s.test'
ON DUPLICATE KEY UPDATE total_points = VALUES(total_points), share_bps = VALUES(share_bps);

INSERT INTO spp_member_score (
  spp_cycle_id, musician_profile_id,
  attendance_points, punctuality_points, creative_points, skill_points, concert_points,
  total_points, share_bps
)
SELECT c.id, m.id, 36, 13, 18, 14, 7, 88, 3520
FROM spp_cycle c
JOIN band b ON b.id = c.band_id
JOIN musician_profile m
JOIN account a ON a.id = m.account_id
WHERE b.code = 'noche-oscura' AND c.code = 'Q2' AND a.email = 'valeria@eh8s.test'
ON DUPLICATE KEY UPDATE total_points = VALUES(total_points), share_bps = VALUES(share_bps);

INSERT INTO spp_member_score (
  spp_cycle_id, musician_profile_id,
  attendance_points, punctuality_points, creative_points, skill_points, concert_points,
  total_points, share_bps
)
SELECT c.id, m.id, 20, 6, 10, 8, 6, 50, 2000
FROM spp_cycle c
JOIN band b ON b.id = c.band_id
JOIN musician_profile m
JOIN account a ON a.id = m.account_id
WHERE b.code = 'noche-oscura' AND c.code = 'Q2' AND a.email = 'marco@eh8s.test'
ON DUPLICATE KEY UPDATE total_points = VALUES(total_points), share_bps = VALUES(share_bps);

INSERT INTO spp_member_score (
  spp_cycle_id, musician_profile_id,
  attendance_points, punctuality_points, creative_points, skill_points, concert_points,
  total_points, share_bps
)
SELECT c.id, m.id, 6, 3, 4, 2, 2, 17, 680
FROM spp_cycle c
JOIN band b ON b.id = c.band_id
JOIN musician_profile m
JOIN account a ON a.id = m.account_id
WHERE b.code = 'noche-oscura' AND c.code = 'Q2' AND a.email = 'sofia@eh8s.test'
ON DUPLICATE KEY UPDATE total_points = VALUES(total_points), share_bps = VALUES(share_bps);

INSERT INTO venue (
  code, name, city, country_code, capacity, suggested_ticket_usdc,
  latitude, longitude, pin_status, eh8s_rating, sound_included, preferred_genres,
  contract_type_id, venue_listing_pda
)
SELECT 'bar-la-cueva', 'Bar La Cueva', 'Mexico City', 'MEX', 180, 10.00,
       19.4326080, -99.1332090, 'partner', 5, 1, 'rock, blues',
       ct.id, NULL
FROM contract_type ct
WHERE ct.code = 'versus'
ON DUPLICATE KEY UPDATE name = VALUES(name), pin_status = VALUES(pin_status);

INSERT INTO venue (
  code, name, city, country_code, capacity, suggested_ticket_usdc,
  latitude, longitude, pin_status, eh8s_rating, sound_included, preferred_genres,
  contract_type_id, venue_listing_pda
)
SELECT 'foro-disponible', 'Foro Disponible', 'Guadalajara', 'MEX', 400, 12.00,
       20.6596980, -103.3496090, 'available', 4, 1, 'indie, pop',
       ct.id, NULL
FROM contract_type ct
WHERE ct.code = 'door'
ON DUPLICATE KEY UPDATE name = VALUES(name), pin_status = VALUES(pin_status);

INSERT INTO booking (venue_id, band_id, show_date, pipeline_week, status, venue_access_token_pda, contract_hash)
SELECT v.id, b.id, '2026-06-14', 0, 'played', NULL, NULL
FROM venue v
JOIN band b ON b.code = 'noche-oscura'
WHERE v.code = 'bar-la-cueva'
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO concert (booking_id, venue_id, band_id, spp_cycle_id, status, concert_settlement_pda)
SELECT bk.id, bk.venue_id, bk.band_id, c.id, 'ready_to_settle', NULL
FROM booking bk
JOIN venue v ON v.id = bk.venue_id
JOIN band b ON b.id = bk.band_id
JOIN spp_cycle c ON c.band_id = b.id AND c.code = 'Q2'
WHERE v.code = 'bar-la-cueva' AND b.code = 'noche-oscura' AND bk.show_date = '2026-06-14'
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO concert_expense (concert_id, label, amount_usdc)
SELECT co.id, 'Production', 200.00
FROM concert co
JOIN booking bk ON bk.id = co.booking_id
JOIN venue v ON v.id = bk.venue_id
WHERE v.code = 'bar-la-cueva'
  AND NOT EXISTS (
    SELECT 1 FROM concert_expense e WHERE e.concert_id = co.id AND e.label = 'Production'
  );

-- ══════════════════════════════════════════════════════════
-- 2. eh8s_catalog — demo band's catalog/royalty/geo rows
-- ══════════════════════════════════════════════════════════
-- Demo data for eh8s_catalog — the "Noche Oscura" fake band's catalog/geo
-- rows. Loaded only by scripts/load_mocks.py, never by canonical_sql/00_run_all.sql.
-- Depends on mocks/eh8s.sql having already loaded (band 'noche-oscura',
-- musician_profile for jorge@eh8s.test) and canonical_sql's own catalog rows
-- (royalty_type 'sync', channel_format 'free_lesson', geo_tier 'country',
-- geo_region 'MEX') already being present from setup_db.py.
USE eh8s_catalog;

INSERT INTO track (band_id, title, isrc, provider, royalty_pool_pda)
SELECT b.id, 'Noche en la Cueva', 'QZ-EH8-26-00001', 'distrokid', NULL
FROM eh8s.band b
WHERE b.code = 'noche-oscura'
  AND NOT EXISTS (SELECT 1 FROM track t WHERE t.title = 'Noche en la Cueva');

INSERT INTO royalty_split (track_id, royalty_type_id, musician_profile_id, party, share_bps)
SELECT t.id, rt.id, m.id, 'artist', 8000
FROM track t
JOIN royalty_type rt ON rt.code = 'sync'
JOIN eh8s.musician_profile m
JOIN eh8s.account a ON a.id = m.account_id
WHERE t.title = 'Noche en la Cueva' AND a.email = 'jorge@eh8s.test'
  AND NOT EXISTS (
    SELECT 1 FROM royalty_split s
    WHERE s.track_id = t.id AND s.royalty_type_id = rt.id AND s.party = 'artist'
  );

INSERT INTO royalty_split (track_id, royalty_type_id, musician_profile_id, party, share_bps)
SELECT t.id, rt.id, NULL, 'eh8s', 2000
FROM track t
JOIN royalty_type rt ON rt.code = 'sync'
WHERE t.title = 'Noche en la Cueva'
  AND NOT EXISTS (
    SELECT 1 FROM royalty_split s
    WHERE s.track_id = t.id AND s.royalty_type_id = rt.id AND s.party = 'eh8s'
  );

INSERT INTO channel_piece (channel_format_id, title, artist_share_bps, eh8s_share_bps)
SELECT f.id, 'Enigma Lesson 01', 6000, 4000
FROM channel_format f
WHERE f.code = 'free_lesson'
  AND NOT EXISTS (SELECT 1 FROM channel_piece p WHERE p.title = 'Enigma Lesson 01');

INSERT INTO geographic_subscription (
  band_id, geo_tier_id, geo_region_id, starts_at, expires_at, geographic_subscription_pda, intended_instruction
)
SELECT b.id, gt.id, gr.id, '2026-06-01', '2026-07-01', NULL, 'subscribe_geographic'
FROM eh8s.band b
JOIN geo_tier gt ON gt.code = 'country'
JOIN geo_region gr ON gr.code = 'MEX'
WHERE b.code = 'noche-oscura'
  AND NOT EXISTS (
    SELECT 1 FROM geographic_subscription s
    WHERE s.band_id = b.id AND s.geo_tier_id = gt.id AND s.geo_region_id = gr.id
  );

INSERT INTO tour_plan (band_id, geo_region_id, title, route_note)
SELECT b.id, gr.id, 'Mexico country loop', 'CDMX to Guadalajara to Monterrey'
FROM eh8s.band b
JOIN geo_region gr ON gr.code = 'MEX'
WHERE b.code = 'noche-oscura'
  AND NOT EXISTS (SELECT 1 FROM tour_plan p WHERE p.title = 'Mexico country loop');

-- ══════════════════════════════════════════════════════════
-- 3. eh8s_ops — one fake pending owner decision
-- ══════════════════════════════════════════════════════════
-- Demo data for eh8s_ops — one fake pending owner decision (the "Foro
-- Disponible" booking-approval scenario). Loaded only by
-- scripts/load_mocks.py, never by canonical_sql/00_run_all.sql. Depends on
-- canonical_sql's own ops_agent catalog rows already being present.
USE eh8s_ops;

INSERT INTO owner_decision (ops_agent_id, title, semaphore, payload, status, related_booking_id)
SELECT a.id, 'New venue booking wait', 'yellow',
       'STAGE asks owner to approve Foro Disponible assignment. Show estimate below 1500 USDC.',
       'pending', NULL
FROM ops_agent a
WHERE a.code = 'STAGE'
  AND NOT EXISTS (SELECT 1 FROM owner_decision d WHERE d.title = 'New venue booking wait');
