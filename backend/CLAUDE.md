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
- **Testcontainers version floor: 1.21.x, never let the BOM silently downgrade
  it.** Spring Boot's dependency-management BOM pins an older Testcontainers;
  `build.gradle.kts` overrides it via `extra["testcontainers.version"]`. This
  is unrelated to which Testcontainers version is current — every Testcontainers
  release through at least 2.0.x hardcodes a fallback to Docker API **1.32**
  for its connectivity probe whenever it can't resolve an explicit client API
  version, and Docker Engine 25+ can reject anything below its configured
  minimum (this host: min 1.40). The fix is not a version bump; it's forcing
  an explicit version so Testcontainers never hits that fallback:
  `systemProperty("api.version", "1.41")` on the `Test` task in
  `build.gradle.kts`. docker-java's config loader reads the API version **only**
  from the `api.version` JVM system property — there is no environment-variable
  form, so setting `DOCKER_API_VERSION` (or any env var) is a silent no-op. If
  Testcontainers tests start failing with `client version 1.32 is too old`
  again, check `systemProperty("api.version", ...)` is still there before
  assuming it's an environment problem.

## Patient duplicate-detection thresholds

05-receptionist.md §4.1's two tiers (`similarity(given_name || ' ' || family_name, ...)`
via `pg_trgm`) are `protocol_setting` rows (`V7__protocol_setting.sql`), read
through `ProtocolSettingReader` — not `@Value` constants — specifically so an
admin can retune them from real registration data without a redeploy. Keys:
`patients.duplicate_detection.low_similarity_threshold` (default **0.4**,
warn tier) and `...high_similarity_threshold` (default **0.65**, block tier,
also requires a DOB match).

The defaults were **0.3 / 0.6** originally, chosen without evidence. Measured
`similarity()` scores against Postgres 15's `pg_trgm` (bundled with
Testcontainers' `postgres:15` image — recompute against your own image if it
changes, the trigram tables aren't guaranteed stable across versions):

| Pair | Relationship | Score |
|---|---|---|
| `juma mkapa` / `zainabu mkapa` | shares only a family name | 0.32 |
| `amina hassan` / `fatuma hassan` | shares only a family name | 0.35 |
| `happiness kimaro` / `godfrey kimaro` | shares only a family name | 0.28 |
| `grace mushi` / `edwin mushi` | shares only a family name | 0.33 |
| `john mwangi` / `peter mwangi` | shares only a family name | 0.39 |
| `neema paul` / `neema pual` | transposed letters, same person | 0.47 |
| `asha juma` / `asha juna` | one-letter typo, same person | 0.54 |
| `ally rashid` / `ali rashid` | given-name spelling variant | 0.64 |
| `mohamed said` / `mohammed said` | spelling variant, same person | 0.80 |
| `amina hassan` / `aminah hassan` | spelling variant, same person | 0.80 |

At the old **0.3** low threshold, every "shares only a family name" pair
above (0.28–0.39) landed **at or above the warn line** — meaning two
unrelated patients who happen to share a common surname would trigger a
duplicate warning on every registration. That is the false-positive rate
that trains front-desk staff to click through the override without reading
it (root `CLAUDE.md` "Safety" — the same mechanism that ruins allergy
checking ruins this). **0.4** sits above that whole noise band and below the
lowest same-person score observed (0.47); **0.65** sits above the noise band
and separates "sounds like a spelling variant" (0.64, warn-only unless DOB
also matches) from "almost certainly a typo of the same name" (0.80+, block
tier alongside a DOB match).

This is still an interim calibration from a dozen synthetic pairs, not the
"real data" 05-receptionist.md §4.1 asks for — it's a materially better
starting point than the unexamined 0.3/0.6, not a finished tuning. Revisit
both `protocol_setting` rows once live registration data exists.

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
