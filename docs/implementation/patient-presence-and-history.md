# Patient presence and longitudinal history implementation

Status: substantial implementation, with the acceptance gaps below still outstanding. No deployment or production migration was performed.

## Audit and implementation choices

Existing functionality reused: encounter state machine and concurrent check-in recovery; patient canonical merge pointers; administrative visit history; inpatient actual departure; surgery lifecycle/dispositions; doctor encounter detail, notes, diagnosis snapshots, prescriptions and laboratory APIs; TAT stages and patient journey. Billing gates, receivables, lab verification, dispensing and inpatient discharge writers were not replaced.

Missing functionality found: outpatient physical departure; a shared presence projection; facility visit summaries; a recovery-completion milestone; functioning care-relationship lookup (the installed implementation was a Phase 0 placeholder). Existing clinical history reads checked permissions/scopes but not care relationships. Here-now queried only nonterminal encounters. TAT inferred visit end from terminal state events.

Implementation order: administrative projection and visit summaries; outpatient confirmation/correction; receptionist/Admin integration; clinical history fields and authorization; physical-departure TAT; targeted tests. Applicable invariants: no clinical/financial deletion, append-only corrections, no discharge payment gate, server timestamps, scoped permissions, no administrative clinical payloads, no additional encounter state machine.

## Implemented

- V87 adds immutable outpatient departure/correction events, a current-departure view, canonical identity projection and centralized encounter-presence view. Database trigger serializes departure insertion on the encounter and rejects duplicate current departures. Corrections preserve originals.
- Facility-scoped, bounded presence endpoint and batched patient summaries. Names/hospital numbers are projected in the same read; no per-row demographic/history requests for Here-now.
- Outpatient confirmation validates facility, permissions, clinical outcome, admissions and surgery. Encounter and existing surgery rows are locked while checking. Repeated confirmation returns the existing event ID. Neither orders, clinical state nor billing is changed.
- Existing awaiting-results Resume events supersede the prior current departure assertion without deleting the earlier excursion.
- Receptionist Here-now, recorded-departures switch, confirmation modal, search revisit badges, modal summary and explicit existing-encounter response. Admin patient detail reuses the same panel and existing TAT history; encounter administrators can correct departures with a reason.
- Doctor history list is compact/expandable and excludes cancelled/later encounters. Encounter detail includes dose/route/frequency/duration and dispensed quantity. Released lab details show values, units, reference ranges, verification and amendment/supersession labels. Existing inline allergies and ICD-10 title snapshots remain.
- Active assigned-provider relationship lookup through canonical identity. Notes/diagnoses require relationship plus target department permission; clinician prescription/laboratory history reads require relationship plus the existing facility permission. History reads use audit records. Administrative permissions do not grant clinical access.
- TAT visit ends use the presence departure timestamp, never terminal events; missing timestamps stay missing. Existing outpatient duration exclusion for admissions and existing stage-event timestamps remain. TAT patient lookup includes merged aliases.

## Modified frontend files

- `frontend/src/workspaces/receptionist/PatientIntakePage.tsx`
- `frontend/src/workspaces/admin/PatientsPage.tsx`
- `frontend/src/workspaces/admin/TatPage.tsx`
- `frontend/src/workspaces/admin/TatPatientHistory.tsx`
- `frontend/src/workspaces/doctor/ConsultationPage.tsx`
- `frontend/src/workspaces/doctor/EncounterDetailPage.tsx`
- New shared `PatientPresencePanel.tsx`, `PatientPresencePanel.test.tsx`, and `api/presence.ts`
- Regenerated `frontend/src/shared/api/types.ts`; updated `api/openapi.yaml`

`PatientRecordsPage`, `ReceptionistDashboard`, and doctor `PatientPage` retain their existing workflows. Pre-existing BillingPage changes were preserved.

## Modified backend files

Under `backend/src/main/java/com/adags/modules/`:

- `reporting/application/PatientPresenceService.java` (new)
- `reporting/api/PatientPresenceController.java` (new)
- `reporting/application/TatAnalyticsService.java`
- `encounters/application/OutpatientDepartureService.java` (new)
- `encounters/api/OutpatientDepartureController.java` (new)
- `encounters/domain/OutpatientDeparturePolicy.java` (new)
- `encounters/infrastructure/EncounterCareRelationship.java` (new)
- `encounters/application/OpenEncounterService.java`
- `encounters/api/EncounterController.java`, `EncounterResponse.java`
- `clinical/api/ConsultationRecordController.java`
- `laboratory/api/LabOrderController.java`, `LabResultController.java`
- `pharmacy/api/PrescriptionController.java`, `PrescriptionLineController.java`

Pre-existing TAT service/test changes were retained. No applied migration was edited.

## Migration and API

Migration: `backend/src/main/resources/db/migration/V87__facility_presence.sql`.

New endpoints:

- `GET /api/patient-presence?facilityId=&patientId=&departed=` (at most 200 encounters)
- `GET /api/patient-presence/summaries?facilityId=&patientIds=` (1–100 patient IDs)
- `POST /api/encounters/{id}/departure` (`facilityId`, `outcome`, optional `note`)
- `POST /api/encounters/{id}/departure/corrections` (`facilityId`, `departureId`, mandatory `reason`)

Existing encounter creation adds `existingEncounter`; prescription history adds dose, route, frequency, duration and dispensed quantity. Existing TAT contracts retain their shape but `endedAt` now means recorded physical departure.

## Rules

Presence: active admission takes stage priority, then surgery in progress/emergency draft, incomplete surgical disposition/recovery, then encounter workflow. Current bed assignment supplies the ward name; bed release never supplies a departure. Scheduling alone does not imply current presence. Workflow-derived care is explicitly labelled expected-present. Closed/consult-complete records without departure remain unconfirmed. Actual inpatient departure and uncorrected current outpatient departures establish departure; contradictions require review. Pending lab/prescription work can label a departed patient away with follow-up pending, but never blocks checkout.

Visits: distinct encounter rows per canonical patient and facility, ordered by creation time and ID. Cancelled-in-error rows are excluded. Abandoned, referred, deceased and left-without-being-seen attendances count because they represent recorded attendance, not necessarily completed treatment. Completed count currently means CLOSED or DISCHARGED. Registration is not a visit. No manually maintained visit number is stored.

History authorization: active assigned provider supplies the care relationship; department/facility permissions are still required on the target historical record. No new break-glass UI was added. Lab staff and pharmacy operational reads retain their existing permissions. Additional clinical team relationship rules require policy confirmation.

## Validation actually performed

- Changed backend production Java files compiled against cached Java 21 dependencies.
- 14 JUnit departure policy cases passed through the installed JUnit launcher.
- TypeScript project build passed.
- Vite production build passed (existing large-bundle warning).
- Vitest: 64 tests passed in six files, including five new administrative presence tests.
- V87 SQL/view and presence/correction/count assertions executed with local PostgreSQL 18 single-user mode against an isolated minimal schema; no SQL errors. This is not a full Flyway/Spring integration run.
- New Spring/Testcontainers `PatientPresenceIntegrationTest` covers confirmation idempotency, correction/history preservation, TAT departure, follow-up state preservation and invalid facility/outcome.
- Gradle execution was attempted with the wrapper and installed distribution. Read-only default cache, unavailable downloads and prohibited daemon sockets prevent execution here. Testcontainers integration/concurrency/regression matrix therefore remains unverified.
- pnpm itself could not acquire its package-manager engine-store lock on the read-only filesystem. Installed `tsc`, `vite`, `vitest`, and `openapi-typescript` entrypoints were run directly.
- API schema additions were extracted from compiled DTOs with Swagger ModelConverters; full Springdoc regeneration must be run in CI.

## Remaining acceptance gaps / clinical confirmation

1. Surgery recovery completion has no existing authoritative milestone. This implementation deliberately reports recovery completion unrecorded and refuses receptionist checkout for RECOVERY_THEN_DISCHARGE. A clinician-authorized recovery-completion event/UI must be implemented and agreed before that path is complete. No timestamp is invented.
2. Full requested authorization, concurrency and cross-module regression matrix has not run; the added tests do not constitute full acceptance coverage. Browser end-to-end verification is outstanding.
3. The compact doctor list links to diagnoses in encounter detail; it does not yet show a primary diagnosis beside every historical encounter. Collection dates and disposition presentation are not expanded beyond the existing API/view fields.
4. Presence lists are capped at 200 without pagination; old unconfirmed historical encounters may occupy the list. Department-only users cannot use the facility-wide presence endpoint without a facility permission assignment. Multi-facility selection uses the existing facility-scope hook.
5. Active-provider care relationships are implemented; nursing/team coverage and historical-provider access outside a current care relationship require clinical authorization policy confirmation. All relevant clinical routes should receive a broader authorization audit before rollout.
6. Completed-visit count intentionally excludes referred/deceased/abandoned/LWBS outcomes; the facility should confirm that reporting definition. They still count as attendances.
7. Confirming a departure from an incompatible clinical state requires existing outcome reconciliation; there is no new against-advice clinical state or silent finalization.
8. Full Springdoc regeneration, migration against the full schema and integration tests must pass before deployment. Existing live data has not been backfilled or altered.
