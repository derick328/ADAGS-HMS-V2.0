import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { compareQueueRows, QueueShell, type QueueRow } from '../../shared/components/QueueShell';
import type { EncounterResponse } from '../../shared/api/encounters';
import { searchEncounters } from '../../shared/api/encounters';
import { fetchPatientById, fetchVisitHistory } from '../../shared/api/patients';
import { fetchActiveDraftSummaries, type TriageAssessmentDraftSummaryResponse } from '../../shared/api/triage';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import { formatDate, formatWaitingSince } from '../../shared/format/date';

// §2.8's declaration order (IMMEDIATE, URGENT, LESS_URGENT, NON_URGENT) is
// the queue sort rank — same ordinal trick TriagePriority.java's own
// comment describes server-side, mirrored here rather than re-derived.
const PRIORITY_RANK: Record<string, number> = { IMMEDIATE: 0, URGENT: 1, LESS_URGENT: 2, NON_URGENT: 3 };

// 06-triage.md §1/§3 — Screen 1, "Patient Queue (dashboard)": every
// AWAITING_TRIAGE encounter, sorted emergency-first then by arrival, never
// FIFO.
//
// §3's "being assessed by N. Kimaro" advisory line and §1's arrival-mode
// column now both come from the same source: the most recently updated
// triage-assessment draft per encounter (encounters/api/TriageAssessmentDraftController,
// GET .../active-summaries) — an encounter with no draft yet shows neither,
// honestly, rather than a placeholder. A draft's priority (set the instant
// a danger sign is ticked, before the form is finished — §4's "one action
// reprioritises immediately") also drives the live re-sort: it stands in
// for compareQueueRows' isEmergency/severityRank exactly the way a real
// TriagePriority would once one exists.
//
// §3's live re-sort is over a WebSocket; nothing in this codebase runs one
// yet (EmergencyBillingBar polls for the same reason) — polling every 10s
// here is the same interim substitute, not a redesign.
export function PatientQueuePage() {
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  const canRead = usePermission('TRIAGE_QUEUE_READ');
  const canAssess = usePermission('TRIAGE_ASSESS');

  const { data: encounters, isLoading } = useQuery({
    queryKey: ['triage-queue', 'awaiting-triage'],
    queryFn: () => searchEncounters({ states: ['AWAITING_TRIAGE'] }, accessToken as string),
    enabled: canRead && accessToken != null,
    refetchInterval: 10_000,
  });

  const encounterIds = useMemo(() => (encounters ?? []).map((e) => e.id as string), [encounters]);

  const { data: draftSummaries } = useQuery({
    queryKey: ['triage-queue', 'active-draft-summaries', encounterIds],
    queryFn: () => fetchActiveDraftSummaries(encounterIds, accessToken as string),
    enabled: canRead && accessToken != null && encounterIds.length > 0,
    refetchInterval: 10_000,
  });

  const draftByEncounterId = useMemo(() => {
    const map = new Map<string, TriageAssessmentDraftSummaryResponse>();
    (draftSummaries ?? []).forEach((s) => map.set(s.encounterId as string, s));
    return map;
  }, [draftSummaries]);

  const rows = useMemo<QueueRow[]>(() => {
    if (!encounters) return [];
    return [...encounters]
      .sort((a, b) => {
        const draftA = draftByEncounterId.get(a.id as string);
        const draftB = draftByEncounterId.get(b.id as string);
        return compareQueueRows(
          {
            isEmergency: a.type === 'EMERGENCY' || draftA?.priority === 'IMMEDIATE',
            severityRank: draftA?.priority ? PRIORITY_RANK[draftA.priority] : undefined,
            tiebreak: safeTimestamp(a.createdAt),
          },
          {
            isEmergency: b.type === 'EMERGENCY' || draftB?.priority === 'IMMEDIATE',
            severityRank: draftB?.priority ? PRIORITY_RANK[draftB.priority] : undefined,
            tiebreak: safeTimestamp(b.createdAt),
          },
        );
      })
      .map((encounter) =>
        toQueueRow(encounter, canAssess, draftByEncounterId.get(encounter.id as string), me?.userId),
      );
  }, [encounters, canAssess, draftByEncounterId, me?.userId]);

  if (!canRead) {
    return <p style={{ color: 'var(--text-muted)' }}>You don't hold TRIAGE_QUEUE_READ.</p>;
  }

  return (
    <QueueShell
      title="Patient Queue"
      rows={rows}
      emptyMessage="No patients waiting for triage."
      isLoading={isLoading}
    />
  );
}

function toQueueRow(
  encounter: EncounterResponse,
  canAssess: boolean,
  draft: TriageAssessmentDraftSummaryResponse | undefined,
  currentUserId: string | undefined,
): QueueRow {
  const patientId = encounter.patientId as string;
  const encounterId = encounter.id as string;
  const isEmergency = encounter.type === 'EMERGENCY' || draft?.priority === 'IMMEDIATE';
  const isOwnDraft = draft != null && draft.authorId === currentUserId;

  return {
    id: encounterId,
    isEmergency,
    severityLabel: isEmergency ? 'Emergency' : draft?.priority,
    primary: <PatientPrimaryLine patientId={patientId} />,
    secondary: <PatientSecondaryLine patientId={patientId} encounterId={encounterId} draft={draft} />,
    meta: formatWaitingSince(encounter.createdAt) + ' waiting',
    action: canAssess ? (
      <Link to={`/triage/assess/${encounterId}`} className="btn btn-primary">
        {isOwnDraft ? 'Resume assessment' : 'Begin assessment'}
      </Link>
    ) : undefined,
  };
}

function PatientPrimaryLine({ patientId }: { patientId: string }) {
  const { accessToken } = useAuth();
  const { data: patient } = useQuery({
    queryKey: ['patient', patientId],
    queryFn: () => fetchPatientById(patientId, accessToken as string),
    enabled: accessToken != null,
    staleTime: 60_000,
  });

  return (
    <>
      {patient?.fullName ?? '…'}
      {patient?.hospitalNo && (
        <span style={{ color: 'var(--text-muted)', fontWeight: 'var(--weight-normal)' }}> · {patient.hospitalNo}</span>
      )}
    </>
  );
}

function PatientSecondaryLine({
  patientId,
  encounterId,
  draft,
}: {
  patientId: string;
  encounterId: string;
  draft: TriageAssessmentDraftSummaryResponse | undefined;
}) {
  const { accessToken } = useAuth();
  const { data: patient } = useQuery({
    queryKey: ['patient', patientId],
    queryFn: () => fetchPatientById(patientId, accessToken as string),
    enabled: accessToken != null,
    staleTime: 60_000,
  });

  return (
    <>
      {ageSexLabel(patient?.dateOfBirth, patient?.gender)}
      {/* Arrival mode: real once a nurse's draft has captured it (draft.modeOfAmbulation,
          §2.1/§2.4), never fabricated for a patient nobody has started assessing yet. */}
      {draft?.modeOfAmbulation && (
        <span style={{ marginLeft: 'var(--space-2)' }}>· {arrivalModeLabel(draft.modeOfAmbulation)}</span>
      )}
      {/* §3: "Being assessed by N. Kimaro" — advisory only, never a lock;
          a second nurse can still open the same patient (the "Begin
          assessment" action above is never disabled by this). */}
      {draft && (
        <span className="badge badge-neutral" style={{ marginLeft: 'var(--space-2)' }}>
          Being assessed by {draft.authorUsername}
        </span>
      )}
      <PriorVisitChip patientId={patientId} currentEncounterId={encounterId} />
    </>
  );
}

// "Last seen 12 Jul · Dr Mushi" — §1's prior-visit chip. Rendered only
// "where applicable" (spec's own wording): most recent OTHER encounter for
// this patient, or nothing at all for a first visit.
function PriorVisitChip({ patientId, currentEncounterId }: { patientId: string; currentEncounterId: string }) {
  const { accessToken } = useAuth();
  const { data: visits } = useQuery({
    queryKey: ['visit-history', patientId],
    queryFn: () => fetchVisitHistory(patientId, accessToken as string),
    enabled: accessToken != null,
    staleTime: 60_000,
  });

  const priorVisit = (visits ?? [])
    .filter((v) => v.encounterId !== currentEncounterId)
    .sort((a, b) => safeTimestamp(b.encounterDate) - safeTimestamp(a.encounterDate))[0];

  if (!priorVisit) {
    return null;
  }

  return (
    <span className="badge badge-info" style={{ marginLeft: 'var(--space-2)' }}>
      Last seen {formatDate(priorVisit.encounterDate)}
      {priorVisit.attendingProviderName ? ` · ${priorVisit.attendingProviderName}` : ''}
    </span>
  );
}

function ageSexLabel(dateOfBirth: string | undefined, gender: string | undefined): string {
  const age = dateOfBirth
    ? Math.floor((Date.now() - new Date(dateOfBirth).getTime()) / (365.25 * 24 * 60 * 60 * 1000))
    : undefined;
  const sex = gender === 'MALE' ? 'M' : gender === 'FEMALE' ? 'F' : gender?.replaceAll('_', ' ').toLowerCase();
  if (age == null && !sex) return '—';
  if (age == null) return sex ?? '—';
  if (!sex) return `${age}`;
  return `${age} · ${sex}`;
}

function arrivalModeLabel(mode: string): string {
  return mode.replaceAll('_', ' ').toLowerCase();
}

function safeTimestamp(value: string | undefined | null): number {
  return value ? new Date(value).getTime() : 0;
}
