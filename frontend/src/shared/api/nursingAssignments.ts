import { authFetch } from './client';
import type { components } from './types';

export type NursingAssignmentHistoryRowResponse = components['schemas']['NursingAssignmentHistoryRowResponse'];

/** 17-ward-nurse.md §11: "My History" — this nurse's own roster assignments, newest first. */
export function fetchMyNursingAssignments(accessToken: string): Promise<NursingAssignmentHistoryRowResponse[]> {
  return authFetch('/api/nursing-assignments/mine', accessToken);
}
