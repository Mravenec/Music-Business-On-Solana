-- Remove bookings that have no concert row (left by the pre-epic-79 two-step book-show path).
-- Safe to re-run: only deletes orphans.

USE eh8s;

-- Specific smoke leftover: Foro Disponible (venue 2), Dry B Trio (band 5), 2026-11-15
DELETE b
FROM booking b
LEFT JOIN concert c ON c.booking_id = b.id
WHERE b.venue_id = 2
  AND b.band_id = 5
  AND b.show_date = '2026-11-15'
  AND c.id IS NULL;

-- Any other booking without a concert (same class of debt)
DELETE b
FROM booking b
LEFT JOIN concert c ON c.booking_id = b.id
WHERE c.id IS NULL;
