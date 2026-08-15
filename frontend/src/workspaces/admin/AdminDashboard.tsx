import { useMe } from '../../shared/auth/useMe';

export function AdminDashboard() {
  const { data: me } = useMe();

  return (
    <div>
      <h1>Welcome{me?.username ? `, ${me.username}` : ''}</h1>
      {me?.mustChangePassword && (
        <p role="alert">Your password was set at install time — change it before continuing.</p>
      )}
      <p>This admin account can log in and do nothing else — Phase 0 exit criterion.</p>
    </div>
  );
}
