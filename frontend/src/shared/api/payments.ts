import { authFetch } from './client';
import type { components } from './types';

export type PaymentResponse = components['schemas']['PaymentResponse'];
export type RecordPaymentRequest = components['schemas']['RecordPaymentRequest'];
export type PaymentMethod = NonNullable<RecordPaymentRequest['method']>;

// 03-billing-addendum.md §1: "the payment screen is a line picker" — body
// carries the ticked allocations, never a typed total.
export function recordPayment(body: RecordPaymentRequest, accessToken: string): Promise<PaymentResponse> {
  return authFetch('/api/payments', accessToken, { method: 'POST', body: JSON.stringify(body) });
}
