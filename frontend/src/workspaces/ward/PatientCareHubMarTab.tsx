import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { fetchItemById } from '../../shared/api/items';
import { fetchPrescriptionLineById } from '../../shared/api/prescriptionLines';
import {
  fetchMar,
  markAdministration,
  type MedicationAdministrationResponse,
  type MedicationAdministrationStatus,
  type OmissionReason,
} from '../../shared/api/medicationAdministration';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { formatDateTime } from '../../shared/format/date';
import type { AdmissionResponse } from '../../shared/api/admissions';

const OMISSION_REASONS: OmissionReason[] = [
  'REFUSED_BY_PATIENT',
  'NIL_BY_MOUTH',
  'PATIENT_ABSENT',
  'DRUG_UNAVAILABLE',
  'CLINICAL_HOLD',
  'VOMITED',
  'IV_ACCESS_LOST',
  'DUPLICATE_DOSE',
];

const NON_GIVEN_STATUSES: MedicationAdministrationStatus[] = ['REFUSED', 'OMITTED', 'HELD'];

// 17-ward-nurse.md §5: generated slots, never typed — drug/dose/route are
// read-only from the prescription line. A DUE slot past its scheduled time
// is a visible OVERDUE row, not an absent one.
export function PatientCareHubMarTab({ admission }: { admission: AdmissionResponse }) {
  const { accessToken } = useAuth();
  const { data: mar, isLoading } = useQuery({
    queryKey: ['mar', admission.id],
    queryFn: () => fetchMar(admission.id as string, accessToken as string),
    enabled: accessToken != null && admission.id != null,
    refetchInterval: 20_000,
  });

  const rows = [...(mar ?? [])].sort(
    (a, b) => new Date(a.scheduledAt ?? 0).getTime() - new Date(b.scheduledAt ?? 0).getTime(),
  );

  if (isLoading) return <p style={{ color: 'var(--text-muted)' }}>Loading…</p>;
  if (rows.length === 0) return <p style={{ color: 'var(--text-muted)' }}>No medication schedule generated yet.</p>;

  return (
    <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
      <table className="table">
        <caption className="sr-only">Medication administration record</caption>
        <thead>
          <tr>
            <th>Due</th>
            <th>Drug</th>
            <th>Status</th>
            <th>Administered</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <MarRow key={row.id} admissionId={admission.id as string} row={row} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function MarRow({ admissionId, row }: { admissionId: string; row: MedicationAdministrationResponse }) {
  const { accessToken } = useAuth();
  const [expanded, setExpanded] = useState(false);
  const canWitness = usePermission('MEDICATION_WITNESS');

  const { data: line } = useQuery({
    queryKey: ['prescription-lines', row.prescriptionLineId],
    queryFn: () => fetchPrescriptionLineById(row.prescriptionLineId as string, accessToken as string),
    enabled: accessToken != null && row.prescriptionLineId != null,
    staleTime: 60_000,
  });
  const { data: item } = useQuery({
    queryKey: ['items', line?.itemId],
    queryFn: () => fetchItemById(line?.itemId as string, accessToken as string),
    enabled: accessToken != null && line?.itemId != null,
    staleTime: 60_000,
  });

  const now = Date.now();
  const isOverdue = row.status === 'DUE' && new Date(row.scheduledAt ?? 0).getTime() < now;

  return (
    <>
      <tr className={isOverdue ? 'row-critical' : undefined}>
        <td className="tabular">{formatDateTime(row.scheduledAt)}</td>
        <td>
          {item ? (
            <>
              <div style={{ fontWeight: 'var(--weight-medium)' }}>{item.name}</div>
              <div style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
                {line?.doseAmount != null && `${line.doseAmount}${line.doseUnit ?? ''}`}
                {line?.route && ` · ${line.route}`}
                {line?.frequency && ` · ${line.frequency}`}
              </div>
              {item.requiresAdministrationWitness && <span className="badge badge-warning">Witness required</span>}
            </>
          ) : (
            <span style={{ color: 'var(--text-muted)' }}>Loading…</span>
          )}
        </td>
        <td>
          <MarStatusBadge status={row.status} overdue={isOverdue} />
        </td>
        <td style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
          {row.administeredAt ? formatDateTime(row.administeredAt) : '—'}
          {row.omissionReason && <div>{row.omissionReason}</div>}
        </td>
        <td>
          {row.status === 'DUE' && (
            <button type="button" className="btn btn-primary" onClick={() => setExpanded((v) => !v)}>
              {expanded ? 'Cancel' : 'Mark'}
            </button>
          )}
        </td>
      </tr>
      {expanded && (
        <tr>
          <td colSpan={5}>
            <MarkForm
              admissionId={admissionId}
              marId={row.id as string}
              requiresWitness={item?.requiresAdministrationWitness ?? false}
              canWitness={canWitness}
              onDone={() => setExpanded(false)}
            />
          </td>
        </tr>
      )}
    </>
  );
}

function MarStatusBadge({ status, overdue }: { status?: string; overdue: boolean }) {
  if (overdue) return <span className="badge badge-critical">Overdue</span>;
  if (status === 'GIVEN') return <span className="badge badge-success">Given</span>;
  if (status === 'DUE') return <span className="badge badge-neutral">Due</span>;
  return <span className="badge badge-warning">{status}</span>;
}

const markSchema = z
  .object({
    status: z.enum(['GIVEN', 'REFUSED', 'OMITTED', 'HELD']),
    doseGiven: z.string().optional(),
    batchId: z.string().optional(),
    witnessUserId: z.string().optional(),
    omissionReason: z.enum(OMISSION_REASONS as [OmissionReason, ...OmissionReason[]]).optional(),
    omissionNotes: z.string().optional(),
    remarks: z.string().optional(),
  })
  .refine((v) => v.status === 'GIVEN' || v.omissionReason != null, {
    message: 'Select a reason',
    path: ['omissionReason'],
  });
type MarkValues = z.infer<typeof markSchema>;

function MarkForm({
  admissionId,
  marId,
  requiresWitness,
  canWitness,
  onDone,
}: {
  admissionId: string;
  marId: string;
  requiresWitness: boolean;
  canWitness: boolean;
  onDone: () => void;
}) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<MarkValues>({ resolver: zodResolver(markSchema), defaultValues: { status: 'GIVEN' } });
  const status = watch('status');

  const submit = useMutation({
    mutationFn: (values: MarkValues) =>
      markAdministration(
        admissionId,
        marId,
        {
          status: values.status,
          doseGiven: values.doseGiven || undefined,
          batchId: values.batchId || undefined,
          witnessUserId: values.witnessUserId || undefined,
          omissionReason: values.status === 'GIVEN' ? undefined : values.omissionReason,
          omissionNotes: values.omissionNotes || undefined,
          remarks: values.remarks || undefined,
        },
        accessToken as string,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mar', admissionId] });
      queryClient.invalidateQueries({ queryKey: ['mar', 'overdue', admissionId] });
      onDone();
    },
  });

  return (
    <form
      onSubmit={handleSubmit((v) => submit.mutate(v))}
      noValidate
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-3)',
        padding: 'var(--space-3)',
        background: 'var(--bg-raised)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius)',
      }}
    >
      <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
        <label style={{ fontSize: 'var(--text-sm)' }}>
          Outcome
          <select {...register('status')} style={selectStyle}>
            <option value="GIVEN">Given</option>
            {NON_GIVEN_STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>

        {status === 'GIVEN' ? (
          <label style={{ fontSize: 'var(--text-sm)' }}>
            Dose given
            <input type="text" {...register('doseGiven')} style={inputStyle} />
          </label>
        ) : (
          <label style={{ fontSize: 'var(--text-sm)' }}>
            Reason
            <select {...register('omissionReason')} style={selectStyle}>
              <option value="">Select a reason</option>
              {OMISSION_REASONS.map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
            {errors.omissionReason && <div className="field-error">{errors.omissionReason.message}</div>}
          </label>
        )}

        {status === 'GIVEN' && (
          <label style={{ fontSize: 'var(--text-sm)' }}>
            Batch (from the ward dispense, if known)
            <input type="text" {...register('batchId')} style={inputStyle} placeholder="Batch ID" />
          </label>
        )}
      </div>

      {requiresWitness && (
        <div>
          {canWitness ? (
            <label style={{ fontSize: 'var(--text-sm)', display: 'block' }}>
              Witness user ID — a second person, different from you
              <input type="text" {...register('witnessUserId')} style={inputStyle} placeholder="Witnessing nurse's user ID" />
            </label>
          ) : (
            <p className="field-error" style={{ margin: 0 }}>
              This item requires a two-person witness, and you do not hold MEDICATION_WITNESS — ask a colleague who does to witness and enter their user ID.
            </p>
          )}
        </div>
      )}

      <label style={{ fontSize: 'var(--text-sm)' }}>
        Remarks (optional)
        <textarea rows={2} {...register('remarks')} style={inputStyle} />
      </label>

      {status !== 'GIVEN' && (
        <label style={{ fontSize: 'var(--text-sm)' }}>
          Notes (optional)
          <textarea rows={2} {...register('omissionNotes')} style={inputStyle} />
        </label>
      )}

      {submit.error && <div className="form-error">Something went wrong. Try again.</div>}

      <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
        {submit.isPending ? 'Saving…' : 'Save'}
      </button>
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
const selectStyle = inputStyle;
