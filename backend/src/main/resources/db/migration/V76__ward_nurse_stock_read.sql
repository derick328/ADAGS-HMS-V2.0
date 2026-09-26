-- 17-ward-nurse.md §10 (surgery episode documentation): the circulating
-- nurse records every consumable/drug used during the surgery from the
-- item catalogue — SurgeryEpisodeProcedureItems on the frontend calls
-- /api/items/search, which is gated on STOCK_READ by ItemController.
--
-- V5's WARD_NURSE role grant omitted STOCK_READ (the ward nurse was
-- never a stock-facing role in the pre-surgery-module design), which
-- means the item picker on the surgery episode page returns 401/403
-- and no procedure items can be added. Grant it here — same reasoning
-- as PHARMACIST/ACCOUNTANT already have it: this is a read-only view
-- of the catalogue, no write.

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM role r, permission p
 WHERE r.code = 'WARD_NURSE'
   AND p.code = 'STOCK_READ'
ON CONFLICT DO NOTHING;
