import { authFetch } from './client';
import type { components } from './types';

export type InvoiceCancellationRequestResponse = components['schemas']['InvoiceCancellationRequestResponse'];
export type ReviewInvoiceCancellationRequest = components['schemas']['ReviewInvoiceCancellationRequest'];
export type RequestInvoiceCancellationRequest = components['schemas']['RequestInvoiceCancellationRequest'];

// 05-receptionist.md §7.6/§8, 02-billing-architecture.md §10: the
// receptionist's half of the lifecycle — reason required, not optional.
// Admin's review side (approve/reject) is fetchPendingCancellationRequests/
// reviewCancellationRequest above, already wired in the admin workspace.
export function requestInvoiceCancellation(
  invoiceId: string,
  body: RequestInvoiceCancellationRequest,
  accessToken: string,
): Promise<InvoiceCancellationRequestResponse> {
  return authFetch(`/api/billing/invoices/${invoiceId}/cancellation-requests`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function fetchPendingCancellationRequests(accessToken: string): Promise<InvoiceCancellationRequestResponse[]> {
  return authFetch('/api/billing/cancellation-requests/pending', accessToken);
}

export function reviewCancellationRequest(
  id: string,
  body: ReviewInvoiceCancellationRequest,
  accessToken: string,
): Promise<InvoiceCancellationRequestResponse> {
  return authFetch(`/api/billing/cancellation-requests/${id}/review`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
