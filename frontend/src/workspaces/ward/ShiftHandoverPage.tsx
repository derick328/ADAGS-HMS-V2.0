import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { z } from 'zod';
import { fetchOverdueMar } from '../../shared/api/medicationAdministration';
import { fetchActiveWorkShifts } from '../../shared/api/shiftAssignments';
import {
  acknowledgeShiftHandover,
  fetchShiftHandovers,
  recordShiftHandover,
  type ShiftHandoverResponse,
} from '../../shared/api/shiftHandovers';
import { fetchWardAlerts } from '../../shared/api/wardAlerts';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import { formatDateTime } from '../../shared/format/date';
import { useWardContext } from './WardContext';
import { useWardPatients } from './useWardPatients';
import type { WardPatientRowResponse } from '../../shared/api/admissions';

// 17-ward-nurse.md §7: SBAR, outgoing nurse records, incoming nurse
// acknowledges. One handover row per admission (the schema's own shape —
// admission_id, not just ward_id), so this screen is the ward roster with
// each patient's handover status, not a single ward-wide note.
export function ShiftHandoverPage() {
  const { wardName } = useWardContext();
  const { data: patients, isLoading } = useWardPatients();
  const [openAdmissionId, setOpenAdmissionId] = useState<string | null>(null);
  const rows = patients ?? [];

  return (
    <div>
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>Shift Handover</h1>
        <p style={{ color: 'var(--text-secondary)', margin: '4px 0 0' }}>{wardName ?? 'Your ward'}</p>
      </header>

      {isLoading ? (
        <p style={{ color: 'var(--text-muted)' }}>Loading…</p>
      ) : rows.length === 0 ? (
        <p style={{ color: 'var(--text-muted)' }}>No patients currently on this ward.</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          {rows.map((row) => (
            <PatientHandoverCard
              key={row.admissionId}
              row={row}
              expanded={openAdmissionId === row.admissionId}
              onToggle={() => setOpenAdmissionId((id) => (id === row.admissionId ? null : (row.admissionId as string)))}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function PatientHandoverCard({
  row,
  expanded,
  onToggle,
}: {
  row: WardPatientRowResponse;
  expanded: boolean;
  onToggle: () => void;
}) {
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  const canAcknowledge = usePermission('SHIFT_HANDOVER_ACKNOWLEDGE');
  const canRecord = usePermission('SHIFT_HANDOVER_CREATE');
  const queryClient = useQueryClient();

  const { data: handovers } = useQuery({
    queryKey: ['shift-handovers', row.admissionId],
    queryFn: () => fetchShiftHandovers(row.admissionId as string, accessToken as string),
    enabled: accessToken != null,
  });
  const sorted = [...(handovers ?? [])].sort(
    (a, b) => new Date(b.handoverAt ?? 0).getTime() - new Date(a.handoverAt ?? 0).getTime(),
  );
  const latest = sorted[0];
  const unacknowledged = sorted.filter((h) => !h.acknowledgedAt);
  const mine = unacknowledged.filter((h) => h.incomingNurseId === me?.userId);

  const acknowledge = useMutation({
    mutationFn: (id: string) => acknowledgeShiftHandover(row.admissionId as string, id, accessToken as string),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['shift-handovers', row.admissionId] }),
  });

  return (
    <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: 'var(--space-3) var(--space-4)' }}>
        <div>
          <div style={{ fontWeight: 'var(--weight-medium)' }}>{row.patientName ?? 'Unknown patient'}</div>
          <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
            {row.bedLabel ?? '—'} · {latest ? `Last handover ${formatDateTime(latest.handoverAt)}` : 'No handover recorded yet'}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
          {unacknowledged.length > 0 && <span className="badge badge-warning">{unacknowledged.length} unacknowledged</span>}
          {mine.length > 0 && canAcknowledge && (
            <button type="button" className="btn btn-secondary" onClick={() => acknowledge.mutate(mine[0].id as string)} disabled={acknowledge.isPending}>
              Acknowledge
            </button>
          )}
          {canRecord && (
            <button type="button" className="btn btn-primary" onClick={onToggle}>
              {expanded ? 'Close' : 'Record handover'}
            </button>
          )}
        </div>
      </div>

      {sorted.length > 0 && (
        <div style={{ padding: '0 var(--space-4) var(--space-3)', display: 'flex', flexDirection: 'column', gap: 4 }}>
          {sorted.slice(0, 3).map((h) => (
            <HandoverSummaryRow key={h.id} handover={h} />
          ))}
        </div>
      )}

      {expanded && <HandoverForm admissionId={row.admissionId as string} onDone={onToggle} />}
    </div>
  );
}

function HandoverSummaryRow({ handover }: { handover: ShiftHandoverResponse }) {
  return (
    <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', borderTop: '1px solid var(--border)', paddingTop: 4 }}>
      <strong>{formatDateTime(handover.handoverAt)}</strong> — {handover.situation}
      {handover.acknowledgedAt ? (
        <span className="badge badge-success" style={{ marginLeft: 'var(--space-2)' }}>Acknowledged</span>
      ) : (
        <span className="badge badge-warning" style={{ marginLeft: 'var(--space-2)' }}>Pending</span>
      )}
    </div>
  );
}

const handoverSchema = z.object({
  shiftFrom: z.string().min(1, 'Select the outgoing shift'),
  shiftTo: z.string().optional(),
  situation: z.string().min(1, 'Enter the situation'),
  background: z.string().min(1, 'Enter the background'),
  assessment: z.string().min(1, 'Enter the assessment'),
  recommendation: z.string().min(1, 'Enter the recommendation'),
  outstandingTasks: z.array(z.object({ value: z.string().min(1, 'Enter a task') })),
});
type HandoverValues = z.infer<typeof handoverSchema>;

// §7: "Pre-populated with what the system already knows: alerting vitals,
// overdue MAR slots, pending lab results, doctor's orders added this
// shift." Assembled here into editable initial text — the nurse adjusts
// rather than starting from a blank page.
function HandoverForm({ admissionId, onDone }: { admissionId: string; onDone: () => void }) {
  const { accessToken } = useAuth();
  const { wardId } = useWardContext();
  const queryClient = useQueryClient();

  const { data: workShifts } = useQuery({
    queryKey: ['work-shifts'],
    queryFn: () => fetchActiveWorkShifts(accessToken as string),
    enabled: accessToken != null,
  });
  const { data: overdueMar } = useQuery({
    queryKey: ['mar', 'overdue', admissionId],
    queryFn: () => fetchOverdueMar(admissionId, accessToken as string),
    enabled: accessToken != null,
  });
  const { data: alerts } = useQuery({
    queryKey: ['ward-alerts', admissionId],
    queryFn: () => fetchWardAlerts(admissionId, accessToken as string),
    enabled: accessToken != null,
  });
  const alertingVitals = (alerts ?? []).filter((a) => a.sourceType === 'VITAL_BREACH' && !a.acknowledged);

  const situationDefault = [
    alertingVitals.length > 0 ? `${alertingVitals.length} unacknowledged vital alert(s): ${alertingVitals.map((a) => a.detail).join('; ')}.` : null,
    (overdueMar ?? []).length > 0 ? `${(overdueMar ?? []).length} overdue MAR dose(s).` : null,
  ]
    .filter(Boolean)
    .join(' ');

  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<HandoverValues>({
    resolver: zodResolver(handoverSchema),
    defaultValues: {
      shiftFrom: '',
      shiftTo: '',
      situation: situationDefault || 'Stable, no acute concerns.',
      background: '',
      assessment: '',
      recommendation: '',
      outstandingTasks: [],
    },
  });
  const { fields, append, remove } = useFieldArray({ control, name: 'outstandingTasks' });

  const submit = useMutation({
    mutationFn: (values: HandoverValues) =>
      recordShiftHandover(
        admissionId,
        {
          wardId: wardId as string,
          shiftFrom: values.shiftFrom,
          shiftTo: values.shiftTo || undefined,
          situation: values.situation,
          background: values.background,
          assessment: values.assessment,
          recommendation: values.recommendation,
          additionalOutstandingTasks: values.outstandingTasks.map((t) => t.value),
        },
        accessToken as string,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shift-handovers', admissionId] });
      onDone();
    },
  });

  return (
    <form
      onSubmit={handleSubmit((v) => submit.mutate(v))}
      noValidate
      style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', padding: 'var(--space-4)', borderTop: '1px solid var(--border)', background: 'var(--bg-raised)' }}
    >
      <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
        <label style={{ fontSize: 'var(--text-sm)', flex: 1 }}>
          Outgoing shift
          <select {...register('shiftFrom')} style={inputStyle}>
            <option value="">Select</option>
            {(workShifts ?? []).map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
          {errors.shiftFrom && <div className="field-error">{errors.shiftFrom.message}</div>}
        </label>
        <label style={{ fontSize: 'var(--text-sm)', flex: 1 }}>
          Incoming shift (optional)
          <select {...register('shiftTo')} style={inputStyle}>
            <option value="">—</option>
            {(workShifts ?? []).map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      <label style={{ fontSize: 'var(--text-sm)' }}>
        Situation
        <textarea rows={2} {...register('situation')} style={inputStyle} />
        {errors.situation && <div className="field-error">{errors.situation.message}</div>}
      </label>
      <label style={{ fontSize: 'var(--text-sm)' }}>
        Background
        <textarea rows={2} {...register('background')} style={inputStyle} />
        {errors.background && <div className="field-error">{errors.background.message}</div>}
      </label>
      <label style={{ fontSize: 'var(--text-sm)' }}>
        Assessment
        <textarea rows={2} {...register('assessment')} style={inputStyle} />
        {errors.assessment && <div className="field-error">{errors.assessment.message}</div>}
      </label>
      <label style={{ fontSize: 'var(--text-sm)' }}>
        Recommendation
        <textarea rows={2} {...register('recommendation')} style={inputStyle} />
        {errors.recommendation && <div className="field-error">{errors.recommendation.message}</div>}
      </label>

      <div>
        <div style={{ fontSize: 'var(--text-sm)', marginBottom: 4 }}>Outstanding tasks</div>
        {fields.map((field, index) => (
          <div key={field.id} style={{ display: 'flex', gap: 'var(--space-2)', marginBottom: 4 }}>
            <input type="text" {...register(`outstandingTasks.${index}.value` as const)} style={{ ...inputStyle, marginTop: 0 }} />
            <button type="button" className="btn btn-ghost" onClick={() => remove(index)}>
              Remove
            </button>
          </div>
        ))}
        <button type="button" className="btn btn-secondary" onClick={() => append({ value: '' })}>
          Add task
        </button>
      </div>

      {submit.error && <div className="form-error">Something went wrong. Try again.</div>}
      <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
        {submit.isPending ? 'Saving…' : 'Save handover'}
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
