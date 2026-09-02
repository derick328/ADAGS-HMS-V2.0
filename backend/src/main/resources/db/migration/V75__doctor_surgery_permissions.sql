-- V5 split surgery scheduling behind the SPECIALIST_DOCTOR role — the
-- brief's original model was "GPs don't book theatres". In practice, in
-- a single-hospital deployment the two roles collapse onto the same
-- clinician account, and hiding "Schedule surgery" from the disposition
-- picker leaves the doctor no path forward when a patient in their
-- consultation actually needs surgery.
--
-- This grants the same two surgery permissions the SPECIALIST_DOCTOR
-- role already has (V5 §SPECIALIST_DOCTOR — SURGERY_ORDER_CREATE and
-- SURGERY_EPISODE_DOCUMENT) to plain DOCTOR as well. Existing
-- SPECIALIST_DOCTOR assignments are unaffected; a hospital that wants
-- the tighter separation can revoke these role_permission rows without
-- another migration.

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM role r, permission p
 WHERE r.code = 'DOCTOR'
   AND p.code IN ('SURGERY_ORDER_CREATE', 'SURGERY_EPISODE_DOCUMENT')
ON CONFLICT DO NOTHING;
