import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchPatientById } from '../../shared/api/patients';
import {
  fetchActiveSurgeryOrders,
  type SurgeryOrderResponse,
  type SurgeryOrderStatus,
} from '../../shared/api/surgeryOrders';
import { fetchEncounterById } from '../../shared/api/encounters';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { formatDate, formatDateTime } from '../../shared/format/date';

// 07-doctor.md §7: the doctor books a surgery from the consultation
// flow, the ward nurse takes over prep/documentation. This page is the
// doctor's read-only window into that ward-side work — surgeries this
// user actually scheduled, with live status so they can see what has
// started, what has completed, and what's still queued for theatre.
// Filter is client-side against SurgeryOrderResponse.scheduledBy
// (server /api/surgery-orders returns every active order — bounded and
// small per §10).
const URGENCY_RANK: Record<string, number> = { IMMEDIATE: 0, URGENT: 1, ELECTIVE: 2 };

type StatusFilter = 'ACTIVE' | SurgeryOrderStatus;

const STATUS_FILTERS: { key: StatusFilter; label: string }[] = [
  { key: 'ACTIVE', label: 'Active' },
  { key: 'SCHEDULED', label: 'Scheduled' },
  { key: 'IN_PROGRESS', label: 'In progress' },
  { key: 'COMPLETED', label: 'Completed' },
  { key: 'CANCELLED', label: 'Cancelled' },
];

export function MyScheduledSurgeriesPage() {
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  const navigate = useNavigate();
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ACTIVE');

  const { data, isLoading, isError } = useQuery({
    queryKey: ['surgery-orders', 'active'],
    queryFn: () => fetchActiveSurgeryOrders(accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 30_000,
  });

  const mine = useMemo(
    () => (data ?? []).filter((o) => o.scheduledBy === me?.userId),
    [data, me?.userId],
  );

  const counts = useMemo(() => bucketByStatus(mine), [mine]);

  const filtered = useMemo(() => {
    const sorted = [...mine].sort((a, b) => {
      const ur = (URGENCY_RANK[a.urgency ?? ''] ?? 9) - (URGENCY_RANK[b.urgency ?? ''] ?? 9);
      if (ur !== 0) return ur;
      return (a.scheduledDate ?? '').localeCompare(b.scheduledDate ?? '');
    });
    if (statusFilter === 'ACTIVE') {
      return sorted.filter((o) => o.status !== 'COMPLETED' && o.status !== 'CANCELLED');
    }
    return sorted.filter((o) => o.status === statusFilter);
  }, [mine, statusFilter]);

  return (
    <div>
      <header style={{ marginBottom: 'var(--space-3)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>
          My Scheduled Surgeries
        </h1>
        <p style={{ margin: '4px 0 0', fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
          Surgeries you scheduled from a consultation. Prep, checklists, and disposition happen on the ward
          nurse's screens — this is your read-only view.
        </p>
      </header>

      <div
        style={{
          display: 'flex',
          gap: 'var(--space-1)',
          flexWrap: 'wrap',
          marginBottom: 'var(--space-3)',
        }}
      >
        {STATUS_FILTERS.map((f) => (
          <button
            key={f.key}
            type="button"
            onClick={() => setStatusFilter(f.key)}
            className={statusFilter === f.key ? 'btn btn-secondary' : 'btn btn-ghost'}
            style={{ fontSize: 'var(--text-sm)' }}
          >
            {f.label}
            <span className="badge badge-neutral" style={{ marginLeft: 'var(--space-1)' }}>
              {counts[f.key] ?? 0}
            </span>
          </button>
        ))}
      </div>

      <div
        style={{
          background: 'var(--bg-surface)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-lg)',
          overflow: 'hidden',
        }}
      >
        {isLoading ? (
          <Empty message="Loading surgeries…" />
        ) : isError ? (
          <Empty message="Couldn't load surgery orders." />
        ) : filtered.length === 0 ? (
          <Empty
            message={
              mine.length === 0
                ? "You haven't scheduled any surgeries yet. Schedule from a consultation via the Schedule surgery disposition."
                : `No surgeries in "${STATUS_FILTERS.find((f) => f.key === statusFilter)?.label}".`
            }
          />
        ) : (
          <table className="table">
            <caption className="sr-only">My scheduled surgeries</caption>
            <thead>
              <tr>
                <th>Patient</th>
                <th>Procedure</th>
                <th>Urgency</th>
                <th>Scheduled</th>
                <th>Status</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((order) => (
                <SurgeryRow
                  key={order.id}
                  order={order}
                  onOpenPatient={(patientId) => navigate(`/doctor/patients/${patientId}`)}
                  onManageItems={(id) => navigate(`/doctor/surgery/${id}/items`)}
                />
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function bucketByStatus(rows: SurgeryOrderResponse[]): Record<StatusFilter, number> {
  const counts: Record<StatusFilter, number> = {
    ACTIVE: 0,
    DRAFT_EMERGENCY: 0,
    SCHEDULED: 0,
    IN_PROGRESS: 0,
    COMPLETED: 0,
    CANCELLED: 0,
  };
  for (const r of rows) {
    if (r.status && r.status !== 'COMPLETED' && r.status !== 'CANCELLED') counts.ACTIVE++;
    if (r.status) counts[r.status]++;
  }
  return counts;
}

function SurgeryRow({
  order,
  onOpenPatient,
  onManageItems,
}: {
  order: SurgeryOrderResponse;
  onOpenPatient: (patientId: string) => void;
  onManageItems: (surgeryOrderId: string) => void;
}) {
  const { accessToken } = useAuth();
  const { data: encounter } = useQuery({
    queryKey: ['encounters', order.encounterId],
    queryFn: () => fetchEncounterById(order.encounterId as string, accessToken as string),
    enabled: accessToken != null && !!order.encounterId,
    staleTime: 5 * 60_000,
  });
  const { data: patient } = useQuery({
    queryKey: ['patients', encounter?.patientId],
    queryFn: () => fetchPatientById(encounter!.patientId as string, accessToken as string),
    enabled: accessToken != null && !!encounter?.patientId,
    staleTime: 5 * 60_000,
  });

  return (
    <tr>
      <td>
        {patient ? (
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => encounter?.patientId && onOpenPatient(encounter.patientId as string)}
            style={{ textAlign: 'left', padding: 0 }}
          >
            {patient.fullName}
            {patient.hospitalNo && (
              <span style={{ color: 'var(--text-secondary)' }}> · {patient.hospitalNo}</span>
            )}
          </button>
        ) : (
          <span style={{ color: 'var(--text-muted)' }}>Loading…</span>
        )}
      </td>
      <td>
        {order.procedureName ?? order.procedureServiceItemCode ?? '—'}
        {order.surgeryType && (
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)' }}>{order.surgeryType}</div>
        )}
      </td>
      <td>
        <span className={`badge ${urgencyBadgeClass(order.urgency)}`}>{order.urgency ?? '—'}</span>
      </td>
      <td>
        {order.scheduledDate ? formatDate(order.scheduledDate) : '—'}
        {order.timeSlot && (
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)' }}>{order.timeSlot}</div>
        )}
      </td>
      <td>
        <span className={`badge ${statusBadgeClass(order.status)}`}>{formatStatus(order.status)}</span>
        {order.status === 'CANCELLED' && order.cancellationReason && (
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)' }}>
            {formatStatus(order.cancellationReason)}
          </div>
        )}
        {order.status === 'COMPLETED' && order.dispositionRecordedAt && (
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)' }}>
            {formatStatus(order.disposition)} · {formatDateTime(order.dispositionRecordedAt)}
          </div>
        )}
      </td>
      <td style={{ textAlign: 'right' }}>
        <div style={{ display: 'inline-flex', gap: 'var(--space-1)', flexWrap: 'wrap', justifyContent: 'flex-end' }}>
          {order.id && order.status !== 'CANCELLED' && (
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => onManageItems(order.id as string)}
            >
              Manage items
            </button>
          )}
          {encounter?.patientId && (
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => onOpenPatient(encounter.patientId as string)}
            >
              Open patient →
            </button>
          )}
        </div>
      </td>
    </tr>
  );
}

function urgencyBadgeClass(urgency: SurgeryOrderResponse['urgency']): string {
  switch (urgency) {
    case 'IMMEDIATE':
      return 'badge-critical';
    case 'URGENT':
      return 'badge-warning';
    default:
      return 'badge-neutral';
  }
}

function statusBadgeClass(status: SurgeryOrderResponse['status']): string {
  switch (status) {
    case 'IN_PROGRESS':
      return 'badge-warning';
    case 'COMPLETED':
      return 'badge-success';
    case 'CANCELLED':
      return 'badge-neutral';
    default:
      return 'badge-neutral';
  }
}

function formatStatus(value: string | undefined): string {
  if (!value) return '—';
  return value.replaceAll('_', ' ').toLowerCase();
}

function Empty({ message }: { message: string }) {
  return (
    <div style={{ padding: 'var(--space-6)', textAlign: 'center', color: 'var(--text-muted)' }}>{message}</div>
  );
}
