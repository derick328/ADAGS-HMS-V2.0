import { authFetch } from './client';
import type { components } from './types';

export type EncounterResponse = components['schemas']['EncounterResponse'];
export type EncounterState = NonNullable<EncounterResponse['state']>;
export type OpenEncounterRequest = components['schemas']['OpenEncounterRequest'];

export interface SearchEncountersParams {
  states?: EncounterState[];
  excludeTerminal?: boolean;
  createdFrom?: string;
  createdTo?: string;
}

export function searchEncounters(params: SearchEncountersParams, accessToken: string): Promise<EncounterResponse[]> {
  const query = new URLSearchParams();
  params.states?.forEach((state) => query.append('states', state));
  if (params.excludeTerminal) query.set('excludeTerminal', 'true');
  if (params.createdFrom) query.set('createdFrom', params.createdFrom);
  if (params.createdTo) query.set('createdTo', params.createdTo);
  return authFetch(`/api/encounters?${query.toString()}`, accessToken);
}

/** 05-receptionist.md §5.2 "Send to triage" — one click, no form. The one-open-encounter-per-patient guard is server-side: a second call for the same patient just returns the existing one. */
export function openEncounter(body: OpenEncounterRequest, accessToken: string): Promise<EncounterResponse> {
  return authFetch('/api/encounters', accessToken, { method: 'POST', body: JSON.stringify(body) });
}

/** 05a-receptionist-decisions.md §3.3: "check in returning patient" — resumes an AWAITING_RESULTS encounter, no new triage, no charge. */
export function resumeEncounter(id: string, accessToken: string): Promise<EncounterResponse> {
  return authFetch(`/api/encounters/${id}/resume`, accessToken, { method: 'POST' });
}

export function fetchEncounterById(id: string, accessToken: string): Promise<EncounterResponse> {
  return authFetch(`/api/encounters/${id}`, accessToken);
}

/** 02-billing-architecture.md §11: invoice creation's "default to the open encounter when there is one" lookup. Resolves to null (not an error) when there isn't one. */
export function fetchOpenEncounterForPatient(patientId: string, accessToken: string): Promise<EncounterResponse | null> {
  return authFetch(`/api/encounters/open-for-patient/${patientId}`, accessToken);
}
