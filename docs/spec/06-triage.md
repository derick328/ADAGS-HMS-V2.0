# HMS — Triage Module Specification v0.3

**Supersedes:** v0.2
**Role:** `TRIAGE_NURSE`
**Status:** §2–§6 are decided. §7 lists flags raised but not yet ruled on.

---

## 1. Sidebar

```
Patient Queue          [12]
Completed Assessments
```

Doctor queue depth appears inside the referral picker (§5), not as a separate screen — the nurse needs it at the moment of choosing.

`Completed Assessments` is read-only. Corrections create an amendment (`supersedes_id`), never an in-place edit.

---

## 2. Field set (final)

**All visible fields are mandatory.** Conditional fields are mandatory once revealed.

### 2.1 Chief complaint

| Field | Type | Notes |
|---|---|---|
| `chief_complaint` * | plain text, 3–500 | Plain text confirmed. No coding, no autocomplete |
| `current_symptoms` * | plain text | See §7.1 — overlap unresolved |

### 2.2 Vital signs

| Field | Unit | Implausible (reject) | Abnormal (warn) |
|---|---|---|---|
| `temperature` * | °C | < 30 or > 43 | < 35.5 or > 38.0 |
| `systolic_bp` * | mmHg | < 40 or > 300 | < 90 or > 180 |
| `diastolic_bp` * | mmHg | < 20 or > 200 | > 110 |
| `pulse` * | bpm | < 20 or > 250 | < 50 or > 110 |
| `respiratory_rate` * | /min | < 4 or > 80 | < 12 or > 20 |
| `spo2` * | % | < 50 or > 100 | < 94 |

Implausible values are **rejected with a message naming the bound**, never silently clamped. Abnormal values save with a visible flag.

Adult thresholds above are wrong for children. Ranges live in `VitalReferenceRange` (age band, sex, four bounds, `effective_from`) so clinical staff can tune them without a deploy. Bands: neonate 0–1m, infant 1–12m, toddler 1–3y, child 3–12y, adolescent 12–18y, adult, elderly 65+.

### 2.3 Anthropometric

| Field | Notes |
|---|---|
| `weight_kg` * | Stored |
| `height_cm` * | Stored |
| `bmi` | **Computed only.** Not stored as an editable field, not nurse-enterable |

BMI resolution (confirmed): derived from weight and height, never typed.

```sql
bmi GENERATED ALWAYS AS (weight_kg / POWER(height_cm / 100.0, 2)) STORED
```

Calculated live in the browser for display as the nurse types. Suppressed from display for patients under 18 — raw BMI on a child is misleading, and a doctor acting on "BMI 15.2" for a five-year-old is acting on a meaningless number.

### 2.4 Medical history

| Field | Type |
|---|---|
| `allergies` * | Free text |
| `comorbidities` * | Free text |
| `mode_of_ambulation` * | Enum |

`mode_of_ambulation`: `WALKING` `INFANT_IN_MOTHERS_LAP` `WHEELCHAIR_ASSISTED` `STRETCHER_ASSISTED`

**Free-text allergies confirmed.** One consequence must be handled in the doctor and pharmacist modules rather than here:

> Automated allergy checking is not possible against free text. The check is therefore human, which means the allergy text must be rendered **inline and unmissable** on the prescribing screen and the dispensing screen — adjacent to the drug entry field, styled as a warning when non-empty, never inside a collapsed panel or a tab. A doctor who must click to see it will not see it.

Allergies are written to the **patient**, not only the encounter, so they persist to future visits.

### 2.5 Pain assessment

| Field | Behaviour |
|---|---|
| `has_pain` * | Boolean |
| `pain_score` | Hidden when `has_pain` is false. Required and 0–10 when true |
| `pain_location` | Same conditional rule |

Confirmed both directions: the UI hides the fields, the server discards any submitted values when `has_pain` is false, and both become required when it is true.

### 2.6 Risk assessment

| Field | Behaviour |
|---|---|
| `fall_risk` * | Boolean |
| `fall_score` | Hidden when false, required when true, discarded server-side when false |
| `infection_risk` * | Boolean |

`fall_risk` must be visible downstream — ward board and wristband, not only the triage record.

### 2.7 Pregnancy status

Triage does not ask pregnancy status. The clinician may record `YES`, `NO`, or `UNKNOWN` during consultation for applicable patients. The answer becomes required before a prescription is sent. Each clinician answer creates a new record, preserving who recorded what and when. The age bounds remain configurable.

`UNKNOWN` should prompt for a pregnancy test before any imaging or teratogenic prescribing. That prompt belongs in the doctor module.

### 2.8 Triage priority

| Value | Meaning |
|---|---|
| `IMMEDIATE` | Life-threatening, immediate intervention |
| `URGENT` | Serious but stable |
| `LESS_URGENT` | Can wait a short time |
| `NON_URGENT` | Minor issue |

Required. Nurse-selected. See §4 for `IMMEDIATE` handling.

### 2.9 Notes

Free text, 0–2000. **Recommended optional** — a mandatory notes field produces `n/a`, `-`, and `none` in three-quarters of rows, which is worse than empty. Flag for confirmation.

---

## 3. Queue

```sql
ORDER BY priority ASC,          -- IMMEDIATE, URGENT, LESS_URGENT, NON_URGENT
         arrived_at ASC
```

Not FIFO. Re-sorts live over WebSocket on any priority change.

No locking (confirmed earlier). An open assessment shows `Being assessed by N. Kimaro` as advisory status; a second nurse can still open it. Optimistic locking via `Encounter.version` means the second submit fails with a reload prompt rather than overwriting.

Assessments autosave server-side every 20 seconds keyed to `(encounter_id, author_id)`, so a nurse whose device fails resumes on another device with the draft intact.

---

## 4. `IMMEDIATE` handling

### 4.1 Confirmation

Selecting `IMMEDIATE` raises a confirmation stating that the patient is being classified as critically ill, will be given top priority everywhere, and will not be blocked by payment.

**The confirmation requires a selection, not an acknowledgement.** A plain OK dialog attracts click-through blindness within a week. The nurse picks a reason:

```
○ Threatened or obstructed airway
○ Severe respiratory distress
○ Shock / circulatory collapse
○ Unresponsive or responding only to pain
○ Active uncontrolled bleeding
○ Active convulsion
○ Obstetric emergency
○ Other  → free text required
```

The selection is stored as the bypass reason. One extra tap, and the justification lands in the record.

### 4.2 Consequences

```
priority = IMMEDIATE
    → encounter.type = EMERGENCY
    → PaymentBypass created, source = TRIAGE_IMMEDIATE, ACTIVE
    → AWAITING_PAYMENT skipped entirely → AWAITING_CONSULT
    → top of the assigned doctor's queue
    → consultation, lab, pharmacy, procedures all permitted
    → charges accrue and appear on the receptionist's emergency billing bar
```

The charge is never skipped, only unblocked.

`URGENT` and below follow the normal gate. Bypass for those requires clinician authorisation — not a triage-nurse action.

---

## 5. Referral (mandatory)

**Confirmed: referral can never be blank.** This closes the pricing gap — every triage assessment now produces a priced charge.

### 5.1 The picker

```
┌─────────────────────────────────────────────┐
│ Dr. J. Mushi        General      10,000/=   │
│ Internal Medicine   ● 4 waiting  ~35 min    │
├─────────────────────────────────────────────┤
│ Dr. A. Kessy        Specialist   40,000/=   │
│ Cardiology          ● 1 waiting  ~10 min    │
├─────────────────────────────────────────────┤
│ Dr. S. Mally        General      10,000/=   │
│ On leave until 12 Aug           unavailable │
└─────────────────────────────────────────────┘
```

The fee is shown on every card. A nurse choosing between two available doctors is choosing between 10,000/= and 40,000/= and must be told so — otherwise the patient discovers it at the cashier.

Queue depth and estimated wait come from a live projection over WebSocket. Off-duty providers appear greyed with the reason rather than hidden; the nurse needs to know why someone is not an option.

Load balancing across doctor queues is the intended use, as specified.

### 5.2 Redirect before payment

```
while encounter.state == AWAITING_PAYMENT and payments == 0:
    reassignment is free
    → void the existing consultation charge line
    → post a new line at the new provider's rate
    → emit EncounterReassigned   (receptionist board updates live)
    → log actor and reason
```

Once any payment lands, redirecting across provider types becomes a top-up or refund and is blocked with a clear message. Reassignment within the same provider type stays free — same price, no financial change.

---

## 6. Submission

One transaction:

1. Insert `VitalSigns`
2. Insert `TriageAssessment`
3. Write `allergies` and `comorbidities` to the **patient** record
4. Update `Encounter` — assigned provider, department, `type = EMERGENCY` where applicable
5. Create `PaymentBypass` when `IMMEDIATE`
6. Post the consultation charge at the assigned provider's rate from the active price list
7. Transition to `AWAITING_PAYMENT`, or `AWAITING_CONSULT` when bypassed
8. Insert `EncounterEvent`
9. Emit `TriageCompleted`

Step 6 failing rolls back everything. A triaged patient with no charge walks straight past the cashier.

---

## 7. Raised but not ruled on

Kept visible rather than dropped. None of these block building §2–§6.

### 7.1 `chief_complaint` and `current_symptoms` overlap
Both plain text, both mandatory, covering nearly the same ground. The nurse will describe the complaint in one and paraphrase it in the other, splitting the data across two columns permanently. Either drop `current_symptoms` or give it a distinct purpose — duration and progression rather than a second description.

### 7.2 "Unable to obtain" escape on vitals
All-mandatory vitals will produce fabricated numbers: height on a stretcher patient, BP on a screaming infant. A typed guess is worse than a blank, because it reads as a measurement downstream. Proposal: keep every field mandatory, but add a per-field "unable to obtain" control with a one-tap reason (`not measurable`, `patient uncooperative`, `equipment unavailable`, `not clinically indicated`). Same completeness enforcement, honest data.

### 7.3 `AVPU`
Level of consciousness — `ALERT` `VOICE` `PAIN` `UNRESPONSIVE`. One enum, four values, and the strongest single predictor of deterioration in every published triage scale. Currently absent from the field set.

### 7.4 Blood glucose
Conditionally required when consciousness is impaired, a convulsion is reported, or the patient is a known diabetic. Hypoglycaemia presenting as a neurological emergency is a cheap catch and an expensive miss.

### 7.5 `infection_risk` has no consequence
A boolean with no downstream effect is decoration — the nurse ticks it and the patient sits in general waiting anyway. Minimum viable version: capture what is suspected and set an isolation flag that visibly follows the patient through every queue.

### 7.6 MUAC for under-5s
Standard malnutrition screen, triggers a nutrition referral independent of the presenting complaint.

### 7.7 `DECEASED_ON_ARRIVAL`
Rare, but the priority enum currently has nowhere to put it.

### 7.8 Re-assessment of long waits
No mechanism exists to re-score a waiting patient. `IMMEDIATE` bypasses payment, but a `LESS_URGENT` patient who waits two hours in `AWAITING_PAYMENT` and deteriorates is not re-triaged. Deterioration in the waiting room is the classic triage failure mode.

### 7.9 Computed priority suggestion
The vitals needed to derive a suggested priority are already being collected. Prefilling the priority and requiring a reason on override would materially improve consistency between nurses at near-zero cost, without removing the nurse's decision.

### 7.10 Overnight `EMERGENCY` declaration
Who declares it when no triage nurse is rostered?

### 7.11 Standing-order tests at triage
Malaria RDT, glucose, pregnancy test before consultation — common regional practice and it shortens visits considerably, but it creates orders and charges from a nurse without prescribing authority. Needs a fixed approved list if permitted.

### 7.12 `Completed Assessments` scope
This nurse's work only, or the whole shift's?

### 7.13 Mandatory `notes`
Recommended optional per §2.9.
