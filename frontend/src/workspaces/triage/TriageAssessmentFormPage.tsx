import { FormSection } from '../../shared/components/FormSection';
import { Radio } from '../../shared/components/Radio';
import { Checkbox } from '../../shared/components/Checkbox';
import { FormField as FormRow } from '../../shared/components/FormField';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { Modal } from '../../shared/components/Modal';
import { PageHeader } from '../../shared/components/PageHeader';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../shared/api/client';
import { fetchEncounterById } from '../../shared/api/encounters';
import { startTriageAssessment } from '../../shared/api/tatWorkflow';
import { fetchPatientById } from '../../shared/api/patients';
import {
  fetchOwnTriageDraft,
  fetchReferralCandidates,
  fetchVitalReferenceRanges,
  saveTriageDraft,
  submitTriageAssessment,
  type ImmediateReason,
  type ModeOfAmbulation,
  type ReferralCandidateResponse,
  type SubmitTriageAssessmentRequest,
  type TriageFormState,
  type TriagePriority,
  type VitalReferenceRangeResponse,
} from '../../shared/api/triage';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { formatMoney } from '../../shared/format/money';

// 06-triage.md §2/§4/§5/§6 — Screen 2, the Triage Assessment Form.
// No `infection_type` enum and no isolation_required auto-set.
//     infectionRisk is a bare boolean server-side; §7.5 of the real spec
//     flags this explicitly as "decoration... no downstream effect" and
//     unresolved, not a built feature this form can wire into.
// Similarly no "reason for choosing a specialist" field — no such column
// exists on SubmitTriageAssessmentCommand either.
//
// Danger signs (brief's "checklist, any tick forces IMMEDIATE") has no
// backend field of its own — there's nothing to submit it as. It's
// implemented here as checkboxes over the same ImmediateReason enum §4.1's
// confirmation dialog already uses: ticking one sets priority=IMMEDIATE
// and opens that exact confirmation, pre-selecting the reason when exactly
// one sign is ticked. One enum, two entry points, not a phantom field.
export function TriageAssessmentFormPage() {
  const { encounterId } = useParams<{ encounterId: string }>();
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const canAssess = usePermission('TRIAGE_ASSESS');
  const [triageStarted, setTriageStarted] = useState(false);
  const startRequested = useRef(false);
  const startTriage = useMutation({
    mutationFn: () => startTriageAssessment(encounterId as string, accessToken as string),
    onSuccess: () => setTriageStarted(true),
    onError: () => { startRequested.current = false; },
  });
  function beginAssessment() {
    if (!canAssess || !encounterId || !accessToken || startRequested.current) return;
    startRequested.current = true;
    startTriage.mutate();
  }

  const { data: encounter, isLoading: encounterLoading } = useQuery({
    queryKey: ['encounter', encounterId],
    queryFn: () => fetchEncounterById(encounterId as string, accessToken as string),
    enabled: accessToken != null && encounterId != null,
  });

  const { data: patient, isLoading: patientLoading } = useQuery({
    queryKey: ['patient', encounter?.patientId],
    queryFn: () => fetchPatientById(encounter?.patientId as string, accessToken as string),
    enabled: accessToken != null && encounter?.patientId != null,
  });

  const { data: ranges } = useQuery({
    queryKey: ['vital-reference-ranges', encounter?.patientId],
    queryFn: () => fetchVitalReferenceRanges(encounter?.patientId as string, accessToken as string),
    enabled: accessToken != null && encounter?.patientId != null,
  });

  const { data: referralCandidates, isLoading: candidatesLoading } = useQuery({
    queryKey: ['referral-candidates', encounterId],
    queryFn: () => fetchReferralCandidates(encounterId as string, accessToken as string),
    enabled: accessToken != null && encounterId != null,
  });

  const { data: ownDraft } = useQuery({
    queryKey: ['triage-draft', 'own', encounterId],
    queryFn: () => fetchOwnTriageDraft(encounterId as string, accessToken as string),
    enabled: accessToken != null && encounterId != null,
    staleTime: Infinity,
  });

  const patientAge = ageFromDob(patient?.dateOfBirth);
  const suppressBmi = patientAge != null && patientAge < 18;
  // Age bounds are configuration in the real system (protocol_setting,
  // V11's seeded triage.pregnancy_prompt.min_age_years/max_age_years) —
  // no endpoint exposes that setting today, so this mirrors the seeded
  // defaults (12/50) rather than the live value an admin may have retuned.
  const pregnancyApplicable = patient?.gender === 'FEMALE' && patientAge != null && patientAge >= 12 && patientAge <= 50;

  const schema = useMemo(() => buildSchema(pregnancyApplicable ?? false), [pregnancyApplicable]);
  type Values = z.infer<typeof schema>;

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    getValues,
    reset,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { hasPain: false, fallRisk: false, infectionRisk: false, dangerSigns: [] },
  });

  const [hydrated, setHydrated] = useState(false);
  useEffect(() => {
    if (hydrated) return;
    if (ownDraft?.formState && Object.keys(ownDraft.formState).length > 0) {
      reset(ownDraft.formState as unknown as Values);
      setHydrated(true);
    } else if (ownDraft === null) {
      setHydrated(true);
    }
  }, [ownDraft, hydrated, reset]);

  const priority = watch('priority');
  const hasPain = watch('hasPain');
  const fallRisk = watch('fallRisk');
  const immediateReason = watch('immediateReason');
  const dangerSigns = watch('dangerSigns') ?? [];
  const weightKg = watch('weightKg');
  const heightCm = watch('heightCm');

  const [immediateConfirmed, setImmediateConfirmed] = useState(false);
  useEffect(() => {
    if (priority !== 'IMMEDIATE') {
      setImmediateConfirmed(false);
    }
  }, [priority]);
  const showImmediateModal = priority === 'IMMEDIATE' && !immediateConfirmed;

  const dangerSignsCount = dangerSigns.length;
  useEffect(() => {
    if (dangerSignsCount > 0 && priority !== 'IMMEDIATE') {
      setValue('priority', 'IMMEDIATE', { shouldValidate: true });
    }
  }, [dangerSignsCount, priority, setValue]);

  const [abnormalVitals, setAbnormalVitals] = useState<Record<string, boolean>>({});

  const saveDraft = useMutation({
    mutationFn: (payload: { priority?: TriagePriority; modeOfAmbulation?: ModeOfAmbulation; formState: TriageFormState }) =>
      saveTriageDraft(encounterId as string, payload, accessToken as string),
  });

  const triggerAutosave = useCallback(() => {
    if (!encounterId || accessToken == null) return;
    const values = getValues();
    saveDraft.mutate({
      priority: values.priority || undefined,
      formState: values as unknown as TriageFormState,
    });
  }, [encounterId, accessToken, getValues, saveDraft]);

  // 06-triage.md §1/§3: autosave every 20s, keyed server-side to
  // (encounter_id, author_id) — so a device failure resumes elsewhere with
  // the draft intact, and the queue's "being assessed by" line stays live.
  useEffect(() => {
    if (!hydrated) return;
    const interval = setInterval(triggerAutosave, 20_000);
    return () => clearInterval(interval);
  }, [hydrated, triggerAutosave]);

  const submit = useMutation({
    mutationFn: (payload: SubmitTriageAssessmentRequest) =>
      submitTriageAssessment(encounterId as string, payload, accessToken as string),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['triage-queue'] });
      navigate('/triage');
    },
  });

  function handleVitalBlur(field: keyof Values, vitalType: VitalReferenceRangeResponse['vitalType'], label: string) {
    const value = getValues(field) as unknown as number;
    if (value == null || Number.isNaN(value)) return;
    const range = (ranges ?? []).find((r) => r.vitalType === vitalType);
    if (!range) {
      setAbnormalVitals((prev) => ({ ...prev, [field]: false }));
      return;
    }
    if (range.implausibleLow != null && value < range.implausibleLow) {
      setError(field as never, { type: 'manual', message: `${label} must be at least ${range.implausibleLow}` });
      return;
    }
    if (range.implausibleHigh != null && value > range.implausibleHigh) {
      setError(field as never, { type: 'manual', message: `${label} must be at most ${range.implausibleHigh}` });
      return;
    }
    clearErrors(field as never);
    const abnormal =
      (range.abnormalLow != null && value < range.abnormalLow) || (range.abnormalHigh != null && value > range.abnormalHigh);
    setAbnormalVitals((prev) => ({ ...prev, [field]: abnormal }));
  }

  function handleConfirmImmediate(reason: ImmediateReason, otherText: string) {
    setValue('immediateReason', reason, { shouldValidate: true });
    setValue('immediateReasonOtherText', reason === 'OTHER' ? otherText : '');
    setImmediateConfirmed(true);
    triggerAutosave();
  }

  function handleCancelImmediate() {
    setValue('dangerSigns', []);
    setValue('priority', '' as never);
    setValue('immediateReason', undefined);
    setValue('immediateReasonOtherText', '');
    // Persist the cleared state now, not on the next 20s tick — otherwise
    // navigating away leaves a stale draft with danger signs still set, and
    // on the next visit the "any danger sign -> IMMEDIATE" effect re-forces
    // the modal the nurse just dismissed.
    triggerAutosave();
  }

  const [formError, setFormError] = useState<string | null>(null);

  const onSubmit = handleSubmit(
    (values) => {
      setFormError(null);
      if (encounter?.version == null) {
        setFormError('This encounter is still loading. Wait a moment and submit again.');
        return;
      }
      const payload: SubmitTriageAssessmentRequest = {
      expectedEncounterVersion: encounter.version,
      assignedProviderId: values.assignedProviderId,
      hasPain: values.hasPain,
      painScore: values.hasPain ? values.painScore : undefined,
      painLocation: values.hasPain ? values.painLocation : undefined,
      fallRisk: values.fallRisk,
      fallScore: values.fallRisk ? values.fallScore : undefined,
      infectionRisk: values.infectionRisk,
      pregnancyStatus: pregnancyApplicable ? values.pregnancyStatus : undefined,
      // Priority is optional in the UI — the backend column is NOT NULL and
      // downstream queue ordering assumes a value, so we default a blank
      // selection to NON_URGENT rather than reject on submit.
      priority: values.priority ?? 'NON_URGENT',
      immediateReason: values.priority === 'IMMEDIATE' ? values.immediateReason : undefined,
      immediateReasonOtherText: values.immediateReason === 'OTHER' ? values.immediateReasonOtherText : undefined,
      notes: values.notes || undefined,
      temperatureC: values.temperatureC,
      systolicMmHg: values.systolicMmHg,
      diastolicMmHg: values.diastolicMmHg,
      pulseBpm: values.pulseBpm,
      respiratoryRate: values.respiratoryRate,
      spo2Pct: values.spo2Pct,
      weightKg: values.weightKg,
      heightCm: values.heightCm,
    };
      submit.mutate(payload);
    },
    (fieldErrors) => {
      // handleSubmit swallows an invalid form silently — no callback, no
      // network. Name every field that's blocking it (some are far down the
      // form; the referral picker and a forced-IMMEDIATE reason have no
      // ordinary inline error of their own), then jump to the first.
      const keys = Object.keys(fieldErrors);
      const names = keys.map((k) => FIELD_LABELS[k] ?? k);
      setFormError(
        names.length > 0
          ? `Can't submit yet — check: ${names.join(', ')}.`
          : "Can't submit yet — one or more fields are invalid.",
      );
      const firstKey = keys[0];
      const named = firstKey ? document.querySelector<HTMLElement>(`[name="${firstKey}"]`) : null;
      const target = named ?? document.querySelector<HTMLElement>('.field-error');
      target?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      named?.focus?.({ preventScroll: true });
    },
  );

  if (!canAssess) {
    return <p style={{ color: 'var(--text-muted)' }}>You don't hold TRIAGE_ASSESS.</p>;
  }
  if (encounterLoading || patientLoading || candidatesLoading || !hydrated) {
    return <FeedbackState loading>Loading…</FeedbackState>;
  }

  const bmi = weightKg && heightCm ? weightKg / Math.pow(heightCm / 100, 2) : undefined;

  return (
    <div>
      <PageHeader title={<>Triage assessment</>} description={<>{patient?.fullName} · {patient?.hospitalNo} · {patientAge != null ? `${patientAge}y` : '—'}</>} />

      <div style={{ marginBottom: 'var(--space-3)' }}>
        <button type="button" className="btn btn-secondary" disabled={!canAssess || triageStarted || startTriage.isPending}
          onClick={beginAssessment}>
          {triageStarted ? 'Assessment started' : 'Begin assessment'}
        </button>
        {startTriage.isError && <p className="form-error">Could not record the assessment start. Try again.</p>}
      </div>

      <form onSubmit={onSubmit} onChangeCapture={beginAssessment} noValidate className="clinical-form-layout">
        <FormSection title="Danger signs — any tick escalates to IMMEDIATE" columns={1} className="form-section--wide" tone="critical">

          <div className="choice-grid">
            {immediateReasonOptions.map((opt) => (
              <Checkbox key={opt.value} className="clinical-choice" checked={dangerSigns.includes(opt.value)} onChange={(e) => {
                    const next = e.target.checked
                      ? [...dangerSigns, opt.value]
                      : dangerSigns.filter((v) => v !== opt.value);
                    setValue('dangerSigns', next);
                  }} label={<>{opt.label}</>} />
            ))}
          </div>
        </FormSection>

        <FormSection title="Vital signs" columns={3} className="form-section--wide">

          {VITAL_FIELDS.map((v) => (
            <FormRow key={v.key} label={`${v.label} (${v.unit})`} error={errors[v.key]?.message as string | undefined}>
              <input className="clinical-control"
                type="number"
                step="any"
                {...register(v.key, { valueAsNumber: true, onBlur: () => handleVitalBlur(v.key, v.vitalType, v.label) })}
                style={fieldStyle}
              />
              {abnormalVitals[v.key] && !errors[v.key] && (
                <span style={{ color: 'var(--warning)', fontSize: 'var(--text-sm)' }}>Abnormal — verify and proceed if correct.</span>
              )}
            </FormRow>
          ))}
        </FormSection>

        <FormSection title="Anthropometric" columns={2} className="form-section--wide">

          <FormRow label="Weight (kg)" error={errors.weightKg?.message}>
            <input className="clinical-control" type="number" step="any" {...register('weightKg', { valueAsNumber: true })} style={fieldStyle} />
          </FormRow>
          <FormRow label="Height (cm)" error={errors.heightCm?.message}>
            <input className="clinical-control" type="number" step="any" {...register('heightCm', { valueAsNumber: true })} style={fieldStyle} />
          </FormRow>
          {!suppressBmi && bmi != null && !Number.isNaN(bmi) && (
            <p style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>BMI: {bmi.toFixed(1)} (computed, not enterable)</p>
          )}
        </FormSection>

        <FormSection title="Pain assessment" columns={1}>

          <Checkbox  className="clinical-choice" {...register('hasPain')} label={<>Patient has pain</>} />
          {hasPain && (
            <>
              <FormRow label="Pain score (0–10)" error={errors.painScore?.message}>
                <input className="clinical-control" type="number" min={0} max={10} {...register('painScore', { valueAsNumber: true })} style={fieldStyle} />
              </FormRow>
              <FormRow label="Pain location" error={errors.painLocation?.message}>
                <input className="clinical-control" {...register('painLocation')} style={fieldStyle} />
              </FormRow>
            </>
          )}
        </FormSection>

        <FormSection title="Risk assessment" columns={1}>

          <Checkbox  className="clinical-choice" {...register('fallRisk')} label={<>Fall risk</>} />
          {fallRisk && (
            <FormRow label="Fall risk score" error={errors.fallScore?.message}>
              <input className="clinical-control" type="number" {...register('fallScore', { valueAsNumber: true })} style={fieldStyle} />
            </FormRow>
          )}
          <Checkbox  className="clinical-choice" {...register('infectionRisk')} label={<>Infection risk</>} />
        </FormSection>

        {pregnancyApplicable && (
          <FormSection title="Pregnancy status" columns={1}>

            <FormRow label="Pregnancy status" error={errors.pregnancyStatus?.message}>
              <select className="clinical-control" {...register('pregnancyStatus')} style={fieldStyle} defaultValue="">
                <option value="" disabled>
                  Choose…
                </option>
                {(['YES', 'NO', 'UNKNOWN'] as const).map((o) => (
                  <option key={o} value={o}>
                    {o}
                  </option>
                ))}
              </select>
            </FormRow>
          </FormSection>
        )}

        <FormSection title="Triage priority (optional)" columns={1}>

          <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)', margin: '0 0 var(--space-2)' }}>
            Leave blank to submit as non-urgent — the default assumed downstream.
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
            {(['IMMEDIATE', 'URGENT', 'LESS_URGENT', 'NON_URGENT'] as const).map((p) => (
              <Radio key={p} className="clinical-choice" value={p} {...register('priority')} label={<>{p.replaceAll('_', ' ').toLowerCase()}</>} />
            ))}
          </div>
          {errors.priority && <span className="field-error">{errors.priority.message}</span>}
          {errors.immediateReason && (
            <span className="field-error">Reopen the IMMEDIATE confirmation and choose a reason.</span>
          )}
          {errors.immediateReasonOtherText && (
            <span className="field-error">{errors.immediateReasonOtherText.message}</span>
          )}
          {priority === 'IMMEDIATE' && immediateConfirmed && (
            <p style={{ color: 'var(--critical)', fontSize: 'var(--text-sm)' }}>
              Reason: {immediateReason ? immediateReasonOptions.find((o) => o.value === immediateReason)?.label : '—'}
            </p>
          )}
        </FormSection>

        <FormSection title="Referral (required)" columns={1} className="form-section--wide">

          {errors.assignedProviderId && <span className="field-error">{errors.assignedProviderId.message}</span>}
          <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)', marginTop: 0 }}>
            On-duty status isn't tracked yet — every provider below is shown without one.
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
            {(referralCandidates ?? []).map((candidate) => (
              <ReferralCard
                key={candidate.providerId}
                candidate={candidate}
                selected={watch('assignedProviderId') === candidate.providerId}
                onSelect={() => setValue('assignedProviderId', candidate.providerId as string, { shouldValidate: true })}
              />
            ))}
            {(referralCandidates ?? []).length === 0 && <FeedbackState>No providers available.</FeedbackState>}
          </div>
        </FormSection>

        <FormSection title="Notes (optional)" columns={1} className="form-section--wide">

          <textarea aria-label="Additional triage notes" className="clinical-control" rows={3} {...register('notes')} style={fieldStyle} />
        </FormSection>

        {formError && <div className="form-error">{formError}</div>}
        {submit.error && <div className="form-error">{submitErrorMessage(submit.error)}</div>}

        <div className="form-actions"><p>Review the assessment and referral before submitting.</p><button type="submit" className="btn btn-primary" disabled={submit.isPending}>
          {submit.isPending ? 'Submitting…' : 'Submit assessment'}
        </button></div>
      </form>

      {showImmediateModal && (
        <ImmediateConfirmationModal
          preselected={dangerSigns.length === 1 ? (dangerSigns[0] as ImmediateReason) : undefined}
          onConfirm={handleConfirmImmediate}
          onCancel={handleCancelImmediate}
        />
      )}
    </div>
  );
}

function ReferralCard({
  candidate,
  selected,
  onSelect,
}: {
  candidate: ReferralCandidateResponse;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className="referral-choice"
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: 'var(--space-3)',
        border: selected ? '2px solid var(--green-600)' : '1px solid var(--border)',
        borderRadius: 'var(--radius)',
        background: 'var(--bg-surface)',
        cursor: 'pointer',
        textAlign: 'left',
        font: 'inherit',
      }}
    >
      <span>
        <strong>{candidate.username}</strong>
        <span className="badge badge-neutral" style={{ marginLeft: 'var(--space-2)' }}>
          {candidate.roleLabel}
        </span>
        <br />
        <span style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>
          {candidate.queueDepth} waiting
        </span>
      </span>
      <span style={{ fontWeight: 'var(--weight-medium)' }} className="tabular">
        {candidate.feeAmountMinor != null ? formatMoney(candidate.feeAmountMinor, candidate.currencyCode) : 'Fee unavailable'}
      </span>
    </button>
  );
}

// 06-triage.md §4.1: "requires a REASON SELECTION, not an OK." Cancelling
// reverts priority entirely (and any danger signs that triggered it) —
// there is no partial-escalation state.
function ImmediateConfirmationModal({
  preselected,
  onConfirm,
  onCancel,
}: {
  preselected?: ImmediateReason;
  onConfirm: (reason: ImmediateReason, otherText: string) => void;
  onCancel: () => void;
}) {
  const [reason, setReason] = useState<ImmediateReason | undefined>(preselected);
  const [otherText, setOtherText] = useState('');

  const canConfirm = reason != null && (reason !== 'OTHER' || otherText.trim() !== '');

  return (
    <Modal open onClose={onCancel} title="Classifying as IMMEDIATE" closeOnBackdropClick={false}>
        <p style={{ color: 'var(--text-secondary)' }}>
          This patient will be given top priority everywhere and will not be blocked by payment. Choose the reason.
        </p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-1)', marginBottom: 'var(--space-3)' }}>
          {immediateReasonOptions.map((opt) => (
            <Radio key={opt.value} className="clinical-choice" checked={reason === opt.value} onChange={() => setReason(opt.value)} label={<>{opt.label}</>} />
          ))}
        </div>
        {reason === 'OTHER' && (
          <FormRow label="Describe the reason">
            <input className="clinical-control" value={otherText} onChange={(e) => setOtherText(e.target.value)} style={fieldStyle} />
          </FormRow>
        )}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-2)', marginTop: 'var(--space-3)' }}>
          <button type="button" className="btn btn-ghost" onClick={onCancel}>
            Not immediate — clear
          </button>
          <button
            type="button"
            className="btn btn-primary"
            disabled={!canConfirm}
            onClick={() => reason && onConfirm(reason, otherText)}
          >
            Confirm IMMEDIATE
          </button>
        </div>
    </Modal>
  );
}


function submitErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Your session expired. Sign in again, then reopen this assessment. Your saved draft will be restored.';
    }
    if (error.status === 409) {
      return `${error.message} Reload the queue and try again.`;
    }
    return error.message;
  }
  return 'Something went wrong. Check the values and try again.';
}

function ageFromDob(dob: string | undefined | null): number | null {
  if (!dob) return null;
  return Math.floor((Date.now() - new Date(dob).getTime()) / (365.25 * 24 * 60 * 60 * 1000));
}

// Field-key -> human label for the "can't submit yet" summary.
const FIELD_LABELS: Record<string, string> = {
  temperatureC: 'Temperature',
  systolicMmHg: 'Systolic BP',
  diastolicMmHg: 'Diastolic BP',
  pulseBpm: 'Pulse',
  respiratoryRate: 'Respiratory rate',
  spo2Pct: 'SpO₂',
  weightKg: 'Weight',
  heightCm: 'Height',
  painScore: 'Pain score',
  painLocation: 'Pain location',
  fallScore: 'Fall risk score',
  pregnancyStatus: 'Pregnancy status',
  priority: 'Triage priority',
  immediateReason: 'IMMEDIATE reason',
  immediateReasonOtherText: 'IMMEDIATE reason description',
  assignedProviderId: 'Referral — click a doctor card',
};

const immediateReasonOptions: { value: ImmediateReason; label: string }[] = [
  { value: 'THREATENED_OR_OBSTRUCTED_AIRWAY', label: 'Threatened or obstructed airway' },
  { value: 'SEVERE_RESPIRATORY_DISTRESS', label: 'Severe respiratory distress' },
  { value: 'SHOCK_OR_CIRCULATORY_COLLAPSE', label: 'Shock / circulatory collapse' },
  { value: 'UNRESPONSIVE_OR_RESPONDING_TO_PAIN_ONLY', label: 'Unresponsive or responding only to pain' },
  { value: 'ACTIVE_UNCONTROLLED_BLEEDING', label: 'Active uncontrolled bleeding' },
  { value: 'ACTIVE_CONVULSION', label: 'Active convulsion' },
  { value: 'OBSTETRIC_EMERGENCY', label: 'Obstetric emergency' },
  { value: 'OTHER', label: 'Other' },
];

const VITAL_FIELDS = [
  { key: 'temperatureC' as const, label: 'Temperature', unit: '°C', vitalType: 'TEMPERATURE' as const },
  { key: 'systolicMmHg' as const, label: 'Systolic BP', unit: 'mmHg', vitalType: 'SYSTOLIC_BP' as const },
  { key: 'diastolicMmHg' as const, label: 'Diastolic BP', unit: 'mmHg', vitalType: 'DIASTOLIC_BP' as const },
  { key: 'pulseBpm' as const, label: 'Pulse', unit: 'bpm', vitalType: 'PULSE' as const },
  { key: 'respiratoryRate' as const, label: 'Respiratory rate', unit: '/min', vitalType: 'RESPIRATORY_RATE' as const },
  { key: 'spo2Pct' as const, label: 'SpO₂', unit: '%', vitalType: 'SPO2' as const },
];

function buildSchema(pregnancyApplicable: boolean) {
  return z
    .object({
      temperatureC: z.number({ invalid_type_error: 'Enter the temperature' }),
      systolicMmHg: z.number({ invalid_type_error: 'Enter the systolic BP' }),
      diastolicMmHg: z.number({ invalid_type_error: 'Enter the diastolic BP' }),
      pulseBpm: z.number({ invalid_type_error: 'Enter the pulse' }),
      respiratoryRate: z.number({ invalid_type_error: 'Enter the respiratory rate' }),
      spo2Pct: z.number({ invalid_type_error: 'Enter the SpO2' }),
      weightKg: z.number({ invalid_type_error: 'Enter the weight' }).positive('Enter a weight greater than 0'),
      heightCm: z.number({ invalid_type_error: 'Enter the height' }).positive('Enter a height greater than 0'),
      hasPain: z.boolean(),
      painScore: z.number().optional(),
      painLocation: z.string().optional(),
      fallRisk: z.boolean(),
      fallScore: z.number().optional(),
      infectionRisk: z.boolean(),
      pregnancyStatus: z.enum(['YES', 'NO', 'UNKNOWN']).optional(),
      // Optional. An untouched radio group hands react-hook-form `null`, and
      // cancelling the IMMEDIATE modal sets it to '' — neither of which a bare
      // `.optional()` enum accepts ("Expected '…', received null"). Coerce both
      // empties to undefined so a blank selection validates; submit then
      // defaults it to NON_URGENT (the value downstream queue ordering assumes).
      priority: z.preprocess(
        (v) => (v === '' || v == null ? undefined : v),
        z.enum(['IMMEDIATE', 'URGENT', 'LESS_URGENT', 'NON_URGENT']).optional(),
      ),
      immediateReason: z
        .enum([
          'THREATENED_OR_OBSTRUCTED_AIRWAY', 'SEVERE_RESPIRATORY_DISTRESS', 'SHOCK_OR_CIRCULATORY_COLLAPSE',
          'UNRESPONSIVE_OR_RESPONDING_TO_PAIN_ONLY', 'ACTIVE_UNCONTROLLED_BLEEDING', 'ACTIVE_CONVULSION',
          'OBSTETRIC_EMERGENCY', 'OTHER',
        ])
        .optional(),
      immediateReasonOtherText: z.string().optional(),
      notes: z.string().max(2000).optional(),
      assignedProviderId: z.string().min(1, 'Choose a referral'),
      dangerSigns: z.array(z.string()).optional(),
    })
    .superRefine((v, ctx) => {
      if (v.hasPain) {
        if (v.painScore != null && !Number.isNaN(v.painScore) && (v.painScore < 0 || v.painScore > 10)) {
          ctx.addIssue({ path: ['painScore'], code: z.ZodIssueCode.custom, message: 'Pain score must be 0 to 10' });
        }
        if (!v.painLocation || v.painLocation.trim() === '') {
          ctx.addIssue({ path: ['painLocation'], code: z.ZodIssueCode.custom, message: 'Enter where the pain is' });
        }
      }
      // fallScore is optional even when fallRisk is true — mirrors the pain
      // score relaxation (V77): the nurse can flag the risk without pinning
      // a numeric on it. Backend + DB CHECK relaxed to match (V79).
      if (v.priority === 'IMMEDIATE') {
        if (!v.immediateReason) {
          ctx.addIssue({ path: ['immediateReason'], code: z.ZodIssueCode.custom, message: 'Choose a reason' });
        } else if (v.immediateReason === 'OTHER' && (!v.immediateReasonOtherText || v.immediateReasonOtherText.trim() === '')) {
          ctx.addIssue({ path: ['immediateReasonOtherText'], code: z.ZodIssueCode.custom, message: 'Describe the reason' });
        }
      }
      if (pregnancyApplicable && !v.pregnancyStatus) {
        ctx.addIssue({ path: ['pregnancyStatus'], code: z.ZodIssueCode.custom, message: 'Choose a pregnancy status' });
      }
    });
}

const fieldStyle: CSSProperties = {
  width: '100%',
  marginTop: 'var(--space-1)',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};
