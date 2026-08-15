# HMS — Receptionist Module: Resolved Decisions

**Version:** 0.2 — supersedes the open questions in `hms-receptionist-module.md` §11
**Status:** Decided unless marked `?`

---

## 1. Decisions taken

| # | Decision | Consequence |
|---|---|---|
| D1 | Forward booking is out of scope. Only doctors create appointments, and only as follow-ups after a consultation | No slot engine, no provider calendar, no availability endpoint in v1 |
| D2 | Payment gates the consultation. Patient pays before seeing the doctor | `AWAITING_PAYMENT` is a real state in the encounter machine |
| D3 | Every check-in goes through triage, including returning patients | Vitals are captured every visit; triage sets the routing and therefore the price |
| D4 | Consultation charge is posted after triage, at the routed provider's rate | Amount is unknown until triage decides doctor vs specialist; no walk-out reversals |
| D5 | Cash prices: general doctor 10,000/=, specialist 40,000/= | **Stored in `PriceList`, never in code** |
| D6 | Referral to a specialist creates a new charge at the specialist rate, in full | Patient pays 50,000/= total across both consultations — see `?Q1` |
| D7 | Insurance settles the insurer portion; patient pays any top-up in cash | Invoice lines split gross / insurer / patient |
| D8 | Patient activity state is visible to all roles, exclusive per activity type | Pharmacy dispensing never blocks other activity |
| D9 | Cannot pay → patient leaves, charge voided, record retained | Does not block future visits (see `?Q3`) |
| D10 | Receptionist has read-only visibility of expected returns | No create, edit, reschedule, or cancel on appointments |

---

## 2. Encounter state machine (final for v1)

```
                     ┌──────────────────────────────────┐
                     ▼                                  │
AWAITING_TRIAGE ─▶ AWAITING_PAYMENT ─▶ AWAITING_CONSULT ─┤
       │                   │                   │         │
       │                   │                   ▼         │
       │                   │              IN_CONSULT     │
       │                   │                   │         │
       │                   │                   ▼         │
       │                   │           AWAITING_RESULTS ─┘
       │                   │
       │                   ▼
       │           ABANDONED_UNPAID  (terminal)
       ▼
LEFT_WITHOUT_BEING_SEEN  (terminal)

from IN_CONSULT:
   ├─▶ CONSULT_COMPLETE ─▶ PENDING_SETTLEMENT ─▶ CLOSED
   ├─▶ ADMITTED  (inpatient module)
   ├─▶ REFERRED_OUT  (terminal)
   └─▶ DECEASED  (terminal)
```

**`AWAITING_PAYMENT` is bypassed when `encounter.type = EMERGENCY`.**

**The payoff of D2:** the doctor's worklist is exactly `state = AWAITING_CONSULT`. No join to billing, no payment check scattered through clinical code. The gate exists in one transition and every downstream consumer trusts the state.

### 2.1 Transition guards

| Transition | Guard |
|---|---|
| `→ AWAITING_TRIAGE` | No other open encounter for this patient (partial unique index) |
| `AWAITING_TRIAGE → AWAITING_PAYMENT` | Triage assessment complete, acuity set, provider type routed |
| `AWAITING_PAYMENT → AWAITING_CONSULT` | `BillingGate.isSatisfied()` — see §4.2 |
| `AWAITING_RESULTS → AWAITING_CONSULT` | At least one ordered result verified, or clinician override |
| `→ CLOSED` | No open orders, invoice settled or written off |

`AWAITING_RESULTS → AWAITING_CONSULT` posts **no new charge**. Same encounter, already paid.

---

## 3. Expected Returns (merged nav item)

Replaces the separate "Follow-up visits" and "Appointments" items. Read-only for the receptionist.

### 3.1 Two sources, one list

| Source | Meaning | Charge on return | Re-triage |
|---|---|---|---|
| **Open encounter** in `AWAITING_RESULTS` | Consultation unfinished; patient sent home for results | None — same encounter continues | No |
| **Follow-up appointment** created by a doctor | Consultation finished; review scheduled | Per follow-up pricing rule (`?Q2`) | Yes — new encounter |

These are genuinely different and must be visually distinguished on the list, because the receptionist's action differs: the first rejoins an existing encounter, the second opens a new one.

### 3.2 The 48-hour rule is a highlight, not an expiry

An `AWAITING_RESULTS` row **remains listed until the encounter resolves**, however long that takes. Histopathology and cultures routinely exceed 48 hours.

After 48 hours the row is highlighted as overdue and gains a "chase" action. Removing it after 48 hours would mean a patient returning on day five is treated as a fresh walk-in — re-triaged and charged a second time for a consultation they already paid for.

Follow-up appointment rows appear from 48 hours before the due date and stay until attended, cancelled, or marked no-show.

### 3.3 On arrival

Patient checks in through Patient Intake as normal. The system matches them against the expected-returns list and:

- **Open encounter match** → resume it, transition `AWAITING_RESULTS → AWAITING_CONSULT`, back to the doctor's queue. No charge, no triage.
- **Follow-up appointment match** → open a new encounter with `source_encounter_id` set, mark the appointment `ATTENDED`, route through triage.
- **No match** → ordinary walk-in.

---

## 4. Billing

### 4.1 Price resolution

All prices live in `PriceList` / `PriceListEntry`, keyed by payer type, admin-editable, with `effective_from` / `effective_to`. Nothing is hardcoded.

| Service item | Cash |
|---|---|
| `CONSULT_GENERAL` | 10,000/= |
| `CONSULT_SPECIALIST` | 40,000/= |
| `CONSULT_FOLLOW_UP` | `?Q2` |

Charge posts on the `AWAITING_TRIAGE → AWAITING_PAYMENT` transition, resolved from `(service_item, encounter.payer_type, date)`. A missing price blocks the transition with an explicit error — it never defaults to zero.

### 4.2 The billing gate

```
gateSatisfied(encounter) =
       encounter.type == EMERGENCY
    OR consultationLine.patient_portion_outstanding == 0
       AND (payer == CASH
            OR policy.verification_status == VERIFIED)
    OR approvedWaiver(encounter) exists
```

**Emergency bypass is mandatory, not optional.** Once payment gates consultation, an unconscious patient would otherwise wait behind a cashier — a safety failure and, in most jurisdictions, a legal one. The charge still posts and still appears in billing; it simply does not block care.

**Who declares an emergency:** the triage nurse, as part of acuity scoring, with the acuity level recorded as justification. Not the receptionist — that would be an unaudited bypass of the only revenue gate in the system.

### 4.3 Insurance

Model the split now; the integration documents only supply rules and an adapter later.

```
SchemeCoverageRule(id, scheme_id, service_category | service_item_id,
                   coverage_type, coverage_value, requires_preauth,
                   effective_from, effective_to)

coverage_type ∈ FULL | PERCENTAGE | FIXED_AMOUNT | EXCLUDED
```

NHIF's top-up is `PERCENTAGE` or `FIXED_AMOUNT` with the remainder falling to the patient. No scheme needs special-case code.

Every invoice line carries:

```
gross_minor
insurance_portion_minor
patient_portion_minor      -- the top-up; may be zero
coverage_rule_id           -- which rule produced the split
```

**Insurance coverage is a receivable, not a payment.** Do not mark an invoice `PAID` because a scheme is covering it — that makes the cash position fiction until a remittance is reconciled months later.

Invoice states:

```
DRAFT → ISSUED → PATIENT_PORTION_SETTLED  (gate opens, claim raised)
                        ↓
                 CLAIM_SUBMITTED → CLAIM_SETTLED → CLOSED
                        ↓
                 CLAIM_REJECTED → patient liable or written off
```

`INSURANCE` is removed from the payment-method dropdown. Cash top-up on any scheme is an ordinary payment against `patient_portion`.

### 4.4 Cancellation

| Invoice condition | Path |
|---|---|
| Unpaid | Receptionist raises cancellation request with reason → admin approves → `VOID` |
| Partly or fully paid | Refund request → accountant, second signature required |

A `VOID` invoice is never deleted. The receptionist can **dismiss it from their own list** (`dismissed_at` on a per-user view row); the invoice, its lines, and its audit trail persist permanently and remain in all reports.

### 4.5 Billing badge

Red badge showing a **count**, not a bare dot. Scope: unpaid patient portions for encounters currently open — patients still in the building. Lifetime unpaid counts would leave the badge permanently lit within weeks, and staff stop seeing it.

### 4.6 Non-payment

Patient in `AWAITING_PAYMENT` who cannot pay leaves. Encounter closes `ABANDONED_UNPAID`, the consultation charge is voided with reason, and the triage assessment is retained (it is a clinical record and nursing time was spent).

Per D9 this does not block a future visit. The history is retained as a signal, not a barrier.

---

## 5. Patient activity state

Visible to every role. Answers "where is this patient and who has them" without anyone making a phone call.

```
EncounterActivity(
  id, encounter_id, activity_type, actor_user_id, location,
  started_at, last_heartbeat_at, ended_at, end_reason
)

activity_type ∈ TRIAGE | CONSULTATION | LAB_COLLECTION |
                LAB_PROCESSING | PROCEDURE | DISPENSING
```

### 5.1 Rules

1. **Exclusive per activity type, not per patient.** Partial unique index on `(encounter_id, activity_type) WHERE ended_at IS NULL`. Two doctors cannot both open a consultation; a lab technician can accession a specimen while the doctor writes notes. A global patient lock would serialise work that is legitimately concurrent.
2. **`DISPENSING` never blocks.** The pharmacist exception, as specified.
3. **Heartbeat every 60 seconds** from the open screen. No heartbeat for 15 minutes → activity is `STALE`, still displayed, reclaimable by anyone in that role.
4. **Force-release** requires a reason and is audited. It will be needed — browser crashes, power cuts, and staff leaving for lunch mid-consultation are routine.
5. Activity start and end emit events; the receptionist board updates over WebSocket.

Without 3 and 4, a single crashed browser strands a patient permanently with no recovery path. This is the most common failure mode of naive locking and the reason to build the escape hatch on day one.

### 5.2 Display

```
Amina Hassan · 34F · HN-2026-04412
With Dr. Mushi · Consultation Room 3 · 14 min
```

The location matters as much as the status — the receptionist's most frequent question is a relative at the desk asking where someone is.

---

## 6. Sidebar (final for v1)

```
Dashboard                  who is in the building, by state
Patient Intake             search-first; register-new lives inside
Billing              [3]   red count badge, scoped to open encounters
Expected Returns     [7]   awaiting-results + doctor-booked follow-ups
Patient Records            lookup only, no actions
──────────────────
Cashier Shift              open / close / reconcile
Profile                    including avatar upload
```

Referrals is **hidden**, not disabled, until built. Greyed-out items train users to stop reading the sidebar.

---

## 7. Profile and photos

Applies to all user roles. Avatar upload: max 2MB, JPEG/PNG/WebP, server-side resize to 256px and 64px, stored in the object store with only the key in the database, initials-based fallback so no broken images appear.

**Higher-value adjacent feature:** patient photo captured at registration. Same upload machinery, and it addresses insurance identity fraud (a relative presenting someone else's NHIF card) and wrong-patient errors. Worth prioritising above staff avatars.

---

## 8. Still open

| | Question | Why it blocks |
|---|---|---|
| `?Q1` | Confirm the referral reading: general doctor refers to specialist → patient pays the full 40,000/= on top of the 10,000/= already paid, total 50,000/=? | Changes the charge-posting rule on referral, and management should sign off on the patient-facing consequence |
| `?Q2` | Follow-up consultation price within the window — free, discounted, or full? And how many days is the window? | If a genuine review visit costs full price, staff will bypass the system rather than charge it, and the data stops matching reality |
| `?Q3` | Does an `ABANDONED_UNPAID` history affect a future visit at all — even as a warning shown to the receptionist? | Policy decision with clinical-ethics weight; should be explicit rather than emergent |
| `?Q4` | Who may declare `EMERGENCY` besides the triage nurse — is there an after-hours case where no triage nurse is present? | Determines whether the bypass needs a secondary path |
