-- Bootstrap pharmacy formulary classification per 14-controlled-medicines-
-- tanzania.md. Every existing item.regulatory_schedule = 'UNCLASSIFIED'
-- currently blocks prescribing (13-controlled-medicines-policy.md §3.1 +
-- ItemSearchResultResponse.selectable), so nothing shows as selectable
-- in the doctor's typeahead. This migration:
--
--   1. Sets regulatory_schedule = 'CONTROLLED' on items whose name
--      matches the Tanzania First Schedule narcotic list (§3.1) or the
--      limited-therapeutic-value psychotropic list (§3.3). Matching is
--      by INN keyword (case-insensitive), not exact name — most
--      catalogues carry brand/formulation suffixes.
--   2. Sets regulatory_schedule = 'PRESCRIPTION_ONLY' on every other
--      UNCLASSIFIED item — the safe default per Reg 16(3): psychotropics
--      of moderate/high therapeutic value belong here, as do most
--      hospital-formulary medicines. A pharmacist-in-charge can re-
--      classify individual rows later via /api/items/{id}/classify.
--   3. Activates every inactive item so it appears as a selectable
--      option in prescription search.
--
-- Names to expand later: this is a starter list, deliberately narrow to
-- avoid false positives. Ketamine is included per 14-*.md §3.1's
-- explicit call-out ("Ketamine and its salts are on the narcotic list").
-- Precursors (§3.5) are excluded here because most have legitimate
-- laboratory-chemical uses in a hospital; classification for those
-- belongs to a follow-up review with the pharmacist-in-charge.
--
-- The exemption thresholds in §3.2 (codeine < 2.5%, morphine < 0.2%,
-- diphenoxylate < 2.5mg with atropine, etc.) are NOT applied here — a
-- name-only classifier cannot see strengths. A pharmacist can
-- individually declassify a low-strength preparation via /classify.

WITH controlled_matches (pattern) AS (
    VALUES
        -- §3.1 narcotics
        ('%morphine%'), ('%pethidine%'), ('%fentanyl%'), ('%alfentanil%'),
        ('%remifentanil%'), ('%sufentanil%'), ('%methadone%'), ('%oxycodone%'),
        ('%hydrocodone%'), ('%hydromorphone%'), ('%codeine%'), ('%dihydrocodeine%'),
        ('%ethylmorphine%'), ('%pholcodine%'), ('%opium%'), ('%cocaine%'),
        ('%cannabis%'), ('%nalbuphine%'), ('%tilidine%'), ('%diphenoxylate%'),
        ('%difenoxin%'), ('%ketamine%'),
        -- §3.3 limited-value psychotropics
        ('%lsd%'), ('%mdma%'), ('%methamphetamine%'), ('%cathinone%'),
        ('%mescaline%'), ('%psilocybin%'), ('%phencyclidine%'),
        ('%tapentadol%'), ('%oliceridine%'), ('%lisdexamfetamine%'),
        ('%etizolam%'), ('%flualprazolam%'), ('%clonazolam%'), ('%diclazepam%')
),
controlled_ids AS (
    SELECT DISTINCT i.id
      FROM item i
      JOIN controlled_matches m ON lower(i.name) LIKE m.pattern
)
UPDATE item i
   SET regulatory_schedule = 'CONTROLLED'
  FROM controlled_ids c
 WHERE i.id = c.id
   AND i.regulatory_schedule <> 'CONTROLLED';

UPDATE item
   SET regulatory_schedule = 'PRESCRIPTION_ONLY'
 WHERE regulatory_schedule = 'UNCLASSIFIED';

UPDATE item
   SET active = TRUE
 WHERE active = FALSE
   AND regulatory_schedule <> 'CONTROLLED';
