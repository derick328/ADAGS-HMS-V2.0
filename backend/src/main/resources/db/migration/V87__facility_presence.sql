-- Physical departure is separate from clinical completion. Corrections preserve originals.
CREATE TABLE outpatient_departure (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 encounter_id UUID NOT NULL REFERENCES encounter(id),
 facility_id UUID NOT NULL,
 departed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 recorded_by UUID NOT NULL,
 outcome TEXT NOT NULL CHECK (outcome IN ('CARE_COMPLETE','FOLLOW_UP_PENDING','REFERRED_OUT','LEFT_WITHOUT_BEING_SEEN','ABANDONED_UNPAID')),
 note TEXT CHECK (length(note) <= 2000)
);
CREATE TABLE outpatient_departure_correction (
 departure_id UUID PRIMARY KEY REFERENCES outpatient_departure(id),
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 recorded_by UUID NOT NULL,
 reason TEXT NOT NULL CHECK (length(trim(reason)) BETWEEN 1 AND 2000)
);
-- Resume is an explicit arrival in the existing awaiting-results workflow.
-- Preserve the earlier excursion, but it no longer confirms the current departure.
CREATE VIEW current_outpatient_departure AS
SELECT d.* FROM outpatient_departure d
WHERE NOT EXISTS (SELECT 1 FROM outpatient_departure_correction c WHERE c.departure_id=d.id)
AND NOT EXISTS (SELECT 1 FROM encounter_event ev WHERE ev.encounter_id=d.encounter_id
 AND d.outcome='FOLLOW_UP_PENDING' AND ev.from_state='AWAITING_RESULTS'
 AND ev.to_state='AWAITING_CONSULT' AND ev.occurred_at>d.departed_at);
CREATE INDEX outpatient_departure_encounter ON outpatient_departure(encounter_id, recorded_at DESC);
CREATE INDEX encounter_facility_patient_arrival ON encounter(facility_id, patient_id, created_at);
-- Serialized on the encounter, including direct writers; no mutable presence boolean.
CREATE FUNCTION guard_outpatient_departure() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM 1 FROM encounter WHERE id=NEW.encounter_id AND facility_id=NEW.facility_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Encounter facility mismatch'; END IF;
 IF EXISTS (SELECT 1 FROM current_outpatient_departure d WHERE d.encounter_id=NEW.encounter_id)
 THEN RAISE EXCEPTION 'Departure already confirmed' USING ERRCODE='23505'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER outpatient_departure_guard BEFORE INSERT ON outpatient_departure
 FOR EACH ROW EXECUTE FUNCTION guard_outpatient_departure();

-- Canonical identity includes all aliases; UNION prevents cycles from duplicating visits.
CREATE VIEW patient_canonical_identity AS
WITH RECURSIVE identities AS (
 SELECT id AS alias_id, id, merged_into_id FROM patient
 UNION
 SELECT i.alias_id, p.id, p.merged_into_id FROM identities i JOIN patient p ON p.id=i.merged_into_id
) SELECT alias_id, id AS patient_id FROM identities WHERE merged_into_id IS NULL;

CREATE VIEW patient_facility_presence AS
SELECT e.id AS encounter_id, ci.patient_id, e.facility_id, e.department_id,
 concat_ws(' ',p.given_name,p.middle_name,p.family_name) AS patient_name, p.hospital_no,
 e.state, e.type, e.created_at AS arrived_at,
 coalesce(a.actual_departure_at, dep.departed_at) AS departed_at, dep.id AS departure_id,
 CASE
 WHEN a.id IS NOT NULL AND a.actual_departure_at IS NULL THEN
   CASE WHEN e.state='DISCHARGE_ORDERED' THEN 'Awaiting actual departure'
        WHEN w.name IS NOT NULL THEN 'Ward care · ' || w.name ELSE 'Awaiting ward placement' END
 WHEN surgery.active THEN 'Surgery in progress'
 WHEN surgery.disposition_pending THEN 'Surgery completed, disposition pending'
 WHEN surgery.recovery THEN 'Recovery completion unrecorded'
 WHEN e.state='AWAITING_TRIAGE' THEN 'Awaiting triage'
 WHEN e.state='AWAITING_CONSULT' THEN 'Awaiting doctor'
 WHEN e.state='IN_CONSULT' THEN 'Doctor consultation'
 WHEN e.state='AWAITING_RESULTS' THEN 'Awaiting results or follow-up'
 WHEN e.state IN ('CONSULT_COMPLETE','PENDING_SETTLEMENT','CLOSED') THEN 'Consultation completed'
 ELSE initcap(replace(e.state,'_',' ')) END AS stage,
 CASE
 WHEN coalesce(a.actual_departure_at,dep.departed_at) IS NOT NULL AND
   ((a.id IS NOT NULL AND a.actual_departure_at IS NULL) OR surgery.active OR surgery.disposition_pending OR surgery.recovery OR e.state IN ('IN_CONSULT','AWAITING_CONSULT'))
   THEN 'Needs review'
 WHEN coalesce(a.actual_departure_at,dep.departed_at) IS NOT NULL
   THEN CASE WHEN e.state='AWAITING_RESULTS' OR dep.outcome='FOLLOW_UP_PENDING'
     OR EXISTS (SELECT 1 FROM lab_order lo WHERE lo.encounter_id=e.id AND lo.facility_id=e.facility_id
       AND NOT EXISTS (SELECT 1 FROM lab_result lr WHERE lr.lab_order_id=lo.id AND lr.released_at IS NOT NULL))
     OR EXISTS (SELECT 1 FROM prescription_order po JOIN prescription_line pl ON pl.prescription_order_id=po.id
       WHERE po.encounter_id=e.id AND po.facility_id=e.facility_id AND pl.status IN ('AWAITING_DISPENSE','PARTIALLY_DISPENSED'))
     THEN 'Away — follow-up pending' ELSE 'Left facility' END
 WHEN a.id IS NOT NULL AND a.actual_departure_at IS NULL
   THEN CASE WHEN e.state='DISCHARGE_ORDERED' THEN 'Awaiting departure' ELSE 'Receiving care — expected present' END
 WHEN surgery.active OR surgery.disposition_pending OR surgery.recovery THEN 'Receiving care — expected present'
 WHEN e.state IN ('CONSULT_COMPLETE','PENDING_SETTLEMENT','CLOSED','REFERRED_OUT','LEFT_WITHOUT_BEING_SEEN','ABANDONED_UNPAID') THEN 'Awaiting departure'
 WHEN e.state IN ('AWAITING_RESULTS','DISCHARGED','DECEASED') THEN 'Needs review'
 ELSE 'Receiving care — expected present' END AS presence,
 row_number() OVER (PARTITION BY ci.patient_id,e.facility_id ORDER BY e.created_at,e.id) AS visit_number
FROM encounter e
JOIN patient_canonical_identity ci ON ci.alias_id=e.patient_id
JOIN patient p ON p.id=ci.patient_id
LEFT JOIN admission a ON a.encounter_id=e.id AND a.facility_id=e.facility_id
LEFT JOIN LATERAL (
 SELECT ward.name FROM bed_assignment ba JOIN bed b ON b.id=ba.bed_id JOIN ward ON ward.id=b.ward_id
 WHERE ba.admission_id=a.id AND ba.released_at IS NULL AND ward.facility_id=e.facility_id
 ORDER BY ba.assigned_at DESC LIMIT 1
) w ON true
LEFT JOIN LATERAL (
 SELECT bool_or(so.status IN ('IN_PROGRESS','DRAFT_EMERGENCY')) AS active,
 bool_or(so.status='COMPLETED' AND so.disposition IS NULL) AS disposition_pending,
 bool_or(so.status='COMPLETED' AND so.disposition='RECOVERY_THEN_DISCHARGE') AS recovery
 FROM surgery_order so WHERE so.encounter_id=e.id
) surgery ON true
LEFT JOIN LATERAL (
 SELECT d.* FROM current_outpatient_departure d WHERE d.encounter_id=e.id AND d.facility_id=e.facility_id
 ORDER BY d.recorded_at DESC LIMIT 1
) dep ON true
WHERE e.state <> 'CANCELLED_IN_ERROR';

-- Preserve physical departure records and corrections permanently.
CREATE FUNCTION preserve_departure_event() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Departure events are append-only; record a correction'; END $$;
CREATE TRIGGER preserve_departure BEFORE UPDATE OR DELETE ON outpatient_departure
 FOR EACH ROW EXECUTE FUNCTION preserve_departure_event();
CREATE TRIGGER preserve_departure_correction BEFORE UPDATE OR DELETE ON outpatient_departure_correction
 FOR EACH ROW EXECUTE FUNCTION preserve_departure_event();
