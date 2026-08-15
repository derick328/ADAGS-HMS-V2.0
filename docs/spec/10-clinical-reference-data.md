# HMS — Clinical Reference Data & Shared Picker

**Version:** 0.1
**Module:** `clinical-reference` (new)
**Consumed by:** doctor module (diagnosis, lab orders, prescriptions, procedures), laboratory, pharmacy, insurance claims, reporting

---

## 1. Purpose

Three problems in one module:

1. Diagnoses must be ICD-10 coded, and a raw 14,000-code list is unusable at a busy desk.
2. Clinicians name conditions in Swahili and in local shorthand, not in ICD vocabulary.
3. Lab tests, drugs, and procedures need the same searchable-catalogue behaviour as diagnoses.

The answer to all three is a curated, clinically-editable reference layer plus one shared picker component.

---

## 2. Schema

### 2.1 ICD-10

```
Icd10Code(
  code            PK,          -- 'B50.9'
  title,                       -- 'Plasmodium falciparum malaria, unspecified'
  chapter, block, parent_code,
  icd10_release,               -- e.g. '2019' — which WHO release this came from
  is_billable,                 -- some codes are category headers, not usable
  is_active,                   -- never delete; retire instead
  created_at
)
```

⚠ **Codes are never deleted.** Historical diagnoses reference retired codes, and a record from 2026 must remain readable. Retirement sets `is_active = false`, which removes it from search while leaving it resolvable.

⚠ **`is_billable` matters.** ICD-10 contains category headers (`B50` — "Plasmodium falciparum malaria") that are not valid diagnoses on their own. Offering them in the picker produces claims that insurers reject. Filter them out of search results.

### 2.2 Common lists — department-scoped

```
CommonCode(
  id,
  code            → Icd10Code,
  department_id   → Department,   -- NULL = applies hospital-wide
  sort_rank,
  added_by, added_at,
  is_active
)
```

⚠ **Department scoping is the point.** Paediatrics and cardiology have nearly disjoint common sets; a single global list means every doctor scrolls past another department's diseases. Resolution order at search time:

```
1. This department's common codes
2. Hospital-wide common codes
3. Full active, billable ICD-10
```

### 2.3 Synonyms

```
Icd10Synonym(
  id,
  code            → Icd10Code,
  term,                          -- 'homa ya malaria', 'presha'
  language,                      -- 'sw' | 'en' | 'local'
  status,                        -- PROPOSED | APPROVED | REJECTED
  proposed_by, proposed_at,
  reviewed_by, reviewed_at, review_note,
  is_active
)
```

Only `APPROVED` synonyms participate in search.

### 2.4 The same pattern on other catalogues

⚠ `is_common`, department scoping, and synonyms apply equally to lab tests, drugs, and procedures. Malaria RDT should be the first result when a doctor types `mal` in a lab field, and a locally-used drug name should resolve to the formulary item.

```
ServiceItemSynonym(service_item_id, term, language, status, ...)
ItemSynonym(item_id, term, language, status, ...)
CommonServiceItem(service_item_id, department_id, sort_rank, ...)
CommonItem(item_id, department_id, sort_rank, ...)
```

Same tables, same moderation flow, same picker. This is what makes the component genuinely one thing with four configurations rather than four near-duplicates.

---

## 3. Synonym moderation — a safety control, not bureaucracy

⚠ **An incorrect synonym silently produces wrong diagnoses.** If a clinician maps a Swahili term to the wrong code, every doctor who later types that term selects the wrong diagnosis, and nothing in the interface reveals the error. It flows into claims, morbidity reporting, and the patient's permanent record.

So the flow mirrors the invoice cancellation pattern — propose freely, approve deliberately:

```
Any clinician         → proposes a synonym (PROPOSED)
Clinical lead         → approves or rejects, with a note
Approved              → enters search
```

| Action | Permission |
|---|---|
| Propose synonym | `REFERENCE_SYNONYM_PROPOSE` — any clinical role |
| Approve / reject synonym | `REFERENCE_SYNONYM_APPROVE` — clinical lead only |
| Add / remove common code | `REFERENCE_COMMON_MANAGE` — clinical lead per department |
| Import an ICD release | `REFERENCE_IMPORT` — admin only |

⚠ **All reference-data changes are audited.** Changing a synonym or a common list alters clinical behaviour going forward; the change needs an actor, a timestamp, and a reason. Retain the previous state.

Proposals appear on the clinical lead's review queue with the proposer, the term, the target code, and the number of times that term has been searched unsuccessfully — which is usually the strongest argument for approving it.

---

## 4. Curation UI (clinical lead)

The point of this module is that clinical staff maintain it without a developer.

```
┌─────────────────────────────────────────────────────────────┐
│ Common Diagnoses — Paediatrics                    [+ Add]   │
├─────────────────────────────────────────────────────────────┤
│ ⠿  B50.9   P. falciparum malaria, unspecified        [ × ]  │
│ ⠿  J18.9   Pneumonia, unspecified organism           [ × ]  │
│ ⠿  A09     Infectious gastroenteritis and colitis    [ × ]  │
│ ⠿  E44.0   Moderate protein-energy malnutrition      [ × ]  │
├─────────────────────────────────────────────────────────────┤
│ Suggested from usage — last 30 days                         │
│    B54     Unspecified malaria         selected 84×  [Add]  │
│    H66.9   Otitis media, unspecified   selected 61×  [Add]  │
└─────────────────────────────────────────────────────────────┘
```

Drag to reorder, remove with one click, and a **suggestions panel driven by actual selection frequency**.

⚠ Suggestions are **suggested, never auto-applied**. Frequency is a good signal and a poor decision-maker — a code selected often because it is the first plausible result is exactly the code that should not be promoted.

### 4.1 Seeding the initial list

Guessing the common set produces a list nobody uses. Better sequence:

1. Import the full WHO ICD-10 release with an empty common list
2. Run for two to four weeks of real use, logging every search term and every selection
3. Build the initial common list from actual selections, and the initial synonym set from search terms that returned nothing useful

The picker is slower in week one and correct from week five. Attempting to curate up front and correct later inverts that.

---

## 5. Search

### 5.1 Indexing

```sql
-- trigram for fuzzy and partial matching
CREATE INDEX ON icd10_code USING gin (title gin_trgm_ops);
CREATE INDEX ON icd10_synonym USING gin (term gin_trgm_ops);

-- prefix match on the code itself
CREATE INDEX ON icd10_code (code text_pattern_ops);
```

A doctor who knows the code types the code; a doctor who does not types words. Both paths must work.

### 5.2 Ranking

```
score =
    (exact code match)                  → 1000
  + (department common list)            →  500 − sort_rank
  + (hospital-wide common list)         →  300 − sort_rank
  + (approved synonym match)            →  200
  + (title prefix match)                →  100
  + (trigram similarity × 50)
  + (this user's recent selections)     →   25
```

Filters applied before ranking: `is_active = true` and `is_billable = true`.

### 5.3 Performance

| | |
|---|---|
| Minimum characters | 2 |
| Debounce | 200ms client-side |
| Result cap | 20 |
| Target latency | under 100ms server-side |
| Cache | Common lists per department in Redis, invalidated on curation change |

Reference data changes rarely and is read constantly, which makes it an ideal cache target. Do not query 14,000 rows on every keystroke.

### 5.4 Log every search

```
ReferenceSearchLog(
  id, actor_user_id, department_id, catalogue_type,
  query_text, result_count, selected_id, searched_at
)
```

This table feeds the suggestions panel in §4, identifies missing synonyms (searches with zero results or no selection), and shows which common lists are working. It is the mechanism that lets the reference layer improve itself.

Retain 90 days, then aggregate.

---

## 6. The shared picker component

```
Diagnosis:  [ mala|                              ]
            ┌──────────────────────────────────────┐
            │ B50.9  P. falciparum malaria, unspec │
            │ B50.0  P. falciparum with cerebral   │
            │ B51.9  P. vivax malaria              │
            │ B54    Unspecified malaria           │
            └──────────────────────────────────────┘
Details:    [ with severe anaemia, Hb 4.2         ]
```

| Configuration | Source | Stored | Extra field |
|---|---|---|---|
| Diagnosis | `Icd10Code` | code + title snapshot | free-text clarifier |
| Lab test | `ServiceItem` (lab) | `service_item_id` | urgency, instructions |
| Drug | `Item` (formulary) | `item_id` | dose config, route |
| Procedure | `ServiceItem` (procedure) | `service_item_id` | notes |

**Contract in every configuration:** the input feels like free text; the storage is always an identifier. There is no free-text-only path.

**Behaviour:** keyboard navigable, matched substring highlighted, code shown alongside the label so doctors learn the codes passively, no-results state offering "propose a synonym" rather than a dead end.

⚠ **Where a price applies** (lab, drug, procedure), the picker shows it on the row — the same rule as the triage referral picker. A doctor ordering a 90,000/= test should know before the patient reaches the cashier.

---

## 7. Diagnosis storage recap

```
Diagnosis(
  id, encounter_id, consultation_record_id,
  icd10_code            → Icd10Code,
  icd10_title_snapshot,               -- copied at diagnosis time
  free_text_clarifier,
  certainty,                          -- PROVISIONAL | CONFIRMED | RULED_OUT
  is_primary,                         -- UNIQUE (encounter_id) WHERE is_primary
  diagnosed_by, diagnosed_at,
  amends_id, amendment_reason
)
```

The title is snapshotted because ICD-10 titles are revised between releases and a 2026 record must read as it did in 2026.

### 7.1 Unspecified-code review

Conditions with no good code use the appropriate residual or unspecified code with detail in `free_text_clarifier` — never a free-text escape, which would reopen the hole coding exists to close.

Those rows are flagged to a **coding review queue** so the clinical lead can extend the common list or the synonym set. A rising unspecified rate is the signal that the reference data needs work, and it is worth a monthly report.

---

## 8. Governance reports

| Report | Purpose |
|---|---|
| Top 50 diagnoses by department, monthly | Validates and drives the common list |
| Unspecified / residual code rate | Rising rate means the reference data is failing clinicians |
| Zero-result searches | The synonym backlog, ranked by frequency |
| Synonym proposals pending | Review queue depth for the clinical lead |
| Reference-data change log | Who changed clinical behaviour, when, and why |

---

## 9. Open

| | Question |
|---|---|
| `?Q1` | Who is the clinical lead role — a new role, or a permission granted to a senior `SPECIALIST_DOCTOR`? |
| `?Q2` | Which ICD-10 release, and does the ministry mandate a specific one for statutory returns? |
| `?Q3` | Is a national or ministry-published common-diagnosis list available to seed from, rather than starting empty? |
| `?Q4` | Do procedures need ICD-9-CM / CPT coding for insurance claims, or is the internal procedure catalogue sufficient for your schemes? |
| `?Q5` | Should synonym proposals be visible to the proposer's whole department while pending, or only to the reviewer? |
