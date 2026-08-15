# HMS — Problem Register

Every issue raised during design, by module, with its resolution.

**Legend:** ✅ resolved · ⏳ open · ⚠️ resolved against my recommendation (noted so the trade-off is visible)

---

## 1. Architecture

| # | Problem | Status |
|---|---|---|
| A1 | Ten roles with heavy overlap (`DOCTOR`/`SPECIALIST_DOCTOR` ~90%, the three nurse roles ~70%). Hardcoding role names means writing the same conditional in 200 places | ✅ Permission-plus-scope model. Roles are named permission bundles; code checks permissions, never role names |
| A2 | Billing gate placement undecided — pay-before-service vs bill-at-discharge changes the entire order state machine | ✅ Gates consultation entry, lab collection, pharmacy dispensing, procedure start. Never blocks discharge |
| A3 | `CLOSED` required a settled invoice — a discharge block in disguise | ✅ Removed. Clinical completeness and financial settlement are independent |

---

## 2. Receptionist

| # | Problem | Status |
|---|---|---|
| R1 | "View full profile → triage history" exposes chief complaint, vitals, and acuity to a non-clinical role | ✅ Restricted `VisitHistoryView` projection — dates, departments, provider names, billing status only |
| R2 | Dashboard showed lifetime counters ("total patients"), which are neither actionable nor fast | ✅ Today-scoped, clickable tiles |
| R3 | Mandatory exact DOB → staff type `01/01/1980` for anyone who only knows their age, corrupting age-banded lab ranges and paediatric dosing | ✅ Age accepted with `dob_is_estimated` flag |
| R4 | Mandatory next-of-kin blocks registering an unconscious casualty | ✅ Emergency fast-path with provisional identity |
| R5 | Flat insurance fields (provider, policy no, member no as text) cannot answer "is this policy valid?" or "who is the principal?" — both needed for claims and the specialist gate | ✅ `InsurancePolicy` with scheme FK, validity dates, verification status |
| R6 | "Idle patients" = every patient ever registered minus a few dozen. Unusable and expensive at 40,000 patients | ✅ Split into bounded "Here now" + search-only "Find a patient" |
| R7 | Send-to-triage created a `TRIAGE` appointment. An appointment is a future intent; an encounter is a visit in progress. Conflating them means no object represents "in the building now" | ✅ Opens an `Encounter`. `TRIAGE` removed from the appointment enum |
| R8 | Dismiss set `patient.active = false` — deactivating a human because they left the waiting room. Next visit fails lookup or creates a duplicate | ✅ Closes the encounter, never the patient |
| R9 | Double-booking check existed only in the specialist modal; standard bookings had none | ✅ One `AppointmentBookingPolicy` in the domain layer |
| R10 | Specialist gate checked "triage referral's invoice OR medical record's invoice" — a third referral source silently passes or fails | ✅ One `BillingGate.isSatisfied()` |
| R11 | Invoices floated on the patient with no encounter link — cannot answer "what did this visit cost" | ✅ `encounter_id`, nullable for standalone cases |
| R12 | Free-text line descriptions + hand-typed prices → "FBC", "Full Blood Count", "CBC" as separate services at different prices per receptionist | ✅ Catalogue-bound, price resolved server-side from the payer's price list |
| R13 | "Delete a voided invoice" — a gap in the invoice sequence with no explanation is what an auditor looks for | ✅ Never deleted; dismissible from the user's own list only |
| R14 | No cashier shift — "the drawer is short 40,000/=" had no answer | ✅ `CashierShift` added; every payment belongs to one |
| R15 | Invoice issued immediately on save — every typo becomes a permanent financial document | ✅ Two-step: `DRAFT` then explicit issue |

---

## 3. Triage

| # | Problem | Status |
|---|---|---|
| T1 | `IMMEDIATE` priority + pay-before-consultation = a dying patient queued at a cashier | ✅ `IMMEDIATE` → `EMERGENCY` type → bypass, straight to `AWAITING_CONSULT` |
| T2 | Free-text allergies cannot be matched by any automated check — prescribing and dispensing silently return clean | ⚠️ Kept as free text. Mitigation: allergy text rendered inline and unmissable on the prescribing and dispensing screens; the check is human |
| T3 | Chief complaint optional, and duplicated by "current symptoms" — data splits across two columns permanently | ⏳ Both kept as plain text. Overlap unresolved |
| T4 | Nurse-entered BMI can contradict weight and height (70kg, 1.70m, BMI 31) | ✅ Computed only, never stored as an editable field. Suppressed under 18 |
| T5 | Referral optional but sets the consultation price — a blank referral means no charge and nothing for the gate to check | ✅ Mandatory |
| T6 | Hardcoded adult vital thresholds — a neonate's normal respiratory rate reads as severe distress | ✅ `VitalReferenceRange` as data, age-banded |
| T7 | `infection_risk` boolean with no consequence — nurse ticks it, patient sits in general waiting anyway | ⏳ Proposed: capture what is suspected, set an isolation flag |
| T8 | Missing AVPU — the strongest single predictor of deterioration, one enum | ⏳ Proposed, not adopted |
| T9 | Missing conditional blood glucose — hypoglycaemia mimicking neurological emergency | ⏳ Proposed, not adopted |
| T10 | Missing pregnancy status | ✅ Added, females 12–50, auto-appearing |
| T11 | Missing MUAC for under-5s | ⏳ Proposed, not adopted |
| T12 | All-mandatory vitals → fabricated numbers (height on a stretcher patient, BP on a screaming infant), which read downstream as real measurements | ⏳ Proposed "unable to obtain" escape with reason, not adopted |
| T13 | No re-assessment of long-waiting patients — deterioration in the waiting room is the classic triage failure | ⏳ Not adopted |
| T14 | The triage nurse makes a 30,000/= routing decision with no governance | ⏳ Partially — fee shown on the picker; routing rules and override reporting proposed, not adopted |

---

## 4. Doctor

| # | Problem | Status |
|---|---|---|
| D1 | Ward and surgical discharge blocked by unpaid bills — detention for debt | ✅ Never blocked. Balance becomes a receivable |
| D2 | Consultation payment guard contradicted the billing rules, which said payment must not block consultation | ✅ Gates *entry*; does not interrupt in progress or block discharge. Bypass exempted |
| D3 | Inpatient pharmacy gated per dose — nobody is at the cashier for a 02:00 IV antibiotic | ✅ Inpatients accrue; deposit model |
| D4 | Free-text lab tests and procedures have no price, specimen type, container, TAT, or result structure — unprocessable and unchargeable | ✅ Catalogue-backed with typeahead, so it feels like free text and stores an ID |
| D5 | `price` on the surgery order — doctors setting prices | ✅ Catalogue + price list |
| D6 | Free-text diagnosis → no insurance claims, no morbidity reporting, no disease surveillance, and no retrofit path | ✅ ICD-10 coded with free-text clarifier |
| D7 | Consultation form re-collected eight triage fields → two sources of truth, most dangerously for allergies | ✅ Split: inherited read-only, patient-level amendable, re-assessed as new records |
| D8 | Bed assignment absent from admission — no occupancy tracking, no double-assignment protection | ✅ `BedAssignment` with partial unique index |
| D9 | "Assigned nurse" as one field cannot survive a multi-day admission across three shifts | ✅ Roster-derived |
| D10 | Finalize conflated with closing the encounter — a note cannot be finalized while labs are pending | ✅ Separated |
| D11 | Missing: sick-leave certificates, imaging orders, `REFERRED_OUT`, `DECEASED`, vitals recheck, procedure notes | ⏳ Noted, mostly not adopted |
| D12 | Incomplete consultations invisible — patient paid, no clinical record exists | ✅ 12-hour prompt, 48-hour escalation, abandon path with reasons |
| D13 | The incomplete prompt could hard-block a doctor while an `IMMEDIATE` patient waits | ✅ Dismissible; suppressed entirely when an emergency is queued |
| D14 | `AWAITING_RESULTS` encounters would appear as "incomplete", producing a list nobody can clear | ✅ Excluded from all three thresholds |

---

## 5. Clinical reference data

| # | Problem | Status |
|---|---|---|
| C1 | Full ICD-10 is ~14,000 codes — typing "mal" returns malaria, malnutrition, malignant neoplasms thirty rows deep | ✅ `is_common`, department-scoped, clinician-curated |
| C2 | ICD category headers (`B50`) are not valid diagnoses; selecting one produces claims insurers reject | ✅ `is_billable` filter |
| C3 | Clinicians name conditions in Swahili and local shorthand, not ICD vocabulary | ✅ `Icd10Synonym` |
| C4 | An incorrect synonym silently produces wrong diagnoses for every user afterwards, invisibly | ✅ Propose-and-approve; `CLINICAL_LEAD` reviews |
| C5 | Curating the common list by guesswork produces a list nobody uses | ✅ `ReferenceSearchLog` drives suggestions from actual usage |
| C6 | ICD titles are revised between releases; a 2026 record must read as it did in 2026 | ✅ Title snapshotted on the diagnosis row |

---

## 6. Pharmacist

| # | Problem | Status |
|---|---|---|
| P1 | Billing gate enforced by which queue an item appeared in — a UI check, defeated by a stale page or a direct call | ✅ Server-side on `POST /dispenses`, evaluated live |
| P2 | No batch number on restock → a manufacturer recall cannot identify affected stock or recipients | ✅ Mandatory |
| P3 | One batch selector cannot fulfil 30 tablets when batch A holds 20 and batch B holds 10 | ✅ Repeatable `DispenseLine` rows |
| P4 | Free batch choice → fresh stock dispensed while near-expiry stock sits and is written off | ✅ FEFO pre-selected, override with reason |
| P5 | Expired batches only flagged on a report, not blocked | ✅ Hard-blocked, quarantined, write-off requires approval |
| P6 | One `price` field on the item — cost and selling price are different things at different levels, and one field cannot hold per-payer prices | ✅ `unit_cost` on the batch, selling price on the price list |
| P7 | No out-of-stock or substitution path — the patient stands at the counter while someone makes phone calls | ✅ Both flows specified |
| P8 | No dispensing labels — a patient handed unlabelled tablets is a dosing error waiting to happen | ✅ Added, including batch number for recall traceability |
| P9 | Pharmacist could create a catalogue item at an arbitrary price directly, bypassing price control from the other direction | ✅ New items go through the same approval queue as price changes |
| P10 | Bulk Excel upload is the fastest way to corrupt a catalogue | ✅ Six safeguards: template, dry-run, all-or-nothing, duplicate detection, audit, price routing |

---

## 7. Controlled medicines

| # | Problem | Status |
|---|---|---|
| M1 | Controlled substances absent entirely — no register, witness, or reconciliation | ✅ Fail closed: excluded from catalogue and dispensing in v1 |
| M2 | A boolean `is_controlled` cannot fail closed — an unset field reads `false`, so any unclassified item is silently dispensable | ⚠️ Three-state model specified, then `UNCLASSIFIED` set to behave as not-controlled. **The original risk is reintroduced**: an unclassified pethidine dispenses normally. Mitigation is the §M5 detection report |
| M3 | "No medicine without a doctor's prescription" was proposed as covering controlled drugs. It does not — the risk is diversion inside the pharmacy, which a prescription check cannot see | ✅ Distinction accepted |
| M4 | I assumed diazepam and midazolam were controlled. Under Tanzanian regulation they are not — reg 16(3) places moderate/high-value psychotropics in Prescription Only | ✅ Corrected. The blocked list is much narrower: opioids plus ketamine |
| M5 | Someone will add "Tramadol" classified as not-controlled to get it dispensable | ⏳ Monthly misclassification report proposed, excluding moderate-value psychotropics to avoid false positives |
| M6 | Controlled drugs given on paper leave the medication history silently incomplete — a doctor may prescribe as though none were given | ⏳ `ControlledMedicationNote` proposed |
| M7 | Ketamine is on the narcotic schedule and is a common regional anaesthetic — the item most likely to make fail-closed bite | ⏳ Unconfirmed whether the hospital uses it |

---

## 8. Laboratory

| # | Problem | Status |
|---|---|---|
| L1 | No verification step — one technician enters and releases; a typo on a blood group or crossmatch is final | ✅ Model built (levels, `entered_by != verified_by`, snapshot). Results release immediately; verification is retrospective when a verifying role exists |
| L2 | Critical results relied on a doctor opening a screen — a potassium of 7.0 cannot wait | ✅ Push notification, acknowledgement tracked, escalation to `CLINICAL_LEAD` |
| L3 | Parameter names and reference ranges typed per result → `Hb`/`HB`/`Haemoglobin` are four parameters, no trending, ranges differ by shift, flags are human judgement | ✅ Form generated from the catalogue; tech enters values only, flags computed |
| L4 | No accession number — tubes identified by handwritten names, the mechanism behind most catastrophic lab errors | ✅ Gapless accession, barcode, scanned at result entry |
| L5 | Sample quality recorded but nothing happened; V26 assessed it *after* processing, when reagents were already spent | ✅ Quality at accept in both pipelines, with a rejection flow. Recollection not re-charged |
| L6 | Two parallel pipelines → two forms, two result models, two of every downstream consumer; three divergences already visible in the spec | ✅ Anti-corruption layer so the technician sees one shape |
| L7 | Machine PDFs stored in the database — bloats Postgres, slows backups | ✅ Object store, key in the database |
| L8 | Reagent lot as free text cannot answer "which results used lot 24-0817?" when a lot is found faulty | ✅ Linked to the stock batch |
| L9 | Retrospective verification impossible without the original analyser output | ✅ Machine output capture mandatory from day one |
| L10 | A "verify everything eventually" queue cannot be emptied and gets ignored | ✅ Split: short clearable clinical window, plus sampled quality audit for older results |

---

## 9. Ward nurse

| # | Problem | Status |
|---|---|---|
| W1 | `DISCHARGED` deactivated the patient record | ✅ Closes the encounter only |
| W2 | Nurse created surgery orders and set prices — contradicting the module's own "doctor orders, nurse executes" principle | ✅ Ordering returned to the doctor; prices from the catalogue |
| W3 | Changing ward voided an `ISSUED` invoice — an unaudited financial action fired by a clinical one, and it mis-bills the earlier days | ✅ Nightly bed-day accrual; no void, no reissue |
| W4 | MAR fields typed freehand → wrong-drug risk, and **a missed dose creates no row, so it is invisible** | ✅ Slots generated from the prescription; drug and route read-only; missed doses appear as overdue |
| W5 | Administration decremented a counter on the prescription, bypassing the stock ledger — no batch traceability, probable double-counting | ✅ Per-patient dispensing from pharmacy; batch from the dispense record |
| W6 | `bedNumber` as free text — nothing prevents two patients in bed 4 | ✅ `Bed` rows with a partial unique index |
| W7 | Three separate vitals tables (triage, ward, post-op) — no single query shows a patient's observation trend | ✅ One table with a `context` marker |
| W8 | Alert thresholds hardcoded and adult-only | ✅ `VitalReferenceRange` |
| W9 | `hasAlerts` was set and nobody was told | ✅ Notification with acknowledgement and escalation |
| W10 | No shift handover — the highest-risk moment in ward nursing | ✅ SBAR handover, pre-populated, acknowledged by the incoming nurse |

---

## 10. Admin

| # | Problem | Status |
|---|---|---|
| N1 | Admin creates an expense and approves it; `Bulk-disburse` moves a month of payroll in one click. The classic embezzlement path | ✅ `created_by != approved_by` at the database; roles to whoever is responsible |
| N2 | Admin held every permission including clinical read — the account most likely to be shared, reading every patient's HIV status with no record of why | ✅ **Clinical read removed entirely.** Break-glass only, alerting `CLINICAL_LEAD` |
| N3 | `User.role` as a single enum breaks `CLINICAL_LEAD` (designed as additive) and has no scope, so a ward nurse sees every ward | ✅ `RoleAssignment` with scope and validity dates |
| N4 | Delete on catalogue items and in-place price edits — past invoices reference items permanently, and editing a price rewrites history | ✅ Deactivate; prices versioned by validity |
| N5 | Reject cancellation → `TERMINATED`, which may kill the invoice — making rejection identical to approval and silently losing revenue | ✅ Rejection returns it to `ISSUED`, collectable; reconsideration raises a new request |
| N6 | Payroll had no statutory deductions | ✅ `DeductionRule` driven by `employment_type`; interns exempt. **Verify the intern exemption with an accountant** — PAYE may depend on stipend amount, not job title |
| N7 | No audit log or facility configuration in the navigation, and no other role can hold them | ✅ Added |
| N8 | Nothing audits the admin — admin reads the log | ⏳ Proposed: alerts route elsewhere, plus a monthly out-of-band summary to management |

---

## 11. Billing

| # | Problem | Status |
|---|---|---|
| B1 | The billing rules said payment must not block consultation; the doctor module required a hard guard | ✅ Gates entry only |
| B2 | "Don't cache the gate result" and "bypass authorisation persists" contradict each other unless the gate reads provenance | ✅ `authorization_id` on the line — durable, set at posting |
| B3 | `ServiceAuthorization` duplicated `posted_under_bypass_id` — two records for one fact | ✅ `ServiceAuthorization` wins |
| B4 | Line-level gating with invoice-level payments — nothing decides which line a partial payment satisfies. Patient pays for the test they need, money lands on the consultation, they are turned away | ✅ `PaymentAllocation`, receptionist-directed, exact amounts only |
| B5 | Follow-up visits would be charged a second consultation fee, so staff would wave patients through and the data would stop matching reality | ✅ Resuming for results is the same encounter, free. Booked appointments are new encounters, full price |
| B6 | `close()` had no path when a clinician never marks completion — encounters accumulate forever | ⚠️ No sweep, nothing deleted. Active views must filter by date instead |
| B7 | Procedures listed as gated with no timing rule | ✅ Gate at procedure start, before the pack is opened |
| B8 | Consent as a checkbox is not consent, and blocking at billing means the patient pays then cannot proceed | ✅ Signed document required; blocks at scheduling |
| B9 | Zombie orders after an unpaid discharge sit in `AWAITING_PAYMENT` forever | ✅ Cancel on discharge, plus TTL expiry — both, since they cover different cases |
| B10 | Results arriving after consultation closure had no owner | ✅ Ordering doctor owns; ungiven-results list with escalation |
| B11 | Insurance treated as a payment method makes invoices show paid when nothing was collected | ✅ Removed from the dropdown; coverage is a receivable |
| B12 | Ward transfer rate ambiguity | ✅ Private wins the day; highest rate among overlapping assignments |
| B13 | Same-day admission would bill zero days | ✅ 4-hour grace, then `+1` gives a one-day floor |
| B14 | Discharge delays caused by the hospital would bill the patient an extra day | ✅ Billing stops at the discharge order, with a 4-hour departure grace |

---

## 12. Ward nurse actions with billing consequences

| # | Problem | Status |
|---|---|---|
| V1 | Discharge order stops the billing clock — a clinical role reduces a bill with no approval | ✅ Doctor, specialist, or that doctor's assigned nurse. Orderer recorded and reported monthly |
| V2 | Bed choice sets the rate — a nurse placing a patient in a free private bed bills the private rate | ✅ Admitting doctor specifies the ward class; nurse picks within it; overrides need a reason |
| V3 | **Per-patient dispensing charges the full course upfront. A patient discharged after 6 of 15 doses paid for 9 they never received** | ✅ Oral goes home, charge stands. Parenteral returns with a credit note |
| V4 | Post-procedure admission clock start undefined | ✅ Starts when sent to admission; recovery time is part of the procedure |

---

## 13. Still open

| Area | Question |
|---|---|
| Triage | T3, T7–T9, T11–T14 — chief complaint duplication, AVPU, glucose, MUAC, unable-to-obtain escape, re-assessment, routing governance |
| Controlled | Does the hospital use ketamine? Who is the pharmacist-in-charge? Is the misclassification report being built? |
| Lab | Verification window and audit sample rate, when a verifying role exists |
| Ward | Which medicines need an administration witness; nursing care plans in v1? |
| Admin | How many admin accounts (self-elevation approval needs two)? External recipient for the activity summary? Does `PRICE_LIST_MANAGE` stay with admin? |
| Billing | Date-filter window for active views (no auto-close); opened parenteral vials — return or write off? |
| Ward nurse | Does the assigned nurse discharge independently, or record a doctor's verbal decision? |
| Cross-cutting | Multi-facility — one hospital or several? Determines whether `facility_id` enters the schema now |

---

## 14. The three worth revisiting

Stated once, and left to your judgement.

**M2 — `UNCLASSIFIED` behaving as not-controlled.** The three-state model existed so an unclassified item could never be dispensed by accident. With `UNCLASSIFIED` dispensable, someone adding pethidine on a busy afternoon without classifying it creates exactly the unregulated electronic path the fail-closed policy was written to prevent. The monthly detection report is a slower substitute for a block.

**T2 — free-text allergies.** Automated interaction and allergy checking is permanently off the table. The compensating control is that allergy text renders inline and unmissable at prescribing and dispensing — if that ever gets moved into a collapsed panel or a tab, there is no check at all.

**B6 — no stale-encounter sweep.** Nothing is deleted, which is right. But without any closure mechanism, every "who is in the building" view depends on a date filter being correctly applied everywhere. One view that filters on state alone will show patients who left months ago.
