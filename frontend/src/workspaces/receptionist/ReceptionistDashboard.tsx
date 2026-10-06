import { FeedbackState } from '../../shared/components/FeedbackState';
import { PageHeader } from '../../shared/components/PageHeader';
import { TableRegion } from '../../shared/components/TableRegion';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  fetchAppointmentsScheduledBetween,
  fetchRecentAppointments,
  type AppointmentResponse,
} from '../../shared/api/appointments';
import { fetchCashierShiftPayments, fetchCurrentCashierShift } from '../../shared/api/cashierShifts';
import { searchEncounters } from '../../shared/api/encounters';
import { searchInvoices } from '../../shared/api/invoices';
import { fetchRegisteredPatientCount } from '../../shared/api/patients';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { formatDateTime, todayRange } from '../../shared/format/date';
import { formatMoney } from '../../shared/format/money';

// 05-receptionist.md §3, as corrected by ⚠2: every number here is
// today-scoped and links to the screen a receptionist would actually work
// from — a lifetime count tells them nothing about their shift.
export function ReceptionistDashboard() {
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  const { from: todayFrom, to: todayTo } = todayRange();
  const now = new Date().toISOString();

  const waitingForTriage = useQuery({
    queryKey: ['dashboard', 'waiting-for-triage'],
    queryFn: () => searchEncounters({ states: ['AWAITING_TRIAGE'] }, accessToken as string),
    enabled: accessToken != null,
  });

  const inProgressToday = useQuery({
    queryKey: ['dashboard', 'in-progress-today', todayFrom, todayTo],
    queryFn: () => searchEncounters({ excludeTerminal: true, createdFrom: todayFrom, createdTo: todayTo }, accessToken as string),
    enabled: accessToken != null,
  });

  const appointmentsToday = useQuery({
    queryKey: ['dashboard', 'appointments-today', todayFrom, todayTo],
    queryFn: () => fetchAppointmentsScheduledBetween(todayFrom, todayTo, accessToken as string),
    enabled: accessToken != null,
  });

  const unpaidAtDesk = useQuery({
    queryKey: ['dashboard', 'unpaid-at-desk', todayFrom, todayTo],
    queryFn: () => searchInvoices({ createdFrom: todayFrom, createdTo: todayTo, outstandingOnly: true }, accessToken as string),
    enabled: accessToken != null,
  });

  const registeredToday = useQuery({
    queryKey: ['dashboard', 'registered-today', todayFrom, todayTo],
    queryFn: () => fetchRegisteredPatientCount(todayFrom, todayTo, accessToken as string),
    enabled: accessToken != null,
  });

  const currentShift = useQuery({
    queryKey: ['cashier-shift', 'current'],
    queryFn: () => fetchCurrentCashierShift(accessToken as string),
    enabled: accessToken != null,
  });

  const shiftPayments = useQuery({
    queryKey: ['cashier-shift', 'payments', currentShift.data?.id],
    queryFn: () => fetchCashierShiftPayments(currentShift.data?.id as string, accessToken as string),
    enabled: accessToken != null && currentShift.data != null,
  });

  const recentAppointments = useQuery({
    queryKey: ['dashboard', 'appointments-recent'],
    queryFn: () => fetchRecentAppointments(8, accessToken as string),
    enabled: accessToken != null,
  });

  const upcomingAppointments = useQuery({
    queryKey: ['dashboard', 'appointments-upcoming', now, todayTo],
    queryFn: () => fetchAppointmentsScheduledBetween(now, todayTo, accessToken as string).then((rows) => rows.slice(0, 8)),
    enabled: accessToken != null,
  });

  const shiftTotalMinor = (shiftPayments.data ?? [])
    .filter((p) => p.status === 'SETTLED')
    .reduce((sum, p) => sum + (p.amountMinor ?? 0), 0);

  const unpaidTotalMinor = (unpaidAtDesk.data ?? []).reduce((sum, inv) => sum + (inv.outstandingTotalMinor ?? 0), 0);

  return (
    <div>
      <PageHeader title={<>Welcome{me?.username ? `, ${me.username}` : ''}</>} />

      <div className="clinical-grid"
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
          gap: 'var(--space-3)',
          marginBottom: 'var(--space-6)',
        }}
      >
        <Tile
          to="/receptionist/patient-intake"
          label="Waiting for triage"
          value={waitingForTriage.data?.length}
          isLoading={waitingForTriage.isLoading}
        />
        <Tile
          to="/receptionist/patient-intake"
          label="In progress today"
          value={inProgressToday.data?.length}
          isLoading={inProgressToday.isLoading}
        />
        <Tile
          to="/receptionist/expected-returns"
          label="Appointments today"
          value={appointmentsToday.data?.length}
          isLoading={appointmentsToday.isLoading}
        />
        <Tile
          to="/receptionist/billing"
          label="Unpaid at desk"
          value={unpaidAtDesk.data?.length}
          isLoading={unpaidAtDesk.isLoading}
          detail={unpaidAtDesk.data && unpaidAtDesk.data.length > 0 ? formatMoney(unpaidTotalMinor, unpaidAtDesk.data[0]?.currencyCode) : undefined}
        />
        <Tile
          to="/receptionist/patient-intake"
          label="Registered today"
          value={registeredToday.data}
          isLoading={registeredToday.isLoading}
        />
        <Tile
          to="/receptionist/shift"
          label="My shift total"
          value={currentShift.data ? shiftPayments.data?.length : undefined}
          isLoading={currentShift.isLoading || shiftPayments.isLoading}
          detail={currentShift.data ? formatMoney(shiftTotalMinor, 'TZS') : 'No shift open'}
        />
      </div>

      <div className="clinical-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 320px), 1fr))', gap: 'var(--space-4)' }}>
        <AppointmentPanel title="Recent appointments" rows={recentAppointments.data} isLoading={recentAppointments.isLoading} emptyMessage="No recent appointments" />
        <AppointmentPanel title="Next up today" rows={upcomingAppointments.data} isLoading={upcomingAppointments.isLoading} emptyMessage="No appointments left today" />
      </div>
    </div>
  );
}

function Tile({
  to,
  label,
  value,
  isLoading,
  detail,
}: {
  to: string;
  label: string;
  value: number | undefined;
  isLoading: boolean;
  detail?: string;
}) {
  return (
    <Link
      to={to}
      style={{
        display: 'block',
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-4)',
        textDecoration: 'none',
        color: 'inherit',
      }}
    >
      <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>{label}</div>
      <div style={{ fontSize: 'var(--text-2xl)', fontWeight: 'var(--weight-bold)', marginTop: 'var(--space-1)' }} className="tabular">
        {isLoading ? '…' : (value ?? 0)}
      </div>
      {detail && (
        <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginTop: 'var(--space-1)' }}>{detail}</div>
      )}
    </Link>
  );
}

function AppointmentPanel({
  title,
  rows,
  isLoading,
  emptyMessage,
}: {
  title: string;
  rows: AppointmentResponse[] | undefined;
  isLoading: boolean;
  emptyMessage: string;
}) {
  return (
    <section
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        overflow: 'hidden',
      }}
    >
      <h2
        style={{
          fontSize: 'var(--text-lg)',
          fontWeight: 'var(--weight-medium)',
          margin: 0,
          padding: 'var(--space-4) var(--space-4) 0',
        }}
      >
        {title}
      </h2>
      {isLoading ? (
        <FeedbackState loading>Loading…</FeedbackState>
      ) : !rows || rows.length === 0 ? (
        <FeedbackState>{emptyMessage}</FeedbackState>
      ) : (
        <TableRegion><table className="table">
          <caption className="sr-only">{title}</caption>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id}>
                <td style={{ width: 1, whiteSpace: 'nowrap' }}>
                  <StatusBadge status={row.status} />
                </td>
                <td style={{ color: 'var(--text-secondary)', fontSize: 'var(--text-sm)' }}>{formatDateTime(row.scheduledAt)}</td>
                <td style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>{row.notes || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table></TableRegion>
      )}
    </section>
  );
}

function StatusBadge({ status }: { status: AppointmentResponse['status'] }) {
  const className =
    status === 'ATTENDED' ? 'badge badge-success' : status === 'NO_SHOW' || status === 'CANCELLED' ? 'badge badge-neutral' : 'badge badge-info';
  return <span className={className}>{(status ?? '').toLowerCase().replace('_', ' ')}</span>;
}
