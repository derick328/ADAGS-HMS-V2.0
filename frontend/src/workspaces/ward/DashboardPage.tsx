import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { useQueries } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { fetchOverdueMar } from '../../shared/api/medicationAdministration';
import { fetchWardAlerts } from '../../shared/api/wardAlerts';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDateTime } from '../../shared/format/date';
import { useWardContext } from './WardContext';
import { useWardPatients } from './useWardPatients';
import type { WardPatientRowResponse } from '../../shared/api/admissions';

// 17-ward-nurse.md screen 1: status counts, the assigned-patient list,
// overdue MAR (highlighted + badge), alerting vitals (highlighted, links to
// the patient) — everything scoped to this nurse's ward only (§3).
export function DashboardPage() {
  const { accessToken } = useAuth();
  const { wardName } = useWardContext();
  const navigate = useNavigate();
  const { data: patients, isLoading } = useWardPatients();
  const rows = patients ?? [];

  const overdueQueries = useQueries({
    queries: rows.map((row) => ({
      queryKey: ['mar', 'overdue', row.admissionId],
      queryFn: () => fetchOverdueMar(row.admissionId as string, accessToken as string),
      enabled: accessToken != null,
      refetchInterval: 30_000,
    })),
  });
  const alertQueries = useQueries({
    queries: rows.map((row) => ({
      queryKey: ['ward-alerts', row.admissionId],
      queryFn: () => fetchWardAlerts(row.admissionId as string, accessToken as string),
      enabled: accessToken != null,
      refetchInterval: 30_000,
    })),
  });

  const overdueByAdmission = new Map(rows.map((row, i) => [row.admissionId as string, overdueQueries[i]?.data ?? []]));
  const alertingVitalsByAdmission = new Map(
    rows.map((row, i) => [
      row.admissionId as string,
      (alertQueries[i]?.data ?? []).filter((a) => a.sourceType === 'VITAL_BREACH' && !a.acknowledged),
    ]),
  );
  const totalOverdue = [...overdueByAdmission.values()].reduce((sum, list) => sum + list.length, 0);
  const totalAlertingVitals = [...alertingVitalsByAdmission.values()].reduce((sum, list) => sum + list.length, 0);

  const counts = {
    CRITICAL: rows.filter((r) => r.wardStatus === 'CRITICAL').length,
    ADMITTED: rows.filter((r) => r.wardStatus === 'ADMITTED').length,
    STABLE: rows.filter((r) => r.wardStatus === 'STABLE').length,
    TRANSFERRED: rows.filter((r) => r.wardStatus === 'TRANSFERRED').length,
  };

  return (
    <div>
      <PageHeader title={<>Dashboard</>} description={<>{wardName ?? 'Your ward'}</>} />

      <div className="clinical-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 'var(--space-3)', marginBottom: 'var(--space-5)' }}>
        <StatusCountCard label="Critical" count={counts.CRITICAL} tone="critical" />
        <StatusCountCard label="Admitted" count={counts.ADMITTED} tone="neutral" />
        <StatusCountCard label="Stable" count={counts.STABLE} tone="success" />
        <StatusCountCard label="Transferred" count={counts.TRANSFERRED} tone="neutral" />
      </div>

      <div className="clinical-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-4)', marginBottom: 'var(--space-5)' }}>
        <section style={panelStyle}>
          <h2 style={panelTitleStyle}>
            Overdue MAR doses {totalOverdue > 0 && <span className="badge badge-critical">{totalOverdue}</span>}
          </h2>
          {totalOverdue === 0 ? (
            <FeedbackState>No overdue doses.</FeedbackState>
          ) : (
            <ul style={{ margin: 0, padding: 0, listStyle: 'none' }}>
              {rows
                .filter((row) => (overdueByAdmission.get(row.admissionId as string) ?? []).length > 0)
                .map((row) => (
                  <li
                    key={row.admissionId}
                    style={rowLinkStyle}
                    onClick={() => navigate(`/ward-nurse/patients/${row.admissionId}?tab=mar`)}
                  >
                    <span style={{ fontWeight: 'var(--weight-medium)' }}>{row.patientName ?? 'Unknown patient'}</span>
                    <span className="badge badge-critical">
                      {(overdueByAdmission.get(row.admissionId as string) ?? []).length} overdue
                    </span>
                  </li>
                ))}
            </ul>
          )}
        </section>

        <section style={panelStyle}>
          <h2 style={panelTitleStyle}>
            Alerting vitals {totalAlertingVitals > 0 && <span className="badge badge-critical">{totalAlertingVitals}</span>}
          </h2>
          {totalAlertingVitals === 0 ? (
            <FeedbackState>No unacknowledged breaches.</FeedbackState>
          ) : (
            <ul style={{ margin: 0, padding: 0, listStyle: 'none' }}>
              {rows
                .filter((row) => (alertingVitalsByAdmission.get(row.admissionId as string) ?? []).length > 0)
                .map((row) => (
                  <li
                    key={row.admissionId}
                    style={rowLinkStyle}
                    onClick={() => navigate(`/ward-nurse/patients/${row.admissionId}?tab=vitals`)}
                  >
                    <span style={{ fontWeight: 'var(--weight-medium)' }}>{row.patientName ?? 'Unknown patient'}</span>
                    <span style={{ color: 'var(--critical)', fontSize: 'var(--text-sm)' }}>
                      {(alertingVitalsByAdmission.get(row.admissionId as string) ?? [])[0]?.detail ?? 'Vital breach'}
                    </span>
                  </li>
                ))}
            </ul>
          )}
        </section>
      </div>

      <section style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
        <h2 style={{ ...panelTitleStyle, padding: 'var(--space-4) var(--space-4) 0' }}>Assigned patients</h2>
        {isLoading ? (
          <FeedbackState loading>Loading…</FeedbackState>
        ) : rows.length === 0 ? (
          <FeedbackState>No patients currently on this ward.</FeedbackState>
        ) : (
          <TableRegion label="Assigned patients"><table className="table">
            <caption className="sr-only">Assigned patients</caption>
            <thead>
              <tr>
                <th>Patient</th>
                <th>Bed</th>
                <th>Status</th>
                <th>Admitted</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <PatientRow key={row.admissionId} row={row} onOpen={() => navigate(`/ward-nurse/patients/${row.admissionId}`)} />
              ))}
            </tbody>
          </table></TableRegion>
        )}
      </section>
    </div>
  );
}

function PatientRow({ row, onOpen }: { row: WardPatientRowResponse; onOpen: () => void }) {
  const isCritical = row.wardStatus === 'CRITICAL';
  return (
    <tr className={isCritical ? 'row-critical' : undefined}>
      <td style={{ fontWeight: 'var(--weight-medium)' }}>
        {row.patientName ?? 'Unknown patient'}
        {row.hospitalNumber && <span style={{ color: 'var(--text-muted)', fontWeight: 'normal' }}> · {row.hospitalNumber}</span>}
      </td>
      <td>{row.bedLabel ?? '—'}</td>
      <td>
        <StatusBadge status={row.wardStatus} />
      </td>
      <td>{formatDateTime(row.admittedAt)}</td>
      <td>
        <button type="button" className="btn btn-secondary" onClick={onOpen}>
          Open
        </button>
      </td>
    </tr>
  );
}

export function StatusBadge({ status }: { status?: string }) {
  if (status === 'CRITICAL') return <span className="badge badge-critical">Critical</span>;
  if (status === 'STABLE') return <span className="badge badge-success">Stable</span>;
  if (status === 'DISCHARGED') return <span className="badge badge-neutral">Discharged</span>;
  if (status === 'TRANSFERRED') return <span className="badge badge-neutral">Transferred</span>;
  return <span className="badge badge-info">Admitted</span>;
}

function StatusCountCard({ label, count, tone }: { label: string; count: number; tone: 'critical' | 'success' | 'neutral' }) {
  const toneColor = tone === 'critical' ? 'var(--critical)' : tone === 'success' ? 'var(--green-600)' : 'var(--text-primary)';
  return (
    <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)' }}>
      <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>{label}</div>
      <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'var(--weight-bold)', color: toneColor }}>{count}</div>
    </div>
  );
}

const panelStyle: React.CSSProperties = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
  borderRadius: 'var(--radius-lg)',
  padding: 'var(--space-4)',
};

const panelTitleStyle: React.CSSProperties = {
  fontSize: 'var(--text-lg)',
  fontWeight: 'var(--weight-medium)',
  marginTop: 0,
  display: 'flex',
  alignItems: 'center',
  gap: 'var(--space-2)',
};

const rowLinkStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  padding: 'var(--space-2) 0',
  borderBottom: '1px solid var(--border)',
  cursor: 'pointer',
};
