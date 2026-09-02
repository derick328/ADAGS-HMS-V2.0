import { useMe } from './useMe';

const SEEDED_MAIN_FACILITY_ID = '00000000-0000-0000-0000-000000000001';

/**
 * Admin screens that need a facility id (revenue, receivables, theatres,
 * dashboards) resolve it from the caller's own FACILITY-scoped role
 * assignments — no facility-list endpoint exists yet. A GLOBAL-only admin
 * falls back to the seeded "Main facility" UUID (V1) so single-facility
 * installs don't dead-end before there's any UI to pick a facility.
 */
export function useAdminFacilityId(): string | null {
  const { data: me } = useMe();
  if (!me) return null;
  const facilityScope = me.scopes?.find((s) => s.scopeType === 'FACILITY' && s.scopeId);
  if (facilityScope?.scopeId) return facilityScope.scopeId;
  const hasGlobal = (me.scopes ?? []).some((s) => s.scopeType === 'GLOBAL');
  return hasGlobal ? SEEDED_MAIN_FACILITY_ID : null;
}
