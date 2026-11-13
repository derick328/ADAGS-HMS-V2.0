import { Checkbox } from '../../shared/components/Checkbox';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { TableRegion } from '../../shared/components/TableRegion';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { ApiError } from '../../shared/api/client';
import {
  applyCatalogImport,
  cancelCatalogImport,
  DEACTIVATE_ALL_CONFIRMATION,
  deactivateAllCatalogItems,
  listCatalogImports,
  previewCatalogImport,
  searchCatalogItems,
  updateCatalogPrice,
  fetchCatalogPriceHistory,
  downloadCatalog,
  type CatalogImportBatch,
  type CatalogImportPreview,
  type CatalogItem,
} from '../../shared/api/catalogImport';
import {
  fetchPendingPharmacyItemProposals,
  reviewPharmacyItemProposal,
  type PharmacyItemProposalResponse,
} from '../../shared/api/pricing';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { approveMedicinePackaging, fetchPendingMedicinePackaging, rejectMedicinePackaging } from '../../shared/api/medicinePackaging';
import { formatDateTime } from '../../shared/format/date';
import { formatMoney } from '../../shared/format/money';
import { QueueShell, type QueueRow } from '../../shared/components/QueueShell';
import { MedicationSettings } from './MedicationSettings';
import { useMe } from '../../shared/auth/useMe';

// 18-admin.md §2/§7, 12-pharmacist.md §9.2: the pharmacy item and
// price-change approval queue is the only pricing backend that exists
// today (PharmacyItemProposalController).
// Approving here creates the item / re-prices it through BillingFacade's
// versioned pricing path (close the old entry, open a new one) — nobody
// types a price directly on this screen either.
export function PricingPage() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const canApprove = usePermission('PHARMACY_ITEM_APPROVE');
  const [reviewingId, setReviewingId] = useState<string | null>(null);
  const [reviewNote, setReviewNote] = useState('');

  const { data: proposals, isLoading } = useQuery({
    queryKey: ['pharmacy-item-proposals', 'pending'],
    queryFn: () => fetchPendingPharmacyItemProposals(accessToken as string),
    enabled: accessToken != null && canApprove,
  });

  const review = useMutation({
    mutationFn: (vars: { id: string; decision: 'APPROVED' | 'REJECTED' }) =>
      reviewPharmacyItemProposal(vars.id, { decision: vars.decision, reviewNote }, accessToken as string),
    onSuccess: () => {
      setReviewingId(null);
      setReviewNote('');
      queryClient.invalidateQueries({ queryKey: ['pharmacy-item-proposals'] });
    },
  });

  const reviewingProposal = proposals?.find((p) => p.id === reviewingId);

  const rows: QueueRow[] = (proposals ?? []).map((proposal) => ({
    id: proposal.id ?? '',
    primary: describeProposal(proposal),
    secondary: proposal.justification ?? undefined,
    meta: <span className="money">{formatMoney(proposal.proposedPriceAmountMinor, proposal.currencyCode)}</span>,
    action: canApprove ? (
      <button type="button" className="btn btn-secondary" onClick={() => setReviewingId(proposal.id ?? null)}>
        Review
      </button>
    ) : null,
  }));

  return (
    <div>
      <CatalogueImportSection />
      <CatalogueItemsBrowser />

      <PackagingApprovalSection />

      <QueueShell
        title="Pricing / price proposals"
        rows={rows}
        isLoading={isLoading}
        emptyMessage="No pending pharmacy item or price-change proposals"
      />

      {reviewingProposal && (
        <ReviewPanel
          proposal={reviewingProposal}
          reviewNote={reviewNote}
          onNoteChange={setReviewNote}
          onApprove={() => review.mutate({ id: reviewingProposal.id as string, decision: 'APPROVED' })}
          onReject={() => review.mutate({ id: reviewingProposal.id as string, decision: 'REJECTED' })}
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

function PackagingApprovalSection() {
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  const canApprove = usePermission('PACKAGING_APPROVE');
  const queryClient = useQueryClient();
  const [rejectingId, setRejectingId] = useState<string | null>(null);
  const [rejectionReason, setRejectionReason] = useState('');
  const { data, isLoading, isError, refetch } = useQuery({ queryKey: ['medicine-packaging', 'pending'],
    queryFn: () => fetchPendingMedicinePackaging(accessToken as string),
    enabled: accessToken != null && canApprove });
  const approve = useMutation({ mutationFn: (id: string) => approveMedicinePackaging(id, accessToken as string),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['medicine-packaging'] }) });
  const reject = useMutation({ mutationFn: (id: string) => rejectMedicinePackaging(id, rejectionReason.trim(), accessToken as string),
    onSuccess: () => { setRejectingId(null); setRejectionReason('');
      queryClient.invalidateQueries({ queryKey: ['medicine-packaging'] }); } });
  if (!canApprove) return null;
  return <section style={{ marginBottom: 'var(--space-5)' }}>
    <h2>Packaging approvals</h2>
    <p>Review each proposed pack or blister conversion. A different authorized person must decide; rejection requires a reason.</p>
    {isLoading ? <FeedbackState loading>Loading proposals…</FeedbackState> : isError ?
      <p role="alert">Could not load packaging proposals. <button type="button" className="link-button" onClick={() => refetch()}>Retry</button></p> : !data?.length ?
      <FeedbackState>No packaging proposals pending.</FeedbackState> :
      <TableRegion label="Pending packaging conversions"><table className="table"><thead><tr>
        <th>Medicine</th><th>Package</th><th>Conversion</th><th>Blister counts</th><th>Split</th><th>Proposed by</th><th>Manufacturer specification</th><th>Decision</th>
      </tr></thead><tbody>{data.map((proposal) => <tr key={proposal.packaging.id}>
        <td>{proposal.itemName}</td><td>{proposal.packaging.unit}</td>
        <td>{proposal.packaging.factor} {proposal.baseUnit}</td>
        <td>{proposal.packaging.blistersPerPack != null && proposal.packaging.unitsPerBlister != null
          ? `${proposal.packaging.blistersPerPack} × ${proposal.packaging.unitsPerBlister}` : '—'}</td>
        <td>{proposal.packaging.canSplit ? 'Yes' : 'No'}</td>
        <td>{proposal.proposedBy}</td><td>{proposal.reason}</td>
        <td>{proposal.proposedBy === me?.username ? 'Your proposal needs another reviewer' : <>
          <button type="button" className="btn btn-primary" disabled={approve.isPending || reject.isPending || !proposal.packaging.id}
            onClick={() => proposal.packaging.id && approve.mutate(proposal.packaging.id)}>Approve</button>{' '}
          <button type="button" className="btn btn-secondary" disabled={approve.isPending || reject.isPending || !proposal.packaging.id}
            onClick={() => { setRejectingId(proposal.packaging.id); setRejectionReason(''); }}>Reject</button>
          {rejectingId === proposal.packaging.id && <form onSubmit={(event) => { event.preventDefault();
            if (rejectionReason.trim() && proposal.packaging.id) reject.mutate(proposal.packaging.id); }}>
            <label>Reason for rejection <textarea className="clinical-control" required value={rejectionReason}
              onChange={(event) => setRejectionReason(event.target.value)} /></label>
            <button type="submit" className="btn btn-secondary" disabled={reject.isPending || !rejectionReason.trim()}>Confirm rejection</button>{' '}
            <button type="button" className="btn btn-ghost" onClick={() => setRejectingId(null)}>Cancel</button>
          </form>}</>}</td>
      </tr>)}</tbody></table></TableRegion>}
    {approve.error && <p className="field-error" role="alert">{approve.error instanceof ApiError ? approve.error.message : 'Could not approve packaging.'}</p>}
    {reject.error && <p className="field-error" role="alert">{reject.error instanceof ApiError ? reject.error.message : 'Could not reject packaging.'}</p>}
  </section>;
}

export function PackagingApprovalsPage() {
  return <div><PackagingApprovalSection /></div>;
}

function ReviewPanel({
  proposal,
  reviewNote,
  onNoteChange,
  onApprove,
  onReject,
  onCancel,
  isSubmitting,
}: {
  proposal: PharmacyItemProposalResponse;
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
        Review {proposal.proposalType === 'NEW_ITEM' ? 'new item' : 'price change'}
      </h2>
      <dl style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
        <dt style={{ fontWeight: 'var(--weight-medium)' }}>Item</dt>
        <dd style={{ margin: '0 0 var(--space-2)' }}>{describeProposal(proposal)}</dd>
        {proposal.currentPriceAmountMinor != null && (
          <>
            <dt style={{ fontWeight: 'var(--weight-medium)' }}>Current price</dt>
            <dd style={{ margin: '0 0 var(--space-2)' }}>
              {formatMoney(proposal.currentPriceAmountMinor, proposal.currencyCode)}
            </dd>
          </>
        )}
        <dt style={{ fontWeight: 'var(--weight-medium)' }}>Proposed price</dt>
        <dd style={{ margin: '0 0 var(--space-2)' }}>
          {formatMoney(proposal.proposedPriceAmountMinor, proposal.currencyCode)}
        </dd>
        {proposal.justification && (
          <>
            <dt style={{ fontWeight: 'var(--weight-medium)' }}>Justification</dt>
            <dd style={{ margin: 0 }}>{proposal.justification}</dd>
          </>
        )}
      </dl>

      <label htmlFor="proposal-review-note" style={{ display: 'block', marginTop: 'var(--space-3)', fontSize: 'var(--text-sm)' }}>
        Review note
      </label>
      <textarea className="clinical-control"
        id="proposal-review-note"
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
        placeholder="Optional for approval, helpful either way"
      />

      <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-4)' }}>
        <button type="button" className="btn btn-primary" onClick={onApprove} disabled={isSubmitting}>
          Approve
        </button>
        <button type="button" className="btn btn-danger" onClick={onReject} disabled={isSubmitting}>
          Reject
        </button>
        <button type="button" className="btn btn-ghost" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </button>
      </div>
    </div>
  );
}

function describeProposal(proposal: PharmacyItemProposalResponse): string {
  if (proposal.proposalType === 'NEW_ITEM') {
    const details = [proposal.proposedClassification, proposal.proposedForm].filter(Boolean).join(' · ');
    return details ? `${proposal.proposedName} (${details})` : (proposal.proposedName ?? 'New item');
  }
  return `Price change — item ${proposal.itemId ? proposal.itemId.slice(0, 8) : '—'}`;
}

// Phase 5/9 catalogue import. Two-stage flow: pick an .xlsx → server
// parses/validates/diffs and returns a preview → admin reviews and hits
// Apply, which writes service_item / price_list_entry inside one
// transaction. A CANCEL escape hatch releases the sha256 idempotency
// lock so a fixed file can be re-uploaded.
function CatalogueImportSection() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const canImport = usePermission('CATALOG_IMPORT');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<CatalogImportPreview | null>(null);
  const [fileName, setFileName] = useState<string | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [rowErrors, setRowErrors] = useState<{ excelRow: number; reason: string }[]>([]);
  const [showClearPanel, setShowClearPanel] = useState(false);
  const [clearResult, setClearResult] = useState<string | null>(null);

  const { data: batches } = useQuery({
    queryKey: ['catalog-imports', 'recent'],
    queryFn: () => listCatalogImports(accessToken as string),
    enabled: canImport && accessToken != null,
  });

  const uploadMutation = useMutation({
    mutationFn: (file: File) => previewCatalogImport(file, accessToken as string),
    onMutate: () => {
      setUploadError(null);
      setRowErrors([]);
      setPreview(null);
    },
    onSuccess: (result) => {
      setPreview(result);
      queryClient.invalidateQueries({ queryKey: ['catalog-imports', 'recent'] });
    },
    onError: (err) => {
      if (err instanceof ApiError) {
        setUploadError(err.message);
        const errors = err.body?.errors;
        if (Array.isArray(errors)) {
          setRowErrors(errors as { excelRow: number; reason: string }[]);
        }
      } else {
        setUploadError('Upload failed — check the file and try again.');
      }
    },
  });

  const applyMutation = useMutation({
    mutationFn: (batchId: string) => applyCatalogImport(batchId, accessToken as string),
    onSuccess: () => {
      setPreview(null);
      setFileName(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
      queryClient.invalidateQueries({ queryKey: ['catalog-imports', 'recent'] });
      queryClient.invalidateQueries({ queryKey: ['catalog-items'] });
    },
    onError: (err) => {
      setUploadError(err instanceof ApiError ? err.message : 'Apply failed — the batch may already be in a terminal state.');
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (batchId: string) => cancelCatalogImport(batchId, accessToken as string),
    onSuccess: () => {
      setPreview(null);
      setFileName(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
      queryClient.invalidateQueries({ queryKey: ['catalog-imports', 'recent'] });
    },
  });

  if (!canImport) return null;

  return (
    <section
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-4)',
        marginBottom: 'var(--space-4)',
      }}
    >
      <h2 style={{ marginTop: 0, fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)' }}>
        Catalogue import
      </h2>
      <p style={{ margin: '0 0 var(--space-3)', color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>
        Upload the hospital's price list (.xlsx). The server validates every row, shows what will change, and
        applies the changes only when you confirm. Existing items missing from the new file stay active
        unless you clear the catalogue first.
      </p>

      <div style={{ marginBottom: 'var(--space-4)' }}>
        {!showClearPanel ? (
          <button type="button" className="btn btn-danger"
            disabled={preview != null || uploadMutation.isPending || applyMutation.isPending}
            onClick={() => setShowClearPanel(true)}>
            Clear catalogue before new upload…
          </button>
        ) : (
          <DeactivateAllPanel
            onClose={() => setShowClearPanel(false)}
            onCleared={(count) => {
              setClearResult(`Catalogue cleared: ${count} active item${count === 1 ? '' : 's'} deactivated. Upload the new Excel file now.`);
              setShowClearPanel(false);
            }}
          />
        )}
        {clearResult && <p role="status" style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>{clearResult}</p>}
        {preview && <p style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>
          Apply or cancel the current preview before clearing the catalogue.
        </p>}
      </div>

      <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
        <input
          ref={fileInputRef}
          type="file"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          onChange={(e) => {
            const f = e.target.files?.[0];
            setFileName(f ? f.name : null);
            if (f) uploadMutation.mutate(f);
          }}
          disabled={showClearPanel || uploadMutation.isPending || applyMutation.isPending}
        />
        {uploadMutation.isPending && <span style={{ color: 'var(--text-muted)' }}>Parsing…</span>}
      </div>

      {uploadError && (
        <div className="form-error" style={{ marginTop: 'var(--space-3)' }}>
          {uploadError}
        </div>
      )}

      {rowErrors.length > 0 && (
        <div style={{ marginTop: 'var(--space-2)', maxHeight: 200, overflowY: 'auto' }}>
          <ul style={{ margin: 0, paddingLeft: 'var(--space-4)', fontSize: 'var(--text-sm)', color: 'var(--critical)' }}>
            {rowErrors.slice(0, 100).map((err, i) => (
              <li key={i}>
                Row {err.excelRow}: {err.reason}
              </li>
            ))}
            {rowErrors.length > 100 && <li>… and {rowErrors.length - 100} more</li>}
          </ul>
        </div>
      )}

      {preview && (
        <PreviewSummary
          preview={preview}
          fileName={fileName ?? 'catalogue.xlsx'}
          onApply={() => applyMutation.mutate(preview.batchId)}
          onCancel={() => cancelMutation.mutate(preview.batchId)}
          isApplying={applyMutation.isPending}
          isCancelling={cancelMutation.isPending}
        />
      )}

      <RecentBatches batches={batches ?? []} />
    </section>
  );
}

function PreviewSummary({
  preview,
  fileName,
  onApply,
  onCancel,
  isApplying,
  isCancelling,
}: {
  preview: CatalogImportPreview;
  fileName: string;
  onApply: () => void;
  onCancel: () => void;
  isApplying: boolean;
  isCancelling: boolean;
}) {
  return (
    <div style={{ marginTop: 'var(--space-3)', padding: 'var(--space-3)', background: 'var(--info-bg)', borderRadius: 'var(--radius)' }}>
      <div style={{ fontWeight: 'var(--weight-medium)' }}>
        {fileName} · {preview.rowCount} rows parsed
      </div>
      <ul style={{ margin: 'var(--space-2) 0', paddingLeft: 'var(--space-4)', fontSize: 'var(--text-sm)' }}>
        <li>
          <strong>{preview.newItemCount}</strong> new item{preview.newItemCount === 1 ? '' : 's'}
        </li>
        <li>
          <strong>{preview.priceChangeCount}</strong> price change{preview.priceChangeCount === 1 ? '' : 's'}
        </li>
        <li>
          <strong>{preview.metadataChangeCount}</strong> metadata update{preview.metadataChangeCount === 1 ? '' : 's'}
        </li>
        <li>
          <strong>{preview.unchangedCount}</strong> unchanged (won't be touched)
        </li>
      </ul>

      {preview.priceChanges.length > 0 && (
        <details style={{ marginTop: 'var(--space-2)' }}>
          <summary style={{ cursor: 'pointer' }}>Price changes ({preview.priceChanges.length})</summary>
          <TableRegion><table className="table" style={{ marginTop: 'var(--space-2)' }}>
            <thead>
              <tr>
                <th>Row</th>
                <th>Code</th>
                <th>Name</th>
                <th>Was</th>
                <th>New</th>
              </tr>
            </thead>
            <tbody>
              {preview.priceChanges.slice(0, 50).map((c) => (
                <tr key={`${c.code}-${c.excelRow}`}>
                  <td>{c.excelRow}</td>
                  <td>{c.code}</td>
                  <td>{c.name}</td>
                  <td className="money">{formatMoney(c.previousMinor, 'TZS')}</td>
                  <td className="money">{formatMoney(c.newMinor, 'TZS')}</td>
                </tr>
              ))}
            </tbody>
          </table></TableRegion>
          {preview.priceChanges.length > 50 && (
            <p style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
              Showing first 50 of {preview.priceChanges.length}.
            </p>
          )}
        </details>
      )}

      {preview.newItems.length > 0 && (
        <details style={{ marginTop: 'var(--space-2)' }}>
          <summary style={{ cursor: 'pointer' }}>New items ({preview.newItems.length})</summary>
          <TableRegion><table className="table" style={{ marginTop: 'var(--space-2)' }}>
            <thead>
              <tr>
                <th>Row</th>
                <th>Code</th>
                <th>Name</th>
                <th>Price</th>
              </tr>
            </thead>
            <tbody>
              {preview.newItems.slice(0, 50).map((n) => (
                <tr key={`${n.code}-${n.excelRow}`}>
                  <td>{n.excelRow}</td>
                  <td>{n.code}</td>
                  <td>{n.name}</td>
                  <td className="money">
                    {n.priceMinor == null ? (
                      <span style={{ color: 'var(--warning)' }}>unpriced</span>
                    ) : (
                      formatMoney(n.priceMinor, 'TZS')
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table></TableRegion>
          {preview.newItems.length > 50 && (
            <p style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
              Showing first 50 of {preview.newItems.length}.
            </p>
          )}
        </details>
      )}

      <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-3)' }}>
        <button type="button" className="btn btn-primary" onClick={onApply} disabled={isApplying || isCancelling}>
          {isApplying ? 'Applying…' : 'Apply changes'}
        </button>
        <button type="button" className="btn btn-ghost" onClick={onCancel} disabled={isApplying || isCancelling}>
          {isCancelling ? 'Cancelling…' : 'Cancel this preview'}
        </button>
      </div>
    </div>
  );
}

// The admin's "View catalogue items" panel — search + type/category
// filters + a paginated table. Doubles as the entry point for the
// destructive "Deactivate all" flow, which is intentionally a
// bordered-red panel with a typed-confirmation gate to keep it well away
// from accidental clicks.
function CatalogueItemsBrowser() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const canImport = usePermission('CATALOG_IMPORT');
  const [q, setQ] = useState('');
  const [submittedQ, setSubmittedQ] = useState('');
  const [type, setType] = useState('');
  const [billingCategory, setBillingCategory] = useState('');
  const [activeOnly, setActiveOnly] = useState(true);
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<CatalogItem | null>(null);
  const [newPrice, setNewPrice] = useState('');
  const [priceError, setPriceError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [historyId, setHistoryId] = useState<string | null>(null);
  const [medicationSettings, setMedicationSettings] = useState<CatalogItem | null>(null);
  const pageSize = 50;

  const savePrice = useMutation({
    mutationFn: ({ item, amount }: { item: CatalogItem; amount: number }) =>
      updateCatalogPrice(item.id, item.currentCashPriceMinor as number, amount, accessToken as string),
    onSuccess: () => {
      setEditing(null); setPriceError(null); setSuccess('Catalogue price saved.');
      void queryClient.invalidateQueries({ queryKey: ['catalog-items'] });
      void queryClient.invalidateQueries({ queryKey: ['price-catalog'] });
    },
    onError: (error) => setPriceError(error instanceof ApiError ? error.message : 'Could not save price.'),
  });
  const { data: history } = useQuery({
    queryKey: ['catalog-price-history', historyId],
    queryFn: () => fetchCatalogPriceHistory(historyId as string, accessToken as string),
    enabled: historyId != null && accessToken != null,
  });

  const { data, isLoading } = useQuery({
    queryKey: ['catalog-items', { q: submittedQ, type, billingCategory, activeOnly, page }],
    queryFn: () =>
      searchCatalogItems(
        {
          q: submittedQ,
          type: type || undefined,
          billingCategory: billingCategory || undefined,
          activeOnly,
          page,
          pageSize,
        },
        accessToken as string,
      ),
    enabled: canImport && accessToken != null,
  });

  if (!canImport) return null;

  const totalPages = data ? Math.max(1, Math.ceil(data.totalCount / pageSize)) : 1;

  return (
    <section
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-4)',
        marginBottom: 'var(--space-4)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
        <h2 style={{ margin: 0, fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)' }}>Catalogue items</h2>
        <button type="button" className="btn btn-secondary" onClick={() => {
          void downloadCatalog(accessToken as string).catch(() => setPriceError('Could not download the catalogue.'));
        }}>Download Price Catalogue</button>
        <div style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>
          {data ? `${data.totalCount.toLocaleString()} item${data.totalCount === 1 ? '' : 's'}` : 'Loading…'}
        </div>
      </div>
      {success && <p role="status" className="badge badge-success">{success}</p>}
      {priceError && !editing && <p role="alert" className="form-error">{priceError}</p>}

      <form className="clinical-filter-bar"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          setSubmittedQ(q.trim());
        }}
        style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap', marginTop: 'var(--space-3)' }}
      >
        <input aria-label="Search product name or code" className="clinical-control"
          type="text"
          placeholder="Search product name or code"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          style={{
            flex: '1 1 240px',
            padding: 'var(--space-2)',
            border: '1px solid var(--border-strong)',
            borderRadius: 'var(--radius)',
            font: 'inherit',
          }}
        />
        <select className="clinical-control"
          value={type}
          onChange={(e) => {
            setType(e.target.value);
            setPage(0);
          }}
          style={selectStyle}
        >
          <option value="">All types</option>
          {['PHARMACY', 'PROCEDURE', 'SURGERY', 'LABORATORY', 'RADIOLOGY', 'OPTICAL', 'DIALYSIS', 'OTHERS', 'OTHER_GENERAL_CHARGES'].map((t) => (
            <option key={t} value={t}>{t.replaceAll('_', ' ').toLowerCase()}</option>
          ))}
        </select>
        <select className="clinical-control"
          value={billingCategory}
          onChange={(e) => {
            setBillingCategory(e.target.value);
            setPage(0);
          }}
          style={selectStyle}
        >
          <option value="">All categories</option>
          {[
            'SURGICAL_CHARGES',
            'MEDICINES_AND_CONSUMABLES',
            'DIAGNOSTIC_EXAMINATIONS',
            'PROCEDURAL_CHARGES',
            'INPATIENT_CHARGES',
            'REGISTRATION_CONSULTATION_CHARGES',
            'OTHER_CHARGES',
            'ADVANCE_PAYMENT',
          ].map((c) => (
            <option key={c} value={c}>{c.replaceAll('_', ' ').toLowerCase()}</option>
          ))}
        </select>
        <Checkbox  className="clinical-choice" checked={activeOnly} onChange={(e) => { setActiveOnly(e.target.checked); setPage(0); }} label={<>Active only</>} />
        <button type="submit" className="btn btn-secondary">Search</button>
      </form>

      {isLoading ? (
        <FeedbackState loading>Loading…</FeedbackState>
      ) : !data || data.items.length === 0 ? (
        <FeedbackState>No items match those filters.</FeedbackState>
      ) : (
        <div style={{ overflowX: 'auto', marginTop: 'var(--space-3)' }}>
          <TableRegion label="Catalogue items"><table className="table" style={{ width: '100%' }}>
            <caption className="sr-only">Catalogue items</caption>
            <thead>
              <tr>
                <th>Code</th>
                <th>Name</th>
                <th>Type</th>
                <th>Category</th>
                <th>Current CASH price</th>
                <th>Active</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((item: CatalogItem) => (
                <tr key={item.id}>
                  <td style={{ fontFamily: 'var(--font-mono, monospace)' }}>{item.code}</td>
                  <td>{item.name}</td>
                  <td style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>{item.type?.replaceAll('_', ' ').toLowerCase() ?? '—'}</td>
                  <td style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>{item.billingCategory?.replaceAll('_', ' ').toLowerCase() ?? '—'}</td>
                  <td className="money">
                    {item.currentCashPriceMinor == null ? (
                      <span style={{ color: 'var(--warning)' }}>unpriced</span>
                    ) : (
                      formatMoney(item.currentCashPriceMinor, item.currencyCode)
                    )}
                  </td>
                  <td>
                    <span className={`badge ${item.isActive ? 'badge-success' : 'badge-neutral'}`}>
                      {item.isActive ? 'active' : 'inactive'}
                    </span>
                  </td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    {item.isActive && item.currentCashPriceMinor != null && <button type="button" className="btn btn-secondary" onClick={() => {
                      setEditing(item); setNewPrice(String(item.currentCashPriceMinor)); setPriceError(null);
                    }}>Edit Price</button>}{' '}
                    <button type="button" className="btn btn-ghost" onClick={() => setHistoryId(item.id)}>History</button>
                    {(item.type === 'PHARMACY' || item.billingCategory === 'MEDICINES_AND_CONSUMABLES') &&
                      <button type="button" className="btn btn-secondary" onClick={() => setMedicationSettings(item)}>Packaging & costs</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table></TableRegion>
        </div>
      )}

      {data && data.totalCount > pageSize && (
        <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center', justifyContent: 'flex-end', marginTop: 'var(--space-2)' }}>
          <button
            type="button"
            className="btn btn-ghost"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Previous
          </button>
          <span style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>
            Page {page + 1} of {totalPages}
          </span>
          <button
            type="button"
            className="btn btn-ghost"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </button>
        </div>
      )}

      {medicationSettings && <div role="dialog" aria-modal="true" aria-label={`Packaging and costs for ${medicationSettings.name}`}
        onKeyDown={(event) => { if (event.key === 'Escape') setMedicationSettings(null); }}
        style={{ position: 'fixed', inset: 0, zIndex: 1000, background: 'rgba(0,0,0,.55)', display: 'grid', placeItems: 'center', padding: 'var(--space-3)' }}>
        <div style={{ width: 'min(100%, 760px)', maxHeight: '90vh', overflowY: 'auto' }}>
          <MedicationSettings catalogueItem={medicationSettings} onClose={() => setMedicationSettings(null)} />
        </div>
      </div>}
      {editing && <div role="dialog" aria-modal="true" aria-label="Edit catalogue price" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,.5)', display: 'grid', placeItems: 'center', zIndex: 1000, padding: 'var(--space-3)' }}>
        <form onSubmit={(event) => {
          event.preventDefault();
          if (!/^(0|[1-9]\d*)$/.test(newPrice) || !Number.isSafeInteger(Number(newPrice))) {
            setPriceError('Enter a nonnegative whole TZS amount.'); return;
          }
          savePrice.mutate({ item: editing, amount: Number(newPrice) });
        }} style={{ background: 'var(--bg-surface)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)', width: 'min(100%, 440px)' }}>
          <h2>Edit Price</h2>
          <p><strong>Code:</strong> {editing.code}<br /><strong>Service:</strong> {editing.name}<br /><strong>Current price:</strong> {formatMoney(editing.currentCashPriceMinor, 'TZS')}</p>
          <label htmlFor="new-catalog-price">New price (TZS)</label>
          <input id="new-catalog-price" className="clinical-control" inputMode="numeric" value={newPrice} onChange={(event) => setNewPrice(event.target.value)} style={{ display: 'block', width: '100%' }} />
          {priceError && <p role="alert" className="form-error">{priceError}</p>}
          <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-3)' }}>
            <button className="btn btn-primary" disabled={savePrice.isPending}>Save</button>
            <button type="button" className="btn btn-secondary" onClick={() => setEditing(null)}>Cancel</button>
          </div>
        </form>
      </div>}
      {historyId && <div role="dialog" aria-modal="true" aria-label="Price history" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,.5)', display: 'grid', placeItems: 'center', zIndex: 1000, padding: 'var(--space-3)' }}>
        <div style={{ background: 'var(--bg-surface)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)', maxHeight: '80vh', overflow: 'auto', width: 'min(100%, 640px)' }}>
          <h2>Price history</h2>
          <TableRegion><table className="table"><thead><tr><th>Previous</th><th>New price</th><th>Changed at</th><th>Administrator ID</th></tr></thead><tbody>
            {(history ?? []).map((change) => <tr key={change.entryId}><td>{change.previousPriceMinor == null ? '—' : formatMoney(change.previousPriceMinor, 'TZS')}</td><td>{formatMoney(change.priceMinor, 'TZS')}</td><td>{formatDateTime(change.changedAt)}</td><td>{change.changedBy}</td></tr>)}
          </tbody></table></TableRegion>
          <button type="button" className="btn btn-secondary" onClick={() => setHistoryId(null)}>Close</button>
        </div>
      </div>}

    </section>
  );
}

// Bordered, typed-confirmation gate. Explains what will and won't happen
// (soft-delete, historical invoices unaffected, prices closed) before
// the button unlocks.
function DeactivateAllPanel({ onClose, onCleared }: { onClose: () => void; onCleared: (count: number) => void }) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [typed, setTyped] = useState('');
  const [message, setMessage] = useState<string | null>(null);

  const deactivate = useMutation({
    mutationFn: () => deactivateAllCatalogItems(typed, accessToken as string),
    onSuccess: (result) => {
      setTyped('');
      queryClient.invalidateQueries({ queryKey: ['catalog-items'] });
      queryClient.invalidateQueries({ queryKey: ['price-catalog'] });
      onCleared(result.deactivatedCount);
    },
    onError: (err) => {
      setMessage(err instanceof ApiError ? err.message : 'Could not deactivate — please try again.');
    },
  });

  const armed = typed === DEACTIVATE_ALL_CONFIRMATION;

  return (
    <div style={{
      background: 'var(--critical-bg)',
      color: 'var(--critical)',
      border: '1px solid var(--critical)',
      borderRadius: 'var(--radius)',
      padding: 'var(--space-3)',
    }}>
      <div style={{ fontWeight: 'var(--weight-medium)', marginBottom: 'var(--space-2)' }}>
        Deactivate catalogue items and their current prices?
      </div>
      <ul style={{ margin: '0 0 var(--space-2)', paddingLeft: 'var(--space-4)', fontSize: 'var(--text-sm)' }}>
        <li>Active service items will be soft-deactivated and their current prices closed, except the three consultation items below.</li>
        <li>Clinical officer, doctor, and specialist consultation fees stay available for triage.</li>
        <li>A later re-upload of the same PRODUCT CODE brings it back on.</li>
        <li>Nothing is hard-deleted from the database.</li>
      </ul>
      <label style={{ display: 'block', fontSize: 'var(--text-sm)', marginTop: 'var(--space-2)' }}>
        To confirm, type: <code style={{ background: 'var(--bg-surface)', padding: '0 var(--space-1)', borderRadius: 2 }}>{DEACTIVATE_ALL_CONFIRMATION}</code>
      </label>
      <input aria-label="Type the phrase exactly" className="clinical-control"
        type="text"
        value={typed}
        onChange={(e) => setTyped(e.target.value)}
        style={{
          width: '100%',
          marginTop: 'var(--space-1)',
          padding: 'var(--space-2)',
          border: '1px solid var(--border-strong)',
          borderRadius: 'var(--radius)',
          font: 'inherit',
        }}
        placeholder="Type the phrase exactly"
      />
      {message && <div style={{ marginTop: 'var(--space-2)', fontSize: 'var(--text-sm)' }}>{message}</div>}
      <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-3)' }}>
        <button
          type="button"
          className="btn btn-danger"
          disabled={!armed || deactivate.isPending}
          onClick={() => deactivate.mutate()}
        >
          {deactivate.isPending ? 'Deactivating…' : 'Deactivate all'}
        </button>
        <button type="button" className="btn btn-ghost" onClick={onClose} disabled={deactivate.isPending}>
          Cancel
        </button>
      </div>
    </div>
  );
}

const selectStyle = {
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
} as const;

function RecentBatches({ batches }: { batches: CatalogImportBatch[] }) {
  if (batches.length === 0) return null;
  return (
    <div style={{ marginTop: 'var(--space-4)' }}>
      <h3 style={{ margin: '0 0 var(--space-2)', fontSize: 'var(--text-base)', fontWeight: 'var(--weight-medium)' }}>
        Recent imports
      </h3>
      <TableRegion><table className="table" style={{ width: '100%' }}>
        <thead>
          <tr>
            <th>Filename</th>
            <th>Uploaded</th>
            <th>Status</th>
            <th>New</th>
            <th>Price ∆</th>
            <th>Meta ∆</th>
            <th>Unchanged</th>
          </tr>
        </thead>
        <tbody>
          {batches.map((b) => (
            <tr key={b.id}>
              <td>{b.originalFilename}</td>
              <td style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>{formatDateTime(b.uploadedAt)}</td>
              <td>
                <span
                  className={`badge ${
                    b.status === 'APPLIED' ? 'badge-success' : b.status === 'CANCELLED' ? 'badge-neutral' : 'badge-warning'
                  }`}
                >
                  {b.status.toLowerCase()}
                </span>
              </td>
              <td>{b.newItemCount}</td>
              <td>{b.priceChangeCount}</td>
              <td>{b.metadataChangeCount}</td>
              <td>{b.unchangedCount}</td>
            </tr>
          ))}
        </tbody>
      </table></TableRegion>
    </div>
  );
}
