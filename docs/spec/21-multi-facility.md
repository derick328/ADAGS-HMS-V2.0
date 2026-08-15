# ADAGS — Multi-facility schema decision

**Decision:** `facility_id` enters the schema in phase 0. A second branch is possible later.
**Admin accounts:** 3.

---

## 1. Why now

Adding `facility_id` after launch means touching every table, every query, every index, and every unique constraint — while the system is live in a hospital. Adding it before there is data costs one column and a `NOT NULL DEFAULT`.

**Single database with `facility_id`, not database-per-branch.** Per-branch databases multiply migrations and backups by the number of branches, and make a shared patient record impossible. Postgres row-level security is available later if isolation ever needs enforcing at the database rather than the query layer.

```sql
CREATE TABLE facility (
  id            BIGINT PRIMARY KEY,
  code          TEXT NOT NULL UNIQUE,
  name          TEXT NOT NULL,
  address       TEXT,
  timezone      TEXT NOT NULL DEFAULT 'Africa/Dar_es_Salaam',
  is_active     BOOLEAN NOT NULL DEFAULT true
);

INSERT INTO facility (id, code, name) VALUES (1, 'ADAGS-MAIN', 'ADAGS ENT Specialized Hospital');
```

Every facility-scoped table gets `facility_id BIGINT NOT NULL REFERENCES facility(id) DEFAULT 1`. The default lets phase 0 through phase 7 be written as if there were one hospital, while the column is already there.

---

## 2. The decision that actually matters

⚠ **Is a patient visiting both branches one record or two?**

**Recommendation: one shared patient record.** `Patient` has **no** `facility_id`.

| | Shared MPI (recommended) | Per-facility patients |
|---|---|---|
| Clinical history | Complete across branches | Fragmented — a doctor at branch B cannot see branch A's allergies |
| Hospital number | One, valid everywhere | Two, and staff must know which |
| Duplicate risk | Managed by the existing merge flow | Guaranteed duplicates for anyone using both |
| Insurance | One policy record | Duplicated, and can disagree |
| Complexity | Encounters carry the facility | Simpler queries, worse care |

The clinical argument settles it: a patient's allergy recorded at the main hospital must be visible at the branch. Fragmenting the record to simplify a query is the wrong trade in a system where the allergy check is already human.

---

## 3. What is scoped and what is shared

### Facility-scoped — `facility_id NOT NULL`

```
Encounter            Invoice              Payment
Order                LabOrder             Prescription
Admission            Bed                  Ward
Department           Clinic               ConsultationRoom
Store                StockBatch           StockMovement
StockBalance         Dispense             CashierShift
WorkShift            ShiftAssignment      Expense
SalaryRecord         DepositAccount       WardChargeAccrual
ProcedureOrder       SurgeryOrder         AuditLog
```

Rule of thumb: **anything that happened at a place, or is physically located at a place.**

### Shared across facilities — no `facility_id`

```
Patient              PatientIdentifier    NextOfKin
Allergy              PatientComorbidity   InsurancePolicy
User                 Role                 Permission
Icd10Code            Icd10Synonym         Item (catalogue)
ServiceItem          LabTest              InsuranceScheme
SchemeCoverageRule
```

Rule of thumb: **people, and definitions of things.** A patient is one human. Amoxicillin 500mg is one product. What differs per branch is the *price* and the *stock*, not the definition.

⚠ `Staff` carries a `home_facility_id` but a person can work at more than one — which facility they can act in comes from `RoleAssignment` scope, not from the staff record.

### Per-facility values on shared definitions

```
PriceList            facility_id — a branch may price differently
CommonCode           facility_id nullable — a branch may curate its own
                     common ICD list; null means group-wide
```

---

## 4. Scope becomes a role dimension

`RoleAssignment` already carries scope. Add `FACILITY` to the scope types:

```
scope_type ∈ GLOBAL | FACILITY | DEPARTMENT | WARD | CLINIC | STORE
```

```
Dr. A. Kessy
  ├─ SPECIALIST_DOCTOR   scope CLINIC:CARDIOLOGY@main
  └─ CLINICAL_LEAD       scope FACILITY:main

Nurse P. Shayo
  ├─ WARD_NURSE          scope WARD:B@main
  └─ WARD_NURSE          scope WARD:A@branch      -- works both sites
```

⚠ **A `DEPARTMENT` or `WARD` scope implies its facility.** Resolving a scope must walk up to the facility, or a nurse scoped to "Ward B" sees Ward B at every branch.

⚠ **Every query is facility-filtered by default.** Implement it once as a Hibernate filter or a repository base class, not as a `WHERE facility_id = ?` a developer must remember on each query. The one someone forgets is the one that leaks branch A's queue into branch B's screen.

---

## 5. Constraints that need the facility added

Several unique constraints already specified become wrong without it:

```sql
-- hospital numbers: gapless per facility per year (already specified)
UNIQUE (facility_id, year, sequence_no)

-- invoice and accession numbers: same
UNIQUE (facility_id, invoice_number)
UNIQUE (facility_id, accession_no)

-- bed occupancy: bed_id already implies a facility, unchanged
UNIQUE (bed_id) WHERE released_at IS NULL

-- one open encounter per patient: ⚠ per facility, or globally?
```

⚠ **The open-encounter constraint is the one to think about.** Should a patient be able to have an open encounter at both branches simultaneously?

**Recommendation: globally unique.** One open encounter per patient, full stop. A patient cannot physically be in two hospitals at once, and allowing it means two doctors ordering independently with no visibility of each other.

```sql
UNIQUE (patient_id) WHERE state NOT IN (terminal states)
```

If a patient genuinely transfers between branches, that is a `REFERRED_OUT` at one and a new encounter at the other — sequential, not concurrent.

---

## 6. What this changes in the phase 0 prompt

Add to the kickoff scope:

```
Migration V1 also includes:
  - facility table, seeded with one row (ADAGS main)
  - facility_id NOT NULL DEFAULT 1 on: audit_log
  - FACILITY added to scope_type on role_assignments
  - a repository base class or Hibernate filter that applies the
    facility filter automatically — not a manual WHERE clause

The authz engine must resolve DEPARTMENT and WARD scopes up to their
facility, so a ward-scoped user cannot see the same-named ward at
another facility.
```

Everything else in phase 0 is unchanged. The column exists, defaults correctly, and nothing else has to think about it until a second facility is created.

---

## 7. Admin accounts — 3

Sufficient for the two-admin controls:

```
Self-elevation (granting ADMIN or granting yourself a permission)
    → requires a second admin's approval

Expense approval
    → CHECK (created_by != approved_by)

Salary disbursement
    → CHECK (generated_by != disbursed_by)
```

⚠ **The deadlock case:** one admin on leave, one raised the request, leaving exactly one possible approver. Workable but occasionally blocking.

Document a fallback before it happens — either a fourth break-glass admin account held by management and used only when the normal path is blocked, or an accepted rule that the request waits. Discovering there is no answer at the moment payroll is due is the bad version.

⚠ **MFA mandatory on all three.** Three accounts is three times the attack surface on the role that controls users, prices, and payroll.

⚠ **Admin scoping.** With a branch coming, decide whether an admin is `GLOBAL` or `FACILITY`-scoped. Recommend: at least one `GLOBAL` for group-level configuration, the others facility-scoped. Reference data imports and role definitions are group-level; user management and expenses are facility-level.
