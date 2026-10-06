import { FeedbackState } from '../../shared/components/FeedbackState';
import { TableRegion } from '../../shared/components/TableRegion';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { fetchVitalSignsByEncounter, recordWardVitals } from '../../shared/api/vitalSigns';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDateTime } from '../../shared/format/date';
import type { AdmissionResponse } from '../../shared/api/admissions';

const vitalsSchema = z.object({
  temperatureC: z.coerce.number({ invalid_type_error: 'Enter the temperature' }),
  systolicMmHg: z.coerce.number({ invalid_type_error: 'Enter the systolic BP' }),
  diastolicMmHg: z.coerce.number({ invalid_type_error: 'Enter the diastolic BP' }),
  pulseBpm: z.coerce.number({ invalid_type_error: 'Enter the pulse' }),
  respiratoryRate: z.coerce.number({ invalid_type_error: 'Enter the respiratory rate' }),
  spo2Pct: z.coerce.number({ invalid_type_error: 'Enter the SpO2' }),
  weightKg: z.coerce.number({ invalid_type_error: 'Enter the weight' }),
  heightCm: z.coerce.number({ invalid_type_error: 'Enter the height' }),
  avpu: z.enum(['ALERT', 'VOICE', 'PAIN', 'UNRESPONSIVE']).optional(),
  bloodGlucoseMmol: z.coerce.number().optional(),
  painScore: z.coerce.number().int().min(0).max(10).optional(),
  intakeMl: z.coerce.number().int().optional(),
  outputMl: z.coerce.number().int().optional(),
});
type VitalsValues = z.infer<typeof vitalsSchema>;

// 17-ward-nurse.md §4: ward-rounds observations, including §4.4's fluid
// balance (intake_ml/output_ml). Alert thresholds and the covering-doctor
// notification are server-side (RecordWardVitalsService) — this form only
// submits readings and reflects the suggested-CRITICAL flag back.
export function PatientCareHubVitalsTab({ admission }: { admission: AdmissionResponse }) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);

  const { data: vitals, isLoading } = useQuery({
    queryKey: ['vital-signs', admission.encounterId],
    queryFn: () => fetchVitalSignsByEncounter(admission.encounterId as string, accessToken as string),
    enabled: accessToken != null && admission.encounterId != null,
  });
  const rows = (vitals ?? [])
    .filter((v) => v.context === 'WARD_ROUNDS')
    .sort((a, b) => new Date(b.occurredAt ?? 0).getTime() - new Date(a.occurredAt ?? 0).getTime());

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<VitalsValues>({ resolver: zodResolver(vitalsSchema) });

  const submit = useMutation({
    mutationFn: (values: VitalsValues) =>
      recordWardVitals(
        admission.id as string,
        { ...values, occurredAt: new Date().toISOString() },
        accessToken as string,
      ),
    onSuccess: () => {
      reset();
      setShowForm(false);
      queryClient.invalidateQueries({ queryKey: ['vital-signs', admission.encounterId] });
      queryClient.invalidateQueries({ queryKey: ['admissions', admission.id] });
      queryClient.invalidateQueries({ queryKey: ['ward-alerts', admission.id] });
    },
  });

  return (
    <div>
      {admission.wardStatusSuggestedCriticalAt && admission.wardStatus !== 'CRITICAL' && (
        <div role="alert" style={{ background: 'var(--critical-bg)', color: 'var(--critical)', borderRadius: 'var(--radius)', padding: 'var(--space-3)', marginBottom: 'var(--space-4)', fontWeight: 'var(--weight-medium)' }}>
          ⚠ A breaching vital suggested CRITICAL status at {formatDateTime(admission.wardStatusSuggestedCriticalAt)} —
          confirm or dismiss on the Status & bed tab.
        </div>
      )}

      <button type="button" className="btn btn-primary" style={{ marginBottom: 'var(--space-4)' }} onClick={() => setShowForm((v) => !v)}>
        {showForm ? 'Cancel' : 'Record vitals'}
      </button>

      {showForm && (
        <form
          onSubmit={handleSubmit((v) => submit.mutate(v))}
          noValidate
          style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 'var(--space-3)', background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)', marginBottom: 'var(--space-5)' }}
        >
          <Field label="Temperature (°C)" error={errors.temperatureC?.message}>
            <input className="clinical-control" type="text" inputMode="decimal" {...register('temperatureC')} style={inputStyle} />
          </Field>
          <Field label="Systolic (mmHg)" error={errors.systolicMmHg?.message}>
            <input className="clinical-control" type="text" inputMode="decimal" {...register('systolicMmHg')} style={inputStyle} />
          </Field>
          <Field label="Diastolic (mmHg)" error={errors.diastolicMmHg?.message}>
            <input className="clinical-control" type="text" inputMode="decimal" {...register('diastolicMmHg')} style={inputStyle} />
          </Field>
          <Field label="Pulse (bpm)" error={errors.pulseBpm?.message}>
            <input className="clinical-control" type="text" inputMode="decimal" {...register('pulseBpm')} style={inputStyle} />
          </Field>
          <Field label="Respiratory rate" error={errors.respiratoryRate?.message}>
            <input className="clinical-control" type="text" inputMode="decimal" {...register('respiratoryRate')} style={inputStyle} />
          </Field>
          <Field label="SpO2 (%)" error={errors.spo2Pct?.message}>
            <input className="clinical-control" type="text" inputMode="decimal" {...register('spo2Pct')} style={inputStyle} />
          </Field>
          <Field label="Weight (kg)" error={errors.weightKg?.message}>
            <input className="clinical-control" type="text" inputMode="decimal" {...register('weightKg')} style={inputStyle} />
          </Field>
          <Field label="Height (cm)" error={errors.heightCm?.message}>
            <input className="clinical-control" type="text" inputMode="decimal" {...register('heightCm')} style={inputStyle} />
          </Field>
          <Field label="AVPU">
            <select className="clinical-control" {...register('avpu')} style={inputStyle}>
              <option value="">—</option>
              <option value="ALERT">Alert</option>
              <option value="VOICE">Voice</option>
              <option value="PAIN">Pain</option>
              <option value="UNRESPONSIVE">Unresponsive</option>
            </select>
          </Field>
          <Field label="Blood glucose (mmol/L)">
            <input className="clinical-control" type="text" inputMode="decimal" {...register('bloodGlucoseMmol')} style={inputStyle} />
          </Field>
          <Field label="Pain score (0–10)">
            <input className="clinical-control" type="text" inputMode="numeric" {...register('painScore')} style={inputStyle} />
          </Field>
          <Field label="Intake (mL)">
            <input className="clinical-control" type="text" inputMode="numeric" {...register('intakeMl')} style={inputStyle} />
          </Field>
          <Field label="Output (mL)">
            <input className="clinical-control" type="text" inputMode="numeric" {...register('outputMl')} style={inputStyle} />
          </Field>

          <div style={{ gridColumn: '1 / -1' }}>
            {submit.error && <div className="form-error" style={{ marginBottom: 'var(--space-2)' }}>Something went wrong. Try again.</div>}
            <button type="submit" className="btn btn-primary" disabled={submit.isPending}>
              {submit.isPending ? 'Saving…' : 'Save vitals'}
            </button>
          </div>
        </form>
      )}

      <section style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
        {isLoading ? (
          <FeedbackState loading>Loading…</FeedbackState>
        ) : rows.length === 0 ? (
          <FeedbackState>No ward-rounds vitals recorded yet.</FeedbackState>
        ) : (
          <TableRegion label="Vitals history"><table className="table">
            <caption className="sr-only">Vitals history</caption>
            <thead>
              <tr>
                <th>Time</th>
                <th>Readings</th>
                <th>AVPU</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((v) => (
                <tr key={v.id}>
                  <td className="tabular">{formatDateTime(v.occurredAt)}</td>
                  <td style={{ fontSize: 'var(--text-sm)' }}>
                    {Object.entries(v.readings ?? {})
                      .map(([k, val]) => `${k} ${val}`)
                      .join(' · ')}
                  </td>
                  <td>{v.avpu ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table></TableRegion>
        )}
      </section>
    </div>
  );
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <label style={{ fontSize: 'var(--text-sm)' }}>
      {label}
      {children}
      {error && <div className="field-error">{error}</div>}
    </label>
  );
}

const inputStyle: React.CSSProperties = {
  display: 'block',
  width: '100%',
  marginTop: 4,
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};
