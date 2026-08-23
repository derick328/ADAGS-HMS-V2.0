-- 02-billing-architecture.md §5.2/§8: two more PaymentBypass sources
-- beyond the automatic TRIAGE_IMMEDIATE/SURGERY_IMMEDIATE routes. Widens
-- V12's source CHECK (already widened once in V50 for SURGERY_IMMEDIATE) —
-- a migration is immutable once merged, so this is a new ALTER, never an
-- edit to either earlier one.
--
-- CLINICIAN_AUTHORISED is added to the enum for completeness (§5.2:
-- "URGENT nurse may declare with a reason... LESS_URGENT/below clinician
-- authorisation required") but has no granting path yet — that needs a
-- clinician to see and act on a request, which needs a Doctor workspace
-- that doesn't exist yet in this frontend. Only ADMIN_WAIVER gets a real
-- endpoint in this migration's companion code: an admin grants directly
-- (charity, staff benefit, ministry directive), no second actor needed.
ALTER TABLE payment_bypass DROP CONSTRAINT payment_bypass_source_check;
ALTER TABLE payment_bypass ADD CONSTRAINT payment_bypass_source_check
    CHECK (source IN ('TRIAGE_IMMEDIATE', 'SURGERY_IMMEDIATE', 'CLINICIAN_AUTHORISED', 'ADMIN_WAIVER'));

-- fn_grant_permissions (V5) was deliberately DROPped at the end of that
-- migration — a scoped seeding helper, not a permanent schema object —
-- same pattern V28 already established for granting a permission directly
-- post-V5.
INSERT INTO permission (code, module, description, is_clinical)
VALUES ('PAYMENT_BYPASS_WAIVE', 'billing', 'Grant an admin payment-bypass waiver', FALSE);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
 WHERE r.code = 'ADMIN' AND p.code = 'PAYMENT_BYPASS_WAIVE'
ON CONFLICT DO NOTHING;
