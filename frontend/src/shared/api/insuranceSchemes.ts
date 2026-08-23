import { authFetch } from './client';
import type { components } from './types';

export type InsuranceSchemeResponse = components['schemas']['InsuranceSchemeResponse'];

export function fetchInsuranceSchemes(accessToken: string): Promise<InsuranceSchemeResponse[]> {
  return authFetch('/api/insurance-schemes', accessToken);
}
