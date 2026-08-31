import { useQuery } from '@tanstack/react-query';
import { NavLink, Outlet } from 'react-router-dom';
import { searchEncounters } from '../../shared/api/encounters';
import { fetchMyLabOrders } from '../../shared/api/labOrders';
import { fetchMyPrescriptions } from '../../shared/api/prescriptions';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import { IncompleteConsultationPrompt } from './IncompleteConsultationPrompt';
import './DoctorShell.css';

// docs/spec/08-doctor-navigation.md §1. Admitted Patients and Surgery
// Patients are hidden until those modules are built — frontend/CLAUDE.md's
// rule against greyed-out nav items that train users to stop reading the
// sidebar.
export function DoctorShell() {
  const { logout } = useAuth();
  const { data: me } = useMe();

  return (
    <div className="doctor-shell-outer">
      <div className="doctor-shell">
        <aside className="doctor-sidebar">
          <div className="doctor-sidebar-brand">ADAGS</div>
          <nav className="doctor-nav">
            <MyQueueLink />
            <MyPatientsLink />
            <HospitalCatalogueLink />
            <RequestHistoryLink />

            <div className="nav-divider" role="separator" />

            <NavLink to="/doctor/profile" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
              Profile
            </NavLink>
          </nav>
          <div className="doctor-sidebar-footer">
            <div className="doctor-user">{me?.username}</div>
            <button type="button" className="doctor-logout" onClick={() => logout()}>
              Sign out
            </button>
          </div>
        </aside>
        <main className="doctor-content">
          <Outlet />
        </main>
      </div>
      <IncompleteConsultationPrompt />
    </div>
  );
}

// §2's ordering rule (EMERGENCY always first) is also exactly the badge
// rule here: a red dot means an EMERGENCY-type (== IMMEDIATE triage
// priority, SubmitTriageAssessmentService sets type=EMERGENCY exactly when
// priority=IMMEDIATE) encounter is waiting in this doctor's queue.
function MyQueueLink() {
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  const canSee = usePermission('CONSULTATION_CREATE');

  // Includes IN_CONSULT so the sidebar count matches what MyQueuePage
  // shows (In progress + Waiting sections). Without this, a doctor with
  // one open consultation and no waiting patients saw "0" on the sidebar
  // while the queue page had a row — confusing.
  const { data } = useQuery({
    queryKey: ['encounters', 'search', { states: ['AWAITING_CONSULT', 'IN_CONSULT'] }],
    queryFn: () => searchEncounters({ states: ['AWAITING_CONSULT', 'IN_CONSULT'] }, accessToken as string),
    enabled: canSee && accessToken != null,
    refetchInterval: 15_000,
  });

  if (!canSee) return null;

  const mine = (data ?? []).filter((e) => e.assignedProviderId === me?.userId);
  const hasImmediate = mine.some((e) => e.type === 'EMERGENCY');

  return (
    <NavLink to="/doctor" end className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      <span className="nav-item-label">
        <span className="nav-item-label-text">
          My Queue
          {hasImmediate && <span className="nav-emergency-dot" aria-label="Emergency patient waiting" title="Emergency patient waiting" />}
        </span>
        <span className="badge badge-neutral">{mine.length}</span>
      </span>
    </NavLink>
  );
}

function MyPatientsLink() {
  const canSee = usePermission('PATIENT_READ_VISIT_HISTORY');
  if (!canSee) return null;
  return (
    <NavLink to="/doctor/patients" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      My Patients
    </NavLink>
  );
}

function HospitalCatalogueLink() {
  const canSee = usePermission('PRICE_CATALOG_READ');
  if (!canSee) return null;
  return (
    <NavLink to="/doctor/catalogue" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      Hospital Catalogue
    </NavLink>
  );
}

// 08-doctor-navigation.md §5.1 ⚠: "results available + overdue unpaid" —
// COMPLETED (result/dispense ready) and PENDING_PAYMENT rows, both states
// that need the doctor's attention. Not an "unread" count (no read/ack
// state exists on this data yet) — a real, if coarser, signal rather than
// a fabricated one.
function RequestHistoryLink() {
  const { accessToken } = useAuth();
  // Both hooks must run unconditionally on every render — `||` would
  // short-circuit the second call once the first is true, changing the
  // number of hooks called between renders (React's Rules of Hooks).
  const canSeeLabs = usePermission('LAB_ORDER_CREATE');
  const canSeePrescriptions = usePermission('PRESCRIPTION_CREATE');
  const canSee = canSeeLabs || canSeePrescriptions;

  const { data: labs } = useQuery({
    queryKey: ['lab-orders', 'mine'],
    queryFn: () => fetchMyLabOrders(accessToken as string),
    enabled: canSee && accessToken != null,
    refetchInterval: 30_000,
  });
  const { data: rx } = useQuery({
    queryKey: ['prescriptions', 'mine'],
    queryFn: () => fetchMyPrescriptions(accessToken as string),
    enabled: canSee && accessToken != null,
    refetchInterval: 30_000,
  });

  if (!canSee) return null;

  const actionable = [...(labs ?? []), ...(rx ?? [])].filter(
    (row) => row.status === 'COMPLETED' || row.status === 'PENDING_PAYMENT',
  ).length;

  return (
    <NavLink to="/doctor/request-history" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      <span className="nav-item-label">
        <span>Request History</span>
        {actionable > 0 && <span className="badge badge-neutral">{actionable}</span>}
      </span>
    </NavLink>
  );
}
