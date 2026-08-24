-- Lab technician workspace, Screen 5: Stock Requests.
-- docs/spec/15-lab-technician.md §11: "request_notes | Optional, max 500 chars"
-- — V27's original lab_stock_request table has no notes column at all.
ALTER TABLE lab_stock_request
    ADD COLUMN request_notes TEXT CHECK (request_notes IS NULL OR char_length(request_notes) <= 500);
