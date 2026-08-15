# ADAGS Hospital Management System

Spring Boot + PostgreSQL backend in `backend/`. React + TypeScript frontend in `frontend/`.
Full specs in `docs/spec/`. Read the relevant spec before implementing any module.

**Before writing code for any module, state which invariants below apply to it.**
If you can't name them, you haven't read them.

---

## Non-negotiable invariants

Violating any of these is a bug, however reasonable the change looks.

### Billing

- **Never block discharge or encounter closure on an unpaid balance.**
  `if medical_complete → CLOSED`, `if balance > 0 → create receivable`.
  There must be no code path where money prevents a patient leaving.
- The billing gate runs **server-side, in the domain layer**, on every
  resource-consuming endpoint. A check in a controller or the UI is not a control.
- The gate is evaluated **live at the point of consumption** — never cached at
  order time. The patient probably paid between ordering and arriving.
- Gating is **per invoice line**, never per invoice.
- An active `PaymentBypass` satisfies the gate. `IMMEDIATE` triage priority
  creates one automatically.
- `line.authorization_id IS NOT NULL` satisfies the gate **permanently** — an
  order placed under a bypass still fulfils after that bypass is revoked.
- Gate positions: consultation entry · lab specimen collection · pharmacy
  dispensing · procedure start. Never on movement, never on discharge.
- **Nobody types a price.** Prices resolve from `PriceListEntry` for the
  encounter's payer type. Not the receptionist, doctor, nurse, or pharmacist.
- Prices are **versioned**, never edited in place. Close `valid_to`, open a new row.
  A March invoice must always resolve March's price.

### Data

- **Nothing clinical or financial is ever hard-deleted.** Soft delete, void, or
  amend. Voided invoices persist permanently — a gap in the invoice sequence is
  what an auditor looks for.
- Clinical records are **amended, never updated in place**: `amends_id`,
  `amendment_reason`, original stays readable.
- Stock is an **append-only movement ledger**. Never a mutable quantity column.
  Balances are derived and materialised.
- Every state transition writes an event row.
- Snapshot values that can change underneath a record: ICD-10 titles on the
  diagnosis, verification level on the lab result, unit cost on the dispense line.

### Authorisation

- Check **permissions, never role names**. No `if (role.equals("DOCTOR"))`.
- `RoleAssignment(user, role, scope_type, scope_id, valid_from, valid_to)`.
  A user holds multiple scoped, time-bounded roles.
- Scope resolution **walks up**: WARD → DEPARTMENT → FACILITY. A ward-scoped
  user must not see the same-named ward at another facility.
- **Admin has no clinical data access.** Break-glass only, with typed
  justification, alerting `CLINICAL_LEAD` — never another admin.
- Second-person constraints at the database:
  - `CHECK (created_by != approved_by)` on expenses
  - `CHECK (verified_by != entered_by)` on lab results
  - `CHECK (reviewed_by != requested_by)` on elevation requests

### Concurrency — database constraints, not application checks

A read-then-write check in application code loses the race. These are real
constraints with tests that prove they reject the bad case:

```sql
UNIQUE (bed_id) WHERE released_at IS NULL
UNIQUE (patient_id) WHERE encounter state not terminal
UNIQUE (encounter_id, activity_type) WHERE ended_at IS NULL
UNIQUE (bed_assignment_id, accrual_boundary_at)
UNIQUE (encounter_id) WHERE is_primary          -- one primary diagnosis
CHECK (quantity >= 0)                            -- stock balances
```

Idempotency keys required on payments, dispensing, and medication administration.
A double-tap must not charge twice or chart two doses.

### Safety

- **Critical lab results release regardless of payment status.** Always. There
  must be no path where an unpaid balance withholds a life-threatening value.
- **Allergy records render inline and unmissable** on prescribing and dispensing
  screens — never collapsed, never behind a tab. Allergies are free text, so no
  automated matching is possible; the check is human, and hiding it removes the
  check entirely.
- Controlled medicines (`regulatory_schedule = 'CONTROLLED'`) are **blocked**
  from catalogue activation, pricing, prescribing, dispensing, restock, and
  transfer. Enforce in the repository layer so new endpoints inherit it.
- `IMMEDIATE` triage priority bypasses the payment gate for the **whole
  encounter**, not one charge.

---

## Conventions

- **Money**: `BIGINT` minor units + currency code. Never floating point, never
  `BigDecimal` without minor units.
- **Time**: store UTC, render facility-local (`Africa/Dar_es_Salaam`).
  `occurred_at` (when it happened, user-editable within limits) and
  `recorded_at` (system-set, immutable) are different fields.
- **Numbering**: gapless per-facility per-year sequences with row locking.
  Never `MAX(n)+1`, never a UUID for a human-facing number.
- **Facility**: everything that happened at a place or is located at a place
  carries `facility_id`. People and definitions (patient, user, ICD code, item
  catalogue) do not. The facility filter is applied automatically by a
  repository base class — never a `WHERE facility_id = ?` a developer must
  remember.
- **Modules**: one public facade interface each. No cross-module entity imports.
  Enforced with ArchUnit.
- **No PHI in logs.** Log patient UUIDs, never names. Scrubber tested.

---

## Design tokens

**`frontend/src/shared/styles/tokens.css` is canonical.** It defines the full
scale — brand, neutrals, semantic status, spacing, type, and the `.badge`,
`.table` and row-severity classes. Never write a raw hex in a component; never
define a token anywhere else.

Brand ramp:

```css
--green-signal: #7FFF00;  /* SIGNAL ONLY — dark text, 16:1 */
--green-100:    #EAF3DE;  /* tinted rows, selected states */
--green-200:    #C0DD97;  /* muted text on dark surfaces */
--green-500:    #639922;  /* success */
--green-600:    #3B6D11;  /* buttons, links — white text 5.6:1 */
--green-900:    #173404;  /* sidebar, headers */
```

**Never put white text on `--green-signal`** — 1.3:1 contrast, fails every
threshold. It is for active nav state, badges, focus rings, and the logo mark
only. Never a page background or large panel — it is fatiguing over a 12-hour
shift. Anything carrying white text uses `--green-600` or darker.

Semantic status (`--critical`, `--warning`, `--info`) is deliberately far from
brand green in hue: green means "active" and "success" here, so clinical
severity must not compete with it.

`frontend/src/shared/components/QueueShell.tsx` is the reference implementation.
Match its conventions.

---

## Build order

Phases 0–7 in `docs/spec/` order. Each ends with something a department can use.
**Phase 0 is not optional and not fast** — every later phase depends on the
permission model, and retrofitting authorisation into code that assumes a role
string is the most common way projects of this shape stall.

## Working style

- Implement **one spec section at a time**, not a whole module.
- Before writing: name the invariants that apply, the constraints needed, and
  the migration you'll add.
- Every constraint above gets a test that proves it rejects the bad case,
  against real Postgres via Testcontainers.
