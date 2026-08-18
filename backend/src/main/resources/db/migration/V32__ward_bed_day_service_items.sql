-- Phase 6, item 2: what a bed-day charge posts against. Two service items,
-- one per ward class — 04-procedure-bedday-rules.md §3.5. Their price is
-- NOT resolved from price_list_entry the way every other category is:
-- Ward.daily_rate_minor (V31) is the authoritative rate, snapshotted onto
-- ward_charge_accrual at posting time (root CLAUDE.md "Data": a value that
-- can change underneath a record — the same pattern as the ICD-10 title
-- snapshot on Diagnosis). These rows exist only so InvoiceLine has a
-- service_item_id to categorize against; ChargePostingService.postFixedAmountCharge
-- (billing module) posts the pre-resolved amount directly, skipping
-- PriceResolutionService/price_list_entry entirely for this category.

ALTER TABLE service_item DROP CONSTRAINT service_item_category_check;
ALTER TABLE service_item ADD CONSTRAINT service_item_category_check
    CHECK (category IN ('CONSULTATION', 'LAB', 'PROCEDURE', 'PHARMACY', 'WARD'));

INSERT INTO service_item (code, name, category)
VALUES ('WARD_BED_DAY_GENERAL', 'General ward bed-day', 'WARD'),
       ('WARD_BED_DAY_PRIVATE', 'Private ward bed-day', 'WARD');
