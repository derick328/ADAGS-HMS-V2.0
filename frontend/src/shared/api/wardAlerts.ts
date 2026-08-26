import { authFetch, authFetchVoid } from './client';
import type { components } from './types';

export type WardAlertNotification = components['schemas']['WardAlertNotificationResponse'];
export type WardAlertSourceType = NonNullable<WardAlertNotification['sourceType']>;

// 17-ward-nurse.md §4.3: "An alert must reach someone" — vital breaches,
// wound infections, and drug-unavailable stockouts all land here.
export function fetchWardAlerts(admissionId: string, accessToken: string): Promise<WardAlertNotification[]> {
  return authFetch(`/api/admissions/${admissionId}/alerts`, accessToken);
}

export function acknowledgeWardAlert(admissionId: string, id: string, accessToken: string): Promise<void> {
  return authFetchVoid(`/api/admissions/${admissionId}/alerts/${id}/acknowledge`, accessToken, { method: 'POST' });
}
