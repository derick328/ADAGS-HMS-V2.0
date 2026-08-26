import { authFetch } from './client';
import type { components } from './types';

export type WorkShiftResponse = components['schemas']['WorkShiftResponse'];
export type ShiftAssignmentResponse = components['schemas']['ShiftAssignmentResponse'];

/** The shift definitions (e.g. "Morning", "Night") — what RecordShiftHandoverRequest's shiftFrom/shiftTo reference. */
export function fetchActiveWorkShifts(accessToken: string): Promise<WorkShiftResponse[]> {
  return authFetch('/api/work-shifts', accessToken);
}

/** 17-ward-nurse.md §7: "Incoming nurse (from roster)" — every WARD_NURSE roster row for this ward and date. */
export function fetchShiftAssignments(
  wardId: string,
  shiftDate: string,
  accessToken: string,
): Promise<ShiftAssignmentResponse[]> {
  return authFetch(
    `/api/shift-assignments?wardId=${encodeURIComponent(wardId)}&shiftDate=${encodeURIComponent(shiftDate)}`,
    accessToken,
  );
}
