import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../shared/api/client';
import { fetchResultEntryForm } from '../../shared/api/labOrders';
import {
  fetchCriticalResultNotificationsByResult,
  type CriticalResultNotificationResponse,
} from '../../shared/api/criticalResultNotifications';
import { recordLabResult, uploadMachineOutput, type LabResultResponse } from '../../shared/api/labResults';
import { useAuth } from '../../shared/auth/AuthProvider';

// 15-lab-technician.md §5.2: flag is computed server-side, never typed —
// the override control exists so a technician can contest an implausible
// flag with a stated reason, not so they can set the flag directly.
const OVERRIDE_OPTIONS = ['NORMAL', 'HIGH', 'LOW', 'CRITICAL'] as const;

const parameterRowSchema = z
  .object({
    parameterId: z.string(),
    resultValue: z
      .string()
      .min(1, 'Enter a value')
      .refine((v) => !Number.isNaN(Number(v)), 'Enter a numeric value'),
    overrideFlag: z.union([z.enum(OVERRIDE_OPTIONS), z.literal('')]).optional(),
    overrideReason: z.string().optional(),
  })
  .refine((v) => !v.overrideFlag || (v.overrideReason && v.overrideReason.trim().length > 0), {
    message: 'Enter a reason for overriding the flag',
    path: ['overrideReason'],
  });

const schema = z.object({
  parameters: z.array(parameterRowSchema),
  methodology: z.string().optional(),
  reagentsUsedNote: z.string().optional(),
  findings: z.string().optional(),
  interpretation: z.string().optional(),
  referenceRangeNote: z.string().optional(),
  conclusion: z.string().optional(),
});
type Values = z.infer<typeof schema>;

export function ResultEntryPage() {
  const { labOrderId } = useParams<{ labOrderId: string }>();
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const [machineOutput, setMachineOutput] = useState<{ objectKey: string; fileName: string } | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [released, setReleased] = useState<LabResultResponse | null>(null);

  const { data: form, isLoading } = useQuery({
    queryKey: ['lab-orders', 'result-entry-form', labOrderId],
    queryFn: () => fetchResultEntryForm(labOrderId as string, accessToken as string),
    enabled: accessToken != null && labOrderId != null,
  });

  const {
    register,
    control,
    handleSubmit,
    reset,
    watch,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { parameters: [] } });
  const { fields } = useFieldArray({ control, name: 'parameters' });
  const watchedParameters = watch('parameters');

  useEffect(() => {
    if (!form) return;
    reset({
      parameters: (form.parameters ?? []).map((p) => ({ parameterId: p.parameterId as string, resultValue: '', overrideFlag: '', overrideReason: '' })),
      methodology: '',
      reagentsUsedNote: '',
      findings: '',
      interpretation: '',
      referenceRangeNote: '',
      conclusion: '',
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form]);

  const submit = useMutation({
    mutationFn: (values: Values) =>
      recordLabResult(
        {
          specimenId: form?.specimenId as string,
          methodology: values.methodology || undefined,
          reagentsUsedNote: values.reagentsUsedNote || undefined,
          findings: values.findings || undefined,
          interpretation: values.interpretation || undefined,
          referenceRangeNote: values.referenceRangeNote || undefined,
          conclusion: values.conclusion || undefined,
          machineOutputObjectKey: machineOutput?.objectKey,
          parameterValues: values.parameters.map((p) => ({
            parameterId: p.parameterId,
            resultValue: Number(p.resultValue),
            overrideFlag: p.overrideFlag || undefined,
            overrideReason: p.overrideReason || undefined,
          })),
        },
        accessToken as string,
      ),
    onSuccess: (result) => setReleased(result),
  });

  async function onFileChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    setUploadError(null);
    setUploading(true);
    try {
      const { objectKey } = await uploadMachineOutput(file, accessToken as string);
      setMachineOutput({ objectKey, fileName: file.name });
    } catch {
      setUploadError('Upload failed — try again.');
    } finally {
      setUploading(false);
    }
  }

  const machineOutputMissing = form?.producesMachineOutput && !machineOutput;

  if (released) {
    return <ReleasedConfirmation result={released} onDone={() => navigate('/lab')} />;
  }

  return (
    <div>
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>Result entry</h1>
        {form && (
          <p style={{ color: 'var(--text-secondary)', margin: '4px 0 0' }}>
            {form.patientName} · {form.hospitalNumber} · {form.labTestName} · {form.accessionNo}
          </p>
        )}
      </header>

      {isLoading || !form ? (
        <p style={{ color: 'var(--text-muted)' }}>Loading…</p>
      ) : (
        <form
          onSubmit={handleSubmit((values) => {
            if (machineOutputMissing) return;
            submit.mutate(values);
          })}
          noValidate
          style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)', maxWidth: 900 }}
        >
          {form.clinicalIndication && (
            <p style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>
              Clinical indication: {form.clinicalIndication}
            </p>
          )}
          {form.specimenQuality && form.specimenQuality !== 'ADEQUATE' && (
            <p style={{ color: 'var(--warning)', fontSize: 'var(--text-sm)' }}>
              Specimen quality: {form.specimenQuality} — {form.specimenQualityNote}
            </p>
          )}

          <div
            style={{
              background: 'var(--bg-surface)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-lg)',
              overflow: 'hidden',
            }}
          >
            <table className="table">
              <caption className="sr-only">Result parameters</caption>
              <thead>
                <tr>
                  <th>Parameter</th>
                  <th>Reference range</th>
                  <th>Result</th>
                  <th>Override</th>
                </tr>
              </thead>
              <tbody>
                {fields.map((field, index) => {
                  const definition = form.parameters?.[index];
                  const rowValue = watchedParameters?.[index];
                  return (
                    <tr key={field.id}>
                      <td>
                        <div style={{ fontWeight: 'var(--weight-medium)' }}>{definition?.name}</div>
                        <div style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
                          {definition?.unit} {definition?.method && `· ${definition.method}`}
                        </div>
                      </td>
                      <td className="tabular" style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>
                        {definition?.normalLow != null && definition?.normalHigh != null
                          ? `${definition.normalLow} – ${definition.normalHigh}`
                          : '—'}
                      </td>
                      <td>
                        <input
                          type="text"
                          inputMode="decimal"
                          {...register(`parameters.${index}.resultValue` as const)}
                          style={{ width: 100, padding: 'var(--space-1) var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', font: 'inherit' }}
                        />
                        {errors.parameters?.[index]?.resultValue && (
                          <div className="field-error">{errors.parameters[index]?.resultValue?.message}</div>
                        )}
                      </td>
                      <td>
                        <select
                          {...register(`parameters.${index}.overrideFlag` as const)}
                          style={{ padding: 'var(--space-1)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', font: 'inherit' }}
                        >
                          <option value="">Computed</option>
                          {OVERRIDE_OPTIONS.map((o) => (
                            <option key={o} value={o}>
                              {o}
                            </option>
                          ))}
                        </select>
                        {rowValue?.overrideFlag && (
                          <input
                            type="text"
                            placeholder="Reason for override"
                            {...register(`parameters.${index}.overrideReason` as const)}
                            style={{ display: 'block', marginTop: 4, width: 160, padding: 'var(--space-1) var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', font: 'inherit' }}
                          />
                        )}
                        {errors.parameters?.[index]?.overrideReason && (
                          <div className="field-error">{errors.parameters[index]?.overrideReason?.message}</div>
                        )}
                      </td>
                    </tr>
                  );
                })}
                {fields.length === 0 && (
                  <tr>
                    <td colSpan={4} style={{ color: 'var(--text-muted)', textAlign: 'center', padding: 'var(--space-4)' }}>
                      This test has no coded parameters — release with the notes below only.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
            <TextField label="Methodology" name="methodology" register={register} />
            <TextField label="Reagents used" name="reagentsUsedNote" register={register} />
            <TextField label="Findings" name="findings" register={register} />
            <TextField label="Interpretation" name="interpretation" register={register} />
            <TextField label="Reference range note" name="referenceRangeNote" register={register} />
            <TextField label="Conclusion" name="conclusion" register={register} />
          </div>

          {form.producesMachineOutput && (
            <div>
              <label style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>
                Machine output <span style={{ color: 'var(--critical)' }}>(required)</span>
              </label>
              <input type="file" onChange={onFileChange} disabled={uploading} />
              {uploading && <div style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>Uploading…</div>}
              {machineOutput && <div style={{ color: 'var(--green-600)', fontSize: 'var(--text-sm)' }}>Attached: {machineOutput.fileName}</div>}
              {uploadError && <div className="field-error">{uploadError}</div>}
            </div>
          )}

          {submit.error && (
            <div className="form-error" role="alert">
              {submit.error instanceof ApiError ? submit.error.body?.detail ?? submit.error.message : 'Something went wrong.'}
            </div>
          )}
          {machineOutputMissing && (
            <div className="form-error" role="alert">
              Attach the machine output before submitting.
            </div>
          )}

          <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
            {submit.isPending ? 'Submitting…' : 'Release result'}
          </button>
        </form>
      )}
    </div>
  );
}

function TextField({
  label,
  name,
  register,
}: {
  label: string;
  name: keyof Values;
  register: ReturnType<typeof useForm<Values>>['register'];
}) {
  return (
    <div>
      <label htmlFor={name} style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>
        {label}
      </label>
      <textarea
        id={name}
        rows={2}
        {...register(name)}
        style={{ width: '100%', padding: 'var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', font: 'inherit' }}
      />
    </div>
  );
}

// §7: "any parameter flag = C on release -> immediate push notification...
// tech's screen shows acknowledgement status so they know it landed."
function ReleasedConfirmation({ result, onDone }: { result: LabResultResponse; onDone: () => void }) {
  const { accessToken } = useAuth();
  const hasCritical = (result.criticalNotificationIds ?? []).length > 0;

  const { data: notifications } = useQuery({
    queryKey: ['critical-result-notifications', 'by-result', result.id],
    queryFn: () => fetchCriticalResultNotificationsByResult(result.id as string, accessToken as string),
    enabled: hasCritical && accessToken != null,
    refetchInterval: 5_000,
  });

  return (
    <div
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-6)',
        maxWidth: 520,
        textAlign: 'center',
      }}
    >
      <h2 style={{ color: 'var(--green-600)', margin: '0 0 var(--space-2)' }}>Result released</h2>
      <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>The ordering doctor can see this result now.</p>

      {hasCritical && (
        <div
          style={{
            marginTop: 'var(--space-4)',
            padding: 'var(--space-3)',
            background: 'var(--critical-bg)',
            borderRadius: 'var(--radius)',
            textAlign: 'left',
          }}
        >
          <div style={{ color: 'var(--critical)', fontWeight: 'var(--weight-medium)', marginBottom: 4 }}>
            Critical value — doctor notified
          </div>
          {(notifications ?? []).map((n: CriticalResultNotificationResponse) => (
            <div key={n.id} style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
              {n.acknowledgedAt ? 'Acknowledged' : n.escalatedAt ? 'Escalated to clinical lead' : 'Awaiting acknowledgement…'}
            </div>
          ))}
        </div>
      )}

      <button type="button" className="btn btn-primary" onClick={onDone} style={{ marginTop: 'var(--space-4)' }}>
        Back to Test Queue
      </button>
    </div>
  );
}
