import { authFetch } from './client';
import type { components } from './types';

export type PrescriptionLineResponse = components['schemas']['PrescriptionLineResponse'];
export type PlacePrescriptionRequest = components['schemas']['PlacePrescriptionRequest'];
export type PrescriptionFrequency = NonNullable<PlacePrescriptionRequest['frequency']>;
export type PrescriptionHistoryResponse = components['schemas']['PrescriptionHistoryResponse'];
export type PrescriptionQueueRowResponse = components['schemas']['PrescriptionQueueRowResponse'];

export function placePrescription(body: PlacePrescriptionRequest, accessToken: string): Promise<PrescriptionLineResponse> {
  return authFetch('/api/prescriptions', accessToken, { method: 'POST', body: JSON.stringify(body) });
}

/** 08-doctor-navigation.md §5: Request History — this doctor's own prescription lines, with live status. */
export function fetchMyPrescriptions(accessToken: string): Promise<PrescriptionHistoryResponse[]> {
  return authFetch('/api/prescriptions/mine', accessToken);
}

/** 08-doctor-navigation.md §3.3: every prescription line on this encounter, across every prescriber. */
export function fetchPrescriptionsByEncounter(encounterId: string, accessToken: string): Promise<PrescriptionHistoryResponse[]> {
  return authFetch(`/api/prescriptions/by-encounter/${encounterId}`, accessToken);
}

/**
 * 12-pharmacist.md §4: the Dashboard's single source list — one flat row
 * per open prescription line. The frontend buckets into the dashboard's
 * queues and sorts client-side, same "server returns the data, client owns
 * the tab split" pattern as labQueueBuckets.ts.
 */
export function fetchPrescriptionQueue(accessToken: string): Promise<PrescriptionQueueRowResponse[]> {
  return authFetch('/api/prescriptions/queue', accessToken);
}
