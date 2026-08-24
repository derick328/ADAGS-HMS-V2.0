import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { fetchMyLabOrders } from '../../shared/api/labOrders';
import { fetchMyPrescriptions } from '../../shared/api/prescriptions';
import { fetchPatientById } from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDateTime, formatWaitingSince } from '../../shared/format/date';

// 08-doctor-navigation.md §5. Every lab request and prescription this
// doctor has raised, with live status — LabOrderController#mine /
// PrescriptionController#mine (added alongside the rest of this build,
// since no read endpoint for "my own orders" existed before). Statuses
// come straight from the server's derivation (PENDING_PAYMENT / AWAITING_
// COLLECTION / IN_PROGRESS / COMPLETED / CANCELLED) — CANCELLED/EXPIRED
// never appear for lab orders specifically because no cancel/TTL path
// exists in this phase (an honest gap, not a fabricated status).
const PENDING_PAYMENT_HIGHLIGHT_HOURS = 24;

type Row = {
  id: string;
  type: 'LAB' | 'RX';
  patientId: string;
  label: string;
  status: string;
  occurredAt?: string;
};

export function RequestHistoryPage() {
  const { accessToken } = useAuth();
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [typeFilter, setTypeFilter] = useState<'ALL' | 'LAB' | 'RX'>('ALL');

  const { data: labs } = useQuery({
    queryKey: ['lab-orders', 'mine'],
    queryFn: () => fetchMyLabOrders(accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 30_000,
  });
  const { data: rx } = useQuery({
    queryKey: ['prescriptions', 'mine'],
    queryFn: () => fetchMyPrescriptions(accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 30_000,
  });

  const rows: Row[] = [
    ...(labs ?? []).map((l) => ({
      id: l.id as string,
      type: 'LAB' as const,
      patientId: l.patientId as string,
      label: l.labTestName ?? l.labTestCode ?? 'Lab test',
      status: l.status as string,
      occurredAt: l.orderedAt,
    })),
    ...(rx ?? []).map((r) => ({
      id: r.prescriptionLineId as string,
      type: 'RX' as const,
      patientId: r.patientId as string,
      label: `${r.itemName ?? r.itemCode ?? 'Drug'} × ${r.quantityPrescribed}`,
      status: r.status as string,
      occurredAt: r.prescribedAt,
    })),
  ]
    .filter((r) => statusFilter === 'ALL' || r.status === statusFilter)
    .filter((r) => typeFilter === 'ALL' || r.type === typeFilter)
    .sort((a, b) => new Date(b.occurredAt ?? 0).getTime() - new Date(a.occurredAt ?? 0).getTime());

  return (
    <div>
      <h1 style={{ fontSize: 'var(--text-xl)' }}>Request History</h1>

      <div style={{ display: 'flex', gap: 'var(--space-3)', margin: 'var(--space-3) 0' }}>
        <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value as typeof typeFilter)} style={selectStyle}>
          <option value="ALL">All types</option>
          <option value="LAB">Lab</option>
          <option value="RX">Prescription</option>
        </select>
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} style={selectStyle}>
          <option value="ALL">All statuses</option>
          <option value="PENDING_PAYMENT">Pending payment</option>
          <option value="AWAITING_COLLECTION">Awaiting collection</option>
          <option value="IN_PROGRESS">In progress</option>
          <option value="COMPLETED">Completed</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
      </div>

      {rows.length === 0 ? (
        <p style={{ color: 'var(--text-muted)' }}>No requests match this filter.</p>
      ) : (
        <table className="table">
          <tbody>
            {rows.map((row) => {
              const overdue =
                row.status === 'PENDING_PAYMENT' &&
                row.occurredAt != null &&
                Date.now() - new Date(row.occurredAt).getTime() > PENDING_PAYMENT_HIGHLIGHT_HOURS * 60 * 60 * 1000;
              return (
                <tr key={`${row.type}-${row.id}`} className={overdue ? 'row-critical' : ''}>
                  <td>
                    <div style={{ fontWeight: 'var(--weight-medium)' }}>
                      {formatDateTime(row.occurredAt)} <PatientName patientId={row.patientId} /> ·{' '}
                      <span className="badge badge-neutral">{row.type}</span> {row.label}
                    </div>
                  </td>
                  <td>
                    <span className={`badge ${statusBadgeClass(row.status)}`}>{statusLabel(row.status)}</span>
                    {overdue && <span style={{ color: 'var(--critical)', marginLeft: 8 }}>⚠ Unpaid — {formatWaitingSince(row.occurredAt)}</span>}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}

function PatientName({ patientId }: { patientId: string }) {
  const { accessToken } = useAuth();
  const { data: patient } = useQuery({
    queryKey: ['patients', patientId],
    queryFn: () => fetchPatientById(patientId, accessToken as string),
    enabled: accessToken != null,
    staleTime: 60_000,
  });
  return <>{patient?.fullName ?? 'Loading…'}</>;
}

function statusLabel(status: string): string {
  return status.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}

function statusBadgeClass(status: string): string {
  switch (status) {
    case 'PENDING_PAYMENT':
      return 'badge-warning';
    case 'COMPLETED':
      return 'badge-success';
    case 'CANCELLED':
      return 'badge-neutral';
    default:
      return 'badge-info';
  }
}

const selectStyle = { padding: 'var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)' };
