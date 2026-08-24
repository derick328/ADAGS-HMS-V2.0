import { authFetch } from './client';
import type { components } from './types';

export type PrescriptionLineResponse = components['schemas']['PrescriptionLineResponse'];
export type MarkUnavailableRequest = components['schemas']['MarkUnavailableRequest'];

/**
 * 12-pharmacist.md §6.2: the pharmacist could not fulfil this line — the
 * doctor is notified to re-prescribe or cancel, any paid amount refunds
 * through the accountant path, an unpaid charge voids automatically.
 */
export function markPrescriptionLineUnavailable(
  prescriptionLineId: string,
  body: MarkUnavailableRequest,
  accessToken: string,
): Promise<PrescriptionLineResponse> {
  return authFetch(`/api/prescription-lines/${prescriptionLineId}/mark-unavailable`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
