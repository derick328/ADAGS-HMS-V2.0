import { useQuery } from '@tanstack/react-query';
import { NavLink, Outlet } from 'react-router-dom';
import { searchEncounters } from '../../shared/api/encounters';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import './TriageNurseShell.css';

// 06-triage.md §1. Just the two leaves plus Profile — no separate
// "Dashboard": Patient Queue *is* the landing screen (§1's own note that
// doctor queue depth lives inside the referral picker, not a sidebar item,
// applies the same logic here — nothing gets a nav entry beyond what the
// spec's sidebar actually lists).
//
// PERMISSIONS block at the bottom of the brief: TRIAGE_QUEUE_READ gates the
// queue link, TRIAGE_HISTORY_READ gates Completed Assessments. Both are
// visibility only — usePermission's contract — every action a screen offers
// is re-checked server-side.
export function TriageNurseShell() {
  const { logout } = useAuth();
  const { data: me } = useMe();

  return (
    <div className="triage-shell-outer">
      <div className="triage-shell">
        <aside className="triage-sidebar">
          <div className="triage-sidebar-brand">ADAGS</div>
          <nav className="triage-nav">
            <PatientQueueLink />
            <CompletedAssessmentsLink />

            <div className="nav-divider" role="separator" />

            <NavLink
              to="/triage/profile"
              className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}
            >
              Profile
            </NavLink>
          </nav>
          <div className="triage-sidebar-footer">
            <div className="triage-user">{me?.username}</div>
            <button type="button" className="triage-logout" onClick={() => logout()}>
              Sign out
            </button>
          </div>
        </aside>
        <main className="triage-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

// Live count of AWAITING_TRIAGE, not a fabricated placeholder — the same
// GET /api/encounters?states=AWAITING_TRIAGE the queue screen itself will
// page through. Polled rather than pushed for now: §3's WebSocket re-sort
// belongs to the queue screen build step, not the shell.
function PatientQueueLink() {
  const { accessToken } = useAuth();
  const canSee = usePermission('TRIAGE_QUEUE_READ');

  const { data } = useQuery({
    queryKey: ['encounters', 'search', { states: ['AWAITING_TRIAGE'] }],
    queryFn: () => searchEncounters({ states: ['AWAITING_TRIAGE'] }, accessToken as string),
    enabled: canSee && accessToken != null,
    refetchInterval: 15_000,
  });

  if (!canSee) {
    return null;
  }

  const count = data?.length ?? 0;

  return (
    <NavLink to="/triage" end className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      <span className="nav-item-label">
        <span>Patient Queue</span>
        <span className="badge badge-neutral">{count}</span>
      </span>
    </NavLink>
  );
}

function CompletedAssessmentsLink() {
  const canSee = usePermission('TRIAGE_HISTORY_READ');
  if (!canSee) {
    return null;
  }
  return (
    <NavLink
      to="/triage/completed-assessments"
      className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}
    >
      Completed Assessments
    </NavLink>
  );
}
