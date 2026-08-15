-- Brings the permission catalogue and role bundles in line with the
-- reference catalogue (123 codes, derived from every module spec — adapted
-- here from its BIGSERIAL draft to this repo's UUID keys). This migration
-- supersedes V2's interim Phase-0-only seed: 7 of V2's 9 codes and all 5 of
-- its role grants are carried forward unchanged in spirit, 2 codes are
-- renamed to match the catalogue, and role_permission is reset and rebuilt
-- from the catalogue's full bundles rather than V2's minimal proof-of-mechanics
-- set.

-- (1) permission gets a module column ------------------------------------

ALTER TABLE permission ADD COLUMN module TEXT;

-- Reconcile the two seeded codes that disagreed with the catalogue. UPDATE,
-- not delete+insert, so the existing role_permission rows referencing these
-- ids (RECEPTIONIST/PATIENT_REGISTER, DOCTOR & SPECIALIST_DOCTOR/
-- CLINICAL_NOTE_WRITE) carry over under the new code without re-granting —
-- moot in a moment anyway, since role_permission is rebuilt wholesale below,
-- but correct regardless of when this migration runs relative to that reset.
UPDATE permission SET code = 'PATIENT_CREATE', module = 'patients',
       description = 'Register a new patient'
 WHERE code = 'PATIENT_REGISTER';

UPDATE permission SET code = 'CLINICAL_NOTE_CREATE', module = 'clinical',
       description = 'Write consultation narrative'
 WHERE code = 'CLINICAL_NOTE_WRITE';

-- Backfill module on the other 7 already-seeded codes — same code, same
-- description, just now carrying the module the catalogue assigns it.
UPDATE permission SET module = 'identity'   WHERE code = 'USER_MANAGE';
UPDATE permission SET module = 'identity'   WHERE code = 'ROLE_ASSIGN';
UPDATE permission SET module = 'audit'      WHERE code = 'AUDIT_LOG_READ';
UPDATE permission SET module = 'admin'      WHERE code = 'FACILITY_CONFIG';
UPDATE permission SET module = 'encounters' WHERE code = 'ENCOUNTER_OPEN';
UPDATE permission SET module = 'clinical'   WHERE code = 'CLINICAL_NOTE_READ';
UPDATE permission SET module = 'inpatient'  WHERE code = 'MEDICATION_ADMINISTER';

ALTER TABLE permission ALTER COLUMN module SET NOT NULL;

ALTER TABLE permission ADD CONSTRAINT permission_module_check
    CHECK (module IN (
        'admin', 'audit', 'billing', 'clinical', 'encounters', 'finance',
        'identity', 'inpatient', 'insurance', 'inventory', 'laboratory',
        'orders', 'patients', 'pharmacy', 'reference', 'scheduling'
    ));

-- (2) the 114 catalogue codes not yet seeded ------------------------------
-- LAB_RESULT_VERIFY is here, defined, but appears in no role's grant list
-- below — the verifying role is undecided per 16-lab-verification-model.md
-- §8. Granting it later is a seed row, not a code change.

INSERT INTO permission (code, module, description, is_clinical) VALUES
-- identity --------------------------------------------------------------
    ('ELEVATION_APPROVE', 'identity', 'Approve another admin''s elevation request', FALSE),
-- audit -----------------------------------------------------------------
    ('BREAK_GLASS_ACCESS', 'audit', 'Emergency access to clinical data with justification', FALSE),
-- admin -----------------------------------------------------------------
    ('WARD_BED_MANAGE', 'admin', 'Define wards and beds', FALSE),
    ('STORE_MANAGE', 'admin', 'Define stores and sub-stores', FALSE),
    ('WORK_SHIFT_MANAGE', 'admin', 'Define shift patterns', FALSE),
    ('ROSTER_MANAGE', 'admin', 'Assign staff to shifts', FALSE),
    ('SYSTEM_SETTINGS_MANAGE', 'admin', 'Numbering, notifications, sessions, backup', FALSE),
    ('NOTIFICATION_TEMPLATE_MANAGE', 'admin', 'SMS and email templates', FALSE),
-- insurance -------------------------------------------------------------
    ('INSURANCE_SCHEME_MANAGE', 'insurance', 'Schemes and coverage rules', FALSE),
    ('CLAIM_MANAGE', 'insurance', 'Assemble, submit and reconcile claims', FALSE),
-- reference -------------------------------------------------------------
    ('REFERENCE_IMPORT', 'reference', 'Import ICD-10 and regulatory schedules', FALSE),
    ('REFERENCE_SYNONYM_PROPOSE', 'reference', 'Propose a diagnosis or catalogue synonym', FALSE),
    ('REFERENCE_SYNONYM_APPROVE', 'reference', 'Approve a proposed synonym', FALSE),
    ('REFERENCE_COMMON_MANAGE', 'reference', 'Curate department common code lists', FALSE),
    ('REFERENCE_CODING_REVIEW', 'reference', 'Work the unspecified-code review queue', FALSE),
    ('STANDING_ORDER_LIST_MANAGE', 'reference', 'Define tests triage may order pre-consultation', FALSE),
    ('PROTOCOL_MANAGE', 'reference', 'Routing rules, vital ranges, screening questions', FALSE),
    ('CLINICAL_GOVERNANCE_REPORT_READ', 'reference', 'Aggregate clinical governance reports', FALSE),
-- billing ---------------------------------------------------------------
    ('PRICE_CATALOG_READ', 'billing', 'Read the price catalogue', FALSE),
    ('SERVICE_CATALOG_MANAGE', 'billing', 'Create and edit service items', FALSE),
    ('PRICE_LIST_MANAGE', 'billing', 'Create and version price list entries', FALSE),
    ('PRICE_OVERRIDE_APPROVE', 'billing', 'Approve a price outside the price list', FALSE),
    ('INVOICE_READ', 'billing', 'Read invoices', FALSE),
    ('INVOICE_CREATE', 'billing', 'Create and issue an invoice', FALSE),
    ('INVOICE_CANCEL_REQUEST', 'billing', 'Request cancellation of an invoice', FALSE),
    ('INVOICE_CANCEL_APPROVE', 'billing', 'Approve or reject a cancellation request', FALSE),
    ('INVOICE_WRITE_OFF', 'billing', 'Write off a balance', FALSE),
    ('PAYMENT_CREATE', 'billing', 'Take a payment against invoice lines', FALSE),
    ('PAYMENT_REFUND', 'billing', 'Refund a payment', FALSE),
    ('CASHIER_SHIFT_OPEN', 'billing', 'Open a cashier shift', FALSE),
    ('CASHIER_SHIFT_CLOSE_OWN', 'billing', 'Close own cashier shift and declare cash', FALSE),
    ('CASHIER_SHIFT_RECONCILE', 'billing', 'Reconcile and sign off a cashier shift', FALSE),
    ('DEPOSIT_MANAGE', 'billing', 'Record deposit top-ups', FALSE),
    ('DEPOSIT_REFUND', 'billing', 'Refund an unused deposit', FALSE),
    ('RECEIVABLE_MANAGE', 'billing', 'Work post-discharge receivables', FALSE),
    ('FINANCIAL_REPORT_READ', 'billing', 'Revenue, expense and leakage reports', FALSE),
-- pharmacy --------------------------------------------------------------
    ('PHARMACY_ITEM_PROPOSE', 'pharmacy', 'Propose a new item or price change', FALSE),
    ('PHARMACY_ITEM_APPROVE', 'pharmacy', 'Approve pharmacy item proposals', FALSE),
    ('PHARMACY_ITEM_BULK_UPLOAD', 'pharmacy', 'Bulk import the pharmacy catalogue', FALSE),
    ('ITEM_CLASSIFY', 'pharmacy', 'Set an item''s regulatory schedule', FALSE),
    ('PRESCRIPTION_QUEUE_READ', 'pharmacy', 'View the dispensing queue', FALSE),
    ('DISPENSE_CREATE', 'pharmacy', 'Dispense medication', TRUE),
    ('DISPENSE_HISTORY_READ', 'pharmacy', 'Read own dispensing history', TRUE),
    ('PATIENT_DISPENSE_LOOKUP', 'pharmacy', 'Look up a patient''s dispensing record', TRUE),
    ('SUBSTITUTION_PROPOSE', 'pharmacy', 'Propose a substitute medicine', TRUE),
    ('PRESCRIPTION_MARK_UNAVAILABLE', 'pharmacy', 'Mark a prescribed item unavailable', FALSE),
-- laboratory --------------------------------------------------------------
    ('LAB_ITEM_PROPOSE', 'laboratory', 'Propose a new lab catalogue item', FALSE),
    ('LAB_ITEM_APPROVE', 'laboratory', 'Approve lab catalogue proposals', FALSE),
    ('LAB_QUEUE_READ', 'laboratory', 'View the lab worklist', FALSE),
    ('SPECIMEN_ACCEPT', 'laboratory', 'Accept and accession a specimen', TRUE),
    ('SPECIMEN_REJECT', 'laboratory', 'Reject a specimen with a reason', TRUE),
    ('LAB_RESULT_ENTER', 'laboratory', 'Enter results', TRUE),
    ('LAB_RESULT_VERIFY', 'laboratory', 'Verify a result', TRUE),
    ('LAB_RESULT_VERIFY_SPECIALIST', 'laboratory', 'Verify histopathology and cytology', TRUE),
    ('LAB_RESULT_AMEND', 'laboratory', 'Amend a released result', TRUE),
    ('LAB_RESULT_READ', 'laboratory', 'Read results', TRUE),
    ('LAB_REPORT_PRINT', 'laboratory', 'Print a lab report', TRUE),
    ('LAB_TEST_SENSITIVE_FLAG', 'laboratory', 'Set a test''s verification level', FALSE),
    ('CRITICAL_RESULT_ESCALATION_RECEIVE', 'laboratory', 'Receive unacknowledged critical results', TRUE),
-- patients --------------------------------------------------------------
    ('PATIENT_SEARCH', 'patients', 'Search the patient index', FALSE),
    ('PATIENT_READ_DEMOGRAPHIC', 'patients', 'Read name, age, sex, contact', FALSE),
    ('PATIENT_UPDATE_DEMOGRAPHIC', 'patients', 'Amend demographics', FALSE),
    ('PATIENT_READ_VISIT_HISTORY', 'patients', 'Visit dates, departments, providers — no clinical content', FALSE),
    ('PATIENT_READ_FINANCIAL', 'patients', 'Invoices, payments, balances', FALSE),
    ('PATIENT_MERGE', 'patients', 'Merge duplicate patient records', FALSE),
-- encounters --------------------------------------------------------------
    ('ENCOUNTER_CLOSE_ADMIN', 'encounters', 'Close an encounter administratively', FALSE),
    ('ENCOUNTER_ROUTE', 'encounters', 'Assign an encounter to a provider', FALSE),
    ('TRIAGE_QUEUE_READ', 'encounters', 'View the triage queue', FALSE),
    ('TRIAGE_ASSESS', 'encounters', 'Record a triage assessment', TRUE),
    ('TRIAGE_HISTORY_READ', 'encounters', 'Read prior triage assessments', TRUE),
    ('TRIAGE_OVERRIDE_REVIEW', 'encounters', 'Review priority downgrades', TRUE),
    ('EMERGENCY_DECLARE', 'encounters', 'Declare an encounter EMERGENCY (IMMEDIATE)', FALSE),
    ('EMERGENCY_DECLARE_AUTHORISE', 'encounters', 'Authorise a bypass below IMMEDIATE', FALSE),
    ('ISOLATION_FLAG', 'encounters', 'Flag a patient as requiring isolation', TRUE),
-- clinical --------------------------------------------------------------
    ('VITALS_CREATE', 'clinical', 'Record vital signs', TRUE),
    ('ALLERGY_READ', 'clinical', 'Read allergy records', TRUE),
    ('ALLERGY_CREATE', 'clinical', 'Record an allergy', TRUE),
    ('ALLERGY_CONFIRM', 'clinical', 'Confirm or refute a recorded allergy', TRUE),
    ('CLINICAL_NOTE_AMEND_AUTHORISE', 'clinical', 'Authorise amending another author''s finalized note', TRUE),
    ('DIAGNOSIS_READ', 'clinical', 'Read diagnoses', TRUE),
    ('DIAGNOSIS_CREATE', 'clinical', 'Record a diagnosis', TRUE),
    ('DIAGNOSIS_READ_CODED', 'clinical', 'Read ICD-10 codes only, for claims', FALSE),
    ('CONSULTATION_CREATE', 'clinical', 'Open a consultation record', TRUE),
    ('CONSULTATION_FINALIZE', 'clinical', 'Finalize a consultation record', TRUE),
-- orders ------------------------------------------------------------------
    ('LAB_ORDER_CREATE', 'orders', 'Order laboratory tests', TRUE),
    ('IMAGING_ORDER_CREATE', 'orders', 'Order imaging', TRUE),
    ('PRESCRIPTION_CREATE', 'orders', 'Prescribe medication', TRUE),
    ('PRESCRIPTION_READ', 'orders', 'Read prescriptions', TRUE),
    ('SURGERY_ORDER_CREATE', 'orders', 'Order a surgical procedure', TRUE),
    ('ORDER_CANCEL', 'orders', 'Cancel an order', TRUE),
-- inventory ---------------------------------------------------------------
    ('LAB_STOCK_REQUEST_CREATE', 'inventory', 'Request reagents from pharmacy', FALSE),
    ('LAB_STOCK_RECEIVE', 'inventory', 'Receive a released stock transfer', FALSE),
    ('STOCK_READ', 'inventory', 'View stock levels and batches', FALSE),
    ('STOCK_RESTOCK', 'inventory', 'Receive stock into a store', FALSE),
    ('STOCK_TRANSFER_RELEASE', 'inventory', 'Release stock to a sub-store', FALSE),
    ('STOCK_WRITE_OFF_APPROVE', 'inventory', 'Approve a stock write-off', FALSE),
-- inpatient ---------------------------------------------------------------
    ('WARD_PATIENT_READ', 'inpatient', 'View assigned ward patients', TRUE),
    ('ADMISSION_ORDER', 'inpatient', 'Admit a patient to a ward', TRUE),
    ('BED_ASSIGN', 'inpatient', 'Assign and release beds', FALSE),
    ('VITALS_RECORD', 'inpatient', 'Record ward observations', TRUE),
    ('MEDICATION_WITNESS', 'inpatient', 'Witness a high-risk administration', TRUE),
    ('WOUND_CARE_RECORD', 'inpatient', 'Record wound care', TRUE),
    ('WARD_STATUS_UPDATE', 'inpatient', 'Update ward patient status', TRUE),
    ('SHIFT_HANDOVER_CREATE', 'inpatient', 'Record a shift handover', TRUE),
    ('SHIFT_HANDOVER_ACKNOWLEDGE', 'inpatient', 'Acknowledge an incoming handover', TRUE),
    ('DISCHARGE_ORDER', 'inpatient', 'Order discharge — stops bed-day billing', TRUE),
    ('SURGERY_EPISODE_DOCUMENT', 'inpatient', 'Document a surgical episode', TRUE),
-- scheduling --------------------------------------------------------------
    ('APPOINTMENT_READ', 'scheduling', 'View appointments', FALSE),
    ('APPOINTMENT_CREATE_FOLLOWUP', 'scheduling', 'Book a follow-up after a consultation', FALSE),
    ('APPOINTMENT_RESCHEDULE', 'scheduling', 'Reschedule an appointment', FALSE),
    ('APPOINTMENT_CANCEL', 'scheduling', 'Cancel an appointment', FALSE),
-- finance -----------------------------------------------------------------
    ('EXPENSE_CREATE', 'finance', 'Record an expense', FALSE),
    ('EXPENSE_APPROVE', 'finance', 'Approve an expense — cannot be its creator', FALSE),
    ('PAYROLL_MANAGE', 'finance', 'Salary rates, generation and disbursement', FALSE);

-- (3) CLINICAL_LEAD — the eleventh role ------------------------------------
-- 11-clinical-lead-role.md: additive, not exclusive. The holder keeps their
-- DOCTOR/SPECIALIST_DOCTOR assignment for clinical work; this role carries
-- governance permissions only. Break-glass alerting (root CLAUDE.md
-- "Authorisation", 18-admin.md §3.2) targets this role — it did not exist
-- before this migration, so that alert had nowhere to go.

INSERT INTO role (code, name) VALUES ('CLINICAL_LEAD', 'Clinical Lead');

-- (4) role bundles ----------------------------------------------------------
-- Reset and rebuild from the catalogue rather than layer on top of it: V2's
-- five grants were an intentionally minimal proof-of-mechanics set, not a
-- subset the catalogue is guaranteed to be a superset of. One difference
-- worth flagging explicitly: TRIAGE_NURSE's V2 grant included
-- CLINICAL_NOTE_READ; the catalogue's TRIAGE_NURSE bundle does not carry it
-- (triage gets TRIAGE_HISTORY_READ instead, not the consultation narrative).
-- That grant is dropped here to match the catalogue.

DELETE FROM role_permission;

CREATE OR REPLACE FUNCTION fn_grant_permissions(p_role TEXT, p_codes TEXT[]) RETURNS void AS $$
BEGIN
    INSERT INTO role_permission (role_id, permission_id)
    SELECT r.id, p.id FROM role r, permission p
     WHERE r.code = p_role AND p.code = ANY(p_codes)
    ON CONFLICT DO NOTHING;
END;
$$ LANGUAGE plpgsql;

-- ADMIN: system, not clinical. Holds no is_clinical permission — an admin
-- who can silently read every chart is the worst insider-threat surface in
-- the hospital. Clinical access is via BREAK_GLASS_ACCESS, which alerts
-- CLINICAL_LEAD (root CLAUDE.md "Authorisation").
SELECT fn_grant_permissions('ADMIN', ARRAY[
  'USER_MANAGE','ROLE_ASSIGN','ELEVATION_APPROVE','AUDIT_LOG_READ','BREAK_GLASS_ACCESS',
  'FACILITY_CONFIG','WARD_BED_MANAGE','STORE_MANAGE','WORK_SHIFT_MANAGE','ROSTER_MANAGE',
  'SYSTEM_SETTINGS_MANAGE','NOTIFICATION_TEMPLATE_MANAGE','INSURANCE_SCHEME_MANAGE',
  'REFERENCE_IMPORT','PRICE_CATALOG_READ','SERVICE_CATALOG_MANAGE','PRICE_LIST_MANAGE',
  'PHARMACY_ITEM_APPROVE','LAB_ITEM_APPROVE','INVOICE_CANCEL_APPROVE','PATIENT_MERGE',
  'PATIENT_SEARCH','PATIENT_READ_DEMOGRAPHIC','PATIENT_READ_FINANCIAL','PATIENT_READ_VISIT_HISTORY',
  'INVOICE_READ','EXPENSE_CREATE','PAYROLL_MANAGE','FINANCIAL_REPORT_READ','STOCK_READ'
]);

SELECT fn_grant_permissions('RECEPTIONIST', ARRAY[
  'PATIENT_SEARCH','PATIENT_CREATE','PATIENT_READ_DEMOGRAPHIC','PATIENT_UPDATE_DEMOGRAPHIC',
  'PATIENT_READ_VISIT_HISTORY','PATIENT_READ_FINANCIAL',
  'ENCOUNTER_OPEN','ENCOUNTER_CLOSE_ADMIN','APPOINTMENT_READ','APPOINTMENT_RESCHEDULE',
  'APPOINTMENT_CANCEL','PRICE_CATALOG_READ','INVOICE_READ','INVOICE_CREATE',
  'INVOICE_CANCEL_REQUEST','PAYMENT_CREATE','CASHIER_SHIFT_OPEN','CASHIER_SHIFT_CLOSE_OWN',
  'DEPOSIT_MANAGE'
]);

SELECT fn_grant_permissions('TRIAGE_NURSE', ARRAY[
  'PATIENT_SEARCH','PATIENT_READ_DEMOGRAPHIC','PATIENT_READ_VISIT_HISTORY',
  'TRIAGE_QUEUE_READ','TRIAGE_ASSESS','TRIAGE_HISTORY_READ','ENCOUNTER_ROUTE',
  'EMERGENCY_DECLARE','ISOLATION_FLAG','VITALS_CREATE','ALLERGY_CREATE','ALLERGY_READ'
]);

SELECT fn_grant_permissions('DOCTOR', ARRAY[
  'PATIENT_SEARCH','PATIENT_READ_DEMOGRAPHIC','PATIENT_READ_VISIT_HISTORY',
  'TRIAGE_HISTORY_READ','VITALS_CREATE','ALLERGY_READ','ALLERGY_CREATE','ALLERGY_CONFIRM',
  'CLINICAL_NOTE_READ','CLINICAL_NOTE_CREATE','DIAGNOSIS_READ','DIAGNOSIS_CREATE',
  'CONSULTATION_CREATE','CONSULTATION_FINALIZE','LAB_ORDER_CREATE','IMAGING_ORDER_CREATE',
  'PRESCRIPTION_CREATE','PRESCRIPTION_READ','ORDER_CANCEL','LAB_RESULT_READ',
  'ADMISSION_ORDER','DISCHARGE_ORDER','APPOINTMENT_READ','APPOINTMENT_CREATE_FOLLOWUP',
  'PRICE_CATALOG_READ','STOCK_READ','ENCOUNTER_ROUTE','WARD_PATIENT_READ'
]);

SELECT fn_grant_permissions('SPECIALIST_DOCTOR', ARRAY[
  'PATIENT_SEARCH','PATIENT_READ_DEMOGRAPHIC','PATIENT_READ_VISIT_HISTORY',
  'TRIAGE_HISTORY_READ','VITALS_CREATE','ALLERGY_READ','ALLERGY_CREATE','ALLERGY_CONFIRM',
  'CLINICAL_NOTE_READ','CLINICAL_NOTE_CREATE','DIAGNOSIS_READ','DIAGNOSIS_CREATE',
  'CONSULTATION_CREATE','CONSULTATION_FINALIZE','LAB_ORDER_CREATE','IMAGING_ORDER_CREATE',
  'PRESCRIPTION_CREATE','PRESCRIPTION_READ','ORDER_CANCEL','LAB_RESULT_READ',
  'ADMISSION_ORDER','DISCHARGE_ORDER','APPOINTMENT_READ','APPOINTMENT_CREATE_FOLLOWUP',
  'PRICE_CATALOG_READ','STOCK_READ','ENCOUNTER_ROUTE','WARD_PATIENT_READ',
  'SURGERY_ORDER_CREATE','SURGERY_EPISODE_DOCUMENT'
]);

SELECT fn_grant_permissions('LAB_TECHNICIAN', ARRAY[
  'PATIENT_SEARCH','PATIENT_READ_DEMOGRAPHIC',
  'LAB_QUEUE_READ','SPECIMEN_ACCEPT','SPECIMEN_REJECT','LAB_RESULT_ENTER',
  'LAB_RESULT_AMEND','LAB_RESULT_READ','LAB_REPORT_PRINT',
  'LAB_STOCK_REQUEST_CREATE','LAB_STOCK_RECEIVE','LAB_ITEM_PROPOSE','STOCK_READ'
]);
-- LAB_RESULT_VERIFY deliberately NOT granted here or to any other role.
-- Verification is retrospective per 16-lab-verification-model.md, and the
-- verifying role is undecided per that spec's §8; granting it later is a
-- seed row, not a code change.

SELECT fn_grant_permissions('PHARMACIST', ARRAY[
  'PATIENT_SEARCH','PATIENT_READ_DEMOGRAPHIC','ALLERGY_READ','PRESCRIPTION_READ',
  'PRESCRIPTION_QUEUE_READ','DISPENSE_CREATE','DISPENSE_HISTORY_READ',
  'PATIENT_DISPENSE_LOOKUP','SUBSTITUTION_PROPOSE','PRESCRIPTION_MARK_UNAVAILABLE',
  'STOCK_READ','STOCK_RESTOCK','STOCK_TRANSFER_RELEASE',
  'PHARMACY_ITEM_PROPOSE','PHARMACY_ITEM_BULK_UPLOAD','PRICE_CATALOG_READ'
]);

SELECT fn_grant_permissions('ACCOUNTANT', ARRAY[
  'PATIENT_SEARCH','PATIENT_READ_DEMOGRAPHIC','PATIENT_READ_FINANCIAL',
  'DIAGNOSIS_READ_CODED',
  'INVOICE_READ','INVOICE_WRITE_OFF','PAYMENT_CREATE','PAYMENT_REFUND',
  'CASHIER_SHIFT_RECONCILE','DEPOSIT_MANAGE','DEPOSIT_REFUND','RECEIVABLE_MANAGE',
  'CLAIM_MANAGE','FINANCIAL_REPORT_READ','PRICE_CATALOG_READ','PRICE_OVERRIDE_APPROVE',
  'EXPENSE_CREATE','EXPENSE_APPROVE','STOCK_WRITE_OFF_APPROVE','STOCK_READ'
]);

SELECT fn_grant_permissions('WARD_NURSE', ARRAY[
  'PATIENT_SEARCH','PATIENT_READ_DEMOGRAPHIC','WARD_PATIENT_READ',
  'VITALS_RECORD','VITALS_CREATE','MEDICATION_ADMINISTER','MEDICATION_WITNESS',
  'WOUND_CARE_RECORD','WARD_STATUS_UPDATE','BED_ASSIGN',
  'SHIFT_HANDOVER_CREATE','SHIFT_HANDOVER_ACKNOWLEDGE',
  'ALLERGY_READ','PRESCRIPTION_READ','LAB_RESULT_READ','INVOICE_READ',
  'SURGERY_EPISODE_DOCUMENT','DISCHARGE_ORDER'
]);
-- DISCHARGE_ORDER is scoped to the patients this nurse's doctor assigned
-- her. The care-relationship check enforces that, not the permission alone.

SELECT fn_grant_permissions('NURSE', ARRAY[
  'PATIENT_SEARCH','PATIENT_READ_DEMOGRAPHIC','WARD_PATIENT_READ',
  'VITALS_RECORD','VITALS_CREATE','MEDICATION_ADMINISTER',
  'WOUND_CARE_RECORD','SHIFT_HANDOVER_CREATE','SHIFT_HANDOVER_ACKNOWLEDGE',
  'ALLERGY_READ','PRESCRIPTION_READ','TRIAGE_QUEUE_READ','TRIAGE_ASSESS'
]);

-- CLINICAL_LEAD is ADDITIVE. It carries governance permissions only; the
-- holder keeps their DOCTOR or SPECIALIST_DOCTOR assignment for clinical work.
SELECT fn_grant_permissions('CLINICAL_LEAD', ARRAY[
  'REFERENCE_SYNONYM_APPROVE','REFERENCE_COMMON_MANAGE','REFERENCE_CODING_REVIEW',
  'REFERENCE_SYNONYM_PROPOSE','STANDING_ORDER_LIST_MANAGE','PROTOCOL_MANAGE',
  'CLINICAL_GOVERNANCE_REPORT_READ','TRIAGE_OVERRIDE_REVIEW',
  'EMERGENCY_DECLARE_AUTHORISE','CLINICAL_NOTE_AMEND_AUTHORISE',
  'CRITICAL_RESULT_ESCALATION_RECEIVE','LAB_TEST_SENSITIVE_FLAG','ROSTER_MANAGE'
]);

DROP FUNCTION fn_grant_permissions(TEXT, TEXT[]);

-- (5) re-assert the ADMIN guard now that it has something to guard --------
-- trg_guard_admin_no_clinical_permission (V2) already rejects each grant
-- row-by-row as it's inserted; this is a second, whole-table assertion after
-- the fact — belt and braces, and it fails this migration loudly if the two
-- ever disagree.
DO $$
DECLARE n INT;
BEGIN
    SELECT count(*) INTO n
      FROM role r
      JOIN role_permission rp ON rp.role_id = r.id
      JOIN permission p ON p.id = rp.permission_id
     WHERE r.code = 'ADMIN' AND p.is_clinical;
    IF n > 0 THEN
        RAISE EXCEPTION 'ADMIN holds % clinical permission(s); see docs/spec/18-admin.md R2', n;
    END IF;
END $$;
