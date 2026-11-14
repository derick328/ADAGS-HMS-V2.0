ALTER TABLE prescription_line DROP CONSTRAINT prescription_line_frequency_check;
ALTER TABLE prescription_line ADD CONSTRAINT prescription_line_frequency_check
    CHECK (frequency IN ('STAT', 'OD', 'BD', 'TDS', 'QDS', 'PRN', 'OTHER'));
