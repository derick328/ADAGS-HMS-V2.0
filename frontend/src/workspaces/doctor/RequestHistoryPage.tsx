import { FeedbackState } from '../../shared/components/FeedbackState';
import { TableRegion } from '../../shared/components/TableRegion';
import { WorkspacePage, QueryState } from '../../shared/components/WorkspacePage';
import { EmptyState } from '../../shared/components/EmptyState';
import { useQuery } from '@tanstack/react-query';
import { Fragment, useState } from 'react';
import { fetchDispensesByPrescriptionLine, type DispenseHistoryResponse } from '../../shared/api/dispenses';
import { fetchLabResultsByOrder, type LabResultDetailResponse, type DetailParameterValueResponse } from '../../shared/api/labResults';
import { fetchMyLabOrders } from '../../shared/api/labOrders';
import { fetchMyPrescriptions } from '../../shared/api/prescriptions';
import { fetchPatientById } from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDateTime, formatWaitingSince } from '../../shared/format/date';

// 08-doctor-navigation.md §5. Every lab request and prescription this
// doctor has raised, with live status and — new — expandable rows that
// pull the fulfilment detail from the lab / pharmacy side: what the
// technician typed into the parameters + notes, and which batch(es) the
// pharmacist actually dispensed and when.
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
  const [expanded, setExpanded] = useState<string | null>(null);

  const { data: labs, isPending: labsPending, isError: labsError, refetch: retryLabs } = useQuery({
    queryKey: ['lab-orders', 'mine'],
    queryFn: () => fetchMyLabOrders(accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 30_000,
  });
  const { data: rx, isPending: rxPending, isError: rxError, refetch: retryRx } = useQuery({
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
    <WorkspacePage title="Request history" description="Track your lab requests and prescriptions, and open a request to review its details.">

      <div className="workspace-toolbar">
        <select className="clinical-control" aria-label="Request type" value={typeFilter} onChange={(e) => setTypeFilter(e.target.value as typeof typeFilter)} style={selectStyle}>
          <option value="ALL">All types</option>
          <option value="LAB">Lab</option>
          <option value="RX">Prescription</option>
        </select>
        <select className="clinical-control" aria-label="Request status" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} style={selectStyle}>
          <option value="ALL">All statuses</option>
          <option value="PENDING_PAYMENT">Pending payment</option>
          <option value="AWAITING_COLLECTION">Awaiting collection</option>
          <option value="IN_PROGRESS">In progress</option>
          <option value="COMPLETED">Completed</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
      </div>

      {labsError || rxError ? (
        <QueryState title="Unable to load request history" description="Retry to load your lab requests and prescriptions." onRetry={() => { void retryLabs(); void retryRx(); }} />
      ) : labsPending || rxPending ? (
        <QueryState loading title="Loading request history…" />
      ) : rows.length === 0 ? (
        <div className="workspace-panel"><EmptyState role="status" title="No requests match these filters" description="Choose another type or status to review other requests." /></div>
      ) : (
        <div className="workspace-table" role="region" aria-label="Request history" tabIndex={0}><TableRegion><table className="table">
          <thead><tr><th scope="col">Request details</th><th scope="col">Status</th></tr></thead><tbody>
            {rows.map((row) => {
              const rowKey = `${row.type}-${row.id}`;
              const isOpen = expanded === rowKey;
              const overdue =
                row.status === 'PENDING_PAYMENT' &&
                row.occurredAt != null &&
                Date.now() - new Date(row.occurredAt).getTime() > PENDING_PAYMENT_HIGHLIGHT_HOURS * 60 * 60 * 1000;
              return (
                <Fragment key={rowKey}>
                  <tr className={overdue ? 'row-critical' : ''}>
                    <td>
                      <button
                        type="button"
                        onClick={() => setExpanded(isOpen ? null : rowKey)}
                        aria-expanded={isOpen}
                        aria-label={isOpen ? 'Hide details' : 'Show details'}
                        style={{
                          background: 'none',
                          border: 'none',
                          font: 'inherit',
                          textAlign: 'left',
                          padding: 0,
                          cursor: 'pointer',
                          fontWeight: 'var(--weight-medium)',
                        }}
                      >
                        <span style={{ display: 'inline-block', width: 14 }}>{isOpen ? '▾' : '▸'}</span>
                        {formatDateTime(row.occurredAt)} <PatientName patientId={row.patientId} /> ·{' '}
                        <span className="badge badge-neutral">{row.type}</span> {row.label}
                      </button>
                    </td>
                    <td>
                      <span className={`badge ${statusBadgeClass(row.status)}`}>{statusLabel(row.status)}</span>
                      {overdue && <span style={{ color: 'var(--critical)', marginLeft: 8 }}>⚠ Unpaid — {formatWaitingSince(row.occurredAt)}</span>}
                    </td>
                  </tr>
                  {isOpen && (
                    <tr>
                      <td colSpan={2} style={{ background: 'var(--bg-subtle)', padding: 'var(--space-4)' }}>
                        {row.type === 'LAB' ? <LabDetail labOrderId={row.id} /> : <DispenseDetail prescriptionLineId={row.id} />}
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </table></TableRegion></div>
      )}
    </WorkspacePage>
  );
}

// One lab order can carry multiple LabResult rows (original + amendments).
// We show them newest-first, matching the /by-lab-order endpoint's order,
// with the current (top) row expanded by default because that's the one
// the doctor almost always wants to read.
function LabDetail({ labOrderId }: { labOrderId: string }) {
  const { accessToken } = useAuth();
  const { data, isLoading, isError } = useQuery({
    queryKey: ['lab-results', 'by-lab-order', labOrderId],
    queryFn: () => fetchLabResultsByOrder(labOrderId, accessToken as string),
    enabled: accessToken != null,
  });

  if (isLoading) return <FeedbackState loading>Loading result…</FeedbackState>;
  if (isError) return <p style={{ color: 'var(--critical)' }}>Could not load the result.</p>;
  const results = data ?? [];
  if (results.length === 0) {
    return <p style={{ color: 'var(--text-muted)' }}>The lab has not released a result yet.</p>;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
      {results.map((result, index) => (
        <LabResultCard key={result.id ?? index} result={result} superseded={index > 0} />
      ))}
    </div>
  );
}

function LabResultCard({ result, superseded }: { result: LabResultDetailResponse; superseded: boolean }) {
  return (
    <div
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius)',
        padding: 'var(--space-3)',
        opacity: superseded ? 0.7 : 1,
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 'var(--space-2)', flexWrap: 'wrap', gap: 'var(--space-2)' }}>
        <div>
          <strong>{result.labTestName}</strong>
          {result.accessionNo && (
            <span style={{ marginLeft: 'var(--space-2)', fontFamily: 'var(--font-mono)', fontSize: 'var(--text-xs)', color: 'var(--text-muted)' }}>
              {result.accessionNo}
            </span>
          )}
        </div>
        <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
          {superseded && <span style={{ marginRight: 'var(--space-2)' }} className="badge badge-neutral">Superseded</span>}
          Released {formatDateTime(result.releasedAt)} by {result.enteredByName ?? 'lab tech'}
        </div>
      </div>

      {result.amendmentReason && (
        <p style={{ margin: '0 0 var(--space-2)', fontSize: 'var(--text-sm)', color: 'var(--warning)' }}>
          Amended — {result.amendmentReason}
        </p>
      )}

      {(result.parameterValues?.length ?? 0) > 0 && (
        <TableRegion><table className="table" style={{ marginBottom: 'var(--space-3)' }}>
          <thead>
            <tr>
              <th>Parameter</th>
              <th>Result</th>
              <th>Reference range</th>
              <th>Flag</th>
            </tr>
          </thead>
          <tbody>
            {(result.parameterValues ?? []).map((p, i) => (
              <ParameterRow key={p.parameterId ?? i} p={p} />
            ))}
          </tbody>
        </table></TableRegion>
      )}

      <NotesBlock result={result} />
    </div>
  );
}

function ParameterRow({ p }: { p: DetailParameterValueResponse }) {
  const range = p.normalLow != null && p.normalHigh != null ? `${p.normalLow} – ${p.normalHigh}` : '—';
  const flagClass =
    p.flag === 'CRITICAL' ? 'badge-critical' :
    p.flag === 'HIGH' || p.flag === 'LOW' ? 'badge-warning' :
    'badge-success';
  return (
    <tr>
      <td>
        {p.parameterName}
        {p.unit && <span style={{ color: 'var(--text-muted)' }}> ({p.unit})</span>}
      </td>
      <td className="tabular"><strong>{p.resultValue}</strong></td>
      <td className="tabular" style={{ color: 'var(--text-muted)' }}>{range}</td>
      <td>
        {p.flag && p.flag !== 'NONE' && <span className={`badge ${flagClass}`}>{p.flag}</span>}
        {p.flagOverridden && p.overrideReason && (
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)', marginTop: 2 }}>
            Overridden — {p.overrideReason}
          </div>
        )}
      </td>
    </tr>
  );
}

function NotesBlock({ result }: { result: LabResultDetailResponse }) {
  const notes: [string, string | undefined][] = [
    ['Methodology', result.methodology],
    ['Reagents used', result.reagentsUsedNote],
    ['Findings', result.findings],
    ['Interpretation', result.interpretation],
    ['Reference range note', result.referenceRangeNote],
    ['Conclusion', result.conclusion],
  ];
  const populated = notes.filter(([, value]) => value && value.trim().length > 0);
  if (populated.length === 0) {
    return <FeedbackState>No notes recorded.</FeedbackState>;
  }
  return (
    <dl className="clinical-grid" style={{ display: 'grid', gridTemplateColumns: 'max-content 1fr', gap: '4px var(--space-3)', margin: 0 }}>
      {populated.map(([label, value]) => (
        <div key={label} style={{ display: 'contents' }}>
          <dt style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>{label}</dt>
          <dd style={{ margin: 0 }}>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function DispenseDetail({ prescriptionLineId }: { prescriptionLineId: string }) {
  const { accessToken } = useAuth();
  const { data, isLoading, isError } = useQuery({
    queryKey: ['dispenses', 'by-prescription-line', prescriptionLineId],
    queryFn: () => fetchDispensesByPrescriptionLine(prescriptionLineId, accessToken as string),
    enabled: accessToken != null,
  });

  if (isLoading) return <FeedbackState loading>Loading dispense record…</FeedbackState>;
  if (isError) return <p style={{ color: 'var(--critical)' }}>Could not load the dispense record.</p>;
  const dispenses = data ?? [];
  if (dispenses.length === 0) {
    return <p style={{ color: 'var(--text-muted)' }}>The pharmacy has not dispensed this line yet.</p>;
  }

  const totalDispensed = dispenses.reduce((sum, d) => sum + (d.quantityDispensed ?? 0), 0);

  return (
    <div>
      <p style={{ margin: '0 0 var(--space-2)', color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>
        Dispensed <strong>{totalDispensed}</strong> unit{totalDispensed === 1 ? '' : 's'} across {dispenses.length}{' '}
        {dispenses.length === 1 ? 'event' : 'events'}.
      </p>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
        {dispenses.map((d) => (
          <DispenseCard key={d.dispenseId} d={d} />
        ))}
      </div>
    </div>
  );
}

function DispenseCard({ d }: { d: DispenseHistoryResponse }) {
  return (
    <div
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius)',
        padding: 'var(--space-3)',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 'var(--space-2)', flexWrap: 'wrap', gap: 'var(--space-2)' }}>
        <div>
          <strong>{d.itemName ?? 'Unknown item'}</strong>
          <span style={{ marginLeft: 'var(--space-2)', color: 'var(--text-secondary)' }}>× {d.quantityDispensed}</span>
        </div>
        <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
          Dispensed {formatDateTime(d.dispensedAt)} by {d.dispensedByUsername ?? 'pharmacist'}
        </div>
      </div>
      {d.counsellingNotes && (
        <p style={{ margin: '0 0 4px', fontSize: 'var(--text-sm)' }}>
          <span style={{ color: 'var(--text-muted)' }}>Counselling notes: </span>
          {d.counsellingNotes}
        </p>
      )}
      {d.dispensingNotes && (
        <p style={{ margin: 0, fontSize: 'var(--text-sm)' }}>
          <span style={{ color: 'var(--text-muted)' }}>Dispensing notes: </span>
          {d.dispensingNotes}
        </p>
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
