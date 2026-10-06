import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { fetchActiveSurgeryOrders, type SurgeryOrderResponse } from '../../shared/api/surgeryOrders';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { formatDate } from '../../shared/format/date';

const URGENCY_RANK: Record<string, number> = { IMMEDIATE: 0, URGENT: 1, ELECTIVE: 2 };

// 17-ward-nurse.md §10: surgical episodes this nurse documents against —
// ordering is the doctor's (✗2), so this queue is every open order, not
// ones this nurse created. IMMEDIATE sorts first, same emergency-first
// convention as every other queue in this codebase.
export function SurgeryQueuePage() {
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  const navigate = useNavigate();
  const { data, isLoading } = useQuery({
    queryKey: ['surgery-orders', 'active'],
    queryFn: () => fetchActiveSurgeryOrders(accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 15_000,
  });

  const rows = [...(data ?? [])].sort((a, b) => {
    const ur = (URGENCY_RANK[a.urgency ?? ''] ?? 9) - (URGENCY_RANK[b.urgency ?? ''] ?? 9);
    if (ur !== 0) return ur;
    return new Date(a.scheduledDate ?? 0).getTime() - new Date(b.scheduledDate ?? 0).getTime();
  });

  return (
    <div>
      <PageHeader title={<>Surgery queue</>} description={<>Track scheduled procedures, urgency, and consent status.</>} />

      <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
        {isLoading ? (
          <FeedbackState loading>Loading…</FeedbackState>
        ) : rows.length === 0 ? (
          <FeedbackState>No open surgical episodes.</FeedbackState>
        ) : (
          <TableRegion label="Surgery queue"><table className="table">
            <caption className="sr-only">Surgery queue</caption>
            <thead>
              <tr>
                <th>Procedure</th>
                <th>Urgency</th>
                <th>Scheduled</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((order) => (
                <SurgeryRow key={order.id} order={order} assignedToMe={(order.assignedNurses ?? []).includes(me?.userId ?? '')} onOpen={() => navigate(`/ward-nurse/surgery/${order.id}`)} />
              ))}
            </tbody>
          </table></TableRegion>
        )}
      </div>
    </div>
  );
}

function SurgeryRow({ order, assignedToMe, onOpen }: { order: SurgeryOrderResponse; assignedToMe: boolean; onOpen: () => void }) {
  const isImmediate = order.urgency === 'IMMEDIATE';
  return (
    <tr className={isImmediate ? 'row-critical' : undefined}>
      <td style={{ fontWeight: 'var(--weight-medium)' }}>
        {order.procedureName ?? 'Unreconciled emergency procedure'}
        {assignedToMe && <span className="badge badge-signal" style={{ marginLeft: 'var(--space-2)' }}>You</span>}
      </td>
      <td>
        {isImmediate ? <span className="badge badge-critical">IMMEDIATE</span> : <span className="badge badge-neutral">{order.urgency}</span>}
      </td>
      <td>
        {formatDate(order.scheduledDate)}
        {order.timeSlot && ` · ${order.timeSlot}`}
      </td>
      <td>
        <span className="badge badge-info">{order.status}</span>
        {!order.consentObtained && order.status !== 'DRAFT_EMERGENCY' && <span className="badge badge-warning" style={{ marginLeft: 4 }}>No consent</span>}
      </td>
      <td>
        <button type="button" className="btn btn-secondary" onClick={onOpen}>
          Open
        </button>
      </td>
    </tr>
  );
}
