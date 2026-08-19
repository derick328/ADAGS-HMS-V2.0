-- Phase 7, item 3 (part 3): "DRAFT -> SUBMITTED -> APPROVED | REJECTED;
-- REJECTED -> RESUBMITTED -> APPROVED | REJECTED." RESUBMITTED wasn't in
-- V46's original five-value CHECK — added here rather than editing that
-- migration in place, since it's already merged (backend/CLAUDE.md: "a
-- migration is immutable once its commit is merged").
ALTER TABLE insurance_claim DROP CONSTRAINT insurance_claim_state_check;
ALTER TABLE insurance_claim ADD CONSTRAINT insurance_claim_state_check
    CHECK (state IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'RESUBMITTED', 'SETTLED'));
