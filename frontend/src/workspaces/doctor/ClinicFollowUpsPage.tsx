import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../shared/components/PageHeader';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { TableRegion } from '../../shared/components/TableRegion';
import { fetchActiveClinicAppointments, fetchMyClinicPlans, fetchPatientAppointments, clinicArrivalLabel, type ClinicPlan } from '../../shared/api/appointments';
import { fetchPatientById } from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { formatDateTime } from '../../shared/format/date';

export function ClinicFollowUpsPage() {
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  const plans = useQuery({ queryKey: ['appointments', 'clinic-plans', 'mine'],
    queryFn: () => fetchMyClinicPlans(accessToken!), enabled: !!accessToken });
  const appointments = useQuery({ queryKey: ['appointments', 'clinic-active'],
    queryFn: () => fetchActiveClinicAppointments(accessToken!), enabled: !!accessToken });
  const mine = plans.data?.filter(p => p.active && p.providerId === me?.userId) ?? [];
  return <div>
    <PageHeader title="Clinic follow-up" description="Active clinic patients, scheduled returns, and attended visits." />
    {plans.isLoading ? <FeedbackState loading>Loading clinic patients…</FeedbackState> : mine.length === 0 ? <FeedbackState>No active clinic patients assigned to you.</FeedbackState> :
      <TableRegion label="Active clinic patients"><table className="table">
        <thead><tr><th>Patient</th><th>Clinic</th><th>Next return</th><th>Visit history</th></tr></thead>
        <tbody>{mine.map(plan => <ClinicRow key={plan.id} plan={plan} scheduled={appointments.data?.find(a => a.clinicFollowUpId === plan.id)} />)}</tbody>
      </table></TableRegion>}
  </div>;
}

function ClinicRow({ plan, scheduled }: { plan: ClinicPlan; scheduled?: { scheduledAt?: string; status?: string } }) {
  const { accessToken } = useAuth();
  const patient = useQuery({ queryKey: ['patient', plan.patientId], queryFn: () => fetchPatientById(plan.patientId, accessToken!), enabled: !!accessToken });
  const history = useQuery({ queryKey: ['appointments', 'patient', plan.patientId], queryFn: () => fetchPatientAppointments(plan.patientId, accessToken!), enabled: !!accessToken });
  return <tr>
    <td><Link to={`/doctor/patients/${plan.patientId}`}>{patient.data?.fullName ?? 'Loading…'}</Link><br /><small>{patient.data?.hospitalNo}</small></td>
    <td>{plan.clinic} · {plan.doctor}</td>
    <td>{scheduled ? <>{formatDateTime(scheduled.scheduledAt)}<br /><span className="badge badge-info">{scheduled.status} · {clinicArrivalLabel(scheduled.scheduledAt)}</span></> : 'No visit scheduled'}</td>
    <td><details><summary>{history.data?.filter(a => a.clinicFollowUpId === plan.id && a.status === 'ATTENDED').length ?? 0} attended · View history</summary>
      <ul>{history.data?.filter(a => a.clinicFollowUpId === plan.id).map(a => <li key={a.id}>{formatDateTime(a.scheduledAt)} · {a.status} {a.arrivedAt ? `· Arrived ${formatDateTime(a.arrivedAt)}` : ''} {a.noShowAt ? `· Marked no-show ${formatDateTime(a.noShowAt)}` : ''}</li>)}</ul>
    </details></td>
  </tr>;
}
