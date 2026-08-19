-- Phase 6, item 4: ward care execution, MAR two-person check.
-- 17-ward-nurse.md §5.3: "Standard practice for a defined class — IV
-- drugs, insulin, anticoagulants, paediatric doses, and anything the
-- clinical lead adds." A plain classification flag on the catalogue item,
-- same shape as regulatory_schedule — read at the application layer
-- (MarkAdministrationService), not enforced by a DB trigger, matching how
-- Item.assertDispensable() is read elsewhere in this codebase.
ALTER TABLE item
    ADD COLUMN requires_administration_witness BOOLEAN NOT NULL DEFAULT FALSE;
