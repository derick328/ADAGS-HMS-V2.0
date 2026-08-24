import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  completeSurgeryOrder,
  fetchSurgeryOrderById,
  recordSurgeryDisposition,
  revokeSurgeryConsent,
  startSurgeryOrder,
  type SurgeryDisposition,
} from '../../shared/api/surgeryOrders';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { ApiError } from '../../shared/api/client';
import { formatDate, formatDateTime } from '../../shared/format/date';
import { SurgeryEpisodeAnaesthesia } from './SurgeryEpisodeAnaesthesia';
import { SurgeryEpisodeChecklist } from './SurgeryEpisodeChecklist';
import { SurgeryEpisodeProcedureItems } from './SurgeryEpisodeProcedureItems';
import { SurgeryEpisodeVitals } from './SurgeryEpisodeVitals';

type TabKey = 'checklist' | 'items' | 'anaesthesia' | 'vitals';
const TABS: { key: TabKey; label: string }[] = [
  { key: 'checklist', label: 'Safety checklist' },
  { key: 'items', label: 'Procedure items' },
  { key: 'anaesthesia', label: 'Anaesthesia' },
  { key: 'vitals', label: 'Post-op vitals' },
];

const DISPOSITIONS: SurgeryDisposition[] = ['RECOVERY_THEN_DISCHARGE', 'ADMIT_TO_WARD', 'TRANSFER_OUT', 'DECEASED'];

// 17-ward-nurse.md §10: episode documentation is nursing work; ordering is
// not (✗2) — SURGERY_ORDER_CREATE-gated actions (cancel, reconcile an
// emergency draft's procedure name) simply don't render for a nurse who
// doesn't hold that permission, same "hide, don't grey out" convention.
export function SurgeryEpisodePage() {
  const { surgeryOrderId } = useParams<{ surgeryOrderId: string }>();
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<TabKey>('checklist');
  const canDocument = usePermission('SURGERY_EPISODE_DOCUMENT');

  const { data: order, isLoading } = useQuery({
    queryKey: ['surgery-orders', surgeryOrderId],
    queryFn: () => fetchSurgeryOrderById(surgeryOrderId as string, accessToken as string),
    enabled: accessToken != null && surgeryOrderId != null,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['surgery-orders', surgeryOrderId] });

  const start = useMutation({
    mutationFn: () => startSurgeryOrder(surgeryOrderId as string, accessToken as string),
    onSuccess: invalidate,
  });
  const complete = useMutation({
    mutationFn: () => completeSurgeryOrder(surgeryOrderId as string, accessToken as string),
    onSuccess: invalidate,
  });
  const revokeConsent = useMutation({
    mutationFn: () => revokeSurgeryConsent(surgeryOrderId as string, accessToken as string),
    onSuccess: invalidate,
  });

  if (isLoading || !order || !surgeryOrderId) {
    return <p style={{ color: 'var(--text-muted)' }}>Loading…</p>;
  }

  const anyActionError = start.error || complete.error || revokeConsent.error;
  const errorDetail = [start.error, complete.error, revokeConsent.error].find((e) => e instanceof ApiError) as ApiError | undefined;

  return (
    <div>
      <button type="button" className="btn btn-ghost" style={{ marginBottom: 'var(--space-3)', paddingLeft: 0 }} onClick={() => navigate('/ward-nurse/surgery')}>
        ← Surgery Queue
      </button>

      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 'var(--space-4)' }}>
        <div>
          <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>
            {order.procedureName ?? 'Unreconciled emergency procedure'}
          </h1>
          <p style={{ color: 'var(--text-secondary)', margin: '4px 0 0' }}>
            {order.surgeryType} · {formatDate(order.scheduledDate)}
            {order.timeSlot && ` · ${order.timeSlot}`} ·{' '}
            {order.urgency === 'IMMEDIATE' ? <span className="badge badge-critical">IMMEDIATE</span> : order.urgency}
          </p>
        </div>
        <span className="badge badge-info">{order.status}</span>
      </header>

      {order.status === 'DRAFT_EMERGENCY' && (
        <div style={{ background: 'var(--warning-bg)', color: 'var(--warning)', borderRadius: 'var(--radius)', padding: 'var(--space-3)', marginBottom: 'var(--space-4)' }}>
          This is an unreconciled emergency draft — the ordering doctor must reconcile it to a catalogue procedure and
          post the charge before it can proceed.
        </div>
      )}

      {canDocument && order.status !== 'DRAFT_EMERGENCY' && (
        <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center', marginBottom: 'var(--space-4)', flexWrap: 'wrap' }}>
          <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
            Consent: {order.consentObtained ? <span className="badge badge-success">Obtained</span> : <span className="badge badge-warning">Not obtained</span>}
          </span>
          {order.consentObtained && order.status === 'SCHEDULED' && (
            <button type="button" className="btn btn-secondary" disabled={revokeConsent.isPending} onClick={() => revokeConsent.mutate()}>
              Revoke consent
            </button>
          )}
          {order.status === 'SCHEDULED' && (
            <button type="button" className="btn btn-primary" disabled={start.isPending} onClick={() => start.mutate()}>
              {start.isPending ? 'Starting…' : 'Start surgery'}
            </button>
          )}
          {order.status === 'IN_PROGRESS' && (
            <button type="button" className="btn btn-primary" disabled={complete.isPending} onClick={() => complete.mutate()}>
              {complete.isPending ? 'Completing…' : 'Mark complete'}
            </button>
          )}
        </div>
      )}

      {anyActionError && (
        <div className="form-error" style={{ marginBottom: 'var(--space-4)' }}>
          {errorDetail?.body?.detail ?? 'Something went wrong. Try again.'}
        </div>
      )}

      {order.status === 'COMPLETED' && !order.disposition && canDocument && (
        <DispositionForm surgeryOrderId={surgeryOrderId} onDone={invalidate} />
      )}
      {order.disposition && (
        <p style={{ marginBottom: 'var(--space-4)' }}>
          Disposition: <span className="badge badge-neutral">{order.disposition}</span>
          {order.dispositionRecordedAt && ` at ${formatDateTime(order.dispositionRecordedAt)}`}
        </p>
      )}

      <nav aria-label="Surgery episode tabs" style={{ display: 'flex', gap: 'var(--space-1)', borderBottom: '1px solid var(--border)', marginBottom: 'var(--space-4)' }}>
        {TABS.map((t) => {
          const isActive = t.key === tab;
          return (
            <button
              key={t.key}
              type="button"
              onClick={() => setTab(t.key)}
              style={{
                padding: 'var(--space-2) var(--space-3)',
                border: 'none',
                background: 'none',
                font: 'inherit',
                cursor: 'pointer',
                color: isActive ? 'var(--text-primary)' : 'var(--text-muted)',
                fontWeight: isActive ? 'var(--weight-medium)' : 'var(--weight-normal)',
                boxShadow: isActive ? 'inset 0 -2px 0 var(--green-signal)' : 'none',
              }}
            >
              {t.label}
            </button>
          );
        })}
      </nav>

      {tab === 'checklist' && <SurgeryEpisodeChecklist surgeryOrderId={surgeryOrderId} />}
      {tab === 'items' && <SurgeryEpisodeProcedureItems surgeryOrderId={surgeryOrderId} />}
      {tab === 'anaesthesia' && <SurgeryEpisodeAnaesthesia surgeryOrderId={surgeryOrderId} />}
      {tab === 'vitals' && <SurgeryEpisodeVitals surgeryOrderId={surgeryOrderId} encounterId={order.encounterId as string} />}
    </div>
  );
}

function DispositionForm({ surgeryOrderId, onDone }: { surgeryOrderId: string; onDone: () => void }) {
  const { accessToken } = useAuth();
  const [disposition, setDisposition] = useState<SurgeryDisposition>('RECOVERY_THEN_DISCHARGE');
  const [orderedWardClass, setOrderedWardClass] = useState('GENERAL');

  const submit = useMutation({
    mutationFn: () =>
      recordSurgeryDisposition(
        surgeryOrderId,
        { disposition, orderedWardClass: disposition === 'ADMIT_TO_WARD' ? orderedWardClass : undefined },
        accessToken as string,
      ),
    onSuccess: onDone,
  });

  return (
    <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)', marginBottom: 'var(--space-4)', maxWidth: 480 }}>
      <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 }}>Post-procedure disposition</h2>
      <div style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap', marginBottom: 'var(--space-3)' }}>
        {DISPOSITIONS.map((d) => (
          <button key={d} type="button" className={disposition === d ? 'btn btn-primary' : 'btn btn-secondary'} onClick={() => setDisposition(d)}>
            {d.replace(/_/g, ' ')}
          </button>
        ))}
      </div>
      {disposition === 'ADMIT_TO_WARD' && (
        <label style={{ fontSize: 'var(--text-sm)', display: 'block', marginBottom: 'var(--space-3)' }}>
          Ordered ward class
          <select value={orderedWardClass} onChange={(e) => setOrderedWardClass(e.target.value)} style={{ display: 'block', width: '100%', marginTop: 4, padding: 'var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', font: 'inherit' }}>
            <option value="GENERAL">General</option>
            <option value="PRIVATE">Private</option>
          </select>
        </label>
      )}
      {submit.error && <div className="form-error" style={{ marginBottom: 'var(--space-2)' }}>Something went wrong. Try again.</div>}
      <button type="button" className="btn btn-primary" disabled={submit.isPending} onClick={() => submit.mutate()}>
        {submit.isPending ? 'Saving…' : 'Record disposition'}
      </button>
    </div>
  );
}
