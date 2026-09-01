import { authFetch } from './client';
import type { components } from './types';

export type NurseWorkloadResponse = components['schemas']['NurseWorkloadResponse'];

/**
 * 07-doctor.md §7 nurse picker: every WARD_NURSE/NURSE in this facility
 * with the count of active admissions each is currently carrying, least
 * loaded first. Doctor uses this to spread the load rather than pile a
 * fifth patient on someone already carrying four.
 */
export function fetchNurseWorkload(facilityId: string, accessToken: string): Promise<NurseWorkloadResponse[]> {
  return authFetch(`/api/nurses/workload?facilityId=${encodeURIComponent(facilityId)}`, accessToken);
}
