// docs/spec/01-architecture.md §1 / V4__audit_facility_and_elevation_scope_guard.sql:
// "assumes a single hospital for v1." No /api/facilities list endpoint
// exists yet (facility onboarding is an admin workflow that isn't built —
// 18-admin.md §12, ?Q8), so every screen that needs a facilityId today
// uses this same well-known bootstrap id the backend itself seeds.
// Revisit the moment a facility picker exists.
export const BOOTSTRAP_FACILITY_ID = '00000000-0000-0000-0000-000000000001';
