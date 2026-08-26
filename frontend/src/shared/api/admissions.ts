import { authFetch } from './client';
import type { components } from './types';

export type AdmissionResponse = components['schemas']['AdmissionResponse'];
export type WardStatus = NonNullable<AdmissionResponse['wardStatus']>;
export type WardClass = NonNullable<AdmissionResponse['orderedWardClass']>;
export type WardPatientRowResponse = components['schemas']['WardPatientRowResponse'];
export type BedAssignmentResponse = components['schemas']['BedAssignmentResponse'];
export type AssignBedRequest = components['schemas']['AssignBedRequest'];
export type PlacementReason = NonNullable<AssignBedRequest['placementReason']>;
export type NursingAssignmentResponse = components['schemas']['NursingAssignmentResponse'];
export type AssignNurseRequest = components['schemas']['AssignNurseRequest'];
export type Shift = NonNullable<AssignNurseRequest['shift']>;
export type UpdateWardStatusRequest = components['schemas']['UpdateWardStatusRequest'];
export type CompleteDischargeRequest = components['schemas']['CompleteDischargeRequest'];
export type ReverseDischargeOrderRequest = components['schemas']['ReverseDischargeOrderRequest'];

/** 17-ward-nurse.md §3: the Dashboard/Ward Patients single source list — every patient currently on wardId. */
export function fetchWardPatients(wardId: string, accessToken: string): Promise<WardPatientRowResponse[]> {
  return authFetch(`/api/admissions?wardId=${encodeURIComponent(wardId)}`, accessToken);
}

export function fetchAdmissionById(id: string, accessToken: string): Promise<AdmissionResponse> {
  return authFetch(`/api/admissions/${id}`, accessToken);
}

export function assignBed(admissionId: string, body: AssignBedRequest, accessToken: string): Promise<BedAssignmentResponse> {
  return authFetch(`/api/admissions/${admissionId}/bed-assignments`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function assignNurse(
  admissionId: string,
  body: AssignNurseRequest,
  accessToken: string,
): Promise<NursingAssignmentResponse> {
  return authFetch(`/api/admissions/${admissionId}/nursing-assignments`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/** 17-ward-nurse.md §9: ADMITTED/STABLE/CRITICAL only — DISCHARGED and TRANSFERRED go through their own flows. */
export function updateWardStatus(
  admissionId: string,
  body: UpdateWardStatusRequest,
  accessToken: string,
): Promise<AdmissionResponse> {
  return authFetch(`/api/admissions/${admissionId}/ward-status`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/** 04-procedure-bedday-rules.md §3.6: stops billing — doctor, or that doctor's assigned nurse only. */
export function orderDischarge(admissionId: string, accessToken: string): Promise<AdmissionResponse> {
  return authFetch(`/api/admissions/${admissionId}/discharge-order`, accessToken, { method: 'POST' });
}

/** §3.9: reason required — billing resumes continuous from admission. */
export function reverseDischargeOrder(
  admissionId: string,
  body: ReverseDischargeOrderRequest,
  accessToken: string,
): Promise<AdmissionResponse> {
  return authFetch(`/api/admissions/${admissionId}/discharge-order/reversal`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/** §3.6/§3.7: actual departure — never blocked by an unpaid balance. */
export function completeDischarge(
  admissionId: string,
  body: CompleteDischargeRequest,
  accessToken: string,
): Promise<AdmissionResponse> {
  return authFetch(`/api/admissions/${admissionId}/discharge`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
