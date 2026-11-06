-- The assessed type may differ from a catalogue placeholder or expected type.
-- Keep the technician's reason on the immutable specimen and publish it in SpecimenAccepted.
ALTER TABLE specimen ADD COLUMN sample_type_override_reason TEXT;
