-- 07-doctor.md §5.1: a doctor may capture vitals during consultation, and
-- unlike triage (06-triage.md §2.2, the full mandatory set) this is an
-- opportunistic re-measure — the doctor records whatever they actually took
-- (a BP and a temperature, say), not the whole panel. Two consequences for
-- the shared vital_signs table (V10):
--
-- 1. context_ref_id points at the row that OWNS the reading — a
--    TriageAssessment, an admission, a surgery. A CONSULTATION reading has no
--    such owning sub-row (it hangs directly off the encounter), so the column
--    must be nullable. It stays required for every other context via a CHECK,
--    so triage/ward/post-op readings can never lose their owner pointer.
--
-- 2. weight_kg / height_cm were mandatory for triage's BMI. A consultation
--    re-measure may legitimately omit them. Made nullable here; bmi is a
--    GENERATED column (weight_kg / (height_cm/100)^2) and resolves to NULL
--    when either input is NULL, so nothing downstream breaks. The height > 0
--    guard keeps the generated division safe now that the NOT NULL is gone.
--
-- Triage and ward requiredness is unchanged — it is enforced in their own
-- request DTOs (@NotNull on SubmitTriageAssessment / WardVitals), not at this
-- shared column, so loosening the column does not loosen those flows.
--
-- V10 is merged and immutable — these are ALTERs in a new migration, never an
-- edit to V10 (backend/CLAUDE.md).
ALTER TABLE vital_signs ALTER COLUMN context_ref_id DROP NOT NULL;
ALTER TABLE vital_signs ALTER COLUMN weight_kg DROP NOT NULL;
ALTER TABLE vital_signs ALTER COLUMN height_cm DROP NOT NULL;

ALTER TABLE vital_signs ADD CONSTRAINT vital_signs_context_ref_present
    CHECK (context = 'CONSULTATION' OR context_ref_id IS NOT NULL);
ALTER TABLE vital_signs ADD CONSTRAINT vital_signs_height_positive
    CHECK (height_cm IS NULL OR height_cm > 0);
ALTER TABLE vital_signs ADD CONSTRAINT vital_signs_weight_nonnegative
    CHECK (weight_kg IS NULL OR weight_kg >= 0);
