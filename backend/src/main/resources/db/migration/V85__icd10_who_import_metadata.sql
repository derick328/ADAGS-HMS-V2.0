-- Keep clinical selection distinct from insurer billing policy. Existing curated flags survive imports.
ALTER TABLE icd10_code ADD COLUMN entry_type TEXT;
ALTER TABLE icd10_code ADD COLUMN usage_marker TEXT;
ALTER TABLE icd10_code ADD COLUMN long_title TEXT;
ALTER TABLE icd10_code ADD COLUMN is_selectable BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE icd10_code ADD CONSTRAINT ck_icd10_entry_type CHECK (entry_type IS NULL OR entry_type IN ('chapter', 'block', 'category'));
ALTER TABLE icd10_code ADD CONSTRAINT ck_icd10_usage CHECK (usage_marker IS NULL OR usage_marker IN ('dagger', 'aster'));
CREATE INDEX idx_icd10_selectable ON icd10_code (is_active, is_selectable);
