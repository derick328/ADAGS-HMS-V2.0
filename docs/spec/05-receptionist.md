# HMS — Receptionist Module Specification

**Version:** 0.1
**Depends on:** `identity`, `patients`, `encounters`, `scheduling`, `billing`
**Notation:** `⚠` marks a deviation from the described flow, with reasoning. `?` marks an open question.

---

## 1. Role definition

### 1.1 Permissions held

```
PATIENT_SEARCH                  PATIENT_CREATE
PATIENT_READ_DEMOGRAPHIC        PATIENT_UPDATE_DEMOGRAPHIC
PATIENT_READ_VISIT_HISTORY      -- dates/departments only, not clinical content
ENCOUNTER_OPEN                  ENCOUNTER_CLOSE_ADMIN
TRIAGE_QUEUE_ADD
APPOINTMENT_CREATE              APPOINTMENT_READ
APPOINTMENT_RESCHEDULE          APPOINTMENT_CANCEL
APPOINTMENT_CREATE_SPECIALIST
PRICE_CATALOG_READ
INVOICE_CREATE                  INVOICE_READ
INVOICE_CANCEL_REQUEST
PAYMENT_CREATE
CASHIER_SHIFT_OPEN              CASHIER_SHIFT_CLOSE_OWN
```

### 1.2 Permissions explicitly withheld

```
PATIENT_MERGE                   -- admin only, destructive
PATIENT_DELETE                  -- nobody
CLINICAL_NOTE_READ              -- no narrative access
TRIAGE_ASSESSMENT_READ          -- see ⚠1
LAB_RESULT_READ
PRESCRIPTION_READ
INVOICE_CANCEL                  -- request only, admin approves
INVOICE_DELETE                  -- nobody
PAYMENT_REFUND                  -- accountant only
PRICE_LIST_MANAGE               -- admin/accountant only
```

### ⚠1 — "View full profile → triage history" leaks clinical data

Triage history contains chief complaint, vital signs, and acuity level. That is clinical content, and the receptionist has no care-relationship justification for it. But the receptionist genuinely does need visit history — to answer "when was she last here?" and "has this patient been seen for this before?"

**Change:** split it. Receptionist sees a `VisitHistoryView` read model:

| Exposed | Withheld |
|---|---|
| Encounter date, type, state | Chief complaint text |
| Department / clinic | Vital signs |
| Attending provider name | Acuity level |
| Disposition (discharged / admitted / referred) | Any diagnosis, note, or result |
| Invoice + payment status | |

Build it as a separate projection queried by the receptionist workspace, not as a filtered version of the clinical query. A projection cannot leak fields it never selects; a filter can be forgotten.

---

## 2. Routes

| Route | Purpose |
|---|---|
| `/receptionist/dashboard` | Operational overview |
| `/receptionist/patients` | Search + active queue |
| `/receptionist/patients/register` | Intake form |
| `/receptionist/patients/:id` | Patient profile |
| `/receptionist/patients/:id/edit` | Amend demographics |
| `/receptionist/appointments` | Appointment list + booking |
| `/receptionist/billing` | Invoice list + creation |
| `/receptionist/billing/invoices/:id` | Invoice detail + payments |
| `/receptionist/shift` | Cashier shift open/close/reconcile |

---

## 3. Dashboard

### ⚠2 — Lifetime counters are not operational information

"Total patients" and "total invoices" are cumulative counts. After eighteen months they read `48,203` and `61,847` and tell the receptionist nothing about their shift. They also get slower every month.

**Change:** make every dashboard number today-scoped and actionable.

| Tile | Query | Why it matters |
|---|---|---|
| Waiting for triage | encounters in `AWAITING_TRIAGE` | The receptionist's own backlog |
| In progress today | encounters opened today, not closed | Who is in the building |
| Appointments today | scheduled today, by status | Arrivals to expect |
| Unpaid at desk | invoices issued today, `outstanding > 0` | Collect before the patient leaves |
| Registered today | patients created today | Volume metric |
| My shift total | payments in the open cashier shift | Must match the drawer |

Keep the 8 most recent appointments panel — that one is useful. Add "next 8 upcoming today" alongside it; recent-past is less actionable than imminent-future at a front desk.

Every tile is a link into a filtered list. A number a user cannot click is a dead end.

---

## 4. Patient registration

### 4.1 Search-before-create is mandatory

`/receptionist/patients/register` must not be reachable as a blank form from the nav. The entry point is a search box; the "Register new patient" button appears *below the results*, after a search has run.

Duplicate detection on submit (not just on search) using trigram similarity over `given_name || family_name`, plus exact match on `phone` or `national_id`:

- Exact `national_id` match → block, offer to open the existing record
- Similarity above the high threshold **and** DOB match → block, require explicit "not the same person" confirmation with a logged reason
- Similarity above the low threshold → warn, show candidates, allow proceed

Tune the thresholds against real data during phase 1. Duplicates are the most expensive data problem in this system and the cheapest place to prevent them is here.

### 4.2 Fields

`*` = required.

**Identity**

| Field | Type | Rules |
|---|---|---|
| `given_name` * | string | 2–50, letters, space, apostrophe, hyphen. Trim, collapse whitespace, title-case for storage |
| `middle_name` | string | 0–50, same charset |
| `family_name` * | string | 2–50, same charset |
| `date_of_birth` * | date | Not future; not more than 120 years past |
| `dob_is_estimated` | bool | See ⚠3 |
| `gender` * | enum | `MALE` `FEMALE` `OTHER` `PREFER_NOT_TO_SAY` |
| `marital_status` | enum | `SINGLE` `MARRIED` `DIVORCED` `WIDOWED` — hide the field entirely when age < 18 |
| `national_id` | string | Optional. Partial unique index `WHERE national_id IS NOT NULL`. Validate format but allow override with a logged reason — real IDs have irregularities |

### ⚠3 — Exact DOB is frequently unknown

Requiring a precise date of birth means staff will type `01/01/1980` for every patient who only knows their age. That garbage then flows into age-banded lab reference ranges and paediatric dosing.

**Change:** accept either a full date, or an age in years with an optional month. Derive DOB from age and set `dob_is_estimated = true`. Every downstream consumer that shows age must indicate estimation. Cheap now, impossible to reconstruct later.

**Contact**

| Field | Type | Rules |
|---|---|---|
| `phone` * | string | Normalise to E.164 on save; default country +255. **Not unique** — families and shared households legitimately share one number. Do not add a unique constraint |
| `email` | string | Optional, format-validated, not unique |
| `occupation` | string | ⚠ free text produces `"teacher"`, `"Teacher"`, `"tchr"`. Use a lookup table with an "other → free text" escape if you intend to report on it |

**Next of kin** — all three required as specified.

| Field | Type | Rules |
|---|---|---|
| `nok_full_name` * | string | 2–100 |
| `nok_relationship` * | enum | ⚠ make this an enum (`SPOUSE` `PARENT` `CHILD` `SIBLING` `GUARDIAN` `OTHER`), not free text — it drives consent logic for minors |
| `nok_phone` * | string | E.164 normalised. Warn if identical to patient phone; allow it |

### ⚠4 — Mandatory next-of-kin blocks emergency intake

An unconscious road-traffic casualty arrives with no companion. Under the current rules the receptionist cannot register them, so they will either invent next-of-kin data or the patient waits.

**Change:** add an explicit emergency fast-path — `POST /patients/emergency` requiring only estimated age, gender, and an arrival note. It generates a temporary identity (`UNKNOWN-<sequence>`), opens an `EMERGENCY` encounter immediately, bypasses the billing gate, and flags the record `identity_provisional = true`. That flag drives a persistent banner and a task queue for completing identity later, ideally merging into an existing record once the patient is identified. Next-of-kin stays required on the normal path.

**Addresses** (optional)

Stored as a repeatable `PatientAddress(patient_id, address_type, ...)` with `address_type ∈ {CURRENT, PERMANENT}`, not as flat columns on `Patient` — people move, and you want the history.

⚠ `street` / `city` does not fit Tanzanian addressing. Use `region`, `district`, `ward`, `street_or_village`, `landmark`. Landmark is the field staff will actually use to find a patient's home for follow-up. Add a "permanent same as current" checkbox.

**Insurance** (optional)

### ⚠5 — Flat insurance fields will not survive contact with claims

`provider` as free text plus two numbers cannot answer "is this policy valid today?", "who is the principal member?", or "has this been verified?" — all of which the specialist gate and the claims module need.

**Change:** create an `InsurancePolicy` row, not three columns:

| Field | Rules |
|---|---|
| `scheme_id` * | FK to `InsuranceScheme` — a managed list, not free text. Determines which price list applies |
| `policy_number` * | Per-scheme format validation |
| `member_number` | Optional |
| `is_principal` | bool, default true |
| `principal_name` | Required when `is_principal = false` |
| `valid_from`, `valid_to` | Nullable; expiry warning at the desk |
| `verification_status` | `UNVERIFIED` `VERIFIED` `FAILED` + `verified_at` |

The scheme drives `encounter.payer_type`, which drives the price list, which drives every charge on the visit. Getting it as free text means every invoice is priced wrong.

### 4.3 On submit

Single transaction:

1. Assign `hospital_no` from a locked per-facility-per-year sequence (gapless, human-readable, printed on the card)
2. Insert `Patient`, `NextOfKin`, `PatientAddress[]`, `InsurancePolicy[]`
3. Write `AuditLog` entry
4. Emit `PatientRegistered`

**Registration does not open an encounter.** A patient registered on Tuesday for a Friday appointment is not in the building. Arrival is a separate action (§5.2).

Response: redirect to the patient profile with the hospital number displayed prominently and a print-card action.

---

## 5. Patient list and actions

### 5.1 The two lists

### ⚠6 — "Idle patients" is the wrong default view

Defined as "patients not currently in triage, consultation, admitted, or in surgery", the idle list is *every patient the hospital has ever registered* minus a few dozen. At 40,000 patients it is an unusable, expensive query, and it grows forever.

**Change:** the default view is two clearly separated panels.

**Panel A — Here now** (the working list, small and bounded)
Encounters opened today or still open from earlier, grouped by state: `AWAITING_TRIAGE`, `AWAITING_CONSULT`, `AWAITING_RESULTS`, `PENDING_SETTLEMENT`. This is what the receptionist actually works from.

**Panel B — Find a patient** (search only, no default result set)
Empty until a query is entered. Search by hospital number, name, phone, or national ID. Results show name, hospital number, age, phone, last visit date, and whether an encounter is currently open.

There is no legitimate "list all patients" screen for a receptionist. Unbounded patient browsing is the classic data-exfiltration path — a curious employee paging through records. Rate-limit search and audit every query.

### 5.2 Send to triage

One click, no form — correct, keep that.

### ⚠7 — This should open an `Encounter`, not create a `TRIAGE` appointment

Modelling arrival as an appointment conflates two different things. An appointment is a *future intention* that can be booked, rescheduled, cancelled, and no-showed. An encounter is an *actual visit in progress* that has a state machine, accumulates orders and charges, and gets closed. If arrival creates an appointment row, then:

- The triage queue, doctor queue, and billing all key off appointments, and you have no single object representing "in the building now"
- "Reschedule" becomes a valid operation on a patient standing at the desk
- A patient who booked a Friday appointment *and* walks in Tuesday has two appointment rows and no way to say which one they are currently attending
- `TRIAGE` sits in the appointment-type enum next to `SPECIALIST` and `FOLLOW_UP`, which are genuinely appointment types

**Change:**
- Remove `TRIAGE` from the appointment type enum
- `POST /encounters` opens the encounter with `state = AWAITING_TRIAGE`
- If the patient arrived against a booked appointment, link it: `encounter.appointment_id`, and mark the appointment `ATTENDED`. This is your no-show metric for free
- If they walked in, `appointment_id` is null

**Guard — one open encounter per patient.** Enforce with a partial unique index: `UNIQUE (patient_id) WHERE state NOT IN (terminal states)`. Without it, an impatient patient clicked through twice appears in the triage queue twice, gets triaged twice, and generates two consultation charges. Attempting a second open encounter returns the existing one and navigates to it rather than erroring.

**Steps in one transaction:**

1. Verify no open encounter (or return the existing one)
2. Resolve `payer_type` from active insurance policy, else `CASH`
3. Insert `Encounter(type=OUTPATIENT, state=AWAITING_TRIAGE, department_id, payer_type)`
4. Insert `EncounterEvent(null → AWAITING_TRIAGE, actor, occurred_at)`
5. Post the consultation charge per the payer's price list (see §7.5)
6. Emit `EncounterOpened`

`EncounterOpened` is consumed by the triage queue projection and pushed over WebSocket to the waiting-room display and the triage nurse's worklist. The receptionist does not notify anyone manually.

### 5.3 Dismiss

### ⚠8 — Do not deactivate the patient

`patient.active = false` says "this human is no longer a patient of this hospital". Applying it to "this person left the waiting room" means their next visit either fails a lookup or creates a duplicate record. Patients are permanent; visits are not.

**Change:** the action closes the *encounter*, not the patient.

| Situation | Encounter terminal state | Notes |
|---|---|---|
| Left before being seen | `LEFT_WITHOUT_BEING_SEEN` | Reverse any prepaid consultation charge or convert to credit |
| Registered in error | `CANCELLED_IN_ERROR` | Requires reason; void associated invoice |
| Seen and finished | `CLOSED` | Only when no open orders and invoice settled |

Reason is required. Guard: cannot close an encounter with open orders or an unsettled invoice unless a supervisor override is supplied, logged with justification.

`patient.active` remains, but as an admin-only flag for deceased or formally transferred-out patients — never a front-desk action.

---

## 6. Appointments

### 6.1 Book standard appointment

| Field | Rules |
|---|---|
| `patient_id` * | Must exist and be active |
| `provider_id` | Optional as specified — ⚠ see below |
| `scheduled_at` * | Not in the past; within the facility's booking horizon |
| `appointment_type` * | `GENERAL` `SPECIALIST` `FOLLOW_UP` `EMERGENCY` `ROUTINE_CHECKUP` (⚠ `TRIAGE` removed per ⚠7) |
| `department_id` | ⚠ add this — required when `provider_id` is null |
| `notes` | Free text, 0–1000 |

### ⚠9 — Standard bookings need the same conflict check as specialist bookings

The spec applies a double-booking check only to the specialist modal. Nothing stops a receptionist booking six general appointments into the same 09:00 slot with the same doctor. The check belongs in the domain layer where both flows pass through it, not in one modal's JavaScript.

**Change:** one `AppointmentBookingPolicy` applied to every booking path:

1. Provider has a clinic session covering that datetime
2. Slot is not full (`session.slot_capacity`, which may be > 1 for group clinics)
3. Provider is not on leave
4. Patient has no other appointment within a configurable window
5. Datetime is inside facility operating hours

Return specific violations, not a generic failure — the receptionist needs to know *why* so they can offer an alternative. Provide the nearest available slots in the error response.

If `provider_id` is null, `department_id` becomes required and the appointment goes to a departmental pool assigned on arrival.

### 6.2 Schedule specialist appointment

Gated as described. The gate logic needs consolidating.

### ⚠10 — "Check the triage referral's invoice or the medical record's invoice" is fragile

An `OR` across two lookup paths means a third referral source (ward discharge, external referral, second opinion) silently fails the gate — or worse, silently passes it. And the payment check is expressed in terms of an invoice, when the real question is about one specific charge.

**Change:** one domain service, one question.

```
BillingGate.isSatisfied(encounter, SPECIALIST_CONSULTATION) → bool
```

It resolves as: locate the `InvoiceLine` for the specialist consultation charge on this encounter, then return true if any of —

- The line is fully paid, or
- The encounter's insurance policy covers this service and a pre-authorisation exists in `APPROVED`, or
- `encounter.type = EMERGENCY` (emergency care is never gated on payment), or
- A documented waiver exists (charity, staff benefit, ministry directive) with an approver

Referral source becomes irrelevant to the gate. A referral is just `source_encounter_id` on the new appointment, and it can come from anywhere.

**Fields:** `patient_id`*, `provider_id`* (required here), `scheduled_at`*, `source_encounter_id`, `notes`. Same booking policy as §6.1 applies on top of the gate.

When the gate fails, do not show "not allowed". Show the outstanding amount and a direct action to collect payment, then return the receptionist to the booking. A blocked flow with no exit is the fastest way to get staff working around your system.

### 6.3 Provider schedule lookup (the AJAX endpoint)

`GET /api/providers/:id/availability?date=YYYY-MM-DD`

Returns sessions, booked slots, and free slots. Notes:

- Return **free slots**, not just existing bookings. Making the client compute availability from a booking list means two clients disagree
- Show booked slots as busy/free only, never patient names — that is a confidentiality leak on a screen a patient can see over the desk
- Cache 30s, invalidate on any booking for that provider/date
- This is advisory only. The authoritative check runs server-side on submit. Two receptionists can both see a free slot in the same second

### 6.4 Reschedule and cancel

**Reschedule** — new datetime*, notes. Full booking policy re-applied. Never mutate the row in place: set the original to `RESCHEDULED`, create a new appointment with `rescheduled_from_id`. You need the history for no-show and utilisation analysis.

**Cancel** — ⚠ reason should be **required**, not optional, and an enum: `PATIENT_REQUEST` `PROVIDER_UNAVAILABLE` `DUPLICATE` `CLINICAL_DECISION` `NO_SHOW` `OTHER` (+ free text when `OTHER`). Optional free-text reasons produce an empty column and no ability to distinguish a patient cancelling from a clinic collapsing.

Both emit events for SMS notification. Reverse or credit any prepaid charge on cancel.

---

## 7. Billing

### 7.1 Cashier shift

Not in the described flow, and it is load-bearing.

A receptionist who takes cash must operate inside an open `CashierShift`. Every `Payment` carries `cashier_shift_id`. At end of shift they enter counted cash; the system computes expected, records the variance, and hands it to the accountant to reconcile and close. Without this there is no answer to "the drawer is short 40,000 — whose shift?", and the receptionist is exposed as much as the hospital is.

Guard: `PAYMENT_CREATE` fails with a clear prompt if no shift is open. Opening a shift is one click.

### 7.2 Create invoice

| Field | Rules |
|---|---|
| `patient_id` * | — |
| `encounter_id` | ⚠ add — see below |
| `due_date` * | Not in the past; default from payer terms (cash = today) |
| `notes` | 0–1000 |
| Line items | ≥ 1 required |

### ⚠11 — Invoices should attach to an encounter, not float on a patient

Without `encounter_id` you cannot answer "what did this visit cost", cannot auto-post charges from clinical events, and cannot tell whether the specialist gate has been satisfied *for this visit*. Standalone patient-level invoices exist (a pharmacy-only walk-in, a records-copy fee) so keep it nullable — but default to the open encounter when there is one, and warn when creating an unattached invoice while an encounter is open.

**Line items**

| Field | Rules |
|---|---|
| `service_item_id` * | ⚠ FK to catalogue, not free text |
| `description` | Defaults from the catalogue; editable for clarification only |
| `category` | ⚠ derived from the catalogue item, not user-selected |
| `quantity` * | > 0; integer or decimal per item's unit |
| `unit_price_minor` * | ⚠ from the active price list — see below |

### ⚠12 — Free-text descriptions and hand-typed prices are a revenue leak

If the receptionist types a description and a price, then "Full Blood Count", "FBC", "Full blood count " and "CBC" are four different services in your reporting, and the same test costs a different amount depending on who was at the desk. Category selection has the same problem — the same item gets filed under `LAB` by one person and `OTHER` by another.

**Change:** the price catalogue is authoritative, not a convenience autofill.

- The line item picker searches `ServiceItem`; selection is required
- `category` and `description` come from the item
- `unit_price_minor` is resolved server-side from the active `PriceList` for `encounter.payer_type` — never sent by the client
- Price override requires `PRICE_OVERRIDE` permission (which the receptionist does not hold), an approver, and a reason. Overrides go in a report the accountant reviews
- Items with no price for the applicable payer are blocked, not defaulted to zero

Keep the live search endpoint — it is the right interaction. Change what it returns from a suggestion to a binding selection. It must also be payer-aware: `GET /api/price-catalog/search?q=...&payer_type=...&encounter_id=...` returns the price that will actually be charged, so the desk quote matches the invoice.

### 7.3 Issue on save

"Saving immediately creates, adds line items, and issues" — correct that it is one atomic transaction. But issuing with no review step means every typo becomes an issued financial document that can only be undone through the admin cancellation-request workflow. That workflow will drown.

**Change:** two-step, both on one screen. Save creates a `DRAFT` (freely editable, deletable, not a financial record). A separate Issue action assigns the invoice number, transitions to `ISSUED`, and locks the lines. Drafts auto-expire nightly. The receptionist still does this in one visit to the screen; they just get a confirm step showing the total before it becomes permanent.

Invoice numbers come from the same locked-sequence pattern as hospital numbers — gapless per facility per year, assigned at issue, never at draft.

### 7.4 Record payment

| Field | Rules |
|---|---|
| `amount_minor` * | > 0; ≤ outstanding unless `ALLOW_OVERPAYMENT` — overpayment creates a patient credit, never a negative balance |
| `method` * | `CASH` `DEBIT_CARD` `CREDIT_CARD` `MOBILE_MONEY` `INSURANCE` `BANK_TRANSFER` `CHEQUE` |
| `reference` | ⚠ required for every non-cash method; optional only for `CASH` |
| `idempotency_key` * | Client-generated per attempt |

Notes:

- `INSURANCE` as a payment method is misleading — it is not cash received, it is a receivable. Model insurance settlement through the claims module (`InsuranceClaim` → remittance → payment on approval). Keeping it in the dropdown means invoices show as paid when nothing has been collected, and your cash position is fiction. If it must stay for a co-pay-plus-scheme split, mark those payments `is_receivable = true` and exclude them from shift reconciliation
- **Idempotency is not optional.** A double-tap on a mobile-money confirmation must not record two payments. Unique index on `idempotency_key`; a repeat returns the original payment
- `CHEQUE` should land in `PENDING_CLEARANCE`, not `SETTLED`
- Payment against a `VOID` or `CANCELLED` invoice is rejected
- Emit `PaymentRecorded`; when the outstanding balance reaches zero, emit `InvoiceSettled` — this is what releases the billing gate in §6.2, so it must fire reliably. Consume via the outbox, not a direct call

### 7.5 Outstanding balance

Computing it as `total − SUM(payments)` misses credit notes, write-offs, insurance-approved portions, and refunds. It also means every invoice list page runs an aggregate per row.

**Change:**

```
outstanding = total
            − payments_settled
            − credit_notes
            − write_offs
            − insurance_approved
            + refunds
```

Maintain it as a materialised column on `Invoice`, updated inside the same transaction as any component change, with a nightly reconciliation job that recomputes from source rows and alerts on drift. Trust the derived value for display; trust the ledger for truth.

### 7.6 Cancellation request

As described, and the design is right — receptionist requests, admin approves.

```
InvoiceCancellationRequest(id, invoice_id, requested_by, reason*,
                           requested_at, state, reviewed_by,
                           reviewed_at, review_note)
```

- Reason required, minimum length enforced
- Invoice moves to `CANCELLATION_PENDING` — blocks further payments, remains visible
- One open request per invoice
- On approval: `VOID`, reverse charges, refund any payments through the accountant's refund flow (not automatically)
- On rejection: back to `ISSUED` with the reviewer's note shown to the requester

### ⚠13 — "Delete a voided invoice" must not exist

This contradicts the audit model. A voided invoice is the *evidence* that a number was issued and cancelled; deleting it leaves a gap in the invoice sequence with no explanation, which is exactly the pattern an auditor looks for. Voided invoices stay forever. Hide them behind a filter if the list is cluttered.

---

## 8. Endpoints

```
GET    /receptionist/dashboard

GET    /api/patients/search?q=&limit=
POST   /api/patients                      -- register
POST   /api/patients/emergency            -- ⚠4 fast path
GET    /api/patients/:id
PATCH  /api/patients/:id
GET    /api/patients/:id/visit-history    -- ⚠1 restricted projection
GET    /api/patients/:id/invoices

GET    /api/encounters?state=&date=       -- "here now" panel
POST   /api/encounters                    -- ⚠7 send to triage
POST   /api/encounters/:id/close          -- ⚠8 dismiss; reason required

GET    /api/appointments?from=&to=&provider=&state=
POST   /api/appointments
POST   /api/appointments/specialist       -- ⚠10 gated
POST   /api/appointments/:id/reschedule
POST   /api/appointments/:id/cancel
GET    /api/providers/:id/availability?date=

GET    /api/price-catalog/search?q=&payer_type=&encounter_id=
POST   /api/invoices                      -- creates DRAFT
POST   /api/invoices/:id/issue
GET    /api/invoices/:id
POST   /api/invoices/:id/payments         -- idempotency-key header required
POST   /api/invoices/:id/cancellation-request

POST   /api/cashier-shifts                -- open
POST   /api/cashier-shifts/:id/close
GET    /api/cashier-shifts/current
```

---

## 9. Events

**Emitted:** `PatientRegistered` · `PatientDetailsAmended` · `EncounterOpened` · `EncounterClosed` · `AppointmentBooked` · `AppointmentRescheduled` · `AppointmentCancelled` · `InvoiceIssued` · `PaymentRecorded` · `InvoiceSettled` · `InvoiceCancellationRequested` · `CashierShiftOpened` · `CashierShiftClosed`

**Consumed:** `TriageCompleted` (remove from receptionist's waiting count) · `PatientDischarged` (prompt for settlement) · `InvoiceCancellationApproved` / `Rejected` (notify requester) · `ChargePosted` (refresh outstanding on the desk view)

---

## 10. Concurrency and edge cases

| Case | Handling |
|---|---|
| Two receptionists register the same walk-in simultaneously | Duplicate check on submit inside the transaction, not just on the search screen |
| Patient clicked to triage twice | Partial unique index on open encounter; second call returns the existing one |
| Two clients book the same slot | Server-side authoritative check on submit; availability endpoint is advisory |
| Double-submitted payment | Idempotency key, unique index |
| Invoice edited while a payment posts | Optimistic locking via `version` on `Invoice` |
| Payment recorded during shift close | Shift close takes a lock; late payments rejected with a prompt to open a new shift |
| Patient arrives without their appointment | Search finds it; link on arrival. If none, walk-in encounter with null `appointment_id` |
| Insurance expired at the desk | Warn, offer to update the policy or switch the encounter to `CASH` — never silently price against a dead policy |

---

## 11. Open questions

1. **?** Consultation charge posting — at encounter open, or after triage confirms the patient will be seen? Charging at open means reversing every walk-out. Charging after triage means unpaid triage work.
2. **?** Can a receptionist amend demographics after registration without approval, or does changing a name or national ID need a second signature? (Name changes are a fraud vector on insured patients.)
3. **?** Is `INSURANCE` staying as a payment method, or moving entirely to the claims module per §7.4?
4. **?** Does `due_date` mean anything for cash patients paid at the desk, or is it insurance/corporate only?
5. **?** Appointment reminder SMS — receptionist-triggered or automatic? Affects whether they need a resend action.
6. **?** Is the receptionist allowed to see whether a patient has an outstanding balance from a *previous* encounter, and to refuse service on that basis? Policy question with clinical-ethics implications, not a technical one.
7. **?** Booking horizon — how far ahead can appointments be made, and are there slot types the front desk cannot book (theatre lists, specialist clinics requiring clinical triage)?
