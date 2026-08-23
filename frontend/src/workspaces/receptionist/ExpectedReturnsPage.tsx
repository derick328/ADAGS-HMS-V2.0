import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { EncounterResponse } from '../../shared/api/encounters';
import { resumeEncounter, searchEncounters } from '../../shared/api/encounters';
import { fetchExpectedReturnAppointments, type AppointmentResponse } from '../../shared/api/appointments';
import { fetchPatientById } from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { formatDateTime } from '../../shared/format/date';

// 05a-receptionist-decisions.md §3: two genuinely different sources,
// visually distinguished because the receptionist's action differs.
// D10: read-only — no create/edit/reschedule/cancel from this workspace.
// The 48h rule is a highlight, not an expiry (§3.2): AWAITING_RESULTS rows
// stay listed until the encounter resolves, however long that takes.
export function ExpectedReturnsPage() {
  const canReadEncounters = usePermission('PATIENT_READ_VISIT_HISTORY');

  return (
    <div>
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>Expected Returns</h1>
      </header>

      {!canReadEncounters ? (
        <p style={{ color: 'var(--text-muted)' }}>You don't hold PATIENT_READ_VISIT_HISTORY.</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
          <AwaitingResultsSection />
          <FollowUpAppointmentsSection />
        </div>
      )}
    </div>
  );
}

function AwaitingResultsSection() {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const canResume = usePermission('ENCOUNTER_OPEN');

  const { data: encounters, isLoading } = useQuery({
    queryKey: ['expected-returns', 'awaiting-results'],
    queryFn: () => searchEncounters({ states: ['AWAITING_RESULTS'] }, accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 30_000,
  });

  const resume = useMutation({
    mutationFn: (id: string) => resumeEncounter(id, accessToken as string),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['expected-returns'] });
      queryClient.invalidateQueries({ queryKey: ['here-now'] });
    },
  });

  return (
    <section style={sectionStyle}>
      <h2 style={sectionTitleStyle}>Awaiting results</h2>
      <p style={{ padding: '0 var(--space-4)', margin: 0, color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
        Consultation unfinished, labs outstanding — no charge, no re-triage on return.
      </p>
      {isLoading ? (
        <p style={{ padding: 'var(--space-4)', color: 'var(--text-muted)' }}>Loading…</p>
      ) : !encounters || encounters.length === 0 ? (
        <p style={{ padding: 'var(--space-4)', color: 'var(--text-muted)' }}>No patients awaiting results.</p>
      ) : (
        <table className="table">
          <caption className="sr-only">Encounters awaiting results</caption>
          <thead>
            <tr>
              <th>Patient</th>
              <th>Since</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {encounters.map((e) => (
              <AwaitingResultsRow key={e.id} encounter={e} canResume={canResume} onResume={() => resume.mutate(e.id as string)} isResuming={resume.isPending} />
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function AwaitingResultsRow({
  encounter,
  canResume,
  onResume,
  isResuming,
}: {
  encounter: EncounterResponse;
  canResume: boolean;
  onResume: () => void;
  isResuming: boolean;
}) {
  const { accessToken } = useAuth();
  const { data: patient } = useQuery({
    queryKey: ['patient', encounter.patientId],
    queryFn: () => fetchPatientById(encounter.patientId as string, accessToken as string),
    enabled: accessToken != null,
    staleTime: 60_000,
  });

  const overdue = encounter.createdAt != null && Date.now() - new Date(encounter.createdAt).getTime() > 48 * 60 * 60 * 1000;

  return (
    <tr className={overdue ? 'row-warning' : ''}>
      <td style={{ fontWeight: 'var(--weight-medium)' }}>
        {patient?.fullName ?? '…'}
        {overdue && (
          <span className="badge badge-warning" style={{ marginLeft: 'var(--space-2)' }}>
            overdue
          </span>
        )}
      </td>
      <td style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>{formatDateTime(encounter.createdAt)}</td>
      <td style={{ width: 1, whiteSpace: 'nowrap' }}>
        {canResume && (
          <button type="button" className="btn btn-primary" disabled={isResuming} onClick={onResume}>
            Resume
          </button>
        )}
      </td>
    </tr>
  );
}

function FollowUpAppointmentsSection() {
  const { accessToken } = useAuth();

  const { data: appointments, isLoading } = useQuery({
    queryKey: ['expected-returns', 'follow-ups'],
    queryFn: () => fetchExpectedReturnAppointments(accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 30_000,
  });

  return (
    <section style={sectionStyle}>
      <h2 style={sectionTitleStyle}>Follow-up appointments</h2>
      <p style={{ padding: '0 var(--space-4)', margin: 0, color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
        Doctor-booked reviews — checking in opens a new encounter and routes through triage. Check in through Patient Intake.
      </p>
      {isLoading ? (
        <p style={{ padding: 'var(--space-4)', color: 'var(--text-muted)' }}>Loading…</p>
      ) : !appointments || appointments.length === 0 ? (
        <p style={{ padding: 'var(--space-4)', color: 'var(--text-muted)' }}>No follow-ups due.</p>
      ) : (
        <table className="table">
          <caption className="sr-only">Follow-up appointments</caption>
          <thead>
            <tr>
              <th>Patient</th>
              <th>Due</th>
              <th>Notes</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {appointments.map((a) => (
              <FollowUpRow key={a.id} appointment={a} />
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function FollowUpRow({ appointment }: { appointment: AppointmentResponse }) {
  const { accessToken } = useAuth();
  const { data: patient } = useQuery({
    queryKey: ['patient', appointment.patientId],
    queryFn: () => fetchPatientById(appointment.patientId as string, accessToken as string),
    enabled: accessToken != null,
    staleTime: 60_000,
  });

  const overdue = appointment.scheduledAt != null && new Date(appointment.scheduledAt).getTime() < Date.now();

  return (
    <tr className={overdue ? 'row-warning' : ''}>
      <td style={{ fontWeight: 'var(--weight-medium)' }}>{patient?.fullName ?? '…'}</td>
      <td style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>{formatDateTime(appointment.scheduledAt)}</td>
      <td style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>{appointment.notes || '—'}</td>
      <td>
        {overdue ? <span className="badge badge-warning">due</span> : <span className="badge badge-info">upcoming</span>}
      </td>
    </tr>
  );
}

const sectionStyle = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
  borderRadius: 'var(--radius-lg)',
  overflow: 'hidden',
} as const;

const sectionTitleStyle = {
  fontSize: 'var(--text-lg)',
  fontWeight: 'var(--weight-medium)',
  margin: 0,
  padding: 'var(--space-4) var(--space-4) 0',
} as const;
