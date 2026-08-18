-- 12-pharmacist.md §2/§7: the pharmacist reviews (approves/releases or
-- rejects) a lab stock request — LAB_STOCK_REQUEST_CREATE and
-- LAB_STOCK_RECEIVE already exist in V5, but no code for the pharmacist's
-- own review step was ever seeded.
--
-- fn_grant_permissions (V5) was deliberately DROPed at the end of that
-- migration — a scoped seeding helper, not a permanent schema object — so
-- this grants the permission directly rather than recreating it.
INSERT INTO permission (code, module, description, is_clinical)
VALUES ('LAB_STOCK_REQUEST_REVIEW', 'inventory', 'Release or reject a lab stock request', FALSE);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
 WHERE r.code = 'PHARMACIST' AND p.code = 'LAB_STOCK_REQUEST_REVIEW'
ON CONFLICT DO NOTHING;
