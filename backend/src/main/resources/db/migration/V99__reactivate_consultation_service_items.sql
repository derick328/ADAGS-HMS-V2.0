-- Catalogue-wide deactivation can disable the older consultation items.
-- These three system items are required for triage charge posting.
UPDATE service_item
SET is_active = TRUE
WHERE code IN ('CONSULT_CLINICAL_OFFICER', 'CONSULT_GENERAL', 'CONSULT_SPECIALIST')
  AND is_active = FALSE;
