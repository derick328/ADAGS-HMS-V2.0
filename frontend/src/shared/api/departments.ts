import { authFetch } from './client';
import type { components } from './types';

export type DepartmentResponse = components['schemas']['DepartmentResponse'];

export function fetchDepartments(accessToken: string): Promise<DepartmentResponse[]> {
  return authFetch('/api/departments', accessToken);
}

export interface DepartmentCategory { id: string; code: string; name: string; active: boolean }
export interface ClinicUnit { id: string; facilityId: string; categoryId: string; code: string; name: string; description: string | null; unitType: string; consultationEnabled: boolean; active: boolean; hodUserId: string | null; hodUsername: string | null }
export interface ClinicAssignment { departmentId: string; primary: boolean; hod: boolean }
export interface ClinicStaff { userId: string; username: string; primary: boolean; active: boolean }
export interface ClinicAssignmentUpdate { unitIds: string[]; primaryUnitId: string | null; hodUnitId: string | null; vacateHodUnitIds?: string[] }
export const fetchClinicCategories = (token: string): Promise<DepartmentCategory[]> => authFetch('/api/departments/categories', token);
export const fetchClinicCatalogue = (token: string): Promise<ClinicUnit[]> => authFetch('/api/departments/catalogue', token);
export const fetchMyClinics = (token: string): Promise<ClinicAssignment[]> => authFetch('/api/departments/mine', token);
export const fetchManagedClinics = (token: string): Promise<ClinicUnit[]> => authFetch('/api/department-management/units', token);
export const fetchClinicStaff = (id: string, token: string): Promise<ClinicStaff[]> => authFetch(`/api/department-management/units/${id}/staff`, token);
export const fetchUserClinics = (id: string, token: string): Promise<ClinicAssignment[]> => authFetch(`/api/users/${id}/clinic-assignments`, token);
export const updateUserClinics = (id: string, value: ClinicAssignmentUpdate, token: string): Promise<void> => authFetch(`/api/users/${id}/clinic-assignments`, token, { method: 'PUT', body: JSON.stringify(value) });
export const appointHod = (id: string, userId: string, token: string): Promise<void> => authFetch(`/api/department-management/units/${id}/hod`, token, { method: 'PUT', body: JSON.stringify({ userId }) });
export const vacateHod = (id: string, token: string): Promise<void> => authFetch(`/api/department-management/units/${id}/hod`, token, { method: 'DELETE' });
export const createClinic = (value: unknown, token: string): Promise<string> => authFetch('/api/department-management/units', token, { method: 'POST', body: JSON.stringify(value) });
export const updateClinic = (id: string, value: unknown, token: string): Promise<void> => authFetch(`/api/department-management/units/${id}`, token, { method: 'PUT', body: JSON.stringify(value) });
