import { useMe } from '../../shared/auth/useMe';

export function AdminDashboard() {
  const { data: me } = useMe();

  return (
    <div>
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>
          Welcome{me?.username ? `, ${me.username}` : ''}
        </h1>
      </header>

      {me?.mustChangePassword && (
        <p
          role="alert"
          style={{
            marginTop: 0,
            padding: 'var(--space-3)',
            background: 'var(--warning-bg)',
            color: 'var(--warning)',
            borderRadius: 'var(--radius)',
            maxWidth: 480,
          }}
        >
          Your password was set at install time — change it before continuing.
        </p>
      )}

      <div
        style={{
          background: 'var(--bg-surface)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-lg)',
          padding: 'var(--space-4)',
          maxWidth: 480,
          color: 'var(--text-secondary)',
          fontSize: 'var(--text-sm)',
        }}
      >
        This admin account can log in and do nothing else — Phase 0 exit criterion.
      </div>
    </div>
  );
}
