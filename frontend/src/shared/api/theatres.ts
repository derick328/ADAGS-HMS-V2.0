import { authFetch } from './client';
import type { components } from './types';

export type TheatreResponse = components['schemas']['TheatreResponse'];

/**
 * 07-doctor.md §7 / phase 8 surgery: theatres this facility runs. Gated
 * server-side on SURGERY_ORDER_CREATE (facility scope) — same permission
 * that lets the caller actually schedule against one of the returned rows.
 */
export function fetchTheatresByFacility(facilityId: string, accessToken: string): Promise<TheatreResponse[]> {
  return authFetch(`/api/theatres?facilityId=${encodeURIComponent(facilityId)}`, accessToken);
}
