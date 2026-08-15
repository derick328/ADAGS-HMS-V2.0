# HMS — Admin Module Specification v0.3

**Supersedes:** `hms-admin-module-v0.2.md`
**Role:** `ADMIN`
**Routes:** `/admin/**`
**Status:** the six flagged conflicts are resolved. Remaining `?` items are open questions, not conflicts.

---

## 1. Resolutions

| | Item | Resolution |
|---|---|---|
| R1 | Expense and salary self-approval | Roles assigned to whoever is responsible, with `created_by != approved_by` enforced at the database |
| R2 | Admin clinical read | **Removed.** Admin holds no clinical data access. Break-glass only |
| R3 | One role per user | **Replaced** by `RoleAssignment` with scope and validity dates |
| R4 | Rejected cancellation | Returns to `ISSUED`, collectable. Reconsideration raises a new request |
| R5 | Delete and in-place price edits | Deactivate instead of delete; prices versioned by validity period |
| R6 | Payroll deductions | Driven by `employment_type` — interns exempt, permanent staff deducted |

---

## 2. Navigation

```
Dashboard                        ● 7 pending
Pricing / Price Proposals           3
Revenue / Invoice Cancellations     1
Patients                            financial view only — R2
Users / Add Nurse / Staff
Expenses
  ├─ Medication & Pharmacy
  ├─ Medications
  ├─ Salaries
  ├─ Utilities
  ├─ Maintenance
  ├─ Equipment
  └─ Other
Expense Analytics
────────────────────
Audit Log                           added — no other role can hold it
Facility & Settings                 added — no other role can hold it
Profile
```

---

## 3. R2 — clinical access removed

Admin holds **no** `CLINICAL_NOTE_READ`, `DIAGNOSIS_READ`, `LAB_RESULT_READ`, `PRESCRIPTION_READ`, or ward-note access.

⚠ This simplifies the module considerably — the per-record justification dialog and clinical-access alerting from v0.2 §2 are no longer needed at all.

### 3.1 The Patients screen splits

```
Admin KEEPS (financial)
  patient identity and hospital number
  invoice history, line items, payment history
  outstanding balances and receivables
  spending aggregates
  encounter dates, departments, attending provider names
  admission dates and ward names

Admin LOSES (clinical)
  consultations and clinical narrative
  diagnoses
  prescriptions and dispensing records
  lab results
  ward notes, vitals, MAR
  surgical records
```

The financial half answers every billing question, which is what admin actually needs the screen for. An invoice line reading "Malaria RDT — 5,000/=" is financial data; the result is not.

⚠ **No browse-all-patients list.** Search-first, returning nothing until queried, rate-limited. An unbounded patient list is the classic exfiltration surface.

### 3.2 Break-glass remains

Genuine cases exist — a data correction, a support investigation, a subject access request.

```
Break-glass → typed justification, minimum length
            → alert to CLINICAL_LEAD (not to another admin)
            → permanent audit entry
            → the patient's record shows it was accessed
```

⚠ Alert routing to `CLINICAL_LEAD` is the point. An admin whose access alerts go to admins is auditing themselves.

---

## 4. R3 — role assignments

```
RoleAssignment(
  id, user_id, role_id,
  scope_type,      -- GLOBAL | DEPARTMENT | WARD | CLINIC | STORE
  scope_id,
  valid_from, valid_to,
  granted_by, granted_at
)
```

```
Dr. A. Kessy
  ├─ SPECIALIST_DOCTOR   scope CLINIC:CARDIOLOGY     from 01 Jan
  └─ CLINICAL_LEAD       scope DEPARTMENT:INT_MED    from 01 Mar
```

Two things this buys beyond supporting `CLINICAL_LEAD`:

**Scope** — a `WARD_NURSE` assigned to Ward B sees Ward B. Without it, every nurse sees every ward.

**Validity** — a locum's access expires on `valid_to` without anyone remembering to revoke it.

The UI shows a user's assignments as a list with add and end-date actions, not a single dropdown.

### 4.1 Two role fields, named distinctly

```
Staff.job_title    HR designation — "Senior Nurse", "Consultant Physician"
RoleAssignment     what the system permits
```

A staff record with a job title and no system role is valid — not everyone employed needs a login.

### 4.2 Nurse onboarding shortcut

⚠ **Must capture ward scope.** As described it captures `nurse type (WARD_NURSE or NURSE)` but no ward, so a new nurse starts with hospital-wide visibility.

```
first/last name · DOB · gender · phone · email
license number · employment date
username · password
nurse type          WARD_NURSE | NURSE
ward scope          ⚠ added — required for WARD_NURSE
employment type     ⚠ added — drives R6
```

---

## 5. R1 — approval separation

Roles go to whoever is responsible, with the constraint enforcing separation regardless of who holds what:

```sql
-- expenses
CHECK (created_by IS DISTINCT FROM approved_by)

-- salary disbursement batches
CHECK (generated_by IS DISTINCT FROM disbursed_by)
```

⚠ The constraint matters because policy alone fails when one person ends up holding both roles. The database blocks it rather than relying on anyone remembering.

### 5.1 Bulk disbursement

```
Preview  → headcount, gross total, deductions total, net total, period
Commit   → single batch record, reversible as a batch
           idempotency key — running twice for one month must not pay twice
           approver ≠ generator
```

`Bulk-disburse` is the largest single financial action in the system. It shows what it is about to do before it does it.

### 5.2 Payroll permission is separate

⚠ `PAYROLL_MANAGE` as its own permission even if admin holds it today. Every staff member's pay is the most sensitive non-clinical data in the system, and splitting it means someone can be given catalogue and user management without it.

---

## 6. R4 — invoice cancellation lifecycle

```
Receptionist raises request (reason required)
        ↓
Invoice → CANCELLATION_PENDING     frozen, no payments accepted
        ↓
    ┌───┴────────────────────────────┐
    ↓                                ↓
ADMIN APPROVES                  ADMIN REJECTS
    ↓                                ↓
Invoice → VOID                  Invoice → ISSUED
charges reversed                collectable again
refund path if paid             admin's note shown to receptionist
                                request CLOSED (not deleted)
```

⚠ **Rejection returns the invoice to `ISSUED`.** The invoice is always in a definite state — alive and collectable, or dead. It is never left frozen after a decision, because a receptionist seeing "rejected" would otherwise not collect and the revenue is lost without anyone deciding to lose it.

### 6.1 Reconsideration

Admin may revisit a rejected request. The mechanism is a **new request**, not reopening the old one:

```
Admin changes their mind
   → raises a new InvoiceCancellationRequest
   → references the prior request
   → approve → VOID
```

The sequence of requests and decisions stays in the record, which is what an auditor needs to see. Reopening a closed decision would leave no trace of the first outcome.

### 6.2 Once payment has landed

⚠ If the patient pays after a rejection and admin then decides the invoice should die, that is a **refund**, not a void.

```
payments_total > 0  →  void is BLOCKED
                    →  routed to the accountant's refund flow
                    →  second signature required
```

```
InvoiceCancellationRequest(
  id, invoice_id, requested_by, reason, requested_at,
  status,                -- PENDING | APPROVED | REJECTED
  reviewed_by, reviewed_at, review_note,
  supersedes_request_id  -- for reconsideration
)
```

One `PENDING` request per invoice at a time.

Site-wide pending badge: keep.

---

## 7. R5 — catalogue and pricing

### 7.1 Deactivate, never delete

Every past invoice line and every dispense references the item permanently.

```
Delete on price catalogue item   → active = false
Delete on medication item        → active = false
Delete on expense                → ⚠ see §8.2
```

### 7.2 Prices are versioned

```
PriceListEntry(id, price_list_id, item_id,
               amount_minor, valid_from, valid_to)
```

A change closes the current entry with `valid_to` and opens a new one. A March invoice always resolves March's price. Editing in place silently changes the value of every historical invoice.

### 7.3 Payer-aware

One `price` field holds only the cash price. NHIF, private schemes, corporate, and staff rates need their own `PriceList` rows, or every insured invoice is wrong.

### 7.4 Field destinations — the price-doubling fix

The medications direct-add form carries three numbers that must land in different places:

```
rate         → StockBatch.unit_cost        what the hospital paid
discount %   → applied to rate, or recorded on the expense
price        → PriceListEntry (cash)       what the patient pays
```

⚠ Conflating these is what produced the doubling: the same number written to two destinations with different meanings.

### 7.5 Bulk operations

Excel import and bulk price changes both need: template download, dry-run validation with row-level errors, all-or-nothing commit, duplicate detection, full audit with the source file retained, and a preview showing old price, new price, and percentage change with outliers highlighted.

### 7.6 Proposal approval

Approve one click; **reject requires a reason**, not optional — a silent rejection tells the proposer nothing and the same proposal returns next week unchanged.

The approver sees the current price alongside the proposed one, snapshotted on the proposal so a later change does not rewrite history.

---

## 8. Expenses

### 8.1 Fields

As described: title, category, amount, date, payment method, paid-to, receipt number, notes, recurring with frequency, receipt file upload.

⚠ Receipt files go to the object store, key in the database — not as a database blob.

⚠ Add a **delivery note reference** on the medications combined-add, so a stock increase has an external document behind it.

### 8.2 Delete on expenses

⚠ An approved expense is a financial record. `delete` should be `VOID` with a reason, retained. A deleted expense leaves a gap in the record with no explanation, which is exactly what an auditor looks for.

Unapproved drafts may be deleted freely.

### 8.3 Recurring expenses

⚠ Recurring generation needs an idempotency guard — a monthly utility bill generated twice creates a duplicate liability. Key on `(expense_template_id, period)`.

---

## 9. R6 — payroll with employment-type-driven deductions

Deductions varying by employment type means the system **calculates** payroll rather than recording payments made. So it needs to know each person's category.

```
Staff.employment_type ∈ PERMANENT | CONTRACT | INTERN | VOLUNTEER | LOCUM

DeductionRule(
  id, employment_type, deduction_code,
  calculation_method,        -- PERCENTAGE | BAND | FIXED
  rate_or_amount, threshold_minor,
  is_employer_paid,          -- ⚠ see below
  effective_from, effective_to
)
```

Interns get zero rows. Permanent staff get the applicable set. Rules as data means a finance-act change is a configuration update, not a deploy.

### 9.1 Employer-paid versus employee-deducted

⚠ These are different things and must not both appear as payslip deductions:

```
Employee-deducted   PAYE, NSSF/PSSSF employee portion,
                    NHIF employee portion
                    → reduces net pay

Employer-paid       SDL, WCF, NSSF/PSSSF employer portion
                    → hospital expense, NOT a payslip deduction
                    → belongs in Expenses, not on the payslip
```

`is_employer_paid` on the rule keeps them apart.

### 9.2 Verify the intern exemption with an accountant

⚠ Two cautions I would not encode from my own reading:

**PAYE depends on the amount, not the job title.** A paid intern whose stipend exceeds the tax-free threshold may be taxable regardless of being an intern. A blanket "intern = no deductions" rule could create an under-withholding liability for the hospital.

**WCF is employer-side** and may attach to anyone the hospital directs to work, including interns.

Both should be confirmed with whoever handles the hospital's payroll before the rules are seeded. The `DeductionRule` structure supports either answer — it is the rule *rows* that need verifying, not the design.

### 9.3 Structure

```
SalaryRate(role_id, employment_type, basic_salary_minor, year)

SalaryRecord(
  staff_id, month, year,
  basic_minor, overtime_hours, overtime_rate_minor,
  gross_minor,
  deductions jsonb,          -- code, amount, rule_id per line
  net_minor,
  payment_method, payment_status, paid_at, notes,
  batch_id                   -- disbursement batch
)
```

Role rates are a default; per-employee override as described, since individuals differ within a role.

⚠ `deductions` stores the `rule_id` applied, so a historical payslip is reconstructable even after rates change.

---

## 10. Analytics

**Revenue** — period-filtered with category drill-down, as described.
**Expenses** — category breakdown and monthly trend, as described.

⚠ Three corrections that matter for the numbers being true:

**Deposits are a liability, not revenue.** Unconsumed inpatient deposits must be excluded, or income is overstated.

**Insurance receivables are not cash.** A claim raised is billed, not collected. Revenue reporting should distinguish the two, or the cash position is fiction until remittances reconcile.

**Emergency bypass debt is neither.** It is accrued and unpaid — visible as a receivable, not as revenue.

⚠ **Neither screen answers whether the hospital is profitable.** Both halves of the data exist:

```
Contribution by department and service line
  = revenue − direct cost (pharmacy unit_cost, consumables,
                           attributable salaries)
```

This is the question management asks in the first month.

---

## 11. Audit log

Read-only, append-only, editable by nobody including admin. Filter by actor, patient, resource type, action, date range, break-glass only, failed access only. CSV export for an external auditor. Monthly partitions.

⚠ **Who audits the admin?** Admin reads the log, so the log alone is not a control on admin. Two mitigations:

1. Break-glass and self-elevation alerts route to `CLINICAL_LEAD` and a second admin
2. A monthly admin-activity summary goes to hospital management **outside the system** — emailed or printed, something admin cannot suppress from inside

⚠ **Admin cannot silently elevate themselves.** Granting `ADMIN`, or granting any permission to one's own account, requires a second admin and alerts regardless.

`?Q1` — how many admin accounts will exist? Self-elevation approval needs at least two.

---

## 12. Facility and settings

```
Facility        name, address, logo, contact — on invoices, lab
                reports, prescriptions
Departments     clinical and non-clinical units
Wards & beds    ⚠ beds as rows, not free text — the occupancy
                constraint depends on it
Clinics         specialty clinics
Insurance       schemes and coverage rules
Shifts & roster the WorkShift model
Numbering       hospital number, invoice, accession, claim formats
Notifications   SMS gateway, templates, sender ID, retry policy
Sessions        idle timeout, PIN re-auth, MFA policy
Thresholds      accumulated ProtocolSetting keys
Backup          schedule, destination, restore-test log
```

⚠ **Restore-test age on the dashboard.** An untested backup is not a backup, and it only gets tested if someone watches the number go stale.

⚠ **MFA mandatory for `ADMIN`.** Even with clinical read removed, admin controls users, prices, and payroll.

⚠ **Bootstrap:** first run creates one admin from an install-time secret, forces password change and MFA enrolment, and the bootstrap credential invalidates once a second admin exists. No default password.

---

## 13. Account lifecycle

**Soft delete only** — as described.

⚠ **Deactivating a user with open work:**

```
Check for  incomplete consultations · open cashier shift
           entered-but-unverified lab results · assigned ward patients
           pending approvals raised by this user
Then       block with the list, or force with a reason and reassignment
Never      silently orphan the work
```

---

## 14. Open

| | Question |
|---|---|
| `?Q1` | How many admin accounts? Self-elevation approval needs at least two |
| `?Q2` | Which role holds expense approval, and which holds `PAYROLL_MANAGE`? |
| `?Q3` | Confirm intern deduction exemption with the hospital's accountant (§9.2) |
| `?Q4` | Second approver for bulk price changes above a threshold? |
| `?Q5` | Is there an external recipient for the monthly admin-activity summary? |
| `?Q6` | Does `PRICE_LIST_MANAGE` stay with admin, or move to the accountant? |
| `?Q7` | `CONSULT_FOLLOW_UP` price — still unanswered since the receptionist phase |
| `?Q8` | Multi-facility — one hospital or several? Determines whether `facility_id` enters the schema now |
