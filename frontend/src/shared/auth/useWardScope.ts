import { useMe } from './useMe';

/**
 * 17-ward-nurse.md §3: "Scope the ward patient list — a nurse on Ward B
 * should not see Ward C's patients." /me's scopes already carry every
 * WARD-scoped RoleAssignment (scopeId is the ward's own id), so this is a
 * client-side filter, not a fetch — the server independently re-checks
 * WARD scope on every request that follows.
 */
export function useWardIds(): string[] {
  const { data } = useMe();
  const scopes = data?.scopes ?? [];
  const ids = scopes.filter((s) => s.scopeType === 'WARD' && s.scopeId).map((s) => s.scopeId as string);
  return [...new Set(ids)];
}
