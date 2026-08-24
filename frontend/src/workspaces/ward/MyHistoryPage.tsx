import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { fetchMyNursingAssignments } from '../../shared/api/nursingAssignments';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDate } from '../../shared/format/date';

// 17-ward-nurse.md §11: "My History — read-only assignment history for the
// logged-in nurse."
export function MyHistoryPage() {
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const { data, isLoading } = useQuery({
    queryKey: ['nursing-assignments', 'mine'],
    queryFn: () => fetchMyNursingAssignments(accessToken as string),
    enabled: accessToken != null,
  });
  const rows = [...(data ?? [])].sort(
    (a, b) => new Date(b.assignmentDate ?? 0).getTime() - new Date(a.assignmentDate ?? 0).getTime(),
  );

  return (
    <div>
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>My History</h1>
      </header>

      <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
        {isLoading ? (
          <p style={{ padding: 'var(--space-4)', color: 'var(--text-muted)' }}>Loading…</p>
        ) : rows.length === 0 ? (
          <p style={{ padding: 'var(--space-4)', color: 'var(--text-muted)' }}>No roster assignments yet.</p>
        ) : (
          <table className="table">
            <caption className="sr-only">My assignment history</caption>
            <thead>
              <tr>
                <th>Date</th>
                <th>Shift</th>
                <th>Patient</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td>{formatDate(row.assignmentDate)}</td>
                  <td>{row.shift}</td>
                  <td>
                    {row.patientName ?? 'Unknown patient'}
                    {row.hospitalNumber && <span style={{ color: 'var(--text-muted)' }}> · {row.hospitalNumber}</span>}
                  </td>
                  <td>
                    {row.admissionId && (
                      <button type="button" className="btn btn-secondary" onClick={() => navigate(`/ward-nurse/patients/${row.admissionId}`)}>
                        Open
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
