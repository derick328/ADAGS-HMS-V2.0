-- Lab technician workspace, Screen 5: resolving "this technician's lab
-- sub-store" needs at most one store per (facility, type) to be
-- deterministic. root CLAUDE.md "Concurrency": a database constraint, not
-- an application-level check — two lab technicians racing to create the
-- first stock request at a facility with no LAB store yet must not each
-- create their own.
CREATE UNIQUE INDEX idx_store_facility_type ON store (facility_id, type);
