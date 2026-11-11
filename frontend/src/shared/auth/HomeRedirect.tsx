import { Navigate } from 'react-router-dom';
import { useMe } from './useMe';
import { landingRouteFor } from './landingRoute';

export function HomeRedirect() {
  const { data: me, isLoading, refetch } = useMe();

  if (isLoading) return <p role="status">Loading your workspace…</p>;
  if (!me) {
    return <main>
      <p role="alert">Your account details could not be loaded.</p>
      <button type="button" onClick={() => void refetch()}>Try again</button>
    </main>;
  }

  const roleCodes = me.scopes?.map((scope) => scope.roleCode).filter((code): code is string => Boolean(code)) ?? [];
  return <Navigate to={landingRouteFor(roleCodes, me.permissions ?? [])} replace />;
}
