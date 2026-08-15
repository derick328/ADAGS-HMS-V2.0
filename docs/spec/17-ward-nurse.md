# HMS — Ward Nurse Module Specification v0.1

**Roles:** `WARD_NURSE`, and `NURSE` sharing access
**Routes:** `/ward-nurse/**`
**Depends on:** `inpatient`, `clinical`, `orders`, `pharmacy`, `inventory`, `billing`
**Notation:** `✗` contradicts a locked decision · `⚠` change from the described workflow · `?` open

---

## 1. The core principle is right

> Admission and orders are written by the doctor. The nurse executes them.

This is correct and worth stating as an invariant. Every deviation below is a place where the implementation departs from it.

```
Doctor writes    →  admission, ward prescriptions, ward lab requests,
                    surgery orders, discharge decision
Nurse executes   →  vitals, medication administration, wound care,
                    status observation, bed management, surgical
                    episode documentation
```

---

## 2. Contradictions with locked decisions

### ✗1 `DISCHARGED` deactivates the patient record

**Described:** `WardNurseService.updateStatus` — "DISCHARGED also deactivates the patient record."

**Conflicts with:** receptionist decisions ⚠8, settled earlier.

`patient.active = false` means this human is no longer a patient of this hospital. Applied to a ward discharge, a patient who recovers and goes home is deactivated; their next visit either fails lookup or creates a duplicate with a fragmented clinical history.

**Resolution:**

```
WardPatientAssignment.status = DISCHARGED
Admission.discharged_at      = now()
BedAssignment.released_at    = now()
Encounter                    → CLOSED
Patient.active               UNCHANGED
```

`patient.active` remains an admin-only flag for deceased or formally transferred-out patients. It is never a ward action.

### ✗2 The nurse creates surgery orders and sets prices

**Described:** `POST /patients/{assignmentId}/surgery` with `procedureName`, `surgeryType`, `urgency`, `anesthesiaType`, `price`, `consentObtained`.

**Conflicts with:** §1's own principle, and the price-control rule applied to the doctor and pharmacist modules.

Price appears in three places a nurse fills: surgery order, pre/post-op item, and lab item. Price resolves from the catalogue against the payer's price list — never from the person filling a clinical form. This is the same hole as a receptionist typing prices, with larger numbers.

**Resolution:**

| Action | Owner |
|---|---|
| Create surgery order (procedure, type, urgency, anaesthesia) | Doctor — `SCHEDULE_SURGERY` disposition |
| Schedule into a theatre slot, assign nurses | Nurse or theatre coordinator |
| Document the surgical episode | Nurse — §8 |
| Set any price | Nobody. Catalogue + price list |

Documenting the episode is legitimately nursing work. Ordering the surgery is not.

### ✗3 Changing ward voids an issued invoice

**Described:** "Changing ward voids prior ISSUED/DRAFT invoice and creates a new 1-day-advance ward invoice."

**Conflicts with:** invoice cancellation requires a reason and admin approval (receptionist §7.6); a clinical action must not silently perform an unaudited financial one.

It also mis-bills. A patient who spends four days in a general ward then transfers to a private room should pay four days at the general rate plus the remainder at the new one. Voiding and reissuing at the new rate charges the entire stay at one rate.

**Resolution — nightly accrual per bed-day:**

```
WardChargeAccrual(
  id, admission_id, bed_assignment_id, ward_id,
  charge_date, daily_rate_minor, invoice_line_id
)
```

A nightly job posts one accrual row per occupied bed-day at the rate applying that night. Transfer closes one `BedAssignment` and opens another; no invoice is voided, nothing is reissued, and the bill reflects where the patient actually slept.

⚠ This also replaces the "1-day-advance" model, which conflicts with the inpatient deposit design (doctor v0.3 §11) — inpatients accrue against a deposit rather than being invoiced in advance.

---

## 3. Navigation

```
Dashboard              ● 3 critical
Ward Patients
Surgery Queue
Shift Handover            ⚠ new — §7
My History
Ward Bills                read-only
Surgery Bills             read-only
────────────────────
Triage                    NURSE role only
Profile
```

Order as described, with handover added.

⚠ **Scope the ward patient list.** `hasAnyRole("WARD_NURSE","NURSE")` grants route access but says nothing about *which* ward. A nurse on Ward B should not see Ward C's patients. Use the permission-plus-scope model:

```
RoleAssignment(user, WARD_NURSE, scope_type=WARD, scope_id=ward_b)
```

---

## 4. Vitals

### 4.1 One table, not three

⚠ Ward vitals sit in `domain/ward/VitalSigns`, triage vitals elsewhere, and post-op vitals as separate fields on the surgery record. Three homes for the same measurement means no single query answers "show me this patient's observations", and a doctor cannot see a trend across the visit.

```
VitalSigns(
  id, patient_id, encounter_id,
  context,              -- TRIAGE | CONSULTATION | WARD_ROUNDS | POST_OP
  context_ref_id,       -- admission_id, surgery_id, etc.
  temperature_c, systolic_mmhg, diastolic_mmhg, pulse_bpm,
  respiratory_rate, spo2_pct, avpu, blood_glucose_mmol,
  pain_score, weight_kg, height_cm,
  intake_ml, output_ml,          -- ⚠ see §4.4
  notes, has_alerts, alert_details,
  recorded_by, recorded_at
)
```

Fields as described, plus `avpu` — consciousness level is already collected post-op under a different name, and it is the strongest single predictor of deterioration.

### 4.2 Alert thresholds are data, not code

⚠ BP >160/<90, pulse >120/<50, SpO2 <92, pain ≥8 are hardcoded and adult-only. A neonate's normal respiratory rate is 40–60/min and would breach any adult bound.

Use the same `VitalReferenceRange` table triage needs — age band, sex, four bounds per vital, `effective_from`. Clinical staff tune them without a deploy.

### 4.3 An alert must reach someone

⚠ `hasAlerts` is set and nothing follows. A flag nobody is told about is a record, not an alert.

```
vital breaches an alert bound
   → nurse sees it immediately on save
   → covering doctor notified, acknowledgement tracked
   → unacknowledged after N minutes → escalate to CLINICAL_LEAD
   → patient's ward status auto-suggested as CRITICAL (nurse confirms)
```

Same mechanism as critical lab results. Deterioration on a ward and a critical potassium are the same class of problem.

`?Q1` — escalation window for ward vitals. Probably shorter than the lab's.

### 4.4 Intake and output

⚠ Absent from the described workflow and standard for inpatients, particularly anyone critical, post-operative, or on IV fluids. Fluid balance is a routine nursing chart and its absence will be noticed on day one.

---

## 5. Medication Administration Record

### 5.1 Generate the schedule — do not type it

⚠ **The most important change in this document.**

As described, the nurse types `medicationName`, `scheduledTime`, `doseGiven`, and `route` freehand. Two consequences:

**Wrong-drug risk.** A typed medication name can differ from what was prescribed, and nothing catches the discrepancy.

**Missed doses are invisible.** If the 02:00 dose is forgotten, no row is created — so there is no evidence anything was missed. The MAR shows the doses that were given and looks complete. That is the exact failure a MAR exists to prevent.

**Schedule-driven instead:**

```
Doctor prescribes: Ceftriaxone 1g IV, TDS, 5 days
   → system generates 15 MedicationAdministration slots with due times
   → nurse marks each slot
   → drug, dose, route are READ-ONLY, inherited from the prescription
```

```
14:00  Ceftriaxone 1g IV     ✓ given          N. Kimaro 14:05
20:00  Ceftriaxone 1g IV     ✓ given          P. Shayo  20:10
02:00  Ceftriaxone 1g IV     ⚠ NOT RECORDED — 4h overdue
08:00  Ceftriaxone 1g IV     ○ due in 40 min
```

A missed dose is a visible empty slot that raises an alert, not an absent row.

```
MedicationAdministration(
  id, admission_id, ward_prescription_id,
  scheduled_at,                    -- system-generated
  status,                          -- DUE | GIVEN | REFUSED | OMITTED | HELD
  administered_at, administered_by,
  dose_given, batch_id,            -- ⚠ §5.2
  witness_user_id,                 -- ⚠ §5.3
  omission_reason, omission_notes, remarks
)
```

`medicationName` and `route` are dropped as stored fields — they are properties of the prescription and duplicating them invites divergence.

### 5.2 Administration must touch the stock ledger

⚠ **Described:** giving a dose "deducts `quantityPerDose` from `dispensedQuantity`" on the prescription.

That counter is not the inventory ledger. Consequences: no batch traceability, so a recall cannot identify who received the affected lot; no `StockMovement` row, so ward stock cannot be reconciled; and probable double-counting, since pharmacy already decremented when it issued to the ward.

**Resolution — transfer then administer:**

```
Pharmacy issues to the ward sub-store
    → StockMovement −qty from PHARMACY_MAIN
    → StockMovement +qty to WARD_B_STORE
    (same pattern as the lab reagent release)

Nurse administers a dose
    → StockMovement −qty from WARD_B_STORE, batch recorded
    → MedicationAdministration.batch_id set
```

The batch on the administration record is what makes ward-level recall traceability possible.

⚠ This depends on `?Q8` from the pharmacist spec, still open: do ward sub-stores hold stock, or does the ward draw per-dose from the main pharmacy? The sub-store model is the one that works at 02:00.

### 5.3 Two-person check for high-risk medicines

⚠ Absent. Standard practice for a defined class — IV drugs, insulin, anticoagulants, paediatric doses, and anything the clinical lead adds.

```
Item.requires_administration_witness = true
   → witness_user_id required, CHECK (witness_user_id != administered_by)
```

Same permission-not-role pattern as lab verification, and the same tiered approach so routine oral medicines are unaffected.

### 5.4 Omission reasons

The described `MedicationSkipReason` enum is right. Ensure it distinguishes states that mean different things clinically:

```
REFUSED_BY_PATIENT     NIL_BY_MOUTH        PATIENT_ABSENT
DRUG_UNAVAILABLE       CLINICAL_HOLD       VOMITED
IV_ACCESS_LOST         DUPLICATE_DOSE
```

⚠ `DRUG_UNAVAILABLE` should notify pharmacy automatically — it means a ward stockout, and nobody else is going to report it.

---

## 6. Wound care

Fields as described — appearance, dressing changed, dressing type, signs of infection, infection description, remarks. No changes needed.

⚠ One addition: `signsOfInfection = true` should notify the covering doctor. A wound infection noticed by a nurse at 22:00 that waits for the morning round is a preventable escalation.

⚠ Photographs would be valuable for wound progression, stored in the object store with the usual consent considerations. `?Q2` — in scope?

---

## 7. Shift handover

⚠ **Absent entirely, and the highest-risk moment in ward nursing.** Every information loss between shifts happens at this point.

```
ShiftHandover(
  id, admission_id, ward_id,
  shift_from, shift_to, handover_at,
  outgoing_nurse_id, incoming_nurse_id, acknowledged_at,
  situation, background, assessment, recommendation,
  outstanding_tasks jsonb
)
```

Structured as SBAR — situation, background, assessment, recommendation — which is the standard nurses will already know.

Rules:

- Outgoing nurse records; incoming nurse **acknowledges**. An unacknowledged handover is flagged
- Pre-populated with what the system already knows: alerting vitals, overdue MAR slots, pending lab results, doctor's orders added this shift
- Outstanding tasks carry forward until completed or explicitly closed

The pre-population is most of the value. A nurse writing a handover from memory at the end of a twelve-hour shift misses things the system already knows.

---

## 8. Bed and ward assignment

### 8.1 Beds are rows, not free text

⚠ `bedNumber` as a text field means nothing prevents two patients being assigned bed 4.

```
Ward(id, name, department_id, daily_rate_minor, gender_restriction, bed_count)
Bed(id, ward_id, label, bed_type, status)
BedAssignment(id, admission_id, bed_id, assigned_at, released_at)
    UNIQUE (bed_id) WHERE released_at IS NULL
```

The partial unique index makes the database reject a double assignment. A read-then-write check in application code loses the race between two nurses.

### 8.2 Nurse assignment across shifts

⚠ `assignedNurse` as a single field on the assignment does not survive a multi-day admission across three shifts a day. Care responsibility comes from the roster:

```
NursingAssignment(id, admission_id, nurse_id, shift, date, assigned_by)
```

`WardPatientAssignment.assignedNurse` becomes the *currently responsible* nurse, derived from the roster rather than set manually.

### 8.3 Transfer

Closes one `BedAssignment`, opens another, and closes one `WardChargeAccrual` period and opens another per ✗3. No invoice is touched.

⚠ Transfer between wards should notify the receiving ward's nurses, and the handover record (§7) should transfer with the patient.

---

## 9. Status

`ADMITTED | STABLE | CRITICAL | DISCHARGED | TRANSFERRED`, as described.

⚠ Two changes:

**`DISCHARGED` does not deactivate the patient** (✗1).

**`CRITICAL` should be auto-suggested from alerting vitals** (§4.3), with the nurse confirming. A patient whose SpO2 has been 88 for an hour while the status still reads `STABLE` is the gap between what the chart says and what is true.

⚠ Discharge is a **doctor's decision**, not a nurse's status update. The nurse records that the discharge happened; she does not decide it. If the described flow lets a nurse set `DISCHARGED` directly, that needs a `discharge_ordered_by` prerequisite.

⚠ Per the locked rule, discharge is **never blocked by an unpaid balance**. The balance becomes a receivable.

---

## 10. Surgery — episode documentation

Retained as nursing work, with ordering removed (✗2).

| Nurse does | Notes |
|---|---|
| Theatre scheduling and nurse assignment | |
| Intraoperative record | ⚠ see below |
| Pre/post-op item administration | Prices from catalogue, not entered |
| Post-op observations | → `VitalSigns` with `context = POST_OP` (§4.1) |
| Consent document download | |
| Send for payment | |

⚠ **The intraoperative record needs surgeon attestation.** Blood loss, complications, and the operative narrative are the surgeon's clinical account even when a circulating nurse types them. Recording complications without the surgeon signing off is a medico-legal exposure.

```
IntraoperativeRecord(..., recorded_by, surgeon_attested_by, attested_at)
```

Unattested records appear on the surgeon's queue.

⚠ Post-op observations write to `VitalSigns`, not to a fourth set of columns. `consciousnessLevel` maps to `avpu`; `bloodPressure` as a single string splits into systolic and diastolic integers.

Surgery remains deferred as a module — this section records what changes when it is picked up.

---

## 11. History and billing

**My History** — read-only assignment history for the logged-in nurse, as described.

**Ward Bills / Surgery Bills** — read-only. The nurse views but does not collect payment. Correct, and consistent with cashiering staying with reception.

⚠ Add an **overdue MAR** and **alerting vitals** view scoped to the nurse's ward — those are the two things she needs at a glance and neither is currently a screen.

---

## 12. Open

| | Question |
|---|---|
| `?Q1` | Escalation window for alerting ward vitals? |
| `?Q2` | Wound photographs in scope? |
| `?Q3` | Ward sub-store model — confirm against pharmacist `?Q8` |
| `?Q4` | Which medicines require an administration witness (§5.3)? |
| `?Q5` | Does a plain `NURSE` see all wards or only assigned ones? |
| `?Q6` | Nursing care plans and nursing assessments — in scope, or vitals and MAR only for v1? |
| `?Q7` | Who orders discharge — confirm it is doctor-only with the nurse recording execution |
| `?Q8` | Ward daily rate — per ward as described, or per bed type within a ward? |
| `?Q9` | Are ward rounds a recorded event, or implicit in the notes? |
