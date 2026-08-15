# HMS — Procedure Reconciliation & Bed-Day Accrual

**Version:** 0.1 — extends `hms-billing-architecture-addendum.md`
**Resolves:** addendum §4.3 (procedure items), and specifies the ward bed-day rule

---

## 1. Booked appointments — confirmed

Closing the last open pricing question.

```
Booked appointment arrival
  → NEW encounter (not a resumption)
  → triage as normal
  → FULL consultation charge at the routed provider's rate
  → elevated queue priority
```

⚠ **No discount.** `CONSULT_FOLLOW_UP` is therefore not needed as a distinct price — a booked review is priced identically to a walk-in.

⚠ **Priority is the third sort key, below both clinical keys:**

```sql
ORDER BY (encounter.type = 'EMERGENCY') DESC,   -- IMMEDIATE always first
         triage_priority ASC,                   -- clinical acuity
         has_appointment DESC,                   -- booked beats walk-in
         gate_satisfied_at ASC
```

A booked patient skips walk-ins at the same acuity. A booked patient never skips a sicker one.

This is distinct from resuming for lab results (addendum §2.2), which stays free because it is the same encounter continuing.

---

## 2. Procedure items — consumption is discovered, not ordered

### 2.1 The correction

The earlier model treated `PREPAID` / `DEFERRED` as a choice someone makes. It is not. **Consumption is discovered during the procedure** — the surgeon opens what they need, and the count is not known until afterwards.

Deferral therefore follows from the *phase*, and nobody marks it:

| Phase | Known in advance? | Gate |
|---|---|---|
| **Pre-op** | Yes — prepared and listed | Normal gate applies before use |
| **Intra-op** | No — consumed during, counted after | **Necessarily deferred** |
| **Post-op** | Yes — prescribed after | Normal gate applies |

⚠ Intra-op items cannot be gated because nothing knows they will be used until they have been. The system infers `DEFERRED` from `phase = INTRA_OP`; it is not a permission anyone holds.

### 2.2 Prepared versus used

```
ProcedureItem(
  id, procedure_id,
  item_type,              -- PHARMACY | LAB | CONSUMABLE
  item_id,                -- catalogue reference; never a typed name or price
  phase,                  -- PRE_OP | INTRA_OP | POST_OP
  quantity_prepared,
  quantity_used,
  quantity_returned,
  quantity_wasted, wastage_reason,
  batch_id,
  prepared_by, prepared_at,
  reconciled_by, reconciled_at
)
```

⚠ **Charges are built from `quantity_used`, not `quantity_prepared`.** A nurse prepares five sutures, three are used, two go back in the drawer — the patient pays for three.

⚠ **Returned items need a stock movement**, or they are lost from the ledger. A return posts `+quantity_returned` back to the source, against the same batch.

⚠ Prepared by the doctor or the ward nurse, as specified. Priced from the catalogue — never typed on the form (ward nurse spec ✗2).

### 2.3 Reconciliation gates completion, which gates payment

```
Reconcile  →  Mark complete  →  Send for payment
```

The procedure cannot be marked complete until reconciliation balances:

```
quantity_prepared == quantity_used
                  + quantity_returned
                  + quantity_wasted

wastage requires a reason
```

⚠ Without this step, prepared-but-unaccounted items sit forever and stock drifts silently. The reconciliation screen shows every prepared item with three fields to fill.

⚠ **Who reconciles and who attests.** The circulating nurse counts; the surgeon attests — the same pattern as the intraoperative record needing surgeon sign-off. `reconciled_by` and a separate `attested_by`.

```
Procedure status:
  SCHEDULED → IN_PROGRESS → PENDING_RECONCILIATION
            → COMPLETE → SENT_FOR_PAYMENT
```

`PENDING_RECONCILIATION` appears on a worklist so it cannot be silently skipped.

### 2.4 Post-procedure disposition

```
Procedure COMPLETE → disposition:

  RECOVERY_THEN_DISCHARGE   same-day; encounter closes normally
  ADMIT_TO_WARD             encounter → ADMITTED, bed assigned,
                            bed-day accrual begins (§3)
  TRANSFER_OUT              external referral, terminal
  DECEASED                  terminal
```

If admitted, the deposit model takes over from that point.

`?Q1` — is a ward deposit collected in advance at scheduling for elective procedures where post-op admission is expected? Advance collection is more realistic than at 02:00 after theatre.

---

## 3. Bed-day accrual — the 10:00 rule (confirmed)

### 3.1 The algorithm

```
if total_stay < 4 hours:
        days = 0                          -- free

else:
        effective_start = admission_time
        if 06:00 <= admission_time <= 09:59:
                effective_start = 10:00 same day

        days = boundaries_passed(effective_start, discharge_time) + 1
```

Where `boundaries_passed` counts 10:00 timestamps strictly **after** `effective_start` and strictly **before** `discharge_time`. A discharge at exactly 10:00 has not passed that boundary.

The `+ 1` is the admission day itself.

### 3.2 Verified cases

| Stay | Duration | Effective start | Boundaries passed | Days |
|---|---|---|---|---|
| Mon 23:00 → Tue 08:00 | 9h | Mon 23:00 | 0 | 1 |
| Mon 23:00 → Tue 11:00 | 12h | Mon 23:00 | 1 | 2 |
| Mon 06:01 → Tue 10:00 | 28h | Mon 10:00 | 0 | 1 |
| Mon 06:00 → Mon 11:00 | 5h | Mon 10:00 | 0 | 1 |
| Mon 11:00 → Mon 14:00 | 3h | — | — | 0 (free) |
| Mon 14:00 → Wed 09:00 | 43h | Mon 14:00 | 1 | 2 |
| Mon 06:30 → Mon 09:30 | 3h | — | — | 0 (free) |

### 3.3 Properties worth noting

**The 4-hour grace and the morning window never conflict.** The window is 3h59m wide, so any admission inside it discharged before 10:00 is automatically under 4 hours and free. No special-case handling required.

**The grace is measured from actual admission, not effective start.** A 06:00 → 11:00 stay is 5 hours actual and therefore bills, despite being only 1 hour past its effective start.

**The grace expires.** It applies only to the initial period. A patient on day 3 discharged two hours after the 10:00 boundary pays for day 3 — they are long past the 4-hour window.

**Sharp boundaries are intentional.** `Tue 10:00` bills 1 day; `Tue 10:01` bills 2. Likewise a 05:59 admission counts from admission while 06:00 counts from 10:00. Confirmed as intended, and consistent with hotel practice.

### 3.4 Ward transfer mid-day — private wins the day

```
Any part of a bed-day spent in private  →  that day bills at the PRIVATE rate
```

The 4-hour discard rule only ever removes a **general** leg. A private leg is never discarded — once the patient is in private, standard 10:00 boundary counting applies at the private rate.

| Same-day movement | Day bills at |
|---|---|
| General 07:00 → private 10:30 | Private |
| Private 08:00 → general 15:00 | Private |
| General throughout | General |

From the next boundary onward, the ward actually occupied governs:

```
Mon 08:00  admitted private
Mon 15:00  transferred to general
Thu 09:00  discharged

Mon 10:00 → Tue 10:00    private touched   → PRIVATE rate
Tue 10:00 → Wed 10:00    general           → general rate
Wed 10:00 → Thu 09:00    general           → general rate
```

⚠ **Three-ward movement within one day is out of scope** — patients are not moved between three wards, so no "all legs short" case exists.

**Implementation:** for each accrual boundary, take the set of `BedAssignment` rows overlapping that bed-day and charge the highest ward rate among them. That single rule produces every row in the table above without special-casing direction.

### 3.5 The accrual job

```
Runs daily at 10:00 (facility local time), not midnight

For each occupied BedAssignment:
    resolve the ward rate in effect now
    post one WardChargeAccrual row
    → invoice line on the encounter's invoice
    → deducts from the DepositAccount if one exists
```

```
WardChargeAccrual(
  id, admission_id, bed_assignment_id, ward_id,
  accrual_boundary_at,          -- the 10:00 timestamp
  daily_rate_minor,
  invoice_line_id,
  deposit_transaction_id        -- null if no deposit
)
```

⚠ **Idempotency:** `UNIQUE (bed_assignment_id, accrual_boundary_at)`. A job that reruns after a failure must not double-bill the night.

⚠ **The job must be catch-up capable.** If the server is down at 10:00, the next run posts every missed boundary rather than skipping them. Iterate boundaries from the last posted one to now.

⚠ **No advance invoicing.** This replaces the "1-day-advance ward invoice" from the original ward nurse workflow — accrual is retrospective, one row per night actually occupied, and deposits cover it as it accrues.

### 3.6 Discharge — who orders it and when billing stops

**Who may order discharge:**

```
Doctor                                          → yes
Nurse assigned to that patient by that doctor   → yes
Any other ward nurse                            → no
```

⚠ `DISCHARGE_ORDER` is **scoped to the assignment**, not a blanket ward-nurse permission. A nurse may discharge her own doctor's patients, not every patient on the ward.

**Two timestamps, not one:**

```
Admission.discharge_ordered_at     → stops billing (§3.6)
Admission.discharge_ordered_by
BedAssignment.released_at          → frees the bed (actual departure)
```

⚠ Collapsing these into one field means a bed shows free while someone is still in it, and the next admission is assigned into an occupied bed.

### 3.7 Billing stop time — the 4-hour departure grace

```
gap = actually_departed_at − discharge_ordered_at

gap <= 4 hours   →  billing_stop = discharge_ordered_at
gap  > 4 hours   →  billing_stop = actually_departed_at
```

`billing_stop` is then used as the discharge time in the §3.1 algorithm.

| Cleared | Left | Gap | Billing stops | Boundary at 10:00 |
|---|---|---|---|---|
| 09:00 | 11:30 | 2.5h | 09:00 | not passed → day not charged |
| 09:00 | 14:00 | 5h | 14:00 | passed → day charged |
| 11:00 | 12:00 | 1h | 11:00 | already passed → day charged |

Short delays caused by the hospital — pharmacy queue, transport, paperwork — cost the patient nothing. Genuine bed-blocking is billed.

⚠ This **removes the need for an accountant waiver queue.** The rule decides automatically; no manual review, no flagged exceptions.

### 3.8 Discharge delay monitoring

⚠ The ordered-to-departure gap is worth measuring even though most of it is unbilled. A ward where the average gap runs to six hours has a bed permanently blocked by process, not by patients.

```
Monthly report: mean and 90th-percentile ordered-to-departure gap,
                by ward and by cause where recorded
```

The purpose is identifying whether pharmacy, transport, or paperwork is the bottleneck — not recovering revenue.

### 3.9 Reversing a discharge order

⚠ A patient cleared at 09:00 who deteriorates at 10:30 and stays another two days needs a path back.

```
Cancel discharge order → reason required
                       → discharge_ordered_at cleared
                       → billing resumes, continuous from admission
                       → recorded in the encounter event log
```

Without this, staff will delete the record and re-admit, which fragments the encounter into two and breaks the clinical history.

### 3.10 Collection timing

As specified: ward billing is normally settled **after** the stay, though it may be collected earlier.

```
Deposit collected at admission (or in advance)   optional but usual
Accruals deduct from the deposit nightly
Deposit exhausted → care continues, shortfall flagged,
                    reception and accounts alerted
Discharge → remaining balance becomes a receivable
         → unused deposit refundable (accountant action)
```

Consistent with the locked rule: an unpaid balance never blocks discharge.

---

## 4. Still open

| | Question |
|---|---|
| `?Q1` | Ward deposit collected in advance for elective procedures expecting post-op admission? |
| `?Q2` | Can the assigned nurse order discharge **independently**, or does she record a discharge the doctor has already decided verbally? Independent authority is unusual, but correct if she has been told "send her home once the fluids finish" — the audit trail shows who acted either way |
| `?Q3` | Who attests procedure reconciliation — surgeon only, or does the anaesthetist sign for drugs? |

**Resolved:** same-day admission (4-hour grace, then 1-day floor via `+1`) · discharge after 10:00 (boundary passed = day counts) · 06:00 boundary is exact · mid-day transfer (private wins the day) · three-ward movement out of scope · billing stops at the discharge order, with a 4-hour departure grace · discharge orderable by the doctor or that doctor's assigned nurse
