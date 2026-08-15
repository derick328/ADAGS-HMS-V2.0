# HMS — `CLINICAL_LEAD` Role Specification

**Version:** 0.1
**Status:** Confirmed — eleventh role
**Resolves:** `clinical-reference ?Q1`, `triage §7.10`, `triage §7.11`, `triage §5.2`, `doctor v0.3 ?Q5`, `doctor v0.3 §8.2`

---

## 1. Nature of the role

⚠ **`CLINICAL_LEAD` is additive, not exclusive.** Nobody's whole job is clinical lead — it is a senior clinician who also carries governance responsibility for a department.

```
RoleAssignment(user_id, role_id, scope_type, scope_id, valid_from, valid_to)

Dr. A. Kessy
  ├─ SPECIALIST_DOCTOR   scope: CLINIC:CARDIOLOGY
  └─ CLINICAL_LEAD       scope: DEPARTMENT:INTERNAL_MEDICINE
```

Two assignments, one user, one login. The same person consults in the morning and reviews the coding queue in the afternoon. This is exactly what the permission-plus-scope model exists for; a separate account would fragment their clinical record authorship.

**Scoped to a department, not global.** A cardiology lead does not curate the paediatric common-code list. Where a hospital wants a single lead over everything, that is a hospital-wide scope assignment — a configuration choice, not a schema change.

---

## 2. Permissions

### 2.1 Granted

```
REFERENCE_SYNONYM_APPROVE            approve or reject proposed synonyms
REFERENCE_COMMON_MANAGE              curate department common lists
REFERENCE_CODING_REVIEW              work the unspecified-code queue
CLINICAL_GOVERNANCE_REPORT_READ      aggregate governance reports
TRIAGE_OVERRIDE_REVIEW               review priority downgrades
EMERGENCY_DECLARE_AUTHORISE          authorise bypass below IMMEDIATE
CLINICAL_NOTE_AMEND_AUTHORISE        authorise amendment of another clinician's finalized note
CRITICAL_RESULT_ESCALATION_RECEIVE   escalation target for unacknowledged results
STANDING_ORDER_LIST_MANAGE           define what triage may order pre-consultation
PROTOCOL_MANAGE                      routing rules, re-assessment intervals, screening questions
```

Plus everything held through their concurrent `DOCTOR` or `SPECIALIST_DOCTOR` assignment.

### 2.2 Withheld

```
USER_MANAGE                  admin — clinical seniority is not system administration
PRICE_LIST_MANAGE            admin / accountant
INVOICE_CANCEL_APPROVE       admin
PAYMENT_REFUND               accountant
PATIENT_MERGE                admin
AUDIT_LOG_READ               admin
REFERENCE_IMPORT             admin — importing an ICD release is a data operation
```

The boundary: `CLINICAL_LEAD` governs **clinical content and clinical decisions**. It does not govern money, identity, or infrastructure.

### 2.3 Self-approval is blocked

⚠ A clinical lead cannot approve their own synonym proposal, nor authorise amendment of their own note.

```
CHECK (proposed_by != reviewed_by)
CHECK (original_author_id != amend_authorised_by)
```

Same principle as lab result verification (`entered_by != verified_by`). Where a department has only one lead, the fallback approver is the lead of another department or an admin-designated deputy — never a self-approval exception.

---

## 3. Privacy constraint

⚠ **Governance duties do not grant blanket patient access.**

A lead reviewing triage downgrade rates does not need to read every chart. Reports are **aggregate by default**:

```
Triage downgrades — Internal Medicine, July
  N. Kimaro     14 downgrades / 312 assessments   4.5%
  J. Mwakasege   3 downgrades / 287 assessments   1.0%
  P. Shayo      31 downgrades / 298 assessments  10.4%  ⚠
```

Drilling into an individual case requires either an existing care relationship or break-glass with a typed justification, logged and alerted — the same rule as any other clinician. Seniority does not suspend §8.1 of the architecture.

---

## 4. Responsibilities by module

### 4.1 Clinical reference data

- Approve or reject synonym proposals, with a note. Review queue shows the proposer, the term, the target code, and the count of unsuccessful searches for that term
- Curate department common lists — drag to reorder, add from the usage-driven suggestions panel
- Work the unspecified-code review queue; a rising unspecified rate means the reference data is failing clinicians
- Monthly: top 50 diagnoses by department, unspecified rate, zero-result search backlog

### 4.2 Triage governance

**Resolves `triage §7.10`** — overnight `EMERGENCY` declaration. `IMMEDIATE` remains automatic and needs no authorisation. `URGENT` and below require `EMERGENCY_DECLARE_AUTHORISE`, which the on-call clinical lead holds. No triage nurse rostered means the on-call lead is the authoriser, not a gap.

**Resolves `triage §5.2`** — priority downgrades. Upgrades are accepted silently and logged. Downgrades appear on the lead's review queue, since that is where harm hides. Review is retrospective and does not block the nurse.

**Resolves `triage §7.11`** — standing orders. The lead defines the fixed list of tests triage may order before consultation (malaria RDT, blood glucose, pregnancy test, urinalysis). The list is data, editable by the lead, not a code constant. If the list is empty, triage orders nothing.

Also owns: routing rules (`RoutingRule`), re-assessment intervals per priority band, and the isolation screening questions — all of which change with clinical practice and outbreak conditions and must not require a deploy.

### 4.3 Doctor module

**Resolves `doctor v0.3 ?Q5`** — amending another clinician's finalized note. Not permitted directly. A doctor may always *append* an addendum. Modifying another author's finalized content requires `CLINICAL_NOTE_AMEND_AUTHORISE`; the original remains readable, the amendment records both the amending author and the authorising lead.

**Resolves `doctor v0.3 §8.2`** — critical result escalation. Where the ordering doctor does not acknowledge a critical result within the timeout, escalation goes to the department's clinical lead. This replaces the placeholder "department head" and gives the escalation path a real holder.

### 4.4 Emergency bypass oversight

Reviews the emergency bypass leakage report alongside admin and the accountant. The lead's contribution is the clinical half: were these declarations appropriate? Admin and accounts see the financial half. Neither view alone answers whether the mechanism is working.

Also reviews `IMMEDIATE` declaration rates per triage nurse. An outlier is a conversation — a nurse on the busiest shift and a nurse doing favours produce the same number.

---

## 5. Dashboard

```
Review Queue
  Synonym proposals                    [4]
  Triage downgrades — this week        [7]
  Unspecified diagnosis codes         [23]
  Unacknowledged critical results      [1]  ⚠
  Note amendment requests              [2]

Reference Data
  Common diagnoses — by department
  Common lab tests / drugs
  Synonyms
  Zero-result searches                [31]

Protocols
  Routing rules
  Standing orders (triage)
  Re-assessment intervals
  Isolation screening questions

Reports
  Top diagnoses          Unspecified rate
  Triage overrides       Emergency declarations
```

⚠ Unacknowledged critical results sit at the top and are the only item that alerts actively. Everything else is review work that can wait a day; a critical result that nobody has read cannot.

---

## 6. Data model

```
Role  ← seed row: code = 'CLINICAL_LEAD', is_system = true

SynonymProposal            -- see clinical-reference §2.3
    CHECK (proposed_by != reviewed_by)

NoteAmendmentRequest(
  id, target_note_id, requested_by, reason, requested_at,
  status, authorised_by, authorised_at, decision_note
)
    CHECK (original_author_id != authorised_by)

StandingOrderList(
  id, department_id, service_item_id,
  managed_by, effective_from, effective_to, is_active
)

ProtocolSetting(
  id, department_id, setting_key, setting_value jsonb,
  updated_by, updated_at, previous_value jsonb
)
```

⚠ `ProtocolSetting` retains `previous_value`. A change to a routing rule or a re-assessment interval alters clinical behaviour, and "what was the protocol when this patient was triaged" must be answerable.

---

## 7. Reference data confirmations

Folded into `hms-clinical-reference-data.md`:

| | Confirmed |
|---|---|
| `is_common` | Per-code, department-scoped, editable by `CLINICAL_LEAD` through the curation UI |
| `is_billable` | Added. Filters ICD-10 category headers out of the picker — offering them produces claims insurers reject |
| `ReferenceSearchLog` | Added. Drives the suggestions panel, the synonym backlog, and the zero-result report. This is the table that makes the reference layer self-improving |

---

## 8. Open

| | Question |
|---|---|
| `?Q1` | Is the lead scope per department, or per specialty clinic? Departments are coarser and simpler; clinics give finer control over common lists |
| `?Q2` | Who is the fallback approver when a department has one lead and the proposal is theirs — another department's lead, or an admin-designated deputy? |
| `?Q3` | Is there an on-call rota for `EMERGENCY_DECLARE_AUTHORISE` overnight, and does the system need to know who is on call, or is it phone-based with retrospective entry? |
| `?Q4` | Does the lead need visibility of prescribing patterns — antibiotic use, controlled substances — or is that out of v1 scope? |
