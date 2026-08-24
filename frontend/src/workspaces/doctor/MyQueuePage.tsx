import { useQuery } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router-dom';
import { fetchEncounterById, searchEncounters, type EncounterResponse } from '../../shared/api/encounters';
import { fetchPatientById } from '../../shared/api/patients';
import { searchCompletedAssessments, type CompletedTriageAssessmentResponse, type TriagePriority } from '../../shared/api/triage';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { compareQueueRows, QueueShell, type QueueRow } from '../../shared/components/QueueShell';
import { formatWaitingSince } from '../../shared/format/date';

const PRIORITY_RANK: Record<NonNullable<TriagePriority>, number> = { IMMEDIATE: 0, URGENT: 1, LESS_URGENT: 2, NON_URGENT: 3 };

// 07-doctor.md §2 / 08-doctor-navigation.md §2:
//   ORDER BY (type == EMERGENCY) DESC, triage_priority ASC, gate_satisfied_at ASC
// gate_satisfied_at has no dedicated column anywhere in this schema yet
// (billing gate authorization is a boolean-ish state on the invoice line,
// not a timestamped event exposed to this screen) — createdAt is the
// closest real proxy and is what's used for the tiebreak, not a fabricated
// field. IMMEDIATE always sorts first via isEmergency (SubmitTriageAssessmentService
// sets encounter.type=EMERGENCY exactly when triage priority=IMMEDIATE).
export function MyQueuePage() {
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  const navigate = useNavigate();
  const location = useLocation() as { state?: { consultationError?: string } };

  const { data: encounters, isLoading } = useQuery({
    queryKey: ['encounters', 'search', { states: ['AWAITING_CONSULT'] }],
    queryFn: () => searchEncounters({ states: ['AWAITING_CONSULT'] }, accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 10_000,
  });

  const { data: triageWindow } = useQuery({
    queryKey: ['triage-assessments', 'window'],
    queryFn: () =>
      searchCompletedAssessments(
        { assessedFrom: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(), assessedTo: new Date().toISOString() },
        accessToken as string,
      ),
    enabled: accessToken != null,
    refetchInterval: 10_000,
  });
  const triageByEncounter = new Map((triageWindow ?? []).map((t) => [t.encounterId, t]));

  const mine = (encounters ?? []).filter((e) => e.assignedProviderId === me?.userId);

  const rows: QueueRow[] = [...mine]
    .map((e) => toQueueRow(e, triageByEncounter.get(e.id as string)))
    .sort((a, b) => compareQueueRows(a.rank, b.rank))
    .map((r) => r.row);

  return (
    <div>
      {location.state?.consultationError && (
        <div className="form-error" style={{ marginBottom: 'var(--space-3)' }}>
          {location.state.consultationError}
        </div>
      )}
      <QueueShell
        title="My Queue"
        rows={rows}
        isLoading={isLoading}
        emptyMessage="No patients waiting for consultation."
      />
    </div>
  );

  function toQueueRow(
    encounter: EncounterResponse,
    triage: CompletedTriageAssessmentResponse | undefined,
  ): { row: QueueRow; rank: { isEmergency: boolean; severityRank: number; tiebreak: number } } {
    const isEmergency = encounter.type === 'EMERGENCY';
    const priority = triage?.priority;
    return {
      rank: {
        isEmergency,
        severityRank: priority ? PRIORITY_RANK[priority] : 99,
        tiebreak: new Date(encounter.createdAt as string).getTime(),
      },
      row: {
        id: encounter.id as string,
        isEmergency,
        severity: !isEmergency && priority === 'URGENT' ? 'warning' : 'normal',
        severityLabel: priority,
        primary: <EncounterIdentity encounterId={encounter.id as string} />,
        secondary: triage?.chiefComplaint,
        meta: formatWaitingSince(encounter.createdAt),
        action: (
          <button type="button" className="btn btn-primary" onClick={() => navigate(`/doctor/consultation/${encounter.id}`)}>
            Open
          </button>
        ),
      },
    };
  }
}

// Kept as its own tiny component (rather than a prop threaded through) so
// it can carry its own cached fetch — the queue page itself only knows
// patientId indirectly via a second round trip, same "per-row subquery"
// pattern triage's PatientQueuePage already established.
function EncounterIdentity({ encounterId }: { encounterId: string }) {
  const { accessToken } = useAuth();
  const { data: encounter } = useQuery({
    queryKey: ['encounters', encounterId],
    queryFn: () => fetchEncounterById(encounterId, accessToken as string),
    enabled: accessToken != null,
    staleTime: 60_000,
  });
  return <PatientLabel patientId={encounter?.patientId} />;
}

function PatientLabel({ patientId }: { patientId?: string }) {
  const { accessToken } = useAuth();
  const { data: patient } = useQuery({
    queryKey: ['patients', patientId],
    queryFn: () => fetchPatientById(patientId as string, accessToken as string),
    enabled: accessToken != null && !!patientId,
    staleTime: 60_000,
  });
  if (!patientId) return <span>Loading…</span>;
  const age = patient?.dateOfBirth ? ageFromDob(patient.dateOfBirth) : null;
  return (
    <span>
      {patient?.fullName ?? 'Loading…'}
      {age != null && ` · ${age}${patient?.gender?.[0] ?? ''}`}
      {patient?.hospitalNo && ` · ${patient.hospitalNo}`}
    </span>
  );
}

function ageFromDob(dob: string): number {
  return Math.floor((Date.now() - new Date(dob).getTime()) / (365.25 * 24 * 60 * 60 * 1000));
}
