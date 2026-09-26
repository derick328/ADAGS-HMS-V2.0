import { authFetch } from './client';
import type { components } from './types';

// The controller now enriches with preparedByName / reconciledByName /
// attestedByName so the report line reads "prepared by <username>",
// not "prepared by 2698b9a1-…". Typed as optional locally; the
// generated openapi picks them up on the next backend build.
type ProcedureItemResponseBase = components['schemas']['ProcedureItemResponse'];
export type ProcedureItemResponse = ProcedureItemResponseBase & {
  preparedByName?: string | null;
  reconciledByName?: string | null;
  attestedByName?: string | null;
};
export type ProcedurePhase = NonNullable<ProcedureItemResponseBase['phase']>;
// batchId is nullable server-side (default = FEFO batch) — the
// generated schema is one build behind; type it locally so the UI can
// omit it without a cast until the next openapi regeneration.
type PrepareProcedureItemRequestBase = components['schemas']['PrepareProcedureItemRequest'];
export type PrepareProcedureItemRequest = Omit<PrepareProcedureItemRequestBase, 'batchId'> & {
  batchId?: string;
};
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
