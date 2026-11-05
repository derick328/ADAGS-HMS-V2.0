import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import {
  assignBed,
  assignNurse,
  fetchAwaitingPlacement,
  type PlacementReason,
  type Shift,
  type WardPatientRowResponse,
} from '../../shared/api/admissions';
import { fetchFreeBeds } from '../../shared/api/wards';
import { ApiError } from '../../shared/api/client';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { Modal } from '../../shared/components/Modal/Modal';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import { formatDateTime, todayLocalDate } from '../../shared/format/date';
import { useWardContext } from './WardContext';

const PLACEMENT_REASONS: PlacementReason[] = ['NO_BED_IN_ORDERED_CLASS', 'CLINICAL_NEED', 'PATIENT_REQUEST', 'OTHER'];
const SHIFTS: Shift[] = ['MORNING', 'AFTERNOON', 'NIGHT'];

// Facility is a fixed UTC+3 (see format/date), so a plain local-hour bucket
// is a fine default — the nurse can still change it before claiming.
function currentShift(): Shift {
  const hour = Number(new Intl.DateTimeFormat('en-GB', { hour: '2-digit', hour12: false, timeZone: 'Africa/Dar_es_Salaam' }).format(new Date()));
  if (hour < 12) return 'MORNING';
  if (hour < 18) return 'AFTERNOON';
  return 'NIGHT';
}

// 17-ward-nurse.md §8: patients the doctor marked ADMITTED but who have not
// yet been placed into a bed. They are in no ward roster and accrue no
// bed-days until a nurse claims one — picks a free bed in her ward and takes
// care responsibility (a NursingAssignment row, §8.2) in a single action.
export function AwaitingPlacementPage() {
  const { wardName, wardId } = useWardContext();
  const { accessToken } = useAuth();
  const canClaim = usePermission('BED_ASSIGN');

  const { data: patients, isLoading } = useQuery({
    queryKey: ['admissions', 'awaiting-placement', wardId],
    queryFn: () => fetchAwaitingPlacement(wardId as string, accessToken as string),
    enabled: canClaim && accessToken != null && wardId != null,
    refetchInterval: 15_000,
  });

  const [claiming, setClaiming] = useState<WardPatientRowResponse | null>(null);
  const rows = patients ?? [];

  return (
    <div>
      <PageHeader
        title={<>Awaiting placement</>}
        description={<>Admitted patients not yet in a bed — {wardName ?? 'your ward'}</>}
      />

      {!canClaim ? (
        <FeedbackState>You do not have permission to place patients into beds.</FeedbackState>
      ) : (
        <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
          {isLoading ? (
            <FeedbackState loading>Loading…</FeedbackState>
          ) : rows.length === 0 ? (
            <FeedbackState>No patients are waiting for a bed.</FeedbackState>
          ) : (
            <TableRegion label="Patients awaiting placement"><table className="table">
              <caption className="sr-only">Patients awaiting placement</caption>
              <thead>
                <tr>
                  <th>Patient</th>
                  <th>Ordered class</th>
                  <th>Admitted</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.admissionId}>
                    <td style={{ fontWeight: 'var(--weight-medium)' }}>
                      {row.patientName ?? 'Unknown patient'}
                      {row.hospitalNumber && <span style={{ color: 'var(--text-muted)', fontWeight: 'normal' }}> · {row.hospitalNumber}</span>}
                    </td>
                    <td>{row.orderedWardClass ?? '—'}</td>
                    <td>{formatDateTime(row.admittedAt)}</td>
                    <td>
                      <button type="button" className="btn btn-primary" onClick={() => setClaiming(row)}>
                        Claim
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table></TableRegion>
          )}
        </div>
      )}

      {claiming && <ClaimModal patient={claiming} onClose={() => setClaiming(null)} />}
    </div>
  );
}

const claimSchema = z.object({
  bedId: z.string().min(1, 'Select a bed'),
  placementReason: z.enum(PLACEMENT_REASONS as [PlacementReason, ...PlacementReason[]]).optional(),
  shift: z.enum(SHIFTS as [Shift, ...Shift[]]),
});
type ClaimValues = z.infer<typeof claimSchema>;

function ClaimModal({ patient, onClose }: { patient: WardPatientRowResponse; onClose: () => void }) {
  const { accessToken } = useAuth();
  const { wardId } = useWardContext();
  const { data: me } = useMe();
  const queryClient = useQueryClient();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ClaimValues>({ resolver: zodResolver(claimSchema), defaultValues: { shift: currentShift() } });

  const { data: freeBeds } = useQuery({
    queryKey: ['beds', 'free', wardId],
    queryFn: () => fetchFreeBeds(wardId as string, accessToken as string),
    enabled: accessToken != null && wardId != null,
  });

  // Place the bed first (it sets the admission's current ward), then record
  // this nurse on the roster for the shift — assignNurse needs the active
  // bed assignment to exist. Both reuse the existing endpoints.
  const claim = useMutation({
    mutationFn: async (values: ClaimValues) => {
      const admissionId = patient.admissionId as string;
      await assignBed(admissionId, { bedId: values.bedId, placementReason: values.placementReason }, accessToken as string);
      await assignNurse(
        admissionId,
        { nurseId: me?.userId as string, shift: values.shift, assignmentDate: todayLocalDate() },
        accessToken as string,
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admissions', 'awaiting-placement', wardId] });
      queryClient.invalidateQueries({ queryKey: ['admissions', 'ward'] });
      queryClient.invalidateQueries({ queryKey: ['beds', 'free', wardId] });
      queryClient.invalidateQueries({ queryKey: ['nursing-assignments', 'mine'] });
      onClose();
    },
  });

  const missingReason =
    claim.error instanceof ApiError && claim.error.status === 400 && /placement reason/i.test(claim.error.body?.detail ?? '');

  return (
    <Modal
      open
      onClose={onClose}
      title={`Claim ${patient.patientName ?? 'patient'}`}
      description="Choose a bed in your ward and the shift you are taking responsibility for."
      footer={
        <button type="submit" form="claim-form" className="btn btn-primary" disabled={claim.isPending}>
          {claim.isPending ? 'Claiming…' : 'Claim patient'}
        </button>
      }
    >
      <form id="claim-form" onSubmit={handleSubmit((v) => claim.mutate(v))} noValidate style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
        <label style={{ fontSize: 'var(--text-sm)', display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
          Bed
          <select className="clinical-control" {...register('bedId')}>
            <option value="">Select a free bed</option>
            {(freeBeds ?? []).map((b) => (
              <option key={b.id} value={b.id}>
                {b.label} ({b.bedType})
              </option>
            ))}
          </select>
          {errors.bedId && <span className="field-error">{errors.bedId.message}</span>}
        </label>

        <label style={{ fontSize: 'var(--text-sm)', display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
          Shift
          <select className="clinical-control" {...register('shift')}>
            {SHIFTS.map((s) => (
              <option key={s} value={s}>{s.charAt(0) + s.slice(1).toLowerCase()}</option>
            ))}
          </select>
        </label>

        <label style={{ fontSize: 'var(--text-sm)', display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
          Placement reason (required only if this ward is above the ordered class)
          <select className="clinical-control" {...register('placementReason', { setValueAs: (v) => (v === '' ? undefined : v) })}>
            <option value="">—</option>
            {PLACEMENT_REASONS.map((r) => (
              <option key={r} value={r}>{r}</option>
            ))}
          </select>
        </label>

        {missingReason && <div className="form-error">A placement reason is required for this bed.</div>}
        {claim.error && !missingReason && <div className="form-error">Something went wrong. Try again.</div>}
      </form>
    </Modal>
  );
}
