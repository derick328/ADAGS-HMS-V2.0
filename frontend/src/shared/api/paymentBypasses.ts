import { authFetch } from './client';
import type { components } from './types';

export type EmergencyBypassResponse = components['schemas']['EmergencyBypassResponse'];
export type GrantAdminWaiverRequest = components['schemas']['GrantAdminWaiverRequest'];
export type GrantAdminWaiverResponse = components['schemas']['GrantAdminWaiverResponse'];

export function fetchActivePaymentBypasses(accessToken: string): Promise<EmergencyBypassResponse[]> {
  return authFetch('/api/payment-bypasses/active', accessToken);
}

/** 02-billing-architecture.md §8's ADMIN_WAIVER route — admin grants directly, no second actor. */
export function grantAdminWaiver(body: GrantAdminWaiverRequest, accessToken: string): Promise<GrantAdminWaiverResponse> {
  return authFetch('/api/payment-bypasses/admin-waiver', accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
