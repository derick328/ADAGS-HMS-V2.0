CREATE TABLE stock_adjustment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id UUID NOT NULL REFERENCES stock_batch(id),
    item_id UUID NOT NULL REFERENCES item(id),
    store_id UUID NOT NULL REFERENCES store(id),
    adjustment_type TEXT NOT NULL CHECK (adjustment_type IN
      ('DAMAGED', 'EXPIRED', 'LOST', 'PHYSICAL_COUNT', 'RECEIPT_CORRECTION', 'QUARANTINE')),
    quantity_delta INTEGER NOT NULL,
    reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    requested_by UUID NOT NULL REFERENCES app_user(id),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_by UUID REFERENCES app_user(id),
    reviewed_at TIMESTAMPTZ,
    review_reason TEXT,
    idempotency_key TEXT NOT NULL UNIQUE,
    CHECK (reviewed_by IS NULL OR reviewed_by <> requested_by),
    CHECK ((status = 'PENDING') = (reviewed_by IS NULL)),
    CHECK (adjustment_type = 'QUARANTINE' OR quantity_delta <> 0),
    CHECK (adjustment_type <> 'QUARANTINE' OR quantity_delta = 0),
    CHECK (adjustment_type NOT IN ('DAMAGED', 'EXPIRED', 'LOST') OR quantity_delta < 0)
);
CREATE INDEX idx_stock_adjustment_pending ON stock_adjustment (requested_at) WHERE status = 'PENDING';
