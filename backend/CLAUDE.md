# Backend conventions

Spring Boot 3, Java 21, PostgreSQL 15+, Flyway, Gradle.
Root `CLAUDE.md` holds the domain invariants — they are not repeated here.

---

## Structure — modular monolith

```
com.adags
  modules/
    identity/       users, roles, permissions, sessions
    patients/       MPI, demographics, next of kin, insurance policies
    encounters/     visit lifecycle, queues, triage
    clinical/       notes, diagnoses, allergies, vitals
    orders/         CPOE: the abstract order and its routing
    laboratory/     specimens, tests, results, reference ranges
    pharmacy/       formulary, prescriptions, dispensing
    inventory/      items, batches, stock movements, stores
    inpatient/      wards, beds, admissions, MAR, handover
    billing/        service catalogue, price lists, invoices, payments
    insurance/      schemes, coverage rules, pre-auth, claims
    scheduling/     appointments, clinic sessions, roster
    reporting/      read models, exports, statutory reports
    notifications/  SMS, email, in-app, print jobs
    audit/          append-only audit log
  shared/
    kernel/         Money, PatientId, DateRange, Quantity
    events/         domain events + transactional outbox
    authz/          permission and scope policy engine
```

### Module boundary rules — enforced, or they rot

1. Each module exposes **one public facade interface**. Nothing else is
   importable from outside.
2. **No module imports another module's entities.** Cross-module reads go
   through the facade or a read model.
3. No cross-module foreign keys except to `patient` and `encounter` — the two
   shared reference points.
4. Anything not needing immediate consistency goes over a domain event.

Enforce 1–3 with ArchUnit tests. Without them, the boundaries are decoration.

---

## Rules

- **Flyway from commit one.** `spring.jpa.hibernate.ddl-auto: validate`.
  Never `update`, never `create-drop` outside tests.
- Domain layer has **no Spring annotations and no I/O**. Plain objects,
  unit-testable without a context.
- `@Transactional` at the application service, never the repository.
- Optimistic locking (`@Version`) on `Encounter`, `Invoice`, `Order`,
  `Admission`, `ConsultationRecord`.
- Domain events published through the **transactional outbox**, never a direct
  call. `InvoiceSettled` releases the billing gate — it must survive a crash.
- The **billing gate lives in the domain layer**, called from a repository base
  class or an aspect. A controller-only check is defeated by a stale page, a
  direct call, or a race.
- Autosave endpoints (consultation drafts, triage assessments) write
  server-side every 20s, keyed `(encounter_id, author_id)`.

## Naming

- Facade: `PatientFacade`, `BillingFacade`
- Application service: `RegisterPatientService` — one per use case
- Events: past tense — `PatientRegistered`, `ChargePosted`
- Migrations: `V<n>__<snake_case_description>.sql`
- Permission codes: `SCREAMING_SNAKE`, matching the `permission` table exactly

## Testing

- **Domain**: plain JUnit, no Spring context.
- **Constraints**: Testcontainers against real Postgres. A partial unique index
  or a `CHECK` tested against H2 proves nothing — H2 does not implement them
  the same way.
- **Concurrency**: for each constraint in the root `CLAUDE.md`, a test that
  fires two concurrent transactions and asserts exactly one succeeds.
- **End to end**: one test covering register → triage → pay → consult → order →
  result → dispense → settle. This is the path that must never break.

## API

- springdoc-openapi emits `api/openapi.yaml` on build. The frontend generates
  its types from it — a rename must break the frontend build, not production.
- REST, resource-oriented. `POST /encounters`, not `POST /openEncounter`.
- `402 Payment Required` when the billing gate refuses, with the outstanding
  amount and the specific service in the body so the UI can offer
  "send to cashier".
- `409 Conflict` on optimistic lock failure, with the current version.

## Things that are always wrong here

```java
if (user.getRole().equals("DOCTOR"))        // role name — use a permission
repository.delete(clinicalNote)             // nothing is deleted
item.setPrice(request.getPrice())           // prices come from the price list
if (invoice.getBalance() > 0) throw ...     // on discharge: detention for debt
BigDecimal amount = new BigDecimal("10.50") // money is BIGINT minor units
bedRepo.findFree(); bedRepo.assign(bed);    // loses the race; use the index
```
