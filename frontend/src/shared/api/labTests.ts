import { authFetch } from './client';
import type { components } from './types';

// V67 admin importer visibility (option 2): rows the admin imported as
// TYPE=Laboratory but which no lab_test classifies yet. Shown in the
// lab-order typeahead as unselectable placeholders so the doctor sees
// the row exists but cannot order it until a lab lead sets specimen
// type / container / TAT on a real lab_test.
export interface PendingLabClassification {
  serviceItemId: string;
  code: string;
  name: string;
  unselectableReason: string;
}

export type LabTestResponse = components['schemas']['LabTestResponse'];
export type LabTestParameterResponse = components['schemas']['LabTestParameterResponse'];

/** 15-lab-technician.md §5.1/§10: the active catalogue — Result Entry's form and Lab Catalogue's context. */
export function fetchLabTests(accessToken: string): Promise<LabTestResponse[]> {
  return authFetch('/api/lab-tests', accessToken);
}

export function fetchLabTest(id: string, accessToken: string): Promise<LabTestResponse> {
  return authFetch(`/api/lab-tests/${id}`, accessToken);
}

/**
 * Fetches LAB service_item rows the admin imported that no lab_test
 * classifies yet. Combined with the priced results from the shared
 * price catalogue to give the doctor's lab-order typeahead full
 * visibility of the catalogue — placeholders render as unselectable
 * per the same pattern the prescription typeahead uses for CONTROLLED
 * / not-yet-active items.
 */
export function fetchPendingLabClassifications(
  query: string,
  accessToken: string,
): Promise<PendingLabClassification[]> {
  return authFetch(`/api/lab-tests/pending-classification?q=${encodeURIComponent(query)}`, accessToken);
}
