import { authFetch } from './client';
import type { components } from './types';

export type SurgicalChecklistResponse = components['schemas']['SurgicalChecklistResponse'];
export type ChecklistPhase = NonNullable<SurgicalChecklistResponse['phase']>;
export type CompleteChecklistRequest = components['schemas']['CompleteChecklistRequest'];

export function fetchChecklistsBySurgeryOrder(
  surgeryOrderId: string,
  accessToken: string,
): Promise<SurgicalChecklistResponse[]> {
  return authFetch(`/api/surgical-checklists?surgeryOrderId=${encodeURIComponent(surgeryOrderId)}`, accessToken);
}

/** 17-ward-nurse.md §10: SIGN_IN / TIME_OUT / SIGN_OUT, each blocking the next — SIGN_OUT's swab count is never waivable. */
export function completeChecklist(
  body: CompleteChecklistRequest,
  accessToken: string,
): Promise<SurgicalChecklistResponse> {
  return authFetch('/api/surgical-checklists', accessToken, { method: 'POST', body: JSON.stringify(body) });
}
