import { Radio } from '../../shared/components/Radio';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { z } from 'zod';
import { completeChecklist, fetchChecklistsBySurgeryOrder, type ChecklistPhase } from '../../shared/api/surgicalChecklists';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDateTime } from '../../shared/format/date';

const PHASES: ChecklistPhase[] = ['SIGN_IN', 'TIME_OUT', 'SIGN_OUT'];

// 17-ward-nurse.md §10/screen 5: SIGN_IN / TIME_OUT / SIGN_OUT, each
// blocking the next stage — enforced server-side (ChecklistNotSatisfiedException
// on start/complete). SIGN_OUT's swab count is never waivable: countCorrect
// is always asked, isEmergencyReduced never skips it.
export function SurgeryEpisodeChecklist({ surgeryOrderId }: { surgeryOrderId: string }) {
  const { accessToken } = useAuth();
  const { data } = useQuery({
    queryKey: ['surgical-checklists', surgeryOrderId],
    queryFn: () => fetchChecklistsBySurgeryOrder(surgeryOrderId, accessToken as string),
    enabled: accessToken != null,
  });
  const byPhase = new Map((data ?? []).map((c) => [c.phase, c]));
  const nextPhase = PHASES.find((p) => !byPhase.has(p));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
      {PHASES.map((phase) => {
        const completed = byPhase.get(phase);
        return (
          <div key={phase} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: 'var(--space-2) 0', borderBottom: '1px solid var(--border)' }}>
            <div>
              <div style={{ fontWeight: 'var(--weight-medium)' }}>{phase.replace('_', ' ')}</div>
              {completed && (
                <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
                  {formatDateTime(completed.completedAt)}
                  {phase === 'SIGN_OUT' && (
                    <span style={{ marginLeft: 'var(--space-2)' }}>
                      {completed.countCorrect ? (
                        <span className="badge badge-success">Count correct</span>
                      ) : (
                        <span className="badge badge-critical">Count discrepancy: {completed.countDiscrepancyNote}</span>
                      )}
                    </span>
                  )}
                </div>
              )}
            </div>
            {completed ? (
              <span className="badge badge-success">Done</span>
            ) : phase === nextPhase ? null : (
              <span className="badge badge-neutral">Waiting</span>
            )}
          </div>
        );
      })}

      {nextPhase && <ChecklistForm surgeryOrderId={surgeryOrderId} phase={nextPhase} />}
    </div>
  );
}

const checklistSchema = z
  .object({
    items: z.array(z.object({ value: z.string().min(1) })),
    countCorrect: z.boolean().optional(),
    countDiscrepancyNote: z.string().optional(),
  })
  .refine((v) => v.countCorrect !== false || !!v.countDiscrepancyNote?.trim(), {
    message: 'A discrepancy note is required when the count is not correct',
    path: ['countDiscrepancyNote'],
  });
type ChecklistValues = z.infer<typeof checklistSchema>;

function ChecklistForm({ surgeryOrderId, phase }: { surgeryOrderId: string; phase: ChecklistPhase }) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [expanded, setExpanded] = useState(false);
  const {
    register,
    control,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<ChecklistValues>({ resolver: zodResolver(checklistSchema), defaultValues: { items: [{ value: '' }] } });
  const { fields, append, remove } = useFieldArray({ control, name: 'items' });
  const countCorrect = watch('countCorrect');

  const submit = useMutation({
    mutationFn: (values: ChecklistValues) =>
      completeChecklist(
        {
          surgeryOrderId,
          phase,
          items: values.items.map((i) => i.value).filter(Boolean),
          countCorrect: phase === 'SIGN_OUT' ? values.countCorrect : undefined,
          countDiscrepancyNote: phase === 'SIGN_OUT' ? values.countDiscrepancyNote || undefined : undefined,
        },
        accessToken as string,
      ),
    onSuccess: () => {
      setExpanded(false);
      queryClient.invalidateQueries({ queryKey: ['surgical-checklists', surgeryOrderId] });
    },
  });

  if (!expanded) {
    return (
      <button type="button" className="btn btn-primary" style={{ alignSelf: 'flex-start' }} onClick={() => setExpanded(true)}>
        Complete {phase.replace('_', ' ')}
      </button>
    );
  }

  return (
    <form
      onSubmit={handleSubmit((v) => submit.mutate(v))}
      noValidate
      style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', padding: 'var(--space-3)', background: 'var(--bg-raised)', border: '1px solid var(--border)', borderRadius: 'var(--radius)' }}
    >
      <div style={{ fontSize: 'var(--text-sm)', fontWeight: 'var(--weight-medium)' }}>{phase.replace('_', ' ')} checklist items</div>
      {fields.map((field, index) => (
        <div key={field.id} style={{ display: 'flex', gap: 'var(--space-2)' }}>
          <input aria-label="Checklist item confirmed" className="clinical-control" type="text" {...register(`items.${index}.value` as const)} style={inputStyle} placeholder="Checklist item confirmed" />
          <button type="button" className="btn btn-ghost" onClick={() => remove(index)}>
            Remove
          </button>
        </div>
      ))}
      <button type="button" className="btn btn-secondary" style={{ alignSelf: 'flex-start' }} onClick={() => append({ value: '' })}>
        Add item
      </button>

      {phase === 'SIGN_OUT' && (
        <div style={{ marginTop: 'var(--space-2)' }}>
          <div style={{ fontSize: 'var(--text-sm)', fontWeight: 'var(--weight-medium)', color: 'var(--critical)' }}>
            Swab/instrument count — never waivable
          </div>
          <Radio  className="clinical-choice" value="true" {...register('countCorrect', { setValueAs: (v) => v === 'true' })} label={<>Count correct</>} />
          <Radio  className="clinical-choice" value="false" {...register('countCorrect', { setValueAs: (v) => v === 'true' })} label={<>Discrepancy</>} />
          {countCorrect === false && (
            <label style={{ fontSize: 'var(--text-sm)', display: 'block', marginTop: 4 }}>
              Discrepancy note
              <textarea className="clinical-control" rows={2} {...register('countDiscrepancyNote')} style={inputStyle} />
              {errors.countDiscrepancyNote && <div className="field-error">{errors.countDiscrepancyNote.message}</div>}
            </label>
          )}
        </div>
      )}

      {submit.error && <div className="form-error">Something went wrong. Try again.</div>}
      <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
        <button type="submit" className="btn btn-primary" disabled={submit.isPending}>
          {submit.isPending ? 'Saving…' : `Confirm ${phase.replace('_', ' ')}`}
        </button>
        <button type="button" className="btn btn-secondary" onClick={() => setExpanded(false)}>
          Cancel
        </button>
      </div>
    </form>
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
