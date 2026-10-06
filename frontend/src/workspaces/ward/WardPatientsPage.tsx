import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { formatDateTime } from '../../shared/format/date';
import { StatusBadge } from './DashboardPage';
import { useWardContext } from './WardContext';
import { useWardPatients } from './useWardPatients';
import type { WardStatus } from '../../shared/api/admissions';

const FILTERS: { key: WardStatus | 'ALL'; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'CRITICAL', label: 'Critical' },
  { key: 'ADMITTED', label: 'Admitted' },
  { key: 'STABLE', label: 'Stable' },
  { key: 'TRANSFERRED', label: 'Transferred' },
];

// 17-ward-nurse.md screen: the full ward roster, filterable by status —
// Dashboard shows the same list unfiltered as a glance; this is the
// working list a nurse scans through a shift.
export function WardPatientsPage() {
  const { wardName } = useWardContext();
  const { data: patients, isLoading } = useWardPatients();
  const navigate = useNavigate();
  const [filter, setFilter] = useState<WardStatus | 'ALL'>('ALL');

  const rows = (patients ?? []).filter((row) => filter === 'ALL' || row.wardStatus === filter);

  return (
    <div>
      <PageHeader title={<>Ward patients</>} description={<>{wardName ?? 'Your ward'}</>} />

      <div className="workspace-filters" role="group" aria-label="Ward patient status">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            type="button"
            className={filter === f.key ? 'btn btn-primary' : 'btn btn-secondary'}
            aria-pressed={filter === f.key}
            onClick={() => setFilter(f.key)}
          >
            {f.label}
          </button>
        ))}
      </div>

      <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
        {isLoading ? (
          <FeedbackState loading>Loading…</FeedbackState>
        ) : rows.length === 0 ? (
          <FeedbackState>No patients match this filter.</FeedbackState>
        ) : (
          <TableRegion label="Ward patients"><table className="table">
            <caption className="sr-only">Ward patients</caption>
            <thead>
              <tr>
                <th>Patient</th>
                <th>Bed</th>
                <th>Ward class</th>
                <th>Status</th>
                <th>Admitted</th>
                <th>Discharge ordered</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.admissionId} className={row.wardStatus === 'CRITICAL' ? 'row-critical' : undefined}>
                  <td style={{ fontWeight: 'var(--weight-medium)' }}>
                    {row.patientName ?? 'Unknown patient'}
                    {row.hospitalNumber && <span style={{ color: 'var(--text-muted)', fontWeight: 'normal' }}> · {row.hospitalNumber}</span>}
                  </td>
                  <td>{row.bedLabel ?? '—'}</td>
                  <td>{row.orderedWardClass ?? '—'}</td>
                  <td><StatusBadge status={row.wardStatus} /></td>
                  <td>{formatDateTime(row.admittedAt)}</td>
                  <td>
                    {row.dischargeOrderedAt ? (
                      <span className="badge badge-warning">{formatDateTime(row.dischargeOrderedAt)}</span>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td>
                    <button type="button" className="btn btn-secondary" onClick={() => navigate(`/ward-nurse/patients/${row.admissionId}`)}>
                      Open
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table></TableRegion>
        )}
      </div>
    </div>
  );
}
