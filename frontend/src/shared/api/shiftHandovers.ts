import { authFetch, authFetchVoid } from './client';
import type { components } from './types';

export type ShiftHandoverResponse = components['schemas']['ShiftHandoverResponse'];
export type RecordShiftHandoverRequest = components['schemas']['RecordShiftHandoverRequest'];

export function fetchShiftHandovers(admissionId: string, accessToken: string): Promise<ShiftHandoverResponse[]> {
  return authFetch(`/api/admissions/${admissionId}/shift-handovers`, accessToken);
}

/** 17-ward-nurse.md §7: outgoing nurse records SBAR — pre-population is assembled client-side from what the system already knows. */
export function recordShiftHandover(
  admissionId: string,
  body: RecordShiftHandoverRequest,
  accessToken: string,
): Promise<ShiftHandoverResponse> {
  return authFetch(`/api/admissions/${admissionId}/shift-handovers`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/** §7: incoming nurse acknowledges — an unacknowledged handover stays flagged. */
export function acknowledgeShiftHandover(admissionId: string, id: string, accessToken: string): Promise<void> {
  return authFetchVoid(`/api/admissions/${admissionId}/shift-handovers/${id}/acknowledge`, accessToken, { method: 'POST' });
}
