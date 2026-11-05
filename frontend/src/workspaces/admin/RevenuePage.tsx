import { PageHeader } from '../../shared/components/PageHeader';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import {
  Banknote,
  FileCheck2,
  Percent,
  Receipt,
  Scissors,
  Undo2,
  Wallet,
} from 'lucide-react';
import {
  fetchPendingCancellationRequests,
  reviewCancellationRequest,
  type InvoiceCancellationRequestResponse,
} from '../../shared/api/billing';
import {
  fetchRevenueAdjustments,
  fetchRevenueByCategory,
  fetchRevenueSummary,
  fetchRevenueTrend,
  fetchTopRevenueItems,
  type RevenueCategoryKey,
  type TopServiceItemLine,
} from '../../shared/api/revenueAnalytics';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useAdminFacilityId } from '../../shared/auth/useFacilityScope';
import { usePermission } from '../../shared/auth/usePermission';
import { QueueShell, type QueueRow } from '../../shared/components/QueueShell';
import { TableRegion } from '../../shared/components/TableRegion';
import { CategoryBarChart } from '../../shared/components/charts/CategoryBarChart';
import { ChartCard } from '../../shared/components/charts/ChartCard';
import { KpiCard } from '../../shared/components/charts/KpiCard';
import { RankedBarChart } from '../../shared/components/charts/RankedBarChart';
import { RangeFilter } from '../../shared/components/charts/RangeFilter';
import {
  granularityLabel,
  resolveRange,
  toLocalIsoDate,
  type DateRange,
  type RangePreset,
} from '../../shared/components/charts/dateRange';
import { TrendAreaChart } from '../../shared/components/charts/TrendAreaChart';
import { formatMoney } from '../../shared/format/money';

// 18-admin.md §6 + §10. Two jobs on one screen, in this order: read the
// period's money, then decide the cancellations waiting on an admin. The
// analytics are deliberately deeper than /admin's overview rather than a
// second copy of it — longer top-N lists, procedure revenue, a billed/
// collected split per category with percentages, and a period the admin
// chooses.
//
// Every figure's basis is printed next to it. Collected cash and insurance
// billed are never summed (02-billing-architecture.md §13), and nothing here
// subtracts collected from billed to produce "outstanding" — outstanding is
// its own point-in-time balance from invoice.outstanding_total_minor.
//
// Emergency bypass debt is not presented here any more. The figure, its
// endpoint and the bypass mechanism are all intact; it was a point-in-time
// receivable sitting in a row of period flows, which is how a reader ends up
// adding it to one.
const TOP_N = 10;

export function RevenuePage() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const canApprove = usePermission('INVOICE_CANCEL_APPROVE');
  const canReadReports = usePermission('FINANCIAL_REPORT_READ');
  const facilityId = useAdminFacilityId();

  const [reviewingId, setReviewingId] = useState<string | null>(null);
  const [reviewNote, setReviewNote] = useState('');
  const [preset, setPreset] = useState<RangePreset>('MTD');
  const [custom, setCustom] = useState<DateRange>(() => {
    const today = toLocalIsoDate(new Date());
    return { from: today, to: today };
  });

  const range = useMemo(() => resolveRange(preset, custom), [preset, custom]);
  const reportsEnabled = accessToken != null && canReadReports && !!facilityId;
  const rangeKey = [range.from, range.to, facilityId] as const;

  const summary = useQuery({
    queryKey: ['reporting', 'revenue', 'summary', ...rangeKey],
    queryFn: () => fetchRevenueSummary(range, facilityId as string, accessToken as string),
    enabled: reportsEnabled,
  });

  const trend = useQuery({
    queryKey: ['reporting', 'revenue', 'trend', ...rangeKey],
    queryFn: () => fetchRevenueTrend(range, facilityId as string, accessToken as string),
    enabled: reportsEnabled,
  });

  const byCategory = useQuery({
    queryKey: ['reporting', 'revenue', 'by-category', ...rangeKey],
    queryFn: () => fetchRevenueByCategory(range, facilityId as string, accessToken as string),
    enabled: reportsEnabled,
  });

  const adjustments = useQuery({
    queryKey: ['reporting', 'revenue', 'adjustments', ...rangeKey],
    queryFn: () => fetchRevenueAdjustments(range, facilityId as string, accessToken as string),
    enabled: reportsEnabled,
  });

  const surgeries = useTopItems('SURGERY', range, facilityId, accessToken, reportsEnabled);
  const procedures = useTopItems('THEATRE_PROCEDURE', range, facilityId, accessToken, reportsEnabled);
  const medicines = useTopItems('PHARMACY', range, facilityId, accessToken, reportsEnabled);
  const labTests = useTopItems('LABORATORY', range, facilityId, accessToken, reportsEnabled);

  const requests = useQuery({
    queryKey: ['cancellation-requests', 'pending'],
    queryFn: () => fetchPendingCancellationRequests(accessToken as string),
    enabled: accessToken != null,
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

  const reviewingRequest = requests.data?.find((r) => r.id === reviewingId);
  const rows: QueueRow[] = (requests.data ?? []).map((request) => ({
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

  const currency = summary.data?.currencyCode;
  const collectionRate = rateOf(summary.data?.cohortSettledMinor, summary.data?.cohortPatientPortionBilledMinor);
  const categoryBilledTotal = (byCategory.data ?? []).reduce((sum, line) => sum + (line.billedMinor ?? 0), 0);
  // Credit notes + write-offs only. Refunds are excluded: they move cash back
  // out, they do not forgive a charge, and they are already netted out of
  // Collected — adding them here would deduct the same money twice.
  const adjustmentsTotal =
    (adjustments.data?.creditNotesAppliedMinor ?? 0) + (adjustments.data?.writeOffsAppliedMinor ?? 0);
  const trendGranularity = granularityLabel(range);

  return (
    <div className="analytics-page">
      <PageHeader
        title="Revenue analytics"
        description="Billed versus collected for the period you choose, by service, procedure, medicine and test — then the cancellations waiting on you."
      />

      {canReadReports && (
        <RangeFilter preset={preset} custom={custom} onPresetChange={setPreset} onCustomChange={setCustom} />
      )}

      {!canReadReports ? null : !facilityId ? (
        <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
          Grant yourself a facility-scoped role to see revenue and receivables.
        </p>
      ) : (
        <>
          {/* Row 1 — KPIs */}
          <div className="kpi-row">
            <KpiCard
              label="Collected"
              value={summary.isPending ? '…' : formatMoney(summary.data?.collectedCashMinor, currency)}
              basis="Cash received in period, net of refunds"
              icon={Banknote}
              tone="positive"
            />
            <KpiCard
              label="Billed"
              value={summary.isPending ? '…' : formatMoney(summary.data?.grossBilledMinor, currency)}
              basis={`Charges raised in period · ${formatMoney(
                summary.data?.insuranceBilledMinor,
                currency,
              )} of it insurance`}
              icon={Receipt}
            />
            <KpiCard
              label="Outstanding"
              value={summary.isPending ? '…' : formatMoney(summary.data?.outstandingTotalMinor, currency)}
              basis="Owed now, all periods — not period-bounded"
              icon={Wallet}
              tone={(summary.data?.outstandingTotalMinor ?? 0) > 0 ? 'warning' : 'neutral'}
            />
            <KpiCard
              label="Settled invoices"
              value={summary.isPending ? '…' : (summary.data?.settledInvoiceCount ?? 0)}
              basis={`of ${summary.data?.issuedInvoiceCount ?? 0} issued — partials excluded`}
              icon={FileCheck2}
            />
            <KpiCard
              label="Collection rate"
              value={summary.isPending ? '…' : collectionRate}
              basis="Patient portion billed in period, settled to date"
              icon={Percent}
            />
          </div>

          {/* Row 2 — main trend */}
          <div className="analytics-row analytics-row--single">
            <ChartCard
              title="Revenue billed and collected"
              basis={`${trendGranularity}. Billed by charge date, collected by payment date — the two series answer different questions and are never added together.`}
              isLoading={trend.isPending}
              isError={trend.isError}
              onRetry={() => void trend.refetch()}
              isEmpty={(trend.data ?? []).every((p) => !p.billedMinor && !p.collectedMinor)}
              emptyMessage="No billing or collection activity in this period."
            >
              <TrendAreaChart
                granularityLabel={trendGranularity}
                currencyCode={trend.data?.[0]?.currencyCode}
                points={(trend.data ?? []).map((point) => ({
                  bucket: point.bucketStart ?? '',
                  billed: point.billedMinor ?? 0,
                  collected: point.collectedMinor ?? 0,
                }))}
              />
            </ChartCard>
          </div>

          {/* Row 3 — category breakdown + reconciliation table */}
          <div className="analytics-row">
            <ChartCard
              title="Revenue by service category"
              basis="Category comes from the billing catalogue; each charge falls in exactly one, so the bars sum to the period total."
              isLoading={byCategory.isPending}
              isError={byCategory.isError}
              onRetry={() => void byCategory.refetch()}
              isEmpty={(byCategory.data ?? []).every((line) => !line.billedMinor && !line.collectedMinor)}
              emptyMessage="No charges or payments in this period."
            >
              <CategoryBarChart
                primaryLabel="Collected"
                secondaryLabel="Billed"
                currencyCode={byCategory.data?.[0]?.currencyCode}
                bars={(byCategory.data ?? []).map((line) => ({
                  id: line.category ?? '',
                  label: line.categoryLabel ?? '',
                  primary: line.collectedMinor ?? 0,
                  secondary: line.billedMinor ?? 0,
                }))}
              />
            </ChartCard>

            <ChartCard
              title="Collection analysis by category"
              basis="Billed is GROSS — credit notes and write-offs are invoice-level and are not deducted from any category. Share is of billed in the period."
              isLoading={byCategory.isPending}
              isError={byCategory.isError}
              onRetry={() => void byCategory.refetch()}
              isEmpty={(byCategory.data ?? []).every((line) => !line.billedMinor && !line.collectedMinor)}
              emptyMessage="No charges or payments in this period."
            >
              <TableRegion>
                <table className="table">
                  <thead>
                    <tr>
                      <th scope="col">Category</th>
                      <th scope="col">Billed</th>
                      <th scope="col">Share</th>
                      <th scope="col">Collected</th>
                      <th scope="col">Charges</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(byCategory.data ?? []).map((line) => (
                      <tr key={line.category}>
                        <td>{line.categoryLabel}</td>
                        <td className="money tabular">{formatMoney(line.billedMinor, line.currencyCode)}</td>
                        <td className="tabular">{sharePercent(line.billedMinor, categoryBilledTotal)}</td>
                        <td className="money tabular">{formatMoney(line.collectedMinor, line.currencyCode)}</td>
                        <td className="tabular">{line.billedLineCount ?? 0}</td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr>
                      <th scope="row">Total billed (gross)</th>
                      <td className="money tabular">{formatMoney(categoryBilledTotal, currency)}</td>
                      <td className="tabular">100%</td>
                      <td className="money tabular">
                        {formatMoney(
                          (byCategory.data ?? []).reduce((sum, l) => sum + (l.collectedMinor ?? 0), 0),
                          currency,
                        )}
                      </td>
                      <td className="tabular">
                        {(byCategory.data ?? []).reduce((sum, l) => sum + (l.billedLineCount ?? 0), 0)}
                      </td>
                    </tr>
                  </tfoot>
                </table>
              </TableRegion>
              {/* The reconciliation statement this table must carry: the
                  category totals are gross, and the gap to a net figure is
                  named rather than quietly absorbed into a bar. */}
              <p className="analytics-footnote">
                {adjustmentsTotal > 0 ? (
                  <>
                    <strong>{formatMoney(adjustmentsTotal, currency)}</strong> of credit notes and write-offs was
                    applied in this period. It reduces receivables at invoice level and is <strong>not</strong>{' '}
                    deducted from any category above — no line-level allocation exists for it, so these totals are
                    gross, not net revenue.
                  </>
                ) : (
                  <>
                    No credit notes or write-offs were applied in this period, so gross and net billed are the same
                    here. Category totals are always reported gross of invoice-level adjustments.
                  </>
                )}
              </p>
            </ChartCard>
          </div>

          {/* Invoice-level financial adjustments — deliberately their own
              strip, not a bar on the category chart, because neither a credit
              note nor a write-off can be attributed to a service. */}
          <section className="analytics-page" style={{ gap: 'var(--space-3)' }}>
            <div className="analytics-section-head">
              <h2>Financial adjustments</h2>
              <p>Invoice-level and unattributed to any service category.</p>
            </div>
            <div className="kpi-row kpi-row--four">
              <KpiCard
                label="Credit notes applied"
                value={adjustments.isPending ? '…' : formatMoney(adjustments.data?.creditNotesAppliedMinor, currency)}
                basis={`${adjustments.data?.creditNotesAppliedCount ?? 0} applied in period — reduces receivables`}
                icon={Scissors}
                tone={(adjustments.data?.creditNotesAppliedMinor ?? 0) > 0 ? 'warning' : 'neutral'}
              />
              <KpiCard
                label="Write-offs applied"
                value={adjustments.isPending ? '…' : formatMoney(adjustments.data?.writeOffsAppliedMinor, currency)}
                basis={`${adjustments.data?.writeOffsAppliedCount ?? 0} applied in period — debt forgiven`}
                icon={Scissors}
                tone={(adjustments.data?.writeOffsAppliedMinor ?? 0) > 0 ? 'warning' : 'neutral'}
              />
              <KpiCard
                label="Refunds"
                value={adjustments.isPending ? '…' : formatMoney(adjustments.data?.refundsAppliedMinor, currency)}
                basis="Cash returned — already deducted from Collected above"
                icon={Undo2}
              />
              <KpiCard
                label="Awaiting decision"
                value={
                  adjustments.isPending
                    ? '…'
                    : (adjustments.data?.pendingCreditNoteCount ?? 0) + (adjustments.data?.pendingWriteOffCount ?? 0)
                }
                basis="Pending credit notes and write-offs, all periods"
                icon={FileCheck2}
              />
            </div>
          </section>

          {/* Row 4 — surgery and procedure revenue */}
          <div className="analytics-row">
            <ChartCard
              title="Highest-revenue surgeries"
              basis="Surgical charges only. Billable count is charges raised; collected is payments received in the period."
              isLoading={surgeries.isPending}
              isError={surgeries.isError}
              onRetry={() => void surgeries.refetch()}
              isEmpty={(surgeries.data ?? []).length === 0}
              emptyMessage="No surgical charges in this period."
            >
              <ProcedureTable lines={surgeries.data ?? []} />
            </ChartCard>

            <ChartCard
              title="Highest-revenue theatre procedures"
              basis="Day-case and theatre procedure charges, separate from surgery so neither inflates the other."
              isLoading={procedures.isPending}
              isError={procedures.isError}
              onRetry={() => void procedures.refetch()}
              isEmpty={(procedures.data ?? []).length === 0}
              emptyMessage="No procedure charges in this period."
            >
              <ProcedureTable lines={procedures.data ?? []} />
            </ChartCard>
          </div>

          {/* Row 5 — pharmacy and laboratory */}
          <div className="analytics-row">
            <ChartCard
              title={`Top ${TOP_N} medicines by revenue`}
              basis="Quantity BILLED, from invoice lines — not quantity dispensed. The charge is raised at prescribing and dispensing is gated on payment, so the two legitimately differ."
              isLoading={medicines.isPending}
              isError={medicines.isError}
              onRetry={() => void medicines.refetch()}
              isEmpty={(medicines.data ?? []).length === 0}
              emptyMessage="No medicines billed in this period."
            >
              <MedicineTable lines={medicines.data ?? []} />
            </ChartCard>

            <ChartCard
              title={`Top ${TOP_N} laboratory tests by revenue`}
              basis="One row per catalogue test; a test billed twice counts as two charges, never as two rows."
              isLoading={labTests.isPending}
              isError={labTests.isError}
              onRetry={() => void labTests.refetch()}
              isEmpty={(labTests.data ?? []).length === 0}
              emptyMessage="No laboratory tests billed in this period."
            >
              <RankedBarChart
                valueLabel="Collected"
                secondaryLabel="Billed"
                currencyCode={labTests.data?.[0]?.currencyCode}
                rows={(labTests.data ?? []).map((line) => ({
                  id: line.serviceItemId ?? '',
                  label: line.name ?? 'Unnamed test',
                  value: line.collectedMinor ?? 0,
                  secondaryValue: line.billedMinor ?? 0,
                  meta: `${line.billedLineCount ?? 0} billed · ${line.settledLineCount ?? 0} fully paid · billed ${formatMoney(
                    line.billedMinor,
                    line.currencyCode,
                  )}`,
                }))}
              />
            </ChartCard>
          </div>
        </>
      )}

      {/* Row 6 — cancellations, unchanged behaviour */}
      <section>
        <QueueShell
          headingLevel={2}
          title="Invoice cancellations"
          rows={rows}
          isLoading={requests.isLoading}
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
      </section>
    </div>
  );
}

function useTopItems(
  category: RevenueCategoryKey,
  range: DateRange,
  facilityId: string | null,
  accessToken: string | null,
  enabled: boolean,
) {
  return useQuery({
    queryKey: ['reporting', 'revenue', 'top', category, range.from, range.to, facilityId],
    queryFn: () => fetchTopRevenueItems(range, facilityId as string, category, TOP_N, accessToken as string),
    enabled,
  });
}

// Procedures and surgeries are read as a table, not a bar: the question is
// "how many did we do and what did we actually collect", and four numbers per
// row read better side by side than as a bar with three labels hung off it.
function ProcedureTable({ lines }: { lines: TopServiceItemLine[] }) {
  return (
    <TableRegion>
      <table className="table">
        <thead>
          <tr>
            <th scope="col">Procedure</th>
            <th scope="col">Charges</th>
            <th scope="col">Billed</th>
            <th scope="col">Collected</th>
          </tr>
        </thead>
        <tbody>
          {lines.map((line) => (
            <tr key={line.serviceItemId}>
              <td>{line.name}</td>
              <td className="tabular">{line.billedLineCount ?? 0}</td>
              <td className="money tabular">{formatMoney(line.billedMinor, line.currencyCode)}</td>
              <td className="money tabular">{formatMoney(line.collectedMinor, line.currencyCode)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </TableRegion>
  );
}

function MedicineTable({ lines }: { lines: TopServiceItemLine[] }) {
  return (
    <TableRegion>
      <table className="table">
        <thead>
          <tr>
            <th scope="col">Medicine</th>
            <th scope="col">Strength / form</th>
            <th scope="col">Qty billed</th>
            <th scope="col">Billed</th>
            <th scope="col">Collected</th>
          </tr>
        </thead>
        <tbody>
          {lines.map((line) => (
            <tr key={line.serviceItemId}>
              <td>{line.name}</td>
              {/* Null means no matching stock-catalogue item for this billing
                  code, never "no strength" — pharmacy names usually carry it. */}
              <td>{[line.strength, line.form].filter(Boolean).join(' · ') || '—'}</td>
              <td className="tabular">{line.quantityBilled ?? 0}</td>
              <td className="money tabular">{formatMoney(line.billedMinor, line.currencyCode)}</td>
              <td className="money tabular">{formatMoney(line.collectedMinor, line.currencyCode)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </TableRegion>
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
      <h3 style={{ fontSize: 'var(--text-base)', fontWeight: 'var(--weight-bold)', marginTop: 0 }}>
        Review cancellation request
      </h3>
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
        className="clinical-control"
        id="review-note"
        value={reviewNote}
        onChange={(e) => onNoteChange(e.target.value)}
        rows={3}
        style={{ width: '100%', marginTop: 'var(--space-1)' }}
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

/**
 * Collection rate over one cohort: the patient portion billed in the period,
 * and how much of exactly those charges has been settled to date. Not
 * collected-in-period over billed-in-period — those have different date bases,
 * so their ratio reconciles to nothing and can exceed 100% for no real reason.
 */
function rateOf(settledMinor: number | undefined, billedMinor: number | undefined): string {
  const billed = billedMinor ?? 0;
  if (billed <= 0) return '—';
  return `${Math.round(((settledMinor ?? 0) / billed) * 100)}%`;
}

function sharePercent(value: number | undefined, total: number): string {
  if (total <= 0) return '—';
  return `${Math.round(((value ?? 0) / total) * 100)}%`;
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
