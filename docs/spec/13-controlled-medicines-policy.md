# HMS — Controlled Medicines: v1 Exclusion Policy

**Version:** 0.1
**Status:** Confirmed — fail closed
**Scope:** cross-cutting — `pharmacy`, `inventory`, `orders`, `billing`, `clinical`, `admin`

---

## 1. The decision

> Controlled medicines are excluded from the ordinary pharmacy catalogue and dispensing workflow in v1 unless the controlled-drug register, authorization, reconciliation, witness, and audit mechanisms are implemented. The system fails closed rather than treating controlled medicines as ordinary stock.

This is a compliance and safety boundary, not a feature deferral. The consequence is that controlled medicines are managed on paper in v1 — which is the current state, so no capability is lost. What matters is that the system never provides an unregulated electronic path.

---

## 2. Classification — three states, not two

⚠ A boolean `is_controlled` cannot fail closed. An unset boolean reads as `false`, so any item added without classification becomes freely dispensable. That is the exact failure mode the policy exists to prevent.

```
Item.controlled_status ∈
    UNCLASSIFIED      -- default for every new item. BLOCKED.
    NOT_CONTROLLED    -- explicitly classified as ordinary. Dispensable.
    CONTROLLED        -- on the national schedule. BLOCKED in v1.
```

**`UNCLASSIFIED` behaves identically to `CONTROLLED`** — blocked everywhere. An item becomes usable only when someone with authority states it is not controlled.

```sql
controlled_status  NOT NULL  DEFAULT 'UNCLASSIFIED'
```

| Action | Permission |
|---|---|
| Classify an item `NOT_CONTROLLED` | `ITEM_CLASSIFY` — pharmacist-in-charge or `CLINICAL_LEAD` |
| Classify an item `CONTROLLED` | Same |
| Reclassify `CONTROLLED` → `NOT_CONTROLLED` | Requires a second approver and a written justification |

Every classification change is audited with actor, timestamp, previous value, and reason.

### 2.1 Seeding the schedule

⚠ The authoritative list must be **obtained from the regulator**, not inferred. The Tanzanian schedule for narcotics and psychotropics is published by the national medicines authority and the Ministry of Health; drugs at the boundary — tramadol, codeine combinations, some benzodiazepines — are scheduled differently across jurisdictions, and guessing produces either an illegal gap or a blocked essential medicine.

Seed procedure:

1. Obtain the current national controlled-substances schedule
2. Match against the formulary by INN and ATC code
3. Pharmacist-in-charge reviews and confirms each match
4. Everything unmatched stays `UNCLASSIFIED` until reviewed — not auto-set to `NOT_CONTROLLED`

Store the schedule reference so classifications are traceable to a source:

```
ControlledScheduleEntry(
  id, inn_name, atc_code, schedule_reference,
  source_document, source_date, added_by, added_at
)
```

---

## 3. Where the block bites

⚠ **One check is a single point of failure.** A guard at the dispense endpoint alone is defeated the first time someone adds a new endpoint, a background job, or a bulk import path. The block is enforced at every layer that can move a controlled item.

| # | Layer | Behaviour when `controlled_status != NOT_CONTROLLED` |
|---|---|---|
| 1 | **Catalogue creation / activation** | Item cannot be activated. Creation permitted so it can be classified, but it never becomes dispensable |
| 2 | **Price list** | No `PriceListEntry` may be created. No price, no charge, no gate to satisfy |
| 3 | **Prescriber typeahead** | Excluded from results, with an explicit message — §4 |
| 4 | **`POST /prescriptions`** | Rejected server-side even when a valid `item_id` is submitted directly |
| 5 | **`POST /dispenses`** | Rejected server-side |
| 6 | **`POST /stock-movements` / restock** | Cannot receive controlled stock into any store |
| 7 | **Excel bulk upload** | Rows rejected with a row-level error. **Never silently skipped** — a silent skip looks like success |
| 8 | **Lab / ward sub-store transfer** | Rejected |

### 3.1 Enforce below the endpoints

The check belongs in the repository or domain layer, not only in controllers:

```
PharmacyRepository.assertDispensable(item):
    if item.controlled_status != NOT_CONTROLLED:
        raise ControlledItemBlocked(item)
```

Called from every write path. A developer adding a new endpoint next quarter inherits the block without knowing it exists — which is the point.

Belt and braces at the database:

```sql
-- no price may exist for a non-classified item
ALTER TABLE price_list_entry ADD CONSTRAINT no_controlled_pricing
  CHECK (...)  -- enforced via trigger against item.controlled_status
```

---

## 4. What the prescriber sees

⚠ Silent absence is the worst outcome. A doctor types `morph`, gets nothing, concludes the formulary is broken, and stops trusting the picker — including for the drugs that *are* there.

```
Drug:  [ morph|                                        ]
       ┌──────────────────────────────────────────────┐
       │ ⚠ Morphine sulfate 10mg/ml injection         │
       │   Controlled medicine — prescribe on paper.   │
       │   Not available in this version.              │
       │                                               │
       │ ⚠ Morphine sulfate 10mg tablet                │
       │   Controlled medicine — prescribe on paper.   │
       └──────────────────────────────────────────────┘
       Record what was given → [ Add paper-record note ]
```

The item is **visible and unselectable**, with the reason stated and the alternative offered. The doctor learns the boundary once instead of rediscovering it every shift.

The same treatment applies in the pharmacist's catalogue view and the hospital catalogue screen.

---

## 5. The medication-history gap — and why it must be closed

⚠ **This is the one genuine safety cost of the policy, and it needs a deliberate answer.**

If controlled medicines are wholly absent from the system, a doctor reading a chart sees no opioids and may reasonably conclude none were given. Fail-closed on *dispensing* is correct. Fail-closed on *knowing the patient received pethidine four hours ago* is dangerous.

### 5.1 Paper-record annotation

A non-dispensable, non-chargeable structured note:

```
ControlledMedicationNote(
  id, encounter_id, patient_id,
  drug_name_text,               -- free text; no item_id, deliberately
  dose_text, route, administered_at,
  paper_record_reference,       -- register page / prescription serial
  recorded_by, recorded_at,
  amends_id
)
```

**What it does:** appears in the medication history, on the prescribing screen alongside allergy warnings, and in the discharge summary.

**What it does not do:** create an order, post a charge, move stock, satisfy a billing gate, or count as an electronic prescription. It is a record that something happened elsewhere.

⚠ It must be visually distinct from a dispensed medication — labelled `PAPER RECORD` — so nobody mistakes it for an electronic dispensing record or tries to reconcile it against stock.

`?Q1` — should recording this note be mandatory when a controlled drug is given, and if so, how is that enforced when the whole workflow is off-system?

---

## 6. The workaround, and detecting it

⚠ **The block will be worked around.** Under pressure, someone adds "Tramadol 50mg" to the catalogue classified `NOT_CONTROLLED` so it can be dispensed normally. Nothing in the interface reveals this, and it is precisely the unregulated path the policy forbids.

The mitigation is detection, not trust.

### 6.1 Misclassification report

Monthly, to `CLINICAL_LEAD` and the pharmacist-in-charge:

```
Items classified NOT_CONTROLLED whose name or ATC code
fuzzy-matches the controlled-substances watchlist

  Tramadol 50mg capsule        matched: tramadol        ⚠ review
  Diazepam 5mg tablet          matched: diazepam        ⚠ review
  Codeine phosphate syrup      matched: codeine         ⚠ review
```

Trigram match on INN plus exact match on ATC prefix. False positives are expected and cheap; a missed controlled drug is not.

### 6.2 Classification change log

Every `CONTROLLED → NOT_CONTROLLED` reclassification appears on the same report with its justification and both approvers, permanently. This is the transition that requires scrutiny; the reverse direction does not.

### 6.3 Anomaly signals

- Items activated within 24 hours of creation with no classification review
- Bulk uploads containing rows that were rejected for controlled status — repeated attempts indicate someone trying to get a drug in
- Stock movements for an item whose classification changed in the preceding 7 days

---

## 7. Lifting the block in v2

The exclusion ends when all five mechanisms named in the decision exist. Concretely:

| # | Mechanism | Definition of done |
|---|---|---|
| 1 | **Register** | Append-only per-item register with a running balance, reconcilable to a physical count, no editable balance field |
| 2 | **Authorization** | Prescriber authority verified against professional registration; controlled prescriptions carry the prescriber's registration number |
| 3 | **Reconciliation** | Daily physical count against register balance, variances recorded with explanation and sign-off, unresolved variance escalates |
| 4 | **Witness** | Two distinct users on every dispensing and every wastage event, `dispenser_id != witness_id` enforced at the database |
| 5 | **Audit** | Every movement including wastage and breakage carries actor, witness, reason, and timestamp. No bulk or Excel adjustment path exists |

⚠ All five, not a majority. A register without a witness requirement, or reconciliation without escalation, reproduces the gap in a different shape.

When they are in place, controlled items become dispensable through a **separate workflow** — not by relaxing the checks in §3. The ordinary dispensing path stays closed to them permanently.

---

## 8. Consequences to accept in v1

Stating these explicitly so nobody is surprised by them later:

| Consequence | Impact |
|---|---|
| Controlled stock is off-system | Stock valuation and pharmacy inventory reports are incomplete by that amount |
| No electronic prescription for controlled drugs | Paper prescription, paper register, as today |
| No billing line for controlled drugs | Revenue from them is recorded manually, outside the system |
| Medication history relies on §5 annotation | Incomplete if staff do not record the note |
| Interaction and allergy checking excludes them | The pharmacist's human check does not run for paper-dispensed items |

The third row is worth flagging to the accountant early — pharmacy revenue reconciliation in v1 will not balance against the physical dispensary unless controlled-drug income is accounted for separately.

---

## 9. Open

| | Question |
|---|---|
| `?Q1` | Is the §5 paper-record annotation mandatory, and how is that enforced off-system? |
| `?Q2` | Who is the pharmacist-in-charge — a permission on `PHARMACIST`, or a distinct scoped assignment like `CLINICAL_LEAD`? |
| `?Q3` | Which regulator publication is the authoritative schedule, and how often is it reissued? |
| `?Q4` | Do partially controlled combination products (low-dose codeine formulations) follow the schedule strictly, or does the hospital take a stricter internal line? |
| `?Q5` | Is v2 controlled-drug support scheduled, or is paper handling the long-term intent? Affects whether §7 is a roadmap item or a permanent boundary |
