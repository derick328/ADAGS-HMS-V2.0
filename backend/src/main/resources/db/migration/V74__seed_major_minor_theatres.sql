-- Every facility gets a Major Theatre and a Minor Theatre out of the box.
-- V50 left the theatre table empty — THEATRE_MANAGE is an admin task per
-- spec — but that leaves the doctor's Schedule Surgery flow unable to pick
-- a theatre until an admin creates one, which stops the whole booking
-- path for new deployments. Two rows per facility named exactly as
-- clinicians say them ("Major", "Minor") covers the everyday district-
-- hospital case; an admin can rename them or add more theatres later.
--
-- The active flag stays TRUE — same soft-deactivation convention every
-- other admin-defined resource (ward, item) uses. Idempotency: NOT EXISTS
-- guards keep this migration safe against environments where these two
-- names were already created by hand.

INSERT INTO theatre (facility_id, name, active)
SELECT f.id, 'Major Theatre', TRUE
FROM facility f
WHERE NOT EXISTS (
    SELECT 1 FROM theatre t
    WHERE t.facility_id = f.id AND t.name = 'Major Theatre'
);

INSERT INTO theatre (facility_id, name, active)
SELECT f.id, 'Minor Theatre', TRUE
FROM facility f
WHERE NOT EXISTS (
    SELECT 1 FROM theatre t
    WHERE t.facility_id = f.id AND t.name = 'Minor Theatre'
);
