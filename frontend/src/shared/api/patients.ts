import { ApiError, authFetch } from './client';
import type { components } from './types';

export type PatientSearchResult = components['schemas']['PatientSearchResult'];
export type PatientResponse = components['schemas']['PatientResponse'];
export type RegisterPatientRequest = components['schemas']['RegisterPatientRequest'];
export type VisitHistoryResponse = components['schemas']['VisitHistoryResponse'];

// 05-receptionist.md §4.1: DuplicatePatientException attaches this shape to
// the 409's ProblemDetail body dynamically (GlobalExceptionHandler) —
// springdoc has no static return type to generate a schema from for it, so
// unlike everything else in this file, this one genuinely cannot come from
// api/openapi.yaml. Hand-typed to match com.adags.modules.patients.domain.DuplicateCandidate exactly.
export interface DuplicateCandidate {
  patientId: string;
  hospitalNo: string;
  fullName: string;
  nameSimilarity: number;
  reasons: ('NATIONAL_ID_EXACT' | 'NAME_SIMILARITY_WITH_DOB_MATCH' | 'NAME_SIMILARITY' | 'PHONE_EXACT')[];
}

export class DuplicatePatientError extends Error {
  constructor(public candidates: DuplicateCandidate[]) {
    super('Registration blocked by duplicate candidate(s)');
  }
}

export function searchPatients(query: string, accessToken: string): Promise<PatientSearchResult[]> {
  return authFetch(`/api/patients/search?q=${encodeURIComponent(query)}`, accessToken);
}

export function fetchPatientById(id: string, accessToken: string): Promise<PatientResponse> {
  return authFetch(`/api/patients/${id}`, accessToken);
}

/** A count only — deliberately no browsable list backs this (root CLAUDE.md's "no browse-all-patients" reasoning). */
export function fetchRegisteredPatientCount(from: string, to: string, accessToken: string): Promise<number> {
  return authFetch(`/api/patients/registered-count?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, accessToken);
}

export function fetchVisitHistory(patientId: string, accessToken: string): Promise<VisitHistoryResponse[]> {
  return authFetch(`/api/patients/${patientId}/visit-history`, accessToken);
}

/** Throws DuplicatePatientError (candidates attached) on a 409 duplicate block — 05-receptionist.md §4.1. */
export async function registerPatient(body: RegisterPatientRequest, accessToken: string): Promise<PatientResponse> {
  try {
    return await authFetch<PatientResponse>('/api/patients', accessToken, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  } catch (error) {
    if (error instanceof ApiError && error.status === 409 && Array.isArray(error.body?.candidates)) {
      throw new DuplicatePatientError(error.body.candidates as DuplicateCandidate[]);
    }
    throw error;
  }
}
