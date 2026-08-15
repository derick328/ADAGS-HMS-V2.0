# HMS — Lab Technician Module Specification v0.1

**Role:** `LAB_TECHNICIAN`
**Routes:** `/labtech/**`
**Depends on:** `laboratory`, `orders`, `billing`, `inventory`, `clinical-reference`
**Notation:** `⚠` change from the described workflow · `?` open

---

## 1. Locked rules that apply

| | Rule | Source |
|---|---|---|
| L1 | The billing gate sits at **specimen collection**, not result release | Billing spec §8 |
| L2 | The gate is evaluated server-side, live, at the point of resource consumption | Billing spec §4, §5 |
| L3 | An active `PaymentBypass` satisfies the gate | Billing spec §1, §11 |
| L4 | Verified insurance coverage satisfies the gate | Billing spec §6, §7 |
| L5 | Gating is per line, not per invoice | Billing spec §10 |
| L6 | Inpatients accrue against a deposit | Doctor v0.3 R3 |
| L7 | **Critical results must reach clinicians regardless of financial status** | Billing spec §8 |
| L8 | The ordering doctor owns the result; escalation goes to `CLINICAL_LEAD` | Doctor v0.3 §8.2 |
| L9 | Clinical records are amended, never overwritten | Architecture §5.5 |

---

## 2. Navigation

```
Test Queue           ● 14
Lab Catalogue
Stock Requests          2
My History
────────────────────
Profile
```

Order as specified.

---

## 3. Test Queue

### 3.1 Queues

⚠ The described pair (queue + awaitingPayment) misses two categories that must be processable:

| Queue | Contents |
|---|---|
| **Ready to process** | Gate satisfied, awaiting accept |
| **In progress** | Accepted, result not yet submitted |
| **Awaiting payment** | Gate not satisfied — visible but not actionable |
| **Emergency** | ⚠ Active `PaymentBypass` — processable now, charges accrue (L3) |
| **Inpatient / ward** | ⚠ Deposit-backed — processable without prior payment (L6) |
| **Overdue** | ⚠ Past expected turnaround — see §3.3 |

### 3.2 Ordering

```sql
ORDER BY (encounter.type = 'EMERGENCY') DESC,
         urgency ASC,              -- STAT, URGENT, ROUTINE
         gate_satisfied_at ASC
```

Urgency then time, as specified, with emergency first. `STAT` rows visually distinct — colour alone is insufficient, use an icon and position.

### 3.3 Turnaround time

⚠ Absent from the described workflow. The catalogue carries an expected TAT per test; a background job flags anything past it into **Overdue**.

Without this, a blood culture sits for five days and nobody notices until the doctor asks. The accept timestamp already exists; this just measures against it.

```
LabTest.expected_tat_hours
Overdue = now() - accepted_at > expected_tat_hours AND status != COMPLETED
```

---

## 4. Accept sample

### 4.1 Accession number and barcode

⚠ **Missing from the described workflow and the most valuable addition here.**

Nothing currently generates a specimen identifier, which means tubes on a bench are identified by handwritten patient names. That is the mechanism behind most catastrophic laboratory errors — wrong-patient results.

At accept:

```
1. Generate accession_no from a locked sequence, gapless per facility per year
2. Print a barcode label for the specimen container
3. At result entry, the tech SCANS the accession rather than selecting a patient
```

One sequence table, one label printer, and the most expensive error class in a lab largely disappears.

```
Specimen(id, lab_order_id, accession_no, sample_type,
         collected_by, collected_at, received_by, received_at,
         quality, quality_note, status, rejection_reason)
```

### 4.2 Quality assessment happens at accept, not at result entry

⚠ The described workflow has legacy capturing quality at accept and V26 capturing it at result entry. The second is the wrong order.

Sample quality decides *whether to process*. A haemolysed sample must not be run — the potassium reads falsely high and someone may treat it. Assessing quality after processing means reagents, analyser time, and technician time already spent on a specimen that should have been rejected.

**Quality assessment belongs at accept, in both pipelines.**

| Field | Type | Notes |
|---|---|---|
| `sample_type` | select | ⚠ from the catalogue's expected specimen type, not free text |
| `quality` | enum | `ADEQUATE` `HAEMOLYSED` `CLOTTED` `INSUFFICIENT_VOLUME` `WRONG_CONTAINER` `MISLABELLED` `DELAYED_TRANSPORT` |
| `quality_note` | text | Required when quality ≠ `ADEQUATE` |

### 4.3 Rejection flow

⚠ The described workflow records `HAEMOLYSED` but nothing happens as a result.

```
quality = ADEQUATE     → status IN_PROGRESS, received_at stamped
quality ≠ ADEQUATE     → status REJECTED
                       → reason recorded
                       → ordering doctor notified with the reason
                       → recollection requested
```

⚠ **Recollection is not re-charged.** The collection failed, not the patient. The original charge stands and covers the repeat.

`?Q1` — confirm that billing rule. The alternative (charging again) will be experienced as the patient paying twice for one test because the hospital dropped a tube.

`MISLABELLED` deserves separate treatment: it is a patient-safety incident, not just a quality issue. Route it to `CLINICAL_LEAD` as well as the ordering doctor.

---

## 5. Result entry

### 5.1 Generate the form from the catalogue

⚠ **The most significant change in this document.**

In the described workflow the technician types the parameter name, unit, and reference range for every result. Three consequences:

**No trending.** `Hb`, `HB`, `Haemoglobin`, and `haemoglobin` are four different parameters. A doctor managing anaemia cannot plot haemoglobin over six months — which is precisely what they want to see.

**Inconsistent reference ranges.** A typed range varies between technicians and shifts, so the same value reads normal on Monday and high on Tuesday.

**The flag becomes a human judgement when it should be arithmetic.** With ranges from the catalogue, H/L/N/C is computed. That is more reliable than a technician at 2am, and it is what makes critical alerting (§6) possible at all.

```
LabTest(id, code, name, specimen_type, expected_tat_hours,
        is_sensitive, container_type, active)

LabTestParameter(id, test_id, code, name, unit,
                 sort_order, method, loinc_code)

LabReferenceRange(id, parameter_id,
                  age_min_months, age_max_months, sex,
                  normal_low, normal_high,
                  critical_low, critical_high,
                  effective_from)
```

The result form is **generated** from `LabTestParameter` for that test. The technician enters values only.

Same pattern as the vitals ranges in triage and the same reasoning as the ICD-10 decision: coded where it matters, free text alongside for nuance.

### 5.2 Fields

**Per parameter — generated rows, tech fills the value:**

| Field | Source |
|---|---|
| Parameter name, unit, method, sort order | Catalogue, read-only |
| Reference range | Computed from patient age and sex, read-only |
| `result_value` | **Tech enters** |
| `flag` | **Computed** — `N` `H` `L` `C`. Override permitted with a reason |

**Per result — one set:**

| Field | Type |
|---|---|
| `methodology` | text |
| `reagents_used` | text — ⚠ see §5.5 |
| `findings` | text |
| `interpretation` | text |
| `reference_range_note` | text |
| `conclusion` | text |
| `machine_output` | file — ⚠ see §5.4 |

The V26 field set, retained. Legacy's free-text `result_value` summary is replaced by the generated parameters.

### 5.3 Implausible values

Two bounds per parameter, as with vitals: implausible rejects as a typo, abnormal flags and saves. A potassium of 70 is a decimal error, not a finding.

### 5.4 Machine output goes to object storage

⚠ The described workflow stores the PDF directly in the database. Binary blobs bloat Postgres, slow every backup, and complicate replication.

MinIO is already in the architecture. Store the object key in the database, the file in the object store, and serve it through a signed URL.

### 5.5 Reagent lot traceability

⚠ `reagents_used` as free text cannot answer "which results were produced with reagent lot 24-0817?" — which is the question when a lot is found faulty and results must be recalled.

Link to the lab sub-store batch instead:

```
LabResultReagent(result_id, stock_batch_id, quantity_used)
```

Free text remains as a supplementary note. This also gives you reagent consumption per test, which drives reorder levels.

---

## 6. Verification and release

### 6.1 Entry and verification must be separable

⚠ **The most serious gap in the described workflow.** The same technician enters the result and submits it, at which point it locks and clinicians act on it. No second check exists anywhere.

For a haemoglobin that is a bad day. For a blood group, a crossmatch, an HIV result, or a histopathology report it is the error class that kills people.

A tiered model, because a single technician on a night shift cannot verify their own work:

```
LabTest.is_sensitive = true
    → two-person release required
    → CHECK (entered_by != verified_by)  enforced at the database
    → covers blood group, crossmatch, HIV, histopathology, cultures,
      and anything the CLINICAL_LEAD adds

LabTest.is_sensitive = false
    → single-person release permitted
    → recorded as released_unverified
    → appears on a retrospective review queue
```

The sensitive list is data, editable by `CLINICAL_LEAD`. Safety where it matters, no night-shift deadlock.

```
LabResult(id, lab_order_id, specimen_id,
          entered_by, entered_at,
          verified_by, verified_at,
          released_unverified BOOLEAN,
          status,                 -- DRAFT | ENTERED | VERIFIED | COMPLETED | AMENDED
          amends_id, amendment_reason)
    CHECK (verified_by IS NULL OR verified_by != entered_by)
```

### 6.2 Amendment after release

⚠ Absent from the described workflow, which locks on submit with no correction path. Results do get corrected — wrong sample, transcription error, re-run.

```
Amendment creates a NEW result row with amends_id
Original remains readable
Reason required
⚠ Ordering doctor notified that a result they may have acted on has changed
```

That notification is safety-critical. A silently corrected result is worse than an uncorrected one, because the clinician has already acted on the first value.

---

## 7. Critical results

⚠ The described workflow ends at "doctors see it in their lab-results view". Passive visibility is insufficient — a potassium of 7.0 cannot wait for someone to open a screen.

The V26 `flag` field already carries `C`, so the data model supports this. What is missing is the behaviour:

```
any parameter flag = C on release
    → immediate push notification to the ordering doctor
    → acknowledgement required and tracked
    → unacknowledged after N minutes → escalate to CLINICAL_LEAD (L8)
    → acknowledgement, escalation, and timestamps recorded on the result
    → tech's screen shows acknowledgement status so they know it landed
```

Per L7, critical results are released regardless of payment status. There must be no code path where an unpaid balance withholds a life-threatening value.

```
CriticalResultNotification(
  id, result_id, parameter_id, value,
  notified_user_id, notified_at,
  acknowledged_at, acknowledged_by,
  escalated_at, escalated_to_user_id
)
```

`?Q2` — what is N? 15 minutes is typical for a STAT critical value.

---

## 8. The two pipelines

⚠ Legacy `LabRequest` and V26 `VisitLabRequest` feeding one queue creates a compounding cost: two models, two forms, two accept behaviours, and every downstream consumer — doctor's view, billing, history, printing, reporting — handling both shapes. Bugs get fixed twice and reporting cannot aggregate.

**Recommendation: an anti-corruption layer, not two UIs.**

```
LabRequest      ─┐
                 ├─→ LabOrderView (one shape) ─→ tech UI, doctor view,
VisitLabRequest ─┘                                billing, reporting
```

The technician should never see a different form depending on which pipeline produced the order. That is an internal detail leaking into a clinical workflow, and it is where mistakes come from.

`?Q3` — is legacy being retired? If yes, set a sunset date and stop writing to it now. If it is permanent, the adapter becomes load-bearing rather than transitional and needs owning accordingly.

---

## 9. My History and reporting

Read-only view of this technician's completed results across both pipelines, as specified, plus the printable report page.

⚠ Add a **patient result lookup** as a second tab — search by patient or accession, showing all results regardless of which technician produced them. A doctor querying a result, or a repeat sample needing comparison against the previous value, cannot be answered from a self-scoped list.

### 9.1 Printable report

Must carry: patient identity and hospital number, accession number, specimen type and collection time, every parameter with value, unit, reference range and flag, methodology, the entering and verifying technicians, release timestamp, and an amendment notice where applicable.

⚠ An amended result's printout must state that it supersedes an earlier version, with the date of the original. Otherwise two printouts of the same test circulate with different values and no way to tell which is current.

---

## 10. Lab Catalogue proposals

As specified, mirroring the pharmacist's flow.

| Field | Type |
|---|---|
| Product name | text |
| Classification | select, with custom entry when `OTHERS` |
| Price | decimal |
| Notes | text |

⚠ Two additions, because a test without them cannot be processed once approved:

- `specimen_type`, `container_type`, `expected_tat_hours`
- Parameter definitions per §5.1 — a test with no parameters generates an empty result form

⚠ `is_sensitive` is set by `CLINICAL_LEAD`, not by the proposing technician — it determines whether two-person verification applies, and that should not be self-assigned.

Price goes to admin for approval; the technician does not set prices. Same `justification` requirement as the pharmacy proposals.

---

## 11. Stock and reagent requests

As specified.

| Field | Rules |
|---|---|
| Stock item | Typeahead over pharmacy stock |
| `requested_quantity` | Minimum 1 |
| `request_notes` | Optional, max 500 chars |

Status: `PENDING → RELEASED | REJECTED`, handled pharmacist-side with released quantity and notes.

⚠ **On release, the lab must record receipt** — the transfer creates a positive movement into the lab sub-store, and the lab's own balance is then trackable. Without it the reagent leaves pharmacy's ledger and enters nothing, and §5.5's lot traceability has no batch to point at.

---

## 12. Permissions

```
Granted:
  LAB_QUEUE_READ              SPECIMEN_ACCEPT
  SPECIMEN_REJECT             LAB_RESULT_ENTER
  LAB_RESULT_VERIFY           -- subject to entered_by != verified_by
  LAB_RESULT_AMEND            LAB_REPORT_PRINT
  LAB_HISTORY_READ            PATIENT_RESULT_LOOKUP
  LAB_STOCK_REQUEST_CREATE    LAB_STOCK_RECEIVE
  LAB_ITEM_PROPOSE
  PATIENT_READ_DEMOGRAPHIC    -- name, age, sex; needed for reference ranges

Withheld:
  PRICE_LIST_MANAGE           admin / accountant
  LAB_ITEM_APPROVE            admin
  LAB_TEST_SENSITIVE_FLAG     CLINICAL_LEAD
  LAB_ORDER_CREATE            doctors only
  CLINICAL_NOTE_READ          no narrative access
  DIAGNOSIS_READ              ⚠ see below
```

⚠ `?Q4` — should the technician see the working diagnosis? It genuinely helps interpretation (a haemoglobin of 7 means something different in a known malaria case), but it widens clinical data access. A middle path is the clinical indication the doctor typed on the order, not the diagnosis itself.

---

## 13. Absent from v1 — worth naming

| Item | Note |
|---|---|
| **Quality control** | Daily controls, Levey-Jennings charts, out-of-control rules. Without QC records, results are not defensible to an inspector or in a dispute. Probably out of v1 scope, but it is a real gap |
| **Analyser interfacing** | Manual entry from a machine printout is a transcription-error source. Direct instrument capture eliminates it, but is a substantial build |
| **Blood bank / crossmatch** | Distinct workflow with distinct safety requirements. If transfusion happens, this needs its own module, not a lab test |
| **Send-out / referred tests** | Samples sent to an external lab — tracking, external result entry, external pricing |
| **Panic-value delta checks** | Flagging a result that has changed implausibly from the patient's previous value, which catches sample mix-ups |

---

## 14. Open

| | Question |
|---|---|
| `?Q1` | Recollection after a rejected sample — not re-charged, confirmed? |
| `?Q2` | Critical result escalation timeout — 15 minutes? |
| `?Q3` | Is the legacy pipeline being retired, and by when? |
| `?Q4` | Does the technician see the diagnosis, the clinical indication, or neither? |
| `?Q5` | Who collects specimens — the lab, a nurse, or triage? Determines where the gate actually bites (L1) |
| `?Q6` | Which tests are `is_sensitive` at launch? |
| `?Q7` | Is there a phlebotomy step and queue, or do patients arrive at the lab with the sample already drawn? |
| `?Q8` | Does the lab hold its own sub-store balance, or does it draw per-use from pharmacy? |
| `?Q9` | Blood bank in scope at all, or handled entirely outside the system? |
