import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../shared/api/client';
import { Combobox } from '../../shared/components/Combobox';
import { dispense, fetchDispenseContext, fetchDispenseLabel } from '../../shared/api/dispenses';
import { searchItems, type ItemSearchResultResponse } from '../../shared/api/items';
import { markPrescriptionLineUnavailable } from '../../shared/api/prescriptionLines';
import { fetchPrescriptionQueue } from '../../shared/api/prescriptions';
import { proposeSubstitution } from '../../shared/api/substitutions';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDate, formatDateTime } from '../../shared/format/date';
import { formatMoney } from '../../shared/format/money';

type Mode = 'dispense' | 'markUnavailable' | 'substitute';

const lineSchema = z.object({
  batchId: z.string().min(1, 'Select a batch'),
  quantity: z.coerce.number({ invalid_type_error: 'Enter a quantity' }).int().positive('Enter a quantity'),
  overrideReason: z.string().optional(),
});
const dispenseSchema = z.object({
  lines: z.array(lineSchema).min(1, 'Add at least one batch line'),
  counsellingNotes: z.string().optional(),
  dispensingNotes: z.string().optional(),
});
type DispenseValues = z.infer<typeof dispenseSchema>;

// 12-pharmacist.md §5.2/§5.3: allergies inline and unmissable (root
// CLAUDE.md "Safety"), weight and prescribed dose for sanity-checking,
// FEFO-suggested batch lines the pharmacist can override with a reason.
export function DispensePage() {
  const { prescriptionLineId } = useParams<{ prescriptionLineId: string }>();
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<Mode>('dispense');
  const [dispensedId, setDispensedId] = useState<string | null>(null);
  const [markedUnavailable, setMarkedUnavailable] = useState(false);
  const [substituted, setSubstituted] = useState(false);

  // Patient identity/dosage isn't part of the dispense-context response —
  // it's already in the queue row cache the pharmacist navigated from,
  // same "reuse the shared query cache" treatment as AcceptSamplePage.
  const { data: queue } = useQuery({
    queryKey: ['prescriptions', 'queue'],
    queryFn: () => fetchPrescriptionQueue(accessToken as string),
    enabled: accessToken != null,
  });
  const queueRow = (queue ?? []).find((row) => row.prescriptionLineId === prescriptionLineId);

  const { data: context, isLoading } = useQuery({
    queryKey: ['dispense-context', prescriptionLineId],
    queryFn: () => fetchDispenseContext(prescriptionLineId as string, accessToken as string),
    enabled: accessToken != null && prescriptionLineId != null,
  });

  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<DispenseValues>({ resolver: zodResolver(dispenseSchema), defaultValues: { lines: [] } });
  const { fields, append, remove } = useFieldArray({ control, name: 'lines' });
  const [originalSuggestion, setOriginalSuggestion] = useState<{ batchId: string; quantity: number }[]>([]);

  useEffect(() => {
    if (!context) return;
    const suggestion = (context.fefoSuggestion ?? []).map((a) => ({ batchId: a.batchId as string, quantity: a.quantity as number }));
    setOriginalSuggestion(suggestion);
    reset({ lines: suggestion.map((s) => ({ ...s, overrideReason: '' })), counsellingNotes: '', dispensingNotes: '' });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [context]);

  const submitDispense = useMutation({
    mutationFn: (values: DispenseValues) =>
      dispense(
        {
          prescriptionLineId: prescriptionLineId as string,
          idempotencyKey: crypto.randomUUID(),
          lines: values.lines.map((l) => ({ batchId: l.batchId, quantity: l.quantity, overrideReason: l.overrideReason || undefined })),
          counsellingNotes: values.counsellingNotes || undefined,
          dispensingNotes: values.dispensingNotes || undefined,
        },
        accessToken as string,
      ),
    onSuccess: (result) => {
      setDispensedId(result.id as string);
      queryClient.invalidateQueries({ queryKey: ['prescriptions', 'queue'] });
      queryClient.invalidateQueries({ queryKey: ['dispenses', 'mine'] });
    },
  });

  function onSubmitDispense(values: DispenseValues) {
    // D5: "Batch selection is FEFO-suggested, overridable with a reason."
    // Any line that departs from the original suggestion — a different
    // batch, a different quantity, or a manually added row — needs one.
    let hasMissingReason = false;
    values.lines.forEach((line, index) => {
      const suggested = originalSuggestion[index];
      const matchesSuggestion = suggested && suggested.batchId === line.batchId && suggested.quantity === line.quantity;
      if (!matchesSuggestion && !line.overrideReason?.trim()) {
        setError(`lines.${index}.overrideReason`, { message: 'Enter a reason for overriding the FEFO suggestion' });
        hasMissingReason = true;
      }
    });
    if (hasMissingReason) return;
    submitDispense.mutate(values);
  }

  const gateRejected = submitDispense.error instanceof ApiError && submitDispense.error.status === 402;

  if (dispensedId) {
    return <DispensedConfirmation dispenseId={dispensedId} onDone={() => navigate('/pharmacy')} />;
  }
  if (markedUnavailable) {
    return <ActionConfirmation message="Line marked unavailable. The doctor has been notified." onDone={() => navigate('/pharmacy')} />;
  }
  if (substituted) {
    return <ActionConfirmation message="Substitution proposed. The doctor has been notified to approve or reject." onDone={() => navigate('/pharmacy')} />;
  }

  return (
    <div>
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>Dispense</h1>
        {queueRow && (
          <p style={{ color: 'var(--text-secondary)', margin: '4px 0 0' }}>
            {queueRow.patientName ?? 'Unknown patient'}
            {queueRow.hospitalNumber && ` · ${queueRow.hospitalNumber}`} · {queueRow.itemName}
          </p>
        )}
      </header>

      {isLoading || !context ? (
        <p style={{ color: 'var(--text-muted)' }}>Loading…</p>
      ) : (
        <div style={{ maxWidth: 760 }}>
          <AllergyInlineWarning allergies={context.allergies ?? []} />

          <section
            style={{
              background: 'var(--bg-surface)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-lg)',
              padding: 'var(--space-4)',
              marginBottom: 'var(--space-4)',
            }}
          >
            <dl style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 'var(--space-2)', fontSize: 'var(--text-sm)', margin: 0 }}>
              <div>
                <dt style={{ color: 'var(--text-muted)' }}>Prescribed</dt>
                <dd style={{ margin: 0 }}>{context.prescriptionLine?.dosageInstructions}</dd>
              </div>
              <div>
                <dt style={{ color: 'var(--text-muted)' }}>Route / dose</dt>
                <dd style={{ margin: 0 }}>
                  {context.prescriptionLine?.route ?? '—'}
                  {context.prescriptionLine?.doseAmount != null && ` · ${context.prescriptionLine.doseAmount}${context.prescriptionLine.doseUnit ?? ''}`}
                  {context.prescriptionLine?.frequency && ` · ${context.prescriptionLine.frequency}`}
                </dd>
              </div>
              <div>
                <dt style={{ color: 'var(--text-muted)' }}>Remaining to dispense</dt>
                <dd style={{ margin: 0, fontWeight: 'var(--weight-medium)' }}>{context.prescriptionLine?.remaining}</dd>
              </div>
              {context.weightKg != null && (
                <div>
                  <dt style={{ color: 'var(--text-muted)' }}>Weight (paediatric check)</dt>
                  <dd style={{ margin: 0 }}>{context.weightKg} kg</dd>
                </div>
              )}
            </dl>
          </section>

          <div style={{ display: 'flex', gap: 'var(--space-2)', marginBottom: 'var(--space-4)' }}>
            <button type="button" className={mode === 'dispense' ? 'btn btn-primary' : 'btn btn-secondary'} onClick={() => setMode('dispense')}>
              Dispense
            </button>
            <button type="button" className={mode === 'markUnavailable' ? 'btn btn-primary' : 'btn btn-secondary'} onClick={() => setMode('markUnavailable')}>
              Mark unavailable
            </button>
            {context.prescriptionLine?.substitutionAllowed && (
              <button type="button" className={mode === 'substitute' ? 'btn btn-primary' : 'btn btn-secondary'} onClick={() => setMode('substitute')}>
                Propose substitution
              </button>
            )}
          </div>

          {mode === 'dispense' && (
            <form onSubmit={handleSubmit(onSubmitDispense)} noValidate style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
                <table className="table">
                  <caption className="sr-only">Batch lines</caption>
                  <thead>
                    <tr>
                      <th>Batch</th>
                      <th>Quantity</th>
                      <th>Override reason</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {fields.map((field, index) => (
                      <tr key={field.id}>
                        <td>
                          <select {...register(`lines.${index}.batchId` as const)} style={selectStyle}>
                            <option value="">Select a batch</option>
                            {(context.availableBatches ?? []).map((b) => (
                              <option key={b.batchId} value={b.batchId}>
                                {b.batchNumber} · exp {formatDate(b.expiryDate)} · {b.quantityAvailable} available
                              </option>
                            ))}
                          </select>
                          {errors.lines?.[index]?.batchId && <div className="field-error">{errors.lines[index]?.batchId?.message}</div>}
                        </td>
                        <td>
                          <input type="text" inputMode="numeric" {...register(`lines.${index}.quantity` as const)} style={{ ...inputStyle, width: 80 }} />
                          {errors.lines?.[index]?.quantity && <div className="field-error">{errors.lines[index]?.quantity?.message}</div>}
                        </td>
                        <td>
                          <input type="text" placeholder="Reason, if overriding FEFO" {...register(`lines.${index}.overrideReason` as const)} style={inputStyle} />
                          {errors.lines?.[index]?.overrideReason && <div className="field-error">{errors.lines[index]?.overrideReason?.message}</div>}
                        </td>
                        <td>
                          <button type="button" className="btn btn-ghost" onClick={() => remove(index)}>
                            Remove
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {(context.availableBatches ?? []).length === 0 && (
                  <p style={{ padding: 'var(--space-4)', color: 'var(--critical)' }}>No stock available for this item.</p>
                )}
              </div>
              {errors.lines?.root && <div className="field-error">{errors.lines.root.message}</div>}

              <button
                type="button"
                className="btn btn-secondary"
                style={{ alignSelf: 'flex-start' }}
                onClick={() => append({ batchId: '', quantity: 1, overrideReason: '' })}
              >
                Add batch line
              </button>

              <div>
                <label htmlFor="counsellingNotes" style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>
                  Counselling notes (optional)
                </label>
                <textarea id="counsellingNotes" rows={2} {...register('counsellingNotes')} style={inputStyle} />
              </div>
              <div>
                <label htmlFor="dispensingNotes" style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>
                  Dispensing notes (optional)
                </label>
                <textarea id="dispensingNotes" rows={2} {...register('dispensingNotes')} style={inputStyle} />
              </div>

              {gateRejected && submitDispense.error instanceof ApiError && (
                <div className="form-error" role="alert">
                  <p style={{ margin: '0 0 var(--space-2)' }}>
                    Payment not settled — {formatMoney(
                      submitDispense.error.body?.outstandingAmountMinor as number | undefined,
                      submitDispense.error.body?.currencyCode as string | undefined,
                    )}{' '}
                    outstanding for {String(submitDispense.error.body?.serviceName ?? 'this item')}.
                  </p>
                  <button type="button" className="btn btn-secondary" onClick={() => navigate('/pharmacy')}>
                    Send to cashier
                  </button>
                </div>
              )}
              {submitDispense.error && !gateRejected && (
                <div className="form-error" role="alert">
                  {submitDispense.error instanceof ApiError ? submitDispense.error.body?.detail ?? submitDispense.error.message : 'Something went wrong.'}
                </div>
              )}

              <button type="submit" className="btn btn-primary" disabled={submitDispense.isPending} style={{ alignSelf: 'flex-start' }}>
                {submitDispense.isPending ? 'Dispensing…' : 'Dispense'}
              </button>
            </form>
          )}

          {mode === 'markUnavailable' && (
            <MarkUnavailableForm prescriptionLineId={prescriptionLineId as string} onDone={() => setMarkedUnavailable(true)} />
          )}

          {mode === 'substitute' && (
            <SubstitutionForm prescriptionLineId={prescriptionLineId as string} onDone={() => setSubstituted(true)} />
          )}
        </div>
      )}
    </div>
  );
}

function AllergyInlineWarning({ allergies }: { allergies: { description?: string; reaction?: string; severity?: string; confirmationStatus?: string }[] }) {
  if (allergies.length === 0) {
    return (
      <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)', marginBottom: 'var(--space-4)' }}>
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
        marginBottom: 'var(--space-4)',
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

const unavailableSchema = z.object({ reason: z.string().min(1, 'Enter a reason') });
type UnavailableValues = z.infer<typeof unavailableSchema>;

function MarkUnavailableForm({ prescriptionLineId, onDone }: { prescriptionLineId: string; onDone: () => void }) {
  const { accessToken } = useAuth();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<UnavailableValues>({ resolver: zodResolver(unavailableSchema) });

  const submit = useMutation({
    mutationFn: (values: UnavailableValues) => markPrescriptionLineUnavailable(prescriptionLineId, { reason: values.reason }, accessToken as string),
    onSuccess: onDone,
  });

  return (
    <form
      onSubmit={handleSubmit((v) => submit.mutate(v))}
      noValidate
      style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', maxWidth: 480, background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)' }}
    >
      <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>
        The doctor is notified to re-prescribe or cancel. Any paid amount refunds through the accountant path; an unpaid charge voids automatically.
      </p>
      <div>
        <label htmlFor="unavailable-reason" style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>
          Reason
        </label>
        <textarea id="unavailable-reason" rows={2} {...register('reason')} style={inputStyle} />
        {errors.reason && <span className="field-error">{errors.reason.message}</span>}
      </div>
      {submit.error && <div className="form-error">Something went wrong. Try again.</div>}
      <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
        {submit.isPending ? 'Submitting…' : 'Mark unavailable'}
      </button>
    </form>
  );
}

const substitutionSchema = z.object({ proposedItemId: z.string().min(1, 'Select a replacement item'), reason: z.string().min(1, 'Enter a reason') });
type SubstitutionValues = z.infer<typeof substitutionSchema>;

function SubstitutionForm({ prescriptionLineId, onDone }: { prescriptionLineId: string; onDone: () => void }) {
  const { accessToken } = useAuth();
  const [selected, setSelected] = useState<ItemSearchResultResponse | null>(null);
  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<SubstitutionValues>({ resolver: zodResolver(substitutionSchema) });

  const submit = useMutation({
    mutationFn: (values: SubstitutionValues) =>
      proposeSubstitution(prescriptionLineId, { proposedItemId: values.proposedItemId, reason: values.reason }, accessToken as string),
    onSuccess: onDone,
  });

  return (
    <form
      onSubmit={handleSubmit((v) => submit.mutate(v))}
      noValidate
      style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', maxWidth: 480, background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)' }}
    >
      <div>
        <Combobox<ItemSearchResultResponse>
          label="Replacement item"
          placeholder="Search the formulary"
          search={(q) => searchItems(q, accessToken as string)}
          getKey={(i) => i.id as string}
          renderOption={(i) => (
            <span style={{ opacity: i.selectable === false ? 0.5 : 1 }}>
              {i.name} {i.strength && <span style={{ color: 'var(--text-muted)' }}>· {i.strength}</span>}
              {i.selectable === false && <span style={{ color: 'var(--critical)' }}> — {i.unselectableReason}</span>}
            </span>
          )}
          onSelect={(i) => {
            if (i.selectable === false) return;
            setSelected(i);
            setValue('proposedItemId', i.id as string, { shouldValidate: true });
          }}
          clearOnSelect={false}
        />
        {selected && <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginTop: 4 }}>Selected: {selected.name}</div>}
        {errors.proposedItemId && <span className="field-error">{errors.proposedItemId.message}</span>}
      </div>
      <div>
        <label htmlFor="substitution-reason" style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>
          Reason
        </label>
        <textarea id="substitution-reason" rows={2} {...register('reason')} style={inputStyle} />
        {errors.reason && <span className="field-error">{errors.reason.message}</span>}
      </div>
      {submit.error && <div className="form-error">Something went wrong. Try again.</div>}
      <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
        {submit.isPending ? 'Submitting…' : 'Propose substitution'}
      </button>
    </form>
  );
}

function ActionConfirmation({ message, onDone }: { message: string; onDone: () => void }) {
  return (
    <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-6)', maxWidth: 480, textAlign: 'center' }}>
      <p style={{ color: 'var(--text-secondary)' }}>{message}</p>
      <button type="button" className="btn btn-primary" onClick={onDone} style={{ marginTop: 'var(--space-3)' }}>
        Back to Dashboard
      </button>
    </div>
  );
}

// 12-pharmacist.md §5.6: the dispensing label — a patient handed
// unlabelled tablets is a dosing error waiting to happen.
function DispensedConfirmation({ dispenseId, onDone }: { dispenseId: string; onDone: () => void }) {
  const { accessToken } = useAuth();
  const [showLabel, setShowLabel] = useState(false);

  const { data: label } = useQuery({
    queryKey: ['dispenses', 'label', dispenseId],
    queryFn: () => fetchDispenseLabel(dispenseId, accessToken as string),
    enabled: showLabel && accessToken != null,
  });

  return (
    <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-6)', maxWidth: 520, textAlign: 'center' }}>
      <h2 style={{ color: 'var(--green-600)', margin: '0 0 var(--space-2)' }}>Dispensed</h2>
      <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>Hand the patient the labelled medicine.</p>

      {!showLabel && (
        <button type="button" className="btn btn-secondary" onClick={() => setShowLabel(true)} style={{ marginTop: 'var(--space-3)' }}>
          Show label
        </button>
      )}

      {showLabel && label && (
        <div style={{ marginTop: 'var(--space-4)', textAlign: 'left', border: '1px dashed var(--border-strong)', borderRadius: 'var(--radius)', padding: 'var(--space-3)', fontFamily: 'var(--font-mono)', fontSize: 'var(--text-sm)' }}>
          <div style={{ fontWeight: 'var(--weight-medium)' }}>{label.patientName} · {label.hospitalNumber}</div>
          <div>{label.drugName}</div>
          <div>{label.doseInstructions}</div>
          <div style={{ color: 'var(--text-muted)', marginTop: 4 }}>
            Dispensed {formatDateTime(label.dispensedDate)}
            {(label.batchLines ?? []).map((b, i) => (
              <span key={i}> · Exp {formatDate(b.expiryDate)} · Batch {b.batchNumber}</span>
            ))}
          </div>
          <button type="button" className="btn btn-secondary" onClick={() => window.print()} style={{ marginTop: 'var(--space-3)' }}>
            Print label
          </button>
        </div>
      )}

      <div style={{ marginTop: 'var(--space-4)' }}>
        <button type="button" className="btn btn-primary" onClick={onDone}>
          Back to Dashboard
        </button>
      </div>
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};

const selectStyle: React.CSSProperties = {
  width: '100%',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};
