CREATE TABLE clinic_follow_up (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient(id),
    facility_id UUID NOT NULL REFERENCES facility(id),
    department_id UUID NOT NULL REFERENCES department(id),
    clinic_name TEXT NOT NULL,
    provider_id UUID NOT NULL REFERENCES app_user(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_active_clinic_follow_up ON clinic_follow_up(patient_id, department_id, lower(clinic_name)) WHERE active;
CREATE INDEX idx_clinic_follow_up_facility ON clinic_follow_up(facility_id, active);
ALTER TABLE appointment ADD COLUMN clinic_follow_up_id UUID REFERENCES clinic_follow_up(id);
ALTER TABLE appointment ADD COLUMN arrived_at TIMESTAMPTZ;
ALTER TABLE appointment ADD COLUMN no_show_at TIMESTAMPTZ;
CREATE UNIQUE INDEX uq_clinic_scheduled_appointment ON appointment(clinic_follow_up_id) WHERE status = 'SCHEDULED' AND clinic_follow_up_id IS NOT NULL;
CREATE UNIQUE INDEX uq_appointment_attended_encounter ON appointment(attended_encounter_id) WHERE attended_encounter_id IS NOT NULL;

CREATE TABLE appointment_schedule_change (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id UUID NOT NULL REFERENCES appointment(id),
    previous_at TIMESTAMPTZ NOT NULL,
    new_at TIMESTAMPTZ NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_appointment_schedule_change_appointment ON appointment_schedule_change(appointment_id, changed_at);
CREATE FUNCTION record_appointment_reschedule() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.scheduled_at IS DISTINCT FROM NEW.scheduled_at THEN
        INSERT INTO appointment_schedule_change(appointment_id, previous_at, new_at)
        VALUES (OLD.id, OLD.scheduled_at, NEW.scheduled_at);
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_appointment_reschedule AFTER UPDATE OF scheduled_at ON appointment
FOR EACH ROW EXECUTE FUNCTION record_appointment_reschedule();
