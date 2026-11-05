import { WorkspaceBrand } from '../../shared/components/WorkspaceBrand';
import { useQueries, useQuery } from '@tanstack/react-query';
import { NavLink, Outlet } from 'react-router-dom';
import { fetchAwaitingPlacement } from '../../shared/api/admissions';
import { fetchShiftHandovers } from '../../shared/api/shiftHandovers';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import { useWardContext, WardProvider } from './WardContext';
import { useWardPatients } from './useWardPatients';
import './WardNurseShell.css';

// 17-ward-nurse.md §3: sidebar order as specified, Triage appended last and
// gated on TRIAGE_QUEUE_READ — only the plain NURSE role holds that
// permission (V5 migration), so it naturally disappears for WARD_NURSE
// without a role-name check. Every link runs its own usePermission (renders
// null, never greyed out), same convention as every other shell in this
// codebase.
export function WardNurseShell() {
  return (
    <WardProvider>
      <WardNurseShellInner />
    </WardProvider>
  );
}

function WardNurseShellInner() {
  const { logout } = useAuth();
  const { data: me } = useMe();
  const { wardId, wardIds, setWardId, wardName } = useWardContext();

  return (
    <div className="ward-shell-outer">
      <div className="ward-shell app-shell">
        <a className="skip-link" href="#workspace-main">Skip to content</a>
        <aside className="ward-sidebar app-sidebar">
          <WorkspaceBrand workspace="Ward nursing" />

          {wardIds.length === 0 ? (
            <div className="ward-sidebar-ward">
              <div className="ward-sidebar-ward-label">Ward</div>
              <div style={{ color: 'var(--warning)', fontSize: '0.85rem' }}>No ward assignment</div>
            </div>
          ) : wardIds.length === 1 ? (
            <div className="ward-sidebar-ward">
              <div className="ward-sidebar-ward-label">Ward</div>
              <div style={{ color: 'var(--text-on-dark)', fontSize: '0.9rem', fontWeight: 600 }}>
                {wardName ?? '…'}
              </div>
            </div>
          ) : (
            <div className="ward-sidebar-ward">
              <div className="ward-sidebar-ward-label">Ward</div>
              <select value={wardId ?? ''} onChange={(e) => setWardId(e.target.value)}>
                {wardIds.map((id) => (
                  <option key={id} value={id}>
                    {id === wardId ? (wardName ?? id.slice(0, 8)) : id.slice(0, 8)}
                  </option>
                ))}
              </select>
            </div>
          )}

          <nav className="ward-nav app-nav" aria-label="Ward nursing navigation">
            <DashboardLink />
            <AwaitingPlacementLink />
            <WardPatientsLink />
            <SurgeryQueueLink />
            <ShiftHandoverLink />
            <MyHistoryLink />
            <WardBillsLink />
            <SurgeryBillsLink />

            <div className="nav-divider" role="separator" />

            <NavLink to="/ward-nurse/profile" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
              Profile
            </NavLink>
            <TriageLink />
          </nav>
          <div className="ward-sidebar-footer app-sidebar-footer">
            <div className="ward-user">{me?.username}</div>
            <button type="button" className="ward-logout app-logout" onClick={() => logout()}>
              Sign out
            </button>
          </div>
        </aside>
        <main id="workspace-main" tabIndex={-1} className="ward-content app-content">
          <div className="workspace-body"><Outlet /></div>
        </main>
      </div>
    </div>
  );
}

function DashboardLink() {
  const canSee = usePermission('WARD_PATIENT_READ');
  const { data } = useWardPatients();

  if (!canSee) return null;
  const criticalCount = (data ?? []).filter((row) => row.wardStatus === 'CRITICAL').length;

  return (
    <NavLink to="/ward-nurse" end className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      <span className="nav-item-label">
        <span>Dashboard</span>
        {criticalCount > 0 && <span className="badge badge-critical">{criticalCount}</span>}
      </span>
    </NavLink>
  );
}

// 17-ward-nurse.md §8: newly admitted patients not yet placed into a bed —
// invisible to the ward roster until claimed. Gated on BED_ASSIGN (the
// permission the claim itself needs), with a badge so a waiting admission
// never sits unnoticed.
function AwaitingPlacementLink() {
  const { accessToken } = useAuth();
  const { wardId } = useWardContext();
  const canSee = usePermission('BED_ASSIGN');

  const { data } = useQuery({
    queryKey: ['admissions', 'awaiting-placement', wardId],
    queryFn: () => fetchAwaitingPlacement(wardId as string, accessToken as string),
    enabled: canSee && accessToken != null && wardId != null,
    refetchInterval: 15_000,
  });

  if (!canSee) return null;
  const count = (data ?? []).length;

  return (
    <NavLink to="/ward-nurse/awaiting-placement" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      <span className="nav-item-label">
        <span>Awaiting Placement</span>
        {count > 0 && <span className="badge badge-warning">{count}</span>}
      </span>
    </NavLink>
  );
}

function WardPatientsLink() {
  const canSee = usePermission('WARD_PATIENT_READ');
  if (!canSee) return null;
  return (
    <NavLink to="/ward-nurse/patients" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      Ward Patients
    </NavLink>
  );
}

function SurgeryQueueLink() {
  const canSee = usePermission('SURGERY_EPISODE_DOCUMENT');
  if (!canSee) return null;
  return (
    <NavLink to="/ward-nurse/surgery" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      Surgery Queue
    </NavLink>
  );
}

// 17-ward-nurse.md §7: "An unacknowledged handover is flagged" — counted
// across every patient on the ward, not just one. Small N (a ward's worth
// of admissions), one query per admission, same per-row-subquery pattern
// used throughout this codebase (e.g. doctor MyQueuePage's EncounterIdentity).
function ShiftHandoverLink() {
  const { accessToken } = useAuth();
  const canSee = usePermission('SHIFT_HANDOVER_CREATE');
  const { data: patients } = useWardPatients();

  const handoverQueries = useQueries({
    queries: (canSee ? (patients ?? []) : []).map((row) => ({
      queryKey: ['shift-handovers', row.admissionId],
      queryFn: () => fetchShiftHandovers(row.admissionId as string, accessToken as string),
      enabled: accessToken != null,
      staleTime: 10_000,
    })),
  });

  if (!canSee) return null;
  const unacknowledgedCount = handoverQueries.reduce(
    (sum, q) => sum + (q.data ?? []).filter((h) => !h.acknowledgedAt).length,
    0,
  );

  return (
    <NavLink to="/ward-nurse/handover" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      <span className="nav-item-label">
        <span>Shift Handover</span>
        {unacknowledgedCount > 0 && <span className="badge badge-warning">{unacknowledgedCount}</span>}
      </span>
    </NavLink>
  );
}

function MyHistoryLink() {
  const canSee = usePermission('WARD_PATIENT_READ');
  if (!canSee) return null;
  return (
    <NavLink to="/ward-nurse/history" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      My History
    </NavLink>
  );
}

function WardBillsLink() {
  const canSee = usePermission('INVOICE_READ');
  if (!canSee) return null;
  return (
    <NavLink to="/ward-nurse/ward-bills" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      Ward Bills
    </NavLink>
  );
}

function SurgeryBillsLink() {
  const canSee = usePermission('INVOICE_READ');
  if (!canSee) return null;
  return (
    <NavLink to="/ward-nurse/surgery-bills" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      Surgery Bills
    </NavLink>
  );
}

function TriageLink() {
  const canSee = usePermission('TRIAGE_QUEUE_READ');
  if (!canSee) return null;
  return (
    <NavLink to="/triage" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      Triage
    </NavLink>
  );
}
