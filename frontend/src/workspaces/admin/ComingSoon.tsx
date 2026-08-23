export function ComingSoon({ title }: { title: string }) {
  return (
    <div>
      <header style={{ marginBottom: 'var(--space-4)', display: 'flex', alignItems: 'baseline', gap: 'var(--space-3)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>{title}</h1>
        <span className="badge badge-neutral">not built yet</span>
      </header>
      <div
        style={{
          background: 'var(--bg-surface)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-lg)',
          padding: 'var(--space-10) var(--space-4)',
          textAlign: 'center',
          color: 'var(--text-muted)',
          fontSize: 'var(--text-sm)',
        }}
      >
        No backend module behind this screen yet — Phase 0 only ships the shell.
      </div>
    </div>
  );
}
