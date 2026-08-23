import { authFetch } from './client';
import type { components } from './types';

export type DepartmentResponse = components['schemas']['DepartmentResponse'];

export function fetchDepartments(accessToken: string): Promise<DepartmentResponse[]> {
  return authFetch('/api/departments', accessToken);
}
