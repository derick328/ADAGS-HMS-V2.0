import { authFetchVoid } from './client';

/** Explicit clinical start; server records the timestamp and deduplicates retries. */
export function startTriageAssessment(encounterId: string, accessToken: string): Promise<void> {
  return authFetchVoid(`/api/encounters/${encounterId}/triage-start`, accessToken, { method: 'POST' });
}

export function startPharmacyProcessing(prescriptionLineId: string, accessToken: string): Promise<void> {
  return authFetchVoid(`/api/prescription-lines/${prescriptionLineId}/processing-start`, accessToken, { method: 'POST' });
}
