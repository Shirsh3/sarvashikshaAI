-- Ensure LKG exists in grade_ref for all environments.
-- We keep UKG and 1-12 as-is (seeded elsewhere), and only add missing rows safely.

INSERT INTO grade_ref (code, label, sort_order)
SELECT 'LKG', 'LKG', 0
WHERE NOT EXISTS (SELECT 1 FROM grade_ref WHERE code = 'LKG');

-- If UKG exists but has sort_order 0 (older seeds), we nudge it after LKG.
UPDATE grade_ref
SET sort_order = 1
WHERE code = 'UKG' AND sort_order = 0;

