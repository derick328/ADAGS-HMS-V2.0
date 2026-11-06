import { Radio } from '../../shared/components/Radio';
import { Checkbox } from '../../shared/components/Checkbox';
import { FormField as FormRow } from '../../shared/components/FormField';
import { FormSection } from '../../shared/components/FormSection';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Fragment, useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { admitPatient, assignNurse, type Shift, type WardClass } from '../../shared/api/admissions';
import { createFollowUpAppointment } from '../../shared/api/appointments';
import { ApiError } from '../../shared/api/client';
import { fetchNurseWorkload } from '../../shared/api/nurses';
import {
  addDiagnosis,
  autosaveConsultationRecord,
  createAllergyRecord,
  fetchAllergiesByPatient,
  fetchConsultationRecordsByEncounter,
  fetchDiagnosesByEncounter,
  finalizeConsultation,
  markAwaitingResults,
  openConsultation,
  reconcileAllergyRecord,
  type AllergyRecordResponse,
  type ConsultationRecordResponse,
  type DiagnosisCertainty,
  type DiagnosisResponse,
  type DispositionOutcome,
  type EncounterResponse,
} from '../../shared/api/clinical';
import { fetchEncounterById, fetchEncountersByPatient } from '../../shared/api/encounters';
import { recordIcd10Selection, searchIcd10, type Icd10SearchResult } from '../../shared/api/icd10';
import { searchItems, type ItemSearchResultResponse } from '../../shared/api/items';
import { fetchMedicinePackaging } from '../../shared/api/medicinePackaging';
import { fetchLabOrdersByEncounter, placeLabOrder, type LabOrderUrgency } from '../../shared/api/labOrders';
import { fetchLabTests, fetchPendingLabClassifications } from '../../shared/api/labTests';
import { fetchPatientById, type PatientResponse } from '../../shared/api/patients';
import { searchPriceCatalog, type PriceCatalogItemResponse } from '../../shared/api/priceCatalog';
import { placePrescription, type PrescriptionFrequency } from '../../shared/api/prescriptions';
import { fetchCurrentStock } from '../../shared/api/stockReports';
import { searchCompletedAssessments, type CompletedTriageAssessmentResponse } from '../../shared/api/triage';
import { fetchVitalSignsByEncounter, recordConsultationVitals, type VitalSignsResponse } from '../../shared/api/vitalSigns';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Combobox } from '../../shared/components/Combobox';
import { formatDateTime, formatWeekdayDate } from '../../shared/format/date';
import { formatMoney } from '../../shared/format/money';

const fieldStyle: CSSProperties = {
  width: '100%',
  marginTop: 'var(--space-1)',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};

// UI-only union — 'ADMIT_TO_WARD' routes through the admissions endpoint
// (which itself finalizes the consultation record); the three
// FinalizeConsultationRequest.disposition enum values go through
// finalizeConsultation() directly. 'SCHEDULE_SURGERY' is deliberately
// disabled: 07-doctor.md §7 lists it, but the surgery-order form
// (theatre, timeslot, anaesthesia — POST /api/surgery-orders) needs a
// scheduling page the doctor workspace does not yet ship. Marked
// unavailable so the doctor can see it's on the map, and can't pick a
// disposition the code silently downgrades.
type DispositionChoice = DispositionOutcome | 'ADMIT_TO_WARD' | 'SCHEDULE_SURGERY';

// Every disposition is offered here. SURGERY_ORDER_CREATE and the
// surgery-scheduling endpoints stay permission-checked server-side —
// hiding an option in the dropdown produces a dead end for anyone who
// looks for it and can't find it. V75 grants the surgery permissions
// to the DOCTOR role too so the option isn't blocked at the next page.
const DISPOSITION_OPTIONS: {
  value: DispositionChoice;
  label: string;
}[] = [
  { value: 'CONSULT_COMPLETE', label: 'Consult complete — send home' },
  { value: 'ADMIT_TO_WARD', label: 'Admit to ward' },
  { value: 'REFERRED_OUT', label: 'Referred out' },
  { value: 'DECEASED', label: 'Deceased' },
  { value: 'SCHEDULE_SURGERY', label: 'Schedule surgery' },
];

const FREQUENCY_DOSES_PER_DAY: Record<string, number | null> = { STAT: 1, OD: 1, BD: 2, TDS: 3, QDS: 4, PRN: null };
const QUANTITY_SANITY_CEILING = 200;

// Picks the shift the doctor is currently working in — a sensible
// default for the nurse-assignment shift picker on the admit form.
// 07:00–14:59 = MORNING, 15:00–21:59 = AFTERNOON, else NIGHT. Roughly
// matches Tanzanian hospital shift patterns; the doctor can override.
function currentShift(): 'MORNING' | 'AFTERNOON' | 'NIGHT' {
  const hour = new Date().getHours();
  if (hour >= 7 && hour < 15) return 'MORNING';
  if (hour >= 15 && hour < 22) return 'AFTERNOON';
  return 'NIGHT';
}

type Tab = 'consultation' | 'prescription' | 'lab';

// 07-doctor.md §2's entry guard, §3-§10, 08-doctor-navigation.md §6. "The
// core" per the build brief. On open: canOpenConsultation is enforced
// server-side (encounters/api/ConsultationController#open) — a guard
// failure here means the encounter isn't AWAITING_CONSULT/IN_CONSULT for
// this doctor, and we bounce back to the queue with the server's message
// rather than re-deciding the guard client-side.
export function ConsultationPage() {
  const { encounterId } = useParams<{ encounterId: string }>();
  const { accessToken } = useAuth();
  const navigate = useNavigate();

  const openQuery = useQuery({
    queryKey: ['consultation', 'open', encounterId],
    queryFn: () => openConsultation(encounterId as string, accessToken as string),
    enabled: accessToken != null && encounterId != null,
    retry: false,
    staleTime: Infinity,
  });

  useEffect(() => {
    if (openQuery.isError) {
      const err = openQuery.error;
      navigate('/doctor', {
        state: { consultationError: err instanceof ApiError ? err.message : 'This consultation could not be opened.' },
      });
    }
  }, [openQuery.isError, openQuery.error, navigate]);

  if (openQuery.isLoading || !openQuery.data) {
    return <p style={{ color: 'var(--text-muted)' }}>Opening consultation…</p>;
  }

  return (
    <ConsultationWorkspace
      encounterId={encounterId as string}
      consultationRecordId={openQuery.data.consultationRecordId as string}
      initialEncounter={openQuery.data.encounter!}
    />
  );
}

function ConsultationWorkspace({
  encounterId,
  consultationRecordId,
  initialEncounter,
}: {
  encounterId: string;
  consultationRecordId: string;
  initialEncounter: EncounterResponse;
}) {
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>('consultation');
  const isEmergency = initialEncounter.type === 'EMERGENCY';

  const { data: encounter } = useQuery({
    queryKey: ['encounters', encounterId],
    queryFn: () => fetchEncounterById(encounterId, accessToken as string),
    enabled: accessToken != null,
    initialData: initialEncounter,
    staleTime: 30_000,
  });

  const { data: patient } = useQuery({
    queryKey: ['patients', encounter?.patientId],
    queryFn: () => fetchPatientById(encounter!.patientId as string, accessToken as string),
    enabled: accessToken != null && !!encounter?.patientId,
  });

  const { data: triageWindow } = useQuery({
    queryKey: ['triage-assessments', 'window'],
    queryFn: () =>
      searchCompletedAssessments(
        { assessedFrom: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(), assessedTo: new Date().toISOString() },
        accessToken as string,
      ),
    enabled: accessToken != null,
    staleTime: 60_000,
  });
  const triage: CompletedTriageAssessmentResponse | undefined = triageWindow?.find((t) => t.encounterId === encounterId);

  const { data: vitalsList } = useQuery({
    queryKey: ['vital-signs', encounterId],
    queryFn: () => fetchVitalSignsByEncounter(encounterId, accessToken as string),
    enabled: accessToken != null,
  });
  // The "Triage summary" panel always shows the TRIAGE reading — a doctor's
  // own consultation re-measure must never overwrite it on screen (it is
  // shown separately in the Record-vitals card). Falls back to the newest
  // reading only when no triage set exists.
  const vitals =
    vitalsList?.find((v) => v.context === 'TRIAGE') ??
    vitalsList?.[0];

  const { data: allergies } = useQuery({
    queryKey: ['allergies', encounter?.patientId],
    queryFn: () => fetchAllergiesByPatient(encounter!.patientId as string, accessToken as string),
    enabled: accessToken != null && !!encounter?.patientId,
  });

  const { data: records } = useQuery({
    queryKey: ['consultation-records', 'by-encounter', encounterId],
    queryFn: () => fetchConsultationRecordsByEncounter(encounterId, accessToken as string),
    enabled: accessToken != null,
    staleTime: 10_000,
  });
  const ownRecord = records?.find((r) => r.id === consultationRecordId);

  const { data: diagnoses } = useQuery({
    queryKey: ['diagnoses', encounterId],
    queryFn: () => fetchDiagnosesByEncounter(encounterId, accessToken as string),
    enabled: accessToken != null,
  });

  const patientAge = ageFromDob(patient?.dateOfBirth);

  // §6.4 Send button state, shared by both order tabs.
  const [rxLines, setRxLines] = useState<DraftRxLine[]>([]);
  const [labLines, setLabLines] = useState<DraftLabLine[]>([]);
  const runningTotalMinor = rxLines.reduce((sum, l) => sum + (l.priceMinor ?? 0) * l.quantity, 0) +
    labLines.reduce((sum, l) => sum + (l.priceMinor ?? 0), 0);

  return (
    <div>
      <ConsultationHeader patient={patient} encounter={encounter} />

      <div className="consultation-layout">
        <aside className="consultation-summary">
          <TriagePanel triage={triage} vitals={vitals} />
          <RecordVitalsCard encounterId={encounterId} />
          <PreviousEncountersPanel patientId={encounter?.patientId} currentEncounterId={encounterId} />
        </aside>

        <div style={{ flex: 1, minWidth: 0 }}>
          <TabBar
            tab={tab}
            onChange={setTab}
            rxTotalMinor={rxLines.reduce((sum, l) => sum + (l.priceMinor ?? 0) * l.quantity, 0)}
            labTotalMinor={labLines.reduce((sum, l) => sum + (l.priceMinor ?? 0), 0)}
            grandTotalMinor={runningTotalMinor}
            suppressTotal={isEmergency}
          />

          {tab === 'consultation' && (
            <ConsultationTab
              encounterId={encounterId}
              consultationRecordId={consultationRecordId}
              ownRecord={ownRecord}
              allergies={allergies ?? []}
              diagnoses={diagnoses ?? []}
              patientId={encounter?.patientId as string}
              onFinalized={() => navigate('/doctor')}
            />
          )}
          {tab === 'prescription' && (
            <PrescriptionTab
              encounterId={encounterId}
              encounter={encounter}
              allergies={allergies ?? []}
              patientAge={patientAge}
              triageWeightKg={vitals?.weightKg}
              lines={rxLines}
              setLines={setRxLines}
              queryClient={queryClient}
            />
          )}
          {tab === 'lab' && <LabTab encounterId={encounterId} encounter={encounter} lines={labLines} setLines={setLabLines} />}
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Header + left panel
// ---------------------------------------------------------------------------

function ConsultationHeader({
  patient,
  encounter,
}: {
  patient?: PatientResponse;
  encounter?: EncounterResponse;
}) {
  const age = ageFromDob(patient?.dateOfBirth);
  return (
    <PageHeader title={<>{patient?.fullName ?? 'Loading…'}</>}><div style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)', marginTop: 4 }}>
        {patient?.hospitalNo} · {age != null ? `${age}${patient?.gender?.[0] ?? ''}` : '—'} · {encounter?.payerType}
        {encounter?.type === 'EMERGENCY' && (
          <span className="badge badge-critical" style={{ marginLeft: 8 }}>
            EMERGENCY
          </span>
        )}
      </div></PageHeader>
  );
}

function TriagePanel({
  triage,
  vitals,
}: {
  triage?: CompletedTriageAssessmentResponse;
  vitals?: VitalSignsResponse;
}) {
  return (
    <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-3)' }}>
      <h2 style={{ fontSize: 'var(--text-sm)', textTransform: 'uppercase', color: 'var(--text-muted)', marginTop: 0 }}>
        Triage summary
      </h2>
      {vitals && (
        <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)', marginBottom: 4 }}>
          {vitals.context === 'CONSULTATION'
            ? 'Recorded at consultation'
            : vitals.context === 'TRIAGE'
              ? 'Recorded at triage'
              : 'Latest recorded'}
          {vitals.occurredAt ? ` · ${formatDateTime(vitals.occurredAt)}` : ''}
        </div>
      )}
      {vitals ? (
        <VitalsReadings vitals={vitals} />
      ) : (
        <FeedbackState>No vitals recorded.</FeedbackState>
      )}

      <hr style={{ border: 'none', borderTop: '1px solid var(--border)', margin: 'var(--space-3) 0' }} />

      {/* 07-doctor.md §5.1 — triage context inherited read-only. All of these
          are already fetched on the triage assessment; the doctor needs to see
          the nurse's chief complaint, symptoms and comorbidities while writing
          the note, not just the vitals. */}
      <div style={{ fontSize: 'var(--text-sm)', display: 'flex', flexDirection: 'column', gap: 4 }}>
        <div>
          <strong>Chief complaint:</strong> {triage?.chiefComplaint || '—'}
        </div>
        <div>
          <strong>Current symptoms:</strong> {triage?.currentSymptoms || '—'}
        </div>
        <div>
          <strong>Comorbidities:</strong> {triage?.comorbidities || '—'}
        </div>
        <div>
          <strong>Priority:</strong> {triage?.priority ? triage.priority.replaceAll('_', ' ').toLowerCase() : '—'}
        </div>
        <div>
          <strong>Mode of ambulation:</strong>{' '}
          {triage?.modeOfAmbulation ? triage.modeOfAmbulation.replaceAll('_', ' ').toLowerCase() : '—'}
        </div>
      </div>

      <hr style={{ border: 'none', borderTop: '1px solid var(--border)', margin: 'var(--space-3) 0' }} />

      {/* 07-doctor.md §5.2 — pain/fall/infection/pregnancy as recorded at
          triage. Shown read-only here ("triage's original stands"); a formal
          doctor re-assessment is a new record, out of this view's scope. */}
      <div style={{ fontSize: 'var(--text-sm)' }}>
        <div style={{ fontWeight: 'var(--weight-medium)', textTransform: 'uppercase', color: 'var(--text-muted)', fontSize: 'var(--text-xs)' }}>
          Pain &amp; risk assessment
        </div>
        <div style={{ marginTop: 4 }}>
          <strong>Pain:</strong>{' '}
          {triage?.hasPain
            ? `Yes${triage.painScore != null ? ` (${triage.painScore}/10)` : ''}${triage.painLocation ? ` — ${triage.painLocation}` : ''}`
            : 'No'}
        </div>
        <div style={{ marginTop: 4 }}>
          <strong>Fall risk:</strong>{' '}
          {triage?.fallRisk ? `Yes${triage.fallScore != null ? ` (score ${triage.fallScore})` : ''}` : 'No'}
        </div>
        <div style={{ marginTop: 4 }}>
          <strong>Infectious disease risk:</strong> {triage?.infectionRisk ? 'Yes' : 'No'}
        </div>
        {triage?.pregnancyStatus && (
          <div style={{ marginTop: 4 }}>
            <strong>Pregnancy:</strong> {triage.pregnancyStatus.toLowerCase()}
            {triage.gestationalWeeks != null ? ` · ${triage.gestationalWeeks} weeks` : ''}
          </div>
        )}
      </div>

      {/* root CLAUDE.md "Safety": allergies render inline and unmissable, never collapsed. */}
      <div style={{ marginTop: 'var(--space-3)' }}>
        <div style={{ fontSize: 'var(--text-sm)', fontWeight: 'var(--weight-medium)', color: 'var(--critical)' }}>Allergies</div>
        <TriagePanelAllergies triageAllergiesText={triage?.allergies} />
      </div>
    </div>
  );
}

// Renders the recorded vitals, flagging any reading the backend marked
// out-of-range for the patient's age band (abnormalVitalTypes, populated by
// VitalReferenceRangeEvaluator at record time). Matches EncounterDetailPage's
// vitals block exactly — a warning banner plus per-reading orange value and an
// "out of range" badge. Colour is never the only signal (frontend/CLAUDE.md
// accessibility): the ⚠ icon, the badge text, and role="alert" carry it too.
// Reading keys match the VitalType enum names (see VitalSignsController).
function VitalsReadings({ vitals }: { vitals: VitalSignsResponse }) {
  const abnormal = new Set(vitals.abnormalVitalTypes ?? []);
  const rows: { label: string; value: string; abnormalKeys?: string[] }[] = [
    { label: 'Temp', value: `${vitals.readings?.TEMPERATURE ?? '—'} °C`, abnormalKeys: ['TEMPERATURE'] },
    {
      label: 'BP',
      value: `${vitals.readings?.SYSTOLIC_BP ?? '—'}/${vitals.readings?.DIASTOLIC_BP ?? '—'}`,
      abnormalKeys: ['SYSTOLIC_BP', 'DIASTOLIC_BP'],
    },
    { label: 'Pulse', value: `${vitals.readings?.PULSE ?? '—'} bpm`, abnormalKeys: ['PULSE'] },
    { label: 'SpO₂', value: `${vitals.readings?.SPO2 ?? '—'}%`, abnormalKeys: ['SPO2'] },
    ...(vitals.readings?.RESPIRATORY_RATE != null
      ? [{ label: 'Resp. rate', value: `${vitals.readings.RESPIRATORY_RATE}/min`, abnormalKeys: ['RESPIRATORY_RATE'] }]
      : []),
    ...(vitals.weightKg != null || vitals.heightCm != null
      ? [{ label: 'Weight / height', value: `${vitals.weightKg != null ? `${vitals.weightKg}kg` : '—'} / ${vitals.heightCm != null ? `${vitals.heightCm}cm` : '—'}` }]
      : []),
    ...(vitals.bmi != null ? [{ label: 'BMI', value: vitals.bmi.toFixed(1) }] : []),
  ];
  const abnormalLabels = rows.filter((r) => (r.abnormalKeys ?? []).some((k) => abnormal.has(k))).map((r) => r.label);

  return (
    <>
      {abnormalLabels.length > 0 && (
        <div
          role="alert"
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: 'var(--space-2)',
            padding: 'var(--space-2)',
            marginBottom: 'var(--space-2)',
            border: '1px solid var(--warning)',
            background: 'var(--warning-bg)',
            borderRadius: 'var(--radius-sm)',
            color: 'var(--warning)',
            fontSize: 'var(--text-sm)',
          }}
        >
          <span aria-hidden="true" style={{ fontWeight: 'var(--weight-bold)' }}>⚠</span>
          <span>
            <strong>{abnormalLabels.length}</strong> vital {abnormalLabels.length === 1 ? 'sign is' : 'signs are'} outside the
            reference range for this patient&rsquo;s age band: <strong>{abnormalLabels.join(', ')}</strong>.
          </span>
        </div>
      )}
      <dl className="clinical-grid" style={{ fontSize: 'var(--text-sm)', display: 'grid', gridTemplateColumns: '1fr auto', gap: '2px 8px', margin: 0 }}>
        {rows.map((row) => {
          const isAbnormal = (row.abnormalKeys ?? []).some((k) => abnormal.has(k));
          return (
            <Fragment key={row.label}>
              <dt>{row.label}</dt>
              <dd className="tabular" style={{ margin: 0, display: 'flex', gap: 'var(--space-1)', alignItems: 'center', justifyContent: 'flex-end' }}>
                <span style={isAbnormal ? { color: 'var(--warning)', fontWeight: 'var(--weight-medium)' } : undefined}>{row.value}</span>
                {isAbnormal && (
                  <span className="badge badge-warning" title="Outside the reference range for this patient's age band" aria-label="Out of range">
                    out of range
                  </span>
                )}
              </dd>
            </Fragment>
          );
        })}
      </dl>
    </>
  );
}

function TriagePanelAllergies({ triageAllergiesText }: { triageAllergiesText?: string }) {
  if (!triageAllergiesText) {
    return <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>None recorded at triage.</div>;
  }
  return (
    <div
      style={{
        marginTop: 4,
        padding: 'var(--space-2)',
        background: 'var(--critical-bg)',
        color: 'var(--critical)',
        borderRadius: 'var(--radius-sm)',
        fontSize: 'var(--text-sm)',
      }}
    >
      {triageAllergiesText}
    </div>
  );
}

// 07-doctor.md §5.1: recording vitals during consultation is optional — triage
// usually captures them — but a doctor may re-measure. Permission-gated on
// VITALS_CREATE (the DOCTOR role holds it; the server re-checks, scoped to the
// encounter's department). Posts context=CONSULTATION (pinned server-side) and
// invalidates the vitals query so the panel above reflects the new reading.
// Optional-blank numeric fields are stripped to undefined rather than coerced
// to 0 — "not recorded" and "zero" are different clinical statements.
const optionalNumber = z.preprocess(
  (v) => (v === '' || v == null ? undefined : v),
  z.coerce.number().optional(),
);
// 07-doctor.md §5.1: every field is optional — the doctor records only what
// they actually measured. The form just requires that at least one reading is
// present (an empty submission is a mistake, mirrored by a server-side guard
// in RecordVitalSignsService).
const vitalsSchema = z
  .object({
    temperatureC: optionalNumber,
    systolicMmHg: optionalNumber,
    diastolicMmHg: optionalNumber,
    pulseBpm: optionalNumber,
    respiratoryRate: optionalNumber,
    spo2Pct: optionalNumber,
    weightKg: optionalNumber,
    heightCm: optionalNumber,
    avpu: z.enum(['ALERT', 'VOICE', 'PAIN', 'UNRESPONSIVE']).optional().or(z.literal('')),
    bloodGlucoseMmol: optionalNumber,
    painScore: z.preprocess(
      (v) => (v === '' || v == null ? undefined : v),
      z.coerce.number().int().min(0).max(10).optional(),
    ),
  })
  .refine(
    (v) =>
      [
        v.temperatureC,
        v.systolicMmHg,
        v.diastolicMmHg,
        v.pulseBpm,
        v.respiratoryRate,
        v.spo2Pct,
        v.weightKg,
        v.heightCm,
        v.bloodGlucoseMmol,
        v.painScore,
      ].some((n) => n != null) || (v.avpu !== undefined && v.avpu !== ''),
    { message: 'Record at least one vital sign.', path: ['temperatureC'] },
  );
type VitalsValues = z.infer<typeof vitalsSchema>;

function RecordVitalsCard({ encounterId }: { encounterId: string }) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const canRecord = usePermission('VITALS_CREATE');
  const [showForm, setShowForm] = useState(false);

  // The doctor's own consultation readings, shown here (newest first) so a
  // recording is visibly confirmed — kept separate from the Triage summary so
  // it never overwrites the triage reading.
  const { data: vitalsList } = useQuery({
    queryKey: ['vital-signs', encounterId],
    queryFn: () => fetchVitalSignsByEncounter(encounterId, accessToken as string),
    enabled: accessToken != null,
  });
  const latestConsultationVitals = vitalsList?.find((v) => v.context === 'CONSULTATION');

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<VitalsValues>({ resolver: zodResolver(vitalsSchema) });

  const submit = useMutation({
    mutationFn: (values: VitalsValues) =>
      recordConsultationVitals(
        encounterId,
        {
          temperatureC: values.temperatureC,
          systolicMmHg: values.systolicMmHg,
          diastolicMmHg: values.diastolicMmHg,
          pulseBpm: values.pulseBpm,
          respiratoryRate: values.respiratoryRate,
          spo2Pct: values.spo2Pct,
          weightKg: values.weightKg,
          heightCm: values.heightCm,
          avpu: values.avpu ? values.avpu : undefined,
          bloodGlucoseMmol: values.bloodGlucoseMmol,
          painScore: values.painScore,
          occurredAt: new Date().toISOString(),
        },
        accessToken as string,
      ),
    onSuccess: () => {
      reset();
      setShowForm(false);
      queryClient.invalidateQueries({ queryKey: ['vital-signs', encounterId] });
    },
  });

  if (!canRecord) return null;

  return (
    <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-3)' }}>
      <h2 style={{ fontSize: 'var(--text-sm)', textTransform: 'uppercase', color: 'var(--text-muted)', marginTop: 0 }}>
        Vital signs
      </h2>

      {latestConsultationVitals && !showForm && (
        <div style={{ marginBottom: 'var(--space-3)' }}>
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)', marginBottom: 4 }}>
            Recorded at consultation
            {latestConsultationVitals.occurredAt ? ` · ${formatDateTime(latestConsultationVitals.occurredAt)}` : ''}
          </div>
          <VitalsReadings vitals={latestConsultationVitals} />
        </div>
      )}

      <button type="button" className="btn btn-secondary" onClick={() => setShowForm((v) => !v)} style={{ width: '100%' }}>
        {showForm ? 'Cancel' : latestConsultationVitals ? 'Record again' : 'Record vitals'}
      </button>

      {showForm && (
        <form
          onSubmit={handleSubmit((v) => submit.mutate(v))}
          noValidate
          style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-2)', marginTop: 'var(--space-3)' }}
        >
          <VitalField label="Temp (°C)" error={errors.temperatureC?.message}>
            <input className="clinical-control" type="text" inputMode="decimal" {...register('temperatureC')} style={vitalInputStyle} />
          </VitalField>
          <VitalField label="Pulse (bpm)" error={errors.pulseBpm?.message}>
            <input className="clinical-control" type="text" inputMode="decimal" {...register('pulseBpm')} style={vitalInputStyle} />
          </VitalField>
          <VitalField label="Systolic (mmHg)" error={errors.systolicMmHg?.message}>
            <input className="clinical-control" type="text" inputMode="decimal" {...register('systolicMmHg')} style={vitalInputStyle} />
          </VitalField>
          <VitalField label="Diastolic (mmHg)" error={errors.diastolicMmHg?.message}>
            <input className="clinical-control" type="text" inputMode="decimal" {...register('diastolicMmHg')} style={vitalInputStyle} />
          </VitalField>
          <VitalField label="Resp. rate" error={errors.respiratoryRate?.message}>
            <input className="clinical-control" type="text" inputMode="decimal" {...register('respiratoryRate')} style={vitalInputStyle} />
          </VitalField>
          <VitalField label="SpO₂ (%)" error={errors.spo2Pct?.message}>
            <input className="clinical-control" type="text" inputMode="decimal" {...register('spo2Pct')} style={vitalInputStyle} />
          </VitalField>
          <VitalField label="Weight (kg)" error={errors.weightKg?.message}>
            <input className="clinical-control" type="text" inputMode="decimal" {...register('weightKg')} style={vitalInputStyle} />
          </VitalField>
          <VitalField label="Height (cm)" error={errors.heightCm?.message}>
            <input className="clinical-control" type="text" inputMode="decimal" {...register('heightCm')} style={vitalInputStyle} />
          </VitalField>
          <VitalField label="AVPU">
            <select className="clinical-control" {...register('avpu')} style={vitalInputStyle}>
              <option value="">—</option>
              <option value="ALERT">Alert</option>
              <option value="VOICE">Voice</option>
              <option value="PAIN">Pain</option>
              <option value="UNRESPONSIVE">Unresponsive</option>
            </select>
          </VitalField>
          <VitalField label="Glucose (mmol/L)">
            <input className="clinical-control" type="text" inputMode="decimal" {...register('bloodGlucoseMmol')} style={vitalInputStyle} />
          </VitalField>
          <VitalField label="Pain (0–10)" error={errors.painScore?.message}>
            <input className="clinical-control" type="text" inputMode="numeric" {...register('painScore')} style={vitalInputStyle} />
          </VitalField>

          <div style={{ gridColumn: '1 / -1' }}>
            {submit.isError && <div className="form-error" style={{ marginBottom: 'var(--space-2)' }}>{errorMessage(submit.error)}</div>}
            <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ width: '100%' }}>
              {submit.isPending ? 'Saving…' : 'Save vitals'}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

function VitalField({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <label style={{ fontSize: 'var(--text-xs)', color: 'var(--text-secondary)' }}>
      {label}
      {children}
      {error && <div className="field-error">{error}</div>}
    </label>
  );
}

const vitalInputStyle: CSSProperties = {
  display: 'block',
  width: '100%',
  marginTop: 2,
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};

// The patient's prior encounters, surfaced alongside the live consultation so
// the doctor sees earlier triage and consultation visits without leaving the
// screen (08-doctor-navigation.md §3 — the Patient page carries the full
// detail; this is the in-consult shortcut to it). The current encounter is
// excluded; each row links to that encounter's full record (triage, notes,
// diagnoses). Gated on PATIENT_READ_VISIT_HISTORY — the same permission the
// doctor's patient-history nav uses.
function PreviousEncountersPanel({
  patientId,
  currentEncounterId,
}: {
  patientId?: string;
  currentEncounterId: string;
}) {
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const canSee = usePermission('PATIENT_READ_VISIT_HISTORY');

  const { data: encounters, isLoading } = useQuery({
    queryKey: ['encounters', 'by-patient', patientId],
    queryFn: () => fetchEncountersByPatient(patientId as string, accessToken as string),
    enabled: accessToken != null && patientId != null && canSee,
    staleTime: 60_000,
  });

  if (!canSee || !patientId) {
    return null;
  }

  const prior = [...(encounters ?? [])]
    .filter((e) => e.id !== currentEncounterId && e.state !== 'CANCELLED_IN_ERROR' && (!encounters?.find(c => c.id === currentEncounterId)?.createdAt || (e.createdAt ?? '') <= encounters!.find(c => c.id === currentEncounterId)!.createdAt!))
    .sort((a, b) => new Date(b.createdAt as string).getTime() - new Date(a.createdAt as string).getTime());

  return (
    <details
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-3)',
      }}
    >
      <summary style={{ fontSize: 'var(--text-sm)', textTransform: 'uppercase', color: 'var(--text-muted)', marginTop: 0 }}>
        Previous clinical history · {prior.length} earlier encounters
      </summary>
      {isLoading ? (
        <FeedbackState loading>Loading…</FeedbackState>
      ) : prior.length === 0 ? (
        <FeedbackState>No earlier visits on record.</FeedbackState>
      ) : (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
          {prior.map((e) => (
            <li key={e.id}>
              <button
                type="button"
                className="workspace-name"
                onClick={() => navigate(`/doctor/patients/${patientId}/encounters/${e.id}`)}
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 2, width: '100%', textAlign: 'left' }}
              >
                <span style={{ fontWeight: 'var(--weight-medium)', fontSize: 'var(--text-sm)' }}>{formatWeekdayDate(e.createdAt)}</span>
                <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
                  {(e.type ?? '—').toString().toLowerCase()} · {(e.state ?? '').replaceAll('_', ' ').toLowerCase()}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </details>
  );
}

// ---------------------------------------------------------------------------
// Tab bar with running total — 08-doctor-navigation.md §6.1, suppressed for EMERGENCY.
// ---------------------------------------------------------------------------

function TabBar({
  tab,
  onChange,
  rxTotalMinor,
  labTotalMinor,
  grandTotalMinor,
  suppressTotal,
}: {
  tab: Tab;
  onChange: (t: Tab) => void;
  rxTotalMinor: number;
  labTotalMinor: number;
  grandTotalMinor: number;
  suppressTotal: boolean;
}) {
  const tabDef: { key: Tab; label: string }[] = [
    { key: 'consultation', label: 'Consultation' },
    { key: 'prescription', label: suppressTotal ? 'Prescription' : `Prescription · ${formatMoney(rxTotalMinor, 'TZS')}` },
    { key: 'lab', label: suppressTotal ? 'Lab Requests' : `Lab Requests · ${formatMoney(labTotalMinor, 'TZS')}` },
  ];
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-3)', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid var(--border)', marginBottom: 'var(--space-4)' }}>
      <nav aria-label="Consultation sections" className="queue-filters">
        {tabDef.map((t) => (
          <button
            key={t.key}
            onClick={() => onChange(t.key)}
            type="button"
            className="queue-filter"
            aria-pressed={tab === t.key}
          >
            {t.label}
          </button>
        ))}
      </nav>
      {!suppressTotal && (
        <div style={{ fontWeight: 'var(--weight-medium)' }}>Total {formatMoney(grandTotalMinor, 'TZS')}</div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Consultation tab
// ---------------------------------------------------------------------------

interface ConsultationFormValues {
  historyOfPresentingIllness: string;
  pastMedicalHistory: string;
  pastSurgicalHistory: string;
  comorbidities: string;
  familySocialHistory: string;
  physicalExamination: string;
  clinicalNotes: string;
  treatmentPlan: string;
}

function ConsultationTab({
  encounterId,
  consultationRecordId,
  ownRecord,
  allergies,
  diagnoses,
  patientId,
  onFinalized,
}: {
  encounterId: string;
  consultationRecordId: string;
  ownRecord?: ConsultationRecordResponse;
  allergies: AllergyRecordResponse[];
  diagnoses: DiagnosisResponse[];
  patientId: string;
  onFinalized: () => void;
}) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const [values, setValues] = useState<ConsultationFormValues>({
    historyOfPresentingIllness: '',
    pastMedicalHistory: '',
    pastSurgicalHistory: '',
    comorbidities: '',
    familySocialHistory: '',
    physicalExamination: '',
    clinicalNotes: '',
    treatmentPlan: '',
  });
  // Read by the autosave interval (below) instead of `values` directly, so
  // that effect's dependency list doesn't change on every keystroke — a
  // setInterval whose *effect* is torn down and rebuilt every edit never
  // survives a full 20s window during continuous typing, silently
  // defeating "autosave every 20s" (backend/CLAUDE.md §10).
  const valuesRef = useRef(values);
  useEffect(() => {
    valuesRef.current = values;
  }, [values]);
  // Set the moment the doctor edits a field, before the hydration fetch
  // may have resolved — guards against the hydration effect below
  // overwriting text already typed with the (still-empty, for a new
  // record) server response.
  const touchedRef = useRef(false);

  const [hydrated, setHydrated] = useState(false);
  useEffect(() => {
    if (hydrated || !ownRecord) return;
    if (!touchedRef.current) {
      setValues({
        historyOfPresentingIllness: ownRecord.historyOfPresentingIllness ?? '',
        pastMedicalHistory: ownRecord.pastMedicalHistory ?? '',
        pastSurgicalHistory: ownRecord.pastSurgicalHistory ?? '',
        comorbidities: ownRecord.comorbidities ?? '',
        familySocialHistory: ownRecord.familySocialHistory ?? '',
        physicalExamination: ownRecord.physicalExamination ?? '',
        clinicalNotes: ownRecord.clinicalNotes ?? '',
        treatmentPlan: ownRecord.treatmentPlan ?? '',
      });
    }
    setHydrated(true);
  }, [ownRecord, hydrated]);

  const autosave = useMutation({
    mutationFn: (v: ConsultationFormValues) => autosaveConsultationRecord(consultationRecordId, v, accessToken as string),
  });
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(null);
  const [autosaveFailed, setAutosaveFailed] = useState(false);

  const triggerAutosave = useCallback(() => {
    autosave.mutate(valuesRef.current, {
      onSuccess: () => {
        setLastSavedAt(new Date());
        setAutosaveFailed(false);
      },
      onError: () => setAutosaveFailed(true),
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // §10: autosave every 20s, keyed to (encounter_id, author_id) server-side.
  // A stable triggerAutosave (reading valuesRef, not `values`, above) means
  // this effect only re-runs on the hydrated flag flipping, not per edit.
  useEffect(() => {
    if (!hydrated) return;
    const interval = setInterval(triggerAutosave, 20_000);
    return () => clearInterval(interval);
  }, [hydrated, triggerAutosave]);

  function field(key: keyof ConsultationFormValues) {
    return {
      value: values[key],
      onChange: (e: React.ChangeEvent<HTMLTextAreaElement>) => {
        touchedRef.current = true;
        setValues((v) => ({ ...v, [key]: e.target.value }));
      },
    };
  }

  // Diagnosis picker
  const [clarifier, setClarifier] = useState('');
  const [certainty, setCertainty] = useState<DiagnosisCertainty>('PROVISIONAL');
  const [isPrimary, setIsPrimary] = useState(diagnoses.length === 0);
  // The diagnoses query is still empty on first render (fetch in flight),
  // so the useState initializer above can default to `true` for an
  // encounter that already has a primary diagnosis. Re-derive once the
  // real list is in — keyed on length, not identity, so it doesn't
  // override a manual toggle while diagnoses.length is unchanged.
  useEffect(() => {
    setIsPrimary(diagnoses.length === 0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [diagnoses.length]);
  const [selectedCode, setSelectedCode] = useState<Icd10SearchResult | null>(null);
  const latestIcd10SearchLogId = useRef<string | null>(null);

  // §4/§5.3 — a provisional diagnosis is often confirmed only once lab results
  // return. When the doctor flags that confirmation is pending results, block
  // adding a Final (confirmed) diagnosis until this encounter actually has a
  // released lab result. "Results returned" is a real signal, not a guess: a
  // lab order reaches status COMPLETED (LabOrderController#toHistoryResponse)
  // only when a result has been released against it.
  const canReadLabs = usePermission('LAB_RESULT_READ');
  const { data: labOrders } = useQuery({
    queryKey: ['lab-orders', 'by-encounter', encounterId],
    queryFn: () => fetchLabOrdersByEncounter(encounterId, accessToken as string),
    enabled: accessToken != null && canReadLabs,
    staleTime: 30_000,
  });
  const labResultsReturned = (labOrders ?? []).some((o) => o.status === 'COMPLETED');
  const [pendingLabConfirmation, setPendingLabConfirmation] = useState(false);
  const finalBlockedByLab = pendingLabConfirmation && !labResultsReturned;
  // If the gate flips on while "Final (confirmed)" is selected, drop back to
  // Provisional so the certainty picker can never submit a blocked value.
  useEffect(() => {
    if (finalBlockedByLab && certainty === 'CONFIRMED') {
      setSelectedCode(null);
      setCertainty('PROVISIONAL');
    }
  }, [finalBlockedByLab, certainty]);

  const addDiagnosisMutation = useMutation({
    mutationFn: () =>
      addDiagnosis(
        consultationRecordId,
        { icd10Code: selectedCode!.code as string, freeTextClarifier: clarifier || undefined, certainty, isPrimary },
        accessToken as string,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['diagnoses', encounterId] });
      setSelectedCode(null);
      setClarifier('');
      setIsPrimary(false);
    },
  });

  // Disposition + finalize
  const [disposition, setDisposition] = useState<DispositionChoice | ''>('');
  const [wardClass, setWardClass] = useState<WardClass>('GENERAL');
  const [nurseId, setNurseId] = useState<string>('');
  const [shift, setShift] = useState<Shift>(currentShift());
  const [followUpDate, setFollowUpDate] = useState('');
  const [followUpInstructions, setFollowUpInstructions] = useState('');
  const [retrospective, setRetrospective] = useState(false);
  const [retrospectiveNote, setRetrospectiveNote] = useState('');

  // The encounter query the parent already ran is cached under this same
  // key — this hits the cache and returns instantly, no re-fetch.
  const { data: encounterForFacility } = useQuery({
    queryKey: ['encounters', encounterId],
    queryFn: () => fetchEncounterById(encounterId, accessToken as string),
    enabled: accessToken != null,
  });
  const facilityId = encounterForFacility?.facilityId;

  // Nurse workload — only fetched once the doctor picks Admit-to-ward
  // (nurse picker is hidden otherwise, no reason to hit the endpoint).
  // Refetches every 30s so a stale count doesn't cause the doctor to
  // over-assign a nurse who's just picked up a patient elsewhere.
  const {
    data: nurses,
    isLoading: nursesLoading,
    isError: nursesError,
    error: nursesErrorValue,
  } = useQuery({
    queryKey: ['nurses', 'workload', facilityId],
    queryFn: () => fetchNurseWorkload(facilityId as string, accessToken as string),
    enabled: accessToken != null && disposition === 'ADMIT_TO_WARD' && !!facilityId,
    refetchInterval: 30_000,
  });

  const finalizeMutation = useMutation({
    // §7.2: follow-up is optional, doctor-only booking, created alongside
    // finalize rather than as a separate action — the date/instructions
    // fields on this screen only mean something if they actually create
    // the appointment the receptionist's read-only Follow-ups list shows.
    //
    // Admit-to-ward is a distinct backend action (POST /api/admissions),
    // not a value of the FinalizeConsultationRequest enum: the admission
    // service does the encounter transition and the consultation
    // finalization itself, in one transaction — calling finalize
    // separately would double-transition. Optional nurse assignment chains
    // after admit: it's not part of admitPatient's own request shape.
    mutationFn: async () => {
      if (disposition === 'ADMIT_TO_WARD') {
        const admission = await admitPatient(
          {
            encounterId,
            orderedWardClass: wardClass,
            retrospectiveCompletion: retrospective,
            retrospectiveNote: retrospective ? retrospectiveNote : undefined,
          },
          accessToken as string,
        );
        if (nurseId && admission.id) {
          await assignNurse(
            admission.id,
            {
              nurseId,
              shift,
              assignmentDate: new Date().toISOString().slice(0, 10),
            },
            accessToken as string,
          );
        }
      } else {
        await finalizeConsultation(
          encounterId,
          { disposition: disposition as DispositionOutcome, retrospectiveCompletion: retrospective, retrospectiveNote: retrospective ? retrospectiveNote : undefined },
          accessToken as string,
        );
      }
      if (followUpDate) {
        await createFollowUpAppointment(
          {
            patientId,
            sourceEncounterId: encounterId,
            scheduledAt: new Date(`${followUpDate}T09:00:00`).toISOString(),
            notes: followUpInstructions || undefined,
          },
          accessToken as string,
        );
      }
    },
    onSuccess: () => onFinalized(),
  });

  const markAwaitingResultsMutation = useMutation({
    mutationFn: () => markAwaitingResults(encounterId, accessToken as string),
    onSuccess: () => onFinalized(),
  });

  const requiredMissing =
    !values.historyOfPresentingIllness.trim() || !values.physicalExamination.trim() || !values.treatmentPlan.trim();
  const canFinalize = !requiredMissing && diagnoses.some((d) => d.isPrimary) && disposition !== '' &&
    (!retrospective || retrospectiveNote.trim().length >= 20);
  const blockersRef = useRef<HTMLParagraphElement | null>(null);
  function attemptFinalize(action: () => void) {
    if (!canFinalize) {
      blockersRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      return;
    }
    action();
  }

  // Human-readable list of what's still missing — surfaced under the
  // finalize / surgery button so a doctor never sees a disabled button
  // without knowing which section to go back to. Matches the exact
  // guards in canFinalize above so the list can never disagree with the
  // enabled state.
  const finalizeBlockers = (() => {
    const missing: string[] = [];
    if (!values.historyOfPresentingIllness.trim()) missing.push('History of presenting illness');
    if (!values.physicalExamination.trim()) missing.push('Physical examination');
    if (!values.treatmentPlan.trim()) missing.push('Treatment plan');
    if (!diagnoses.some((d) => d.isPrimary)) missing.push('A diagnosis marked Primary');
    if (disposition === '') missing.push('Select a disposition');
    if (retrospective && retrospectiveNote.trim().length < 20) missing.push('Retrospective note (min 20 characters)');
    return missing;
  })();

  return (
    <div className="consultation-form">
      <AllergyReconciliation patientId={patientId} encounterId={encounterId} allergies={allergies} />

      <FormSection title="Consultation note">
        <FormRow label="History of presenting illness *">
          <textarea className="clinical-control" rows={4} {...field('historyOfPresentingIllness')} />
        </FormRow>
        <FormRow label="Past medical history">
          <textarea className="clinical-control" rows={2} {...field('pastMedicalHistory')} />
        </FormRow>
        <FormRow label="Past surgical history">
          <textarea className="clinical-control" rows={2} {...field('pastSurgicalHistory')} />
        </FormRow>
        <FormRow label="Comorbidities">
          <textarea className="clinical-control" rows={2} {...field('comorbidities')} placeholder="e.g. hypertension, type 2 diabetes, HIV" />
        </FormRow>
        <FormRow label="Family & social history">
          <textarea className="clinical-control" rows={2} {...field('familySocialHistory')} placeholder="Family history, smoking, alcohol, occupation, living situation" />
        </FormRow>
        <FormRow label="Physical examination *">
          <textarea className="clinical-control" rows={4} {...field('physicalExamination')} />
        </FormRow>
        <FormRow label="Clinical notes">
          <textarea className="clinical-control" rows={3} {...field('clinicalNotes')} />
        </FormRow>
      </FormSection>

      <FormSection title="Diagnosis">
        <div className="form-field">
          <label>Provisional diagnosis *</label>
          <DiagnosisGroup items={diagnoses.filter((d) => d.certainty === 'PROVISIONAL')} emptyHint="Search and add a provisional diagnosis below." />
          <Combobox<Icd10SearchResult>
            label="Add provisional diagnosis"
            placeholder="Search by disease title or ICD-10 code…"
            search={(q) => searchIcd10(q, undefined, accessToken as string).then((r) => {
              latestIcd10SearchLogId.current = r.searchLogId ?? null;
              return r.results ?? [];
            })}
            getKey={(o) => o.code as string}
            renderOption={(o) => <span>{o.title} <strong>{o.code}</strong></span>}
            onSelect={(o) => {
              setSelectedCode(o);
              setCertainty('PROVISIONAL');
              if (latestIcd10SearchLogId.current && o.code) recordIcd10Selection(latestIcd10SearchLogId.current, o.code, accessToken as string);
            }}
          />
        </div>
        {diagnoses.some((d) => d.certainty === 'PROVISIONAL') && (
          <div className="form-field">
            <Checkbox
              className="clinical-choice"
              checked={pendingLabConfirmation}
              onChange={(e) => setPendingLabConfirmation(e.target.checked)}
              label={<>Confirmation pending lab results</>}
            />
            {pendingLabConfirmation && (
              <p style={{ margin: '4px 0 0', fontSize: 'var(--text-sm)', color: labResultsReturned ? 'var(--green-600)' : 'var(--text-muted)' }}>
                {labResultsReturned
                  ? 'Lab results have returned for this encounter — you can now add a final (confirmed) diagnosis.'
                  : 'A final (confirmed) diagnosis is blocked until lab results return for this encounter.'}
              </p>
            )}
          </div>
        )}
        <div className="form-field">
          <label>Final diagnosis</label>
          <DiagnosisGroup
            items={diagnoses.filter((d) => d.certainty === 'CONFIRMED')}
            emptyHint={
              finalBlockedByLab
                ? 'Awaiting lab results — confirmation is on hold (uncheck the box above to override).'
                : 'Search and add a final diagnosis below.'
            }
          />
          <Combobox<Icd10SearchResult>
            label="Add final diagnosis"
            placeholder="Search by disease title or ICD-10 code…"
            disabled={finalBlockedByLab}
            search={(q) => searchIcd10(q, undefined, accessToken as string).then((r) => {
              latestIcd10SearchLogId.current = r.searchLogId ?? null;
              return r.results ?? [];
            })}
            getKey={(o) => o.code as string}
            renderOption={(o) => <span>{o.title} <strong>{o.code}</strong></span>}
            onSelect={(o) => {
              setSelectedCode(o);
              setCertainty('CONFIRMED');
              if (latestIcd10SearchLogId.current && o.code) recordIcd10Selection(latestIcd10SearchLogId.current, o.code, accessToken as string);
            }}
          />
        </div>
        <div className="form-field">
          <label>Ruled out</label>
          <DiagnosisGroup items={diagnoses.filter((d) => d.certainty === 'RULED_OUT')} />
          <Combobox<Icd10SearchResult>
            label="Add ruled-out diagnosis"
            placeholder="Search by disease title or ICD-10 code…"
            search={(q) => searchIcd10(q, undefined, accessToken as string).then((r) => {
              latestIcd10SearchLogId.current = r.searchLogId ?? null;
              return r.results ?? [];
            })}
            getKey={(o) => o.code as string}
            renderOption={(o) => <span>{o.title} <strong>{o.code}</strong></span>}
            onSelect={(o) => {
              setSelectedCode(o);
              setCertainty('RULED_OUT');
              if (latestIcd10SearchLogId.current && o.code) recordIcd10Selection(latestIcd10SearchLogId.current, o.code, accessToken as string);
            }}
          />
        </div>
        {selectedCode && (
          <div style={{ marginTop: 'var(--space-2)', display: 'flex', gap: 'var(--space-2)', alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <div>
              {certainty === 'PROVISIONAL' ? 'Provisional' : certainty === 'CONFIRMED' ? 'Final' : 'Ruled out'}: {selectedCode.title} <strong>({selectedCode.code})</strong>
            </div>
            <input aria-label="Free-text clarifier" className="clinical-control"
              placeholder="Free-text clarifier"
              value={clarifier}
              onChange={(e) => setClarifier(e.target.value)}
              style={{ ...fieldStyle, width: 240 }}
            />
            <Checkbox  className="clinical-choice" checked={isPrimary} onChange={(e) => setIsPrimary(e.target.checked)} label={<>Primary</>} />
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => addDiagnosisMutation.mutate()}
              disabled={addDiagnosisMutation.isPending || (certainty === 'CONFIRMED' && finalBlockedByLab)}
            >
              Add {certainty === 'PROVISIONAL' ? 'provisional' : certainty === 'CONFIRMED' ? 'final' : 'ruled-out'} diagnosis
            </button>
          </div>
        )}
        {addDiagnosisMutation.isError && (
          <p className="form-error">{errorMessage(addDiagnosisMutation.error)}</p>
        )}
      </FormSection>

      <FormSection title="Treatment & disposition">
        <FormRow label="Treatment plan *">
          <textarea className="clinical-control" rows={4} {...field('treatmentPlan')} />
        </FormRow>
        <FormRow label="Disposition">
          <select className="clinical-control" value={disposition} onChange={(e) => setDisposition(e.target.value as DispositionChoice)}>
            <option value="">Select disposition…</option>
            {DISPOSITION_OPTIONS.map((o, i) => (
              <option key={i} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </FormRow>
        {disposition === 'ADMIT_TO_WARD' && (
          <div style={{ marginTop: 'var(--space-2)', display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            <div>
              <label style={{ display: 'block', fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginBottom: 4 }}>
                Ward class
              </label>
              <select className="clinical-control" value={wardClass} onChange={(e) => setWardClass(e.target.value as WardClass)} style={{ ...fieldStyle, width: 220 }}>
                <option value="GENERAL">General</option>
                <option value="PRIVATE">Private</option>
              </select>
              <p style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)', marginTop: 4 }}>
                A ward nurse will assign a specific bed after admission.
              </p>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginBottom: 4 }}>
                Assign primary nurse (optional) · current active-patient count shown after each name
              </label>
              {nursesLoading ? (
                <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>Loading nurses…</span>
              ) : nursesError ? (
                <span style={{ fontSize: 'var(--text-sm)', color: 'var(--critical)' }}>
                  {nursesErrorValue instanceof ApiError ? nursesErrorValue.message : "Couldn't load the nurse workload — the ward nurse can assign someone later."}
                </span>
              ) : (nurses ?? []).length === 0 ? (
                <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
                  No ward nurses configured for this facility yet.
                </span>
              ) : (
                <select className="clinical-control" value={nurseId} onChange={(e) => setNurseId(e.target.value)} style={{ ...fieldStyle, width: 360 }}>
                  <option value="">Leave unassigned — a ward nurse will pick up the patient</option>
                  {(nurses ?? []).map((n) => (
                    <option key={n.nurseId} value={n.nurseId as string}>
                      {n.username} — {n.activePatients} {n.activePatients === 1 ? 'patient' : 'patients'}
                      {(n.activePatients ?? 0) >= 5 ? ' ⚠ heavy load' : ''}
                    </option>
                  ))}
                </select>
              )}
            </div>

            {nurseId && (
              <div>
                <label style={{ display: 'block', fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginBottom: 4 }}>
                  Shift
                </label>
                <select className="clinical-control" value={shift} onChange={(e) => setShift(e.target.value as Shift)} style={{ ...fieldStyle, width: 220 }}>
                  <option value="MORNING">Morning</option>
                  <option value="AFTERNOON">Afternoon</option>
                  <option value="NIGHT">Night</option>
                </select>
                <p style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)', marginTop: 4 }}>
                  Today, {new Date().toLocaleDateString()}. Care responsibility for this shift; the ward roster covers subsequent shifts.
                </p>
              </div>
            )}
          </div>
        )}
        <FormRow label="Follow-up appointment (optional)">
          <div style={{ display: 'flex', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
            <input aria-label="Follow-up date" className="clinical-control" type="date" value={followUpDate} onChange={(e) => setFollowUpDate(e.target.value)} style={{ flex: '0 0 auto' }} />
            <input aria-label="Follow-up instructions" className="clinical-control"
              placeholder="Follow-up instructions"
              value={followUpInstructions}
              onChange={(e) => setFollowUpInstructions(e.target.value)}
              style={{ flex: '1 1 220px' }}
            />
          </div>
        </FormRow>

        <Checkbox  className="clinical-choice" checked={retrospective} onChange={(e) => setRetrospective(e.target.checked)} label={<>This is a retrospective completion (patient was seen earlier)</>} />
        {retrospective && (
          <textarea className="clinical-control"
            rows={2}
            placeholder="What this note is based on (memory, paper notes, the nurse's record) — minimum 20 characters"
            value={retrospectiveNote}
            onChange={(e) => setRetrospectiveNote(e.target.value)}
          />
        )}
      </FormSection>

      <div className="form-actions">
        <button type="button" className="btn btn-secondary" onClick={triggerAutosave}>
          Save draft
        </button>
        {autosaveFailed ? (
          <span style={{ fontSize: 'var(--text-sm)', color: 'var(--critical)' }}>Draft not saved — check your connection</span>
        ) : (
          lastSavedAt && <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>Saved {formatDateTime(lastSavedAt.toISOString())}</span>
        )}
        <button type="button" className="btn btn-ghost" onClick={() => markAwaitingResultsMutation.mutate()}>
          Mark awaiting results
        </button>
        <div style={{ flex: 1 }} />
        {disposition === 'SCHEDULE_SURGERY' ? (
          // Surgery scheduling is a full booking (theatre / slot / anaesthesia
          // / consent) that lives on its own page — clicking here jumps to
          // ScheduleSurgeryPage, which schedules the surgery AND finalizes
          // this consultation on submit. Same disabled rule as Finalize
          // (missing sections / no primary diagnosis / retrospective note
          // too short) so the doctor can't sneak past the guard by picking
          // this disposition.
          <button
            type="button"
            className="btn btn-primary"
            aria-disabled={!canFinalize}
            onClick={() => attemptFinalize(() => navigate(`/doctor/schedule-surgery/${encounterId}`))}
          >
            Continue to surgery scheduling →
          </button>
        ) : (
          <button
            type="button"
            className="btn btn-primary"
            aria-disabled={!canFinalize}
            disabled={finalizeMutation.isPending}
            onClick={() => attemptFinalize(() => finalizeMutation.mutate())}
          >
            Finalize
          </button>
        )}
      </div>
      {!canFinalize && finalizeBlockers.length > 0 && (
        <p
          ref={blockersRef}
          role="status"
          style={{
            margin: 0,
            fontSize: 'var(--text-sm)',
            color: 'var(--text-muted)',
            padding: 'var(--space-2) var(--space-3)',
            background: 'var(--bg-subtle)',
            borderLeft: '3px solid var(--warning)',
            borderRadius: 'var(--radius-sm)',
          }}
        >
          {disposition === 'SCHEDULE_SURGERY'
            ? 'Complete the consultation before continuing to surgery scheduling:'
            : 'Finalize needs:'}{' '}
          <strong>{finalizeBlockers.join('; ')}</strong>.
        </p>
      )}
      {finalizeMutation.isError && <p className="form-error">{errorMessage(finalizeMutation.error)}</p>}
    </div>
  );
}

// Renders one certainty group of the diagnosis list (provisional / final /
// ruled out). Empty groups show a muted hint rather than nothing, so the
// doctor sees the field exists and what fills it.
function DiagnosisGroup({ items, emptyHint }: { items: DiagnosisResponse[]; emptyHint?: string }) {
  if (items.length === 0) {
    return <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>{emptyHint ?? '—'}</p>;
  }
  return (
    <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
      {items.map((d) => (
        <li key={d.id} style={{ padding: 'var(--space-2) var(--space-3)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', background: 'var(--bg-surface)' }}>
          <strong>{d.icd10Code}</strong> {d.icd10TitleSnapshot}{' '}
          {d.isPrimary && <span className="badge badge-signal">Primary</span>}
          {d.freeTextClarifier && <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>{d.freeTextClarifier}</div>}
        </li>
      ))}
    </ul>
  );
}

function AllergyReconciliation({
  patientId,
  encounterId,
  allergies,
}: {
  patientId: string;
  encounterId: string;
  allergies: AllergyRecordResponse[];
}) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [refutingId, setRefutingId] = useState<string | null>(null);
  const [note, setNote] = useState('');
  const [newDescription, setNewDescription] = useState('');
  const [newReaction, setNewReaction] = useState('');

  const reconcile = useMutation({
    mutationFn: (input: { id: string; decision: 'CONFIRMED' | 'REFUTED'; confirmationNote?: string }) =>
      reconcileAllergyRecord(input.id, { decision: input.decision, confirmationNote: input.confirmationNote }, accessToken as string),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['allergies', patientId] });
      setRefutingId(null);
      setNote('');
    },
  });

  const addAllergy = useMutation({
    mutationFn: () =>
      createAllergyRecord(patientId, { encounterId, description: newDescription, reaction: newReaction || undefined }, accessToken as string),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['allergies', patientId] });
      setNewDescription('');
      setNewReaction('');
    },
  });

  return (
    <section style={{ border: '1px solid var(--warning)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-3)', background: 'var(--warning-bg)' }}>
      <h2 style={{ fontSize: 'var(--text-base)', marginTop: 0 }}>Allergies — reconcile</h2>
      {allergies.length === 0 ? (
        <FeedbackState>No allergy records for this patient.</FeedbackState>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
          {allergies.map((a) => (
            <div key={a.id} style={{ background: 'var(--bg-surface)', borderRadius: 'var(--radius)', padding: 'var(--space-2)' }}>
              <div>
                <strong>{a.description}</strong> {a.reaction && `— ${a.reaction}`}{' '}
                <span className="badge badge-warning">{a.confirmationStatus}</span>
              </div>
              <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
                {a.source} · {a.recordedByName} · {formatDateTime(a.recordedAt)}
                {a.confirmationNote && ` — "${a.confirmationNote}"`}
              </div>
              {a.confirmationStatus === 'UNCONFIRMED' && (
                <div style={{ marginTop: 4, display: 'flex', gap: 'var(--space-2)' }}>
                  <button type="button" className="btn btn-secondary" onClick={() => reconcile.mutate({ id: a.id as string, decision: 'CONFIRMED' })}>
                    Confirm
                  </button>
                  <button type="button" className="btn btn-secondary" onClick={() => setRefutingId(a.id as string)}>
                    Refute
                  </button>
                </div>
              )}
              {refutingId === a.id && (
                <div style={{ marginTop: 4 }}>
                  <textarea className="clinical-control"
                    rows={2}
                    placeholder="Note required to refute"
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    style={fieldStyle}
                  />
                  <button
                    type="button"
                    className="btn btn-primary"
                    disabled={!note.trim()}
                    onClick={() => reconcile.mutate({ id: a.id as string, decision: 'REFUTED', confirmationNote: note })}
                    style={{ marginTop: 4 }}
                  >
                    Confirm refute
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
      <div style={{ marginTop: 'var(--space-3)', display: 'flex', gap: 'var(--space-2)' }}>
        <input aria-label="Add allergy" className="clinical-control" placeholder="Add allergy" value={newDescription} onChange={(e) => setNewDescription(e.target.value)} style={fieldStyle} />
        <input aria-label="Reaction" className="clinical-control" placeholder="Reaction" value={newReaction} onChange={(e) => setNewReaction(e.target.value)} style={fieldStyle} />
        <button type="button" className="btn btn-secondary" disabled={!newDescription.trim()} onClick={() => addAllergy.mutate()}>
          + Add
        </button>
      </div>
    </section>
  );
}

// ---------------------------------------------------------------------------
// Prescription tab
// ---------------------------------------------------------------------------

interface DraftRxLine {
  key: string;
  item: ItemSearchResultResponse;
  priceMinor: number | null;
  quantity: number;
  requestedQuantity: number;
  requestedUnit: string;
  dosageInstructions: string;
  route: string;
  doseAmount?: number;
  doseUnit?: string;
  frequency?: PrescriptionFrequency;
  durationDays?: number;
  quantityPerDose: number;
}

function PrescriptionTab({
  encounterId,
  encounter,
  allergies,
  patientAge,
  triageWeightKg,
  lines,
  setLines,
  queryClient,
}: {
  encounterId: string;
  encounter?: { type?: string };
  allergies: AllergyRecordResponse[];
  patientAge: number | null;
  triageWeightKg?: number;
  lines: DraftRxLine[];
  setLines: React.Dispatch<React.SetStateAction<DraftRxLine[]>>;
  queryClient: ReturnType<typeof useQueryClient>;
}) {
  const { accessToken } = useAuth();
  // Populated by each Combobox search, read by onSelect — the item and
  // price-catalog searches share the same typed text, so this is a cache
  // of the last search's results, not cross-component state.
  const latestPriceByCode = useRef<Record<string, PriceCatalogItemResponse>>({});
  const priceSearchRequestId = useRef(0);
  const [pending, setPending] = useState<ItemSearchResultResponse | null>(null);
  const [priceMinor, setPriceMinor] = useState<number | null>(null);
  const [mode, setMode] = useState<'whole' | 'structured'>('whole');
  const [wholeQuantity, setWholeQuantity] = useState(1);
  const [quantityPerDose, setQuantityPerDose] = useState(1);
  const [frequency, setFrequency] = useState<PrescriptionFrequency>('OD');
  const [durationDays, setDurationDays] = useState(3);
  const [dosageInstructions, setDosageInstructions] = useState('');
  const [route, setRoute] = useState('Oral');
  const [selectedUnit, setSelectedUnit] = useState('');
  const [sendResult, setSendResult] = useState<string | null>(null);

  const { data: stock } = useQuery({
    queryKey: ['stock-reports', 'current-stock'],
    queryFn: () => fetchCurrentStock(accessToken as string),
    enabled: accessToken != null,
    staleTime: 30_000,
  });

  const { data: medicinePackages } = useQuery({
    queryKey: ['medicine-packaging', pending?.id],
    queryFn: () => fetchMedicinePackaging(pending?.id as string, accessToken as string),
    enabled: accessToken != null && pending?.id != null,
  });
  const stockByItem = useMemo(() => {
    const map: Record<string, number> = {};
    (stock ?? []).forEach((row) => {
      if (!row.itemId) return;
      map[row.itemId] = (map[row.itemId] ?? 0) + (row.quantity ?? 0);
    });
    return map;
  }, [stock]);

  function stockStatus(itemId?: string): { label: string; className: string } {
    const qty = itemId ? stockByItem[itemId] ?? 0 : 0;
    if (qty <= 0) return { label: 'Out of stock', className: 'badge-critical' };
    if (qty < 10) return { label: `Low stock — ${qty}`, className: 'badge-warning' };
    return { label: `In stock — ${qty}`, className: 'badge-success' };
  }

  const dosesPerDay = FREQUENCY_DOSES_PER_DAY[frequency];
  const requestedQuantity =
    mode === 'whole' ? wholeQuantity : dosesPerDay != null ? quantityPerDose * dosesPerDay * durationDays : wholeQuantity;
  const baseUnit = pending?.unit || 'unit';
  const effectiveUnit = mode === 'structured' ? baseUnit : selectedUnit || baseUnit;
  const selectedPackage = medicinePackages?.find((p) => p.unit === effectiveUnit);
  const conversionFactor = effectiveUnit.toUpperCase() === baseUnit.toUpperCase() ? 1 : selectedPackage?.factor ?? 0;
  const computedQuantity = requestedQuantity * conversionFactor;
  const overCeiling = computedQuantity > QUANTITY_SANITY_CEILING;

  function addLine() {
    if (!pending || conversionFactor <= 0 || !Number.isSafeInteger(computedQuantity) || computedQuantity <= 0) return;
    setLines((prev) => [
      ...prev,
      {
        key: `${pending.id}-${Date.now()}`,
        item: pending,
        priceMinor,
        quantity: computedQuantity,
        requestedQuantity,
        requestedUnit: effectiveUnit,
        dosageInstructions: dosageInstructions || `${route}, ${mode === 'whole' ? `${wholeQuantity} total` : `${frequency} x${durationDays}d`}`,
        route,
        doseAmount: mode === 'structured' ? quantityPerDose : undefined,
        doseUnit: mode === 'structured' ? 'unit' : undefined,
        frequency: mode === 'structured' ? frequency : undefined,
        durationDays: mode === 'structured' ? durationDays : undefined,
        quantityPerDose,
      },
    ]);
    setPending(null);
    setPriceMinor(null);
    setDosageInstructions('');
  }

  const send = useMutation({
    // Removes each line from state as it's placed, not just once the whole
    // batch succeeds — a mid-list failure (network blip, a controlled-item
    // block on one drug) must not leave already-placed lines in the retry
    // queue, or clicking "Send" again duplicates them.
    mutationFn: async () => {
      for (const line of [...lines]) {
        await placePrescription(
          {
            encounterId,
            itemId: line.item.id as string,
            quantityPrescribed: line.quantity,
            requestedQuantity: line.requestedQuantity,
            requestedUnit: line.requestedUnit,
            dosageInstructions: line.dosageInstructions,
            route: line.route,
            doseAmount: line.doseAmount,
            doseUnit: line.doseUnit,
            frequency: line.frequency,
            durationDays: line.durationDays,
          },
          accessToken as string,
        );
        setLines((prev) => prev.filter((x) => x.key !== line.key));
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['prescriptions', 'mine'] });
      setSendResult(
        encounter?.type === 'EMERGENCY'
          ? 'Emergency bypass — pharmacy can dispense now. Charges added to the emergency balance.'
          : 'Patient must pay at reception before pharmacy dispenses.',
      );
    },
  });

  return (
    <div>
      <div style={{ marginBottom: 'var(--space-3)' }}>
        <Combobox<ItemSearchResultResponse>
          label="Drug"
          placeholder="Search formulary…"
          search={async (q) => {
            const requestId = ++priceSearchRequestId.current;
            const [items, prices] = await Promise.all([
              searchItems(q, accessToken as string),
              searchPriceCatalog(q, encounterId, accessToken as string),
            ]);
            // Only the most recently *issued* search may write the price
            // map — otherwise a slower earlier response (e.g. "para"
            // resolving after a fast-typed "amox") can overwrite the
            // price map for what's currently on screen with a stale one.
            if (requestId === priceSearchRequestId.current) {
              latestPriceByCode.current = Object.fromEntries(prices.map((p) => [p.code, p]));
            }
            return items;
          }}
          getKey={(o) => o.id as string}
          renderOption={(o) => (
            <span style={{ opacity: o.selectable === false ? 0.5 : 1 }}>
              {o.name} {o.strength} {o.form}
              {o.selectable === false && <em> — {o.unselectableReason}</em>}
            </span>
          )}
          isOptionDisabled={(o) => o.selectable === false}
          onSelect={(o) => {
            setPending(o);
            setSelectedUnit(o.unit ?? '');
            setPriceMinor(latestPriceByCode.current[o.code as string]?.priceMinor ?? null);
          }}
        />
      </div>

      {/* root CLAUDE.md "Safety": allergy records inline, adjacent to the drug field, never collapsed. */}
      <AllergyInlineWarning allergies={allergies} />

      {pending && (
        <div style={{ border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: 'var(--space-3)', marginBottom: 'var(--space-3)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', marginBottom: 'var(--space-2)' }}>
            <strong>{pending.name}</strong>
            <span className={`badge ${stockStatus(pending.id).className}`}>{stockStatus(pending.id).label}</span>
            {priceMinor != null && <span>{formatMoney(priceMinor, 'TZS')}</span>}
          </div>

          {patientAge != null && patientAge < 18 && (
            <div style={{ fontSize: 'var(--text-sm)', color: 'var(--info)', marginBottom: 'var(--space-2)' }}>
              Paediatric patient — triage weight {triageWeightKg != null ? `${triageWeightKg} kg` : 'not recorded'}
            </div>
          )}

          <Radio  className="clinical-choice" checked={mode === 'whole'} onChange={() => setMode('whole')} label={<>Dispense as whole</>} />
          <Radio  className="clinical-choice" checked={mode === 'structured'} onChange={() => setMode('structured')} label={<>Structured dosing</>} />

          {mode === 'whole' ? (
            <div className="clinical-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-2)' }}>
              <FormRow label="Requested quantity">
                <input className="clinical-control" type="number" min={1} value={wholeQuantity} onChange={(e) => setWholeQuantity(Number(e.target.value))} style={fieldStyle} />
              </FormRow>
              <FormRow label="Requested unit">
                <select className="clinical-control" value={effectiveUnit} onChange={(e) => setSelectedUnit(e.target.value)} style={fieldStyle}>
                  <option value={baseUnit}>{baseUnit}</option>
                  {(medicinePackages ?? []).map((p) => <option key={p.id} value={p.unit}>{p.unit}</option>)}
                </select>
              </FormRow>
            </div>
          ) : (
            <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-2)', flexWrap: 'wrap' }}>
              <FormRow label="Quantity per dose">
                <input className="clinical-control" type="number" min={1} value={quantityPerDose} onChange={(e) => setQuantityPerDose(Number(e.target.value))} style={fieldStyle} />
              </FormRow>
              <FormRow label="Frequency">
                <select className="clinical-control" value={frequency} onChange={(e) => setFrequency(e.target.value as PrescriptionFrequency)} style={fieldStyle}>
                  {(['STAT', 'OD', 'BD', 'TDS', 'QDS', 'PRN'] as PrescriptionFrequency[]).map((f) => (
                    <option key={f} value={f}>
                      {f}
                    </option>
                  ))}
                </select>
              </FormRow>
              {dosesPerDay != null ? (
                <FormRow label="Days">
                  <input className="clinical-control" type="number" min={1} value={durationDays} onChange={(e) => setDurationDays(Number(e.target.value))} style={fieldStyle} />
                </FormRow>
              ) : (
                <FormRow label="Total quantity (PRN — no fixed schedule)">
                  <input className="clinical-control" type="number" min={1} value={wholeQuantity} onChange={(e) => setWholeQuantity(Number(e.target.value))} style={fieldStyle} />
                </FormRow>
              )}
              <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)', alignSelf: 'center' }}>
                = {computedQuantity} {baseUnit}
              </div>
            </div>
          )}
          <p style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
            {requestedQuantity} {effectiveUnit} = {computedQuantity} {baseUnit}
          </p>
          {overCeiling && <p className="field-error">Quantity looks unusually high for one item — double-check before adding.</p>}

          <FormRow label="Route / instructions">
            <input className="clinical-control" value={route} onChange={(e) => setRoute(e.target.value)} style={{ ...fieldStyle, marginBottom: 'var(--space-2)' }} />
            <textarea className="clinical-control" rows={2} value={dosageInstructions} onChange={(e) => setDosageInstructions(e.target.value)} style={fieldStyle} placeholder="Dosage instructions" />
          </FormRow>

          <button type="button" className="btn btn-primary" onClick={addLine} disabled={conversionFactor <= 0 || overCeiling} style={{ marginTop: 'var(--space-2)' }}>
            Add to prescription
          </button>
          <p style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)', marginTop: 'var(--space-2)' }}>
            Adds this drug to the list below. You'll send the whole list at the end with <strong>Send Prescription</strong>.
          </p>
        </div>
      )}

      <TableRegion><table className="table">
        <tbody>
          {lines.map((l) => (
            <tr key={l.key}>
              <td>
                {l.item.name} — {l.requestedQuantity} {l.requestedUnit} ({l.quantity} {l.item.unit ?? 'base units'})
                <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>{l.dosageInstructions}</div>
              </td>
              <td className="money">{l.priceMinor != null ? formatMoney(l.priceMinor * l.quantity, 'TZS') : '—'}</td>
              <td>
                <button
                  type="button"
                  className="btn btn-ghost"
                  aria-label={`Remove ${l.item.name}`}
                  title="Remove this line before sending"
                  onClick={() => setLines((prev) => prev.filter((x) => x.key !== l.key))}
                  style={{ color: 'var(--critical)', fontWeight: 'var(--weight-medium)' }}
                >
                  ✕ Remove
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table></TableRegion>

      <button type="button" className="btn btn-primary" disabled={lines.length === 0 || send.isPending} onClick={() => send.mutate()} style={{ marginTop: 'var(--space-3)' }}>
        {send.isPending ? 'Sending…' : 'Send Prescription'}
      </button>
      {lines.length === 0 && (
        <p style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)', marginTop: 'var(--space-2)' }}>
          {pending
            ? "Click 'Add to prescription' above to add this drug — then Send Prescription becomes clickable."
            : 'Search a drug above and click Add to prescription before sending.'}
        </p>
      )}
      {sendResult && <p style={{ color: 'var(--green-800)' }}>✓ Sent · {lines.length} items. {sendResult}</p>}
      {send.isError && <p className="form-error">{errorMessage(send.error)}</p>}
    </div>
  );
}

function AllergyInlineWarning({ allergies }: { allergies: AllergyRecordResponse[] }) {
  if (allergies.length === 0) {
    return <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)', marginBottom: 'var(--space-3)' }}>No known allergies recorded.</div>;
  }
  return (
    <div
      style={{
        background: 'var(--critical-bg)',
        color: 'var(--critical)',
        borderRadius: 'var(--radius)',
        padding: 'var(--space-2) var(--space-3)',
        marginBottom: 'var(--space-3)',
        fontWeight: 'var(--weight-medium)',
      }}
    >
      ⚠ Allergies:{' '}
      {allergies
        .map((a) => {
          const detail = [a.reaction, a.severity].filter(Boolean).join(', ');
          return `${a.description}${detail ? ` — ${detail}` : ''} (${a.confirmationStatus})`;
        })
        .join('; ')}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Lab tab
// ---------------------------------------------------------------------------

interface DraftLabLine {
  key: string;
  test: PriceCatalogItemResponse;
  urgency: LabOrderUrgency;
  instructions: string;
  clinicalIndication: string;
  priceMinor: number | null;
}

// Discriminated union for the lab-test Combobox: orderable rows come
// from the price catalogue (real lab_test row + resolved price);
// pending rows come from the admin importer (V67 service_item TYPE=
// Laboratory) that no lab_test classifies yet. Same visible-but-
// unselectable UX the prescription typeahead uses for CONTROLLED /
// not-yet-active items.
type LabTestOption =
  | { kind: 'orderable'; serviceItemId: string; test: PriceCatalogItemResponse }
  | { kind: 'pending'; serviceItemId: string; code: string; name: string; unselectableReason: string };

function LabTab({
  encounterId,
  encounter,
  lines,
  setLines,
}: {
  encounterId: string;
  encounter?: { type?: string };
  lines: DraftLabLine[];
  setLines: React.Dispatch<React.SetStateAction<DraftLabLine[]>>;
}) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [pending, setPending] = useState<PriceCatalogItemResponse | null>(null);
  const [urgency, setUrgency] = useState<LabOrderUrgency>('ROUTINE');
  const [instructions, setInstructions] = useState('');
  const [clinicalIndication, setClinicalIndication] = useState('');
  const [sendResult, setSendResult] = useState<string | null>(null);

  function addLine() {
    if (!pending) return;
    setLines((prev) => [
      ...prev,
      { key: `${pending.serviceItemId}-${Date.now()}`, test: pending, urgency, instructions, clinicalIndication, priceMinor: pending.priceMinor ?? null },
    ]);
    setPending(null);
    setInstructions('');
    setClinicalIndication('');
  }

  const send = useMutation({
    // Same incremental-removal reasoning as PrescriptionTab's send — a
    // mid-list failure must not leave already-placed lines re-sendable.
    mutationFn: async () => {
      for (const line of [...lines]) {
        await placeLabOrder(
          { encounterId, labTestCode: line.test.code as string, clinicalIndication: line.clinicalIndication || undefined, specialInstructions: line.instructions || undefined, urgency: line.urgency },
          accessToken as string,
        );
        setLines((prev) => prev.filter((x) => x.key !== line.key));
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['lab-orders', 'mine'] });
      setSendResult(
        encounter?.type === 'EMERGENCY'
          ? 'Emergency bypass — lab can collect now. Charges added to the emergency balance.'
          : 'Patient must pay at reception before the lab proceeds.',
      );
    },
  });

  return (
    <div>
      <Combobox<LabTestOption>
        label="Lab test"
        placeholder="Search catalogue…"
        search={async (q) => {
          // Ordering only works when a matching lab_test row exists —
          // PlaceLabOrderService rejects any code that doesn't
          // (NoSuchElementException → 404 "Resource not found"). Fetch
          // the active lab_test list once and use it as the "orderable"
          // whitelist; a price-catalogue row not in the whitelist is
          // rendered as a Pending placeholder, unselectable, same
          // pattern the CONTROLLED / not-yet-active prescription case
          // uses. Every side-fetch is wrapped so a single failure never
          // rejects Promise.all and blanks the typeahead.
          const [priced, placeholders, activeLabTests] = await Promise.all([
            searchPriceCatalog(q, encounterId, accessToken as string).then((r) => r.filter((x) => x.category === 'LAB')),
            fetchPendingLabClassifications(q, accessToken as string).catch(() => []),
            fetchLabTests(accessToken as string).catch(() => []),
          ]);
          const orderableCodes = new Set(activeLabTests.filter((t) => t.active !== false).map((t) => t.code));
          const orderable: LabTestOption[] = priced
            .filter((p) => orderableCodes.has(p.code as string))
            .map((p) => ({ kind: 'orderable', test: p, serviceItemId: p.serviceItemId as string }));
          const priceCatalogWithoutLabTestRow = priced
            .filter((p) => !orderableCodes.has(p.code as string))
            .map<LabTestOption>((p) => ({
              kind: 'pending',
              serviceItemId: p.serviceItemId as string,
              code: p.code as string,
              name: p.name as string,
              unselectableReason:
                'Pending lab classification — a lab lead must set specimen type, container and turnaround time before this can be ordered.',
            }));
          // Placeholders from the admin importer (V67 service_item type=
          // Laboratory) that aren't already covered by a price-catalogue
          // row — same "pending classification" surface.
          const shownCodes = new Set<string>([
            ...priced.map((p) => p.code as string),
          ]);
          const importedPending: LabTestOption[] = placeholders
            .filter((p) => !shownCodes.has(p.code))
            .map((p) => ({
              kind: 'pending',
              serviceItemId: p.serviceItemId,
              code: p.code,
              name: p.name,
              unselectableReason: p.unselectableReason,
            }));
          return [...orderable, ...priceCatalogWithoutLabTestRow, ...importedPending];
        }}
        getKey={(o) => o.serviceItemId}
        renderOption={(o) =>
          o.kind === 'orderable' ? (
            <span>
              {o.test.name} — {formatMoney(o.test.priceMinor, o.test.currencyCode)}
            </span>
          ) : (
            <span style={{ opacity: 0.5 }}>
              {o.name} <em>— {o.unselectableReason}</em>
            </span>
          )
        }
        isOptionDisabled={(o) => o.kind !== 'orderable'}
        onSelect={(o) => {
          if (o.kind === 'orderable') setPending(o.test);
        }}
      />

      {pending && (
        <div style={{ border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: 'var(--space-3)', margin: 'var(--space-3) 0' }}>
          <strong>{pending.name}</strong> — {formatMoney(pending.priceMinor, pending.currencyCode)}
          <FormRow label="Urgency">
            <select className="clinical-control" value={urgency} onChange={(e) => setUrgency(e.target.value as LabOrderUrgency)} style={fieldStyle}>
              <option value="ROUTINE">Routine</option>
              <option value="URGENT">Urgent</option>
              <option value="STAT">STAT</option>
            </select>
          </FormRow>
          <FormRow label="Clinical indication">
            <textarea className="clinical-control" rows={2} value={clinicalIndication} onChange={(e) => setClinicalIndication(e.target.value)} style={fieldStyle} />
          </FormRow>
          <FormRow label="Special instructions">
            <textarea className="clinical-control" rows={2} value={instructions} onChange={(e) => setInstructions(e.target.value)} style={fieldStyle} />
          </FormRow>
          <button type="button" className="btn btn-secondary" onClick={addLine} style={{ marginTop: 'var(--space-2)' }}>
            Add to request
          </button>
        </div>
      )}

      <TableRegion><table className="table">
        <tbody>
          {lines.map((l) => (
            <tr key={l.key}>
              <td>
                {l.test.name} <span className="badge badge-neutral">{l.urgency}</span>
              </td>
              <td className="money">{l.priceMinor != null ? formatMoney(l.priceMinor, 'TZS') : '—'}</td>
              <td>
                <button
                  type="button"
                  className="btn btn-ghost"
                  aria-label={`Remove ${l.test.name}`}
                  title="Remove this line before sending"
                  onClick={() => setLines((prev) => prev.filter((x) => x.key !== l.key))}
                  style={{ color: 'var(--critical)', fontWeight: 'var(--weight-medium)' }}
                >
                  ✕ Remove
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table></TableRegion>

      <button type="button" className="btn btn-primary" disabled={lines.length === 0 || send.isPending} onClick={() => send.mutate()} style={{ marginTop: 'var(--space-3)' }}>
        {send.isPending ? 'Sending…' : 'Send Lab Requests'}
      </button>
      {lines.length === 0 && (
        <p style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)', marginTop: 'var(--space-2)' }}>
          Add at least one lab test above before sending.
        </p>
      )}
      {sendResult && <p style={{ color: 'var(--green-800)' }}>✓ Sent · {lines.length} items. {sendResult}</p>}
      {send.isError && <p className="form-error">{errorMessage(send.error)}</p>}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------


function ageFromDob(dob: string | undefined | null): number | null {
  if (!dob) return null;
  const diff = Date.now() - new Date(dob).getTime();
  return Math.floor(diff / (365.25 * 24 * 60 * 60 * 1000));
}

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong. Please try again.';
}
