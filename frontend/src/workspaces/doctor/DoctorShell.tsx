import { WorkspaceUser } from '../../shared/components/WorkspaceUser';
import { WorkspaceNavIcon } from '../../shared/components/WorkspaceNavIcon';
import { WorkspaceBrand } from '../../shared/components/WorkspaceBrand';
import { useQuery } from '@tanstack/react-query';
import { NavLink, Outlet } from 'react-router-dom';
import { fetchMyAdmissions } from '../../shared/api/admissions';
import { searchEncounters } from '../../shared/api/encounters';
import { fetchUnreadLabResultOrderIds } from '../../shared/api/labOrders';
import { fetchMyPrescriptions } from '../../shared/api/prescriptions';
import { fetchActiveSurgeryOrders } from '../../shared/api/surgeryOrders';
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
  const isClinicalOfficer = me?.scopes?.some((scope) => scope.roleCode === 'CLINICAL_OFFICER');
  const workspaceLabel = isClinicalOfficer ? 'Clinical Officer' : 'Doctor';

  return (
    <div className="doctor-shell-outer">
      <div className="doctor-shell app-shell">
        <a className="skip-link" href="#workspace-main">Skip to content</a>
        <aside className="doctor-sidebar app-sidebar staff-sidebar">
          <WorkspaceBrand workspace={`${workspaceLabel} workspace`} />
          <nav className="doctor-nav app-nav" aria-label="Doctor workspace navigation">
            <MyQueueLink />
            <MyPatientsLink />
            <NavLink to="/doctor/clinic-follow-ups" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
              <WorkspaceNavIcon path="/doctor/clinic-follow-ups" />Clinic Follow-Up
            </NavLink>
            <AdmittedPatientsLink />
            <MyScheduledSurgeriesLink />
            <HospitalCatalogueLink />
            <RequestHistoryLink />

            <div className="nav-divider" role="separator" />

            <NavLink to="/doctor/profile" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
              <WorkspaceNavIcon path="/doctor/profile" />Profile
            </NavLink>
          </nav>
          <div className="doctor-sidebar-footer app-sidebar-footer">
            <WorkspaceUser name={me?.username} role={workspaceLabel} />
            <button type="button" className="doctor-logout app-logout" onClick={() => logout()}>
              Sign out
            </button>
          </div>
        </aside>
        <main id="workspace-main" tabIndex={-1} className="doctor-content app-content staff-workspace">
          <div className="workspace-body"><Outlet /></div>
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
          <WorkspaceNavIcon path="/doctor" />My Queue
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
      <WorkspaceNavIcon path="/doctor/patients" />My Patients
    </NavLink>
  );
}

// 07-doctor.md §7/§11: patients this doctor admitted and who are still in
// hospital (active admissions, bedded or awaiting a bed). Gated on
// ADMISSION_ORDER — the same permission the admit action needs, so it
// naturally appears only for doctors who admit. Badge counts the open ones.
function AdmittedPatientsLink() {
  const { accessToken } = useAuth();
  const canSee = usePermission('ADMISSION_ORDER');

  const { data } = useQuery({
    queryKey: ['admissions', 'mine'],
    queryFn: () => fetchMyAdmissions(accessToken as string),
    enabled: canSee && accessToken != null,
    refetchInterval: 30_000,
  });

  if (!canSee) return null;
  const count = (data ?? []).length;

  return (
    <NavLink to="/doctor/admitted" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      <span className="nav-item-label">
        <span className="nav-item-label-text"><WorkspaceNavIcon path="/doctor/admitted" />Admitted</span>
        {count > 0 && <span className="badge badge-neutral">{count}</span>}
      </span>
    </NavLink>
  );
}

// Read-only surgery view — the ward nurse owns the working screen. Badge
// counts open (non-terminal) orders this doctor scheduled so the sidebar
// mirrors how many patients are still moving through theatre.
function MyScheduledSurgeriesLink() {
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  // Both hooks must run unconditionally — `||` short-circuits the second
  // call once the first is true, changing the hook count between
  // renders (React's Rules of Hooks). Same pattern RequestHistoryLink
  // already uses for its two-permission gate.
  const canOrder = usePermission('SURGERY_ORDER_CREATE');
  const canDocument = usePermission('SURGERY_EPISODE_DOCUMENT');
  const canSee = canOrder || canDocument;

  const { data } = useQuery({
    queryKey: ['surgery-orders', 'active'],
    queryFn: () => fetchActiveSurgeryOrders(accessToken as string),
    enabled: canSee && accessToken != null,
    refetchInterval: 60_000,
  });

  if (!canSee) return null;

  const openMine = (data ?? []).filter(
    (o) => o.scheduledBy === me?.userId && o.status !== 'COMPLETED' && o.status !== 'CANCELLED',
  );

  return (
    <NavLink
      to="/doctor/scheduled-surgeries"
      className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}
    >
      <span className="nav-item-label">
        <span className="nav-item-label-text"><WorkspaceNavIcon path="/doctor/scheduled-surgeries" />My Surgeries</span>
        {openMine.length > 0 && <span className="badge badge-neutral">{openMine.length}</span>}
      </span>
    </NavLink>
  );
}

function HospitalCatalogueLink() {
  const canSee = usePermission('PRICE_CATALOG_READ');
  if (!canSee) return null;
  return (
    <NavLink to="/doctor/catalogue" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      <WorkspaceNavIcon path="/doctor/catalogue" />Hospital Catalogue
    </NavLink>
  );
}

// Released lab results use their persisted per-user read state. Prescription
// rows retain their existing completed/unpaid attention count.
function RequestHistoryLink() {
  const { accessToken } = useAuth();
  // Both hooks must run unconditionally on every render — `||` would
  // short-circuit the second call once the first is true, changing the
  // number of hooks called between renders (React's Rules of Hooks).
  const canSeeLabs = usePermission('LAB_ORDER_CREATE');
  const canSeePrescriptions = usePermission('PRESCRIPTION_CREATE');
  const canSee = canSeeLabs || canSeePrescriptions;

  const { data: rx } = useQuery({
    queryKey: ['prescriptions', 'mine'],
    queryFn: () => fetchMyPrescriptions(accessToken as string),
    enabled: canSee && accessToken != null,
    refetchInterval: 30_000,
  });
  const { data: unreadLabOrders } = useQuery({
    queryKey: ['lab-result-unread', 'mine'],
    queryFn: () => fetchUnreadLabResultOrderIds(accessToken as string),
    enabled: canSeeLabs && accessToken != null,
    refetchInterval: 30_000,
  });

  if (!canSee) return null;

  const actionable = (unreadLabOrders?.length ?? 0) + (rx ?? []).filter(
    (row) => row.status === 'COMPLETED' || row.status === 'PENDING_PAYMENT',
  ).length;

  return (
    <NavLink to="/doctor/request-history" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      <span className="nav-item-label">
        <span className="nav-item-label-text"><WorkspaceNavIcon path="/doctor/request-history" />Request History</span>
        {actionable > 0 && <span className="badge badge-neutral">{actionable}</span>}
      </span>
    </NavLink>
  );
}
