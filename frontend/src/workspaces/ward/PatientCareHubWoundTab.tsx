import { Checkbox } from '../../shared/components/Checkbox';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { TableRegion } from '../../shared/components/TableRegion';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { fetchWoundCare, recordWoundCare } from '../../shared/api/woundCare';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDateTime } from '../../shared/format/date';
import type { AdmissionResponse } from '../../shared/api/admissions';

const woundSchema = z.object({
  appearance: z.string().min(1, 'Describe the wound appearance'),
  dressingChanged: z.boolean().optional(),
  dressingType: z.string().optional(),
  signsOfInfection: z.boolean().optional(),
  infectionDescription: z.string().optional(),
  remarks: z.string().optional(),
});
type WoundValues = z.infer<typeof woundSchema>;

// 17-ward-nurse.md §6: signsOfInfection=true notifies the covering doctor
// immediately, server-side — nothing further for this screen to do.
export function PatientCareHubWoundTab({ admission }: { admission: AdmissionResponse }) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);

  const { data: wounds, isLoading } = useQuery({
    queryKey: ['wound-care', admission.id],
    queryFn: () => fetchWoundCare(admission.id as string, accessToken as string),
    enabled: accessToken != null && admission.id != null,
  });
  const rows = [...(wounds ?? [])].sort(
    (a, b) => new Date(b.recordedAt ?? 0).getTime() - new Date(a.recordedAt ?? 0).getTime(),
  );

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<WoundValues>({ resolver: zodResolver(woundSchema) });
  const signsOfInfection = watch('signsOfInfection');

  const submit = useMutation({
    mutationFn: (values: WoundValues) => recordWoundCare(admission.id as string, values, accessToken as string),
    onSuccess: () => {
      reset();
      setShowForm(false);
      queryClient.invalidateQueries({ queryKey: ['wound-care', admission.id] });
    },
  });

  return (
    <div>
      <button type="button" className="btn btn-primary" style={{ marginBottom: 'var(--space-4)' }} onClick={() => setShowForm((v) => !v)}>
        {showForm ? 'Cancel' : 'Record wound care'}
      </button>

      {showForm && (
        <form
          onSubmit={handleSubmit((v) => submit.mutate(v))}
          noValidate
          style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', maxWidth: 560, background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)', marginBottom: 'var(--space-5)' }}
        >
          <label style={{ fontSize: 'var(--text-sm)' }}>
            Appearance
            <textarea className="clinical-control" rows={2} {...register('appearance')} style={inputStyle} />
            {errors.appearance && <div className="field-error">{errors.appearance.message}</div>}
          </label>

          <Checkbox  className="clinical-choice" {...register('dressingChanged')} label={<>Dressing changed</>} />

          <label style={{ fontSize: 'var(--text-sm)' }}>
            Dressing type
            <input className="clinical-control" type="text" {...register('dressingType')} style={inputStyle} />
          </label>

          <Checkbox  className="clinical-choice" {...register('signsOfInfection')} label={<>Signs of infection</>} />

          {signsOfInfection && (
            <label style={{ fontSize: 'var(--text-sm)' }}>
              Infection description
              <textarea className="clinical-control" rows={2} {...register('infectionDescription')} style={inputStyle} />
            </label>
          )}

          <label style={{ fontSize: 'var(--text-sm)' }}>
            Remarks (optional)
            <textarea className="clinical-control" rows={2} {...register('remarks')} style={inputStyle} />
          </label>

          {signsOfInfection && (
            <p style={{ margin: 0, fontSize: 'var(--text-sm)', color: 'var(--critical)' }}>
              The covering doctor will be notified immediately on save.
            </p>
          )}

          {submit.error && <div className="form-error">Something went wrong. Try again.</div>}
          <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
            {submit.isPending ? 'Saving…' : 'Save'}
          </button>
        </form>
      )}

      <section style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
        {isLoading ? (
          <FeedbackState loading>Loading…</FeedbackState>
        ) : rows.length === 0 ? (
          <FeedbackState>No wound care recorded yet.</FeedbackState>
        ) : (
          <TableRegion label="Wound care history"><table className="table">
            <caption className="sr-only">Wound care history</caption>
            <thead>
              <tr>
                <th>Time</th>
                <th>Appearance</th>
                <th>Dressing</th>
                <th>Infection</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((w) => (
                <tr key={w.id} className={w.signsOfInfection ? 'row-critical' : undefined}>
                  <td className="tabular">{formatDateTime(w.recordedAt)}</td>
                  <td>{w.appearance}</td>
                  <td>{w.dressingChanged ? `Changed${w.dressingType ? ` — ${w.dressingType}` : ''}` : 'Not changed'}</td>
                  <td>
                    {w.signsOfInfection ? (
                      <span className="badge badge-critical">{w.infectionDescription || 'Yes'}</span>
                    ) : (
                      '—'
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table></TableRegion>
        )}
      </section>
    </div>
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
