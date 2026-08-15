# HMS — Pharmacist Module Specification v1.0 (consolidated)

**Role:** `PHARMACIST`
**Consolidates:** `hms-pharmacist-module.md` v0.1 · `hms-pharmacist-module-v0.2.md` · `hms-controlled-medicines-policy.md` · `hms-controlled-medicines-tanzania-reference.md`
**Depends on:** `pharmacy`, `inventory`, `orders`, `billing`, `clinical-reference`

---

## 1. Decisions register

Everything settled across the discussion, in one place.

| | Decision | Status |
|---|---|---|
| D1 | No medicine is dispensed without a doctor's prescription. No pharmacist-initiated dispensing, no counter sales in v1 | Confirmed |
| D2 | The billing gate is enforced **server-side** on the dispense endpoint, not by queue membership | Confirmed |
| D3 | `batch_number` is mandatory on restock | Confirmed |
| D4 | Dispensing uses **repeatable batch lines** — one dispense may draw from several batches | Confirmed |
| D5 | Batch selection is **FEFO-suggested**, overridable with a reason. Expired batches are hard-blocked | Confirmed |
| D6 | Cost lives on the batch (`unit_cost`); selling price lives on the price list. Not one `price` field on the item | Confirmed |
| D7 | Out-of-stock and substitution flows are required | Confirmed |
| D8 | Controlled medicines fail closed — excluded from catalogue and dispensing until register, authorisation, reconciliation, witness, and audit exist | Confirmed |
| D9 | `UNCLASSIFIED` items are treated as **not controlled** (dispensable) | Confirmed — amended from the original fail-closed default |
| D10 | `Item.regulatory_schedule` carries the full TMDA category, not a controlled boolean | Confirmed |

---

## 2. Permissions

```
Granted:
  PRESCRIPTION_QUEUE_READ        DISPENSE_CREATE
  DISPENSE_HISTORY_READ          PATIENT_DISPENSE_LOOKUP
  ALLERGY_READ                   PATIENT_READ_DEMOGRAPHIC
  STOCK_READ                     STOCK_RESTOCK
  STOCK_TRANSFER_RELEASE         LAB_STOCK_REQUEST_REVIEW
  PHARMACY_ITEM_PROPOSE          PHARMACY_ITEM_BULK_UPLOAD
  SUBSTITUTION_PROPOSE           PRESCRIPTION_MARK_UNAVAILABLE

Withheld:
  PRICE_LIST_MANAGE              admin / accountant
  PHARMACY_ITEM_APPROVE          admin
  ITEM_CLASSIFY                  pharmacist-in-charge / CLINICAL_LEAD
  PAYMENT_CREATE                 no cashiering at the pharmacy counter
  PAYMENT_REFUND                 accountant
  STOCK_WRITE_OFF_APPROVE        accountant / admin
  PRESCRIPTION_CREATE            doctors only — D1
  CLINICAL_NOTE_READ             no narrative access
```

---

## 3. Navigation

```
Dashboard              ● 12
History
Stock                     3
Pharmacy Items
Lab Stock Requests        2
────────────────────
Profile
```

Order as specified. Badges: dispense queue depth · combined low-stock and expiry alerts · pending lab requests.

---

## 4. Dashboard

Action queues only, no data entry.

| Queue | Contents |
|---|---|
| **Ready to dispense** | Outpatient prescriptions where every line satisfies the gate |
| **Partially payable** | Some lines satisfied, others not — line-level gating |
| **Awaiting payment** | No line satisfied yet |
| **Emergency** | Active `PaymentBypass`; dispensable now, charges accrue to the emergency balance |
| **Inpatient / ward** | Deposit-backed; dispensable without prior payment |
| **Surgery items** | Deferred with the surgery module |

**Partially payable** exists because line-level gating means a prescription with two paid items and one unpaid belongs in neither of the other two queues. The pharmacist dispenses what is covered and leaves the rest.

`?Q1` — the original "prescription" vs "visit-prescription" split is unresolved. If it maps to outpatient vs ward, the table above covers it.

---

## 5. Dispensing

### 5.1 The gate

```
POST /dispenses
   → BillingGate.isSatisfied(prescription_line)     -- evaluated NOW
   → TRUE  : proceed
   → FALSE : 402 with the outstanding amount and a "send to cashier" action
```

Two rules that make this hold:

**Queue membership is not the control.** Filtering the UI by payment status is correct and useful, but the endpoint checks independently — a stale page, a direct call, or a race between payment and page load all defeat a UI-only check.

**Never cache the gate result from order time.** The patient very likely paid between the doctor writing the prescription and arriving at the counter.

```
isSatisfied(line) =
       line.patient_portion_settled
    OR verified_insurance_covers(line)
    OR active_bypass_exists_now
    OR line.authorization_id IS NOT NULL        -- durable bypass provenance
    OR admission_deposit_backed(line)
```

The fourth clause is why an order placed at 15:30 under an emergency bypass still dispenses at 16:05 after the bypass is revoked.

### 5.2 Form

| Field | Type | Notes |
|---|---|---|
| Batch lines | repeatable | §5.3 |
| `quantity_dispensed` | number | ≤ prescribed minus already dispensed |
| `dispensing_notes` | text | Optional |
| `counselling_notes` | textarea | Optional |

No price entered — billing is upstream.

**Displayed, not entered:**

- **All allergy records, every confirmation status, inline and unmissable.** Free-text allergies mean no automated matching, so the pharmacist is the second human check. Adjacent to the drug field, never in a collapsed panel or tab
- Patient weight, for paediatric verification
- Prescribed dose, route, frequency, duration — so the arithmetic can be sanity-checked
- Available batches with expiry and quantity on hand

### 5.3 Batch lines — repeatable, FEFO-suggested

```
DispenseLine(id, dispense_id, item_id, batch_id, quantity, unit_cost_snapshot)
```

Several lines per item, summing to `quantity_dispensed`. A single selector cannot fulfil 30 tablets when batch A holds 20 and batch B holds 10.

**FEFO pre-selected.** The system proposes batches in first-expiry-first-out order and fills the quantity automatically; override requires a reason. Free choice means fresh stock goes out while near-expiry stock sits and later expires.

`unit_cost_snapshot` is copied from the batch at dispense time — cost of goods for a past dispense must not move when a later delivery arrives at a different price.

### 5.4 Partial dispensing

```
dispensed_total < prescribed  → line PARTIALLY_DISPENSED, remainder owed
dispensed_total = prescribed  → line COMPLETED
dispensed_total > prescribed  → rejected, hard block
```

A partially dispensed line returns to the queue showing the remainder, so a patient collecting the balance later is not treated as a fresh request.

### 5.5 Stock decrement

One `StockMovement` row per dispense line, `qty_delta` negative. Balance derived and materialised — never a mutable quantity column.

**Concurrency:** `CHECK (quantity >= 0)` on the materialised balance table, inside the dispense transaction. Two pharmacists dispensing the same batch simultaneously must not both succeed past available quantity, and a read-then-write check in application code will not hold.

**Idempotency key required.** A double-tap on confirm must not decrement twice.

### 5.6 Dispensing label

```
Amina Hassan            HN-2026-04412
Amoxicillin 500mg capsule
TAKE ONE CAPSULE THREE TIMES A DAY
FOR 5 DAYS · AFTER FOOD
Dispensed 12 Jul 2026 · Exp 03/2027 · Batch AMX24071
```

A patient handed unlabelled tablets is a dosing error waiting to happen. The batch number on the label also makes a recall traceable to the individual.

### 5.7 Surgery items

"Mark dispensed", no fields — as specified. Deferred with the surgery module.

---

## 6. Out-of-stock and substitution

### 6.1 Substitution

```
Pharmacist proposes an equivalent (generic or therapeutic)
   → doctor notified, approves or rejects
   → on approval: original line cancelled, replacement line posted
   → price difference → top-up owed, or partial refund via accountant
   → substitution recorded against the original prescription
```

The prescriber's `substitution_allowed` flag governs whether a proposal is possible at all.

### 6.2 Unavailable

```
Pharmacist marks the line UNAVAILABLE with a reason
   → doctor notified to re-prescribe or cancel
   → already paid: refund the line through the accountant path
   → unpaid: charge voided automatically
```

Without these two flows the patient stands at the counter while someone makes phone calls.

---

## 7. Stock management

### 7.1 Views

Current stock · low-stock alerts below `reorder_level` · batches expiring within 90 days · **stock valuation** (`SUM(quantity × unit_cost)` by store, needed by the accountant and otherwise uncomputable).

### 7.2 Restock form

| Field | Required | Notes |
|---|---|---|
| Catalogue item | Yes | AJAX search |
| `batch_number` | **Yes** | D3 |
| Quantity | Yes | |
| Expiry date | Yes | Must be future-dated |
| `unit_cost` | **Yes** | D6 |
| Supplier | Yes | Managed list, not free text |
| Delivery note reference | **Yes** | Ties every stock increase to an external document |
| Notes | No | |

`batch_number` is what makes recall traceability possible — without it, a recall of lot `AMX24071` cannot identify which stock came from it or which patients received it. It also keeps the batch model coherent: keyed on expiry alone, two separate deliveries sharing an expiry month collapse into one row despite different costs.

The delivery note reference is the minimum control on stock creation. Without it a pharmacist can add 10,000 units of anything with no paper trail.

### 7.3 Expiry lifecycle

```
expiry − 90d  → expiry watchlist
expiry        → batch EXPIRED, hard-blocked from dispensing,
                excluded from available balance, moved to quarantine
              → write-off requires approval, recorded as a loss
```

An expired batch is undispensable by rule. Relying on a pharmacist noticing a date under time pressure is not a control.

---

## 8. Lab stock requests

| Action | Fields |
|---|---|
| Release | `released_quantity` (≤ requested), response notes optional |
| Reject | Response notes **required** |

**A release is a transfer, not a dispense.** Two movement rows — negative from the main pharmacy store, positive to the lab sub-store. A single decrement makes the reagent vanish from the ledger and leaves the lab's stock untracked.

Partial release is permitted; the remainder stays open or closes with a note. No billing gate — internal movement, not a patient-facing dispense.

The same request-and-release pattern will serve ward sub-stores when the ward module lands.

---

## 9. Pharmacy Items catalogue and pricing

### 9.1 Cost and price sit on different objects

```
Item              identity, name, classification, form, strength,
                  reorder_level, regulatory_schedule
StockBatch        batch_no, expiry, unit_cost           ← what the hospital paid
PriceListEntry    item, payer_type, amount, validity    ← what the patient pays
```

The single `price` field on the original form is the **cash selling price**, and it writes to the cash `PriceList`, not to the `Item` row. Stored on the item, per-payer pricing is impossible and every insured patient's invoice is wrong.

Without `unit_cost` on the batch there is no stock valuation and no margin reporting.

### 9.2 Proposals — new items and price changes both

```
PharmacyItemProposal(
  id, proposal_type,            -- NEW_ITEM | PRICE_CHANGE
  item_id,                      -- null for NEW_ITEM
  proposed_name, proposed_price_minor, proposed_classification,
  proposed_regulatory_schedule,
  current_price_minor,          -- snapshot at proposal, for PRICE_CHANGE
  justification,
  proposed_by, proposed_at,
  status, reviewed_by, reviewed_at, review_note
)
```

New items go through admin approval as well as price changes — otherwise a pharmacist can create an item at an arbitrary price and bypass price control from the other direction.

`justification` required on price changes; a price movement with no stated reason gives the approving admin nothing to approve on.

Category and type remain forced to `MEDICINES AND CONSUMABLES` / `PHARMACY`. Product code auto-generated.

### 9.3 Excel bulk upload

Single file field (.xlsx/.xls), with six safeguards — bulk import is the fastest way to corrupt a catalogue:

1. Template download with the exact expected columns
2. Dry-run validation first — parse, validate every row, show a row-level error report, commit nothing
3. All-or-nothing commit, or an explicit staged review. Never partial commit on error
4. Duplicate detection against existing items by name and code
5. Audit — uploader, timestamp, row count, source file retained
6. Price columns route through §9.2 rather than going live

---

## 10. Regulatory schedule and controlled medicines

### 10.1 The field

Per the TMDA Scheduling of Medicines Regulations 2025:

```
Item.regulatory_schedule ∈
  CONTROLLED              narcotics, limited-value psychotropics, precursors
  PRESCRIPTION_ONLY       includes psychotropics with moderate/high value
  PHARMACY_ONLY_BTC       behind the counter
  PHARMACY_ONLY_OTC       over the counter
  GENERAL_SALES           no prescription needed by law
  ADDO_PRESCRIPTION       not applicable to a hospital pharmacy
  RADIOPHARMACEUTICAL     authorised premises only
  UNCLASSIFIED            not yet reviewed
```

### 10.2 Behaviour

```
CONTROLLED       → BLOCKED (D8) — catalogue, pricing, prescribing,
                   dispensing, restock, transfer, bulk upload
UNCLASSIFIED     → treated as NOT CONTROLLED, dispensable (D9)
everything else  → dispensable, subject to D1 (prescription required)
```

The block is enforced in the repository layer, not only in controllers, so a future endpoint inherits it:

```
PharmacyRepository.assertDispensable(item):
    if item.regulatory_schedule == 'CONTROLLED':
        raise ControlledItemBlocked(item)
```

### 10.3 What is actually blocked

Narrower than first assumed. **Blocked:** morphine, pethidine, fentanyl and analogues, methadone, oxycodone, hydromorphone, full-strength codeine, opium, cannabis, and **ketamine** — which is on the narcotic list and is the item most likely to cause operational difficulty.

**Not blocked** — regulation 16(3) places psychotropics with moderate or high therapeutic value in Prescription Only: diazepam, midazolam, lorazepam, clonazepam, alprazolam, phenobarbital, pentobarbital, buprenorphine, pentazocine, zolpidem.

**Not blocked** — regulation 15(3) exempts low-strength narcotic preparations: codeine and dihydrocodeine at not more than 2.5% in undivided preparations, morphine at not more than 0.2%.

**Classification is per product, not per molecule.** A cough linctus at 0.2% codeine is not controlled; a 30mg codeine phosphate tablet is.

### 10.4 Prescriber-side visibility

A controlled item appears in the doctor's typeahead as visible and unselectable:

```
⚠ Morphine sulfate 10mg/ml injection
  Controlled medicine — prescribe on paper. Not available in this version.
```

Silent absence is worse: the doctor concludes the formulary is broken and stops trusting it for the drugs that *are* there.

### 10.5 Paper-record annotation

Since controlled drugs are given on paper, the medication history is otherwise silently incomplete — and a doctor reading a chart showing no opioids may prescribe accordingly.

```
ControlledMedicationNote(
  id, encounter_id, patient_id,
  drug_name_text, dose_text, route, administered_at,
  paper_record_reference, recorded_by, recorded_at
)
```

Appears in the medication history and on the prescribing screen, labelled `PAPER RECORD`. Creates no order, no charge, no stock movement, and satisfies no gate.

### 10.6 Detection

Because `UNCLASSIFIED` is now dispensable (D9), the safety net is a monthly report rather than a block:

- Items with `UNCLASSIFIED` or `PRESCRIPTION_ONLY` schedule whose name fuzzy-matches the First Schedule narcotic and limited-value psychotropic lists
- Match on INN trigram plus ATC prefix. **Exclude the moderate/high-value psychotropics** from the watchlist, or it fires constantly on diazepam and midazolam
- Every `CONTROLLED → PRESCRIPTION_ONLY` reclassification, permanently, with justification and both approvers

Routed to `CLINICAL_LEAD` and the pharmacist-in-charge.

### 10.7 v1 consequences accepted

| Consequence | Impact |
|---|---|
| Controlled stock off-system | Stock valuation incomplete by that amount |
| No electronic controlled prescription | Paper prescription and paper register, as today |
| No billing line for controlled drugs | Revenue recorded manually — flag to the accountant early, pharmacy reconciliation will not balance without it |
| Medication history relies on §10.5 | Incomplete if staff do not record the note |

---

## 11. History

Two tabs.

**My dispenses** — read-only list of what this pharmacist personally dispensed. Default view.

**Patient lookup** — search by patient, showing all dispenses regardless of recording pharmacist. A patient returning with a question, or a doctor querying what was given, cannot be answered from a self-scoped list, and a pharmacist has a legitimate care relationship with anyone whose prescription reaches the counter.

---

## 12. Data model summary

```
Item(id, code, name, classification, form, strength, unit,
     regulatory_schedule, classification_verified,
     reorder_level, active)

StockBatch(id, item_id, batch_number, expiry_date, unit_cost,
           supplier_id, delivery_note_ref, status, received_by, received_at)
     status ∈ ACTIVE | QUARANTINED | EXPIRED | WRITTEN_OFF

Store(id, name, type, department_id)

StockMovement(id, item_id, batch_id, store_id, qty_delta, movement_type,
              reference_type, reference_id, actor_id, occurred_at)
     movement_type ∈ RECEIPT | DISPENSE | TRANSFER_OUT | TRANSFER_IN
                   | ADJUSTMENT | WASTAGE | WRITE_OFF | RETURN

StockBalance(item_id, batch_id, store_id, quantity)   -- CHECK >= 0

Dispense(id, prescription_order_id, dispensed_by, dispensed_at,
         idempotency_key, counselling_notes, dispensing_notes)

DispenseLine(id, dispense_id, item_id, batch_id, quantity,
             unit_cost_snapshot)

SubstitutionProposal(id, prescription_line_id, proposed_item_id,
                     reason, proposed_by, status, reviewed_by, reviewed_at)

LabStockRequest(id, item_id, requested_quantity, requested_by,
                status, released_quantity, response_notes,
                reviewed_by, reviewed_at)

PharmacyItemProposal(...)          -- §9.2
ControlledMedicationNote(...)      -- §10.5
```

---

## 13. Events

**Emitted:** `MedicationDispensed` · `MedicationPartiallyDispensed` · `StockReceived` · `StockDepleted` · `BatchExpired` · `SubstitutionProposed` · `PrescriptionItemUnavailable` · `LabStockReleased` · `PharmacyItemProposed`

**Consumed:** `PrescriptionIssued` (queue) · `PaymentRecorded` / `InvoiceSettled` (requeue as payable) · `PaymentBypassGranted` (emergency queue) · `PatientAdmitted` (inpatient queue) · `PharmacyItemApproved` (activate item)

---

## 14. Open

| | Question |
|---|---|
| `?Q1` | What distinguishes "prescription" from "visit-prescription" in the dashboard queues? |
| `?Q2` | Same-molecule generic substitution — pharmacist proceeds and notifies, or waits for doctor approval? |
| `?Q3` | Second-person verification on high-value restocks? |
| `?Q4` | Drug interaction checking against the formulary — pharmacist side in v1? |
| `?Q5` | Returns of dispensed medicine — accepted, and does stock return to the batch or to quarantine? |
| `?Q6` | Prescription TTL — how many days before an undispensed prescription expires? |
| `?Q7` | Physical stock count and reconciliation in v1, or manual? |
| `?Q8` | Do ward sub-stores hold stock, or does the ward draw per-dose from the main pharmacy? |
| `?Q9` | Does the hospital use ketamine? It is the one blocked item likely to force the issue |
| `?Q10` | Who is the pharmacist-in-charge — a permission on `PHARMACIST`, or a scoped assignment like `CLINICAL_LEAD`? |
