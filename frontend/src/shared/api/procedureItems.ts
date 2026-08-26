import { authFetch } from './client';
import type { components } from './types';

export type ProcedureItemResponse = components['schemas']['ProcedureItemResponse'];
export type ProcedurePhase = NonNullable<ProcedureItemResponse['phase']>;
export type PrepareProcedureItemRequest = components['schemas']['PrepareProcedureItemRequest'];
export type ReconcileProcedureItemRequest = components['schemas']['ReconcileProcedureItemRequest'];

export function fetchProcedureItems(surgeryOrderId: string, accessToken: string): Promise<ProcedureItemResponse[]> {
  return authFetch(`/api/procedure-items?surgeryOrderId=${encodeURIComponent(surgeryOrderId)}`, accessToken);
}

/** 04-procedure-bedday-rules.md §2.2: priced from the catalogue by itemId — never a typed price. */
export function prepareProcedureItem(
  body: PrepareProcedureItemRequest,
  accessToken: string,
): Promise<ProcedureItemResponse> {
  return authFetch('/api/procedure-items', accessToken, { method: 'POST', body: JSON.stringify(body) });
}

/** §2.3: quantity_prepared == used + returned + wasted, wastage needs a reason — enforced server-side. */
export function reconcileProcedureItem(
  id: string,
  body: ReconcileProcedureItemRequest,
  accessToken: string,
): Promise<ProcedureItemResponse> {
  return authFetch(`/api/procedure-items/${id}/reconcile`, accessToken, { method: 'POST', body: JSON.stringify(body) });
}

/** §2.3: the surgeon's sign-off on a reconciled line — the circulating nurse counts, the surgeon attests. */
export function attestProcedureItem(id: string, accessToken: string): Promise<ProcedureItemResponse> {
  return authFetch(`/api/procedure-items/${id}/attest`, accessToken, { method: 'POST' });
}
