import { authFetch } from './client';
import type { components } from './types';

export type LabStockRequestResponse = components['schemas']['LabStockRequestResponse'];
export type CreateLabStockRequestRequest = components['schemas']['CreateLabStockRequestRequest'];
export type LabStockRequestQueueRowResponse = components['schemas']['LabStockRequestQueueRowResponse'];
export type ReleaseLabStockRequestRequest = components['schemas']['ReleaseLabStockRequestRequest'];
export type RespondToLabStockRequestRequest = components['schemas']['RespondToLabStockRequestRequest'];

// 15-lab-technician.md §11: request reagents from pharmacy. No billing
// gate — internal movement, not a patient-facing dispense.
export function createLabStockRequest(
  body: CreateLabStockRequestRequest,
  accessToken: string,
): Promise<LabStockRequestResponse> {
  return authFetch('/api/lab-stock-requests', accessToken, { method: 'POST', body: JSON.stringify(body) });
}

/** The Stock Requests screen's own list — status, response notes, the sidebar's pending-count badge. */
export function fetchMyLabStockRequests(accessToken: string): Promise<LabStockRequestResponse[]> {
  return authFetch('/api/lab-stock-requests/mine', accessToken);
}

/** 12-pharmacist.md §8: the pharmacist's review queue — every open request across every lab technician, oldest first. */
export function fetchPendingLabStockRequests(accessToken: string): Promise<LabStockRequestQueueRowResponse[]> {
  return authFetch('/api/lab-stock-requests/pending', accessToken);
}

/** 12-pharmacist.md §8: a release is a transfer, not a dispense — two movement rows, no billing gate. Partial release permitted. */
export function releaseLabStockRequest(
  id: string,
  body: ReleaseLabStockRequestRequest,
  accessToken: string,
): Promise<LabStockRequestResponse> {
  return authFetch(`/api/lab-stock-requests/${id}/release`, accessToken, { method: 'POST', body: JSON.stringify(body) });
}

/** 12-pharmacist.md §8: reject requires response notes. */
export function rejectLabStockRequest(
  id: string,
  body: RespondToLabStockRequestRequest,
  accessToken: string,
): Promise<LabStockRequestResponse> {
  return authFetch(`/api/lab-stock-requests/${id}/reject`, accessToken, { method: 'POST', body: JSON.stringify(body) });
}
