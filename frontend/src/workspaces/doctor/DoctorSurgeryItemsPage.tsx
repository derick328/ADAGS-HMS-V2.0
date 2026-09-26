import { useQuery } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { fetchEncounterById } from '../../shared/api/encounters';
import { fetchPatientById } from '../../shared/api/patients';
import { fetchSurgeryOrderById } from '../../shared/api/surgeryOrders';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatDate } from '../../shared/format/date';
import { SurgeryEpisodeProcedureItems } from '../ward/SurgeryEpisodeProcedureItems';

// V75 gives the DOCTOR role SURGERY_EPISODE_DOCUMENT alongside the
// SPECIALIST_DOCTOR grant that already had it — the same permission the
// ProcedureItemController checks — so the same procedure-item surface
// the circulating nurse uses works for the ordering doctor too. Rather
// than duplicating the pre-op / post-op flow, this page mounts the
// exact ward-side component (no fork, no drift) inside a doctor-shell
// route with a header that names the surgery and patient.
export function DoctorSurgeryItemsPage() {
  const { surgeryOrderId } = useParams<{ surgeryOrderId: string }>();
  const { accessToken } = useAuth();
  const navigate = useNavigate();

  const { data: order, isLoading: orderLoading, isError: orderError } = useQuery({
    queryKey: ['surgery-orders', surgeryOrderId],
    queryFn: () => fetchSurgeryOrderById(surgeryOrderId as string, accessToken as string),
    enabled: accessToken != null && !!surgeryOrderId,
  });
  const { data: encounter } = useQuery({
    queryKey: ['encounters', order?.encounterId],
    queryFn: () => fetchEncounterById(order!.encounterId as string, accessToken as string),
    enabled: accessToken != null && !!order?.encounterId,
    staleTime: 5 * 60_000,
  });
  const { data: patient } = useQuery({
    queryKey: ['patients', encounter?.patientId],
    queryFn: () => fetchPatientById(encounter!.patientId as string, accessToken as string),
    enabled: accessToken != null && !!encounter?.patientId,
    staleTime: 5 * 60_000,
  });

  return (
    <div>
      <button
        type="button"
        className="btn btn-ghost"
        onClick={() => navigate('/doctor/scheduled-surgeries')}
        style={{ marginBottom: 'var(--space-3)' }}
      >
        ← Back to My Surgeries
      </button>

      <header style={{ marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>
          Procedure items
        </h1>
        {orderLoading ? (
          <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)', margin: '4px 0 0' }}>Loading…</p>
        ) : orderError || !order ? (
          <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)', margin: '4px 0 0' }}>
            Couldn't load this surgery order.
          </p>
        ) : (
          <p style={{ margin: '4px 0 0', fontSize: 'var(--text-sm)', color: 'var(--text-secondary)' }}>
            {order.procedureName ?? order.procedureServiceItemCode ?? 'Procedure'}
            {patient?.fullName && <> · {patient.fullName}</>}
            {patient?.hospitalNo && <> · {patient.hospitalNo}</>}
            {order.scheduledDate && <> · {formatDate(order.scheduledDate)}</>}
          </p>
        )}
        <p style={{ margin: '4px 0 0', fontSize: 'var(--text-xs)', color: 'var(--text-muted)' }}>
          Add pre-op / post-op items you want prepared. Each row records who prepared, reconciled, and attested
          it — that attribution appears on the final surgery report.
        </p>
      </header>

      {surgeryOrderId && <SurgeryEpisodeProcedureItems surgeryOrderId={surgeryOrderId} />}
    </div>
  );
}
