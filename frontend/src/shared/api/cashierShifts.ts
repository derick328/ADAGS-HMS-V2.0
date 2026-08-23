import { authFetch, ApiError } from './client';
import type { components } from './types';

export type CashierShiftResponse = components['schemas']['CashierShiftResponse'];
export type OpenCashierShiftRequest = components['schemas']['OpenCashierShiftRequest'];
export type CloseCashierShiftRequest = components['schemas']['CloseCashierShiftRequest'];
export type PaymentResponse = components['schemas']['PaymentResponse'];

export function openCashierShift(body: OpenCashierShiftRequest, accessToken: string): Promise<CashierShiftResponse> {
  return authFetch('/api/cashier-shifts', accessToken, { method: 'POST', body: JSON.stringify(body) });
}

/** Resolves to null when the current user has no open shift (404), rather than throwing. */
export async function fetchCurrentCashierShift(accessToken: string): Promise<CashierShiftResponse | null> {
  try {
    return await authFetch<CashierShiftResponse>('/api/cashier-shifts/current', accessToken);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

export function fetchCashierShiftPayments(shiftId: string, accessToken: string): Promise<PaymentResponse[]> {
  return authFetch(`/api/cashier-shifts/${shiftId}/payments`, accessToken);
}

/** 05-receptionist.md §7.1: "at end of shift they enter counted cash; the system computes expected, records the variance." Reconciliation is a separate, later accountant action. */
export function closeCashierShift(id: string, body: CloseCashierShiftRequest, accessToken: string): Promise<CashierShiftResponse> {
  return authFetch(`/api/cashier-shifts/${id}/close`, accessToken, { method: 'POST', body: JSON.stringify(body) });
}
