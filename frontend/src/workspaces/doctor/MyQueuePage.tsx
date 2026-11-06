import { PageHeader } from '../../shared/components/PageHeader';
import { useQuery } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router-dom';
import { fetchEncounterById, fetchEncountersByPatient, searchEncounters, type EncounterResponse } from '../../shared/api/encounters';
import { fetchPatientById } from '../../shared/api/patients';
import { fetchPatientAppointments } from '../../shared/api/appointments';
import { searchCompletedAssessments, type CompletedTriageAssessmentResponse, type TriagePriority } from '../../shared/api/triage';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { compareQueueRows, QueueShell, type QueueRow } from '../../shared/components/QueueShell';
import { formatWaitingSince, formatDateTime } from '../../shared/format/date';

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

  // Also fetches IN_CONSULT so a consultation the doctor already opened
  // (and hasn't finalized — a page reload, a logout mid-visit, an
  // autosaved draft) still has a visible resume point on the queue. Before
  // this, the encounter fell off the queue the instant it transitioned
  // AWAITING_CONSULT -> IN_CONSULT, and the only way back was the
  // dismissible IncompleteConsultationPrompt on the next fresh sign-in.
  const { data: encounters, isLoading } = useQuery({
    queryKey: ['encounters', 'search', { states: ['AWAITING_CONSULT', 'IN_CONSULT'] }],
    queryFn: () => searchEncounters({ states: ['AWAITING_CONSULT', 'IN_CONSULT'] }, accessToken as string),
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
  const inProgress = mine.filter((e) => e.state === 'IN_CONSULT');
  const waiting = mine.filter((e) => e.state === 'AWAITING_CONSULT');

  // In-progress rows carry no triage priority (they're already open) — sort
  // by createdAt oldest-first so the doctor sees the one they've had open
  // longest at the top.
  const inProgressRows: QueueRow[] = [...inProgress]
    .sort((a, b) => new Date(a.createdAt as string).getTime() - new Date(b.createdAt as string).getTime())
    .map((e) => toQueueRow(e, triageByEncounter.get(e.id as string)).row);

  const waitingRows: QueueRow[] = [...waiting]
    .map((e) => toQueueRow(e, triageByEncounter.get(e.id as string)))
    .sort((a, b) => compareQueueRows(a.rank, b.rank))
    .map((r) => r.row);

  return (
    <div>
      <PageHeader title="My queue" description="Continue consultations in progress or open the next waiting patient." />
      {location.state?.consultationError && (
        <div className="form-error" style={{ marginBottom: 'var(--space-3)' }}>
          {location.state.consultationError}
        </div>
      )}
      {inProgressRows.length > 0 && (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <QueueShell headingLevel={2}
            title="In progress"
            rows={inProgressRows}
            isLoading={false}
            emptyMessage=""
          />
        </div>
      )}
      <QueueShell headingLevel={2}
        title="Waiting"
        rows={waitingRows}
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
            {encounter.state === 'IN_CONSULT' ? 'Continue' : 'Open'}
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
  return <PatientLabel patientId={encounter?.patientId} currentEncounterId={encounterId} appointmentId={encounter?.appointmentId} />;
}

function PatientLabel({ patientId, currentEncounterId, appointmentId }: { patientId?: string; currentEncounterId: string; appointmentId?: string }) {
  const { accessToken } = useAuth();
  const { data: patient } = useQuery({
    queryKey: ['patients', patientId],
    queryFn: () => fetchPatientById(patientId as string, accessToken as string),
    enabled: accessToken != null && !!patientId,
    staleTime: 60_000,
  });
  const clinicHistory = useQuery({ queryKey: ['appointments', 'patient', patientId],
    queryFn: () => fetchPatientAppointments(patientId!, accessToken!), enabled: !!accessToken && !!patientId && !!appointmentId });
  const clinicAppointment = clinicHistory.data?.find(a => a.id === appointmentId && a.clinicFollowUpId);
  const priorClinicVisits = clinicHistory.data?.filter(a => a.clinicFollowUpId === clinicAppointment?.clinicFollowUpId && a.status === 'ATTENDED' && a.id !== appointmentId).length ?? 0;
  const history = useQuery({ queryKey: ['encounters', 'by-patient', patientId],
    queryFn: () => fetchEncountersByPatient(patientId!, accessToken as string), enabled: !!accessToken && !!patientId, staleTime: 60_000 });
  if (!patientId) return <span>Loading…</span>;
  const valid = (history.data ?? []).filter(e => e.state !== 'CANCELLED_IN_ERROR');
  const current = valid.find(e => e.id === currentEncounterId);
  const prior = valid.filter(e => e.id !== currentEncounterId && (!current?.createdAt || (e.createdAt ?? '') <= current.createdAt)).sort((a,b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));

  const age = patient?.dateOfBirth ? ageFromDob(patient.dateOfBirth) : null;
  return (
    <span>
      {patient?.fullName ?? 'Loading…'}
      {clinicAppointment && <span className="badge badge-info" style={{ marginLeft: 8 }}>Clinic Follow-Up · {clinicAppointment.clinic} · Scheduled {formatDateTime(clinicAppointment.scheduledAt)} · Arrived {formatDateTime(clinicAppointment.arrivedAt)} · {priorClinicVisits} prior clinic visits</span>}
      {age != null && ` · ${age}${patient?.gender?.[0] ?? ''}`}
      {patient?.hospitalNo && ` · ${patient.hospitalNo}`}
      {history.isLoading ? ' · Loading visits…' : history.isError ? ' · Visit history unavailable' :
        ` · ${prior.length === 0 ? 'New' : 'Returning'} · ${valid.length} visit${valid.length === 1 ? '' : 's'} · ${prior.length} previous · Last ${prior[0]?.createdAt ? new Intl.DateTimeFormat('en-TZ', { dateStyle: 'medium', timeZone: 'Africa/Dar_es_Salaam' }).format(new Date(prior[0].createdAt)) : 'none'}`}
    </span>
  );
}

function ageFromDob(dob: string): number {
  return Math.floor((Date.now() - new Date(dob).getTime()) / (365.25 * 24 * 60 * 60 * 1000));
}
