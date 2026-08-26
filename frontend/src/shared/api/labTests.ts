import { authFetch } from './client';
import type { components } from './types';

export type LabTestResponse = components['schemas']['LabTestResponse'];
export type LabTestParameterResponse = components['schemas']['LabTestParameterResponse'];

/** 15-lab-technician.md §5.1/§10: the active catalogue — Result Entry's form and Lab Catalogue's context. */
export function fetchLabTests(accessToken: string): Promise<LabTestResponse[]> {
  return authFetch('/api/lab-tests', accessToken);
}

export function fetchLabTest(id: string, accessToken: string): Promise<LabTestResponse> {
  return authFetch(`/api/lab-tests/${id}`, accessToken);
}
