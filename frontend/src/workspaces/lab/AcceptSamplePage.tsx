import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
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
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { quality: 'ADEQUATE' } });
  const quality = watch('quality');

  const submit = useMutation({
    mutationFn: (values: Values) =>
      collectSpecimen({ labOrderId: labOrderId as string, quality: values.quality, qualityNote: values.qualityNote || undefined }, accessToken as string),
    onSuccess: (specimen) => setResult(specimen),
  });

  const gateRejected = submit.error instanceof ApiError && submit.error.status === 402;

  if (result) {
    return <AcceptedConfirmation specimen={result} onDone={() => navigate('/lab')} />;
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
          <dl style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', margin: '0 0 var(--space-4)' }}>
            <dt style={{ fontWeight: 'var(--weight-medium)' }}>Sample type</dt>
            <dd style={{ margin: '0 0 var(--space-2)' }}>{order.specimenType}</dd>
            <dt style={{ fontWeight: 'var(--weight-medium)' }}>Container</dt>
            <dd style={{ margin: 0 }}>{order.containerType}</dd>
          </dl>

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

          <form onSubmit={handleSubmit((v) => submit.mutate(v))} noValidate style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
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
              {submit.isPending ? 'Submitting…' : quality === 'ADEQUATE' ? 'Accept sample' : 'Reject sample'}
            </button>
          </form>
        </div>
      )}
    </div>
  );
}

function AcceptedConfirmation({ specimen, onDone }: { specimen: SpecimenResponse; onDone: () => void }) {
  const rejected = specimen.status === 'REJECTED';
  return (
    <div
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-6)',
        maxWidth: 480,
        textAlign: 'center',
      }}
    >
      {rejected ? (
        <>
          <h2 style={{ color: 'var(--critical)', margin: '0 0 var(--space-2)' }}>Sample rejected</h2>
          <p style={{ color: 'var(--text-secondary)' }}>Reason: {specimen.rejectionReason}</p>
          <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
            The ordering doctor has been notified and a recollection requested.
          </p>
        </>
      ) : (
        <>
          <h2 style={{ color: 'var(--green-600)', margin: '0 0 var(--space-2)' }}>Sample accepted</h2>
          <p style={{ fontSize: 'var(--text-2xl)', fontFamily: 'var(--font-mono)', fontWeight: 'var(--weight-bold)', margin: '0 0 var(--space-2)' }}>
            {specimen.accessionNo}
          </p>
          <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>Label the container with this accession number.</p>
          <button type="button" className="btn btn-secondary" onClick={() => window.print()} style={{ marginTop: 'var(--space-3)' }}>
            Print barcode label
          </button>
        </>
      )}
      <div style={{ marginTop: 'var(--space-4)' }}>
        <button type="button" className="btn btn-primary" onClick={onDone}>
          Back to Test Queue
        </button>
      </div>
    </div>
  );
}
