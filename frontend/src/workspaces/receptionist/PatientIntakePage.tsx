import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type CSSProperties } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { fetchDepartments } from '../../shared/api/departments';
import { openEncounter, searchEncounters, type EncounterResponse } from '../../shared/api/encounters';
import { fetchInsuranceSchemes } from '../../shared/api/insuranceSchemes';
import {
  DuplicatePatientError,
  fetchPatientById,
  registerPatient,
  searchPatients,
  type DuplicateCandidate,
  type PatientResponse,
  type PatientSearchResult,
  type RegisterPatientRequest,
} from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { formatDate } from '../../shared/format/date';
import { formatPhone } from '../../shared/format/phone';
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
  const canOpenEncounter = usePermission('ENCOUNTER_OPEN');
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
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>Patient Intake</h1>
      </header>

      <HereNowPanel />

      <section style={{ marginTop: 'var(--space-6)' }}>
        <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)' }}>Find a patient</h2>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            setSubmittedQuery(query.trim());
            setShowRegisterForm(false);
          }}
          style={{ display: 'flex', gap: 'var(--space-2)', marginBottom: 'var(--space-4)' }}
        >
          <input
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
          <SearchResultsTable results={results} canOpenEncounter={canOpenEncounter} />
        )}

        {/* 05-receptionist.md §4.1: the entry point is a search box —
            "Register new patient" appears below results, after a search
            has run, never as a blank form from the nav. */}
        {submittedQuery !== '' && canRegister && (
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

function HereNowPanel() {
  const { accessToken } = useAuth();
  const canOpenEncounter = usePermission('ENCOUNTER_OPEN');

  const { data: encounters, isLoading } = useQuery({
    queryKey: ['here-now'],
    queryFn: () => searchEncounters({ excludeTerminal: true }, accessToken as string),
    enabled: accessToken != null && canOpenEncounter,
    refetchInterval: 30_000,
  });

  if (!canOpenEncounter) {
    return null;
  }

  const grouped = new Map<string, EncounterResponse[]>();
  for (const e of encounters ?? []) {
    const state = e.state ?? 'UNKNOWN';
    grouped.set(state, [...(grouped.get(state) ?? []), e]);
  }

  return (
    <section
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        overflow: 'hidden',
      }}
    >
      <h2
        style={{
          fontSize: 'var(--text-lg)',
          fontWeight: 'var(--weight-medium)',
          margin: 0,
          padding: 'var(--space-4) var(--space-4) 0',
        }}
      >
        Here now
      </h2>
      {isLoading ? (
        <p style={{ padding: 'var(--space-4)', color: 'var(--text-muted)' }}>Loading…</p>
      ) : !encounters || encounters.length === 0 ? (
        <p style={{ padding: 'var(--space-4)', color: 'var(--text-muted)' }}>No patients in the building.</p>
      ) : (
        <table className="table">
          <caption className="sr-only">Encounters currently open</caption>
          <thead>
            <tr>
              <th>Patient</th>
              <th>State</th>
              <th>Since</th>
            </tr>
          </thead>
          <tbody>
            {[...grouped.entries()].flatMap(([state, rows]) =>
              rows.map((row) => (
                <tr key={row.id}>
                  <td>
                    <PatientNameCell patientId={row.patientId} />
                  </td>
                  <td>
                    <span className="badge badge-info">{state.replaceAll('_', ' ').toLowerCase()}</span>
                  </td>
                  <td style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>{formatDate(row.createdAt)}</td>
                </tr>
              )),
            )}
          </tbody>
        </table>
      )}
    </section>
  );
}

function PatientNameCell({ patientId }: { patientId: string | undefined }) {
  const { accessToken } = useAuth();
  const { data: patient } = useQuery({
    queryKey: ['patient', patientId],
    queryFn: () => fetchPatientById(patientId as string, accessToken as string),
    enabled: accessToken != null && patientId != null,
    staleTime: 60_000,
  });
  return <span style={{ fontWeight: 'var(--weight-medium)' }}>{patient?.fullName ?? '…'}</span>;
}

function SearchResultsTable({ results, canOpenEncounter }: { results: PatientSearchResult[]; canOpenEncounter: boolean }) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();

  const { data: departments } = useQuery({
    queryKey: ['departments'],
    queryFn: () => fetchDepartments(accessToken as string),
    enabled: accessToken != null && canOpenEncounter,
  });

  const [pickingDepartmentFor, setPickingDepartmentFor] = useState<string | null>(null);

  const sendToTriage = useMutation({
    mutationFn: (vars: { patientId: string; departmentId: string }) =>
      openEncounter({ patientId: vars.patientId, departmentId: vars.departmentId }, accessToken as string),
    onSuccess: () => {
      setPickingDepartmentFor(null);
      queryClient.invalidateQueries({ queryKey: ['here-now'] });
    },
  });

  function handleSendToTriage(patientId: string) {
    if (!departments || departments.length === 0) {
      return;
    }
    if (departments.length === 1) {
      sendToTriage.mutate({ patientId, departmentId: departments[0].id as string });
    } else {
      setPickingDepartmentFor(patientId);
    }
  }

  return (
    <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
      <table className="table">
        <caption className="sr-only">Patient search results</caption>
        <thead>
          <tr>
            <th>Hospital no.</th>
            <th>Name</th>
            <th>Age</th>
            <th>Phone</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {results.map((patient) => (
            <tr key={patient.id}>
              <td>{patient.hospitalNo ?? '—'}</td>
              <td style={{ fontWeight: 'var(--weight-medium)' }}>
                {patient.fullName ?? '—'}
                {patient.dobIsEstimated && (
                  <span className="badge badge-neutral" style={{ marginLeft: 'var(--space-2)' }}>
                    est. DOB
                  </span>
                )}
              </td>
              <td>{ageFromDob(patient.dateOfBirth)}</td>
              <td>{formatPhone(patient.phone)}</td>
              <td style={{ width: 1, whiteSpace: 'nowrap' }}>
                {canOpenEncounter &&
                  (pickingDepartmentFor === patient.id ? (
                    <select
                      autoFocus
                      onChange={(e) => sendToTriage.mutate({ patientId: patient.id as string, departmentId: e.target.value })}
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
                  ) : (
                    <button
                      type="button"
                      className="btn btn-primary"
                      disabled={sendToTriage.isPending}
                      onClick={() => handleSendToTriage(patient.id as string)}
                    >
                      Send to triage
                    </button>
                  ))}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const relationshipOptions = ['SPOUSE', 'PARENT', 'CHILD', 'SIBLING', 'GUARDIAN', 'OTHER'] as const;
const genderOptions = ['MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY'] as const;
const maritalStatusOptions = ['SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED'] as const;

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
    nokPhone: z.string().min(1, "Enter the next of kin's phone number"),
    currentRegion: z.string().optional(),
    currentDistrict: z.string().optional(),
    currentWard: z.string().optional(),
    currentStreetOrVillage: z.string().optional(),
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
    notSamePersonConfirmationReason: z.string().optional(),
  })
  .refine((v) => v.dobKnown === 'exact' || v.ageYears != null, {
    message: 'Enter an age in years', path: ['ageYears'],
  })
  .refine((v) => v.dobKnown === 'estimated' || (v.dateOfBirth ?? '') !== '', {
    message: 'Enter a date of birth', path: ['dateOfBirth'],
  })
  .refine((v) => v.isPrincipal || (v.principalName ?? '').trim() !== '', {
    message: 'Enter the principal member’s name', path: ['principalName'],
  });

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
  const isMinor = dobKnown === 'estimated' ? (ageYears ?? 99) < 18 : false;

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
    const addresses = [];
    if (values.currentRegion || values.currentDistrict || values.currentWard || values.currentStreetOrVillage || values.currentLandmark) {
      addresses.push({
        addressType: 'CURRENT',
        region: values.currentRegion,
        district: values.currentDistrict,
        ward: values.currentWard,
        streetOrVillage: values.currentStreetOrVillage,
        landmark: values.currentLandmark,
      });
    }
    if (!values.permanentSameAsCurrent && (values.permanentRegion || values.permanentDistrict || values.permanentWard || values.permanentStreetOrVillage || values.permanentLandmark)) {
      addresses.push({
        addressType: 'PERMANENT',
        region: values.permanentRegion,
        district: values.permanentDistrict,
        ward: values.permanentWard,
        streetOrVillage: values.permanentStreetOrVillage,
        landmark: values.permanentLandmark,
      });
    } else if (values.permanentSameAsCurrent && addresses.length > 0) {
      addresses.push({ ...addresses[0], addressType: 'PERMANENT' });
    }

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
      notSamePersonConfirmationReason: values.notSamePersonConfirmationReason || undefined,
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
        <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
          <button type="button" className="btn btn-secondary" onClick={() => window.print()}>
            Print card
          </button>
          <button type="button" className="btn btn-ghost" onClick={onRegistered}>
            Done
          </button>
        </div>
      </div>
    );
  }

  const hardBlock = duplicateCandidates?.some((c) => c.reasons.includes('NATIONAL_ID_EXACT'));

  return (
    <form onSubmit={onSubmit} noValidate style={{ ...cardStyle, marginTop: 'var(--space-4)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
      {duplicateCandidates && (
        <div
          role="alert"
          style={{ padding: 'var(--space-3)', background: 'var(--warning-bg)', color: 'var(--warning)', borderRadius: 'var(--radius)' }}
        >
          <strong>{hardBlock ? 'A patient with this national ID already exists.' : 'Possible existing patient found.'}</strong>
          <ul style={{ margin: 'var(--space-2) 0 0', paddingLeft: 'var(--space-4)' }}>
            {duplicateCandidates.map((c) => (
              <li key={c.patientId}>
                {c.fullName} — {c.hospitalNo} ({c.reasons.map((r) => r.replaceAll('_', ' ').toLowerCase()).join(', ')})
              </li>
            ))}
          </ul>
          {hardBlock ? (
            <p style={{ margin: 'var(--space-2) 0 0' }}>Open the existing record instead of registering a new one.</p>
          ) : (
            <div style={{ marginTop: 'var(--space-3)' }}>
              <label htmlFor="not-same-person" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>
                Reason this is not the same person
              </label>
              <textarea id="not-same-person" rows={2} {...register('notSamePersonConfirmationReason')} style={fieldStyle} />
              <button type="button" className="btn btn-danger" style={{ marginTop: 'var(--space-2)' }} onClick={onSubmit}>
                Confirm not the same person and register
              </button>
            </div>
          )}
        </div>
      )}

      <fieldset style={fieldsetStyle}>
        <legend style={legendStyle}>Identity</legend>
        <FormRow label="Given name" error={errors.givenName?.message}>
          <input {...register('givenName')} style={fieldStyle} />
        </FormRow>
        <FormRow label="Middle name (optional)">
          <input {...register('middleName')} style={fieldStyle} />
        </FormRow>
        <FormRow label="Family name" error={errors.familyName?.message}>
          <input {...register('familyName')} style={fieldStyle} />
        </FormRow>
        <div style={{ display: 'flex', gap: 'var(--space-4)' }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)', fontSize: 'var(--text-sm)' }}>
            <input type="radio" value="exact" {...register('dobKnown')} /> Exact date of birth
          </label>
          <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)', fontSize: 'var(--text-sm)' }}>
            <input type="radio" value="estimated" {...register('dobKnown')} /> Estimated age
          </label>
        </div>
        {dobKnown === 'exact' ? (
          <FormRow label="Date of birth" error={errors.dateOfBirth?.message}>
            <input type="date" {...register('dateOfBirth')} style={fieldStyle} />
          </FormRow>
        ) : (
          <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
            <FormRow label="Age (years)" error={errors.ageYears?.message}>
              <input type="number" min={0} {...register('ageYears')} style={fieldStyle} />
            </FormRow>
            <FormRow label="Months (optional)">
              <input type="number" min={0} max={11} {...register('ageMonths')} style={fieldStyle} />
            </FormRow>
          </div>
        )}
        <FormRow label="Gender" error={errors.gender?.message}>
          <select {...register('gender')} style={fieldStyle}>
            {genderOptions.map((g) => (
              <option key={g} value={g}>
                {g.replaceAll('_', ' ').toLowerCase()}
              </option>
            ))}
          </select>
        </FormRow>
        {!isMinor && (
          <FormRow label="Marital status (optional)">
            <select {...register('maritalStatus')} style={fieldStyle} defaultValue="">
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
          <input {...register('nationalId')} style={fieldStyle} />
        </FormRow>
      </fieldset>

      <fieldset style={fieldsetStyle}>
        <legend style={legendStyle}>Contact</legend>
        <FormRow label="Phone" error={errors.phone?.message}>
          <input {...register('phone')} style={fieldStyle} placeholder="0700000000" />
        </FormRow>
        <FormRow label="Email (optional)">
          <input type="email" {...register('email')} style={fieldStyle} />
        </FormRow>
        <FormRow label="Occupation (optional)">
          <input {...register('occupation')} style={fieldStyle} />
        </FormRow>
      </fieldset>

      <fieldset style={fieldsetStyle}>
        <legend style={legendStyle}>Next of kin</legend>
        <FormRow label="Full name" error={errors.nokFullName?.message}>
          <input {...register('nokFullName')} style={fieldStyle} />
        </FormRow>
        <FormRow label="Relationship" error={errors.nokRelationship?.message}>
          <select {...register('nokRelationship')} style={fieldStyle}>
            {relationshipOptions.map((r) => (
              <option key={r} value={r}>
                {r.toLowerCase()}
              </option>
            ))}
          </select>
        </FormRow>
        <FormRow label="Phone" error={errors.nokPhone?.message}>
          <input {...register('nokPhone')} style={fieldStyle} placeholder="0700000000" />
        </FormRow>
      </fieldset>

      <fieldset style={fieldsetStyle}>
        <legend style={legendStyle}>Current address (optional)</legend>
        <FormRow label="Region"><input {...register('currentRegion')} style={fieldStyle} /></FormRow>
        <FormRow label="District"><input {...register('currentDistrict')} style={fieldStyle} /></FormRow>
        <FormRow label="Ward"><input {...register('currentWard')} style={fieldStyle} /></FormRow>
        <FormRow label="Street or village"><input {...register('currentStreetOrVillage')} style={fieldStyle} /></FormRow>
        <FormRow label="Landmark"><input {...register('currentLandmark')} style={fieldStyle} /></FormRow>
      </fieldset>

      <fieldset style={fieldsetStyle}>
        <legend style={legendStyle}>Permanent address</legend>
        <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)', fontSize: 'var(--text-sm)', marginBottom: 'var(--space-2)' }}>
          <input type="checkbox" {...register('permanentSameAsCurrent')} /> Same as current
        </label>
        {!permanentSameAsCurrent && (
          <>
            <FormRow label="Region"><input {...register('permanentRegion')} style={fieldStyle} /></FormRow>
            <FormRow label="District"><input {...register('permanentDistrict')} style={fieldStyle} /></FormRow>
            <FormRow label="Ward"><input {...register('permanentWard')} style={fieldStyle} /></FormRow>
            <FormRow label="Street or village"><input {...register('permanentStreetOrVillage')} style={fieldStyle} /></FormRow>
            <FormRow label="Landmark"><input {...register('permanentLandmark')} style={fieldStyle} /></FormRow>
          </>
        )}
      </fieldset>

      <fieldset style={fieldsetStyle}>
        <legend style={legendStyle}>Insurance (optional)</legend>
        <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)', fontSize: 'var(--text-sm)', marginBottom: 'var(--space-2)' }}>
          <input type="checkbox" {...register('hasInsurance')} /> Patient has insurance
        </label>
        {hasInsurance && (
          <>
            <FormRow label="Scheme">
              <select {...register('schemeId')} style={fieldStyle} defaultValue="">
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
              <input {...register('policyNumber')} style={fieldStyle} />
            </FormRow>
            <FormRow label="Member number (optional)">
              <input {...register('memberNumber')} style={fieldStyle} />
            </FormRow>
            <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)', fontSize: 'var(--text-sm)' }}>
              <input
                type="checkbox"
                checked={isPrincipal}
                onChange={(e) => setValue('isPrincipal', e.target.checked)}
              />{' '}
              Patient is the principal member
            </label>
            {!isPrincipal && (
              <FormRow label="Principal member's name" error={errors.principalName?.message}>
                <input {...register('principalName')} style={fieldStyle} />
              </FormRow>
            )}
          </>
        )}
      </fieldset>

      {submit.error && !duplicateCandidates && (
        <div className="form-error">Something went wrong. Check the values and try again.</div>
      )}

      <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
        {submit.isPending ? 'Registering…' : 'Register patient'}
      </button>
    </form>
  );
}

function FormRow({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 'var(--space-2)' }}>
      <label style={{ display: 'block', fontSize: 'var(--text-sm)' }}>{label}</label>
      {children}
      {error && <span className="field-error">{error}</span>}
    </div>
  );
}

function ageFromDob(dob: string | undefined): string {
  if (!dob) return '—';
  const years = Math.floor((Date.now() - new Date(dob).getTime()) / (365.25 * 24 * 60 * 60 * 1000));
  return `${years}`;
}

function EmptyState({ message }: { message: string }) {
  return (
    <div style={{ padding: 'var(--space-10) var(--space-4)', textAlign: 'center', color: 'var(--text-muted)', background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)' }}>
      {message}
    </div>
  );
}

const cardStyle: CSSProperties = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
  borderRadius: 'var(--radius-lg)',
  padding: 'var(--space-4)',
  maxWidth: 560,
};

const fieldsetStyle: CSSProperties = { border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: 'var(--space-3)' };
const legendStyle: CSSProperties = { fontSize: 'var(--text-sm)', fontWeight: 'var(--weight-medium)', padding: '0 var(--space-2)' };

const fieldStyle: CSSProperties = {
  width: '100%',
  marginTop: 'var(--space-1)',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};
