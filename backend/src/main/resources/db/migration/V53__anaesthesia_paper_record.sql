-- Phase 8, surgery module, item 7: anaesthesia paper record.
-- 04-*.md §4, root CLAUDE.md task text: "Labelled PAPER RECORD everywhere
-- it appears. Creates no order, no charge, no stock movement." — the
-- application layer honours that by never calling BillingFacade or
-- InventoryFacade from RecordAnaesthesiaService.
--
-- no_anaesthesia is the alternative path task text names explicitly: "A
-- procedure requiring anaesthesia cannot reach COMPLETED without either
-- this pointer or an explicit no_anaesthesia flag" — both live on the
-- same row (a single insert-only table) rather than two, since they are
-- the two mutually exclusive ways this row can satisfy that gate.
-- Insert-only, no uniqueness: a mistaken record can be corrected with a
-- further row — CompleteSurgeryOrderService's gate only needs one to
-- exist, whichever was recorded last is irrelevant to it.
CREATE TABLE anaesthesia_paper_record (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    surgery_order_id         UUID NOT NULL REFERENCES surgery_order(id),
    no_anaesthesia           BOOLEAN NOT NULL DEFAULT FALSE,
    anaesthetist_name_text   TEXT,
    anaesthesia_type_text    TEXT,
    paper_record_reference   TEXT,
    recorded_by              UUID NOT NULL REFERENCES app_user(id),
    recorded_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (no_anaesthesia = TRUE OR (
        anaesthetist_name_text IS NOT NULL AND anaesthesia_type_text IS NOT NULL
        AND paper_record_reference IS NOT NULL
    ))
);

CREATE INDEX idx_anaesthesia_paper_record_order ON anaesthesia_paper_record (surgery_order_id);
