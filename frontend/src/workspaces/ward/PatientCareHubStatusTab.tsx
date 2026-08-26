import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import {
  assignBed,
  completeDischarge,
  orderDischarge,
  reverseDischargeOrder,
  updateWardStatus,
  type PlacementReason,
  type WardStatus,
} from '../../shared/api/admissions';
import { fetchFreeBeds } from '../../shared/api/wards';
import { ApiError } from '../../shared/api/client';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { formatDateTime } from '../../shared/format/date';
import { StatusBadge } from './DashboardPage';
import { useWardContext } from './WardContext';
import type { AdmissionResponse } from '../../shared/api/admissions';

// 17-ward-nurse.md §9/§8/04-procedure-bedday-rules.md §3.6-§3.9. Status
// here is ADMITTED/STABLE/CRITICAL only — DISCHARGED runs through the
// discharge flow below, and TRANSFERRED has no settable path in this build
// (UpdateWardStatusService rejects it; ward-to-ward transfer would need
// visibility into a second ward this nurse isn't scoped to, §3's "never all
// wards"). Discharge is NEVER blocked by an unpaid balance.
const SETTABLE_STATUSES: WardStatus[] = ['ADMITTED', 'STABLE', 'CRITICAL'];

const PLACEMENT_REASONS: PlacementReason[] = ['NO_BED_IN_ORDERED_CLASS', 'CLINICAL_NEED', 'PATIENT_REQUEST', 'OTHER'];

export function PatientCareHubStatusTab({ admission }: { admission: AdmissionResponse }) {
  const canUpdateStatus = usePermission('WARD_STATUS_UPDATE');
  const canAssignBed = usePermission('BED_ASSIGN');
  const canDischarge = usePermission('DISCHARGE_ORDER');

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-4)' }}>
      <section style={panelStyle}>
        <h2 style={panelTitleStyle}>Status</h2>
        <p style={{ margin: '0 0 var(--space-3)' }}>
          Current: <StatusBadge status={admission.wardStatus} />
        </p>
        {canUpdateStatus ? (
          <StatusForm admission={admission} />
        ) : (
          <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>You do not hold WARD_STATUS_UPDATE.</p>
        )}
      </section>

      <section style={panelStyle}>
        <h2 style={panelTitleStyle}>Bed assignment</h2>
        {canAssignBed ? (
          <BedForm admission={admission} />
        ) : (
          <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>You do not hold BED_ASSIGN.</p>
        )}
      </section>

      <section style={{ ...panelStyle, gridColumn: '1 / -1' }}>
        <h2 style={panelTitleStyle}>Discharge</h2>
        {canDischarge ? (
          <DischargeSection admission={admission} />
        ) : (
          <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
            You do not hold DISCHARGE_ORDER for this patient — only the ordering doctor or the nurse that doctor
            assigned may order discharge.
          </p>
        )}
      </section>
    </div>
  );
}

function StatusForm({ admission }: { admission: AdmissionResponse }) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<WardStatus>((admission.wardStatus as WardStatus) ?? 'ADMITTED');

  const submit = useMutation({
    mutationFn: () => updateWardStatus(admission.id as string, { wardStatus: selected }, accessToken as string),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admissions', admission.id] });
      queryClient.invalidateQueries({ queryKey: ['admissions', 'ward'] });
    },
  });

  return (
    <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'flex-start', flexWrap: 'wrap' }}>
      <select value={selected} onChange={(e) => setSelected(e.target.value as WardStatus)} style={inputStyle}>
        {SETTABLE_STATUSES.map((s) => (
          <option key={s} value={s}>
            {s}
          </option>
        ))}
      </select>
      <button type="button" className="btn btn-primary" disabled={submit.isPending} onClick={() => submit.mutate()}>
        {submit.isPending ? 'Saving…' : 'Update status'}
      </button>
      {submit.error && <div className="form-error">Something went wrong. Try again.</div>}
    </div>
  );
}

const bedSchema = z.object({
  bedId: z.string().min(1, 'Select a bed'),
  placementReason: z.enum(PLACEMENT_REASONS as [PlacementReason, ...PlacementReason[]]).optional(),
});
type BedValues = z.infer<typeof bedSchema>;

function BedForm({ admission }: { admission: AdmissionResponse }) {
  const { accessToken } = useAuth();
  const { wardId } = useWardContext();
  const queryClient = useQueryClient();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<BedValues>({ resolver: zodResolver(bedSchema) });

  const { data: freeBeds } = useQuery({
    queryKey: ['beds', 'free', wardId],
    queryFn: () => fetchFreeBeds(wardId as string, accessToken as string),
    enabled: accessToken != null && wardId != null,
  });

  const submit = useMutation({
    mutationFn: (values: BedValues) =>
      assignBed(admission.id as string, { bedId: values.bedId, placementReason: values.placementReason }, accessToken as string),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admissions', 'ward'] });
      queryClient.invalidateQueries({ queryKey: ['beds', 'free', wardId] });
    },
  });

  const missingReason =
    submit.error instanceof ApiError && submit.error.status === 400 && /placement reason/i.test(submit.error.body?.detail ?? '');

  return (
    <form onSubmit={handleSubmit((v) => submit.mutate(v))} noValidate style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
      <label style={{ fontSize: 'var(--text-sm)' }}>
        Bed
        <select {...register('bedId')} style={inputStyle}>
          <option value="">Select a free bed</option>
          {(freeBeds ?? []).map((b) => (
            <option key={b.id} value={b.id}>
              {b.label} ({b.bedType})
            </option>
          ))}
        </select>
        {errors.bedId && <div className="field-error">{errors.bedId.message}</div>}
      </label>

      <label style={{ fontSize: 'var(--text-sm)' }}>
        Placement reason (required only if this ward is above the ordered class)
        <select {...register('placementReason')} style={inputStyle}>
          <option value="">—</option>
          {PLACEMENT_REASONS.map((r) => (
            <option key={r} value={r}>
              {r}
            </option>
          ))}
        </select>
      </label>

      {missingReason && <div className="form-error">A placement reason is required for this bed.</div>}
      {submit.error && !missingReason && <div className="form-error">Something went wrong. Try again.</div>}
      {submit.isSuccess && <div style={{ color: 'var(--green-600)', fontSize: 'var(--text-sm)' }}>Bed assigned.</div>}

      <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
        {submit.isPending ? 'Assigning…' : 'Assign bed'}
      </button>
    </form>
  );
}

function DischargeSection({ admission }: { admission: AdmissionResponse }) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [reversing, setReversing] = useState(false);
  const [reason, setReason] = useState('');

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admissions', admission.id] });
    queryClient.invalidateQueries({ queryKey: ['admissions', 'ward'] });
  };

  const order = useMutation({
    mutationFn: () => orderDischarge(admission.id as string, accessToken as string),
    onSuccess: invalidate,
  });
  const complete = useMutation({
    mutationFn: () => completeDischarge(admission.id as string, {}, accessToken as string),
    onSuccess: invalidate,
  });
  const reverse = useMutation({
    mutationFn: () => reverseDischargeOrder(admission.id as string, { reason }, accessToken as string),
    onSuccess: () => {
      setReversing(false);
      setReason('');
      invalidate();
    },
  });

  if (admission.wardStatus === 'DISCHARGED') {
    return <p style={{ color: 'var(--text-muted)' }}>This patient has been discharged.</p>;
  }

  return (
    <div>
      <p style={{ margin: '0 0 var(--space-3)', fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
        Discharge is never blocked by an unpaid balance — any remaining balance becomes a receivable.
      </p>

      {!admission.dischargeOrderedAt ? (
        <button type="button" className="btn btn-primary" disabled={order.isPending} onClick={() => order.mutate()}>
          {order.isPending ? 'Ordering…' : 'Order discharge'}
        </button>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          <p style={{ margin: 0 }}>
            Discharge ordered {formatDateTime(admission.dischargeOrderedAt)}
            {admission.dischargeOrdererRole && <span className="badge badge-neutral" style={{ marginLeft: 'var(--space-2)' }}>{admission.dischargeOrdererRole}</span>}
            — billing has stopped as of that time (with a 4-hour departure grace).
          </p>
          <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
            <button type="button" className="btn btn-primary" disabled={complete.isPending} onClick={() => complete.mutate()}>
              {complete.isPending ? 'Completing…' : 'Complete discharge (patient has left)'}
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => setReversing((v) => !v)}>
              {reversing ? 'Cancel' : 'Reverse discharge order'}
            </button>
          </div>

          {reversing && (
            <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'flex-start' }}>
              <textarea
                rows={2}
                placeholder="Reason the discharge is being reversed"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                style={{ ...inputStyle, marginTop: 0, flex: 1 }}
              />
              <button
                type="button"
                className="btn btn-danger"
                disabled={reverse.isPending || !reason.trim()}
                onClick={() => reverse.mutate()}
              >
                {reverse.isPending ? 'Reversing…' : 'Confirm reversal'}
              </button>
            </div>
          )}

          {(order.error || complete.error || reverse.error) && (
            <div className="form-error">Something went wrong. Try again.</div>
          )}
        </div>
      )}
    </div>
  );
}

const panelStyle: React.CSSProperties = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--border)',
  borderRadius: 'var(--radius-lg)',
  padding: 'var(--space-4)',
};
const panelTitleStyle: React.CSSProperties = { fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 };
const inputStyle: React.CSSProperties = {
  display: 'block',
  width: '100%',
  marginTop: 4,
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};
