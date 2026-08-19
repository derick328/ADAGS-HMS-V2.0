-- Phase 6, item 4: ward care execution, MAR. 17-ward-nurse.md §5.1.
--
-- prescription_line.dosage_instructions is free text today — nothing
-- structured exists to generate a schedule from. "Doctor prescribes
-- Ceftriaxone 1g IV, TDS, 5 days -> system generates 15 slots" needs route,
-- dose, frequency and duration as real columns, not parsed out of prose.
-- All nullable: an outpatient prescription (no admission) has no reason to
-- carry a MAR schedule, and every existing row predates this column.
-- dosage_instructions stays — free-text remains the human-readable
-- instruction alongside the structured fields used for MAR generation.
ALTER TABLE prescription_line
    ADD COLUMN route         TEXT,
    ADD COLUMN dose_amount   NUMERIC,
    ADD COLUMN dose_unit     TEXT,
    ADD COLUMN frequency     TEXT CHECK (frequency IN ('STAT', 'OD', 'BD', 'TDS', 'QDS', 'PRN')),
    ADD COLUMN duration_days INTEGER CHECK (duration_days IS NULL OR duration_days > 0);
