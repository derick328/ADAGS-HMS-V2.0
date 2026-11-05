DO $$
DECLARE p uuid := gen_random_uuid(); e uuid := gen_random_uuid(); f uuid := gen_random_uuid(); d uuid; a uuid;
BEGIN
 INSERT INTO patient(id,hospital_no,given_name,family_name) VALUES(p,'PRESENCE-TEST','Presence','Test');
 INSERT INTO encounter(id,patient_id,facility_id,state,type) VALUES(e,p,f,'AWAITING_CONSULT','OUTPATIENT');
 IF (SELECT stage FROM patient_facility_presence WHERE encounter_id=e) <> 'Awaiting doctor' THEN RAISE EXCEPTION 'awaiting consultation'; END IF;
 IF (SELECT visit_number FROM patient_facility_presence WHERE encounter_id=e) <> 1 THEN RAISE EXCEPTION 'first visit'; END IF;
 UPDATE encounter SET state='CLOSED' WHERE id=e;
 IF (SELECT departed_at FROM patient_facility_presence WHERE encounter_id=e) IS NOT NULL THEN RAISE EXCEPTION 'fabricated departure'; END IF;
 IF (SELECT presence FROM patient_facility_presence WHERE encounter_id=e) <> 'Awaiting departure' THEN RAISE EXCEPTION 'closed is not departed'; END IF;
 INSERT INTO surgery_order(encounter_id,facility_id,status) VALUES(e,f,'SCHEDULED');
 IF (SELECT stage FROM patient_facility_presence WHERE encounter_id=e) <> 'Consultation completed' THEN RAISE EXCEPTION 'scheduled surgery implies presence'; END IF;
 UPDATE surgery_order SET status='IN_PROGRESS' WHERE encounter_id=e;
 IF (SELECT stage FROM patient_facility_presence WHERE encounter_id=e) <> 'Surgery in progress' THEN RAISE EXCEPTION 'surgery priority'; END IF;
 UPDATE surgery_order SET status='COMPLETED' WHERE encounter_id=e;
 IF (SELECT stage FROM patient_facility_presence WHERE encounter_id=e) <> 'Surgery completed, disposition pending' THEN RAISE EXCEPTION 'disposition pending'; END IF;
 UPDATE surgery_order SET disposition='RECOVERY_THEN_DISCHARGE' WHERE encounter_id=e;
 IF (SELECT stage FROM patient_facility_presence WHERE encounter_id=e) <> 'Recovery completion unrecorded' THEN RAISE EXCEPTION 'recovery'; END IF;
 UPDATE surgery_order SET status='CANCELLED' WHERE encounter_id=e;
 INSERT INTO outpatient_departure(encounter_id,facility_id,recorded_by,outcome) VALUES(e,f,p,'CARE_COMPLETE') RETURNING id INTO d;
 IF (SELECT presence FROM patient_facility_presence WHERE encounter_id=e) <> 'Left facility' THEN RAISE EXCEPTION 'confirmed departure'; END IF;
 BEGIN
  INSERT INTO outpatient_departure(encounter_id,facility_id,recorded_by,outcome) VALUES(e,f,p,'CARE_COMPLETE');
  RAISE EXCEPTION 'duplicate accepted';
 EXCEPTION WHEN unique_violation THEN NULL; END;
 BEGIN UPDATE outpatient_departure SET note='rewrite' WHERE id=d; RAISE EXCEPTION 'rewrite accepted';
 EXCEPTION WHEN raise_exception THEN IF SQLERRM='rewrite accepted' THEN RAISE; END IF; END;
 INSERT INTO outpatient_departure_correction(departure_id,recorded_by,reason) VALUES(d,p,'Mistaken confirmation');
 IF (SELECT departed_at FROM patient_facility_presence WHERE encounter_id=e) IS NOT NULL THEN RAISE EXCEPTION 'correction ineffective'; END IF;
 UPDATE encounter SET state='AWAITING_RESULTS' WHERE id=e;
 INSERT INTO outpatient_departure(encounter_id,facility_id,recorded_by,outcome) VALUES(e,f,p,'FOLLOW_UP_PENDING');
 IF (SELECT presence FROM patient_facility_presence WHERE encounter_id=e) <> 'Away — follow-up pending' THEN RAISE EXCEPTION 'follow up'; END IF;
 a := gen_random_uuid();
 INSERT INTO admission(id,encounter_id,facility_id) VALUES(a,e,f);
 IF (SELECT presence FROM patient_facility_presence WHERE encounter_id=e) <> 'Needs review' THEN RAISE EXCEPTION 'contradiction'; END IF;
 UPDATE admission SET actual_departure_at=now() WHERE id=a;
 IF (SELECT departed_at FROM patient_facility_presence WHERE encounter_id=e) IS NULL THEN RAISE EXCEPTION 'inpatient departure'; END IF;
 INSERT INTO encounter(patient_id,facility_id,state,type,created_at) VALUES(p,f,'CANCELLED_IN_ERROR','OUTPATIENT',now()+interval '1 day');
 IF (SELECT count(*) FROM patient_facility_presence WHERE patient_id=p) <> 1 THEN RAISE EXCEPTION 'cancelled counted'; END IF;
 INSERT INTO encounter(patient_id,facility_id,state,type,created_at) VALUES(p,f,'LEFT_WITHOUT_BEING_SEEN','OUTPATIENT',now()+interval '2 days');
 IF (SELECT max(visit_number) FROM patient_facility_presence WHERE patient_id=p) <> 2 THEN RAISE EXCEPTION 'revisit'; END IF;
 RAISE NOTICE 'PASS: 16 presence, visit count, departure and correction assertions';
END $$;

