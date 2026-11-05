import { fetchLabResultsByOrder } from '../../shared/api/labResults';
import type { PrescriptionHistoryResponse } from '../../shared/api/prescriptions';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { TableRegion } from '../../shared/components/TableRegion';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Fragment, useMemo, useState, type CSSProperties } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  fetchAllergiesByPatient,
  fetchConsultationRecordsByEncounter,
  fetchDiagnosesByEncounter,
  type ConsultationRecordResponse,
  type DiagnosisResponse,
} from '../../shared/api/clinical';
import { fetchDepartments } from '../../shared/api/departments';
import { fetchEncounterById } from '../../shared/api/encounters';
import { searchInvoices } from '../../shared/api/invoices';
import { fetchLabOrdersByEncounter } from '../../shared/api/labOrders';
import { fetchPatientById } from '../../shared/api/patients';
import { fetchPrescriptionsByEncounter } from '../../shared/api/prescriptions';
import { searchCompletedAssessments, type CompletedTriageAssessmentResponse } from '../../shared/api/triage';
import {
  fetchVitalSignsByEncounter,
  recordConsultationVitals,
  type RecordConsultationVitalsRequest,
  type VitalSignsResponse,
} from '../../shared/api/vitalSigns';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import { formatDate, formatDateTime } from '../../shared/format/date';
import { formatMoney } from '../../shared/format/money';

// Redesign brief §2-§12: the doctor's professional clinical record view
// for one encounter. Prioritized order: identity → status → clinical
// summary (vitals + consultation) → diagnosis → prescription → lab →
// billing → timeline. Sections that have no data say so; nothing is
// fabricated. Sections whose backend surface is missing entirely
// (procedures for an outpatient encounter, event-log timeline) are
// omitted with a comment rather than shown as empty stubs.
export function EncounterDetailPage() {
  const { patientId, encounterId } = useParams<{ patientId: string; encounterId: string }>();
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  const navigate = useNavigate();

  const { data: encounter, isLoading: encounterLoading, isError: encounterError } = useQuery({
    queryKey: ['encounters', encounterId],
    queryFn: () => fetchEncounterById(encounterId as string, accessToken as string),
    enabled: accessToken != null && !!encounterId,
  });
  const { data: patient } = useQuery({
    queryKey: ['patients', patientId],
    queryFn: () => fetchPatientById(patientId as string, accessToken as string),
    enabled: accessToken != null && !!patientId,
  });
  const { data: allergies } = useQuery({
    queryKey: ['allergies', patientId],
    queryFn: () => fetchAllergiesByPatient(patientId as string, accessToken as string),
    enabled: accessToken != null && !!patientId,
  });
  const { data: departments } = useQuery({
    queryKey: ['departments'],
    queryFn: () => fetchDepartments(accessToken as string),
    enabled: accessToken != null,
    staleTime: 5 * 60_000,
  });
  const { data: notes } = useQuery({
    queryKey: ['consultation-records', 'by-encounter', encounterId],
    queryFn: () => fetchConsultationRecordsByEncounter(encounterId as string, accessToken as string),
    enabled: accessToken != null && !!encounterId,
  });
  const { data: diagnoses } = useQuery({
    queryKey: ['diagnoses', encounterId],
    queryFn: () => fetchDiagnosesByEncounter(encounterId as string, accessToken as string),
    enabled: accessToken != null && !!encounterId,
  });
  const { data: vitals } = useQuery({
    queryKey: ['vitals', encounterId],
    queryFn: () => fetchVitalSignsByEncounter(encounterId as string, accessToken as string),
    enabled: accessToken != null && !!encounterId,
  });
  const { data: prescriptions } = useQuery({
    queryKey: ['prescriptions', 'by-encounter', encounterId],
    queryFn: () => fetchPrescriptionsByEncounter(encounterId as string, accessToken as string),
    enabled: accessToken != null && !!encounterId,
  });
  const { data: labOrders } = useQuery({
    queryKey: ['lab-orders', 'by-encounter', encounterId],
    queryFn: () => fetchLabOrdersByEncounter(encounterId as string, accessToken as string),
    enabled: accessToken != null && !!encounterId,
  });
  const { data: invoices } = useQuery({
    queryKey: ['invoices', 'by-encounter', encounterId],
    queryFn: () => searchInvoices({ encounterId }, accessToken as string),
    enabled: accessToken != null && !!encounterId,
  });
  // Triage search only takes a date window (no patient/encounter filter),
  // so widen enough to catch it and filter client-side. 60-day back window
  // matched to the assessment retention pattern MyQueuePage already uses,
  // plus 1 day of forward slack for facility-local timezone drift.
  const triageWindow = useTriageWindow(encounter?.createdAt);
  const { data: triageAll } = useQuery({
    queryKey: ['triage-assessments', 'window', triageWindow],
    queryFn: () =>
      searchCompletedAssessments(
        { assessedFrom: triageWindow.from, assessedTo: triageWindow.to },
        accessToken as string,
      ),
    enabled: accessToken != null && !!encounter?.createdAt,
  });
  const triage = (triageAll ?? []).find((t) => t.encounterId === encounterId);

  if (encounterLoading) {
    return <FeedbackState loading>Loading encounter…</FeedbackState>;
  }
  if (encounterError || !encounter) {
    return (
      <div>
        <BackLink patientId={patientId} navigate={navigate} />
        <p className="form-error">Could not load this encounter.</p>
      </div>
    );
  }

  const departmentName = (departments ?? []).find((d) => d.id === encounter.departmentId)?.name ?? '—';
  const isMine = encounter.assignedProviderId === me?.userId;
  const providerLabel = isMine ? 'You' : encounter.assignedProviderId ? 'Assigned to another provider' : 'Unassigned';
  const latestNote = pickLatestNote(notes ?? []);
  const triageVitals = (vitals ?? []).find((v) => v.context === 'TRIAGE');
  const consultVitals = (vitals ?? []).find((v) => v.context === 'CONSULTATION');
  const encounterInvoices = invoices ?? [];

  return (
    <div>
      <BackLink patientId={patientId} navigate={navigate} />

      {/* Encounter header */}
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
          <h1 style={{ margin: 0, fontSize: 'var(--text-2xl)', fontWeight: 'var(--weight-medium)' }}>
            {encounter.type ?? '—'}
          </h1>
          <span style={{ color: 'var(--text-secondary)' }}>{formatDate(encounter.createdAt)}</span>
          <EncounterStateBadge state={encounter.state} />
        </div>
        <dl className="clinical-grid" style={metaGridStyle}>
          <MetaItem label="Encounter" value={shortId(encounter.id)} />
          <MetaItem label="Department" value={departmentName} />
          <MetaItem label="Assigned to" value={providerLabel} />
          <MetaItem label="Opened" value={formatDateTime(encounter.createdAt)} />
          <MetaItem label="Payer" value={encounter.payerType ?? '—'} />
        </dl>
      </header>

      <EncounterStateGuidance
        state={encounter.state}
        encounterId={encounter.id as string}
        patientId={patientId as string}
        navigate={navigate}
      />

      {/* Compact patient context */}
      {patient && (
        <div
          style={{
            background: 'var(--bg-subtle, var(--neutral-bg, #f7f7f7))',
            borderRadius: 'var(--radius)',
            padding: 'var(--space-3)',
            marginBottom: 'var(--space-4)',
          }}
        >
          <div style={{ fontWeight: 'var(--weight-medium)' }}>
            {patient.fullName ?? '—'}
            {patient.hospitalNo && (
              <span style={{ color: 'var(--text-secondary)', fontWeight: 'var(--weight-normal)' }}> · {patient.hospitalNo}</span>
            )}
            {patient.gender && (
              <span style={{ color: 'var(--text-secondary)', fontWeight: 'var(--weight-normal)' }}> · {genderLabel(patient.gender)}</span>
            )}
            {patient.dateOfBirth && (
              <span style={{ color: 'var(--text-secondary)', fontWeight: 'var(--weight-normal)' }}>
                {' '}
                · {ageFromDob(patient.dateOfBirth)} y
              </span>
            )}
          </div>
          <div style={{ marginTop: 'var(--space-2)' }}>
            {(allergies ?? []).length === 0 ? (
              <span style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>No known allergies recorded.</span>
            ) : (
              <span
                role="alert"
                style={{
                  display: 'inline-block',
                  background: 'var(--critical-bg)',
                  color: 'var(--critical)',
                  borderRadius: 'var(--radius)',
                  padding: '2px var(--space-2)',
                  fontSize: 'var(--text-sm)',
                }}
              >
                ⚠ Allergies: {allergies!.map((a) => a.description).filter(Boolean).join(' · ')}
              </span>
            )}
          </div>
        </div>
      )}

      {/* Clinical summary — two columns on desktop, one on mobile */}
      <div className="clinical-grid" style={twoColStyle}>
        <SectionCard title="Vitals & Triage">
          <VitalsBlock triageVitals={triageVitals} consultVitals={consultVitals} triage={triage} />
          {encounterId && <RecordConsultationVitals encounterId={encounterId} hasConsultVitals={!!consultVitals} />}
        </SectionCard>
        <SectionCard title="Consultation">
          <ConsultationBlock note={latestNote} triage={triage} />
        </SectionCard>
      </div>

      <SectionCard title="Diagnosis">
        <DiagnosisBlock diagnoses={diagnoses ?? []} />
      </SectionCard>

      <SectionCard title="Prescription & Medications">
        <PrescriptionBlock prescriptions={prescriptions ?? []} />
      </SectionCard>

      <SectionCard title="Laboratory">
        <LabBlock orders={labOrders ?? []} />
      </SectionCard>

      {/* Procedures / services: no by-encounter read endpoint currently
          exists (fetchProcedureItems needs a surgeryOrderId; there's no
          endpoint listing procedures for a standard outpatient encounter).
          Rather than showing a stub that always says "None recorded"
          without querying, the section is deliberately omitted here — a
          future endpoint would slot in exactly at this point in the flow. */}

      <SectionCard title="Billing & Payment">
        <BillingBlock invoices={encounterInvoices} />
      </SectionCard>

      <SectionCard title="Timeline">
        <TimelineBlock
          encounter={encounter}
          triage={triage}
          notes={notes ?? []}
          diagnoses={diagnoses ?? []}
          prescriptionsAt={(prescriptions ?? []).map((p) => p.prescribedAt).filter(Boolean) as string[]}
          labOrdersAt={(labOrders ?? []).map((l) => l.orderedAt).filter(Boolean) as string[]}
          invoicesAt={encounterInvoices.map((i) => i.createdAt).filter(Boolean) as string[]}
        />
      </SectionCard>
    </div>
  );
}

// Small in-page callout that names what state the encounter is in and
// what the doctor's next step actually is. The detail page is a
// read-only reference view (there is no consultation editor mounted
// here), and clicking "Open encounter" on My Patients used to land the
// doctor here with no explanation for why nothing is editable — this
// banner is that explanation.
function EncounterStateGuidance({
  state,
  encounterId,
  patientId,
  navigate,
}: {
  state?: string;
  encounterId: string;
  patientId: string;
  navigate: (to: string) => void;
}) {
  if (!state) return null;

  if (state === 'IN_CONSULT') {
    return (
      <GuidanceBanner tone="info">
        <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'center', flexWrap: 'wrap' }}>
          <div>
            <strong>Consultation open.</strong> Continue your notes, orders, and finalize on the consultation
            page.
          </div>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => navigate(`/doctor/consultation/${encounterId}`)}
          >
            Continue consultation →
          </button>
        </div>
      </GuidanceBanner>
    );
  }

  if (state === 'AWAITING_CONSULT') {
    return (
      <GuidanceBanner tone="info">
        <div style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'center', flexWrap: 'wrap' }}>
          <div>
            <strong>Waiting to be seen.</strong> Open the consultation to begin.
          </div>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => navigate(`/doctor/consultation/${encounterId}`)}
          >
            Open consultation →
          </button>
        </div>
      </GuidanceBanner>
    );
  }

  if (state === 'AWAITING_RESULTS') {
    return (
      <GuidanceBanner tone="warning">
        <div>
          <div style={{ marginBottom: 'var(--space-2)' }}>
            <strong>Awaiting results.</strong> Your consultation note is finalized; the encounter stays open
            until lab or imaging results come back.
          </div>
          <ol style={{ margin: '0 0 var(--space-2) var(--space-4)', padding: 0, fontSize: 'var(--text-sm)' }}>
            <li>
              Watch <strong>Request History</strong> for status changes on the orders you placed — results
              flow in there as the lab enters them.
            </li>
            <li>
              When the patient physically returns, the receptionist opens <strong>Expected Returns</strong>{' '}
              and clicks <strong>Resume</strong>; the encounter moves back to <em>Awaiting consult</em> and
              lands on your My Queue.
            </li>
            <li>Reopen the consultation there to amend the diagnosis, adjust prescription, or finalize.</li>
          </ol>
          <p
            style={{
              margin: 0,
              fontSize: 'var(--text-xs)',
              color: 'var(--text-muted)',
            }}
          >
            This page is read-only by design — it's your reference view. Editing lives on the consultation
            page, reached only via the queue once the patient is back.
          </p>
          <div style={{ marginTop: 'var(--space-2)', display: 'flex', gap: 'var(--space-2)' }}>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => navigate('/doctor/request-history')}
            >
              Go to Request History
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => navigate(`/doctor/patients/${patientId}`)}
            >
              Back to patient
            </button>
          </div>
        </div>
      </GuidanceBanner>
    );
  }

  if (state === 'CONSULT_COMPLETE' || state === 'CLOSED' || state === 'DISCHARGED') {
    return (
      <GuidanceBanner tone="neutral">
        <div>
          <strong>Encounter closed.</strong> This is the historical record — everything is read-only.
        </div>
      </GuidanceBanner>
    );
  }

  return null;
}

function GuidanceBanner({
  tone,
  children,
}: {
  tone: 'info' | 'warning' | 'neutral';
  children: React.ReactNode;
}) {
  const bg =
    tone === 'warning' ? 'var(--warning-bg)' : tone === 'info' ? 'var(--info-bg, #eef4ff)' : 'var(--gray-100)';
  const border =
    tone === 'warning' ? 'var(--warning)' : tone === 'info' ? 'var(--info, #1f5fff)' : 'var(--border)';
  return (
    <div
      role={tone === 'warning' ? 'status' : undefined}
      style={{
        background: bg,
        borderLeft: `3px solid ${border}`,
        borderRadius: 'var(--radius)',
        padding: 'var(--space-3) var(--space-4)',
        marginBottom: 'var(--space-4)',
        fontSize: 'var(--text-sm)',
      }}
    >
      {children}
    </div>
  );
}

function BackLink({ patientId, navigate }: { patientId: string | undefined; navigate: (to: string) => void }) {
  return (
    <button
      type="button"
      className="btn btn-ghost"
      onClick={() => navigate(patientId ? `/doctor/patients/${patientId}` : '/doctor/patients')}
      style={{ marginBottom: 'var(--space-3)' }}
    >
      ← Back to Patient
    </button>
  );
}

function SectionCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-4)',
        marginBottom: 'var(--space-3)',
      }}
    >
      <h2 style={{ margin: 0, fontSize: 'var(--text-base)', fontWeight: 'var(--weight-medium)', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.03em' }}>
        {title}
      </h2>
      <div style={{ marginTop: 'var(--space-3)' }}>{children}</div>
    </section>
  );
}

function MetaItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.03em' }}>{label}</dt>
      <dd style={{ margin: '2px 0 0', fontSize: 'var(--text-sm)' }}>{value}</dd>
    </div>
  );
}

function VitalsBlock({
  triageVitals,
  consultVitals,
  triage,
}: {
  triageVitals?: VitalSignsResponse;
  consultVitals?: VitalSignsResponse;
  triage?: CompletedTriageAssessmentResponse;
}) {
  const v = consultVitals ?? triageVitals;
  if (!v && !triage) {
    return <Empty>No vitals or triage assessment recorded.</Empty>;
  }
  // Reading keys match VitalType enum names, per
  // clinical/api/VitalSignsController#toResponse.
  const bp =
    readingOf(v, 'SYSTOLIC_BP') != null && readingOf(v, 'DIASTOLIC_BP') != null
      ? `${readingOf(v, 'SYSTOLIC_BP')}/${readingOf(v, 'DIASTOLIC_BP')} mmHg`
      : null;
  const pulse = readingOf(v, 'PULSE');
  const temp = readingOf(v, 'TEMPERATURE');
  const spo2 = readingOf(v, 'SPO2');
  const rr = readingOf(v, 'RESPIRATORY_RATE');
  const abnormal = new Set(v?.abnormalVitalTypes ?? []);
  const rows: { label: string; value: string | null; abnormalKeys?: string[] }[] = [
    { label: 'BP', value: bp, abnormalKeys: ['SYSTOLIC_BP', 'DIASTOLIC_BP'] },
    { label: 'Pulse', value: pulse ? `${pulse} bpm` : null, abnormalKeys: ['PULSE'] },
    { label: 'Temp', value: temp ? `${temp} °C` : null, abnormalKeys: ['TEMPERATURE'] },
    { label: 'SpO₂', value: spo2 ? `${spo2} %` : null, abnormalKeys: ['SPO2'] },
    { label: 'Resp rate', value: rr ? `${rr}/min` : null, abnormalKeys: ['RESPIRATORY_RATE'] },
    { label: 'Weight', value: v?.weightKg ? `${v.weightKg} kg` : null },
    { label: 'Height', value: v?.heightCm ? `${v.heightCm} cm` : null },
    { label: 'BMI', value: v?.bmi != null ? v.bmi.toFixed(1) : null },
    { label: 'AVPU', value: v?.avpu ?? null },
    { label: 'Glucose', value: v?.bloodGlucoseMmol ? `${v.bloodGlucoseMmol} mmol/L` : null },
    { label: 'Triage priority', value: triage?.priority ?? null },
    { label: 'Chief complaint', value: triage?.chiefComplaint ?? null },
    {
      label: 'Pain',
      value: triage?.hasPain ? `Yes (${triage.painScore ?? '—'}/10${triage.painLocation ? `, ${triage.painLocation}` : ''})` : null,
    },
  ];
  const present = rows.filter((r) => r.value != null && r.value !== '');
  if (present.length === 0) {
    return <Empty>No vitals or triage assessment recorded.</Empty>;
  }
  const abnormalLabels = present
    .filter((row) => (row.abnormalKeys ?? []).some((k) => abnormal.has(k)))
    .map((row) => row.label);
  return (
    <>
      {abnormalLabels.length > 0 && (
        <div
          role="alert"
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: 'var(--space-2)',
            padding: 'var(--space-3)',
            marginBottom: 'var(--space-3)',
            border: '1px solid var(--warning)',
            background: 'var(--warning-bg)',
            borderRadius: 'var(--radius)',
            color: 'var(--warning)',
            fontSize: 'var(--text-sm)',
          }}
        >
          <span aria-hidden="true" style={{ fontWeight: 'var(--weight-bold)' }}>⚠</span>
          <span>
            <strong>{abnormalLabels.length}</strong> vital {abnormalLabels.length === 1 ? 'sign is' : 'signs are'} outside the reference range
            for this patient&rsquo;s age band: <strong>{abnormalLabels.join(', ')}</strong>.
          </span>
        </div>
      )}
      <dl className="clinical-grid" style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '4px var(--space-3)', margin: 0 }}>
        {present.map((row) => {
        const isAbnormal = (row.abnormalKeys ?? []).some((k) => abnormal.has(k));
        return (
          <Fragment key={row.label}>
            <dt style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>{row.label}</dt>
            <dd style={{ margin: 0, fontSize: 'var(--text-sm)', display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
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

function RecordConsultationVitals({ encounterId, hasConsultVitals }: { encounterId: string; hasConsultVitals: boolean }) {
  const { accessToken } = useAuth();
  const canRecord = usePermission('VITALS_CREATE');
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<Partial<Record<keyof RecordConsultationVitalsRequest, string>>>({});
  const [error, setError] = useState<string | null>(null);

  const submit = useMutation({
    mutationFn: (body: RecordConsultationVitalsRequest) => recordConsultationVitals(encounterId, body, accessToken as string),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['vitals', encounterId] });
      setForm({});
      setOpen(false);
      setError(null);
    },
    onError: (e: Error) => setError(e.message || 'Failed to record vitals.'),
  });

  if (!canRecord) {
    return null;
  }

  if (!open) {
    return (
      <div style={{ marginTop: 'var(--space-3)' }}>
        <button type="button" className="btn btn-secondary" onClick={() => setOpen(true)}>
          {hasConsultVitals ? 'Record another consultation vitals reading' : 'Record vitals now (optional)'}
        </button>
      </div>
    );
  }

  const numeric: (keyof RecordConsultationVitalsRequest)[] = [
    'temperatureC', 'systolicMmHg', 'diastolicMmHg', 'pulseBpm', 'respiratoryRate', 'spo2Pct', 'weightKg', 'heightCm',
  ];

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    for (const key of numeric) {
      if (form[key] == null || form[key] === '') {
        setError('All six vitals plus weight and height are required to submit. Cancel if you do not want to record.');
        return;
      }
    }
    const body: RecordConsultationVitalsRequest = {
      temperatureC: Number(form.temperatureC),
      systolicMmHg: Number(form.systolicMmHg),
      diastolicMmHg: Number(form.diastolicMmHg),
      pulseBpm: Number(form.pulseBpm),
      respiratoryRate: Number(form.respiratoryRate),
      spo2Pct: Number(form.spo2Pct),
      weightKg: Number(form.weightKg),
      heightCm: Number(form.heightCm),
      painScore: form.painScore ? Number(form.painScore) : undefined,
      bloodGlucoseMmol: form.bloodGlucoseMmol ? Number(form.bloodGlucoseMmol) : undefined,
    };
    submit.mutate(body);
  }

  const cell: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 'var(--space-1)', fontSize: 'var(--text-sm)' };
  const input: CSSProperties = {
    padding: 'var(--space-1) var(--space-2)',
    border: '1px solid var(--border-strong)',
    borderRadius: 'var(--radius)',
    font: 'inherit',
  };

  return (
    <form onSubmit={handleSubmit} style={{ marginTop: 'var(--space-3)', padding: 'var(--space-3)', border: '1px solid var(--border)', borderRadius: 'var(--radius)' }}>
      <h3 style={{ margin: '0 0 var(--space-2)', fontSize: 'var(--text-sm)', fontWeight: 'var(--weight-medium)' }}>
        Record vitals now (optional)
      </h3>
      <div className="clinical-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 'var(--space-2) var(--space-3)' }}>
        <label style={cell}>Temperature (°C)<input className="clinical-control" style={input} type="number" step="0.1" value={form.temperatureC ?? ''} onChange={(e) => setForm({ ...form, temperatureC: e.target.value })} /></label>
        <label style={cell}>Pulse (bpm)<input className="clinical-control" style={input} type="number" value={form.pulseBpm ?? ''} onChange={(e) => setForm({ ...form, pulseBpm: e.target.value })} /></label>
        <label style={cell}>Systolic BP (mmHg)<input className="clinical-control" style={input} type="number" value={form.systolicMmHg ?? ''} onChange={(e) => setForm({ ...form, systolicMmHg: e.target.value })} /></label>
        <label style={cell}>Diastolic BP (mmHg)<input className="clinical-control" style={input} type="number" value={form.diastolicMmHg ?? ''} onChange={(e) => setForm({ ...form, diastolicMmHg: e.target.value })} /></label>
        <label style={cell}>Resp rate (/min)<input className="clinical-control" style={input} type="number" value={form.respiratoryRate ?? ''} onChange={(e) => setForm({ ...form, respiratoryRate: e.target.value })} /></label>
        <label style={cell}>SpO₂ (%)<input className="clinical-control" style={input} type="number" value={form.spo2Pct ?? ''} onChange={(e) => setForm({ ...form, spo2Pct: e.target.value })} /></label>
        <label style={cell}>Weight (kg)<input className="clinical-control" style={input} type="number" step="0.1" value={form.weightKg ?? ''} onChange={(e) => setForm({ ...form, weightKg: e.target.value })} /></label>
        <label style={cell}>Height (cm)<input className="clinical-control" style={input} type="number" step="0.1" value={form.heightCm ?? ''} onChange={(e) => setForm({ ...form, heightCm: e.target.value })} /></label>
        <label style={cell}>Glucose (mmol/L, optional)<input className="clinical-control" style={input} type="number" step="0.1" value={form.bloodGlucoseMmol ?? ''} onChange={(e) => setForm({ ...form, bloodGlucoseMmol: e.target.value })} /></label>
        <label style={cell}>Pain (0–10, optional)<input className="clinical-control" style={input} type="number" min={0} max={10} value={form.painScore ?? ''} onChange={(e) => setForm({ ...form, painScore: e.target.value })} /></label>
      </div>
      {error && <p style={{ color: 'var(--critical)', fontSize: 'var(--text-sm)', marginTop: 'var(--space-2)' }}>{error}</p>}
      <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-3)' }}>
        <button type="submit" className="btn btn-primary" disabled={submit.isPending}>
          {submit.isPending ? 'Saving…' : 'Save vitals'}
        </button>
        <button type="button" className="btn btn-ghost" onClick={() => { setOpen(false); setError(null); }}>
          Cancel
        </button>
      </div>
    </form>
  );
}

function ConsultationBlock({
  note,
  triage,
}: {
  note?: ConsultationRecordResponse;
  triage?: CompletedTriageAssessmentResponse;
}) {
  if (!note && !triage) {
    return <Empty>No consultation note recorded.</Empty>;
  }
  const fields: [string, string | undefined][] = [
    ['Chief complaint', triage?.chiefComplaint],
    ['History of presenting illness', note?.historyOfPresentingIllness],
    ['Past medical history', note?.pastMedicalHistory],
    ['Past surgical history', note?.pastSurgicalHistory],
    ['Comorbidities', note?.comorbidities],
    ['Family / social history', note?.familySocialHistory],
    ['Examination', note?.physicalExamination],
    ['Clinical notes', note?.clinicalNotes],
    ['Treatment plan', note?.treatmentPlan],
  ];
  const present = fields.filter(([, v]) => v && v.trim() !== '');
  if (present.length === 0) {
    return <Empty>No consultation content recorded.</Empty>;
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
      {present.map(([label, value]) => (
        <div key={label}>
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.03em' }}>{label}</div>
          <div style={{ marginTop: 2, whiteSpace: 'pre-wrap' }}>{value}</div>
        </div>
      ))}
      {note && (
        <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)', marginTop: 'var(--space-1)' }}>
          Status: {note.status ?? '—'}
          {note.completedAt && ` · Completed ${formatDateTime(note.completedAt)}`}
          {note.updatedAt && ` · Last saved ${formatDateTime(note.updatedAt)}`}
        </div>
      )}
    </div>
  );
}

function DiagnosisBlock({ diagnoses }: { diagnoses: DiagnosisResponse[] }) {
  if (diagnoses.length === 0) {
    return <Empty>No diagnosis recorded.</Empty>;
  }
  const primary = diagnoses.filter((d) => d.isPrimary);
  const secondary = diagnoses.filter((d) => !d.isPrimary);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
      {primary.length > 0 && (
        <div>
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.03em' }}>Primary</div>
          <ul style={diagnosisListStyle}>
            {primary.map((d) => (
              <DiagnosisRow key={d.id} d={d} />
            ))}
          </ul>
        </div>
      )}
      {secondary.length > 0 && (
        <div>
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.03em' }}>Secondary</div>
          <ul style={diagnosisListStyle}>
            {secondary.map((d) => (
              <DiagnosisRow key={d.id} d={d} />
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function DiagnosisRow({ d }: { d: DiagnosisResponse }) {
  return (
    <li style={{ marginBottom: 4 }}>
      <span style={{ fontFamily: 'var(--font-mono, monospace)', color: 'var(--text-secondary)' }}>{d.icd10Code ?? '—'}</span>{' '}
      {d.icd10TitleSnapshot ?? ''}
      {d.certainty && d.certainty !== 'CONFIRMED' && (
        <span className="badge badge-warning" style={{ marginLeft: 'var(--space-2)' }}>{d.certainty.toLowerCase()}</span>
      )}
      {d.freeTextClarifier && (
        <div style={{ marginLeft: 'var(--space-3)', color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>{d.freeTextClarifier}</div>
      )}
      {d.diagnosedAt && (
        <div style={{ marginLeft: 'var(--space-3)', color: 'var(--text-muted)', fontSize: 'var(--text-xs)' }}>
          {formatDateTime(d.diagnosedAt)}
        </div>
      )}
    </li>
  );
}

function PrescriptionBlock({ prescriptions }: { prescriptions: PrescriptionHistoryResponse[] }) {
  if (prescriptions.length === 0) {
    return <Empty>No medications prescribed.</Empty>;
  }
  return (
    <div style={{ overflowX: 'auto' }}>
      <TableRegion label="Prescribed medications"><table className="table" style={{ width: '100%' }}>
        <caption>Prescriptions and dispensing records do not establish that medication was taken or administered.</caption>
        <thead>
          <tr>
            <th>Medication</th>
            <th>Prescribed / dispensed</th><th>Dose · route · frequency · duration</th>
            <th>Prescribed</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {prescriptions.map((p, i) => (
            <tr key={i}>
              <td>{p.itemName ?? '—'}</td>
              <td>{p.quantityPrescribed ?? '—'} / {p.quantityDispensed ?? '—'} dispensed</td><td>{p.doseAmount ?? '—'} {p.doseUnit} · {p.route ?? '—'} · {p.frequency ?? '—'} · {p.durationDays == null ? '—' : `${p.durationDays} days`}</td>
              <td style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>{formatDateTime(p.prescribedAt)}</td>
              <td>
                <span className="badge badge-neutral">{(p.status ?? '').toLowerCase()}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table></TableRegion>
    </div>
  );
}

function LabBlock({ orders }: { orders: { id?: string; labTestName?: string; urgency?: string; orderedAt?: string; status?: string }[] }) {
  if (orders.length === 0) {
    return <Empty>No laboratory tests requested.</Empty>;
  }
  return (
    <div style={{ overflowX: 'auto' }}>
      <TableRegion label="Laboratory tests"><table className="table" style={{ width: '100%' }}>
        <caption className="sr-only">Laboratory tests</caption>
        <thead>
          <tr>
            <th>Test</th>
            <th>Urgency</th>
            <th>Ordered</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {orders.map((o, i) => (
            <Fragment key={o.id ?? i}><tr>
              <td>{o.labTestName ?? '—'}</td>
              <td>
                <span className={`badge ${o.urgency === 'STAT' ? 'badge-critical' : o.urgency === 'URGENT' ? 'badge-warning' : 'badge-neutral'}`}>
                  {(o.urgency ?? '').toLowerCase() || '—'}
                </span>
              </td>
              <td style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>{formatDateTime(o.orderedAt)}</td>
              <td>
                <span className="badge badge-neutral">{(o.status ?? '').toLowerCase()}</span>
              </td>
            </tr>{o.id && <tr><td colSpan={4}><HistoricalLabResults orderId={o.id} /></td></tr>}</Fragment>
          ))}
        </tbody>
      </table></TableRegion>
    </div>
  );
}

function BillingBlock({ invoices }: { invoices: { invoiceNumber?: string; state?: string; grossTotalMinor?: number; outstandingTotalMinor?: number; currencyCode?: string }[] }) {
  if (invoices.length === 0) {
    return <Empty>No invoices raised for this encounter.</Empty>;
  }
  return (
    <div style={{ overflowX: 'auto' }}>
      <TableRegion label="Invoices"><table className="table" style={{ width: '100%' }}>
        <caption className="sr-only">Invoices</caption>
        <thead>
          <tr>
            <th>Invoice</th>
            <th>State</th>
            <th>Total</th>
            <th>Outstanding</th>
          </tr>
        </thead>
        <tbody>
          {invoices.map((i, idx) => (
            <tr key={idx}>
              <td style={{ fontWeight: 'var(--weight-medium)' }}>{i.invoiceNumber ?? '—'}</td>
              <td>
                <span className={`badge ${(i.outstandingTotalMinor ?? 0) === 0 ? 'badge-success' : 'badge-warning'}`}>
                  {(i.state ?? '').replaceAll('_', ' ').toLowerCase()}
                </span>
              </td>
              <td className="money">{formatMoney(i.grossTotalMinor, i.currencyCode)}</td>
              <td className="money">{formatMoney(i.outstandingTotalMinor, i.currencyCode)}</td>
            </tr>
          ))}
        </tbody>
      </table></TableRegion>
    </div>
  );
}

// Timeline derived from clinical timestamps we actually have. The
// encounter_event table (the true state-transition log) has no read
// endpoint yet — labeled honestly as an activity timeline rather than
// pretending it's the audited state history. A future GET
// /encounters/{id}/events endpoint would replace this with the real thing.
function TimelineBlock({
  encounter,
  triage,
  notes,
  diagnoses,
  prescriptionsAt,
  labOrdersAt,
  invoicesAt,
}: {
  encounter: { createdAt?: string };
  triage?: CompletedTriageAssessmentResponse;
  notes: ConsultationRecordResponse[];
  diagnoses: DiagnosisResponse[];
  prescriptionsAt: string[];
  labOrdersAt: string[];
  invoicesAt: string[];
}) {
  const events: { at: string; label: string }[] = [];
  if (encounter.createdAt) events.push({ at: encounter.createdAt, label: 'Encounter registered' });
  if (triage?.assessedAt) events.push({ at: triage.assessedAt, label: 'Triage completed' });
  for (const at of invoicesAt) events.push({ at, label: 'Invoice created' });
  for (const n of notes) {
    if (n.createdAt) events.push({ at: n.createdAt, label: 'Consultation started' });
    if (n.completedAt) events.push({ at: n.completedAt, label: 'Consultation finalized' });
  }
  for (const d of diagnoses) if (d.diagnosedAt) events.push({ at: d.diagnosedAt, label: `Diagnosis recorded${d.isPrimary ? ' (primary)' : ''}` });
  for (const at of prescriptionsAt) events.push({ at, label: 'Prescription placed' });
  for (const at of labOrdersAt) events.push({ at, label: 'Lab order placed' });

  events.sort((a, b) => new Date(a.at).getTime() - new Date(b.at).getTime());

  if (events.length === 0) {
    return <Empty>No events recorded yet.</Empty>;
  }

  return (
    <ol style={{ margin: 0, padding: 0, listStyle: 'none' }}>
      {events.map((e, i) => (
        <li
          key={i}
          style={{
            display: 'grid',
            gridTemplateColumns: 'auto 1fr',
            gap: 'var(--space-3)',
            padding: 'var(--space-2) 0',
            borderBottom: i === events.length - 1 ? 'none' : '1px dashed var(--border)',
          }}
        >
          <span style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)', whiteSpace: 'nowrap' }}>{formatDateTime(e.at)}</span>
          <span>{e.label}</span>
        </li>
      ))}
    </ol>
  );
}

function Empty({ children }: { children: React.ReactNode }) {
  return <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>{children}</p>;
}

const metaGridStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
  gap: 'var(--space-3)',
  margin: 'var(--space-3) 0 0',
};

const twoColStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 320px), 1fr))',
  gap: 'var(--space-3)',
  marginBottom: 'var(--space-3)',
};

const diagnosisListStyle: CSSProperties = {
  margin: '4px 0 0',
  paddingLeft: 'var(--space-4)',
};

function pickLatestNote(notes: ConsultationRecordResponse[]): ConsultationRecordResponse | undefined {
  const active = notes.filter((n) => n.status !== 'ABANDONED');
  return active.sort((a, b) => new Date(b.updatedAt ?? b.createdAt ?? 0).getTime() - new Date(a.updatedAt ?? a.createdAt ?? 0).getTime())[0];
}

function useTriageWindow(encounterCreatedAt: string | undefined): { from: string; to: string } {
  // Stable per encounter — memoized on the createdAt string so the query
  // key doesn't churn every render (which would refetch endlessly).
  return useMemo(() => {
    const base = encounterCreatedAt ? new Date(encounterCreatedAt).getTime() : Date.now();
    const from = new Date(base - 60 * 24 * 60 * 60 * 1000).toISOString();
    const to = new Date(base + 1 * 24 * 60 * 60 * 1000).toISOString();
    return { from, to };
  }, [encounterCreatedAt]);
}

function readingOf(v: VitalSignsResponse | undefined, ...keys: string[]): number | null {
  if (!v?.readings) return null;
  for (const k of keys) {
    if (v.readings[k] != null) return v.readings[k];
  }
  return null;
}

function ageFromDob(dob: string): number {
  return Math.floor((Date.now() - new Date(dob).getTime()) / (365.25 * 24 * 60 * 60 * 1000));
}

function genderLabel(g: string): string {
  if (g.toUpperCase().startsWith('M')) return 'Male';
  if (g.toUpperCase().startsWith('F')) return 'Female';
  return g;
}

function shortId(id: string | undefined): string {
  if (!id) return '—';
  return id.slice(0, 8);
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

function HistoricalLabResults({ orderId }: { orderId: string }) {
  const { accessToken } = useAuth();
  const [open, setOpen] = useState(false);
  const results = useQuery({ queryKey: ['lab-results', orderId], queryFn: () => fetchLabResultsByOrder(orderId, accessToken!), enabled: open && !!accessToken });
  const released = results.data?.filter(r => r.releasedAt) ?? [];
  const superseded = new Set(released.map(r => r.amendsId).filter(Boolean));
  return <details open={open} onToggle={e => setOpen(e.currentTarget.open)}><summary>Released laboratory results</summary>
    {results.isLoading && <p>Loading results…</p>}
    {results.isError && <p role="alert">Results could not be loaded or access was denied.</p>}
    {results.isSuccess && released.length === 0 && <p>No released results recorded.</p>}
    {released.map(r => <article key={r.id}>
      <p><strong>{superseded.has(r.id) ? 'Superseded result' : 'Released result'}</strong> · {formatDateTime(r.releasedAt)} · Verification: {r.verificationState}<br />
        {r.amendsId && `Amends ${r.amendsId} · ${r.amendmentReason ?? ''}`}</p>
      <p>{r.findings} {r.interpretation} {r.conclusion}</p>
      <table className="table"><thead><tr><th>Parameter</th><th>Value</th><th>Reference range</th><th>Flag</th></tr></thead><tbody>
        {r.parameterValues?.map(v => <tr key={v.parameterId}><td>{v.parameterName}</td><td>{v.resultValue} {v.unit}</td><td>{v.normalLow ?? '—'} – {v.normalHigh ?? '—'}</td><td>{v.flag}</td></tr>)}
      </tbody></table>
    </article>)}
  </details>;
}
