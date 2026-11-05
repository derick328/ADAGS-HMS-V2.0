import { authFetch } from './client';
import type { components } from './types';
export type Presence = components['schemas']['Presence'];
export type VisitSummary = components['schemas']['VisitSummary'];
export function fetchPresence(facilityId: string, token: string, patientId?: string, departed = false): Promise<Presence[]> {
  const params = new URLSearchParams({ facilityId, departed: String(departed) });
  if (patientId) params.set('patientId', patientId);
  return authFetch(`/api/patient-presence?${params}`, token);
}
export function fetchVisitSummaries(facilityId: string, patientIds: string[], token: string): Promise<VisitSummary[]> {
  return authFetch(`/api/patient-presence/summaries?${new URLSearchParams({ facilityId, patientIds: patientIds.join(',') })}`, token);
}
export function confirmDeparture(id: string, body: components['schemas']['DepartureRequest'], token: string): Promise<components['schemas']['DepartureResponse']> {
  return authFetch(`/api/encounters/${id}/departure`, token, { method: 'POST', body: JSON.stringify(body) });
}
export function correctDeparture(id: string, body: components['schemas']['DepartureCorrectionRequest'], token: string): Promise<void> {
  return authFetch(`/api/encounters/${id}/departure/corrections`, token, { method: 'POST', body: JSON.stringify(body) });
}
