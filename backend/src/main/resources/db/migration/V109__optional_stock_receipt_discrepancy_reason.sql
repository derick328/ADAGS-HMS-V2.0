-- Receiving discrepancies may be recorded without an explanation.
-- The original check was created without a name, so find it by its expression.
DO $$
DECLARE
    receipt_constraint RECORD;
BEGIN
    FOR receipt_constraint IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'stock_receipt_line'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%discrepancy_reason%'
    LOOP
        EXECUTE format('ALTER TABLE stock_receipt_line DROP CONSTRAINT %I', receipt_constraint.conname);
    END LOOP;
END $$;
