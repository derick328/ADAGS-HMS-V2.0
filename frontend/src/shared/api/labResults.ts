import { ApiError, authFetch } from './client';
import type { components } from './types';

export type LabResultResponse = components['schemas']['LabResultResponse'];
export type LabResultDetailResponse = components['schemas']['LabResultDetailResponse'] & {
  resultType?: string; singleValue?: string; singleValueUnit?: string;
  overallInterpretation?: string; remarks?: string; labReportObjectKey?: string;
};
export type DetailParameterValueResponse = components['schemas']['DetailParameterValueResponse'];
export type RecordLabResultRequest = components['schemas']['RecordLabResultRequest'];
export type AmendLabResultRequest = components['schemas']['AmendLabResultRequest'];
export type ParameterValueRequest = components['schemas']['ParameterValueRequest'];
export type ResultFlag = NonNullable<ParameterValueRequest['overrideFlag']>;

// 15-lab-technician.md §5: release on submit — results reach the doctor
// immediately. Verification is separate/retrospective and not part of this
// workspace (LAB_RESULT_VERIFY is granted to nobody in the current seed
// data, so lab.verification_active is effectively false — v0.2 §6: no
// badge in that state).
export function recordLabResult(body: RecordLabResultRequest & {
  resultType?: string; singleValue?: string; singleValueUnit?: string;
  overallInterpretation?: string; remarks?: string; labReportObjectKey?: string;
}, accessToken: string): Promise<LabResultResponse> {
  return authFetch('/api/lab-results', accessToken, { method: 'POST', body: JSON.stringify(body) });
}

export function amendLabResult(id: string, body: AmendLabResultRequest, accessToken: string): Promise<LabResultResponse> {
  return authFetch(`/api/lab-results/${id}/amend`, accessToken, { method: 'POST', body: JSON.stringify(body) });
}

/** §9: the printable-report / amendment-supersession detail view. */
export function fetchLabResult(id: string, accessToken: string): Promise<LabResultDetailResponse> {
  return authFetch(`/api/lab-results/${id}`, accessToken);
}

/** §9: "My results" — this technician's own completed work. */
export function fetchMyLabResults(accessToken: string): Promise<LabResultDetailResponse[]> {
  return authFetch('/api/lab-results/mine', accessToken);
}

/** 08-doctor-navigation.md §5: doctor's Request History detail — every result released against one order (newest first, superseded amendments included). */
export function fetchLabResultsByOrder(labOrderId: string, accessToken: string): Promise<LabResultDetailResponse[]> {
  return authFetch(`/api/lab-results/by-lab-order/${labOrderId}`, accessToken);
}

/** §9: "Patient lookup" — by patient or accession, every result regardless of technician. */
export function searchLabResults(
  params: { patientId?: string; accessionNo?: string },
  accessToken: string,
): Promise<LabResultDetailResponse[]> {
  const query = new URLSearchParams();
  if (params.patientId) query.set('patientId', params.patientId);
  if (params.accessionNo) query.set('accessionNo', params.accessionNo);
  return authFetch(`/api/lab-results/search?${query.toString()}`, accessToken);
}

/**
 * §5.4: machine output goes to object storage, never a database blob.
 * Uploads first; the returned objectKey then goes into
 * RecordLabResultRequest.machineOutputObjectKey. Bypasses authFetch's
 * JSON content-type default — a multipart body needs the browser-set
 * boundary, not an explicit header.
 */
export async function uploadMachineOutput(labOrderId: string, file: File, accessToken: string): Promise<{ objectKey: string }> {
  const formData = new FormData();
  formData.append('file', file);
  const response = await fetch(`/api/lab-results/machine-output/${labOrderId}`,  {
    method: 'POST',
    credentials: 'include',
    headers: { Authorization: `Bearer ${accessToken}` },
    body: formData,
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(response.status, body);
  }
  return response.json();
}

/** A short-lived signed URL for viewing a stored machine output file — My History. */
export function fetchMachineOutputUrl(resultId: string, accessToken: string): Promise<{ url: string }> {
  return authFetch(`/api/lab-results/machine-output/${resultId}`, accessToken);
}

export interface LabResultDraft {
  id: string;
  specimenId: string;
  payload: Record<string, unknown>;
  version: number;
  status: 'DRAFT' | 'SUBMITTED';
  submittedResultId?: string;
  updatedAt: string;
}

export function fetchLabResultDraft(labOrderId: string, accessToken: string): Promise<LabResultDraft | null> {
  return authFetch(`/api/lab-orders/${labOrderId}/result-draft`, accessToken);
}

export function saveLabResultDraft(labOrderId: string, version: number, payload: Record<string, unknown>, accessToken: string): Promise<LabResultDraft> {
  return authFetch(`/api/lab-orders/${labOrderId}/result-draft`, accessToken, {
    method: 'PUT', body: JSON.stringify({ version, payload }),
  });
}

export function submitLabResultDraft(labOrderId: string, version: number, result: RecordLabResultRequest & {
  resultType?: string; singleValue?: string; singleValueUnit?: string;
  overallInterpretation?: string; remarks?: string; labReportObjectKey?: string;
}, accessToken: string): Promise<LabResultResponse> {
  return authFetch(`/api/lab-orders/${labOrderId}/result-draft/submit`, accessToken, {
    method: 'POST', body: JSON.stringify({ version, result }),
  });
}

export async function uploadLabReportPdf(labOrderId: string, file: File, accessToken: string): Promise<{ objectKey: string }> {
  const body = new FormData();
  body.append('file', file);
  const response = await fetch(`/api/lab-results/report/${labOrderId}`, {
    method: 'POST', credentials: 'include', headers: { Authorization: `Bearer ${accessToken}` }, body,
  });
  if (!response.ok) throw new ApiError(response.status, await response.json().catch(() => null));
  return response.json();
}

export function fetchLabReportUrl(resultId: string, accessToken: string): Promise<{ url: string }> {
  return authFetch(`/api/lab-results/report/${resultId}/url`, accessToken);
}

export function fetchDraftMachineOutputUrl(labOrderId: string, objectKey: string, accessToken: string): Promise<{ url: string }> {
  return authFetch(`/api/lab-results/machine-output/draft/${labOrderId}/${encodeURIComponent(objectKey)}`, accessToken);
}
