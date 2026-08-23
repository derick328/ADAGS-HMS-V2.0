import { authFetch } from './client';
import type { components } from './types';

export type TriagePriority = components['schemas']['TriageAssessmentDraftRequest']['priority'];
export type ModeOfAmbulation = components['schemas']['TriageAssessmentDraftRequest']['modeOfAmbulation'];
export type ImmediateReason = NonNullable<components['schemas']['SubmitTriageAssessmentRequest']['immediateReason']>;
export type PregnancyStatus = components['schemas']['SubmitTriageAssessmentRequest']['pregnancyStatus'];

// springdoc has no useful schema for a raw `Map<String, Object>` (formState
// is deliberately opaque JSONB server-side, V60) — openapi-typescript
// collapses it to `Record<string, never>`, unusable for an actual draft
// payload. Every autosave/draft type below overrides that one field with a
// real index signature instead of trusting the generated shape.
export type TriageFormState = Record<string, unknown>;

export type TriageAssessmentDraftRequest =
  Omit<components['schemas']['TriageAssessmentDraftRequest'], 'formState'> & { formState: TriageFormState };
export type TriageAssessmentDraftResponse =
  Omit<components['schemas']['TriageAssessmentDraftResponse'], 'formState'> & { formState: TriageFormState };
export type TriageAssessmentDraftSummaryResponse = components['schemas']['TriageAssessmentDraftSummaryResponse'];

export type SubmitTriageAssessmentRequest = components['schemas']['SubmitTriageAssessmentRequest'];
export type TriageAssessmentResponse = components['schemas']['TriageAssessmentResponse'];

export type ReferralCandidateResponse = components['schemas']['ReferralCandidateResponse'];
export type VitalReferenceRangeResponse = components['schemas']['VitalReferenceRangeResponse'];
export type CompletedTriageAssessmentResponse = components['schemas']['CompletedTriageAssessmentResponse'];

/** Own draft only, or null when nothing has been autosaved yet for this encounter. */
export function fetchOwnTriageDraft(encounterId: string, accessToken: string): Promise<TriageAssessmentDraftResponse | null> {
  return authFetch(`/api/encounters/${encounterId}/triage-draft`, accessToken);
}

/** Upsert keyed server-side to (encounterId, the caller's own userId) — 06-triage.md §1/§3's 20s autosave. */
export function saveTriageDraft(
  encounterId: string,
  body: TriageAssessmentDraftRequest,
  accessToken: string,
): Promise<TriageAssessmentDraftResponse> {
  return authFetch(`/api/encounters/${encounterId}/triage-draft`, accessToken, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

/** The queue's "being assessed by" advisory line — most-recent draft per encounter, across every author. */
export function fetchActiveDraftSummaries(
  encounterIds: string[],
  accessToken: string,
): Promise<TriageAssessmentDraftSummaryResponse[]> {
  if (encounterIds.length === 0) return Promise.resolve([]);
  const query = new URLSearchParams();
  encounterIds.forEach((id) => query.append('encounterIds', id));
  return authFetch(`/api/triage-assessment-drafts/active-summaries?${query.toString()}`, accessToken);
}

/** §5.1's referral picker — fee and queue depth are real, onDuty is always null (not modelled yet, never guessed). */
export function fetchReferralCandidates(encounterId: string, accessToken: string): Promise<ReferralCandidateResponse[]> {
  return authFetch(`/api/encounters/${encounterId}/referral-candidates`, accessToken);
}

/** §2.2's implausible/abnormal bounds, resolved server-side for this patient's current age band and sex. */
export function fetchVitalReferenceRanges(patientId: string, accessToken: string): Promise<VitalReferenceRangeResponse[]> {
  return authFetch(`/api/clinical/vital-reference-ranges?patientId=${encodeURIComponent(patientId)}`, accessToken);
}

/** §6's one-transaction submit. */
export function submitTriageAssessment(
  encounterId: string,
  body: SubmitTriageAssessmentRequest,
  accessToken: string,
): Promise<TriageAssessmentResponse> {
  return authFetch(`/api/encounters/${encounterId}/triage-assessments`, accessToken, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export interface SearchCompletedAssessmentsParams {
  /** true = only the caller's own rows ("mine today"); omitted = every author's ("the whole shift") — §7.12 resolved as one endpoint, two callers. */
  ownOnly?: boolean;
  assessedFrom: string;
  assessedTo: string;
}

/** Never includes a superseded row — the list always shows each assessment's current version. */
export function searchCompletedAssessments(
  params: SearchCompletedAssessmentsParams,
  accessToken: string,
): Promise<CompletedTriageAssessmentResponse[]> {
  const query = new URLSearchParams();
  if (params.ownOnly) query.set('ownOnly', 'true');
  query.set('assessedFrom', params.assessedFrom);
  query.set('assessedTo', params.assessedTo);
  return authFetch(`/api/triage-assessments?${query.toString()}`, accessToken);
}
