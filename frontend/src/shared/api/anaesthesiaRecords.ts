import { authFetch } from './client';
import type { components } from './types';

export type AnaesthesiaPaperRecordResponse = components['schemas']['AnaesthesiaPaperRecordResponse'];
export type RecordAnaesthesiaRequest = components['schemas']['RecordAnaesthesiaRequest'];

export function fetchAnaesthesiaRecords(
  surgeryOrderId: string,
  accessToken: string,
): Promise<AnaesthesiaPaperRecordResponse[]> {
  return authFetch(`/api/anaesthesia-paper-records?surgeryOrderId=${encodeURIComponent(surgeryOrderId)}`, accessToken);
}

/** 17-ward-nurse.md §10: a pointer to the paper anaesthesia record, not a structured chart — anaesthesiaType is deferred. */
export function recordAnaesthesia(
  body: RecordAnaesthesiaRequest,
  accessToken: string,
): Promise<AnaesthesiaPaperRecordResponse> {
  return authFetch('/api/anaesthesia-paper-records', accessToken, { method: 'POST', body: JSON.stringify(body) });
}
