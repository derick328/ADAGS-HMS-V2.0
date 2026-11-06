-- One-time operator action: clear the four Pharmaceutical stock balances
-- observed on 2026-11-06 (196 units). Preserve receipts, dispenses and items.
-- Run with psql -v actor_username=<your ADAGS login> -v ON_ERROR_STOP=1.

BEGIN;

-- Block restocks and balance changes while the exact target is checked.
LOCK TABLE item IN SHARE MODE;
LOCK TABLE stock_batch IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE stock_balance IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE stock_clear_actor ON COMMIT DROP AS
SELECT id, username FROM app_user
WHERE username = :'actor_username' AND status = 'ACTIVE';

CREATE TEMP TABLE stock_clear_target ON COMMIT DROP AS
SELECT sb.item_id, sb.batch_id, sb.store_id, sb.quantity
FROM stock_balance sb
JOIN item i ON i.id = sb.item_id
WHERE i.classification = 'Pharmaceuticals' AND sb.quantity > 0;

DO $$
BEGIN
  IF (SELECT COUNT(*) FROM stock_clear_actor) <> 1 THEN
    RAISE EXCEPTION 'An active ADAGS user matching actor_username is required';
  END IF;
  IF (SELECT COUNT(*) FROM stock_clear_target) <> 4
     OR (SELECT COALESCE(SUM(quantity), 0) FROM stock_clear_target) <> 196 THEN
    RAISE EXCEPTION 'Stock changed since review; expected 4 balances and 196 units. Nothing was cleared';
  END IF;
END $$;

INSERT INTO stock_movement
  (item_id, batch_id, store_id, qty_delta, movement_type,
   reference_type, actor_id)
SELECT t.item_id, t.batch_id, t.store_id, -t.quantity, 'WRITE_OFF',
       'OPERATOR_STOCK_CLEAR', a.id
FROM stock_clear_target t CROSS JOIN stock_clear_actor a;

UPDATE stock_balance sb
SET quantity = 0
FROM stock_clear_target t
WHERE sb.item_id = t.item_id AND sb.batch_id = t.batch_id
  AND sb.store_id = t.store_id;

UPDATE stock_batch b
SET status = 'WRITTEN_OFF'
WHERE b.id IN (SELECT batch_id FROM stock_clear_target)
  AND NOT EXISTS (
    SELECT 1 FROM stock_balance sb
    WHERE sb.batch_id = b.id AND sb.quantity > 0
  );

INSERT INTO audit_log
  (actor_user_id, action, resource_type, resource_id, justification)
SELECT DISTINCT a.id, 'STOCK_WRITE_OFF', 'STOCK_BATCH', t.batch_id,
       'Operator-requested clearance of all Pharmaceutical stock on hand'
FROM stock_clear_target t CROSS JOIN stock_clear_actor a;

INSERT INTO outbox_event (event_type, payload)
SELECT 'PharmaceuticalStockCleared',
       jsonb_build_object('actorUserId', a.id, 'balanceCount', COUNT(*),
                          'unitsCleared', SUM(t.quantity))
FROM stock_clear_target t CROSS JOIN stock_clear_actor a
GROUP BY a.id;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM stock_balance sb JOIN item i ON i.id = sb.item_id
    WHERE i.classification = 'Pharmaceuticals' AND sb.quantity > 0
  ) THEN
    RAISE EXCEPTION 'Pharmaceutical stock remains; transaction rolled back';
  END IF;
END $$;

SELECT COUNT(*) AS balances_cleared, SUM(quantity) AS units_cleared
FROM stock_clear_target;

COMMIT;
