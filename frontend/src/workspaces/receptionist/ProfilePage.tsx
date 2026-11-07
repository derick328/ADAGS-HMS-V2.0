import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { useMe } from '../../shared/auth/useMe';
import { formatDate } from '../../shared/format/date';

export function ProfilePage() {
  const { data: me, isLoading } = useMe();

  return (
    <div>
      <PageHeader title={<>Profile</>} />

      {isLoading || !me ? (
        <FeedbackState loading>Loading…</FeedbackState>
      ) : (
        <>
          <section
            style={{
              background: 'var(--bg-surface)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-lg)',
              padding: 'var(--space-4)',
              maxWidth: 480,
              marginBottom: 'var(--space-4)',
            }}
          >
            <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 }}>Account</h2>
            <dl style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', margin: 0 }}>
              <dt style={{ fontWeight: 'var(--weight-medium)' }}>Username</dt>
              <dd style={{ margin: '0 0 var(--space-2)' }}>{me.username}</dd>
              <dt style={{ fontWeight: 'var(--weight-medium)' }}>User ID</dt>
              <dd style={{ margin: 0, fontFamily: 'var(--font-mono)', fontSize: 'var(--text-xs)' }}>{me.userId}</dd>
            </dl>
          </section>

          <section
            style={{
              background: 'var(--bg-surface)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-lg)',
              overflow: 'hidden',
              marginBottom: 'var(--space-4)',
            }}
          >
            <h2
              style={{
                fontSize: 'var(--text-lg)',
                fontWeight: 'var(--weight-medium)',
                margin: 0,
                padding: 'var(--space-4) var(--space-4) 0',
              }}
            >
              Role assignments
            </h2>
            {!me.scopes || me.scopes.length === 0 ? (
              <FeedbackState>No role assignments.</FeedbackState>
            ) : (
              <TableRegion label="Role assignments"><table className="table">
                <caption className="sr-only">Role assignments</caption>
                <thead>
                  <tr>
                    <th>Role</th>
                    <th>Scope</th>
                    <th>Valid from</th>
                    <th>Valid to</th>
                  </tr>
                </thead>
                <tbody>
                  {me.scopes.map((scope, i) => (
                    <tr key={i}>
                      <td style={{ fontWeight: 'var(--weight-medium)' }}>{scope.roleCode}</td>
                      <td>
                        {scope.scopeType}
                        {scope.scopeId && (
                          <span style={{ color: 'var(--text-muted)' }}> · {scope.scopeId.slice(0, 8)}</span>
                        )}
                      </td>
                      <td>{formatDate(scope.validFrom)}</td>
                      <td>{scope.validTo ? formatDate(scope.validTo) : <span className="badge badge-success">ongoing</span>}</td>
                    </tr>
                  ))}
                </tbody>
              </table></TableRegion>
            )}
          </section>

          <section
            style={{
              background: 'var(--bg-surface)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-lg)',
              padding: 'var(--space-4)',
            }}
          >
            <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 }}>
              Permissions ({me.permissions?.length ?? 0})
            </h2>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-1)' }}>
              {(me.permissions ?? []).map((code) => (
                <span key={code} className="badge badge-neutral">
                  {code}
                </span>
              ))}
            </div>
          </section>
        </>
      )}
    </div>
  );
}
