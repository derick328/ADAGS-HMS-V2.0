import { useMe } from './useMe';

// UI permission checks hide controls; they are not security — every one is
// duplicated server-side. Assume the user can call the endpoint directly.
export function usePermission(code: string): boolean {
  const { data } = useMe();
  return data?.permissions?.includes(code) ?? false;
}
