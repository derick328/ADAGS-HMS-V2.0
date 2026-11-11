import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useMe } from './useMe';

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { data: me, isLoading, refetch } = useMe();

  if (isLoading) return null;
  if (!me) {
    return <main>
      <p role="alert">Your account details could not be loaded.</p>
      <button type="button" onClick={() => void refetch()}>Try again</button>
    </main>;
  }

  if (!me.scopes?.some((scope) => scope.roleCode === 'ADMIN') && !me.permissions?.includes('USER_MANAGE')) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
