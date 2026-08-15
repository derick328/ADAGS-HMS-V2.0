# HMS — Doctor Module: Navigation & Consultation UI

**Version:** 0.4 delta on `hms-doctor-module-v0.3.md`
**Scope:** sidebar, patient views, catalogue, request history, consultation tab layout

---

## 1. Sidebar

```
My Queue              [6]
My Patients
Hospital Catalogue
Request History       [4]
────────────────────
Profile
```

⚠ **Admitted Patients** and **Surgery Patients** are hidden until those modules are built. Greyed-out nav items train users to stop reading the sidebar. They slot in above the divider when ready.

---

## 2. My Queue

Encounters assigned to this doctor in `AWAITING_CONSULT`:

```sql
ORDER BY (encounter.type = 'EMERGENCY') DESC,
         triage_priority ASC,
         gate_satisfied_at ASC
```

`IMMEDIATE` always first. Row: identity, age/sex, priority badge, triage chief complaint, waiting time, emergency flag.

Live over WebSocket — a patient who pays at reception appears without a refresh.

---

## 3. My Patients

⚠ **Two views, not one list.** As a single list this is ambiguous between "patients I am seeing now" and "every patient I have consulted", and the second grows without bound — a busy doctor reaches several thousand entries within two years, at which point the list is unusable and the query is expensive.

### 3.1 Active

Open encounters assigned to this doctor, in any state: `AWAITING_CONSULT`, `IN_CONSULT`, `AWAITING_RESULTS`, `AWAITING_PAYMENT` for a pending order. Bounded, small, no search needed.

### 3.2 History

**Search-first.** Empty until a query is entered — no default result set.

Search by hospital number, name, or phone. Filters and sorting as specified:

| Control | Options |
|---|---|
| Sort | Date (newest / oldest), Name (A–Z / Z–A), Date and time |
| Filter | Date range, encounter type, diagnosis code |

⚠ Search matters more than sort in practice. A doctor looking for a specific patient types a name; sort order only helps when browsing, which is the rarer case.

Paginated at 25 rows.

### 3.3 Patient detail view

Everything for the patient, across encounters and across clinicians:

```
Amina Hassan · 34F · HN-2026-04412 · NHIF
⚠ Allergies: penicillin – rash (UNCONFIRMED, triage 12 Jul)
Comorbidities: Type 2 diabetes (E11.9)
Pregnancy status: NO (recorded 12 Jul, N. Kimaro)
─────────────────────────────────────────────────────────
Encounters                                    [ timeline ]

▼ 12 Jul 2026 · OUTPATIENT · Dr. J. Mushi · CLOSED
    Triage        priority URGENT · vitals · anthropometrics
    Consultation  HPI, examination, notes
    Diagnosis     B50.9 P. falciparum malaria, unspecified
    Prescription  3 items · dispensed 12 Jul
    Lab           FBC, Malaria RDT · results available
    Disposition   CONSULT_COMPLETE

▶ 04 Mar 2026 · OUTPATIENT · Dr. A. Kessy · CLOSED
▶ 18 Jan 2026 · EMERGENCY  · Dr. J. Mushi · CLOSED
```

**Includes other clinicians' notes.** Continuity requires it — a patient's third malaria presentation this year is only visible if the previous two are readable regardless of who wrote them.

⚠ Allergies pinned to the header, always visible, never in a collapsed section. Every record regardless of confirmation status (v0.3 §6.1).

### 3.4 Access boundary

The `My Patients` scoping **is** the privacy control. A doctor sees the full record of patients they have consulted or are assigned — a legitimate care relationship. Reaching a patient outside that set requires break-glass with a typed justification, logged and alerted (architecture §8.1).

There is no browse-all-patients view for a doctor.

---

## 4. Hospital Catalogue

Read-only reference: procedures, medicines, lab tests.

### 4.1 Availability, not just price

⚠ Price alone is the less useful half. A doctor prescribing an out-of-stock drug wastes the patient's trip and the pharmacist's time explaining; a doctor ordering a test on a broken analyser does the same.

```
┌──────────────────────────────────────────────────────────────┐
│ Search  [ amox|                          ]   Payer: [Cash ▾] │
├──────────────────────────────────────────────────────────────┤
│ Amoxicillin 500mg capsule                          1,200/=   │
│ ● In stock — 2,400 units                                     │
├──────────────────────────────────────────────────────────────┤
│ Amoxicillin 250mg/5ml suspension                   3,500/=   │
│ ◐ Low stock — 12 units                                       │
├──────────────────────────────────────────────────────────────┤
│ Amoxicillin + clavulanic acid 625mg                4,800/=   │
│ ○ Out of stock — expected 14 Aug                             │
└──────────────────────────────────────────────────────────────┘
```

Stock status comes live from inventory. Lab tests carry a `temporarily_unavailable` flag with a reason (analyser down, reagent stockout) and an expected-return date.

### 4.2 Payer-aware pricing

⚠ Prices differ by payer type. The catalogue defaults to cash with a payer toggle, and when opened from inside a consultation it defaults to *that patient's* payer.

Without this, a doctor quotes 20,000/= to an NHIF patient whose invoice reads something else, and the disagreement surfaces at the cashier.

### 4.3 Not editable here

Doctors read; they do not price. Catalogue and price-list management stays with admin and the accountant (`PRICE_LIST_MANAGE`).

---

## 5. Request History

Every lab request and prescription this doctor has raised, with live status.

```
┌──────────────────────────────────────────────────────────────┐
│ Filter: [All ▾] [Last 30 days ▾]         Search patient [  ] │
├──────────────────────────────────────────────────────────────┤
│ 12 Jul 14:52  Amina Hassan   LAB   FBC, Malaria RDT          │
│                              ● Results available             │
├──────────────────────────────────────────────────────────────┤
│ 12 Jul 14:52  Amina Hassan   RX    3 items                   │
│                              ● Dispensed                     │
├──────────────────────────────────────────────────────────────┤
│ 12 Jul 11:20  Joseph Mwita   LAB   Blood culture             │
│                              ◐ In progress — 48h TAT         │
├──────────────────────────────────────────────────────────────┤
│ 11 Jul 16:04  Grace Kimaro   LAB   Lipid profile             │
│                              ⚠ Unpaid — 3 days              │
└──────────────────────────────────────────────────────────────┘
```

### 5.1 Status set

```
PENDING_PAYMENT       charge posted, gate not satisfied
AWAITING_COLLECTION   paid, patient not yet at lab / pharmacy
IN_PROGRESS           specimen collected / partially dispensed
COMPLETED             results verified / fully dispensed
CANCELLED             withdrawn by the doctor or on discharge
EXPIRED               TTL elapsed unpaid
```

⚠ **`PENDING_PAYMENT` beyond a threshold is highlighted.** This is where a doctor discovers a patient never paid for a test he is waiting on — currently there is nowhere that surfaces. Highlighting it turns a silent dead end into an actionable row.

Filterable by status, patient, date, and type. Extends to imaging when added.

---

## 6. Consultation tabs

```
┌──────────────────────────────────────────────────────────────┐
│ [ Consultation ] [ Prescription · 18,000/= ] [ Lab · 29,000/=]│
│                                              Total 47,000/=   │
└──────────────────────────────────────────────────────────────┘
```

### 6.1 Running total

⚠ **The cumulative figure on the tab bar is the most important addition here.** A doctor ordering four tests and three drugs commits the patient to a bill, and in a cash-paying context the patient may not be able to meet it. Nothing currently tells the doctor the number until reception does.

Showing it lets them prioritise — run the malaria RDT now, defer the lipid profile — which is a clinical judgement they can only make with the cost visible. Excluded for `EMERGENCY` and inpatient encounters, where it would be noise.

### 6.2 Shared draft state

All three tabs are one autosaved payload keyed to `(encounter_id, author_id)`, saved server-side every 20 seconds. Switching tabs loses nothing; a device failure resumes elsewhere with all three intact.

### 6.3 Independent of finalize

Orders can be sent before the consultation is finalized. That is the normal path: order labs → send → finalize the note → encounter moves to `AWAITING_RESULTS` → review results → amend `final_diagnosis`.

Sending orders does not finalize the note, and finalizing does not send pending orders. Two separate actions, two separate buttons.

### 6.4 Prescription tab

Formulary typeahead per v0.3 §3. Per item: drug, route, instructions, and either dispense-as-whole or `doses_per_day` × `quantity_per_dose` × `days` with computed total.

- Allergy records rendered inline beside the drug field, all statuses, never collapsed (v0.3 §6.3)
- Stock status shown per item — do not let a doctor prescribe what pharmacy cannot supply
- Triage weight displayed beside the dose field for under-18s
- Quantity sanity ceiling per item, with a warning
- Remove undispensed items before sending

### 6.5 Lab tab

Catalogue typeahead. Per item: test, urgency, instructions. Turnaround time and `temporarily_unavailable` status shown per row.

### 6.6 Send — state the real path

⚠ **"Send to pharmacist" is not what happens for an ordinary outpatient.** The order posts a charge, the patient pays at reception, and only then does it reach the pharmacy worklist. A button labelled "sent to pharmacist" makes the doctor believe it is done, and the patient walks out.

The confirmation must state the actual next step, and it differs by encounter type:

```
Outpatient, gate applies
  ✓ Sent · 3 items · 18,000/=
    Patient must pay at reception before pharmacy dispenses.

Emergency bypass active
  ✓ Sent · 3 items · 18,000/=
    Emergency bypass — pharmacy can dispense now.
    Charges added to the emergency balance.

Inpatient
  ✓ Sent · 3 items · 18,000/=
    Charged to admission deposit. Ward pharmacy notified.
```

One send action per tab, or one combined send — see `?Q2`.

### 6.7 Tab bar extensibility

Imaging and in-room procedures are both missing and both will need a tab. Build the bar to take four or five without redesign rather than hardcoding two.

---

## 7. Open

| | Question |
|---|---|
| `?Q1` | Should `Request History` show only this doctor's requests, or the whole department's? Department-wide helps cover for absent colleagues but widens clinical data access |
| `?Q2` | One combined send for prescription and labs together, or separate per tab? Combined is fewer clicks and one invoice; separate lets the doctor send urgent labs immediately and finish prescribing after |
| `?Q3` | Can a doctor cancel a sent order that is already paid? That is a refund, so it needs the accountant path |
| `?Q4` | Does the catalogue need a "request addition" action for missing items, routed to `CLINICAL_LEAD` or admin? |
| `?Q5` | In `My Patients → History`, should encounters where this doctor was *not* the attending clinician appear at all, or only those they personally handled? |
