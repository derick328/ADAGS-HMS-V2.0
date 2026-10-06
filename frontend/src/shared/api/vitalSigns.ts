import { authFetch } from './client';
import type { components } from './types';

export type VitalSignsResponse = components['schemas']['VitalSignsResponse'];
export type RecordWardVitalsRequest = components['schemas']['RecordWardVitalsRequest'];
export type RecordSurgeryVitalsRequest = components['schemas']['RecordSurgeryVitalsRequest'];
export type RecordConsultationVitalsRequest = components['schemas']['RecordConsultationVitalsRequest'];
export type Avpu = NonNullable<VitalSignsResponse['avpu']>;

/** 07-doctor.md §5.1: the consultation left panel's triage-recorded vitals — newest first, every context. */
export function fetchVitalSignsByEncounter(encounterId: string, accessToken: string): Promise<VitalSignsResponse[]> {
  return authFetch(`/api/encounters/${encounterId}/vital-signs`, accessToken);
}

/**
 * 17-ward-nurse.md §4: ward-rounds observations, context=WARD_ROUNDS server-side.
 * Returns just the new record's id — refetch fetchVitalSignsByEncounter for the full row.
 */
export function recordWardVitals(
  admissionId: string,
  body: RecordWardVitalsRequest,
  accessToken: string,
): Promise<string> {
  return authFetch(`/api/admissions/${admissionId}/vitals`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/**
 * Consultation-time vitals recorded by the doctor. Optional in the workflow —
 * if triage already recorded a set, this replaces nothing; both rows persist
 * with distinct contexts and the doctor's screen shows the CONSULTATION one
 * when present, else the TRIAGE one. Context is pinned to CONSULTATION
 * server-side.
 */
export function recordConsultationVitals(
  encounterId: string,
  body: RecordConsultationVitalsRequest,
  accessToken: string,
): Promise<string> {
  return authFetch(`/api/encounters/${encounterId}/vital-signs`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/** 17-ward-nurse.md §10: post-op observations, context=POST_OP server-side. */
export function recordSurgeryVitals(
  surgeryOrderId: string,
  body: RecordSurgeryVitalsRequest,
  accessToken: string,
): Promise<string> {
  return authFetch(`/api/surgery-orders/${surgeryOrderId}/vitals`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
