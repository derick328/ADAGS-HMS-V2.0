import { useAuth } from '../shared/auth/AuthProvider';

export function AccessPendingPage() {
  const { logout } = useAuth();

  return <main style={{ maxWidth: 520, margin: '10vh auto', padding: 'var(--space-4)' }}>
    <h1>Workspace access pending</h1>
    <p>Your account has no active workspace role. Ask your administrator to grant a role before signing in again.</p>
    <button type="button" className="btn btn-primary" onClick={() => void logout()}>Sign out</button>
  </main>;
}
