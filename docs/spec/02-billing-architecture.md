# HMS — Billing Architecture (consolidated)

**Version:** 1.0
**Consolidates:** the billing rules document, receptionist decisions, emergency billing bar, doctor/triage/pharmacist/lab/ward/admin module specs
**Status:** §2–§11 settled. §12 lists what is still genuinely open.

---

## 1. The founding principle

> **Clinical permission and financial collection are independent.**
>
> "Should this patient receive care?" is a different question from "has the hospital received money?"

Every rule below follows from this. Emergency care, insurance, deferred payment, and discharge only work correctly when the two are separate concepts.

Two corollaries that carry most of the weight:

**Payment protects consumables, not patient movement.** The gate exists to stop the hospital consuming reagents, drugs, and theatre packs it will not be paid for. It does not exist to stop patients walking around a building.

**Charges are posted by events, not by screens.** If a person has to manually enter what a clinician ordered, you have two systems that disagree.

---

## 2. What is gated and what is not

| Step | Gated? | Reason |
|---|---|---|
| Registration | No | Nothing consumed |
| Triage | No | Nurse time already spent by the time anyone could charge |
| **Consultation entry** | **Yes** | The one deliberate exception — §2.1 |
| Consultation in progress | No | Never interrupted once started |
| **Lab specimen collection** | **Yes** | Reagents and analyser time |
| Lab result release | No | Cost already sunk; critical values never withheld |
| **Pharmacy dispensing** | **Yes** | Stock leaves the building |
| **Procedures with consumables** | **Yes** | Packs opened |
| Ward medication (inpatient) | No | Accrues — §7 |
| **Discharge** | **No** | Never. §8 |
| Encounter closure | No | §8 |

### 2.1 Consultation is gated — the resolved contradiction

The original billing rules stated payment must not block consultation. The doctor module then required a hard payment guard. Both cannot hold.

**Resolution:**

> Payment gates **entry** to consultation at the reception step. It does not block a consultation already in progress, nor does it block discharge.

```
canOpenConsultation(encounter) =
       encounter.state == AWAITING_CONSULT
    OR active_payment_bypass_exists(encounter)
```

Only gated-through patients reach `AWAITING_CONSULT`, so the doctor's guard is a redundant second check rather than the primary control. The bypass clause is mandatory — without it an `IMMEDIATE` patient is bounced to the dashboard with a payment error.

---

## 3. The gate function

One function, called server-side at the point of resource consumption.

```
BillingGate.isSatisfied(invoice_line) =

       invoice_line.patient_portion_settled
    OR verified_insurance_covers(invoice_line)
    OR active_payment_bypass_exists(encounter)
    OR invoice_line.authorization_id IS NOT NULL
    OR admission_deposit_backed(invoice_line)
```

### 3.1 Four rules about how it is called

**Server-side, always.** Every resource-consuming endpoint calls it. `POST /specimens`, `POST /dispenses`, `POST /procedures` each check independently and return `402` on failure. A UI check is a suggestion — a stale page, a direct call, or a race between payment and page load all defeat it.

Enforce below the controllers, in the repository or domain layer, so a new endpoint next quarter inherits the check without the developer knowing it exists.

**Evaluated live, never cached.** Do not stamp `gate_passed` at order time. The patient very likely paid between the doctor ordering and arriving at the counter.

**Per line, not per invoice.** A patient who can afford the malaria RDT but not the full panel should get the RDT. Per-invoice gating gives them nothing.

```
Invoice
  Consultation    10,000/=   Paid     ✓ dispensable
  Malaria RDT      5,000/=   Paid     ✓ collectable
  X-Ray           20,000/=   Unpaid   ✗ blocked
```

**The fourth clause is durable provenance.** `authorization_id` is set when the line is posted and never removed. This is what makes an order placed at 15:30 under an emergency bypass still fulfil at 16:05 after the bypass is revoked — otherwise a patient mid-treatment cannot get their second dose.

### 3.2 Service authorization

One record covering every way a line becomes fulfillable:

```
ServiceAuthorization(
  id, invoice_line_id,
  authorization_type,     -- PAYMENT | INSURANCE | EMERGENCY_BYPASS
                          -- | WAIVER | DEPOSIT
  authorized_at, authorized_by,
  reference_id            -- payment, claim, bypass, or deposit account
)

InvoiceLine.authorization_id → ServiceAuthorization
```

⚠ This **replaces** `posted_under_bypass_id` from the emergency bar spec. Two records for one fact would eventually disagree.

### 3.3 Rejection needs a destination

A refusal without a next step leaves a patient at a counter.

```
402 response carries:
   the outstanding amount
   the specific service name
   a "send patient to cashier" action

Cashier's queue receives:
   Patient · Pending service · Amount
   e.g.  John Mwita · Malaria RDT · 5,000/=
```

Not a generic unpaid invoice — the specific line, so the receptionist can take 5,000/= rather than discussing the whole bill.

Better still: the doctor's order-completion screen tells the patient where to go — cashier first, then lab. Cheaper than discovering it at the window.

⚠ Cashier pending items need their own expiry, or a patient sent to the cashier who walks out leaves a permanent row.

---

## 4. Charge origination

Every charge is posted by a domain event. There is no manual charge-entry path.

| Event | Charge | Amount from |
|---|---|---|
| `TriageCompleted` | Consultation | Routed provider type × payer price list |
| Internal referral | New consultation | Receiving provider's full rate |
| `OrderPlaced` (lab/imaging) | Test | Catalogue × payer |
| `PrescriptionIssued` | Drugs | Catalogue × payer |
| `SurgeryOrdered` | Procedure | Catalogue × payer |
| Nightly job | Bed-day | Ward rate that night — §7.2 |

### 4.1 The consultation charge posts after triage

Resolved from the receptionist phase: the amount is unknown until triage routes to a doctor or specialist, so posting at encounter-open would mean reversing a charge for every walk-out.

```
AWAITING_TRIAGE → [triage routes] → AWAITING_PAYMENT → AWAITING_CONSULT
                       ↓
              charge posted here
```

⚠ The charge posting is inside the triage submission transaction. If it fails, the whole assessment rolls back — a triaged patient with no charge walks straight past the cashier.

### 4.2 Prices are never in code

```
CONSULT_GENERAL        10,000/=   cash
CONSULT_SPECIALIST     40,000/=   cash
CONSULT_FOLLOW_UP      ?          still open — §12
Ward daily rate        per ward
```

All in `PriceList` / `PriceListEntry`, admin-editable, versioned by `valid_from` / `valid_to`. A missing price for the applicable payer **blocks** the transition with an explicit error — it never defaults to zero.

⚠ Prices are never edited in place. A change closes the current entry and opens a new one, so a March invoice always resolves March's price.

### 4.3 Nobody types a price

The rule appeared in four modules and was resolved the same way each time: receptionist invoice lines, doctor surgery orders, ward nurse pre/post-op items, lab and pharmacy catalogue proposals. Price resolves from the catalogue against the payer's price list.

Bespoke pricing exists as a **quote requiring accountant approval**, referenced from the order — never a free field on a clinical form.

---

## 5. Emergency bypass

### 5.1 Not a waiver — a deferred payment authorization

```
Triage priority = IMMEDIATE
   → encounter.type = EMERGENCY
   → PaymentBypass created, source TRIAGE_IMMEDIATE, ACTIVE
   → AWAITING_PAYMENT skipped entirely → AWAITING_CONSULT
   → consultation, lab, pharmacy, procedures all permitted
   → charges accrue and appear on the emergency billing bar
   → on discharge, balance → receivable
```

The charge is never skipped, only unblocked.

**Scope is the whole encounter, not one charge.** A consultation-only waiver would leave the same critical patient hitting a payment wall at the lab and again at pharmacy.

### 5.2 Declaration and confirmation

```
IMMEDIATE          automatic, triage nurse
                   confirmation requires a REASON SELECTION, not an OK —
                   plain dialogs attract click-through blindness
URGENT             nurse may declare with a reason
LESS_URGENT / below  clinician authorisation required
```

The reason selection (airway, shock, unresponsive, active bleeding, convulsion, obstetric emergency, other) becomes the bypass reason in the record. One extra tap, and the justification is on file.

### 5.3 Revocation

The attending doctor revokes once the patient is stabilised. Charges posted after revocation gate normally; charges already posted stay unblocked via `authorization_id`.

### 5.4 The emergency billing bar

A persistent bar at the top of the receptionist workspace, hidden when empty.

```
⚠ EMERGENCY — 2 patients · 87,000/= outstanding        [expand]
```

Membership is **"has an active bypass with unsettled balance"**, not "priority == IMMEDIATE". Identical today; correct forever, because a clinician-authorised bypass would otherwise be invisible.

Balance is **live**, not a fixed consultation fee — a critical patient accrues consultation, labs, consumables, drugs, and possibly a bed over hours.

Two constraints that must survive future "improvements":

⚠ **Never blocks discharge.** Detention for debt.

⚠ **No payment prompt during active critical care.** The balance displays; the "take payment" action is not surfaced as a nudge while the patient is in resuscitation. Nobody should be prompted to find a family in a corridor.

---

## 6. Insurance

### 6.1 Coverage satisfies the gate; it is not cash

```
gateSatisfied via insurance =
       policy.verification_status == VERIFIED
   AND scheme_covers(service)
   AND patient_portion_outstanding == 0
```

The insurer portion becomes a **claim**, a receivable. Marking an invoice `PAID` because NHIF is covering 30,000/= makes the cash position fiction until a remittance reconciles months later.

⚠ `INSURANCE` is removed from the payment-method dropdown. A cash top-up on any scheme is an ordinary payment against `patient_portion`.

### 6.2 Coverage rules as data

```
SchemeCoverageRule(
  id, scheme_id, service_category | service_item_id,
  coverage_type,          -- FULL | PERCENTAGE | FIXED_AMOUNT | EXCLUDED
  coverage_value, requires_preauth,
  effective_from, effective_to
)
```

NHIF's top-up is `PERCENTAGE` or `FIXED_AMOUNT` with the remainder falling to the patient. No scheme needs special-case code, and the integration documents supply rule rows and an adapter rather than a schema change.

Rules are versioned — a claim must be assessable against the rule that applied on the service date.

### 6.3 Line-level split

```
InvoiceLine:
  gross_minor
  insurance_portion_minor
  patient_portion_minor       -- the top-up; may be zero
  coverage_rule_id            -- which rule produced the split
```

### 6.4 Invoice states

```
DRAFT → ISSUED → PATIENT_PORTION_SETTLED   gate opens, claim raised
                        ↓
                 CLAIM_SUBMITTED → CLAIM_SETTLED → CLOSED
                        ↓
                 CLAIM_REJECTED → patient liable, or written off
```

---

## 7. Inpatient — accrue, don't gate

### 7.1 Deposit ledger

Per-admission, non-insurance patients.

```
DepositAccount(id, admission_id, patient_id, opened_at, closed_at)

DepositTransaction(
  id, deposit_account_id,
  direction,        -- TOP_UP | DEDUCTION | REFUND | ADJUSTMENT
  amount_minor,
  reference_type, reference_id,     -- the line that consumed it
  actor_user_id, cashier_shift_id,
  idempotency_key, occurred_at
)
```

Balance is derived from the ledger, materialised into a cached column, reconciled nightly with an alert on drift. A mutable balance field drifts and cannot be reconstructed when a patient disputes a statement.

| Rule | |
|---|---|
| Accounting | A deposit is a **liability, not revenue** — excluded from revenue reports until consumed |
| Refunds | Unused balance refundable at discharge, accountant action |
| Exhaustion | **Does not block care.** Dispensing continues, shortfall flagged, reception and accounts alerted, balance → receivable at discharge |

Ward medication is dispensed per-patient direct from pharmacy (no ward sub-store), so stock leaves at dispense and administration consumes from that patient's own supply.

### 7.2 Bed-day accrual, not advance invoicing

Resolved from the ward nurse phase, replacing "changing ward voids the prior invoice":

```
WardChargeAccrual(id, admission_id, bed_assignment_id, ward_id,
                  charge_date, daily_rate_minor, invoice_line_id)
```

A nightly job posts one row per occupied bed-day at the rate applying that night. A transfer closes one `BedAssignment` and opens another; **no invoice is voided and nothing is reissued.** Four days in a general ward then three in a private room bills correctly as four plus three.

---

## 8. Encounter closure and discharge

```
close(encounter):
    if medical_complete:  encounter.status = CLOSED
    if balance > 0:       create receivable
```

The two are **independent**. Neither blocks the other.

⚠ This replaces the original architecture rule requiring a settled invoice to close. That guard was the discharge block in disguise.

```
canClose(encounter):
  clinical   no open orders          → blocks; override with reason
  financial  (nothing)               → never blocks
```

⚠ **There must be no code path where an outstanding balance prevents an `EncounterClosed` transition.** This appeared as a block in the doctor and ward nurse workflows and was removed in both. Holding a patient until a debt is settled is detention for debt.

Post-discharge balances become `RECEIVABLE_POST_DISCHARGE`, appearing on the accountant's aging report and leaving the receptionist's emergency bar.

### 8.1 Unpaid orders at discharge

Both mechanisms, because they solve different cases:

```
On discharge      unpaid orders → CANCELLED, charge voided, reason recorded
                  (handles the patient who leaves)

TTL expiry        AWAITING_PAYMENT → EXPIRED after a configured period,
                  per order type — pharmacy expires faster than a
                  scheduled procedure
                  (handles orders on encounters that stay open)
```

Neither alone is sufficient.

---

## 9. Payments

### 9.1 Cashier shift

Every payment belongs to an open `CashierShift`. `PAYMENT_CREATE` fails with a prompt when none is open.

At shift end the cashier enters counted cash; the system computes expected, records variance, and hands it to the accountant to reconcile and close. Without this, "the drawer is short 40,000/=" has no answer, and the receptionist is exposed as much as the hospital.

⚠ Shift close takes a lock; late payments are rejected with a prompt to open a new shift.

### 9.2 Payment rules

| Field | Rule |
|---|---|
| `amount_minor` | > 0; overpayment creates a patient credit, never a negative balance |
| `method` | `CASH` `DEBIT_CARD` `CREDIT_CARD` `MOBILE_MONEY` `BANK_TRANSFER` `CHEQUE` — `INSURANCE` removed |
| `reference` | Required for every non-cash method |
| `idempotency_key` | Required. Unique index. A repeat returns the original payment |

⚠ **Idempotency is not optional.** A double-tap on a mobile-money confirmation must not record two payments.

⚠ `CHEQUE` lands in `PENDING_CLEARANCE`, not `SETTLED`.

Payment against a `VOID` or `CANCELLATION_PENDING` invoice is rejected.

### 9.3 Outstanding balance

```
outstanding = total
            − payments_settled
            − credit_notes
            − write_offs
            − insurance_approved
            + refunds
```

Materialised on `Invoice`, updated inside the same transaction as any component change, with a nightly job recomputing from source rows and alerting on drift. Trust the derived value for display; trust the ledger for truth.

---

## 10. Invoice lifecycle and cancellation

```
DRAFT → ISSUED → PATIENT_PORTION_SETTLED → CLOSED
  │        │
  │        └→ CANCELLATION_PENDING → VOID          (approved)
  │                               └→ ISSUED        (rejected)
  └→ expires nightly if never issued
```

**Two-step issue.** Save creates a `DRAFT` — freely editable, not a financial record. A separate Issue action assigns the invoice number from a gapless sequence and locks the lines. Without this, every typo becomes an issued financial document that can only be undone through the cancellation queue, which then drowns.

**Cancellation:** receptionist requests with a reason → admin approves or rejects.

```
Approve → VOID, charges reversed, refund path if paid
Reject  → back to ISSUED, collectable, admin's note shown to requester
```

⚠ Rejection must return the invoice to a **live** state. Leaving it frozen means a receptionist who sees "rejected" does not collect, and the revenue is lost without anyone deciding to lose it.

⚠ **Reconsideration raises a new request** referencing the first, rather than reopening a closed decision. The sequence stays in the record.

⚠ **Once any payment has landed, void is blocked** and routes to the accountant's refund flow with a second signature.

⚠ **A `VOID` invoice is never deleted.** It is the evidence that a number was issued and cancelled; a gap in the sequence with no explanation is what an auditor looks for. The receptionist may dismiss it from their own list (`dismissed_at` on a per-user view row); the invoice persists.

---

## 11. Data model

```
ServiceItem / Item          the chargeable thing
PriceList                   per payer type, validity-dated
PriceListEntry              item × list × amount × validity

Invoice                     encounter_id (nullable), patient_id, state,
                            totals, payer_type, policy, version
InvoiceLine                 service_item, quantity, gross_minor,
                            insurance_portion_minor, patient_portion_minor,
                            coverage_rule_id, authorization_id,
                            source_event_type, source_event_id

ServiceAuthorization        §3.2
PaymentBypass               source, reason, granted/authorised/revoked
Payment                     amount, method, reference, shift,
                            idempotency_key
PaymentAllocation           payment × invoice_line × amount   ← §12
CashierShift                expected, counted, variance, reconciled_by

DepositAccount              per admission
DepositTransaction          the ledger
WardChargeAccrual           per bed-day

InsuranceScheme
SchemeCoverageRule          validity-dated
InsuranceClaim              invoice, scheme, state, approved, rejected

InvoiceCancellationRequest  reason, decision, supersedes_request_id
```

### 11.1 Events

**Emitted:** `ChargePosted` · `InvoiceIssued` · `PaymentRecorded` · `InvoiceSettled` · `PaymentBypassGranted` · `PaymentBypassRevoked` · `DepositToppedUp` · `DepositExhausted` · `ClaimSubmitted` · `ReceivableCreated` · `InvoiceVoided`

**Consumed:** `TriageCompleted` · `OrderPlaced` · `PrescriptionIssued` · `MedicationDispensed` · `ResultVerified` · `PatientAdmitted` · `EncounterClosed`

⚠ `InvoiceSettled` releases the gate, so it must fire reliably — delivered via the transactional outbox, not a direct call.

---

## 12. Genuinely open

These four block implementation. The rest of this document is settled.

### `?Q1` Line-level payment allocation

§3.1 gates per line, but payments attach to invoices. Invoice holds consultation 10,000/= and malaria RDT 5,000/=; patient pays 5,000/=. **Which line is satisfied?**

Arbitrary allocation — oldest first, pro-rata — means the patient pays for the test they need and the money lands on the consultation. They are turned away at the lab holding a receipt.

```
PaymentAllocation(payment_id, invoice_line_id, amount_minor)
```

The payment screen becomes a **line picker**: tick the lines being paid, total fills in, take the money. A line's gate opens when allocated amount covers its patient portion. Partial allocation to one line leaves that line closed — no half-dispensing.

**Without this, line-level gating does not function.**

### `?Q2` Follow-up consultation price

Open since the receptionist phase. Payment gates consultation and every check-in re-triages, so a patient returning Wednesday for the doctor to read Monday's labs is charged a second 10,000/=.

Needs: a price for `CONSULT_FOLLOW_UP` (free, discounted, full) and a window in days.

⚠ If a genuine review visit costs full price, staff will wave patients through informally and the data stops matching reality — which is worse than either pricing choice.

### `?Q3` The `close()` sweep

`close()` fires when `medical_complete`. Nothing says what happens when a clinician never closes one. Those encounters accumulate and pollute every "who is in the building" query.

Proposal: open beyond a configured window → flagged for review → auto-close `CLOSED_ADMINISTRATIVELY` with the reason recorded, distinguishable from clinical closure in reporting.

### `?Q4` Procedure gate timing

Procedures are named as gated but have no timing rule like lab (§ specimen collection) and pharmacy (§ before stock leaves).

Proposal: gate at **procedure start**, before the consumable pack is opened.

---

## 13. Reporting truths

Three corrections that determine whether the numbers mean anything:

| | |
|---|---|
| **Deposits are a liability** | Unconsumed deposits are money held, not earned. Excluded from revenue until a service consumes them |
| **Claims are billed, not collected** | Revenue reporting must distinguish the two, or the cash position is fiction until remittances reconcile |
| **Bypass debt is neither** | Accrued and unpaid — a receivable, not revenue |

Monthly governance reports: emergency bypass leakage (bypassed / recovered / written off / receivable, by department) · price change history · write-off and refund summary · cashier variance by user · aged receivables.

⚠ Bypass leakage goes to admin, the accountant, **and** the clinical lead. Admin and accounts see whether the money came back; the clinical lead sees whether the declarations were appropriate. Neither view alone answers whether the mechanism is working.
