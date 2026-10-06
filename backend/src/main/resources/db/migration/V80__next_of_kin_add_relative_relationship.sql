-- Add RELATIVE to the next_of_kin.relationship allowed set.
--
-- V77 introduced next_of_kin_relationship_check with
-- ('SPOUSE','PARENT','CHILD','SIBLING','GUARDIAN','FRIEND','OTHER'). That
-- migration is merged and therefore immutable (backend/CLAUDE.md) — a new
-- value is a new migration, not an edit in place. RELATIVE covers the common
-- Tanzanian case where the kin is family but not one of the specific ties
-- above (cousin, aunt, uncle, in-law) — previously those had to be logged as
-- OTHER + free text, which loses them to any relationship-based query.
--
-- relationship_other stays required iff relationship = 'OTHER' (the V77
-- next_of_kin_relationship_other_shape check is untouched): RELATIVE is a
-- first-class value with no free-text companion, same as SPOUSE or FRIEND.

DO $$
DECLARE
    cname text;
BEGIN
    SELECT conname INTO cname
      FROM pg_constraint
     WHERE conrelid = 'next_of_kin'::regclass
       AND conname = 'next_of_kin_relationship_check';
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE next_of_kin DROP CONSTRAINT %I', cname);
    END IF;

    ALTER TABLE next_of_kin
        ADD CONSTRAINT next_of_kin_relationship_check CHECK (
            relationship IN ('SPOUSE', 'PARENT', 'CHILD', 'SIBLING', 'GUARDIAN', 'FRIEND', 'RELATIVE', 'OTHER')
        );
END $$;
