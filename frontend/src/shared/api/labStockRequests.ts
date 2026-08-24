import { authFetch } from './client';
import type { components } from './types';

export type LabStockRequestResponse = components['schemas']['LabStockRequestResponse'];
export type CreateLabStockRequestRequest = components['schemas']['CreateLabStockRequestRequest'];

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
