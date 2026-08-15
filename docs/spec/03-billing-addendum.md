# HMS — Billing Architecture Addendum

**Version:** 1.1 — extends `hms-billing-architecture.md`
**Resolves:** §12 `?Q1`, `?Q2`, `?Q3`, `?Q4`

---

## 1. `?Q1` — Payment allocation (resolved)

**Exact-amount allocation, receptionist-directed, no partials except emergency.**

```
PaymentAllocation(payment_id, invoice_line_id, amount_minor)
```

The payment screen is a **line picker**. The receptionist ticks which lines the money is for; the total fills in.

```
allocated_to_line == line.patient_portion   (exact)
    → gate opens for that line

allocated_to_line < line.patient_portion
    → REJECTED, unless an active PaymentBypass exists
```

⚠ **No partial payment on any single line.** A 10,000/= consultation is paid as 10,000/= or not at all. This is simpler to enforce than proportional allocation and removes the ambiguity of which line a part-payment satisfies.

**Emergency exception:** with an active bypass, partial payment is accepted and the remainder accrues to the emergency balance.

**Consequence to be aware of:** a patient with 10,000/= who needs a 20,000/= X-ray gets nothing rather than a part-payment toward it. Correct if the hospital does not take deposits against individual services; revisit if it does.

Overpayment on a line still creates a patient credit rather than a negative balance.

---

## 2. `?Q2` — Result delivery, and why the follow-up price mostly disappears (resolved)

### 2.1 The flow

```
Result verified → doctor notified
                → consultation CANNOT be completed until results
                  are given to the patient

Patient still present
    → seen immediately, results explained, consultation completed

Patient has left
    → doctor flags the result to reception
    → appears on reception's "Ungiven lab results" section
    → patient returns and tells reception
    → reception flags them
    → patient reappears on the doctor's page
    → doctor explains, THEN completes the consultation
```

### 2.2 The billing consequence

⚠ **This is one encounter throughout.** The return visit resumes an open encounter rather than starting a new one.

```
No new triage
No new consultation charge
No follow-up price question
Encounter state: AWAITING_RESULTS → AWAITING_CONSULT
```

The patient paid on Monday. Coming back Wednesday for the doctor to read Monday's results is the same visit continuing, and charging again would be charging twice for one consultation.

⚠ **`?Q2` from the main document (follow-up consultation price) is therefore narrowed, not eliminated.** It still applies to a genuinely *new* visit — a doctor-booked review appointment for a fortnight later, which is a new encounter with new triage. That price is still unset.

```
Resuming for results       same encounter, no charge         ✓ resolved
Doctor-booked review       new encounter, price = ?          still open
```

### 2.3 Ungiven results need an age and an owner

⚠ A result sitting unclaimed has no natural exit — the patient may never return. Without an escalation the list grows forever and reception stops reading it.

```
UngivenResultFlag(
  id, lab_result_id, encounter_id, patient_id,
  flagged_by, flagged_at,
  patient_returned_at, returned_flagged_by,
  delivered_at, delivered_by,
  escalated_at, escalation_reason,
  status              -- AWAITING_PATIENT | PATIENT_PRESENT
                      -- | DELIVERED | ESCALATED | CLOSED_UNDELIVERED
)
```

**Reception's list shows age per row:**

```
Ungiven lab results                                        [4]

  Amina Hassan   · FBC, Malaria RDT     ·  2 days
  Joseph Mwita   · Blood culture        ·  9 days
  Grace Kimaro   · Lipid profile        · 31 days   ⚠
  Peter Shayo    · Histopathology       · 44 days   ⚠ escalated
```

**Escalation:**

```
> 14 days   highlighted on reception's list
> 30 days   escalated to CLINICAL_LEAD
            → decide: chase the patient, or close undelivered
            → CLOSED_UNDELIVERED requires a reason and is recorded
```

⚠ **The clinical lead owns the decision, not reception.** Whether an unclaimed abnormal result warrants chasing a patient is a clinical judgement — an abnormal histopathology result is not the same as a normal lipid profile.

⚠ **Critical results never enter this list.** A `C`-flagged result goes straight to the doctor with acknowledgement tracking and escalation on timeout, regardless of whether the patient is present. It is never left waiting for someone to walk back in.

### 2.4 Interaction with the incomplete-consultation prompt

⚠ **A doctor blocked from completing must not be nagged about it.**

```
Consultation OPEN, encounter AWAITING_RESULTS
    → EXCLUDED from the 12-hour incomplete list
    → EXCLUDED from the 48-hour clinical lead escalation
    → EXCLUDED from the 7-day auto-close

Consultation OPEN, results delivered, still not completed
    → INCLUDED in all three
```

This exclusion already exists in the incomplete-consultation spec §2. Confirming it covers the ungiven-results case: the doctor is waiting on the patient, not neglecting the record.

⚠ **Do not auto-close an encounter with an outstanding ungiven result.** The clock only starts once §2.3 resolves it.

---

## 3. `?Q3` — Stale encounters (resolved: no deletion, no sweep)

**Nothing is ever deleted.** No sweep closes encounters, and no consultation record is removed.

Consequence: encounters can remain open indefinitely, so any "who is in the building" view must filter by date rather than by state alone.

```
Reception "Here now"     encounters opened today, or still open
                         from the last N days
Doctor "My Queue"        state = AWAITING_CONSULT (already bounded)
Doctor "Active"          opened within the last N days
```

⚠ The existing incomplete-consultation mechanism still applies — 12-hour prompt, 48-hour escalation to `CLINICAL_LEAD` — but with no auto-close at the end. The consultation stays open until a clinician completes or abandons it, and abandonment (with a reason) remains the only closure path.

`?Q1` — what is N for the date filter? 30 days keeps the list usable without hiding anything genuinely active.

---

## 4. `?Q4` — Procedures (resolved)

### 4.1 Consent blocks scheduling, not billing

⚠ **A signed consent document is required before a procedure can be scheduled**, and checked again at procedure start.

```
No consent_document_id
    → scheduling BLOCKED
    → message: "Signed consent document required before this
                procedure can be scheduled."

Consent present at scheduling, missing at start (revoked, superseded)
    → procedure start BLOCKED
```

⚠ **Blocking at billing would be worse.** The patient pays, then discovers they cannot proceed — and now there is a refund to process for a procedure that never should have been payable. Blocking at scheduling means nobody pays for something they cannot have.

⚠ A `consent_obtained` checkbox is **not** consent. The checkbox records that the conversation happened; the signed PDF is the evidence.

```
SurgeryOrder / ProcedureOrder:
  consent_obtained        BOOLEAN NOT NULL DEFAULT false
  consent_document_id     → object store key, NOT NULL before scheduling
  consent_obtained_by     the clinician who took consent
  consent_obtained_at
```

### 4.2 Procedure gate timing

```
Gate checks at PROCEDURE START — before the consumable pack is opened
```

Consistent with the other two:

| Service | Gate moment | Why there |
|---|---|---|
| Lab | Specimen collection | Before reagents are consumed |
| Pharmacy | Before stock leaves | Before the drug leaves the shelf |
| **Procedure** | **Procedure start** | **Before the pack is opened** |

Nothing is consumed before the check, so a refusal costs the hospital nothing. Gating at scheduling instead would mean a paid slot held by a patient who never arrives, and a patient who can pay on the day unable to book.

### 4.3 Procedure items — per-item authorization, before or after

⚠ Drugs and lab tests prepared by the doctor or ward nurse for a procedure may be **paid before or after the procedure**. This is a per-item decision, not one gate for the whole procedure.

```
ProcedureItem
  authorization_mode ∈ PREPAID | DEFERRED

PREPAID    → gate applies before the item is used
DEFERRED   → ServiceAuthorization created with type DEFERRED
           → item fulfils immediately
           → charge accrues to the encounter balance
```

The existing `ServiceAuthorization` mechanism covers this — a deferred item is authorized by its mode, exactly as a bypassed item is authorized by its bypass. `isSatisfied()` needs no new clause: the `authorization_id IS NOT NULL` branch already handles it.

⚠ **Items are priced from the catalogue, never typed on the form.** This was the resolution in the ward nurse spec, and it applies to the doctor's procedure items equally.

⚠ **Who decides `DEFERRED`?** Not the person preparing the item — that would be a self-granted bypass on every procedure. Suggest: `DEFERRED` is available when the encounter is `EMERGENCY`, when the patient is an inpatient with a deposit, or with a specific authorising permission. Otherwise `PREPAID`.

`?Q2` — confirm who may mark an item deferred.

### 4.4 Scheduling interface

The doctor's disposition includes `SCHEDULE_SURGERY`, which opens a scheduling view showing existing procedure bookings so a day can be chosen.

⚠ Guards on the slot itself:

```
Theatre not already booked for that slot
    → UNIQUE (theatre_id, slot) WHERE cancelled_at IS NULL

Consent document present (§4.1)
Required staff available per the roster
```

The database constraint is what prevents two procedures being booked into one theatre — a read-then-write availability check loses the race.

---

## 5. Summary of changes to the main document

| Section | Change |
|---|---|
| §3.1 | Per-line gating requires exact allocation; `PaymentAllocation` added |
| §9.2 | Payment screen becomes a line picker |
| §12 `?Q1` | Resolved — §1 here |
| §12 `?Q2` | Narrowed — resuming for results is free; doctor-booked reviews still unpriced |
| §12 `?Q3` | Resolved — no sweep, no deletion; date-filter the active views instead |
| §12 `?Q4` | Resolved — gate at procedure start; consent blocks scheduling |
| New | Ungiven lab results list, with age and clinical lead escalation |
| New | Per-item `authorization_mode` for procedure consumables |

---

## 6. Still open

| | Question |
|---|---|
| `?Q1` | Date-filter window for "active" views now that encounters never auto-close — 30 days? |
| `?Q2` | Who may mark a procedure item `DEFERRED`? |
| `?Q3` | Price for a doctor-booked review appointment (a genuinely new encounter, unlike §2.2) |
| `?Q4` | Does `CLOSED_UNDELIVERED` on an ungiven result require patient contact to have been attempted first? |

---

## 7. Ward nurse actions with billing consequences

Four points where a clinical role's action moves money. Three are governance; one was a genuine over-billing gap.

### 7.1 Discharge stops the billing clock — record who ordered it

The discharge order sets `discharge_ordered_at`, which stops bed-day accrual. A clinical role therefore reduces a bill with no approval step.

That is clinically necessary — a discharge cannot wait for an accountant. What it needs is visibility.

```
Admission.discharge_ordered_by       -- always recorded
Admission.discharge_orderer_role     -- DOCTOR | SPECIALIST | NURSE
```

Authorised: doctor, specialist, and the nurse assigned to that patient by that doctor.

⚠ **Monthly report: discharges by ordering role and by user**, alongside the mean ordered-to-departure gap. An outlier is a conversation, not an automated block — the same pattern as triage priority downgrades.

### 7.2 Bed choice sets the rate — the admission order specifies the class

Private wins the day (§3.4 of the bed-day rules), so a nurse placing a patient in a private bed because it happened to be free bills them at the private rate. The patient discovers it at the desk.

```
Admission.ward_class          -- set by the ADMITTING DOCTOR
BedAssignment.bed_id          -- chosen by the nurse, WITHIN that class
```

⚠ Placing a patient above the ordered class requires either a doctor's amended order or a recorded reason:

```
ward_class_override_reason ∈
    NO_BED_IN_ORDERED_CLASS
    CLINICAL_NEED (isolation, monitoring)
    PATIENT_REQUEST          -- patient accepts the higher rate
    OTHER (free text)
```

`PATIENT_REQUEST` should surface a confirmation showing the rate difference, so consent to the cost is explicit.

Overrides appear on the same monthly report as §7.1.

### 7.3 Unadministered ward medication — resolved

Per-patient dispensing means the full course is dispensed and **charged upfront**:

```
Doctor prescribes 15 doses  →  pharmacy dispenses 15, charge posts
Nurse administers 6         →  patient discharged
                            →  9 doses paid for, not given
```

**Resolution — by route:**

| Route | Undispensed remainder | Charge |
|---|---|---|
| **Oral** (tablet, capsule, syrup) | Patient takes it home | **Stands** — the patient received the medicine |
| **Parenteral** (IV, IM, SC) and anything requiring administration | Returned to pharmacy | **Credit note** for the unadministered quantity |

⚠ Oral is the confirmed default. Parenteral cannot go home — a patient cannot take nine vials of IV ceftriaxone — so those return with a stock movement and a credit.

```
On discharge, for each ward prescription with quantity_dispensed > quantity_administered:

  route is oral        →  no action; charge stands
                          discharge summary lists what goes home

  route is parenteral  →  return movement: +qty to pharmacy stock
                          (to the originating batch, or quarantine
                          if the vial was opened)
                       →  credit note on the invoice for the
                          unadministered lines
                       →  appears on the pharmacist's returns queue
```

⚠ The same rule applies to a course stopped mid-way — `CLINICAL_HOLD`, `NIL_BY_MOUTH`, a changed prescription — not only to discharge.

`?Q1` — does an opened but unused parenteral vial return to stock, or to quarantine and write-off? Sterility usually rules out returning it to the shelf.

### 7.4 Post-procedure admission — the clock starts at admission

```
Bed-day accrual begins at the moment the patient is SENT TO ADMISSION
```

Not at procedure end, and not at arrival on the ward. Recovery-room time is part of the procedure, not a bed-day.

```
Procedure ends 08:00
Recovery       08:00 – 09:00
Sent to admission 09:00        →  admission_at = 09:00
                               →  inside the 06:00–09:59 window
                               →  effective start 10:00 that day
```

⚠ The morning window and the 4-hour grace both apply normally from that timestamp, so a post-procedure admission is treated exactly like any other.

```
Admission.admitted_at = the sent-to-admission timestamp
```

---

## 8. Open from this section

| | Question |
|---|---|
| `?Q1` | Opened parenteral vials — return to stock, or quarantine and write off? |
| `?Q2` | Does the discharge summary need to itemise take-home medicine for the patient's own record? |
| `?Q3` | Who processes the returns queue — pharmacist confirms receipt, or is the nurse's return movement sufficient? |
