import { Checkbox } from '../../shared/components/Checkbox';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { fetchAnaesthesiaRecords, recordAnaesthesia } from '../../shared/api/anaesthesiaRecords';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDateTime } from '../../shared/format/date';

const anaesthesiaSchema = z.object({
  noAnaesthesia: z.boolean().optional(),
  anaesthetistNameText: z.string().optional(),
  anaesthesiaTypeText: z.string().optional(),
  paperRecordReference: z.string().optional(),
});
type AnaesthesiaValues = z.infer<typeof anaesthesiaSchema>;

// 17-ward-nurse.md §10: a pointer to the paper anaesthesia record — this
// codebase does not chart a structured anaesthesia record, only who gave
// it and where the paper copy is filed.
export function SurgeryEpisodeAnaesthesia({ surgeryOrderId }: { surgeryOrderId: string }) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);

  const { data } = useQuery({
    queryKey: ['anaesthesia-records', surgeryOrderId],
    queryFn: () => fetchAnaesthesiaRecords(surgeryOrderId, accessToken as string),
    enabled: accessToken != null,
  });
  const rows = data ?? [];

  const { register, handleSubmit, reset } = useForm<AnaesthesiaValues>({ resolver: zodResolver(anaesthesiaSchema) });

  const submit = useMutation({
    mutationFn: (values: AnaesthesiaValues) => recordAnaesthesia({ surgeryOrderId, ...values }, accessToken as string),
    onSuccess: () => {
      reset();
      setShowForm(false);
      queryClient.invalidateQueries({ queryKey: ['anaesthesia-records', surgeryOrderId] });
    },
  });

  return (
    <div>
      {rows.length > 0 && (
        <ul style={{ margin: '0 0 var(--space-3)', padding: 0, listStyle: 'none' }}>
          {rows.map((r) => (
            <li key={r.id} style={{ fontSize: 'var(--text-sm)', padding: '4px 0', borderBottom: '1px solid var(--border)' }}>
              {formatDateTime(r.recordedAt)} — {r.noAnaesthesia ? 'No anaesthesia' : `${r.anaesthetistNameText ?? '—'} · ${r.anaesthesiaTypeText ?? '—'}`}
              {r.paperRecordReference && <span style={{ color: 'var(--text-muted)' }}> · Paper ref: {r.paperRecordReference}</span>}
            </li>
          ))}
        </ul>
      )}

      <button type="button" className="btn btn-secondary" onClick={() => setShowForm((v) => !v)}>
        {showForm ? 'Cancel' : 'Record anaesthesia pointer'}
      </button>

      {showForm && (
        <form
          onSubmit={handleSubmit((v) => submit.mutate(v))}
          noValidate
          style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', maxWidth: 480, marginTop: 'var(--space-3)', padding: 'var(--space-3)', background: 'var(--bg-raised)', border: '1px solid var(--border)', borderRadius: 'var(--radius)' }}
        >
          <Checkbox  className="clinical-choice" {...register('noAnaesthesia')} label={<>No anaesthesia given</>} />
          <label style={{ fontSize: 'var(--text-sm)' }}>
            Anaesthetist
            <input className="clinical-control" type="text" {...register('anaesthetistNameText')} style={inputStyle} />
          </label>
          <label style={{ fontSize: 'var(--text-sm)' }}>
            Anaesthesia type
            <input className="clinical-control" type="text" {...register('anaesthesiaTypeText')} style={inputStyle} />
          </label>
          <label style={{ fontSize: 'var(--text-sm)' }}>
            Paper record reference
            <input className="clinical-control" type="text" {...register('paperRecordReference')} style={inputStyle} />
          </label>
          {submit.error && <div className="form-error">Something went wrong. Try again.</div>}
          <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
            {submit.isPending ? 'Saving…' : 'Save'}
          </button>
        </form>
      )}
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
