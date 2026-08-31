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
import { formatDateTime } from '../../shared/format/date';
import { formatMoney } from '../../shared/format/money';
import { QueueShell, type QueueRow } from '../../shared/components/QueueShell';

// 18-admin.md §2/§7, 12-pharmacist.md §9.2: the pharmacy item and
// price-change approval queue is the only pricing backend that exists
// today (PharmacyItemProposalController) — the fuller catalogue (payer-aware
// PriceListEntry versioning, bulk Excel import, §7.5) has no backend yet.
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
    enabled: accessToken != null,
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
      <textarea
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
        applies the diff only when you confirm — new items, price changes, and metadata updates all go through
        the versioned pricing path (root CLAUDE.md "Billing"). Existing items missing from the new file are
        left alone.
      </p>

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
          disabled={uploadMutation.isPending || applyMutation.isPending}
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
          <table className="table" style={{ marginTop: 'var(--space-2)' }}>
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
          </table>
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
          <table className="table" style={{ marginTop: 'var(--space-2)' }}>
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
          </table>
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
  const canImport = usePermission('CATALOG_IMPORT');
  const [q, setQ] = useState('');
  const [submittedQ, setSubmittedQ] = useState('');
  const [type, setType] = useState('');
  const [billingCategory, setBillingCategory] = useState('');
  const [activeOnly, setActiveOnly] = useState(true);
  const [page, setPage] = useState(0);
  const pageSize = 50;
  const [showDeactivatePanel, setShowDeactivatePanel] = useState(false);

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
        <div style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>
          {data ? `${data.totalCount.toLocaleString()} item${data.totalCount === 1 ? '' : 's'}` : 'Loading…'}
        </div>
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          setSubmittedQ(q.trim());
        }}
        style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap', marginTop: 'var(--space-3)' }}
      >
        <input
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
        <select
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
        <select
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
        <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)', fontSize: 'var(--text-sm)' }}>
          <input type="checkbox" checked={activeOnly} onChange={(e) => { setActiveOnly(e.target.checked); setPage(0); }} />
          Active only
        </label>
        <button type="submit" className="btn btn-secondary">Search</button>
      </form>

      {isLoading ? (
        <p style={{ color: 'var(--text-muted)', marginTop: 'var(--space-3)' }}>Loading…</p>
      ) : !data || data.items.length === 0 ? (
        <p style={{ color: 'var(--text-muted)', marginTop: 'var(--space-3)' }}>No items match those filters.</p>
      ) : (
        <div style={{ overflowX: 'auto', marginTop: 'var(--space-3)' }}>
          <table className="table" style={{ width: '100%' }}>
            <caption className="sr-only">Catalogue items</caption>
            <thead>
              <tr>
                <th>Code</th>
                <th>Name</th>
                <th>Type</th>
                <th>Category</th>
                <th>Current CASH price</th>
                <th>Active</th>
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
                </tr>
              ))}
            </tbody>
          </table>
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

      <div style={{ marginTop: 'var(--space-4)', borderTop: '1px solid var(--border)', paddingTop: 'var(--space-3)' }}>
        {!showDeactivatePanel ? (
          <button type="button" className="btn btn-danger" onClick={() => setShowDeactivatePanel(true)}>
            Deactivate all items…
          </button>
        ) : (
          <DeactivateAllPanel onClose={() => setShowDeactivatePanel(false)} />
        )}
      </div>
    </section>
  );
}

// Bordered, typed-confirmation gate. Explains what will and won't happen
// (soft-delete, historical invoices unaffected, prices closed) before
// the button unlocks.
function DeactivateAllPanel({ onClose }: { onClose: () => void }) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [typed, setTyped] = useState('');
  const [message, setMessage] = useState<string | null>(null);

  const deactivate = useMutation({
    mutationFn: () => deactivateAllCatalogItems(typed, accessToken as string),
    onSuccess: (result) => {
      setMessage(`Deactivated ${result.deactivatedCount} item${result.deactivatedCount === 1 ? '' : 's'}.`);
      setTyped('');
      queryClient.invalidateQueries({ queryKey: ['catalog-items'] });
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
        Deactivate every catalogue item?
      </div>
      <ul style={{ margin: '0 0 var(--space-2)', paddingLeft: 'var(--space-4)', fontSize: 'var(--text-sm)' }}>
        <li>Every currently active service item will be soft-deactivated (is_active = false).</li>
        <li>Their current CASH price entries are closed (valid_to = today). Historical invoices keep their line snapshots.</li>
        <li>A later re-upload of the same PRODUCT CODE brings it back on.</li>
        <li>Nothing is hard-deleted from the database.</li>
      </ul>
      <label style={{ display: 'block', fontSize: 'var(--text-sm)', marginTop: 'var(--space-2)' }}>
        To confirm, type: <code style={{ background: 'var(--bg-surface)', padding: '0 var(--space-1)', borderRadius: 2 }}>{DEACTIVATE_ALL_CONFIRMATION}</code>
      </label>
      <input
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
      <table className="table" style={{ width: '100%' }}>
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
      </table>
    </div>
  );
}
