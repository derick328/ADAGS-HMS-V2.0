import { authFetch } from './client';
import type { components } from './types';

export type AppointmentResponse = components['schemas']['AppointmentResponse'];
export type CreateFollowUpAppointmentRequest = components['schemas']['CreateFollowUpAppointmentRequest'];

export function fetchAppointmentsScheduledBetween(
  from: string,
  to: string,
  accessToken: string,
): Promise<AppointmentResponse[]> {
  return authFetch(`/api/appointments?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, accessToken);
}

export function fetchRecentAppointments(limit: number, accessToken: string): Promise<AppointmentResponse[]> {
  return authFetch(`/api/appointments/recent?limit=${limit}`, accessToken);
}

/** 05a-receptionist-decisions.md §3.2 — still SCHEDULED, due within 48h or already overdue, no upper bound. */
export function fetchExpectedReturnAppointments(accessToken: string): Promise<AppointmentResponse[]> {
  return authFetch('/api/appointments/expected-returns', accessToken);
}

export function createFollowUpAppointment(
  body: CreateFollowUpAppointmentRequest,
  accessToken: string,
): Promise<AppointmentResponse> {
  return authFetch('/api/appointments', accessToken, { method: 'POST', body: JSON.stringify(body) });
}
