# HMS — Doctor Module Specification v0.3

**Supersedes:** v0.2
**Scope:** consultation phase. Ward and surgery deferred (§12).
**Roles:** `DOCTOR`, `SPECIALIST_DOCTOR`

---

## Changes from v0.2

| | Change |
|---|---|
| 1 | Diagnosis is **ICD-10 coded** — resolves the open question from v0.1 |
| 2 | Lab tests and prescriptions are **catalogue-backed with typeahead**. `PENDING_PRICING` removed entirely |
| 3 | `UNCONFIRMED` and `REFUTED` allergies receive **identical treatment** downstream |
| 4 | `DepositAccount` attaches to `Admission`, not `Encounter` — inpatient only |
| 5 | Billing spec §2 wording remains **pending**, not resolved |

---

## 1. Locked rules carried forward

| | Rule |
|---|---|
| R1 | Discharge is never blocked by an unpaid balance. `if medical_complete → CLOSED`, `if balance > 0 → receivable`. Surgical discharge parked with the surgery workflow |
| R2 | Consultation is payment-gated for every patient except `IMMEDIATE`, which holds an active `PaymentBypass` |
| R3 | Inpatients receive medication without prior payment; charges accrue |

---

## 2. Queue

```sql
ORDER BY (encounter.type = 'EMERGENCY') DESC,   -- IMMEDIATE always first
         triage_priority ASC,
         gate_satisfied_at ASC
```

`IMMEDIATE` outranks everything, always. Within remaining bands, ordering is by time since the payment cleared — not arrival time, since a patient who paid at 09:00 should precede one who paid at 09:40.

Entry guard:

```
canOpenConsultation(encounter) =
       encounter.state == AWAITING_CONSULT
    OR active_payment_bypass_exists(encounter)
```

---

## 3. The shared picker component

One component, four configurations. Build it once.

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

| Used for | Source table | Stored |
|---|---|---|
| Diagnosis | `Icd10Code` | code + snapshot label + clarifier |
| Lab test | `ServiceItem` (lab) | `service_item_id` |
| Drug | `Item` (formulary) | `item_id` |
| Procedure | `ServiceItem` (procedure) | `service_item_id` |

**Behaviour in all four cases:** free-text-*feeling* input, catalogue-backed storage. The user types; the system stores an identifier. No free-text-only path.

**Mechanics:** minimum 2 characters, 200ms debounce, results capped at 20, keyboard navigable, target under 100ms server response. Recent selections by this user ranked first.

---

## 4. Diagnosis

### 4.1 Storage

```
Diagnosis(
  id, encounter_id, consultation_record_id,
  icd10_code            → Icd10Code,
  icd10_title_snapshot,               -- copied at diagnosis time
  free_text_clarifier,                -- the doctor's own words
  certainty,                          -- PROVISIONAL | CONFIRMED | RULED_OUT
  is_primary,
  diagnosed_by, diagnosed_at,
  amends_id, amendment_reason
)
```

⚠ **The title is snapshotted, not only referenced.** ICD-10 titles are revised between releases, and a record written in 2026 must still read as it did in 2026. Same reasoning as versioning the triage scale.

Multiple diagnoses per encounter are permitted. Exactly one may be primary:

```sql
UNIQUE (encounter_id) WHERE is_primary = true
```

### 4.2 Reference data

```
Icd10Code(
  code, title, chapter, block, parent_code,
  is_active, is_common, sort_rank
)

Icd10Synonym(
  id, code → Icd10Code, term, language
)
```

**`is_common` is what makes this usable.** Full ICD-10 is roughly 14,000 codes; a doctor typing `mal` otherwise gets malaria, malnutrition, malignant neoplasms, malformations, and malaise thirty rows deep. Curate 300–500 locally common codes, rank them first, keep the full list searchable behind them.

The common set is **data, editable by clinical staff** through the admin module — not a hardcoded list. Departments will want to tune it.

⚠ **`Icd10Synonym` matters in this setting specifically.** Doctors and patients name conditions in Swahili and in local clinical shorthand. Mapping those terms onto codes means nobody has to memorise ICD vocabulary, and it is largely what determines whether the coding decision survives contact with a busy clinic.

Search: trigram + full-text over `title` and synonym `term`, plus code prefix match, with `is_common` boosted.

### 4.3 Conditions with no good code

Rare, but real. The answer is **not** a free-text escape — that reopens the hole coding exists to close. Use the appropriate ICD unspecified or residual code with the detail in `free_text_clarifier`, and flag the row for coding review so the common-codes list can be extended.

### 4.4 `final_diagnosis` remains nullable at finalize

A doctor closing with labs pending frequently cannot state one; forcing the field produces guesses. It becomes completable when results arrive (§8.2), as an amendment to the finalized record.

---

## 5. Consultation form

### 5.1 Inherited from triage — read-only panel

```
Vital signs (all)          Mode of ambulation
Weight, height, BMI        Triage priority
Triage chief complaint     Arrival time and mode
```

### 5.2 Re-assessed — new record, triage's original stands

```
Pain: has_pain / score / location
Fall risk / fall score
Infectious disease risk
Pregnancy status  (females within configured age bounds; optional in consultation, required before prescribing; YES/NO/UNKNOWN; new record on update, never overwrite)
```

`UNKNOWN` pregnancy status prompts for a test before imaging or teratogenic prescribing.

### 5.3 The doctor's own work

| Field | Type |
|---|---|
| `history_of_presenting_illness` * | text |
| `past_medical_history` | text |
| `past_surgical_history` | text |
| `family_social_history` | text |
| `physical_examination` * | text |
| `clinical_notes` | text |
| `provisional_diagnosis` * | coded — §4 |
| `final_diagnosis` | coded, nullable — §4 |
| `treatment_plan` * | text |

`chief_complaints` is absent from the doctor's form — triage records the patient's words, and `history_of_presenting_illness` is the clinical expansion.

---

## 6. Allergy reconciliation

```
AllergyRecord(
  id, patient_id,
  allergen_text,                -- free text (confirmed)
  reaction, severity,
  source,                       -- TRIAGE | DOCTOR | PHARMACIST | PATIENT_REPORTED
  recorded_by, recorded_at,
  confirmation_status,          -- UNCONFIRMED | CONFIRMED | REFUTED
  confirmed_by, confirmed_at, confirmation_note,
  supersedes_id
)
```

### 6.1 Status treatment (final)

⚠ **`UNCONFIRMED` and `REFUTED` are treated identically everywhere.** Both display, both warn, neither is filtered from any downstream view. Only the label differs, so the history remains readable.

| Status | Displayed | Warns | Filtered out |
|---|---|---|---|
| `CONFIRMED` | Yes | Yes | Never |
| `UNCONFIRMED` | Yes | Yes | Never |
| `REFUTED` | Yes | Yes | Never |

Consequence to be aware of: the confirm/refute action is **documentary, not functional**. It records what was investigated and by whom; it does not change what anyone downstream sees or reduce warning volume. The value is the audit trail and the clarifying note.

### 6.2 Comparison view

```
┌──────────────────────────────────────────────────────────┐
│ ALLERGIES — reconcile                                    │
├──────────────────────────────────────────────────────────┤
│ From triage (N. Kimaro, 14:22)                           │
│ "penicillin - rash"                    UNCONFIRMED       │
│                            [Confirm] [Refute] [Amend]    │
├──────────────────────────────────────────────────────────┤
│ From Dr. Mushi (15:04)                                   │
│ "no known drug allergy"                CONFIRMED         │
├──────────────────────────────────────────────────────────┤
│ Add allergy                                       [ + ]  │
└──────────────────────────────────────────────────────────┘
```

Rules: nothing is ever deleted · refuting requires a note · records persist on the patient across encounters · conflicting records are shown as a conflict, never collapsed into one value.

### 6.3 Prescribing screen

Automated matching is impossible against free text, so the check is human. **Every allergy record, regardless of status, renders inline and adjacent to the drug entry field**, styled as a warning — never in a collapsed panel or a separate tab. A doctor who must click to see it will not see it.

---

## 7. Disposition

| `nextStep` | Transition | Status |
|---|---|---|
| `CONSULT_COMPLETE` | → `PENDING_SETTLEMENT` → `CLOSED` | Active. Balance → receivable if unpaid |
| `REFERRED_DOCTOR` | stays open, reassigned | Active — §7.1 |
| `REFERRED_SPECIALIST` | stays open, reassigned | Active — §7.1 |
| `REFERRED_OUT` | terminal | To add |
| `DECEASED` | terminal | To add |
| `ABSCONDED` | terminal | To add |
| `ADMITTED` | → `ADMITTED` | Deferred (§12) |
| `SCHEDULE_SURGERY` | → `AWAITING_PROCEDURE` | Deferred (§12) |

`DISCHARGED` is renamed `CONSULT_COMPLETE` for the outpatient case, reserving `DISCHARGED` for ward discharge.

### 7.1 Internal referral stays inside one encounter

A same-day referral is one visit. A second `Encounter` would break the one-open-encounter index, double-count the patient in the building, and fragment the visit's charges.

```
same encounter
  + new ConsultationRecord for the receiving doctor
  + reassign assigned_provider_id
  + post a new consultation charge at the receiving doctor's full rate
  + state → AWAITING_PAYMENT  (or AWAITING_CONSULT if bypassed)
  + receiving doctor sees the triage record and the referring doctor's note
```

Full rate, not a top-up: general then specialist = 10,000/= then 40,000/=.

Future-dated follow-up referrals are different — those create a new encounter on arrival, linked by `source_encounter_id`.

### 7.2 Follow-up

`follow_up_date` and `follow_up_instructions`, optional. Doctors are the only role creating appointments. Appears on the receptionist's read-only Follow-ups list.

---

## 8. Orders

### 8.1 Catalogue-backed, no pricing step

⚠ **`PENDING_PRICING` is removed.** Every lab test, drug, and procedure resolves to a catalogue item, which carries a price, so:

```
order placed
   → charge posts automatically from the active price list
   → AWAITING_PAYMENT
   → BillingGate evaluable at the point of fulfilment
```

No manual pricing worklist, nobody to staff it, and no order that the gate cannot evaluate. The typeahead makes this feel like free text to the doctor while keeping every order priced and processable.

Where a genuinely novel test or procedure is needed, it is added to the catalogue by admin with a price — a one-time action, not a per-order workaround.

### 8.2 Late results

```
Encounter still open
    → AWAITING_RESULTS → AWAITING_CONSULT → ordering doctor's queue

Encounter CLOSED
    → critical/abnormal → immediate notification to ordering doctor,
                          acknowledgement tracked, escalation on timeout
    → normal            → Follow-ups queue
```

The ordering doctor owns the result; escalation goes to the department head where he is unavailable. No result enters an unowned queue.

`final_diagnosis` becomes completable here as an amendment.

### 8.3 Imaging

Still missing from the described workflow though the billing rules reference an X-ray charge. Same order shape, routed to radiology.

---

## 9. Prescriptions (outpatient)

Per drug: formulary item via typeahead, route, instructions, and either dispense-as-whole or `doses_per_day` × `quantity_per_dose` × `days` with computed total. Doctor removes undispensed items, then sends to payment, generating a pharmacy invoice for reception.

- Allergy records rendered inline per §6.3
- **Quantity sanity check** — `10 doses/day × 3 tablets × 90 days` = 2,700 tablets. Warn above a per-item plausible ceiling
- **Paediatric dosing** shows the triage weight beside the dose field for under-18s; weight is mandatory at triage so it is always present

---

## 10. Draft and finalize

```
finalize = false → ConsultationRecord.status = OPEN       (editable)
finalize = true  → ConsultationRecord.status = FINALIZED   (immutable)
```

Finalizing the record is not closing the encounter — a note can be finalized while labs are outstanding, and the encounter stays open in `AWAITING_RESULTS`.

`FINALIZED` is immutable; corrections create an amendment with the original preserved. Nobody un-finalizes.

**Draft sweep:** drafts untouched beyond a configured window are flagged, then closed `CLOSED_ADMINISTRATIVELY` with the reason recorded — distinguishable from clinical closure in reporting.

Autosave server-side every 20 seconds keyed to `(encounter_id, author_id)`.

---

## 11. Deposit ledger (inpatient)

⚠ Attaches to `Admission`, not `Encounter`. Outpatients pay per service; only admitted patients hold a running balance. Non-insurance patients only.

```
DepositAccount(id, admission_id, patient_id, opened_at, closed_at)

DepositTransaction(
  id, deposit_account_id,
  direction,            -- TOP_UP | DEDUCTION | REFUND | ADJUSTMENT
  amount_minor,
  reference_type, reference_id,      -- the invoice line that consumed it
  actor_user_id, cashier_shift_id,
  idempotency_key, occurred_at
)
```

Balance is derived from the ledger, materialised into a cached column, with a nightly job recomputing from source and alerting on drift. A mutable balance field drifts and cannot be reconstructed when a patient disputes a statement.

| Rule | |
|---|---|
| Accounting | A deposit is a **liability, not revenue**. Excluded from revenue reports until consumed |
| Refunds | Unused balance is refundable at discharge via an explicit accountant action |
| Exhaustion | Does not block care. Dispensing continues, shortfall flagged, reception and accounts alerted, balance becomes a receivable at discharge |

Permissions: receptionist records top-ups against an open shift · system deducts on charge posting · accountant only for refunds and manual adjustments.

---

## 12. Deferred

Ward admission and management · surgery workflow (including the surgical discharge rule, price-on-order, procedure catalogue, consent document, post-op observations) · bed assignment and occupancy · inpatient MAR.

Notes on these remain in `hms-doctor-module.md` v0.1.

---

## 13. Open

| | Question |
|---|---|
| `?Q1` | Billing spec §2 wording — pending |
| `?Q2` | Can an insured patient hold a deposit against their top-up portion, or cash-payers only? |
| `?Q3` | Drug interaction checking against the coded formulary in v1? |
| `?Q4` | Controlled-substance register and second signature in v1? |
| `?Q5` | Can a doctor amend another doctor's finalized note, or only append? |
| `?Q6` | Does a specialist receive the referring doctor's note automatically, or is an explicit referral summary field needed? |
| `?Q7` | Sick-leave / medical certificate generation — consultation phase or later? |
| `?Q8` | Who curates the `is_common` ICD-10 list and the synonym table — a clinical lead, or admin? |
