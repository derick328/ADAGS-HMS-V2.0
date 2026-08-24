import { authFetch, authFetchVoid } from './client';
import type { components } from './types';

export type CriticalResultNotificationResponse = components['schemas']['CriticalResultNotificationResponse'];

// 15-lab-technician.md §7: "tech's screen shows acknowledgement status so
// they know it landed" — polled right after a result carrying a critical
// value is submitted.
export function fetchCriticalResultNotificationsByResult(
  resultId: string,
  accessToken: string,
): Promise<CriticalResultNotificationResponse[]> {
  return authFetch(`/api/critical-result-notifications/by-result/${resultId}`, accessToken);
}

export function acknowledgeCriticalResultNotification(id: string, accessToken: string): Promise<void> {
  return authFetchVoid(`/api/critical-result-notifications/${id}/acknowledge`, accessToken, { method: 'POST' });
}
