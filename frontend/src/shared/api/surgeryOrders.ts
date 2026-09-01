import { authFetch, authFetchVoid } from './client';
import type { components } from './types';

export type SurgeryOrderResponse = components['schemas']['SurgeryOrderResponse'];
export type SurgeryOrderStatus = NonNullable<SurgeryOrderResponse['status']>;
export type SurgeryUrgency = NonNullable<SurgeryOrderResponse['urgency']>;
export type SurgeryDisposition = NonNullable<SurgeryOrderResponse['disposition']>;
export type RecordSurgeryDispositionRequest = components['schemas']['RecordSurgeryDispositionRequest'];
export type CancelSurgeryOrderRequest = components['schemas']['CancelSurgeryOrderRequest'];
export type CancellationReason = NonNullable<CancelSurgeryOrderRequest['reason']>;
export type ScheduleSurgeryRequest = components['schemas']['ScheduleSurgeryRequest'];

/** 07-doctor.md §7: doctor books a surgery from the consultation's Schedule Surgery flow. Server posts the procedure charge in the same transaction. */
export function scheduleSurgery(body: ScheduleSurgeryRequest, accessToken: string): Promise<SurgeryOrderResponse> {
  return authFetch('/api/surgery-orders', accessToken, { method: 'POST', body: JSON.stringify(body) });
}

/** 17-ward-nurse.md §10: the Surgery Queue's source list — every order not yet COMPLETED/CANCELLED. */
export function fetchActiveSurgeryOrders(accessToken: string): Promise<SurgeryOrderResponse[]> {
  return authFetch('/api/surgery-orders', accessToken);
}

export function fetchSurgeryOrderById(id: string, accessToken: string): Promise<SurgeryOrderResponse> {
  return authFetch(`/api/surgery-orders/${id}`, accessToken);
}

/** SCHEDULED -> IN_PROGRESS: consent and the checklist/billing gate are re-checked live, server-side. */
export function startSurgeryOrder(id: string, accessToken: string): Promise<SurgeryOrderResponse> {
  return authFetch(`/api/surgery-orders/${id}/start`, accessToken, { method: 'POST' });
}

export function revokeSurgeryConsent(id: string, accessToken: string): Promise<void> {
  return authFetchVoid(`/api/surgery-orders/${id}/revoke-consent`, accessToken, { method: 'POST' });
}

/** COMPLETED gates payment — only after every prepared procedure item is reconciled. */
export function completeSurgeryOrder(id: string, accessToken: string): Promise<SurgeryOrderResponse> {
  return authFetch(`/api/surgery-orders/${id}/complete`, accessToken, { method: 'POST' });
}

export function recordSurgeryDisposition(
  id: string,
  body: RecordSurgeryDispositionRequest,
  accessToken: string,
): Promise<SurgeryOrderResponse> {
  return authFetch(`/api/surgery-orders/${id}/disposition`, accessToken, { method: 'POST', body: JSON.stringify(body) });
}

export function cancelSurgeryOrder(
  id: string,
  body: CancelSurgeryOrderRequest,
  accessToken: string,
): Promise<SurgeryOrderResponse> {
  return authFetch(`/api/surgery-orders/${id}/cancel`, accessToken, { method: 'POST', body: JSON.stringify(body) });
}
