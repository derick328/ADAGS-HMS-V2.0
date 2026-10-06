import { PageHeader } from '../../shared/components/PageHeader';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { fetchAllergiesByPatient, fetchConsultationRecordsByEncounter, fetchDiagnosesByEncounter } from '../../shared/api/clinical';
import { fetchEncountersByPatient, type EncounterResponse } from '../../shared/api/encounters';
import { fetchPatientById } from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDate } from '../../shared/format/date';

// 08-doctor-navigation.md §3. The Patient page's brief: patient identity +
// clinical alerts (allergies — root CLAUDE.md "Safety": inline,
// unmissable) + a scannable list of encounters. Everything clinically
// detailed lives on EncounterDetailPage; the cards here stay to one HPI
// line so the doctor picks by date/state, not by re-reading the record.
export function PatientPage() {
  const { patientId } = useParams<{ patientId: string }>();
  const { accessToken } = useAuth();
  const navigate = useNavigate();

  const { data: patient, isLoading: patientLoading } = useQuery({
    queryKey: ['patients', patientId],
    queryFn: () => fetchPatientById(patientId as string, accessToken as string),
    enabled: accessToken != null && !!patientId,
  });
  const { data: allergies } = useQuery({
    queryKey: ['allergies', patientId],
    queryFn: () => fetchAllergiesByPatient(patientId as string, accessToken as string),
    enabled: accessToken != null && !!patientId,
  });
  const { data: encounters, isLoading: encountersLoading } = useQuery({
    queryKey: ['encounters', 'by-patient', patientId],
    queryFn: () => fetchEncountersByPatient(patientId as string, accessToken as string),
    enabled: accessToken != null && !!patientId,
  });

  const sortedEncounters = [...(encounters ?? [])].sort(
    (a, b) => new Date(b.createdAt as string).getTime() - new Date(a.createdAt as string).getTime(),
  );

  return (
    <div>
      <button
        type="button"
        className="btn btn-ghost"
        onClick={() => navigate('/doctor/patients')}
        style={{ marginBottom: 'var(--space-3)' }}
      >
        ← Back to My Patients
      </button>

      {patientLoading ? (
        <FeedbackState loading>Loading patient…</FeedbackState>
      ) : !patient ? (
        <p className="form-error">Could not load this patient.</p>
      ) : (
        <>
          <PatientHeader
            fullName={patient.fullName}
            hospitalNo={patient.hospitalNo}
            gender={patient.gender}
            dateOfBirth={patient.dateOfBirth}
          />
          <AllergyStrip allergies={allergies ?? []} />
        </>
      )}

      <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', margin: 'var(--space-4) 0 var(--space-3)' }}>
        Encounters
      </h2>

      {encountersLoading ? (
        <FeedbackState loading>Loading encounters…</FeedbackState>
      ) : sortedEncounters.length === 0 ? (
        <FeedbackState>No encounters on record.</FeedbackState>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          {sortedEncounters.map((e) => (
            <EncounterCard key={e.id} encounter={e} patientId={patientId as string} />
          ))}
        </div>
      )}
    </div>
  );
}

function PatientHeader({
  fullName,
  hospitalNo,
  gender,
  dateOfBirth,
}: {
  fullName?: string;
  hospitalNo?: string;
  gender?: string;
  dateOfBirth?: string;
}) {
  const age = dateOfBirth ? ageFromDob(dateOfBirth) : null;
  return (
    <PageHeader title={fullName ?? '—'} description={<>
      Patient no: {hospitalNo ?? '—'}
      {gender && ` · ${genderLabel(gender)}`}
      {age != null && ` · ${age} y`}
      {dateOfBirth && ` (DOB ${formatDate(dateOfBirth)})`}
    </>} />
  );
}

// Root CLAUDE.md "Safety": allergies inline and unmissable. Warning-styled
// when present, muted "No known allergies" when not — never collapsed
// behind a tab or button.
function AllergyStrip({ allergies }: { allergies: { description?: string; severity?: string; confirmationStatus?: string; recordedAt?: string }[] }) {
  if (allergies.length === 0) {
    return (
      <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)', padding: 'var(--space-2) 0' }}>
        No known allergies recorded.
      </div>
    );
  }
  return (
    <div
      role="alert"
      style={{
        background: 'var(--critical-bg)',
        color: 'var(--critical)',
        borderRadius: 'var(--radius)',
        padding: 'var(--space-2) var(--space-3)',
        fontSize: 'var(--text-sm)',
        marginTop: 'var(--space-2)',
      }}
    >
      <strong>⚠ Allergies:</strong>{' '}
      {allergies
        .map((a) => `${a.description ?? '—'}${a.severity ? ` (${a.severity.toLowerCase()})` : ''}${a.confirmationStatus ? ` [${a.confirmationStatus.toLowerCase()}]` : ''}`)
        .join(' · ')}
    </div>
  );
}

// Cards stay concise on purpose (§1 of the redesign brief). One HPI line
// for open encounters, one primary diagnosis for closed ones — enough for
// the doctor to pick which encounter they want, no more.
function EncounterCard({ encounter, patientId }: { encounter: EncounterResponse; patientId: string }) {
  const { accessToken } = useAuth();
  const navigate = useNavigate();

  // Only the small summary lookup — the detail view fetches the full record.
  const { data: notes } = useQuery({
    queryKey: ['consultation-records', 'by-encounter', encounter.id, 'card-summary'],
    queryFn: () => fetchConsultationRecordsByEncounter(encounter.id as string, accessToken as string),
    enabled: accessToken != null,
    staleTime: 60_000,
  });
  const { data: diagnoses } = useQuery({
    queryKey: ['diagnoses', encounter.id, 'card-summary'],
    queryFn: () => fetchDiagnosesByEncounter(encounter.id as string, accessToken as string),
    enabled: accessToken != null,
    staleTime: 60_000,
  });

  const isTerminal = ['CLOSED', 'DISCHARGED', 'CONSULT_COMPLETE', 'REFERRED_OUT', 'DECEASED', 'ABANDONED_UNPAID', 'LEFT_WITHOUT_BEING_SEEN', 'CANCELLED_IN_ERROR'].includes(
    (encounter.state ?? '') as string,
  );
  const primary = (diagnoses ?? []).find((d) => d.isPrimary);
  const openNote = (notes ?? []).find((n) => n.status !== 'ABANDONED');

  return (
    <article
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-3) var(--space-4)',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-2)',
      }}
    >
      <header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-2)' }}>
        <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
          {formatDate(encounter.createdAt)} · {encounter.type ?? '—'}
        </div>
        <EncounterStateBadge state={encounter.state} />
      </header>

      <div style={{ fontSize: 'var(--text-sm)' }}>
        {isTerminal ? (
          <>
            <div style={{ fontWeight: 'var(--weight-medium)' }}>Consultation completed</div>
            <div style={{ color: 'var(--text-secondary)' }}>
              Diagnosis: {primary ? `${primary.icd10Code ?? ''} ${primary.icd10TitleSnapshot ?? ''}`.trim() || '—' : 'Not recorded'}
            </div>
          </>
        ) : openNote ? (
          <>
            <div style={{ fontWeight: 'var(--weight-medium)' }}>Consultation</div>
            <div style={{ color: 'var(--text-secondary)' }}>HPI: {truncate(openNote.historyOfPresentingIllness, 90) || '—'}</div>
          </>
        ) : (
          <div style={{ color: 'var(--text-muted)' }}>No consultation note yet.</div>
        )}
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => navigate(`/doctor/patients/${patientId}/encounters/${encounter.id}`)}
        >
          View Encounter →
        </button>
      </div>
    </article>
  );
}

function EncounterStateBadge({ state }: { state?: string }) {
  const map: Record<string, string> = {
    AWAITING_TRIAGE: 'badge-warning',
    AWAITING_PAYMENT: 'badge-warning',
    AWAITING_CONSULT: 'badge-info',
    IN_CONSULT: 'badge-info',
    AWAITING_RESULTS: 'badge-warning',
    CONSULT_COMPLETE: 'badge-success',
    CLOSED: 'badge-success',
    DISCHARGED: 'badge-success',
    REFERRED_OUT: 'badge-neutral',
    DECEASED: 'badge-neutral',
    ABANDONED_UNPAID: 'badge-critical',
    LEFT_WITHOUT_BEING_SEEN: 'badge-critical',
    CANCELLED_IN_ERROR: 'badge-critical',
  };
  const cls = map[state ?? ''] ?? 'badge-neutral';
  return <span className={`badge ${cls}`}>{(state ?? '').replaceAll('_', ' ').toLowerCase()}</span>;
}

function ageFromDob(dob: string): number {
  return Math.floor((Date.now() - new Date(dob).getTime()) / (365.25 * 24 * 60 * 60 * 1000));
}

function genderLabel(g: string): string {
  if (g === 'M' || g.toUpperCase().startsWith('M')) return 'Male';
  if (g === 'F' || g.toUpperCase().startsWith('F')) return 'Female';
  return g;
}

function truncate(s: string | undefined, max: number): string {
  if (!s) return '';
  const trimmed = s.trim();
  return trimmed.length > max ? trimmed.slice(0, max - 1) + '…' : trimmed;
}
