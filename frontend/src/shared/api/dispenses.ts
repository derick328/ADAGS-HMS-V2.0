import { authFetch } from './client';
import type { components } from './types';

export type DispenseContextResponse = components['schemas']['DispenseContextResponse'];
export type DispenseRequest = components['schemas']['DispenseRequest'];
export type DispenseLineRequest = components['schemas']['DispenseLineRequest'];
export type DispenseResponse = components['schemas']['DispenseResponse'];
export type DispenseHistoryResponse = components['schemas']['DispenseHistoryResponse'];
export type DispenseLabelResponse = components['schemas']['DispenseLabelResponse'];

/**
 * 12-pharmacist.md §5.2: everything the dispensing screen needs in one
 * call — allergies (unmissable, root CLAUDE.md "Safety"), weight, dose
 * instructions, available batches, and the FEFO-suggested allocation to
 * pre-fill the form with.
 */
export function fetchDispenseContext(prescriptionLineId: string, accessToken: string): Promise<DispenseContextResponse> {
  return authFetch(`/api/prescription-lines/${prescriptionLineId}/dispense-context`, accessToken);
}

/**
 * 12-pharmacist.md §5.1: the gate is evaluated server-side, live, on this
 * call — never trust the queue row's gateSatisfied flag as the control.
 * A 402 means "send to cashier", not a client-side error.
 */
export function dispense(body: DispenseRequest, accessToken: string): Promise<DispenseResponse> {
  return authFetch('/api/dispenses', accessToken, { method: 'POST', body: JSON.stringify(body) });
}

/** 12-pharmacist.md §11: "My dispenses" — this pharmacist's own work, the History screen's default tab. */
export function fetchMyDispenses(accessToken: string): Promise<DispenseHistoryResponse[]> {
  return authFetch('/api/dispenses/mine', accessToken);
}

/** 12-pharmacist.md §11: "Patient lookup" — every dispense to this patient, regardless of recording pharmacist. */
export function searchDispensesByPatient(q: string, accessToken: string): Promise<DispenseHistoryResponse[]> {
  return authFetch(`/api/dispenses/patient-search?q=${encodeURIComponent(q)}`, accessToken);
}

/** 12-pharmacist.md §5.6: the dispensing label payload — rendering is the frontend's job. */
export function fetchDispenseLabel(dispenseId: string, accessToken: string): Promise<DispenseLabelResponse> {
  return authFetch(`/api/dispenses/${dispenseId}/label`, accessToken);
}
