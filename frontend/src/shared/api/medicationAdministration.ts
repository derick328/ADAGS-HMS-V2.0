import { authFetch } from './client';
import type { components } from './types';

export type MedicationAdministrationResponse = components['schemas']['MedicationAdministrationResponse'];
export type MedicationAdministrationStatus = NonNullable<MedicationAdministrationResponse['status']>;
export type OmissionReason = NonNullable<MedicationAdministrationResponse['omissionReason']>;
export type MarkAdministrationRequest = components['schemas']['MarkAdministrationRequest'];

// 17-ward-nurse.md §5.1: every generated slot, given or not — a missed dose
// is a visible row here, never an absence.
export function fetchMar(admissionId: string, accessToken: string): Promise<MedicationAdministrationResponse[]> {
  return authFetch(`/api/admissions/${admissionId}/mar`, accessToken);
}

/** The overdue view — DUE slots past their scheduled time. */
export function fetchOverdueMar(admissionId: string, accessToken: string): Promise<MedicationAdministrationResponse[]> {
  return authFetch(`/api/admissions/${admissionId}/mar/overdue`, accessToken);
}

/** §5: drug/dose/route stay read-only from the prescription — this only ever sets the outcome. */
export function markAdministration(
  admissionId: string,
  id: string,
  body: MarkAdministrationRequest,
  accessToken: string,
): Promise<MedicationAdministrationResponse> {
  return authFetch(`/api/admissions/${admissionId}/mar/${id}/mark`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
