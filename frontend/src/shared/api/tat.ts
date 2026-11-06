import { authFetch } from './client';
import type { components } from './types';

export type TatOverview = components['schemas']['TatOverview'];
export type TatDepartment = components['schemas']['TatDepartment'];
export type TatTrend = components['schemas']['TatTrend'];
export type TatWaiting = components['schemas']['TatWaiting'];
export type TatVisit = components['schemas']['TatVisit'];
export type TatStage = components['schemas']['TatStage'];
export type TatJourney = components['schemas']['TatJourney'];
export type TatActivity = components['schemas']['TatActivity'];
export type TatUserSummary = { userId: string; userName: string; services: number; completedServices: number; patients: number; averageWaitingSeconds: number | null; averageServiceSeconds: number | null; averageTatSeconds: number | null };
export type TatUserPatient = { patientId: string; encounterId: string; hospitalNo: string; patientName: string; services: number; completedServices: number; firstServiceAt: string; averageWaitingSeconds: number | null; averageServiceSeconds: number | null; averageTatSeconds: number | null };

type Period = { facilityId: string; from: string; to: string; workflowType?: string };

function query(params: Record<string, string | undefined>): string {
  const result = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => { if (value) result.set(key, value); });
  return result.toString();
}

export function fetchTatTimezone(facilityId: string, token: string): Promise<components['schemas']['TatTimezone']> {
  return authFetch(`/api/admin/tat/timezone?${query({ facilityId })}`, token);
}

export function fetchTatOverview(params: Period, token: string): Promise<TatOverview> {
  return authFetch(`/api/admin/tat/overview?${query(params)}`, token);
}

export function fetchTatDepartments(params: Period, token: string): Promise<TatDepartment[]> {
  return authFetch(`/api/admin/tat/departments?${query(params)}`, token);
}

export function fetchTatDepartmentUsers(params: Period & { workflowType: string }, token: string): Promise<TatUserSummary[]> {
  return authFetch(`/api/admin/tat/departments/${encodeURIComponent(params.workflowType)}/users?${query(params)}`, token);
}

export function fetchTatUserPatients(params: Period & { workflowType: string; userId: string; page: number }, token: string): Promise<TatUserPatient[]> {
  return authFetch(`/api/admin/tat/departments/${encodeURIComponent(params.workflowType)}/users/${encodeURIComponent(params.userId)}/patients?${query({ ...params, page: String(params.page) })}`, token);
}

export function fetchTatTrend(params: Period, token: string): Promise<TatTrend[]> {
  return authFetch(`/api/admin/tat/trend?${query(params)}`, token);
}

export function fetchTatWaiting(facilityId: string, workflowType: string | undefined, token: string): Promise<TatWaiting[]> {
  return authFetch(`/api/admin/tat/currently-waiting?${query({ facilityId, workflowType })}`, token);
}

export function fetchTatActivity(params: Period & { status?: string; page: number }, token: string): Promise<TatActivity[]> {
  return authFetch(`/api/admin/tat/activity?${query({ ...params, page: String(params.page) })}`, token);
}

export function fetchPatientTatVisits(facilityId: string, patientId: string, token: string): Promise<TatVisit[]> {
  return authFetch(`/api/admin/tat/patients/${patientId}/encounters?${query({ facilityId })}`, token);
}

export function fetchEncounterTat(facilityId: string, encounterId: string, token: string): Promise<TatJourney> {
  return authFetch(`/api/admin/tat/encounters/${encounterId}?${query({ facilityId })}`, token);
}
