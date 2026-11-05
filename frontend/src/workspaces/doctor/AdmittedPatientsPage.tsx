import { EmptyState } from '../../shared/components/EmptyState/EmptyState';
import { TableRegion } from '../../shared/components/TableRegion';
import { WorkspacePage, QueryState } from '../../shared/components/WorkspacePage';
import { useQuery } from '@tanstack/react-query';
import { BedDouble } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { fetchMyAdmissions, type WardPatientRowResponse } from '../../shared/api/admissions';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDateTime } from '../../shared/format/date';

// 07-doctor.md §7/§11: the doctor's window into the patients they admitted —
// active admissions this doctor ordered, placed into a ward or still waiting
// for a bed. Read-and-navigate, same shape as My Surgeries: the clinical
// actions live on the encounter record this links to, not here.
export function AdmittedPatientsPage() {
  const { accessToken } = useAuth();
  const navigate = useNavigate();

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['admissions', 'mine'],
    queryFn: () => fetchMyAdmissions(accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 30_000,
  });

  const rows = data ?? [];

  return (
    <WorkspacePage title="Admitted patients" description="Patients you admitted — their ward, bed, and status.">
      <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflowX: 'auto' }}>
        {isError ? (
          <QueryState title="Unable to load admitted patients" onRetry={() => void refetch()} />
        ) : isLoading ? (
          <QueryState loading title="Loading admitted patients…" />
        ) : rows.length === 0 ? (
          <div style={{ padding: 'var(--space-5)' }}>
            <EmptyState
              icon={BedDouble}
              title="No admitted patients"
              description="Patients you admit from a consultation appear here until they are discharged."
            />
          </div>
        ) : (
          <TableRegion label="Admitted patients"><table className="table">
            <caption className="sr-only">Admitted patients</caption>
            <thead>
              <tr>
                <th>Patient</th>
                <th>Ward class</th>
                <th>Bed</th>
                <th>Status</th>
                <th>Admitted</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.admissionId} className={row.wardStatus === 'CRITICAL' ? 'row-critical' : undefined}>
                  <td style={{ fontWeight: 'var(--weight-medium)' }}>
                    {row.patientName ?? 'Unknown patient'}
                    {row.hospitalNumber && <span style={{ color: 'var(--text-muted)', fontWeight: 'normal' }}> · {row.hospitalNumber}</span>}
                  </td>
                  <td>{row.orderedWardClass ?? '—'}</td>
                  <td>{row.bedLabel ?? <span className="badge badge-warning">Awaiting bed</span>}</td>
                  <td><AdmissionStatusBadge status={row.wardStatus} /></td>
                  <td>{formatDateTime(row.admittedAt)}</td>
                  <td style={{ textAlign: 'right' }}>
                    <button
                      type="button"
                      className="btn btn-secondary"
                      disabled={!row.patientId || !row.encounterId}
                      onClick={() => navigate(`/doctor/patients/${row.patientId}/encounters/${row.encounterId}`)}
                    >
                      Open record
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table></TableRegion>
        )}
      </div>
    </WorkspacePage>
  );
}

function AdmissionStatusBadge({ status }: { status?: WardPatientRowResponse['wardStatus'] }) {
  if (status === 'CRITICAL') return <span className="badge badge-critical">Critical</span>;
  if (status === 'STABLE') return <span className="badge badge-success">Stable</span>;
  if (status === 'DISCHARGED') return <span className="badge badge-neutral">Discharged</span>;
  if (status === 'TRANSFERRED') return <span className="badge badge-neutral">Transferred</span>;
  return <span className="badge badge-info">Admitted</span>;
}
