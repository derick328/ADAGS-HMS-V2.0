import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { createFollowUpAppointment } from '../../shared/api/appointments';
import { ApiError } from '../../shared/api/client';
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
import { fetchEncounterById } from '../../shared/api/encounters';
import { recordIcd10Selection, searchIcd10, type Icd10SearchResult } from '../../shared/api/icd10';
import { searchItems, type ItemSearchResultResponse } from '../../shared/api/items';
import { placeLabOrder, type LabOrderUrgency } from '../../shared/api/labOrders';
import { fetchLabTests, fetchPendingLabClassifications } from '../../shared/api/labTests';
import { fetchPatientById, type PatientResponse } from '../../shared/api/patients';
import { searchPriceCatalog, type PriceCatalogItemResponse } from '../../shared/api/priceCatalog';
import { placePrescription, type PrescriptionFrequency } from '../../shared/api/prescriptions';
import { fetchCurrentStock } from '../../shared/api/stockReports';
import { searchCompletedAssessments, type CompletedTriageAssessmentResponse } from '../../shared/api/triage';
import { fetchVitalSignsByEncounter, type VitalSignsResponse } from '../../shared/api/vitalSigns';
import { useAuth } from '../../shared/auth/AuthProvider';
import { Combobox } from '../../shared/components/Combobox';
import { formatDateTime } from '../../shared/format/date';
import { formatMoney } from '../../shared/format/money';

const fieldStyle: CSSProperties = {
  width: '100%',
  marginTop: 'var(--space-1)',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};

const DISPOSITION_OPTIONS: { value: DispositionOutcome; label: string; available: boolean }[] = [
  { value: 'CONSULT_COMPLETE', label: 'Consult complete', available: true },
  { value: 'REFERRED_OUT', label: 'Referred out', available: true },
  { value: 'DECEASED', label: 'Deceased', available: true },
  { value: 'REFERRED_OUT', label: 'Referred to another doctor (not yet available)', available: false },
  { value: 'REFERRED_OUT', label: 'Referred to a specialist (not yet available)', available: false },
  { value: 'REFERRED_OUT', label: 'Admitted (not yet available)', available: false },
  { value: 'REFERRED_OUT', label: 'Schedule surgery (not yet available)', available: false },
  { value: 'REFERRED_OUT', label: 'Absconded (not yet available)', available: false },
];

const FREQUENCY_DOSES_PER_DAY: Record<string, number | null> = { STAT: 1, OD: 1, BD: 2, TDS: 3, QDS: 4, PRN: null };
const QUANTITY_SANITY_CEILING = 200;

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
  const vitals = vitalsList?.find((v) => v.context === 'TRIAGE') ?? vitalsList?.[0];

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

      <div style={{ display: 'flex', gap: 'var(--space-6)', marginTop: 'var(--space-4)' }}>
        <aside style={{ width: 280, flexShrink: 0 }}>
          <TriagePanel triage={triage} vitals={vitals} />
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
    <header>
      <h1 style={{ margin: 0, fontSize: 'var(--text-xl)' }}>{patient?.fullName ?? 'Loading…'}</h1>
      <div style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)', marginTop: 4 }}>
        {patient?.hospitalNo} · {age != null ? `${age}${patient?.gender?.[0] ?? ''}` : '—'} · {encounter?.payerType}
        {encounter?.type === 'EMERGENCY' && (
          <span className="badge badge-critical" style={{ marginLeft: 8 }}>
            EMERGENCY
          </span>
        )}
      </div>
    </header>
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
      {vitals ? (
        <dl style={{ fontSize: 'var(--text-sm)', display: 'grid', gridTemplateColumns: '1fr auto', gap: '2px 8px', margin: 0 }}>
          {/* Keys match backend VitalType enum names exactly — TEMPERATURE,
              SYSTOLIC_BP, DIASTOLIC_BP, PULSE, SPO2 (see VitalSignsController.
              toResponse). Earlier "_C / _MMHG / _BPM / _PCT" suffixes never
              matched and always resolved to undefined → every reading
              rendered as "—". */}
          <dt>Temp</dt>
          <dd className="tabular">{vitals.readings?.TEMPERATURE ?? '—'} °C</dd>
          <dt>BP</dt>
          <dd className="tabular">
            {vitals.readings?.SYSTOLIC_BP ?? '—'}/{vitals.readings?.DIASTOLIC_BP ?? '—'}
          </dd>
          <dt>Pulse</dt>
          <dd className="tabular">{vitals.readings?.PULSE ?? '—'} bpm</dd>
          <dt>SpO₂</dt>
          <dd className="tabular">{vitals.readings?.SPO2 ?? '—'}%</dd>
          <dt>Weight / height</dt>
          <dd className="tabular">
            {vitals.weightKg}kg / {vitals.heightCm}cm
          </dd>
          <dt>BMI</dt>
          <dd className="tabular">{vitals.bmi != null ? vitals.bmi.toFixed(1) : '—'}</dd>
        </dl>
      ) : (
        <p style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>No vitals recorded.</p>
      )}

      <hr style={{ border: 'none', borderTop: '1px solid var(--border)', margin: 'var(--space-3) 0' }} />

      <div style={{ fontSize: 'var(--text-sm)' }}>
        <div>
          <strong>Chief complaint:</strong> {triage?.chiefComplaint ?? '—'}
        </div>
        <div style={{ marginTop: 4 }}>
          <strong>Priority:</strong> {triage?.priority ?? '—'}
        </div>
        <div style={{ marginTop: 4 }}>
          <strong>Mode of arrival:</strong> {triage?.modeOfAmbulation ?? '—'}
        </div>
      </div>

      {/* root CLAUDE.md "Safety": allergies render inline and unmissable, never collapsed. */}
      <div style={{ marginTop: 'var(--space-3)' }}>
        <div style={{ fontSize: 'var(--text-sm)', fontWeight: 'var(--weight-medium)', color: 'var(--critical)' }}>Allergies</div>
        <TriagePanelAllergies triageAllergiesText={triage?.allergies} />
      </div>
    </div>
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
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid var(--border)', marginBottom: 'var(--space-4)' }}>
      <nav style={{ display: 'flex', gap: 'var(--space-1)' }}>
        {tabDef.map((t) => (
          <button
            key={t.key}
            onClick={() => onChange(t.key)}
            aria-current={tab === t.key ? 'page' : undefined}
            style={{
              padding: 'var(--space-2) var(--space-3)',
              border: 'none',
              background: 'none',
              font: 'inherit',
              cursor: 'pointer',
              color: tab === t.key ? 'var(--text-primary)' : 'var(--text-muted)',
              fontWeight: tab === t.key ? 'var(--weight-medium)' : 'var(--weight-normal)',
              boxShadow: tab === t.key ? 'inset 0 -2px 0 var(--green-signal)' : 'none',
            }}
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

  const [values, setValues] = useState<ConsultationFormValues>({
    historyOfPresentingIllness: '',
    pastMedicalHistory: '',
    pastSurgicalHistory: '',
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
  const [disposition, setDisposition] = useState<DispositionOutcome | ''>('');
  const [followUpDate, setFollowUpDate] = useState('');
  const [followUpInstructions, setFollowUpInstructions] = useState('');
  const [retrospective, setRetrospective] = useState(false);
  const [retrospectiveNote, setRetrospectiveNote] = useState('');

  const finalizeMutation = useMutation({
    // §7.2: follow-up is optional, doctor-only booking, created alongside
    // finalize rather than as a separate action — the date/instructions
    // fields on this screen only mean something if they actually create
    // the appointment the receptionist's read-only Follow-ups list shows.
    mutationFn: async () => {
      const result = await finalizeConsultation(
        encounterId,
        { disposition: disposition as DispositionOutcome, retrospectiveCompletion: retrospective, retrospectiveNote: retrospective ? retrospectiveNote : undefined },
        accessToken as string,
      );
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
      return result;
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

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
      <AllergyReconciliation patientId={patientId} encounterId={encounterId} allergies={allergies} />

      <section>
        <FormRow label="History of presenting illness *">
          <textarea rows={3} {...field('historyOfPresentingIllness')} style={fieldStyle} />
        </FormRow>
        <FormRow label="Past medical / surgical / family-social history">
          <textarea rows={2} {...field('pastMedicalHistory')} style={fieldStyle} placeholder="Medical" />
          <textarea rows={2} {...field('pastSurgicalHistory')} style={{ ...fieldStyle, marginTop: 'var(--space-2)' }} placeholder="Surgical" />
          <textarea rows={2} {...field('familySocialHistory')} style={{ ...fieldStyle, marginTop: 'var(--space-2)' }} placeholder="Family / social" />
        </FormRow>
        <FormRow label="Physical examination *">
          <textarea rows={3} {...field('physicalExamination')} style={fieldStyle} />
        </FormRow>
        <FormRow label="Clinical notes">
          <textarea rows={2} {...field('clinicalNotes')} style={fieldStyle} />
        </FormRow>
      </section>

      <section>
        <h2 style={{ fontSize: 'var(--text-base)' }}>Diagnosis</h2>
        {diagnoses.length > 0 && (
          <ul style={{ listStyle: 'none', padding: 0, margin: '0 0 var(--space-3)' }}>
            {diagnoses.map((d) => (
              <li key={d.id} style={{ padding: 'var(--space-2)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', marginBottom: 'var(--space-1)' }}>
                <strong>{d.icd10Code}</strong> {d.icd10TitleSnapshot} {d.isPrimary && <span className="badge badge-signal">Primary</span>}{' '}
                <span className="badge badge-neutral">{d.certainty}</span>
                {d.freeTextClarifier && <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>{d.freeTextClarifier}</div>}
              </li>
            ))}
          </ul>
        )}
        <Combobox<Icd10SearchResult>
          label="ICD-10 diagnosis"
          placeholder="Type at least 2 characters…"
          search={(q) =>
            searchIcd10(q, undefined, accessToken as string).then((r) => {
              latestIcd10SearchLogId.current = r.searchLogId ?? null;
              return r.results ?? [];
            })
          }
          getKey={(o) => o.code as string}
          renderOption={(o) => (
            <span>
              <strong>{o.code}</strong> {o.title}
            </span>
          )}
          onSelect={(o) => {
            setSelectedCode(o);
            // 10-clinical-reference-data.md §5: the mandatory selection-
            // audit callback — feeds "recent selections ranked first."
            if (latestIcd10SearchLogId.current && o.code) {
              recordIcd10Selection(latestIcd10SearchLogId.current, o.code, accessToken as string);
            }
          }}
        />
        {selectedCode && (
          <div style={{ marginTop: 'var(--space-2)', display: 'flex', gap: 'var(--space-2)', alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <div>
              Selected: <strong>{selectedCode.code}</strong> {selectedCode.title}
            </div>
            <input
              placeholder="Free-text clarifier"
              value={clarifier}
              onChange={(e) => setClarifier(e.target.value)}
              style={{ ...fieldStyle, width: 240 }}
            />
            <select value={certainty} onChange={(e) => setCertainty(e.target.value as DiagnosisCertainty)} style={fieldStyle}>
              <option value="PROVISIONAL">Provisional</option>
              <option value="CONFIRMED">Confirmed</option>
              <option value="RULED_OUT">Ruled out</option>
            </select>
            <label style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 'var(--text-sm)' }}>
              <input type="checkbox" checked={isPrimary} onChange={(e) => setIsPrimary(e.target.checked)} /> Primary
            </label>
            <button type="button" className="btn btn-secondary" onClick={() => addDiagnosisMutation.mutate()} disabled={addDiagnosisMutation.isPending}>
              Add diagnosis
            </button>
          </div>
        )}
        {addDiagnosisMutation.isError && (
          <p className="form-error">{errorMessage(addDiagnosisMutation.error)}</p>
        )}
      </section>

      <section>
        <FormRow label="Treatment plan *">
          <textarea rows={3} {...field('treatmentPlan')} style={fieldStyle} />
        </FormRow>
      </section>

      <section>
        <h2 style={{ fontSize: 'var(--text-base)' }}>Disposition</h2>
        <select value={disposition} onChange={(e) => setDisposition(e.target.value as DispositionOutcome)} style={{ ...fieldStyle, width: 320 }}>
          <option value="">Select disposition…</option>
          {DISPOSITION_OPTIONS.map((o, i) => (
            <option key={i} value={o.available ? o.value : ''} disabled={!o.available}>
              {o.label}
            </option>
          ))}
        </select>
        <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-2)' }}>
          <input type="date" value={followUpDate} onChange={(e) => setFollowUpDate(e.target.value)} style={fieldStyle} />
          <input
            placeholder="Follow-up instructions"
            value={followUpInstructions}
            onChange={(e) => setFollowUpInstructions(e.target.value)}
            style={fieldStyle}
          />
        </div>

        <label style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 'var(--space-3)', fontSize: 'var(--text-sm)' }}>
          <input type="checkbox" checked={retrospective} onChange={(e) => setRetrospective(e.target.checked)} />
          This is a retrospective completion (patient was seen earlier)
        </label>
        {retrospective && (
          <textarea
            rows={2}
            placeholder="What this note is based on (memory, paper notes, the nurse's record) — minimum 20 characters"
            value={retrospectiveNote}
            onChange={(e) => setRetrospectiveNote(e.target.value)}
            style={{ ...fieldStyle, marginTop: 'var(--space-2)' }}
          />
        )}
      </section>

      <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
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
        <button type="button" className="btn btn-primary" disabled={!canFinalize || finalizeMutation.isPending} onClick={() => finalizeMutation.mutate()}>
          Finalize
        </button>
      </div>
      {finalizeMutation.isError && <p className="form-error">{errorMessage(finalizeMutation.error)}</p>}
    </div>
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
        <p style={{ fontSize: 'var(--text-sm)' }}>No allergy records for this patient.</p>
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
                  <textarea
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
        <input placeholder="Add allergy" value={newDescription} onChange={(e) => setNewDescription(e.target.value)} style={fieldStyle} />
        <input placeholder="Reaction" value={newReaction} onChange={(e) => setNewReaction(e.target.value)} style={fieldStyle} />
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
  const [sendResult, setSendResult] = useState<string | null>(null);

  const { data: stock } = useQuery({
    queryKey: ['stock-reports', 'current-stock'],
    queryFn: () => fetchCurrentStock(accessToken as string),
    enabled: accessToken != null,
    staleTime: 30_000,
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
  const computedQuantity =
    mode === 'whole' ? wholeQuantity : dosesPerDay != null ? quantityPerDose * dosesPerDay * durationDays : wholeQuantity;
  const overCeiling = computedQuantity > QUANTITY_SANITY_CEILING;

  function addLine() {
    if (!pending) return;
    setLines((prev) => [
      ...prev,
      {
        key: `${pending.id}-${Date.now()}`,
        item: pending,
        priceMinor,
        quantity: computedQuantity,
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
          onSelect={(o) => {
            // Visible-but-unselectable rows (CONTROLLED,
            // not-yet-active, or "pending pharmacy classification"
            // placeholders from the V67 admin importer) must not become
            // a pending line — placePrescription would 404 on the
            // synthetic id and the mutation would fail with an opaque
            // "Something went wrong" message. Ignore the select.
            if (o.selectable === false) return;
            setPending(o);
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

          <label style={{ marginRight: 'var(--space-3)' }}>
            <input type="radio" checked={mode === 'whole'} onChange={() => setMode('whole')} /> Dispense as whole
          </label>
          <label>
            <input type="radio" checked={mode === 'structured'} onChange={() => setMode('structured')} /> Structured dosing
          </label>

          {mode === 'whole' ? (
            <FormRow label="Total quantity">
              <input type="number" min={1} value={wholeQuantity} onChange={(e) => setWholeQuantity(Number(e.target.value))} style={fieldStyle} />
            </FormRow>
          ) : (
            <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-2)', flexWrap: 'wrap' }}>
              <FormRow label="Quantity per dose">
                <input type="number" min={1} value={quantityPerDose} onChange={(e) => setQuantityPerDose(Number(e.target.value))} style={fieldStyle} />
              </FormRow>
              <FormRow label="Frequency">
                <select value={frequency} onChange={(e) => setFrequency(e.target.value as PrescriptionFrequency)} style={fieldStyle}>
                  {(['STAT', 'OD', 'BD', 'TDS', 'QDS', 'PRN'] as PrescriptionFrequency[]).map((f) => (
                    <option key={f} value={f}>
                      {f}
                    </option>
                  ))}
                </select>
              </FormRow>
              {dosesPerDay != null ? (
                <FormRow label="Days">
                  <input type="number" min={1} value={durationDays} onChange={(e) => setDurationDays(Number(e.target.value))} style={fieldStyle} />
                </FormRow>
              ) : (
                <FormRow label="Total quantity (PRN — no fixed schedule)">
                  <input type="number" min={1} value={wholeQuantity} onChange={(e) => setWholeQuantity(Number(e.target.value))} style={fieldStyle} />
                </FormRow>
              )}
              <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)', alignSelf: 'center' }}>
                = {computedQuantity} total units
              </div>
            </div>
          )}
          {overCeiling && <p className="field-error">Quantity looks unusually high for one item — double-check before adding.</p>}

          <FormRow label="Route / instructions">
            <input value={route} onChange={(e) => setRoute(e.target.value)} style={{ ...fieldStyle, marginBottom: 'var(--space-2)' }} />
            <textarea rows={2} value={dosageInstructions} onChange={(e) => setDosageInstructions(e.target.value)} style={fieldStyle} placeholder="Dosage instructions" />
          </FormRow>

          <button type="button" className="btn btn-secondary" onClick={addLine} style={{ marginTop: 'var(--space-2)' }}>
            Add to prescription
          </button>
        </div>
      )}

      <table className="table">
        <tbody>
          {lines.map((l) => (
            <tr key={l.key}>
              <td>
                {l.item.name} — {l.quantity} units
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
      </table>

      <button type="button" className="btn btn-primary" disabled={lines.length === 0 || send.isPending} onClick={() => send.mutate()} style={{ marginTop: 'var(--space-3)' }}>
        {send.isPending ? 'Sending…' : 'Send Prescription'}
      </button>
      {lines.length === 0 && (
        <p style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)', marginTop: 'var(--space-2)' }}>
          Add at least one drug above before sending.
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
  const [sendResult, setSendResult] = useState<string | null>(null);

  function addLine() {
    if (!pending) return;
    setLines((prev) => [
      ...prev,
      { key: `${pending.serviceItemId}-${Date.now()}`, test: pending, urgency, instructions, priceMinor: pending.priceMinor ?? null },
    ]);
    setPending(null);
    setInstructions('');
  }

  const send = useMutation({
    // Same incremental-removal reasoning as PrescriptionTab's send — a
    // mid-list failure must not leave already-placed lines re-sendable.
    mutationFn: async () => {
      for (const line of [...lines]) {
        await placeLabOrder(
          { encounterId, labTestCode: line.test.code as string, clinicalIndication: line.instructions || undefined, urgency: line.urgency },
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
        onSelect={(o) => {
          // Placeholders are visible-and-unselectable — matching the
          // prescription typeahead's CONTROLLED / not-yet-active pattern.
          if (o.kind === 'orderable') setPending(o.test);
        }}
      />

      {pending && (
        <div style={{ border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: 'var(--space-3)', margin: 'var(--space-3) 0' }}>
          <strong>{pending.name}</strong> — {formatMoney(pending.priceMinor, pending.currencyCode)}
          <FormRow label="Urgency">
            <select value={urgency} onChange={(e) => setUrgency(e.target.value as LabOrderUrgency)} style={fieldStyle}>
              <option value="ROUTINE">Routine</option>
              <option value="URGENT">Urgent</option>
              <option value="STAT">STAT</option>
            </select>
          </FormRow>
          <FormRow label="Clinical indication / instructions">
            <textarea rows={2} value={instructions} onChange={(e) => setInstructions(e.target.value)} style={fieldStyle} />
          </FormRow>
          <button type="button" className="btn btn-secondary" onClick={addLine} style={{ marginTop: 'var(--space-2)' }}>
            Add to request
          </button>
        </div>
      )}

      <table className="table">
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
      </table>

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

function FormRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 'var(--space-3)' }}>
      <label style={{ display: 'block', fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>{label}</label>
      {children}
    </div>
  );
}

function ageFromDob(dob: string | undefined | null): number | null {
  if (!dob) return null;
  const diff = Date.now() - new Date(dob).getTime();
  return Math.floor(diff / (365.25 * 24 * 60 * 60 * 1000));
}

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong. Please try again.';
}
