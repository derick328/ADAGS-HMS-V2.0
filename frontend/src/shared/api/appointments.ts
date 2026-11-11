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

export type ClinicAppointment = AppointmentResponse & {
  clinicFollowUpId?: string;
  arrivedAt?: string;
  noShowAt?: string;
  departmentId?: string;
  clinic?: string;
  doctor?: string;
};
export type ClinicPlan = {
  id: string;
  patientId: string;
  facilityId: string;
  departmentId: string;
  providerId: string;
  clinic: string;
  doctor: string;
  active: boolean;
};
export function fetchPatientAppointments(patientId: string, token: string): Promise<ClinicAppointment[]> {
  return authFetch(`/api/appointments/patient/${patientId}`, token);
}
export function fetchClinicPlans(facilityId: string, token: string): Promise<ClinicPlan[]> {
  return authFetch(`/api/appointments/clinic-plans?facilityId=${facilityId}`, token);
}
export function fetchMyClinicPlans(token: string): Promise<ClinicPlan[]> {
  return authFetch('/api/appointments/clinic-plans?mine=true', token);
}
export function fetchPatientClinicPlans(patientId: string, token: string): Promise<ClinicPlan[]> {
  return authFetch(`/api/appointments/clinic-plans?patientId=${patientId}`, token);
}
export function fetchActiveClinicAppointments(token: string): Promise<ClinicAppointment[]> {
  return authFetch('/api/appointments/clinic-active', token);
}
export function scheduleClinicVisit(body: {
  patientId: string; sourceEncounterId: string; departmentId: string; clinicName: string; scheduledAt: string; instructions?: string;
}, token: string): Promise<ClinicAppointment> {
  return authFetch('/api/appointments/clinic', token, { method: 'POST', body: JSON.stringify(body) });
}
export function rescheduleClinicVisit(id: string, body: { scheduledAt: string; instructions?: string }, token: string): Promise<ClinicAppointment> {
  return authFetch(`/api/appointments/${id}/reschedule`, token, { method: 'PATCH', body: JSON.stringify(body) });
}
export function closeClinicPlan(id: string, token: string): Promise<void> {
  return authFetch(`/api/appointments/clinic-plans/${id}/close`, token, { method: 'POST' });
}

export function clinicArrivalLabel(scheduledAt: string | undefined, arrival = new Date()): string {
  if (!scheduledAt) return 'Scheduled';
  const localDay = (date: Date) => new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Dar_es_Salaam', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(date);
  const scheduled = localDay(new Date(scheduledAt));
  const actual = localDay(arrival);
  return actual < scheduled ? 'Arrived Early' : actual === scheduled ? 'Due Today' : 'Overdue';
}
export function markClinicNoShow(id: string, token: string): Promise<ClinicAppointment> {
  return authFetch(`/api/appointments/${id}/no-show`, token, { method: 'POST' });
}

export function canCancelOverdueClinic(scheduledAt: string | undefined, now = new Date()): boolean {
  return !!scheduledAt && now.getTime() >= new Date(scheduledAt).getTime() + 24 * 60 * 60 * 1000;
}

export function cancelOverdueClinic(id: string, token: string): Promise<ClinicAppointment> {
  return authFetch(`/api/appointments/${id}/cancel`, token, { method: 'POST' });
}
