import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  fetchPendingPharmacyItemProposals,
  reviewPharmacyItemProposal,
  type PharmacyItemProposalResponse,
} from '../../shared/api/pricing';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
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
