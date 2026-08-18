-- Phase 5, pharmacy remaining pieces item 4: stock views.
-- docs/spec/12-pharmacist.md §6.1: "batches expiring within the protocol-
-- settable window" — same admin-tunable-without-a-redeploy convention as
-- the patient duplicate-detection thresholds and the misclassification
-- similarity threshold (backend/CLAUDE.md), read through
-- ProtocolSettingReader. §7.3's own "expiry - 90d -> expiry watchlist"
-- is the default.
INSERT INTO protocol_setting (setting_key, setting_value, updated_by)
SELECT 'pharmacy.stock.expiry_watchlist_window_days', '90'::jsonb, id
FROM app_user ORDER BY created_at ASC LIMIT 1;
