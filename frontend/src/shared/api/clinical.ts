import { authFetch } from './client';
import type { components } from './types';

export type ConsultationRecordResponse = components['schemas']['ConsultationRecordResponse'];
export type DiagnosisResponse = components['schemas']['DiagnosisResponse'];
export type DiagnosisCertainty = NonNullable<DiagnosisResponse['certainty']>;
export type AllergyRecordResponse = components['schemas']['AllergyRecordResponse'];
export type AllergyConfirmationStatus = NonNullable<AllergyRecordResponse['confirmationStatus']>;
export type OpenConsultationResponse = components['schemas']['OpenConsultationResponse'];
export type FinalizeConsultationRequest = components['schemas']['FinalizeConsultationRequest'];
export type DispositionOutcome = FinalizeConsultationRequest['disposition'];
export type EncounterResponse = components['schemas']['EncounterResponse'];
export type AutosaveConsultationRecordRequest = components['schemas']['AutosaveConsultationRecordRequest'];
export type AddDiagnosisRequest = components['schemas']['AddDiagnosisRequest'];
export type CreateAllergyRequest = components['schemas']['CreateAllergyRequest'];
export type ReconcileAllergyRequest = components['schemas']['ReconcileAllergyRequest'];

// 07-doctor.md §2's entry guard: opens (AWAITING_CONSULT -> IN_CONSULT) or
// resumes (already IN_CONSULT) this doctor's consultation record. Throws
// ApiError with a 409 body when the guard fails — the caller bounces back
// to the queue with the message.
export function openConsultation(encounterId: string, accessToken: string): Promise<OpenConsultationResponse> {
  return authFetch(`/api/encounters/${encounterId}/consultation/open`, accessToken, { method: 'POST' });
}

export function finalizeConsultation(
  encounterId: string,
  body: FinalizeConsultationRequest,
  accessToken: string,
): Promise<EncounterResponse> {
  return authFetch(`/api/encounters/${encounterId}/consultation/finalize`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/** §8.2: the doctor's own "waiting on results" action — a separate step from finalize. */
export function markAwaitingResults(encounterId: string, accessToken: string): Promise<EncounterResponse> {
  return authFetch(`/api/encounters/${encounterId}/consultation/mark-awaiting-results`, accessToken, { method: 'POST' });
}

/** §10: autosaved server-side every 20s, keyed to this consultation record. */
export function autosaveConsultationRecord(
  consultationRecordId: string,
  body: AutosaveConsultationRecordRequest,
  accessToken: string,
): Promise<ConsultationRecordResponse> {
  return authFetch(`/api/consultation-records/${consultationRecordId}/autosave`, accessToken, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

/** 08-doctor-navigation.md §3.3: every author's record for the encounter — "other clinicians' notes visible." */
export function fetchConsultationRecordsByEncounter(encounterId: string, accessToken: string): Promise<ConsultationRecordResponse[]> {
  return authFetch(`/api/encounters/${encounterId}/consultation-records`, accessToken);
}

export function addDiagnosis(
  consultationRecordId: string,
  body: AddDiagnosisRequest,
  accessToken: string,
): Promise<DiagnosisResponse> {
  return authFetch(`/api/consultation-records/${consultationRecordId}/diagnoses`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function fetchDiagnosesByEncounter(encounterId: string, accessToken: string): Promise<DiagnosisResponse[]> {
  return authFetch(`/api/encounters/${encounterId}/diagnoses`, accessToken);
}

/** 09-doctor-incomplete-consultations.md §3: own backlog only, never another doctor's. */
export function fetchIncompleteConsultations(accessToken: string): Promise<ConsultationRecordResponse[]> {
  return authFetch('/api/consultation-records/incomplete', accessToken);
}

export function fetchAllergiesByPatient(patientId: string, accessToken: string): Promise<AllergyRecordResponse[]> {
  return authFetch(`/api/patients/${patientId}/allergies`, accessToken);
}

export function createAllergyRecord(
  patientId: string,
  body: CreateAllergyRequest,
  accessToken: string,
): Promise<AllergyRecordResponse> {
  return authFetch(`/api/patients/${patientId}/allergies`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/** §6.1: documentary only — never filters what a prescribing/dispensing screen shows. */
export function reconcileAllergyRecord(
  allergyRecordId: string,
  body: ReconcileAllergyRequest,
  accessToken: string,
): Promise<AllergyRecordResponse> {
  return authFetch(`/api/allergy-records/${allergyRecordId}/reconcile`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
