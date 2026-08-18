-- Phase 6, item 3: discharge. 04-procedure-bedday-rules.md §3.6-§3.9.
--
-- discharge_ordered_at/by already exist (V31) but were never written to
-- before this item. discharge_orderer_role is new: "record ... so the
-- monthly report can distinguish doctor-ordered from nurse-ordered" — a
-- reporting field, not an authorization check (root CLAUDE.md
-- "Authorisation": permissions, never role names — OrderDischargeService
-- derives this from which check the actor actually satisfied).
ALTER TABLE admission
    ADD COLUMN discharge_orderer_role TEXT CHECK (discharge_orderer_role IN ('DOCTOR', 'NURSE'));
