-- Imported ITEM IDs remain untouched. Manual entries draw from a separate
-- sequence starting above the largest imported ID present at migration time.
CREATE SEQUENCE manual_catalog_item_code_seq AS integer;
SELECT setval('manual_catalog_item_code_seq',
    GREATEST(COALESCE((SELECT MAX(external_ref_id) FROM service_item), 0), 1), true);
