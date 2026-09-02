import { authFetch } from './client';
import type { components } from './types';

export type TheatreResponse = components['schemas']['TheatreResponse'];
export type CreateTheatreRequest = components['schemas']['CreateTheatreRequest'];

/**
 * 07-doctor.md §7 / phase 8 surgery: active theatres this facility runs.
 * Gated server-side on SURGERY_ORDER_CREATE or THEATRE_MANAGE (facility
 * scope) — schedulers and admins each have a legitimate reason to read
 * the list.
 */
export function fetchTheatresByFacility(facilityId: string, accessToken: string): Promise<TheatreResponse[]> {
  return authFetch(`/api/theatres?facilityId=${encodeURIComponent(facilityId)}`, accessToken);
}

/**
 * Admin management view — active AND inactive theatres. Gated on
 * THEATRE_MANAGE only; a scheduler never sees a parked row.
 */
export function fetchAllTheatresByFacility(
  facilityId: string,
  accessToken: string,
): Promise<TheatreResponse[]> {
  return authFetch(
    `/api/theatres?facilityId=${encodeURIComponent(facilityId)}&includeInactive=true`,
    accessToken,
  );
}

/** 18-admin.md §2 "Facility & settings" — admin defines the physical resource. Gated on THEATRE_MANAGE (facility scope) server-side. */
export function createTheatre(body: CreateTheatreRequest, accessToken: string): Promise<TheatreResponse> {
  return authFetch('/api/theatres', accessToken, { method: 'POST', body: JSON.stringify(body) });
}

/**
 * Toggle a theatre's availability to the doctor's schedule-surgery
 * picker. Root CLAUDE.md "Data": never a hard delete — deactivate is a
 * switch, not a delete, so the doctor's historical surgery orders keep
 * pointing at a row that still exists.
 */
export function activateTheatre(id: string, accessToken: string): Promise<TheatreResponse> {
  return authFetch(`/api/theatres/${encodeURIComponent(id)}/activate`, accessToken, { method: 'POST' });
}

export function deactivateTheatre(id: string, accessToken: string): Promise<TheatreResponse> {
  return authFetch(`/api/theatres/${encodeURIComponent(id)}/deactivate`, accessToken, { method: 'POST' });
}
