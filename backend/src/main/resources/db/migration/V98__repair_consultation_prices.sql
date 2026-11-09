-- Keep existing invoice snapshots; change only the current clinical officer rate.
UPDATE price_list_entry ple
SET valid_to = CURRENT_DATE
FROM service_item si
WHERE ple.service_item_id = si.id
  AND si.code = 'CONSULT_CLINICAL_OFFICER'
  AND ple.valid_to IS NULL
  AND ple.amount_minor <> 5000;

-- Restore missing consultation prices on each configured payer list. These
-- are the rates introduced in V97, with the requested clinical officer change.
-- Existing current doctor and specialist prices are left intact.
WITH wanted (code, amount_minor) AS (
    VALUES ('CONSULT_CLINICAL_OFFICER', 5000::bigint),
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
