# Hospital Management System — System Architecture

**Version:** 0.1 (foundation document)
**Status:** Draft for review

---

## 1. Scope and assumptions

These are stated explicitly because several architectural choices depend on them. Correct any that are wrong and the affected sections are flagged.

| Assumption | Value | Affects |
|---|---|---|
| Facility size | Single hospital, 50–300 beds, multi-branch capable later | §6 arch style, §11 deployment |
| Concurrent users | 50–300 | §6, §11 |
| Deployment | On-premise or hybrid; must survive internet loss | §11, §9.7 |
| Payment mix | Cash, insurance (NHIF and private schemes), corporate accounts | §5.10, §7 |
| Imaging | Out of scope for v1; order + report only, no PACS/DICOM viewer | §5.6 |
| Interop | No external HIE integration in v1, but FHIR-shaped internally | §9.8 |
| Currency | TZS, single currency | §9.4 |

---

## 2. The central design insight

**The whole system is a state machine over one aggregate: the `Encounter`.**

Almost every screen in a hospital system is a filtered view of encounters in a particular state, scoped to a department. Get this right and the rest falls out:

- Reception's queue = encounters in `REGISTERED`
- Triage's worklist = encounters in `TRIAGED_PENDING`
- Doctor's queue = encounters in `AWAITING_CONSULT`, scoped to their department
- Lab's worklist = `LabOrder`s in `PENDING_COLLECTION` / `IN_PROGRESS`
- Pharmacy's queue = `Prescription`s in `PENDING_DISPENSE`
- Ward nurse's board = `Admission`s active on their ward
- Accountant's aging = `Invoice`s in `UNPAID` / `CLAIM_SUBMITTED`

Do not build eleven separate "modules" that each own their own workflow. Build one encounter lifecycle and give each role a scoped projection of it.

### 2.1 Encounter state machine

```
                    ┌──────────────────────────────────────┐
                    ▼                                      │
REGISTERED ──▶ TRIAGED ──▶ AWAITING_CONSULT ──▶ IN_CONSULT ─┤
     │                            ▲                  │      │
     │                            │                  ▼      │
     │                            └──── AWAITING_RESULTS ────┘
     │                                        (lab/imaging)
     │
     ├──▶ LEFT_WITHOUT_BEING_SEEN (terminal)
     │
     └── from IN_CONSULT:
           ├──▶ ADMITTED ──▶ IN_WARD ──▶ DISCHARGE_ORDERED ──▶ DISCHARGED
           ├──▶ REFERRED_OUT (terminal)
           ├──▶ DECEASED (terminal)
           └──▶ CONSULT_COMPLETE ──▶ PENDING_SETTLEMENT ──▶ CLOSED
```

**Rules to enforce in the domain layer, not the UI:**

1. Transitions are guarded — you cannot go `REGISTERED → IN_CONSULT` without triage, except when `encounter.type = EMERGENCY`.
2. `CLOSED` requires zero open orders and a settled or written-off invoice.
3. Terminal states are immutable. Reopening creates a *new* encounter linked by `previous_encounter_id`.
4. Every transition writes an `EncounterEvent` row (append-only): `{encounter_id, from_state, to_state, actor_user_id, occurred_at, reason}`. This is your audit trail, your queue-time analytics, and your debugging tool in one table.

---

## 3. Architecture style — modular monolith

**Recommendation: a modular monolith with explicit module boundaries and in-process domain events.**

### Why not microservices for v1

- The critical transactions are *cross-module by nature*: dispensing a drug must atomically decrement stock, create a dispense record, and post a charge to the invoice. Across services that becomes a saga with compensating transactions — enormous complexity for a hospital of this size.
- On-premise deployment with limited ops staff. One artifact, one database, one backup job.
- Clinical safety: distributed eventual consistency means a nurse can see a medication order that pharmacy hasn't received. That is a patient-safety bug, not an availability tradeoff.

### The structure

```
src/
  modules/
    identity/          # users, roles, permissions, sessions
    patients/          # MPI, demographics, next-of-kin, insurance policies
    scheduling/        # appointments, clinic sessions, provider availability
    encounters/        # visit lifecycle, queues, triage
    clinical/          # notes, diagnoses, allergies, problem list, vitals
    orders/            # CPOE: the abstract order + routing
    laboratory/        # specimens, tests, results, reference ranges
    pharmacy/          # formulary, prescriptions, dispensing
    inventory/         # items, batches, stock movements, stores
    inpatient/         # wards, beds, admissions, MAR, nursing notes
    billing/           # service catalog, price lists, invoices, payments
    insurance/         # schemes, pre-auth, claims, remittance
    reporting/         # read-model queries, exports, statutory reports
    notifications/     # SMS, email, in-app, printer jobs
    audit/             # append-only audit log
  shared/
    kernel/            # value objects: Money, PatientId, DateRange, Quantity
    events/            # domain event bus + transactional outbox
    authz/             # policy engine (see §8)
```

**Module boundary rules — enforce these with a lint rule or ArchUnit-style test, or they will rot:**

1. A module exposes a single public interface file. Nothing else is importable from outside.
2. No module imports another module's ORM entities. Cross-module reads go through the public interface or a read model.
3. No cross-module foreign keys except to `patients` and `encounters` (the two shared reference points).
4. Modules communicate by domain events for anything not needing immediate consistency.

Follow these and extracting `laboratory` or `inventory` into its own service later is a weekend, not a quarter.

### Domain events (the seams)

Emitted in-process, delivered via a transactional outbox so they survive a crash:

```
PatientRegistered, EncounterOpened, TriageCompleted,
DiagnosisRecorded, OrderPlaced, OrderCancelled,
SpecimenCollected, ResultVerified, CriticalResultFlagged,
PrescriptionIssued, MedicationDispensed, StockDepleted,
PatientAdmitted, BedAssigned, MedicationAdministered,
ChargePosted, InvoiceSettled, ClaimSubmitted, PatientDischarged
```

Example of why this matters: `ResultVerified` is consumed by `notifications` (alert the ordering doctor), `encounters` (transition `AWAITING_RESULTS → AWAITING_CONSULT`), and `billing` (post the test charge if not prepaid). None of those three need to know about each other.

---

## 4. Layered view

```
┌─────────────────────────────────────────────────────────────┐
│  Clients                                                     │
│  Web SPA (role-based workspaces) · PWA offline shell ·        │
│  Queue display screens · Label/receipt printers               │
└─────────────────────────────────────────────────────────────┘
                              │ HTTPS / WSS
┌─────────────────────────────────────────────────────────────┐
│  Edge:  reverse proxy · TLS · rate limiting · WAF             │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│  API layer:  REST controllers · WebSocket gateway ·           │
│              request validation · authn · authz policy check  │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│  Application layer:  use-case handlers (one per command),     │
│  transaction boundary, event dispatch, idempotency            │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│  Domain layer:  aggregates, invariants, state machines,       │
│  value objects. No framework, no I/O, unit-testable           │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│  Infrastructure:  repositories · outbox · SMS/email adapters  │
│  · file storage · PDF renderer · scheduler                    │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│  Data:  PostgreSQL (primary) · read replica (reporting) ·     │
│  Redis (cache, queues, sessions) · object store (documents)    │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│  Async workers:  claim submission · SMS dispatch · report     │
│  generation · stock reorder alerts · nightly ETL              │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. Modules in detail

### 5.1 Identity & access
Users, staff profiles, roles, permissions, scope assignments, sessions, MFA, password policy, login audit. See §8 for the authorisation model.

**Non-obvious requirement:** hospitals use shared workstations. You need fast user switching — a short-lived session with a PIN-based re-auth for the same device, plus a hard idle timeout. A nurse should not have to type a full password 40 times a shift, and the previous user's session must not linger.

### 5.2 Patients (Master Patient Index)
Canonical patient record. One `Patient` per human being, ever.

- Hospital number (human-readable, printed on cards), plus internal UUID
- Multiple identifiers: national ID, NHIF card, passport, other-facility numbers
- Fuzzy duplicate detection at registration (name + DOB + phone trigram similarity)
- **Patient merge** — build this in v1, not later. Duplicates happen on day one and merging retroactively across 15 tables is brutal. Design: `patient.merged_into_id` pointer, all reads follow the pointer, nothing is deleted, merge is reversible and audited.
- Next of kin, guardian for minors, consent records

### 5.3 Scheduling
Provider availability, clinic sessions, appointment slots, walk-in vs booked, no-show tracking, specialist referral appointments.

### 5.4 Encounters & queues
The lifecycle in §2. Also owns triage: vitals capture, chief complaint, acuity score (use a published scale — ESI or South African Triage Scale — do not invent one), and priority-based queue ordering.

**Queue ordering is not FIFO.** It is `ORDER BY acuity_level ASC, arrived_at ASC`, and any acuity change re-sorts live. Push updates over WebSocket so waiting-room displays and staff lists stay consistent.

### 5.5 Clinical records
SOAP notes, diagnoses (ICD-10 coded), allergies, problem list, vital signs, procedure notes, sick-leave certificates, discharge summaries.

**Correction model:** clinical records are never updated in place and never deleted. An amendment creates a new version with `amends_id`, `amended_by`, `amendment_reason`; the original remains readable. This is a legal requirement in most jurisdictions and it is much easier to build in from the start than to retrofit.

### 5.6 Orders (CPOE)
One abstract `Order` with typed subtypes: `LabOrder`, `ImagingOrder`, `ProcedureOrder`, `MedicationOrder`. Shared lifecycle:

```
DRAFT → PLACED → [AWAITING_PAYMENT] → ACCEPTED → IN_PROGRESS → COMPLETED
                                                            └→ CANCELLED
                                                            └→ REJECTED
```

The bracketed state is the billing gate. Make it configurable per order type, per payer class, and bypassed for `EMERGENCY` encounters. Hard-coding either policy will bite you.

### 5.7 Laboratory
Test catalog with specimen requirements and turnaround times, panels, worklist, specimen accession numbers with barcodes, result entry, reference ranges by age and sex, abnormal/critical flagging, technician entry vs pathologist verification (two-step for sensitive tests), result amendment, quality control.

**Critical results must page, not just appear in a list.** `CriticalResultFlagged` → notification to the ordering doctor with acknowledgement tracking and escalation on timeout.

### 5.8 Pharmacy & inventory
Formulary, prescription receipt, interaction and allergy checking at prescribe time *and* dispense time, generic substitution with pharmacist approval, partial dispensing, batch and expiry tracking with FEFO issue, returns, controlled-substance register.

Inventory is a separate module because non-drug consumables, reagents, and equipment use the same stock ledger. Model stock as an **append-only movement ledger**, never a mutable `quantity` column:

```
StockMovement(item_id, batch_id, store_id, qty_delta, movement_type,
              reference_type, reference_id, actor_id, occurred_at)
```

Current stock is a sum, materialised into a cached balance table. This gives you a free audit trail and makes stock reconciliation tractable. Enforce non-negative balances with a database-level check on the balance table, not application code.

### 5.9 Inpatient
Ward and bed hierarchy, bed status, admission, transfer, discharge, nursing care plans, medication administration record (MAR) with scheduled dose times and missed-dose reasons, intake/output charts, shift handover notes, daily bed charges posted automatically.

**Bed allocation is a concurrency problem.** Two nurses assigning the same bed simultaneously must not both succeed. Use a partial unique index — `UNIQUE (bed_id) WHERE released_at IS NULL` on `BedAssignment` — so the database rejects the second write. Do not rely on a read-then-write check.

### 5.10 Billing & revenue
Service catalog, multiple price lists (cash / NHIF / private scheme / corporate / staff), charge posting from clinical events, invoices, payments, part-payments, refunds, credit notes, write-offs, cashier shift reconciliation, daily revenue reports.

**Charges are posted by events, not by screens.** `MedicationDispensed` posts a charge; `ResultVerified` posts a charge; a nightly job posts bed-day charges. If the accountant has to manually enter what the doctor ordered, you have built two systems that disagree.

### 5.11 Insurance
Scheme definitions, member verification, benefit and exclusion rules, pre-authorisation requests, claim assembly, batch submission, remittance reconciliation, rejection handling and resubmission.

Note: claims need diagnosis codes and procedure codes. This is the one legitimate reason an accountant sees clinical data — codes, not narrative notes. §8 handles this.

### 5.12 Reporting
Never run reports against the OLTP tables. Read replica plus a nightly ETL into a reporting schema (star-ish: fact tables for encounters, orders, charges; dimensions for patient, provider, department, time). Statutory returns, departmental KPIs, revenue analysis, turnaround times, bed occupancy, stock valuation.

### 5.13 Notifications & audit
Notifications: SMS (appointment reminders, result-ready), in-app, email, printer jobs (labels, receipts, wristbands). All async, all retried, all with delivery status.

Audit: append-only, partitioned by month, capturing **reads as well as writes** for patient data. "Who looked at this patient's record" is a question you will be asked. Every entry: actor, action, resource type and id, patient id, timestamp, IP, user agent, and — for break-glass access — a mandatory justification string.

---

## 6. Role model

### 6.1 The problem with ten hardcoded roles

Your list has substantial overlap:

- `DOCTOR` and `SPECIALIST_DOCTOR` differ by: referral acceptance, procedure privileges, consultation tariff, and clinic scope. Roughly 90% shared.
- `NURSE`, `WARD_NURSE`, `TRIAGE_NURSE` differ by: which worklist they see and whether they can administer medication. Roughly 70% shared.

If you write `if (user.role === 'WARD_NURSE' || user.role === 'NURSE')` you will write that expression in 200 places, and the first time someone needs a nurse who covers both triage and a ward you are stuck.

### 6.2 Recommended model

```
Permission        (code, description)             -- ~150 fine-grained codes
Role              (code, name, is_system)         -- your 10, as named bundles
RolePermission    (role_id, permission_id)
RoleAssignment    (user_id, role_id, scope_type, scope_id, valid_from, valid_to)
```

`scope_type` ∈ `{GLOBAL, DEPARTMENT, WARD, CLINIC, STORE}`. So:

- A ward nurse is `NURSE` role + `MEDICATION_ADMINISTER` permission, scoped to `WARD:B`
- A triage nurse is `NURSE` role scoped to `DEPARTMENT:OPD_TRIAGE` with `TRIAGE_ASSESS`
- A specialist is `DOCTOR` role + specialty permissions, scoped to `CLINIC:CARDIOLOGY`

Your ten roles remain as seeded, named bundles — the admin UI still shows "Triage Nurse" — but the code checks permissions and scope, never role names. Time-bounded assignments (`valid_from`/`valid_to`) handle locums and rotations for free.

### 6.3 Permission matrix

`C` create · `R` read · `U` update · `—` none · `R*` read limited fields only

| Capability | ADMIN | RECEPT | TRIAGE_N | DOCTOR | SPEC_DOC | LAB_TECH | PHARM | ACCT | WARD_N | NURSE |
|---|---|---|---|---|---|---|---|---|---|---|
| User & role management | CRU | — | — | — | — | — | — | — | — | — |
| Facility & catalog config | CRU | — | — | — | — | — | — | R | — | — |
| Price list management | CRU | R | — | — | — | — | R | CRU | — | — |
| Patient registration | R | CRU | R | R | R | R* | R* | R* | R | R |
| Patient merge | CRU | — | — | — | — | — | — | — | — | — |
| Appointments | R | CRU | R | RU | RU | — | — | — | — | R |
| Open / close encounter | R | CU | R | RU | RU | R | R | R | RU | R |
| Triage assessment | R | — | CRU | R | R | — | — | — | CR | CR |
| Vital signs | R | — | CR | CR | CR | — | — | — | CR | CR |
| Clinical notes | —¹ | — | R* | CRU | CRU | — | — | — | R* | R* |
| Diagnosis (ICD-10) | —¹ | — | R | CRU | CRU | R | R | R* | R | R |
| Allergies | —¹ | — | CR | CRU | CRU | R | R | — | CR | CR |
| Lab / imaging orders | R* | R* | R | CRU | CRU | RU | — | R* | R | R |
| Specimen collection | — | — | CR | — | — | CRU | — | — | CR | CR |
| Lab result entry | — | — | — | — | — | CRU | — | — | — | — |
| Lab result verification | — | — | — | R | RU² | U² | — | — | R | R |
| Prescribing | — | — | — | CRU | CRU | — | R | R* | R | — |
| Dispensing | — | — | — | R | R | — | CRU | R* | R | — |
| Stock & batch management | R | — | — | — | — | RU³ | CRU | R | R³ | — |
| Admission & bed assignment | R | R | — | CRU | CRU | — | — | R* | CRU | R |
| Medication administration | — | — | — | R | R | — | R | — | CRU | R⁴ |
| Nursing notes | —¹ | — | CR | R | R | — | — | — | CRU | CRU |
| Discharge | R | R* | — | CU | CU | — | — | R* | RU | R |
| Referral (out / internal) | R | R* | — | CRU | CRU | — | — | — | R | R |
| Invoice & charges | R | R* | — | R* | R* | — | R* | CRU | R* | — |
| Payment collection | R | CR⁵ | — | — | — | — | CR⁵ | CRU | — | — |
| Insurance claims | R | R | — | — | — | — | — | CRU | — | — |
| Refunds & write-offs | R | — | — | — | — | — | — | CU⁶ | — | — |
| Clinical reports | — | — | R* | R | R | R* | R* | — | R* | R* |
| Financial reports | R | — | — | — | — | — | R* | R | — | — |
| Audit log | R | — | — | — | — | — | — | — | — | — |

**Notes**
1. Admin has **no** default clinical read access. Clinical access requires break-glass with justification and generates an alert. An admin who can silently read every patient chart is your worst insider-threat surface.
2. Result verification is a second-person check. The technician who entered a result cannot verify it (enforce `entered_by != verified_by`). Sensitive tests (HIV, histopathology, cultures) require a pathologist or specialist.
3. Lab and ward nurses manage their own sub-store consumables only — scope-limited, not the main pharmacy store.
4. General nurses administer medication only within their assigned scope; a nurse without a ward scope cannot chart a MAR entry.
5. Front-desk and pharmacy cashiering create payments against an open cashier shift; only the accountant reconciles and closes shifts.
6. Refunds and write-offs above a configurable threshold require a second approval. Never single-signature.

### 6.4 What accountants see

The interesting privacy case. Claims require diagnosis and procedure codes, so `R*` on diagnosis means: ICD-10 code and description, yes; SOAP narrative, lab result values, and clinical notes, no. Implement as a projection — a dedicated read model the billing module queries — not as a filter applied to a full clinical query. Filters get forgotten; separate projections cannot leak what they never selected.

---

## 7. Core data model

Abbreviated. `→` denotes a foreign key.

**Patients**
```
Patient(id, hospital_no, given_name, family_name, dob, dob_estimated,
        sex, phone, address, blood_group, photo_url, merged_into_id→Patient,
        created_at, created_by)
PatientIdentifier(id, patient_id→Patient, type, value, issuing_authority)
NextOfKin(id, patient_id→Patient, name, relationship, phone, is_guardian)
InsurancePolicy(id, patient_id→Patient, scheme_id→Scheme, member_no,
                principal_member, valid_from, valid_to, status)
Allergy(id, patient_id→Patient, substance, reaction, severity,
        recorded_by, recorded_at)
```

**Encounter**
```
Encounter(id, patient_id→Patient, type, state, department_id→Department,
          attending_user_id→User, payer_type, insurance_policy_id,
          opened_at, closed_at, previous_encounter_id→Encounter, version)
EncounterEvent(id, encounter_id→Encounter, from_state, to_state,
               actor_user_id, reason, occurred_at)          -- append-only
TriageAssessment(id, encounter_id→Encounter, chief_complaint, acuity_level,
                 triaged_by→User, triaged_at)
VitalSigns(id, encounter_id→Encounter, temperature_c, pulse_bpm,
           systolic_mmhg, diastolic_mmhg, resp_rate, spo2_pct,
           weight_kg, height_cm, recorded_by→User, recorded_at)
```

**Clinical**
```
ClinicalNote(id, encounter_id, author_id→User, note_type,
             subjective, objective, assessment, plan,
             amends_id→ClinicalNote, amendment_reason, signed_at, created_at)
Diagnosis(id, encounter_id, icd10_code, description, certainty,
          is_primary, diagnosed_by→User, diagnosed_at)
```

**Orders**
```
Order(id, encounter_id, order_type, state, priority, ordered_by→User,
      ordered_at, cancelled_at, cancel_reason, version)
LabOrder(order_id→Order, test_id→LabTest, specimen_type)
Specimen(id, lab_order_id, accession_no, collected_by→User,
         collected_at, received_at, rejected_reason)
LabResult(id, lab_order_id, entered_by→User, entered_at,
          verified_by→User, verified_at, comment, amends_id→LabResult)
LabResultValue(id, lab_result_id, analyte_code, value_num, value_text,
               unit, ref_low, ref_high, flag)
Prescription(order_id→Order, drug_id→Item, dose, route, frequency,
             duration_days, quantity, instructions, substitution_allowed)
```

**Pharmacy & inventory**
```
Item(id, code, name, category, form, strength, unit, is_controlled,
     reorder_level, active)
StockBatch(id, item_id→Item, batch_no, expiry_date, unit_cost)
Store(id, name, type, department_id→Department)
StockMovement(id, item_id, batch_id, store_id, qty_delta, movement_type,
              reference_type, reference_id, actor_id→User, occurred_at)
StockBalance(item_id, batch_id, store_id, quantity)   -- materialised, CHECK >= 0
Dispense(id, prescription_order_id→Order, dispensed_by→User, dispensed_at)
DispenseLine(id, dispense_id, item_id, batch_id, quantity, substituted_from)
```

**Inpatient**
```
Ward(id, name, department_id, gender_restriction, bed_count)
Bed(id, ward_id→Ward, label, bed_type, status)
Admission(id, encounter_id→Encounter, admitted_by→User, admitted_at,
          discharge_ordered_at, discharged_at, discharge_type, outcome)
BedAssignment(id, admission_id, bed_id→Bed, assigned_at, released_at)
    -- UNIQUE(bed_id) WHERE released_at IS NULL
MedicationAdministration(id, admission_id, prescription_order_id,
                         scheduled_at, administered_at, administered_by→User,
                         status, omission_reason, dose_given)
NursingNote(id, admission_id, author_id→User, shift, content, created_at)
```

**Billing**
```
ServiceItem(id, code, name, category, department_id)
PriceList(id, name, payer_type, scheme_id, valid_from, valid_to)
PriceListEntry(id, price_list_id, service_item_id, amount_minor, currency)
Invoice(id, encounter_id, patient_id, state, total_minor, paid_minor,
        payer_type, insurance_policy_id, issued_at, settled_at, version)
InvoiceLine(id, invoice_id, service_item_id, description, quantity,
            unit_price_minor, amount_minor, source_event_type, source_event_id)
Payment(id, invoice_id, amount_minor, method, reference, received_by→User,
        cashier_shift_id, idempotency_key, received_at)
CashierShift(id, user_id→User, opened_at, closed_at, expected_minor,
             counted_minor, variance_minor, reconciled_by→User)
InsuranceClaim(id, invoice_id, scheme_id, claim_no, state, submitted_at,
               approved_minor, rejected_reason, batch_id)
```

**Identity & audit**
```
User(id, username, email, phone, password_hash, mfa_secret, status,
     must_change_password, last_login_at, failed_attempts, locked_until)
StaffProfile(user_id→User, employee_no, full_name, professional_reg_no,
             qualification, specialty, signature_image_url)
Role(id, code, name, is_system)
Permission(id, code, description)
RolePermission(role_id, permission_id)
RoleAssignment(id, user_id, role_id, scope_type, scope_id,
               valid_from, valid_to, granted_by→User)
AuditLog(id, actor_user_id, action, resource_type, resource_id,
         patient_id, is_break_glass, justification, ip, user_agent,
         occurred_at)                              -- partitioned by month
```

---

## 8. Security & authorisation

### 8.1 Three-layer check

Every request passes all three:

1. **Authentication** — valid session, not expired, not locked
2. **Permission** — does the actor hold the required permission code?
3. **Scope + relationship** — is the target resource within the actor's scope, *and* does a care relationship exist?

Layer 3 is what most implementations skip and it is the one that matters. A doctor holding `CLINICAL_NOTE_READ` should not be able to read every patient in the hospital — only patients with an active encounter in their department, patients they are attending, or patients they have been referred. Everything else is break-glass.

```
allow(actor, action, resource) =
    hasPermission(actor, action)
    AND withinScope(actor, resource)
    AND (hasCareRelationship(actor, resource.patient)
         OR breakGlass(actor, resource, justification))
```

Break-glass is not a bug — emergencies are real. It must be: available in one click, require a typed justification, log loudly, and generate an alert to the compliance role. Availability with accountability, not prevention.

### 8.2 Other controls

- Password hashing: argon2id. MFA mandatory for `ADMIN` and `ACCOUNTANT`.
- Sessions: short-lived access token, rotating refresh token, device binding, hard idle timeout, plus PIN re-auth for shared-workstation switching.
- Transport TLS everywhere including internal hops. Encryption at rest on the volume; column-level encryption for national ID and insurance member numbers.
- **No PHI in logs, ever.** Log patient UUIDs, never names. Add a log scrubber and test it.
- Idempotency keys required on payments, dispensing, and medication administration. A double-tap on a "confirm dose" button must not chart two doses.
- Optimistic locking (`version` column) on `Encounter`, `Invoice`, `Order`, `Admission`.
- Rate limiting on login and on patient search (patient search is the classic data-exfiltration vector — a curious employee paging through every record).
- Backups: nightly full plus continuous WAL archiving, encrypted, off-site, and **restore-tested monthly**. An untested backup is not a backup.

---

## 9. Cross-cutting concerns

### 9.1 Money
Integer minor units (`amount_minor BIGINT`) plus a currency code. Never floating point. A `Money` value object in the shared kernel; no raw arithmetic on amounts anywhere else.

### 9.2 Time
Store UTC with timezone awareness; render in facility-local time. Clinical timestamps are legally significant — record both `occurred_at` (when the thing happened, user-editable within limits) and `recorded_at` (when it was entered, system-set, immutable). Retrospective charting is normal in wards; treating them as the same field loses information.

### 9.3 Deletion
Clinical and financial data is never hard-deleted. Cancellation and amendment only, with reason and actor. Non-clinical reference data can be soft-deleted via an `active` flag.

### 9.4 Concurrency
Database-level constraints over application checks wherever expressible: partial unique index for bed occupancy, `CHECK (quantity >= 0)` on stock balances, unique index on accession numbers and invoice numbers. Application-level guards are advisory; the database is the arbiter.

### 9.5 Numbering
Hospital numbers, accession numbers, invoice numbers, and claim numbers are human-facing and must be gapless and unique per facility per year. Use a dedicated sequence table with row-level locking, not a UUID and not `MAX(n)+1`.

### 9.6 Flexible clinical forms
Specialty departments will want their own assessment forms and you cannot ship a migration per request. Use a form-definition table plus a `JSONB` response column, with the definition versioned so old responses still render correctly. Keep the *core* clinical fields (vitals, diagnoses) as real columns — those are queried and reported on constantly.

### 9.7 Offline tolerance
Not full offline-first — the conflict resolution cost is not worth it for most of the system. Targeted approach:

- **Append-only data works offline**: vitals, nursing notes, MAR entries, triage assessments. Queue locally in IndexedDB, sync on reconnect, conflicts are impossible because nothing is overwritten.
- **Mutable and safety-critical data does not go offline**: prescribing, dispensing, bed assignment, payments. These require a live connection. A stale drug interaction check is a patient-safety hazard.
- Read caching of the current patient's chart is fine and useful.

### 9.8 Standards
Adopt vocabularies now even without external integration — retrofitting coded data onto free text is a data-migration nightmare.

- Diagnoses: ICD-10 (ICD-11 where mandated)
- Lab analytes: LOINC
- Drugs: ATC classification plus a local formulary code
- Procedures: ICD-9-CM or CPT depending on payer requirements
- Interop shape: model your resources close to FHIR R4 (`Patient`, `Encounter`, `Observation`, `ServiceRequest`, `MedicationRequest`, `DiagnosticReport`). Even if you never expose a FHIR API, the modelling is well-tested and the day a ministry or insurer requires it, you are close.

---

## 10. Technology recommendations

Language choice matters less than discipline. Any of these works; pick by team familiarity.

| Concern | Recommendation | Notes |
|---|---|---|
| Backend | NestJS + TypeScript, or Spring Boot, or ASP.NET Core | Needs first-class transactions, DI, and a mature migration tool. NestJS if the team is TS-fluent — shared types with the frontend is a real productivity win here |
| Database | PostgreSQL 15+ | Non-negotiable choice for this domain: JSONB for flexible forms, partial indexes for the concurrency constraints, table partitioning for audit, row-level security available if needed |
| ORM | Prisma or TypeORM / JPA / EF Core | Whatever it is, own your migrations and never auto-sync a schema |
| Cache / queue | Redis + BullMQ | Sessions, queue projections, WebSocket fan-out, background jobs |
| Search | Postgres trigram + full-text first | Only add OpenSearch if patient search genuinely outgrows it. It probably will not at this scale |
| Frontend | React + TypeScript, Vite, TanStack Query | Separate workspace shell per role, lazy-loaded. Do not build one dashboard with 40 conditional blocks |
| Realtime | WebSocket (Socket.IO or native) | Queues, critical result alerts, bed board |
| Files | S3-compatible; MinIO for on-premise | Scans, consent forms, signed documents |
| Auth | Keycloak if you want it off-the-shelf, else own it | Owning it is defensible here — the scope/relationship logic in §8.1 is domain-specific and awkward to express in a generic IdP |
| Reporting | Read replica + nightly ETL; Metabase for ad-hoc | Do not let anyone point BI at the primary |
| Observability | OpenTelemetry, Prometheus, Grafana, structured JSON logs | Alert on queue depth, order-to-result time, failed claim submissions |
| Testing | Domain unit tests, module integration tests on a real Postgres, Playwright for the critical journeys | Register → triage → consult → order → result → dispense → settle must be an automated E2E test |

---

## 11. Deployment

**Primary: on-premise with cloud DR** — the realistic choice when internet connectivity is unreliable and the hospital cannot stop admitting patients because a link is down.

```
Hospital site
  ├── App server(s)          Docker Compose or single-node k3s
  ├── PostgreSQL primary     local NVMe, streaming to standby
  ├── PostgreSQL standby     separate physical machine
  ├── Redis
  ├── MinIO                  documents
  ├── Reverse proxy          TLS termination, internal CA
  └── UPS + generator        the database will corrupt on dirty shutdown

Cloud (async)
  ├── Encrypted off-site backups (WAL shipping when link is up)
  ├── Log and metric aggregation
  └── Optional read-only DR replica
```

Environments: local (docker-compose) → staging (production-shaped, anonymised data) → production. Blue-green or rolling deploys; a hospital has no maintenance window.

**Multi-facility:** if this becomes a product, go single-database-with-`facility_id` plus Postgres row-level security before you go database-per-tenant. Per-tenant databases multiply your migration and backup surface by the number of customers.

---

## 12. Delivery phases

Each phase ends with something a real department can use in production. Resist building all the schema up front and all the UI at the end.

| Phase | Contents | Exit criterion |
|---|---|---|
| **0. Foundation** | Auth, users, roles, permissions, scopes, audit log, shared kernel, CI/CD, migrations, seed data | An admin can create a receptionist who can log in and do nothing else |
| **1. Front office** | Patient MPI, duplicate detection, merge, encounter open/close, queue, appointments | Reception runs live; paper register retired |
| **2. Clinical core** | Triage, vitals, SOAP notes, ICD-10 diagnoses, allergies, order entry | A patient goes registration → triage → consultation entirely in-system |
| **3. Diagnostics & pharmacy** | Lab catalog, worklist, results, verification, critical alerts; formulary, prescribing, dispensing, stock ledger | Lab and pharmacy retire their standalone registers |
| **4. Revenue** | Service catalog, price lists, event-driven charge posting, invoices, payments, cashier shifts, insurance claims | Finance closes a month from the system |
| **5. Inpatient** | Wards, beds, admission, transfer, MAR, nursing notes, bed charges, discharge summary | A full admission-to-discharge cycle runs in-system |
| **6. Intelligence** | Reporting schema, ETL, dashboards, statutory returns, SMS notifications | Management stops asking for manual spreadsheets |

**Phase 0 is not optional and not fast.** Every later phase depends on the permission model in §6.2. Building it after three modules exist means retrofitting authorisation into code that already assumes a role string, and that refactor is the most common way projects of this shape stall.

---

## 13. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Role logic hardcoded on role names | Every new role variation becomes a code change across the codebase | §6.2 permission model, enforced from phase 0 |
| Retrofitting the audit trail | Legal and compliance exposure; near-impossible to backfill | Append-only event tables from phase 0 |
| Billing and clinical disagreeing | Revenue leakage, patient disputes | Event-driven charge posting only (§5.10); no manual charge entry path |
| Duplicate patient records | Fragmented clinical history — a genuine safety risk | Fuzzy matching plus merge in phase 1, not later |
| Stock counts drifting from reality | Stockouts of critical drugs | Append-only movement ledger, DB-level non-negative constraint, periodic physical count reconciliation |
| Users sharing logins | Audit trail becomes worthless | Fast user switching (§5.1) — make correct behaviour easier than the workaround |
| Untested backups | Total data loss | Monthly restore drill, treated as a release gate |
| Scope creep into imaging/PACS | Phase 3 never ships | Explicitly out of scope for v1; order and text report only |

---

## 14. Open decisions

These need answers before detailed design of the affected modules:

1. **Billing gate policy** — pay-before-service, bill-at-discharge, or configurable per payer? Affects the order state machine directly.
2. **Insurance schemes in scope** — which specific payers, and do they have an electronic claim API or is submission manual/batch?
3. **Imaging** — order-and-report only, or eventual PACS integration? Changes whether you need a DICOM boundary.
4. **Multi-facility** — single hospital forever, or a product? Determines whether `facility_id` goes into the schema from day one (much easier now than later).
5. **Existing systems** — is there data to migrate, or a lab analyser or biometric device to integrate with?
6. **Regulatory** — which data-protection regime applies, and what are the clinical record retention requirements?
7. **Language** — single language, or does the UI need localisation? Affects the schema for catalog descriptions.
