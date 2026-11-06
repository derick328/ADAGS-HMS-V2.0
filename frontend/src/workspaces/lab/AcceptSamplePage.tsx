import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../shared/api/client';
import { fetchLabOrderQueue } from '../../shared/api/labOrders';
import { collectSpecimen, type SpecimenQuality, type SpecimenResponse } from '../../shared/api/specimens';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatMoney } from '../../shared/format/money';

// 15-lab-technician.md §4: quality assessment happens AT ACCEPT, not at
// result entry — deciding whether to process, before reagents/time are
// spent on a specimen that should be rejected.
const QUALITY_OPTIONS: { value: SpecimenQuality; label: string }[] = [
  { value: 'ADEQUATE', label: 'Adequate' },
  { value: 'HAEMOLYSED', label: 'Haemolysed' },
  { value: 'CLOTTED', label: 'Clotted' },
  { value: 'INSUFFICIENT_VOLUME', label: 'Insufficient volume' },
  { value: 'WRONG_CONTAINER', label: 'Wrong container' },
  { value: 'MISLABELLED', label: 'Mislabelled' },
  { value: 'DELAYED_TRANSPORT', label: 'Delayed transport' },
];

const schema = z
  .object({
    quality: z.enum([
      'ADEQUATE',
      'HAEMOLYSED',
      'CLOTTED',
      'INSUFFICIENT_VOLUME',
      'WRONG_CONTAINER',
      'MISLABELLED',
      'DELAYED_TRANSPORT',
    ]),
    qualityNote: z.string().optional(),
    sampleType: z.string().trim().min(1, 'Enter the assessed sample type'),
    sampleTypeOverrideReason: z.string().optional(),
  })
  .refine((v) => v.quality === 'ADEQUATE' || (v.qualityNote && v.qualityNote.trim().length > 0), {
    message: 'Enter a note explaining the quality issue',
    path: ['qualityNote'],
  });
type Values = z.infer<typeof schema>;

export function AcceptSamplePage() {
  const { labOrderId } = useParams<{ labOrderId: string }>();
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [result, setResult] = useState<SpecimenResponse | null>(null);

  // No single-order GET endpoint exists — the queue list (already the
  // source this screen is usually navigated from) is the one place order +
  // catalogue context lives, so it's read from the same shared query cache
  // TestQueuePage populates rather than duplicating a fetch.
  const { data: queue, isLoading } = useQuery({
    queryKey: ['lab-orders', 'queue'],
    queryFn: () => fetchLabOrderQueue(accessToken as string),
    enabled: accessToken != null,
  });
  const order = (queue ?? []).find((row) => row.labOrderId === labOrderId);

  const {
    register,
    handleSubmit,
    watch,
    getValues,
    setValue,
    setError,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { quality: 'ADEQUATE' } });
  const quality = watch('quality');
  const sampleType = watch('sampleType');
  const differsFromCatalogue = !!sampleType?.trim() && sampleType.trim().toLowerCase() !== order?.specimenType?.trim().toLowerCase();

  useEffect(() => {
    if (!order) return;
    if (order.specimenStatus === 'IN_PROGRESS') {
      navigate(`/lab/result-entry/${labOrderId}`, { replace: true });
      return;
    }
    if (!getValues('sampleType') && order.specimenType && order.specimenType !== '—')
      setValue('sampleType', order.specimenType);
  }, [order, labOrderId, navigate, getValues, setValue]);

  const submit = useMutation({
    mutationFn: (values: Values) =>
      collectSpecimen({ labOrderId: labOrderId as string, quality: values.quality,
        qualityNote: values.qualityNote || undefined, sampleType: values.sampleType.trim(),
        sampleTypeOverrideReason: values.sampleTypeOverrideReason?.trim() || undefined }, accessToken as string),
    onSuccess: (specimen) => {
      queryClient.invalidateQueries({ queryKey: ['lab-orders', 'queue'] });
      if (specimen.status === 'IN_PROGRESS') navigate(`/lab/result-entry/${labOrderId}`, { replace: true });
      else setResult(specimen);
    },
  });

  const gateRejected = submit.error instanceof ApiError && submit.error.status === 402;

  if (result) {
    return <RejectedConfirmation specimen={result} onDone={() => navigate('/lab')} />;
  }

  return (
    <div>
      <PageHeader title={<>Accept sample</>}>{order && (
          <p style={{ color: 'var(--text-secondary)', margin: '4px 0 0' }}>
            {order.patientName} · {order.labTestName}
          </p>
        )}</PageHeader>

      {isLoading ? (
        <FeedbackState loading>Loading…</FeedbackState>
      ) : !order ? (
        <p style={{ color: 'var(--text-muted)' }}>Order not found in the queue — return to Test Queue and try again.</p>
      ) : (
        <div
          style={{
            background: 'var(--bg-surface)',
            border: '1px solid var(--border)',
            borderRadius: 'var(--radius-lg)',
            padding: 'var(--space-4)',
            maxWidth: 520,
          }}
        >
          {gateRejected && submit.error instanceof ApiError && (
            <div className="form-error" role="alert" style={{ marginBottom: 'var(--space-3)' }}>
              <p style={{ margin: '0 0 var(--space-2)' }}>
                Payment not settled — {formatMoney(
                  submit.error.body?.outstandingAmountMinor as number | undefined,
                  submit.error.body?.currencyCode as string | undefined,
                )}{' '}
                outstanding for {String(submit.error.body?.serviceName ?? 'this test')}.
              </p>
              <button type="button" className="btn btn-secondary" onClick={() => navigate('/lab')}>
                Send to cashier
              </button>
            </div>
          )}
          {submit.error && !gateRejected && (
            <div className="form-error" role="alert">
              Something went wrong. Try again.
            </div>
          )}

          <form onSubmit={handleSubmit((values) => {
            if (differsFromCatalogue && !values.sampleTypeOverrideReason?.trim()) {
              setError('sampleTypeOverrideReason', { message: 'Explain why the sample type differs from the catalogue' });
              return;
            }
            submit.mutate(values);
          })} noValidate style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            <div>
              <label htmlFor="sampleType" style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>Assessed sample type</label>
              <input id="sampleType" className="clinical-control" {...register('sampleType')}
                placeholder="e.g. Whole Blood (EDTA)" style={{ width: '100%', padding: 'var(--space-2)' }} />
              {order.specimenType && order.specimenType !== '—' && (
                <p style={{ margin: '4px 0 0', color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>
                  Expected for this test: {order.specimenType}
                </p>
              )}
              {errors.sampleType && <span className="field-error">{errors.sampleType.message}</span>}
            </div>
            {differsFromCatalogue && <div>
              <label htmlFor="sampleTypeOverrideReason" style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>
                Reason for using a different sample type
              </label>
              <textarea id="sampleTypeOverrideReason" className="clinical-control" rows={2}
                {...register('sampleTypeOverrideReason')} style={{ width: '100%', padding: 'var(--space-2)' }} />
              {errors.sampleTypeOverrideReason && <span className="field-error">{errors.sampleTypeOverrideReason.message}</span>}
            </div>}
            <div>
              <label htmlFor="quality" style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>
                Quality
              </label>
              <select className="clinical-control"
                id="quality"
                {...register('quality')}
                style={{ width: '100%', padding: 'var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', font: 'inherit' }}
              >
                {QUALITY_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>

            {quality !== 'ADEQUATE' && (
              <div>
                <label htmlFor="qualityNote" style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>
                  Quality note
                </label>
                <textarea className="clinical-control"
                  id="qualityNote"
                  rows={3}
                  {...register('qualityNote')}
                  style={{ width: '100%', padding: 'var(--space-2)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius)', font: 'inherit' }}
                />
                {errors.qualityNote && <span className="field-error">{errors.qualityNote.message}</span>}
                {quality === 'MISLABELLED' && (
                  <p style={{ color: 'var(--critical)', fontSize: 'var(--text-sm)', marginTop: 4 }}>
                    Mislabelling is a patient-safety incident — the clinical lead is alerted alongside the ordering doctor.
                  </p>
                )}
                <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)', marginTop: 4 }}>
                  The doctor is notified and a recollection is requested. The original charge stands — this is not billed again.
                </p>
              </div>
            )}

            <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
              {submit.isPending ? 'Saving sample…' : quality === 'ADEQUATE' ? 'Accept & Enter Result' : 'Reject sample'}
            </button>
          </form>
        </div>
      )}
    </div>
  );
}

function RejectedConfirmation({ specimen, onDone }: { specimen: SpecimenResponse; onDone: () => void }) {
  return (
    <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-6)', maxWidth: 480 }}>
      <h2 style={{ color: 'var(--critical)', margin: '0 0 var(--space-2)' }}>Sample rejected</h2>
      <p>Reason: {specimen.rejectionReason}</p>
      <p>The ordering doctor has been notified and a recollection requested.</p>
      <button type="button" className="btn btn-primary" onClick={onDone}>Back to Test Queue</button>
    </div>
  );
}
