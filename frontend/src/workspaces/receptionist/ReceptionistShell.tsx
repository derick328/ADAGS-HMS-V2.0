import { useQuery } from '@tanstack/react-query';
import { NavLink, Outlet } from 'react-router-dom';
import { searchInvoices } from '../../shared/api/invoices';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import { EmergencyBillingBar } from './EmergencyBillingBar';
import './ReceptionistShell.css';

interface NavLeaf {
  to: string;
  label: string;
  /** Visibility only, not the check — usePermission's own contract (every
   *  action is re-checked server-side). Chosen as the closest single
   *  permission from 05-receptionist.md §1.1's granted list for what each
   *  screen displays; several screens share a permission because the list
   *  doesn't have a 1:1 code per screen (e.g. no ENCOUNTER_READ). */
  permission?: string;
}

// 05a-receptionist-decisions.md §6 (final for v1) — supersedes
// 05-receptionist.md §2's route table where they disagree. Two omissions
// are deliberate, not oversights:
//   - Referrals: hidden, not built — absent from this array entirely,
//     never rendered greyed-out (05a §6: "greyed-out items train users to
//     stop reading the sidebar").
//   - Appointments: no nav item. D1 — forward booking is out of scope;
//     only doctors create appointments, as follow-ups. The receptionist
//     reads them through Expected Returns, read-only (D10).
const MAIN_NAV: NavLeaf[] = [
  { to: '/receptionist/patient-intake', label: 'Patient Intake', permission: 'PATIENT_SEARCH' },
  { to: '/receptionist/billing', label: 'Billing', permission: 'INVOICE_READ' },
  { to: '/receptionist/expected-returns', label: 'Expected Returns', permission: 'PATIENT_READ_VISIT_HISTORY' },
  { to: '/receptionist/patient-records', label: 'Patient Records', permission: 'PATIENT_READ_VISIT_HISTORY' },
];

const AFTER_DIVIDER_NAV: NavLeaf[] = [
  { to: '/receptionist/shift', label: 'Cashier Shift', permission: 'CASHIER_SHIFT_OPEN' },
  { to: '/receptionist/profile', label: 'Profile' },
];

function NavItemLink({ item, showDot }: { item: NavLeaf; showDot?: boolean }) {
  // Called unconditionally to satisfy the rules of hooks; the result is only
  // consulted when the item actually names a permission to check.
  const canSee = usePermission(item.permission ?? '__NONE__');
  if (item.permission && !canSee) {
    return null;
  }
  return (
    <NavLink to={item.to} className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      {item.label}
      {showDot && <span className="nav-dot" role="status" aria-label="Pending bills waiting" title="Pending bills waiting" />}
    </NavLink>
  );
}

export function ReceptionistShell() {
  const { accessToken, logout } = useAuth();
  const { data: me } = useMe();
  const canReadInvoices = usePermission('INVOICE_READ');

  // Drives the red dot on the Billing item — any outstanding invoice.
  const { data: pendingInvoices } = useQuery({
    queryKey: ['invoices', 'pending-count'],
    queryFn: () => searchInvoices({ outstandingOnly: true }, accessToken as string),
    enabled: accessToken != null && canReadInvoices,
    refetchInterval: 30_000,
  });
  const hasPendingBills = (pendingInvoices?.length ?? 0) > 0;

  return (
    <div className="receptionist-shell-outer">
      <EmergencyBillingBar />
      <div className="receptionist-shell">
        <aside className="receptionist-sidebar">
          <div className="receptionist-sidebar-brand">ADAGS</div>
          <nav className="receptionist-nav">
            <NavLink to="/receptionist" end className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
              Dashboard
            </NavLink>

            {MAIN_NAV.map((item) => (
              <NavItemLink
                key={item.to}
                item={item}
                showDot={item.to === '/receptionist/billing' && hasPendingBills}
              />
            ))}

            <div className="nav-divider" role="separator" />

            {AFTER_DIVIDER_NAV.map((item) => (
              <NavItemLink key={item.to} item={item} />
            ))}
          </nav>
          <div className="receptionist-sidebar-footer">
            <div className="receptionist-user">{me?.username}</div>
            <button type="button" className="receptionist-logout" onClick={() => logout()}>
              Sign out
            </button>
          </div>
        </aside>
        <main className="receptionist-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
