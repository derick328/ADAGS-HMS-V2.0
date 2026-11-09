-- Keep department IDs stable: encounter, ward, scope and follow-up records already reference them.
CREATE TABLE department_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
ALTER TABLE department ADD COLUMN category_id UUID REFERENCES department_category(id);
ALTER TABLE department ADD COLUMN description TEXT;
ALTER TABLE department ADD COLUMN unit_type TEXT NOT NULL DEFAULT 'CONSULTATION'
    CHECK (unit_type IN ('CONSULTATION','WARD','DIAGNOSTIC','PHARMACY','THEATRE','SUPPORT'));
ALTER TABLE department ADD COLUMN consultation_enabled BOOLEAN NOT NULL DEFAULT TRUE;
CREATE TABLE department_staff (
    department_id UUID NOT NULL REFERENCES department(id),
    user_id UUID NOT NULL REFERENCES app_user(id),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by UUID REFERENCES app_user(id),
    PRIMARY KEY (department_id, user_id)
);
CREATE UNIQUE INDEX department_staff_one_primary ON department_staff(user_id) WHERE is_primary;
CREATE TABLE department_hod (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id UUID NOT NULL,
    user_id UUID NOT NULL,
    appointed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    appointed_by UUID NOT NULL REFERENCES app_user(id),
    ended_at TIMESTAMPTZ,
    ended_by UUID REFERENCES app_user(id),
    FOREIGN KEY (department_id) REFERENCES department(id),
    FOREIGN KEY (user_id) REFERENCES app_user(id),
    CHECK ((ended_at IS NULL) = (ended_by IS NULL))
);
CREATE UNIQUE INDEX department_one_active_hod ON department_hod(department_id) WHERE ended_at IS NULL;
INSERT INTO department_category(code,name) VALUES
('GENERAL_CLINICAL','General Clinical Departments'),('SPECIALIST','Specialist Clinics'),
('MATERNAL_CHILD','Maternal & Child Health'),('DIAGNOSTIC_TREATMENT','Diagnostic & Treatment Departments')
ON CONFLICT (code) DO NOTHING;
-- Legacy General department remains valid for historical encounters and roles.
UPDATE department SET category_id=(SELECT id FROM department_category WHERE code='GENERAL_CLINICAL') WHERE category_id IS NULL;
ALTER TABLE department ALTER COLUMN category_id SET NOT NULL;
WITH catalogue(category_code, code, name, unit_type, consultation_enabled) AS (VALUES
('GENERAL_CLINICAL','OPD','Outpatient Department (OPD)','CONSULTATION',true),
('GENERAL_CLINICAL','EMERGENCY','Emergency Department','CONSULTATION',true),
('GENERAL_CLINICAL','INTERNAL_MEDICINE','Internal Medicine','CONSULTATION',true),
('GENERAL_CLINICAL','PAEDIATRICS','Paediatrics','CONSULTATION',true),
('GENERAL_CLINICAL','GENERAL_SURGERY','General Surgery','CONSULTATION',true),
('GENERAL_CLINICAL','ORTHOPAEDICS','Orthopaedics','CONSULTATION',true),
('GENERAL_CLINICAL','DENTAL','Dental Department','CONSULTATION',true),
('GENERAL_CLINICAL','IPD','Inpatient Department (IPD)','WARD',false),
('SPECIALIST','CARDIOLOGY','Cardiology Clinic','CONSULTATION',true),
('SPECIALIST','DIABETES','Diabetes Clinic','CONSULTATION',true),
('SPECIALIST','HYPERTENSION','Hypertension Clinic','CONSULTATION',true),
('SPECIALIST','DERMATOLOGY','Dermatology Clinic','CONSULTATION',true),
('SPECIALIST','OPHTHALMOLOGY','Ophthalmology / Eye Clinic','CONSULTATION',true),
('SPECIALIST','ENT','ENT Clinic','CONSULTATION',true),
('SPECIALIST','NEUROLOGY','Neurology Clinic','CONSULTATION',true),
('SPECIALIST','PSYCHIATRY','Psychiatry / Mental Health Clinic','CONSULTATION',true),
('SPECIALIST','UROLOGY','Urology Clinic','CONSULTATION',true),
('SPECIALIST','GASTROENTEROLOGY','Gastroenterology Clinic','CONSULTATION',true),
('SPECIALIST','NEPHROLOGY','Nephrology Clinic','CONSULTATION',true),
('SPECIALIST','ONCOLOGY','Oncology Clinic','CONSULTATION',true),
('SPECIALIST','PHYSIOTHERAPY','Physiotherapy Clinic','CONSULTATION',true),
('SPECIALIST','NUTRITION','Nutrition Clinic','CONSULTATION',true),
('SPECIALIST','HIV_CTC','HIV Care and Treatment Clinic (CTC)','CONSULTATION',true),
('SPECIALIST','TB','Tuberculosis (TB) Clinic','CONSULTATION',true),
('MATERNAL_CHILD','RCH','Reproductive & Child Health (RCH)','CONSULTATION',true),
('MATERNAL_CHILD','ANC','Antenatal Clinic (ANC)','CONSULTATION',true),
('MATERNAL_CHILD','PNC','Postnatal Clinic (PNC)','CONSULTATION',true),
('MATERNAL_CHILD','FAMILY_PLANNING','Family Planning Clinic','CONSULTATION',true),
('MATERNAL_CHILD','OBGYN','Obstetrics & Gynaecology','CONSULTATION',true),
('MATERNAL_CHILD','MATERNITY','Maternity / Labour Ward','WARD',false),
('DIAGNOSTIC_TREATMENT','LABORATORY','Laboratory','DIAGNOSTIC',false),
('DIAGNOSTIC_TREATMENT','RADIOLOGY','Radiology / Imaging','DIAGNOSTIC',false),
('DIAGNOSTIC_TREATMENT','ULTRASOUND','Ultrasound','DIAGNOSTIC',false),
('DIAGNOSTIC_TREATMENT','PHARMACY','Pharmacy','PHARMACY',false),
('DIAGNOSTIC_TREATMENT','OPERATING_THEATRE','Operating Theatre','THEATRE',false),
('DIAGNOSTIC_TREATMENT','NURSING','Nursing Department','SUPPORT',false),
('DIAGNOSTIC_TREATMENT','ICU','ICU / Critical Care','WARD',false),
('DIAGNOSTIC_TREATMENT','DIALYSIS','Dialysis Unit','DIAGNOSTIC',false),
('DIAGNOSTIC_TREATMENT','MINOR_PROCEDURES','Minor Procedures Unit','SUPPORT',false)
)
INSERT INTO department(facility_id,category_id,code,name,unit_type,consultation_enabled)
SELECT f.id, c.id, x.code,x.name,x.unit_type,x.consultation_enabled FROM facility f
CROSS JOIN catalogue x JOIN department_category c ON c.code=x.category_code
WHERE NOT EXISTS (SELECT 1 FROM department existing WHERE existing.facility_id=f.id AND lower(existing.name)=lower(x.name))
ON CONFLICT (facility_id,code) DO NOTHING;

-- Carry existing department-scoped role holders into the organizational roster.
INSERT INTO department_staff(department_id,user_id,is_primary,assigned_by)
SELECT DISTINCT d.id,ra.user_id,false,ra.granted_by FROM role_assignment ra
JOIN department d ON d.id=ra.scope_id JOIN app_user u ON u.id=ra.user_id
WHERE ra.scope_type='DEPARTMENT' AND u.status='ACTIVE' AND ra.revoked_at IS NULL
ON CONFLICT (department_id,user_id) DO NOTHING;
-- Existing doctors using the bootstrap General department retain a working assignment.
INSERT INTO department_staff(department_id,user_id,is_primary)
SELECT d.id,ra.user_id,false FROM department d CROSS JOIN role_assignment ra
JOIN role r ON r.id=ra.role_id JOIN app_user u ON u.id=ra.user_id
WHERE d.code='GENERAL' AND r.code IN ('DOCTOR','SPECIALIST_DOCTOR','CLINICAL_OFFICER')
AND u.status='ACTIVE' AND ra.revoked_at IS NULL
AND (ra.scope_type='GLOBAL' OR (ra.scope_type='FACILITY' AND ra.scope_id=d.facility_id))
ON CONFLICT (department_id,user_id) DO NOTHING;

-- Defend the HOD membership invariant even when staff changes race an appointment.
CREATE FUNCTION check_active_hod_membership() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM department_staff s JOIN app_user u ON u.id=s.user_id
        JOIN department d ON d.id=s.department_id
        WHERE s.department_id=NEW.department_id AND s.user_id=NEW.user_id
          AND u.status='ACTIVE' AND d.active
        FOR SHARE OF s,u
    ) THEN
        RAISE EXCEPTION 'HOD must be an active assigned staff member';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER department_hod_membership BEFORE INSERT ON department_hod
FOR EACH ROW EXECUTE FUNCTION check_active_hod_membership();

CREATE FUNCTION protect_active_hod_staff() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM department_hod h WHERE h.department_id=OLD.department_id
               AND h.user_id=OLD.user_id AND h.ended_at IS NULL) THEN
        RAISE EXCEPTION 'Replace or vacate the active HOD before removing staff membership';
    END IF;
    RETURN OLD;
END $$;
CREATE TRIGGER department_staff_hod_guard BEFORE DELETE ON department_staff
FOR EACH ROW EXECUTE FUNCTION protect_active_hod_staff();

CREATE FUNCTION protect_active_hod_user() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status='ACTIVE' AND NEW.status<>'ACTIVE' AND EXISTS (
        SELECT 1 FROM department_hod h WHERE h.user_id=OLD.id AND h.ended_at IS NULL
    ) THEN
        RAISE EXCEPTION 'Replace or vacate active HOD appointments before deactivating this user';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER app_user_hod_guard BEFORE UPDATE OF status ON app_user
FOR EACH ROW EXECUTE FUNCTION protect_active_hod_user();

CREATE UNIQUE INDEX department_category_name_ci ON department_category(lower(name));
CREATE UNIQUE INDEX department_category_code_ci ON department_category(lower(code));
-- Existing installations may contain same-named legacy departments; retain their IDs
-- and references while rejecting any new duplicate within a category.
CREATE FUNCTION ensure_unique_department_name() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='UPDATE' AND NEW.name IS NOT DISTINCT FROM OLD.name
       AND NEW.category_id IS NOT DISTINCT FROM OLD.category_id
       AND NEW.facility_id IS NOT DISTINCT FROM OLD.facility_id THEN
        RETURN NEW;
    END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended(
        NEW.facility_id::text || NEW.category_id::text || lower(NEW.name), 0));
    IF EXISTS (SELECT 1 FROM department d WHERE d.facility_id=NEW.facility_id
               AND d.category_id=NEW.category_id AND lower(d.name)=lower(NEW.name)
               AND d.id<>NEW.id) THEN
        RAISE EXCEPTION 'Clinic name already exists in this category';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER department_name_guard BEFORE INSERT OR UPDATE OF name,category_id,facility_id ON department
FOR EACH ROW EXECUTE FUNCTION ensure_unique_department_name();

CREATE TABLE department_change_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id UUID NOT NULL REFERENCES department(id),
    actor_user_id UUID NOT NULL REFERENCES app_user(id),
    action TEXT NOT NULL CHECK (action IN ('CREATE','UPDATE')),
    previous_value JSONB,
    new_value JSONB NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX department_change_audit_department ON department_change_audit(department_id,changed_at);
