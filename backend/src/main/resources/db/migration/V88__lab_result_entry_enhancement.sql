ALTER TABLE lab_order ADD COLUMN special_instructions TEXT;
ALTER TABLE lab_result ADD COLUMN result_type TEXT NOT NULL DEFAULT 'NUMERIC'
    CHECK (result_type IN ('NUMERIC', 'QUALITATIVE', 'FREE_TEXT', 'NARRATIVE'));
ALTER TABLE lab_result ADD COLUMN single_value TEXT;
ALTER TABLE lab_result ADD COLUMN single_value_unit TEXT;
ALTER TABLE lab_result ADD COLUMN overall_interpretation TEXT
    CHECK (overall_interpretation IN ('NORMAL', 'ABNORMAL', 'CRITICAL'));
ALTER TABLE lab_result ADD COLUMN remarks TEXT;
ALTER TABLE lab_result ADD COLUMN lab_report_object_key TEXT;

CREATE TABLE lab_result_draft (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_order_id UUID NOT NULL REFERENCES lab_order(id),
    specimen_id UUID NOT NULL REFERENCES specimen(id),
    facility_id UUID NOT NULL REFERENCES facility(id),
    owner_id UUID NOT NULL REFERENCES app_user(id),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    version BIGINT NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SUBMITTED')),
    submitted_result_id UUID REFERENCES lab_result(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (specimen_id),
    UNIQUE (lab_order_id),
    CHECK ((status = 'DRAFT' AND submitted_result_id IS NULL) OR
           (status = 'SUBMITTED' AND submitted_result_id IS NOT NULL))
);
CREATE INDEX idx_lab_result_draft_owner ON lab_result_draft (owner_id, facility_id);
-- One initial release per specimen; amendments remain append-only rows.
CREATE UNIQUE INDEX uq_lab_result_initial_specimen ON lab_result (specimen_id) WHERE amends_id IS NULL;
CREATE UNIQUE INDEX uq_lab_result_initial_order ON lab_result (lab_order_id) WHERE amends_id IS NULL;

CREATE TABLE lab_report_upload (
    object_key TEXT PRIMARY KEY,
    lab_order_id UUID NOT NULL REFERENCES lab_order(id),
    uploaded_by UUID NOT NULL REFERENCES app_user(id),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lab_report_upload_order ON lab_report_upload (lab_order_id);

CREATE TABLE lab_machine_output_upload (
    object_key TEXT PRIMARY KEY,
    lab_order_id UUID NOT NULL REFERENCES lab_order(id),
    uploaded_by UUID NOT NULL REFERENCES app_user(id),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lab_machine_output_upload_order ON lab_machine_output_upload (lab_order_id);
