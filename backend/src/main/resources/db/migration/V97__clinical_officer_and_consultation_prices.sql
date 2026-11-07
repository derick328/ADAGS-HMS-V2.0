-- Clinical officers use the doctor workflow, with a separate consultation rate.
INSERT INTO role (code, name)
VALUES ('CLINICAL_OFFICER', 'Clinical Officer')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT clinical_officer.id, rp.permission_id
FROM role clinical_officer
JOIN role doctor ON doctor.code = 'DOCTOR'
JOIN role_permission rp ON rp.role_id = doctor.id
WHERE clinical_officer.code = 'CLINICAL_OFFICER'
ON CONFLICT DO NOTHING;

INSERT INTO service_item (code, name, category)
VALUES ('CONSULT_CLINICAL_OFFICER', 'Clinical officer consultation', 'CONSULTATION')
ON CONFLICT (code) DO NOTHING;

INSERT INTO provider_billing_category (role_code, service_item_code, priority, created_by)
SELECT 'CLINICAL_OFFICER', 'CONSULT_CLINICAL_OFFICER', 5, id
FROM app_user ORDER BY created_at ASC LIMIT 1
ON CONFLICT DO NOTHING;

-- These are the requested TZS consultation rates for every configured payer list.
-- Close existing current entries so posted invoice amounts remain historical snapshots.
WITH wanted (code, amount_minor) AS (
    VALUES ('CONSULT_CLINICAL_OFFICER', 4000::bigint),
           ('CONSULT_GENERAL', 8000::bigint),
           ('CONSULT_SPECIALIST', 30000::bigint)
)
UPDATE price_list_entry ple
SET valid_to = CURRENT_DATE
FROM price_list pl, service_item si, wanted w
WHERE ple.price_list_id = pl.id
  AND ple.service_item_id = si.id
  AND si.code = w.code
  AND ple.valid_to IS NULL
  AND ple.amount_minor <> w.amount_minor;

WITH wanted (code, amount_minor) AS (
    VALUES ('CONSULT_CLINICAL_OFFICER', 4000::bigint),
           ('CONSULT_GENERAL', 8000::bigint),
           ('CONSULT_SPECIALIST', 30000::bigint)
)
INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, currency_code, valid_from, created_by)
SELECT pl.id, si.id, w.amount_minor, 'TZS', CURRENT_DATE,
       (SELECT id FROM app_user ORDER BY created_at ASC LIMIT 1)
FROM price_list pl
CROSS JOIN wanted w
JOIN service_item si ON si.code = w.code
WHERE NOT EXISTS (
    SELECT 1 FROM price_list_entry current_entry
    WHERE current_entry.price_list_id = pl.id
      AND current_entry.service_item_id = si.id
      AND current_entry.valid_to IS NULL
);
