import { authFetch } from './client';
import type { components } from './types';

export type LabOrderResponse = components['schemas']['LabOrderResponse'];
export type PlaceLabOrderRequest = components['schemas']['PlaceLabOrderRequest'];
export type LabOrderUrgency = NonNullable<PlaceLabOrderRequest['urgency']>;
export type LabOrderHistoryResponse = components['schemas']['LabOrderHistoryResponse'];
export type LabOrderQueueRowResponse = components['schemas']['LabOrderQueueRowResponse'];
export type ResultEntryFormResponse = components['schemas']['ResultEntryFormResponse'] & {
  specialInstructions?: string | null; urgency?: string; orderStatus?: string;
  sampleType?: string; collectedAt?: string; nationalId?: string | null;
};
export type ParameterFieldResponse = components['schemas']['ParameterFieldResponse'];

export function placeLabOrder(body: PlaceLabOrderRequest & { specialInstructions?: string }, accessToken: string): Promise<LabOrderResponse> {
  return authFetch('/api/lab-orders', accessToken, { method: 'POST', body: JSON.stringify(body) });
}

/** 08-doctor-navigation.md §5: Request History — this doctor's own lab requests, with live status. */
export function fetchMyLabOrders(accessToken: string): Promise<LabOrderHistoryResponse[]> {
  return authFetch('/api/lab-orders/mine', accessToken);
}

/** 08-doctor-navigation.md §3.3: every lab order on this encounter, across every ordering doctor. */
export function fetchLabOrdersByEncounter(encounterId: string, accessToken: string): Promise<LabOrderHistoryResponse[]> {
  return authFetch(`/api/lab-orders/by-encounter/${encounterId}`, accessToken);
}

/**
 * 15-lab-technician.md §3: the Test Queue's single source list — one flat
 * row per open order. The frontend buckets rows into the five sub-tabs
 * (ready to process / in progress / awaiting payment / emergency /
 * overdue) and applies §3.2's sort client-side, same pattern as every
 * other QueueShell screen.
 */
export function fetchLabOrderQueue(accessToken: string): Promise<LabOrderQueueRowResponse[]> {
  return authFetch('/api/lab-orders/queue', accessToken);
}

/** §5.1/§5.2: the generated Result Entry form — parameters with server-resolved reference ranges, tech enters values only. */
export function fetchResultEntryForm(labOrderId: string, accessToken: string): Promise<ResultEntryFormResponse> {
  return authFetch(`/api/lab-orders/${labOrderId}/result-entry-form`, accessToken);
}
