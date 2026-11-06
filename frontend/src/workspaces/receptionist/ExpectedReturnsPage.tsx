import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { EncounterResponse } from '../../shared/api/encounters';
import { resumeEncounter, searchEncounters } from '../../shared/api/encounters';
import { fetchExpectedReturnAppointments, markClinicNoShow, clinicArrivalLabel, type ClinicAppointment } from '../../shared/api/appointments';
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
    <div className="staff-page-stack expected-returns-page">
      <PageHeader title={<>Expected returns</>} description="Review patients awaiting results and scheduled clinic follow-ups." />

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
    <section className="staff-data-section">
      <h2>Awaiting results</h2>
      <p style={{ padding: '0 var(--space-4)', margin: 0, color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
        Consultation unfinished, labs outstanding — no charge, no re-triage on return.
      </p>
      {isLoading ? (
        <FeedbackState loading>Loading…</FeedbackState>
      ) : !encounters || encounters.length === 0 ? (
        <FeedbackState>No patients awaiting results.</FeedbackState>
      ) : (
        <TableRegion label="Encounters awaiting results"><table className="table">
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
        </table></TableRegion>
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
    <section className="staff-data-section">
      <h2>Follow-up appointments</h2>
      <p style={{ padding: '0 var(--space-4)', margin: 0, color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
        Doctor-booked reviews — checking in opens a new encounter and routes through triage. Check in through Patient Intake.
      </p>
      {isLoading ? (
        <FeedbackState loading>Loading…</FeedbackState>
      ) : !appointments || appointments.length === 0 ? (
        <FeedbackState>No follow-ups due.</FeedbackState>
      ) : (
        <TableRegion label="Follow-up appointments"><table className="table">
          <caption className="sr-only">Follow-up appointments</caption>
          <thead>
            <tr>
              <th>Patient</th>
              <th>Hospital no.</th>
              <th>Clinic / assigned doctor</th>
              <th>Scheduled return</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {appointments.map((a) => (
              <FollowUpRow key={a.id} appointment={a} />
            ))}
          </tbody>
        </table></TableRegion>
      )}
    </section>
  );
}

function FollowUpRow({ appointment }: { appointment: ClinicAppointment }) {
  const { accessToken } = useAuth();
  const { data: patient } = useQuery({
    queryKey: ['patient', appointment.patientId],
    queryFn: () => fetchPatientById(appointment.patientId as string, accessToken as string),
    enabled: accessToken != null,
    staleTime: 60_000,
  });

  const arrival = clinicArrivalLabel(appointment.scheduledAt);
  const queryClient = useQueryClient();
  const canManage = usePermission('APPOINTMENT_CANCEL');
  const noShow = useMutation({ mutationFn: () => markClinicNoShow(appointment.id!, accessToken as string),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['expected-returns'] }); queryClient.invalidateQueries({ queryKey: ['appointments'] }); } });

  return (
    <tr className={arrival === 'Overdue' ? 'row-warning' : ''}>
      <td style={{ fontWeight: 'var(--weight-medium)' }}>{patient?.fullName ?? '…'}</td>
      <td>{patient?.hospitalNo ?? '—'}</td>
      <td>{appointment.clinic ?? 'General follow-up'} · {appointment.doctor ?? '—'}</td>
      <td style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>{formatDateTime(appointment.scheduledAt)}</td>
      <td><span className={arrival === 'Overdue' ? 'badge badge-warning' : 'badge badge-info'}>{appointment.status} · {arrival}</span></td>
      <td>{canManage && appointment.clinicFollowUpId && arrival === 'Overdue' && <button type="button" className="btn btn-ghost" disabled={noShow.isPending} onClick={() => noShow.mutate()}>Mark no-show</button>}
        {noShow.error && <span className="form-error">Could not update appointment.</span>}</td>
    </tr>
  );
}
