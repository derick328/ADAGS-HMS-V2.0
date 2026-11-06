import { PatientPresencePanel } from '../../shared/components/PatientPresencePanel';
import { fetchPresence, fetchVisitSummaries } from '../../shared/api/presence';
import { useAdminFacilityId } from '../../shared/auth/useFacilityScope';
import { FormSection } from '../../shared/components/FormSection';
import { Checkbox } from '../../shared/components/Checkbox';
import { Radio } from '../../shared/components/Radio';
import { FormField as FormRow } from '../../shared/components/FormField';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type CSSProperties } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import { z } from 'zod';
import { fetchDepartments } from '../../shared/api/departments';
import { ApiError } from '../../shared/api/client';
import { openEncounter } from '../../shared/api/encounters';
import { fetchClinicPlans, fetchActiveClinicAppointments, fetchPatientAppointments, clinicArrivalLabel, type ClinicAppointment } from '../../shared/api/appointments';
import { fetchInsuranceSchemes } from '../../shared/api/insuranceSchemes';
import {
  DuplicatePatientError,
  registerPatient,
  searchPatients,
  type DuplicateCandidate,
  type PatientResponse,
  type PatientSearchResult,
  type RegisterPatientRequest,
} from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { formatDate, formatDateTime, formatWeekdayDate } from '../../shared/format/date';
import { formatPhone } from '../../shared/format/phone';
import { Modal } from '../../shared/components/Modal';
import { BOOTSTRAP_FACILITY_ID } from '../../shared/facility';

// 05-receptionist.md §4/§5.2. Two things this screen deliberately does
// NOT do:
//   - "Resume" for an AWAITING_RESULTS encounter — that action lives on
//     Expected Returns (05a §3.1's own data source for that state), not
//     duplicated here against every search row.
//   - Ask which department — POST /api/encounters requires one; this
//     auto-picks the sole active department and only shows a picker if
//     there's ever more than one, keeping "one click, no form" true today.
export function PatientIntakePage() {
  const { accessToken } = useAuth();
  const canSearch = usePermission('PATIENT_SEARCH');
  const canRegister = usePermission('PATIENT_CREATE');
  const [query, setQuery] = useState('');
  const [submittedQuery, setSubmittedQuery] = useState('');
  const [showRegisterForm, setShowRegisterForm] = useState(false);

  const { data: results, isLoading: searching } = useQuery({
    queryKey: ['patients', 'search', submittedQuery],
    queryFn: () => searchPatients(submittedQuery, accessToken as string),
    enabled: accessToken != null && submittedQuery.trim() !== '',
  });

  return (
    <div>
      <PageHeader title={<>Patient intake</>} description={<>Find or register a patient, then prepare their visit.</>} />

      <PatientPresencePanel />

      <section style={{ marginTop: 'var(--space-6)' }}>
        <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)' }}>Find a patient</h2>
        <form className="clinical-filter-bar"
          onSubmit={(e) => {
            e.preventDefault();
            setSubmittedQuery(query.trim());
            setShowRegisterForm(false);
          }}
          style={{ display: 'flex', gap: 'var(--space-2)', marginBottom: 'var(--space-4)' }}
        >
          <input className="clinical-control"
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by name, hospital number, phone, or national ID"
            aria-label="Search patients"
            style={{ ...fieldStyle, maxWidth: 420, marginTop: 0 }}
          />
          <button type="submit" className="btn btn-primary" disabled={!canSearch || query.trim() === ''}>
            Search
          </button>
        </form>

        {!canSearch ? (
          <p style={{ color: 'var(--text-muted)' }}>You don't hold PATIENT_SEARCH.</p>
        ) : submittedQuery === '' ? (
          <EmptyState message="Search for a patient to get started." />
        ) : searching ? (
          <EmptyState message="Searching…" />
        ) : !results || results.length === 0 ? (
          <EmptyState message={`No patients match "${submittedQuery}"`} />
        ) : (
          <SearchResultsTable results={results} />
        )}

        {/* Deliberately NOT gated on submittedQuery !== '' — deviates from
            05-receptionist.md §4.1 ("must not be reachable as a blank form
            from the nav") on explicit product-owner instruction, prioritising
            registration speed over the UI search-first nudge. Duplicate
            protection is unaffected: RegisterPatientService still runs full
            duplicate detection (exact national ID or similar full name)
            server-side on every submit, independent of whether a
            search ran first — see the duplicateCandidates handling below. */}
        {canRegister && (
          <div style={{ marginTop: 'var(--space-4)' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setShowRegisterForm((v) => !v)}>
              {showRegisterForm ? 'Cancel' : 'Register new patient'}
            </button>
          </div>
        )}

        {showRegisterForm && <RegistrationForm onRegistered={() => setShowRegisterForm(false)} />}
      </section>
    </div>
  );
}

// Shared between the search-results row action and the post-registration
// success panel — same "open an encounter for this patient" action, same
// auto-pick-the-sole-department / else-show-a-picker behaviour either way.
function SendToTriageControl({ patientId, onSent, appointment }: { patientId: string; onSent?: () => void; appointment?: ClinicAppointment }) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const canOpenEncounter = usePermission('ENCOUNTER_OPEN');

  const { data: departments } = useQuery({
    queryKey: ['departments'],
    queryFn: () => fetchDepartments(accessToken as string),
    enabled: accessToken != null && canOpenEncounter,
  });

  const [picking, setPicking] = useState(false);

  const sendToTriage = useMutation({
    mutationFn: (departmentId: string) => openEncounter({ patientId, departmentId, appointmentId: appointment?.id }, accessToken as string),
    onSuccess: () => {
      setPicking(false);
      queryClient.invalidateQueries({ queryKey: ['here-now'] });
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      queryClient.invalidateQueries({ queryKey: ['visit-summary'] });
      queryClient.invalidateQueries({ queryKey: ['visit-summaries'] });
      queryClient.invalidateQueries({ queryKey: ['triage-queue'] });
      queryClient.invalidateQueries({ queryKey: ['encounters', 'search'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard', 'waiting-for-triage'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard', 'in-progress-today'] });
    },
  });

  if (!canOpenEncounter) {
    return null;
  }

  function handleClick() {
    if (!departments || departments.length === 0) {
      return;
    }
    if (appointment?.departmentId) {
      sendToTriage.mutate(appointment.departmentId);
    } else if (departments.length === 1) {
      sendToTriage.mutate(departments[0].id as string);
    } else {
      setPicking(true);
    }
  }

  if (sendToTriage.isSuccess) {
    const awaitingTriage = sendToTriage.data.state === 'AWAITING_TRIAGE';
    return <div role={awaitingTriage ? 'status' : 'alert'}>
      <p>{awaitingTriage
        ? sendToTriage.data.existingEncounter
          ? 'This patient is already waiting for triage.'
          : 'New encounter opened and sent to triage.'
        : `This patient already has an open encounter in ${sendToTriage.data.state?.replaceAll('_', ' ').toLowerCase()}. They were not added to the triage queue.`}</p>
      {!awaitingTriage && <Link to={`/receptionist/patient-records?patientId=${patientId}`}>View patient record</Link>}
      {onSent && <button type="button" className="btn btn-secondary" onClick={onSent}>{awaitingTriage ? 'Done' : 'Close'}</button>}
    </div>;
  }

  if (picking) {
    return (
      <div>
        <select className="clinical-control"
          autoFocus
          disabled={sendToTriage.isPending}
          onChange={(e) => sendToTriage.mutate(e.target.value)}
          style={{ font: 'inherit' }}
          defaultValue=""
        >
          <option value="" disabled>
            Choose department…
          </option>
          {departments?.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </select>
        {sendToTriage.error && <div className="form-error" role="alert">{sendToTriage.error instanceof ApiError ? sendToTriage.error.message : 'Could not send this patient to triage. Please try again.'}</div>}
      </div>
    );
  }

  return (
    <div>
      <button type="button" className="btn btn-primary" disabled={sendToTriage.isPending || !departments?.length} onClick={handleClick}>
        {appointment ? 'Check In for Clinic / Send to Triage' : 'Send to triage'}
      </button>
      {sendToTriage.error && <div className="form-error" role="alert">{sendToTriage.error instanceof ApiError ? sendToTriage.error.message : 'Could not send this patient to triage. Please try again.'}</div>}
    </div>
  );
}

function SearchResultsTable({ results }: { results: PatientSearchResult[] }) {
  const { accessToken } = useAuth();
  const facilityId = useAdminFacilityId();
  const ids = results.map(p => p.id!).filter(Boolean).slice(0, 100);
  const clinics = useQuery({ queryKey: ['appointments', 'clinic-plans', facilityId],
    queryFn: () => fetchClinicPlans(facilityId!, accessToken!), enabled: !!facilityId && !!accessToken });
  const scheduledClinics = useQuery({ queryKey: ['appointments', 'clinic-active'], queryFn: () => fetchActiveClinicAppointments(accessToken!), enabled: !!accessToken });
  const summaries = useQuery({ queryKey: ['visit-summaries', facilityId, ids],
    queryFn: () => fetchVisitSummaries(facilityId!, ids, accessToken!), enabled: !!facilityId && !!accessToken && ids.length > 0 });

  const canOpenEncounter = usePermission('ENCOUNTER_OPEN');
  // The patient whose "start a new encounter" prompt is open. A registered
  // patient found in search doesn't get sent to triage on a single click —
  // the receptionist confirms in a dialog that also surfaces when this
  // patient last visited, so a repeat attendance or a same-day duplicate
  // encounter is visible before a new one is opened.
  const [startEncounterFor, setStartEncounterFor] = useState<PatientSearchResult | null>(null);

  return (
    <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
      <TableRegion label="Patient search results"><table className="table">
        <caption className="sr-only">Patient search results</caption>
        <thead>
          <tr>
            <th>Hospital no.</th>
            <th>Name</th>
            <th>Age</th>
            <th>Phone</th>
            <th>Clinic follow-up</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {results.map((patient) => (
            <tr key={patient.id}>
              <td>{patient.hospitalNo ?? '—'}</td>
              <td style={{ fontWeight: 'var(--weight-medium)' }}>
                {patient.fullName ?? '—'}
                {summaries.data?.find(s => s.patientId === patient.id) && <span className="badge badge-neutral">{summaries.data.find(s => s.patientId === patient.id)!.totalVisits === 0 ? 'First visit' : `Returning patient · ${summaries.data.find(s => s.patientId === patient.id)!.totalVisits} recorded visits`}</span>}
                {clinics.data?.some(c => c.patientId === patient.id && c.active) && <span className="badge badge-info" style={{ marginLeft: 'var(--space-2)' }}>Clinic Patient</span>}
                {patient.dobIsEstimated && (
                  <span className="badge badge-neutral" style={{ marginLeft: 'var(--space-2)' }}>
                    est. DOB
                  </span>
                )}
              </td>
              <td>{ageFromDob(patient.dateOfBirth)}</td>
              <td>{formatPhone(patient.phone)}</td>
              <td>{scheduledClinics.data?.filter(a => a.patientId === patient.id).map(a => <div key={a.id}>
                <span className="badge badge-info">Clinic Patient</span> {a.clinic} · {a.doctor}<br />
                {formatDateTime(a.scheduledAt)} · {a.status} · {clinicArrivalLabel(a.scheduledAt)}
              </div>) ?? '—'}</td>
              <td style={{ width: 1, whiteSpace: 'nowrap' }}>
                {canOpenEncounter && (
                  <button type="button" className="btn btn-primary" onClick={() => setStartEncounterFor(patient)}>
                    Start encounter
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table></TableRegion>

      <StartEncounterModal patient={startEncounterFor} onClose={() => setStartEncounterFor(null)} />
    </div>
  );
}

// 05-receptionist.md §5.2. The "patient is already registered — start a new
// encounter and send them to triage" prompt. It also answers "has this
// person been here before, and when?" inline: PATIENT_READ_VISIT_HISTORY
// is a receptionist permission (V5), and seeing the last visit day/date
// before opening a fresh encounter is how the front desk catches a
// same-day duplicate or recognises a returning patient.
function StartEncounterModal({ patient, onClose }: { patient: PatientSearchResult | null; onClose: () => void }) {
  const { accessToken } = useAuth();
  const canReadVisitHistory = usePermission('PATIENT_READ_VISIT_HISTORY');
  const patientId = patient?.id;
  const facilityId = useAdminFacilityId();
  const summary = useQuery({ queryKey: ['visit-summary', facilityId, patientId],
    queryFn: () => fetchVisitSummaries(facilityId!, [patientId!], accessToken!), enabled: !!facilityId && !!patientId && !!accessToken && canReadVisitHistory });
  const stats = summary.data?.[0];
  const appointments = useQuery({ queryKey: ['appointments', 'patient', patientId],
    queryFn: () => fetchPatientAppointments(patientId!, accessToken!), enabled: !!patientId && !!accessToken });
  const scheduledClinic = appointments.data?.filter(a => a.clinicFollowUpId && (a.status === 'SCHEDULED' || a.status === 'NO_SHOW')) ?? [];
  const [selectedAppointmentId, setSelectedAppointmentId] = useState('');
  const selectedAppointment = scheduledClinic.find(a => a.id === selectedAppointmentId) ?? (scheduledClinic.length === 1 ? scheduledClinic[0] : undefined);


  const { data: history, isLoading } = useQuery({
    queryKey: ['patient', patientId, 'visit-history'],
    queryFn: () => fetchPresence(facilityId!, accessToken as string, patientId),
    enabled: accessToken != null && patientId != null && canReadVisitHistory && !!facilityId,
  });

  return (
    <Modal
      open={patient != null}
      onClose={onClose}
      size="md"
      title={<>Start a new encounter</>}
      description={
        patient ? (
          <>
            {patient.fullName} · {patient.hospitalNo ?? '—'} is already registered. Opening an encounter sends them
            to triage.
          </>
        ) : undefined
      }
      footer={
        <>
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          {patientId && <SendToTriageControl patientId={patientId} appointment={selectedAppointment} onSent={onClose} />}
        </>
      }
    >
      {scheduledClinic.length > 0 && <div style={{ padding: 'var(--space-3)', background: 'var(--bg-subtle)', borderRadius: 'var(--radius)', marginBottom: 'var(--space-3)' }}>
        <strong className="badge badge-info">Clinic Patient</strong>
        <p>Select the appointment to check in. The patient may arrive before or after the scheduled day.</p>
        {scheduledClinic.map(a => <label key={a.id} style={{ display: 'block', marginBottom: 'var(--space-2)' }}>
          <input type="radio" name="clinicAppointment" checked={selectedAppointment?.id === a.id} onChange={() => setSelectedAppointmentId(a.id!)} />{' '}
          {a.clinic} · {a.doctor} · {formatDateTime(a.scheduledAt)} · {clinicArrivalLabel(a.scheduledAt)} · {a.status}
        </label>)}
      </div>}
      {stats && <p>{stats.totalVisits === 0 ? 'First visit' : 'Returning patient'} · Recorded visits: {stats.totalVisits}<br />
        {stats.activeEncounter ? 'An open encounter already exists. Send to triage will return that encounter.' : `Next attendance: visit ${(stats.totalVisits ?? 0) + 1}`}<br />
        Last visit: {formatDate(stats.latestVisit)}</p>}
      <h3 style={{ fontSize: 'var(--text-sm)', fontWeight: 'var(--weight-medium)', margin: '0 0 var(--space-2)', color: 'var(--text-secondary)' }}>
        Previous visits
      </h3>
      {!canReadVisitHistory ? (
        <p style={{ color: 'var(--text-muted)', margin: 0 }}>You don't hold PATIENT_READ_VISIT_HISTORY.</p>
      ) : isLoading ? (
        <FeedbackState loading>Loading visit history…</FeedbackState>
      ) : !history || history.length === 0 ? (
        <p style={{ color: 'var(--text-muted)', margin: 0 }}>No previous visits — this is their first time at the facility.</p>
      ) : (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
          {history.map((h) => (
            <li
              key={h.encounterId}
              style={{ display: 'flex', justifyContent: 'space-between', gap: 'var(--space-3)', fontSize: 'var(--text-sm)' }}
            >
              <span style={{ fontWeight: 'var(--weight-medium)' }}>{formatWeekdayDate(h.arrivedAt)}</span>
              <span style={{ color: 'var(--text-secondary)', textAlign: 'right' }}>
                {h.stage ?? '—'}
                {h.state ? ` · ${h.state.replaceAll('_', ' ').toLowerCase()}` : ''}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  );
}

const relationshipOptions = ['SPOUSE', 'PARENT', 'CHILD', 'SIBLING', 'GUARDIAN', 'FRIEND', 'RELATIVE', 'OTHER'] as const;
const genderOptions = ['MALE', 'FEMALE'] as const;
const maritalStatusOptions = ['SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED', 'WIDOWER'] as const;
const MINOR_MAX_AGE = 15;

const registrationSchema = z
  .object({
    givenName: z.string().min(2, 'Enter a given name'),
    middleName: z.string().optional(),
    familyName: z.string().min(2, 'Enter a family name'),
    dobKnown: z.enum(['exact', 'estimated']),
    dateOfBirth: z.string().optional(),
    ageYears: z.coerce.number().int().min(0).optional(),
    ageMonths: z.coerce.number().int().min(0).max(11).optional(),
    gender: z.enum(genderOptions),
    maritalStatus: z.enum(maritalStatusOptions).optional(),
    nationalId: z.string().optional(),
    phone: z.string().min(1, 'Enter a phone number'),
    email: z.string().optional(),
    occupation: z.string().optional(),
    nokFullName: z.string().min(2, "Enter the next of kin's name"),
    nokRelationship: z.enum(relationshipOptions),
    nokRelationshipOther: z.string().optional(),
    nokPhone: z.string().min(1, "Enter the next of kin's phone number"),
    currentRegion: z.string().min(1, 'Enter a region'),
    currentDistrict: z.string().min(1, 'Enter a district'),
    currentWard: z.string().min(1, 'Enter a ward'),
    currentStreetOrVillage: z.string().min(1, 'Enter a street or village'),
    currentLandmark: z.string().optional(),
    permanentSameAsCurrent: z.boolean(),
    permanentRegion: z.string().optional(),
    permanentDistrict: z.string().optional(),
    permanentWard: z.string().optional(),
    permanentStreetOrVillage: z.string().optional(),
    permanentLandmark: z.string().optional(),
    hasInsurance: z.boolean(),
    schemeId: z.string().optional(),
    policyNumber: z.string().optional(),
    memberNumber: z.string().optional(),
    isPrincipal: z.boolean(),
    principalName: z.string().optional(),
  })
  .refine((v) => v.dobKnown === 'exact' || v.ageYears != null, {
    message: 'Enter an age in years', path: ['ageYears'],
  })
  .refine((v) => v.dobKnown === 'estimated' || (v.dateOfBirth ?? '') !== '', {
    message: 'Enter a date of birth', path: ['dateOfBirth'],
  })
  .refine((v) => v.isPrincipal || (v.principalName ?? '').trim() !== '', {
    message: 'Enter the principal member’s name', path: ['principalName'],
  })
  .refine((v) => v.nokRelationship !== 'OTHER' || (v.nokRelationshipOther ?? '').trim() !== '', {
    message: 'Describe the relationship', path: ['nokRelationshipOther'],
  })
  .refine((v) => v.permanentSameAsCurrent || (v.permanentRegion ?? '').trim() !== '', {
    message: 'Enter a region', path: ['permanentRegion'],
  })
  .refine((v) => v.permanentSameAsCurrent || (v.permanentDistrict ?? '').trim() !== '', {
    message: 'Enter a district', path: ['permanentDistrict'],
  })
  .refine((v) => v.permanentSameAsCurrent || (v.permanentWard ?? '').trim() !== '', {
    message: 'Enter a ward', path: ['permanentWard'],
  })
  .refine((v) => v.permanentSameAsCurrent || (v.permanentStreetOrVillage ?? '').trim() !== '', {
    message: 'Enter a street or village', path: ['permanentStreetOrVillage'],
  })
  .refine((v) => normalizePhone(v.nokPhone) !== normalizePhone(v.phone), {
    message: 'Enter a different phone from the patient’s', path: ['nokPhone'],
  })
  .refine(
    (v) => {
      const nok = normalizeName(v.nokFullName);
      if (nok === '') return true;
      const withMiddle = normalizeName(`${v.givenName} ${v.middleName ?? ''} ${v.familyName}`);
      const withoutMiddle = normalizeName(`${v.givenName} ${v.familyName}`);
      return nok !== withMiddle && nok !== withoutMiddle;
    },
    { message: 'Next of kin must be a different person from the patient', path: ['nokFullName'] },
  );

function normalizePhone(phone: string | undefined): string {
  return (phone ?? '').replace(/\s+/g, '');
}

function normalizeName(name: string | undefined): string {
  return (name ?? '').trim().replace(/\s+/g, ' ').toLowerCase();
}

type RegistrationValues = z.infer<typeof registrationSchema>;

function RegistrationForm({ onRegistered }: { onRegistered: () => void }) {
  const { accessToken } = useAuth();
  const [duplicateCandidates, setDuplicateCandidates] = useState<DuplicateCandidate[] | null>(null);
  const [registered, setRegistered] = useState<PatientResponse | null>(null);

  const { data: schemes } = useQuery({
    queryKey: ['insurance-schemes'],
    queryFn: () => fetchInsuranceSchemes(accessToken as string),
    enabled: accessToken != null,
  });

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<RegistrationValues>({
    resolver: zodResolver(registrationSchema),
    defaultValues: { dobKnown: 'exact', permanentSameAsCurrent: true, hasInsurance: false, isPrincipal: true },
  });

  const dobKnown = watch('dobKnown');
  const permanentSameAsCurrent = watch('permanentSameAsCurrent');
  const hasInsurance = watch('hasInsurance');
  const isPrincipal = watch('isPrincipal');
  const ageYears = watch('ageYears');
  const dateOfBirth = watch('dateOfBirth');
  const nokRelationship = watch('nokRelationship');
  const computedAge = dobKnown === 'exact' ? ageFromDobString(dateOfBirth) : ageYears;
  const isMinor = computedAge != null && computedAge <= MINOR_MAX_AGE;

  const submit = useMutation({
    mutationFn: (body: RegisterPatientRequest) => registerPatient(body, accessToken as string),
    onSuccess: (patient) => {
      setDuplicateCandidates(null);
      setRegistered(patient);
    },
    onError: (error) => {
      if (error instanceof DuplicatePatientError) {
        setDuplicateCandidates(error.candidates);
      }
    },
  });

  function toRequest(values: RegistrationValues): RegisterPatientRequest {
    const current = {
      addressType: 'CURRENT',
      region: values.currentRegion,
      district: values.currentDistrict,
      ward: values.currentWard,
      streetOrVillage: values.currentStreetOrVillage,
      landmark: values.currentLandmark || undefined,
    };
    const permanent = values.permanentSameAsCurrent
      ? { ...current, addressType: 'PERMANENT' }
      : {
          addressType: 'PERMANENT',
          region: values.permanentRegion,
          district: values.permanentDistrict,
          ward: values.permanentWard,
          streetOrVillage: values.permanentStreetOrVillage,
          landmark: values.permanentLandmark || undefined,
        };
    const addresses = [current, permanent];

    return {
      givenName: values.givenName,
      middleName: values.middleName || undefined,
      familyName: values.familyName,
      dateOfBirth: values.dobKnown === 'exact' ? values.dateOfBirth : undefined,
      ageYears: values.dobKnown === 'estimated' ? values.ageYears : undefined,
      ageMonths: values.dobKnown === 'estimated' ? values.ageMonths : undefined,
      gender: values.gender,
      maritalStatus: isMinor ? undefined : values.maritalStatus,
      nationalId: values.nationalId || undefined,
      phone: values.phone,
      email: values.email || undefined,
      occupation: values.occupation || undefined,
      nextOfKin: {
        fullName: values.nokFullName,
        relationship: values.nokRelationship,
        relationshipOther: values.nokRelationship === 'OTHER' ? values.nokRelationshipOther?.trim() : undefined,
        phone: values.nokPhone,
        isGuardian: false,
      },
      addresses,
      insurancePolicy: values.hasInsurance && values.schemeId
        ? {
            schemeId: values.schemeId,
            policyNumber: values.policyNumber ?? '',
            memberNumber: values.memberNumber || undefined,
            isPrincipal: values.isPrincipal,
            principalName: values.isPrincipal ? undefined : values.principalName,
          }
        : undefined,
      facilityId: BOOTSTRAP_FACILITY_ID,
    };
  }

  const onSubmit = handleSubmit((values) => submit.mutate(toRequest(values)));

  if (registered) {
    return (
      <div style={{ ...cardStyle, marginTop: 'var(--space-4)' }}>
        <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 }}>Patient registered</h2>
        <p style={{ fontSize: 'var(--text-2xl)', fontWeight: 'var(--weight-bold)' }} className="tabular">
          {registered.hospitalNo}
        </p>
        <p style={{ color: 'var(--text-secondary)' }}>{registered.fullName}</p>
        <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
          <button type="button" className="btn btn-secondary" onClick={() => window.print()}>
            Print card
          </button>
          <SendToTriageControl patientId={registered.id as string} onSent={onRegistered} />
          <Link to={`/receptionist/patient-records?patientId=${registered.id}`} className="btn btn-secondary">
            View patient record
          </Link>
          <button type="button" className="btn btn-ghost" onClick={onRegistered}>
            Done
          </button>
        </div>
      </div>
    );
  }

  return (
    <>
      <Modal
        open={duplicateCandidates != null}
        onClose={() => setDuplicateCandidates(null)}
        title={<>This patient already exists</>}
        description={<>A registered patient has a matching national ID or a similar full name. Select the correct record to send to triage.</>}
        footer={<button type="button" className="btn btn-ghost" onClick={() => setDuplicateCandidates(null)}>Cancel</button>}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          {duplicateCandidates?.map((candidate) => (
            <div key={candidate.patientId} style={{ ...cardStyle, marginTop: 0 }}>
              <strong>{candidate.fullName}</strong>
              <p style={{ margin: 'var(--space-2) 0', color: 'var(--text-secondary)' }}>
                Hospital number: {candidate.hospitalNo}
              </p>
              <p style={{ margin: '0 0 var(--space-3)', color: 'var(--text-secondary)' }}>
                Matched by {candidate.reasons.includes('NATIONAL_ID_EXACT') ? 'national ID' : 'full name'}
              </p>
              <SendToTriageControl patientId={candidate.patientId} onSent={onRegistered} />
            </div>
          ))}
        </div>
      </Modal>
      <form onSubmit={onSubmit} noValidate className="clinical-form-layout intake-form">

      <FormSection title="Identity" columns={2} className="form-section--wide">

        <FormRow label="Given name" error={errors.givenName?.message}>
          <input className="clinical-control" {...register('givenName')} style={fieldStyle} />
        </FormRow>
        <FormRow label="Middle name (optional)">
          <input className="clinical-control" {...register('middleName')} style={fieldStyle} />
        </FormRow>
        <FormRow label="Family name" error={errors.familyName?.message}>
          <input className="clinical-control" {...register('familyName')} style={fieldStyle} />
        </FormRow>
        <div style={{ display: 'flex', gap: 'var(--space-4)' }}>
          <Radio  className="clinical-choice" value="exact" {...register('dobKnown')} label={<>Exact date of birth</>} />
          <Radio  className="clinical-choice" value="estimated" {...register('dobKnown')} label={<>Estimated age</>} />
        </div>
        {dobKnown === 'exact' ? (
          <FormRow label="Date of birth" error={errors.dateOfBirth?.message}>
            <input className="clinical-control" type="date" {...register('dateOfBirth')} style={fieldStyle} />
          </FormRow>
        ) : (
          <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
            <FormRow label="Age (years)" error={errors.ageYears?.message}>
              <input className="clinical-control" type="number" min={0} {...register('ageYears')} style={fieldStyle} />
            </FormRow>
            <FormRow label="Months (optional)">
              <input className="clinical-control" type="number" min={0} max={11} {...register('ageMonths')} style={fieldStyle} />
            </FormRow>
          </div>
        )}
        <FormRow label="Gender" error={errors.gender?.message}>
          <select className="clinical-control" {...register('gender')} style={fieldStyle}>
            {genderOptions.map((g) => (
              <option key={g} value={g}>
                {g.replaceAll('_', ' ').toLowerCase()}
              </option>
            ))}
          </select>
        </FormRow>
        {!isMinor && (
          <FormRow label="Marital status (optional)">
            <select className="clinical-control" {...register('maritalStatus')} style={fieldStyle} defaultValue="">
              <option value="">—</option>
              {maritalStatusOptions.map((m) => (
                <option key={m} value={m}>
                  {m.toLowerCase()}
                </option>
              ))}
            </select>
          </FormRow>
        )}
        <FormRow label="National ID (optional)">
          <input className="clinical-control" {...register('nationalId')} style={fieldStyle} />
        </FormRow>
      </FormSection>

      <FormSection title="Contact" columns={1}>

        <FormRow label="Phone" error={errors.phone?.message}>
          <input aria-label="0700000000" className="clinical-control" {...register('phone')} style={fieldStyle} placeholder="0700000000" />
        </FormRow>
        <FormRow label="Email (optional)">
          <input className="clinical-control" type="email" {...register('email')} style={fieldStyle} />
        </FormRow>
        <FormRow label="Occupation (optional)">
          <input className="clinical-control" {...register('occupation')} style={fieldStyle} />
        </FormRow>
      </FormSection>

      <FormSection title="Next of kin" columns={1}>

        <FormRow label="Full name" error={errors.nokFullName?.message}>
          <input className="clinical-control" {...register('nokFullName')} style={fieldStyle} />
        </FormRow>
        <FormRow label="Relationship" error={errors.nokRelationship?.message}>
          <select className="clinical-control" {...register('nokRelationship')} style={fieldStyle}>
            {relationshipOptions.map((r) => (
              <option key={r} value={r}>
                {r.toLowerCase()}
              </option>
            ))}
          </select>
        </FormRow>
        {nokRelationship === 'OTHER' && (
          <FormRow label="Specify relationship" error={errors.nokRelationshipOther?.message}>
            <input className="clinical-control" {...register('nokRelationshipOther')} style={fieldStyle} />
          </FormRow>
        )}
        <FormRow label="Phone" error={errors.nokPhone?.message}>
          <input aria-label="0700000000" className="clinical-control" {...register('nokPhone')} style={fieldStyle} placeholder="0700000000" />
        </FormRow>
      </FormSection>

      <FormSection title="Current address" columns={2}>

        <FormRow label="Region" error={errors.currentRegion?.message}><input className="clinical-control" {...register('currentRegion')} style={fieldStyle} /></FormRow>
        <FormRow label="District" error={errors.currentDistrict?.message}><input className="clinical-control" {...register('currentDistrict')} style={fieldStyle} /></FormRow>
        <FormRow label="Ward" error={errors.currentWard?.message}><input className="clinical-control" {...register('currentWard')} style={fieldStyle} /></FormRow>
        <FormRow label="Street or village" error={errors.currentStreetOrVillage?.message}><input className="clinical-control" {...register('currentStreetOrVillage')} style={fieldStyle} /></FormRow>
        <FormRow label="Landmark (optional)"><input className="clinical-control" {...register('currentLandmark')} style={fieldStyle} /></FormRow>
      </FormSection>

      <FormSection title="Permanent address" columns={2}>

        <Checkbox  className="clinical-choice" {...register('permanentSameAsCurrent')} label={<>Same as current</>} />
        {!permanentSameAsCurrent && (
          <>
            <FormRow label="Region" error={errors.permanentRegion?.message}><input className="clinical-control" {...register('permanentRegion')} style={fieldStyle} /></FormRow>
            <FormRow label="District" error={errors.permanentDistrict?.message}><input className="clinical-control" {...register('permanentDistrict')} style={fieldStyle} /></FormRow>
            <FormRow label="Ward" error={errors.permanentWard?.message}><input className="clinical-control" {...register('permanentWard')} style={fieldStyle} /></FormRow>
            <FormRow label="Street or village" error={errors.permanentStreetOrVillage?.message}><input className="clinical-control" {...register('permanentStreetOrVillage')} style={fieldStyle} /></FormRow>
            <FormRow label="Landmark (optional)"><input className="clinical-control" {...register('permanentLandmark')} style={fieldStyle} /></FormRow>
          </>
        )}
      </FormSection>

      <FormSection title="Insurance (optional)" columns={1}>

        <Checkbox  className="clinical-choice" {...register('hasInsurance')} label={<>Patient has insurance</>} />
        {hasInsurance && (
          <>
            <FormRow label="Scheme">
              <select className="clinical-control" {...register('schemeId')} style={fieldStyle} defaultValue="">
                <option value="" disabled>
                  Choose a scheme
                </option>
                {schemes?.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </FormRow>
            <FormRow label="Policy number">
              <input className="clinical-control" {...register('policyNumber')} style={fieldStyle} />
            </FormRow>
            <FormRow label="Member number (optional)">
              <input className="clinical-control" {...register('memberNumber')} style={fieldStyle} />
            </FormRow>
            <Checkbox  className="clinical-choice" checked={isPrincipal} onChange={(e) => setValue('isPrincipal', e.target.checked)} label={<>{' '}
              Patient is the principal member</>} />
            {!isPrincipal && (
              <FormRow label="Principal member's name" error={errors.principalName?.message}>
                <input className="clinical-control" {...register('principalName')} style={fieldStyle} />
              </FormRow>
            )}
          </>
        )}
      </FormSection>

      {submit.error && !(submit.error instanceof DuplicatePatientError) && (
        <div className="form-error">Something went wrong. Check the values and try again.</div>
      )}

      <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
        {submit.isPending ? 'Registering…' : 'Register patient'}
      </button>
      </form>
    </>
  );
}


function ageFromDob(dob: string | undefined): string {
  if (!dob) return '—';
  const years = ageFromDobString(dob);
  return years == null ? '—' : `${years}`;
}

function ageFromDobString(dob: string | undefined): number | undefined {
  if (!dob) return undefined;
  const parsed = new Date(dob);
  if (Number.isNaN(parsed.getTime())) return undefined;
  return Math.floor((Date.now() - parsed.getTime()) / (365.25 * 24 * 60 * 60 * 1000));
}

function EmptyState({ message }: { message: string }) {
  return <FeedbackState loading={message.startsWith('Loading') || message.startsWith('Searching')}>{message}</FeedbackState>;
}

const cardStyle: CSSProperties = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
  borderRadius: 'var(--radius-lg)',
  padding: 'var(--space-4)',
  maxWidth: 560,
};


const fieldStyle: CSSProperties = {
  width: '100%',
  marginTop: 'var(--space-1)',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};
