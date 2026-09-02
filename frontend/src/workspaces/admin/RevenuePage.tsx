import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState, type CSSProperties } from 'react';
import {
  fetchPendingCancellationRequests,
  reviewCancellationRequest,
  type InvoiceCancellationRequestResponse,
} from '../../shared/api/billing';
import { fetchReceivablesOverview, fetchRevenueReport } from '../../shared/api/finance';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useAdminFacilityId } from '../../shared/auth/useFacilityScope';
import { usePermission } from '../../shared/auth/usePermission';
import { QueueShell, type QueueRow } from '../../shared/components/QueueShell';
import { formatMoney } from '../../shared/format/money';

// 18-admin.md §6: the admin's half of the cancellation lifecycle —
// receptionist requests, admin decides. Alongside the pending queue this
// screen also shows the revenue snapshot (cash vs. insurance vs. bypass
// debt — never summed, per 02-billing-architecture.md §13) and the
// two-bucket receivables split so an admin approving a cancellation can
// see what it will do to the period's numbers without navigating away.
export function RevenuePage() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const canApprove = usePermission('INVOICE_CANCEL_APPROVE');
  const canReadReports = usePermission('FINANCIAL_REPORT_READ');
  const facilityId = useAdminFacilityId();
  const [reviewingId, setReviewingId] = useState<string | null>(null);
  const [reviewNote, setReviewNote] = useState('');
  const [rangePreset, setRangePreset] = useState<'MTD' | 'LAST_30' | 'YTD'>('MTD');

  const range = useMemo(() => resolveRange(rangePreset), [rangePreset]);

  const { data: requests, isLoading } = useQuery({
    queryKey: ['cancellation-requests', 'pending'],
    queryFn: () => fetchPendingCancellationRequests(accessToken as string),
    enabled: accessToken != null,
  });

  const { data: revenue, isLoading: revenueLoading } = useQuery({
    queryKey: ['reporting', 'revenue', range.from, range.to, facilityId],
    queryFn: () =>
      fetchRevenueReport(range.from, range.to, facilityId as string, accessToken as string),
    enabled: accessToken != null && canReadReports && !!facilityId,
  });

  const { data: receivables } = useQuery({
    queryKey: ['reporting', 'receivables-overview', facilityId],
    queryFn: () => fetchReceivablesOverview(facilityId as string, accessToken as string),
    enabled: accessToken != null && canReadReports && !!facilityId,
  });

  const review = useMutation({
    mutationFn: (vars: { id: string; decision: 'APPROVED' | 'REJECTED' }) =>
      reviewCancellationRequest(vars.id, { decision: vars.decision, reviewNote }, accessToken as string),
    onSuccess: () => {
      setReviewingId(null);
      setReviewNote('');
      queryClient.invalidateQueries({ queryKey: ['cancellation-requests'] });
      queryClient.invalidateQueries({ queryKey: ['reporting'] });
    },
  });

  const reviewingRequest = requests?.find((r) => r.id === reviewingId);

  const rows: QueueRow[] = (requests ?? []).map((request) => ({
    id: request.id ?? '',
    primary: <span title={request.invoiceId}>Invoice {shortId(request.invoiceId)}</span>,
    secondary: request.reason,
    meta: formatRequestedAt(request.requestedAt),
    action: canApprove ? (
      <button type="button" className="btn btn-secondary" onClick={() => setReviewingId(request.id ?? null)}>
        Review
      </button>
    ) : null,
  }));

  return (
    <div>
      {canReadReports && (
        <section style={{ marginBottom: 'var(--space-5)' }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'baseline',
              marginBottom: 'var(--space-3)',
            }}
          >
            <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', margin: 0 }}>
              Revenue snapshot
            </h2>
            <RangeTabs value={rangePreset} onChange={setRangePreset} />
          </div>

          {!facilityId ? (
            <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
              Grant yourself a facility-scoped role to see revenue and receivables.
            </p>
          ) : (
            <>
              <div style={cardGridStyle}>
                <StatCard
                  label="Cash collected"
                  value={
                    revenueLoading
                      ? '…'
                      : revenue
                      ? formatMoney(revenue.collectedCashMinor, revenue.currencyCode)
                      : '—'
                  }
                  hint="Settled patient payments"
                />
                <StatCard
                  label="Insurance billed"
                  value={
                    revenueLoading
                      ? '…'
                      : revenue
                      ? formatMoney(revenue.insuranceBilledMinor, revenue.currencyCode)
                      : '—'
                  }
                  hint="Not cash until remittance clears"
                />
                <StatCard
                  label="Emergency bypass debt"
                  value={
                    revenueLoading
                      ? '…'
                      : revenue
                      ? formatMoney(revenue.emergencyBypassDebtMinor, revenue.currencyCode)
                      : '—'
                  }
                  hint="Point-in-time balance"
                />
              </div>
              {receivables && (
                <div style={{ ...cardGridStyle, marginTop: 'var(--space-3)' }}>
                  <StatCard
                    label="Post-discharge outstanding"
                    value={formatMoney(receivables.postDischargeOutstandingMinor, receivables.currencyCode)}
                    hint={`${receivables.postDischargeInvoiceCount ?? 0} invoice(s)`}
                    tone={(receivables.postDischargeOutstandingMinor ?? 0) > 0 ? 'warning' : 'neutral'}
                  />
                  <StatCard
                    label="Active encounters outstanding"
                    value={formatMoney(receivables.activeEncounterOutstandingMinor, receivables.currencyCode)}
                    hint={`${receivables.activeEncounterInvoiceCount ?? 0} invoice(s) — governed by the billing gate`}
                  />
                </div>
              )}
            </>
          )}
        </section>
      )}

      <QueueShell
        title="Invoice cancellations"
        rows={rows}
        isLoading={isLoading}
        emptyMessage="No pending cancellation requests"
      />

      {reviewingRequest && (
        <ReviewPanel
          request={reviewingRequest}
          reviewNote={reviewNote}
          onNoteChange={setReviewNote}
          onApprove={() => review.mutate({ id: reviewingRequest.id as string, decision: 'APPROVED' })}
          onReject={() => review.mutate({ id: reviewingRequest.id as string, decision: 'REJECTED' })}
          onCancel={() => {
            setReviewingId(null);
            setReviewNote('');
          }}
          isSubmitting={review.isPending}
        />
      )}
    </div>
  );
}

function RangeTabs({
  value,
  onChange,
}: {
  value: 'MTD' | 'LAST_30' | 'YTD';
  onChange: (v: 'MTD' | 'LAST_30' | 'YTD') => void;
}) {
  const options: { key: 'MTD' | 'LAST_30' | 'YTD'; label: string }[] = [
    { key: 'MTD', label: 'This month' },
    { key: 'LAST_30', label: 'Last 30 days' },
    { key: 'YTD', label: 'Year to date' },
  ];
  return (
    <div style={{ display: 'flex', gap: 'var(--space-1)' }}>
      {options.map((option) => (
        <button
          key={option.key}
          type="button"
          onClick={() => onChange(option.key)}
          className={value === option.key ? 'btn btn-secondary' : 'btn btn-ghost'}
          style={{ fontSize: 'var(--text-sm)' }}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

function StatCard({
  label,
  value,
  hint,
  tone = 'neutral',
}: {
  label: string;
  value: string;
  hint?: string;
  tone?: 'neutral' | 'warning';
}) {
  return (
    <div
      style={{
        background: 'var(--bg-surface)',
        border: `1px solid ${tone === 'warning' ? 'var(--warning)' : 'var(--border)'}`,
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-4)',
      }}
    >
      <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>{label}</div>
      <div
        style={{
          marginTop: 'var(--space-2)',
          fontSize: 'var(--text-xl)',
          fontWeight: 'var(--weight-medium)',
          color: tone === 'warning' ? 'var(--warning)' : 'var(--text-primary)',
        }}
        className="money"
      >
        {value}
      </div>
      {hint && (
        <div style={{ marginTop: 'var(--space-1)', fontSize: 'var(--text-xs)', color: 'var(--text-muted)' }}>
          {hint}
        </div>
      )}
    </div>
  );
}

function ReviewPanel({
  request,
  reviewNote,
  onNoteChange,
  onApprove,
  onReject,
  onCancel,
  isSubmitting,
}: {
  request: InvoiceCancellationRequestResponse;
  reviewNote: string;
  onNoteChange: (value: string) => void;
  onApprove: () => void;
  onReject: () => void;
  onCancel: () => void;
  isSubmitting: boolean;
}) {
  return (
    <div
      style={{
        marginTop: 'var(--space-4)',
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-4)',
        maxWidth: 480,
      }}
    >
      <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 }}>
        Review cancellation request
      </h2>
      <dl style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
        <dt style={{ fontWeight: 'var(--weight-medium)' }}>Invoice</dt>
        <dd style={{ margin: '0 0 var(--space-2)' }}>{request.invoiceId}</dd>
        <dt style={{ fontWeight: 'var(--weight-medium)' }}>Reason given</dt>
        <dd style={{ margin: '0 0 var(--space-2)' }}>{request.reason}</dd>
        <dt style={{ fontWeight: 'var(--weight-medium)' }}>Requested</dt>
        <dd style={{ margin: 0 }}>{formatRequestedAt(request.requestedAt)}</dd>
      </dl>

      <label htmlFor="review-note" style={{ display: 'block', marginTop: 'var(--space-3)', fontSize: 'var(--text-sm)' }}>
        Review note
      </label>
      <textarea
        id="review-note"
        value={reviewNote}
        onChange={(e) => onNoteChange(e.target.value)}
        rows={3}
        style={{
          width: '100%',
          marginTop: 'var(--space-1)',
          padding: 'var(--space-2)',
          border: '1px solid var(--border-strong)',
          borderRadius: 'var(--radius)',
          font: 'inherit',
        }}
        placeholder="Required if you reject this request"
      />

      <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-4)' }}>
        <button type="button" className="btn btn-primary" onClick={onApprove} disabled={isSubmitting}>
          Approve
        </button>
        <button
          type="button"
          className="btn btn-danger"
          onClick={onReject}
          disabled={isSubmitting || reviewNote.trim() === ''}
        >
          Reject
        </button>
        <button type="button" className="btn btn-ghost" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </button>
      </div>
    </div>
  );
}

const cardGridStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
  gap: 'var(--space-3)',
};

function resolveRange(preset: 'MTD' | 'LAST_30' | 'YTD'): { from: string; to: string } {
  const now = new Date();
  const to = toIsoDate(now);
  if (preset === 'MTD') {
    return { from: toIsoDate(new Date(now.getFullYear(), now.getMonth(), 1)), to };
  }
  if (preset === 'YTD') {
    return { from: toIsoDate(new Date(now.getFullYear(), 0, 1)), to };
  }
  const last30 = new Date(now);
  last30.setDate(last30.getDate() - 29);
  return { from: toIsoDate(last30), to };
}

function toIsoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

function shortId(id: string | undefined): string {
  return id ? id.slice(0, 8) : '—';
}

function formatRequestedAt(value: string | undefined): string {
  if (!value) return '—';
  return new Intl.DateTimeFormat('en-TZ', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Africa/Dar_es_Salaam',
  }).format(new Date(value));
}
