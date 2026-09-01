-- Backfill inventory.item and laboratory.lab_test rows for every
-- service_item the V67 admin catalogue importer landed but which nothing
-- has classified yet. Without these, the doctor's prescription and lab
-- typeaheads render every imported row as a visible-but-unselectable
-- "Pending classification" placeholder (ItemController.search /
-- LabTab.search), so nothing can be sent for payment. V71 already
-- rescued the pre-existing item rows; this migration extends the same
-- immediately-selectable end-state to importer-created rows.
--
-- The class of imported information does not carry form/strength/unit
-- (pharmacy) or specimen_type/container_type/TAT (laboratory), so those
-- columns get generic placeholder values here. A pharmacist-in-charge
-- refines each pharmacy row via /api/items/{id}/classify; a lab lead
-- refines lab_test rows through the lab admin UI. Both surfaces exist
-- already — the missing piece was the doctor being blocked before that
-- refinement happens.
--
-- created_by needs an app_user; the earliest user is always the V3
-- bootstrap admin, and the migration ordering (V3 runs before V72)
-- guarantees at least one row exists.

WITH bootstrap_actor AS (
    SELECT id FROM app_user ORDER BY created_at LIMIT 1
)
INSERT INTO item (code, name, classification, form, unit,
                  regulatory_schedule, reorder_level, active, created_by)
SELECT si.code,
       si.name,
       COALESCE(NULLIF(si.classification, ''), 'General'),
       '—',
       'unit',
       'PRESCRIPTION_ONLY',
       0,
       TRUE,
       (SELECT id FROM bootstrap_actor)
  FROM service_item si
 WHERE si.type = 'PHARMACY'
   AND si.is_active = TRUE
   AND NOT EXISTS (SELECT 1 FROM item i WHERE i.code = si.code);

INSERT INTO lab_test (code, name, specimen_type, container_type,
                      expected_tat_hours, verification_level,
                      produces_machine_output, is_active)
SELECT si.code,
       si.name,
       '—',
       '—',
       24,
       'NONE',
       FALSE,
       TRUE
  FROM service_item si
 WHERE si.type = 'LABORATORY'
   AND si.is_active = TRUE
   AND NOT EXISTS (SELECT 1 FROM lab_test lt WHERE lt.code = si.code);
