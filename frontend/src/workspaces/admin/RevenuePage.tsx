import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  fetchPendingCancellationRequests,
  reviewCancellationRequest,
  type InvoiceCancellationRequestResponse,
} from '../../shared/api/billing';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { QueueShell, type QueueRow } from '../../shared/components/QueueShell';

// 18-admin.md §6: the admin's half of the cancellation lifecycle —
// receptionist requests, admin decides. This queue only ever shows
// PENDING requests; that's the only list endpoint the backend exposes
// (no history/all-requests view exists yet), which matches "queue of
// things to act on," not an audit trail.
export function RevenuePage() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const canApprove = usePermission('INVOICE_CANCEL_APPROVE');
  const [reviewingId, setReviewingId] = useState<string | null>(null);
  const [reviewNote, setReviewNote] = useState('');

  const { data: requests, isLoading } = useQuery({
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
      <QueueShell
        title="Revenue / invoice cancellations"
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
