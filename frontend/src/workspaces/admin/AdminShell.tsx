import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import './AdminShell.css';

interface NavLeaf {
  to: string;
  label: string;
  /** Omitted where docs/spec/18-admin.md §2 names a screen whose module
   *  (pricing, revenue, expenses, patients-financial) hasn't shipped a
   *  permission code yet — see the Phase 0 permission catalogue audit.
   *  Those items render unguarded rather than behind a code that doesn't
   *  exist, which would just hide them forever. */
  permission?: string;
}

// docs/spec/18-admin.md §2. Badge counts ("● 7 pending", "3", "1" in the
// spec) are left off — there is no pricing/revenue/expenses module behind
// them yet in Phase 0, and a fabricated number is worse than none.
const MAIN_NAV: NavLeaf[] = [
  { to: '/admin/pricing', label: 'Pricing / price proposals' },
  { to: '/admin/revenue', label: 'Revenue / invoice cancellations' },
  { to: '/admin/patients', label: 'Patients' },
  { to: '/admin/users', label: 'Users / add nurse / staff', permission: 'USER_MANAGE' },
];

const EXPENSE_CATEGORIES: NavLeaf[] = [
  { to: '/admin/expenses/medication-pharmacy', label: 'Medication & pharmacy' },
  { to: '/admin/expenses/medications', label: 'Medications' },
  { to: '/admin/expenses/salaries', label: 'Salaries' },
  { to: '/admin/expenses/utilities', label: 'Utilities' },
  { to: '/admin/expenses/maintenance', label: 'Maintenance' },
  { to: '/admin/expenses/equipment', label: 'Equipment' },
  { to: '/admin/expenses/other', label: 'Other' },
];

const AFTER_EXPENSES_NAV: NavLeaf[] = [{ to: '/admin/expense-analytics', label: 'Expense analytics' }];

// §2's divider — Audit log and Facility & settings are called out as
// "added — no other role can hold it".
const ADMIN_ONLY_NAV: NavLeaf[] = [
  { to: '/admin/audit-log', label: 'Audit log', permission: 'AUDIT_LOG_READ' },
  { to: '/admin/facility-settings', label: 'Facility & settings', permission: 'FACILITY_CONFIG' },
  { to: '/admin/profile', label: 'Profile' },
];

function NavItemLink({ item }: { item: NavLeaf }) {
  // Called unconditionally to satisfy the rules of hooks; the result is only
  // consulted when the item actually names a permission to check.
  const canSee = usePermission(item.permission ?? '__NONE__');
  if (item.permission && !canSee) {
    return null;
  }
  return (
    <NavLink to={item.to} className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      {item.label}
    </NavLink>
  );
}

export function AdminShell() {
  const { logout } = useAuth();
  const { data: me } = useMe();

  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        <div className="admin-sidebar-brand">ADAGS</div>
        <nav className="admin-nav">
          <NavLink to="/admin" end className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
            Dashboard
          </NavLink>

          {MAIN_NAV.map((item) => (
            <NavItemLink key={item.to} item={item} />
          ))}

          <div className="nav-section-label">Expenses</div>
          <div className="nav-subgroup">
            {EXPENSE_CATEGORIES.map((item) => (
              <NavItemLink key={item.to} item={item} />
            ))}
          </div>

          {AFTER_EXPENSES_NAV.map((item) => (
            <NavItemLink key={item.to} item={item} />
          ))}

          <div className="nav-divider" role="separator" />

          {ADMIN_ONLY_NAV.map((item) => (
            <NavItemLink key={item.to} item={item} />
          ))}
        </nav>
        <div className="admin-sidebar-footer">
          <div className="admin-user">{me?.username}</div>
          <button type="button" className="admin-logout" onClick={() => logout()}>
            Sign out
          </button>
        </div>
      </aside>
      <main className="admin-content">
        <Outlet />
      </main>
    </div>
  );
}
