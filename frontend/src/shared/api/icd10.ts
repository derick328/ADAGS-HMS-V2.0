import { authFetch, authFetchVoid } from './client';
import type { components } from './types';

export type Icd10SearchResponse = components['schemas']['Icd10SearchResponse'];
export type Icd10SearchResult = components['schemas']['Icd10SearchResult'];

/** 07-doctor.md §3: min 2 chars, 200ms debounce is the caller's job — this just hits the endpoint. departmentId boosts department-common codes first. */
export function searchIcd10(query: string, departmentId: string | undefined, accessToken: string): Promise<Icd10SearchResponse> {
  const params = new URLSearchParams({ q: query });
  if (departmentId) params.set('departmentId', departmentId);
  return authFetch(`/api/reference/icd10/search?${params.toString()}`, accessToken);
}

/** §3: the mandatory selection-audit callback — records which of the searched candidates the doctor actually picked. */
export function recordIcd10Selection(logId: string, selectedCode: string, accessToken: string): Promise<void> {
  return authFetchVoid(`/api/reference/icd10/search/${logId}/selection`, accessToken, {
    method: 'POST',
    body: JSON.stringify({ selectedCode }),
  });
}
