-- 05a-receptionist-decisions.md D5 and V15's own seed comment: the cash
-- consultation prices are general 10,000/= and specialist 40,000/=. V15
-- stored them as amount_minor 1_000_000 and 4_000_000 — a factor-of-100
-- slip: TZS has no sub-unit here, amount_minor is whole shillings (see
-- frontend/src/shared/format/money.ts and the "12,000/=" convention;
-- nothing divides). They rendered as "1,000,000/=" / "4,000,000/=" on the
-- triage referral picker.
--
-- Corrected the way V15 §4.2 requires: close each current price_list_entry,
-- open a new one. InvoiceLine snapshots the resolved amount when a charge
-- is posted, so any consultation already billed at the old figure is
-- untouched — this only changes what future consultations resolve to. The
-- amount_minor <> target guard makes it a no-op for a row already correct.

WITH wanted (code, amount_minor) AS (
    VALUES ('CONSULT_GENERAL', 10000::bigint),
           ('CONSULT_SPECIALIST', 40000::bigint)
),
current_wrong AS (
    SELECT ple.id, w.amount_minor AS new_amount
    FROM price_list_entry ple
    JOIN service_item si ON si.id = ple.service_item_id
    JOIN price_list pl ON pl.id = ple.price_list_id AND pl.payer_category = 'CASH'
    JOIN wanted w ON w.code = si.code
    WHERE ple.valid_to IS NULL
      AND ple.amount_minor <> w.amount_minor
),
closed AS (
    UPDATE price_list_entry ple
    SET valid_to = CURRENT_DATE
    FROM current_wrong cw
    WHERE ple.id = cw.id
    RETURNING ple.price_list_id, ple.service_item_id, ple.currency_code, ple.created_by, cw.new_amount
)
INSERT INTO price_list_entry (price_list_id, service_item_id, amount_minor, currency_code, valid_from, created_by)
SELECT price_list_id, service_item_id, new_amount, currency_code, CURRENT_DATE, created_by
FROM closed;
