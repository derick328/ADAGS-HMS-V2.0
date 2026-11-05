import { WorkspaceBrand } from '../../shared/components/WorkspaceBrand';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import './AdminShell.css';

interface NavLeaf {
  to: string;
  label: string;
  /** Omitted where docs/spec/18-admin.md §2 names a screen whose module
   *  (audit log, facility settings) hasn't shipped a backend endpoint yet.
   *  Those items render unguarded rather than behind a code that doesn't
   *  exist, which would just hide them forever. Revenue/expenses/analytics/
   *  patients/pricing/users now have real backends (InvoiceCancellationController,
   *  ExpenseController, ReportingController, PatientController,
   *  PharmacyItemProposalController, UserController) and gate on the
   *  permission each one actually requires server-side — this is visibility
   *  only, not the check (usePermission's own contract). */
  permission?: string;
}

// docs/spec/18-admin.md §2. Badge counts ("● 7 pending", "3", "1" in the
// spec) are left off — a fabricated number is worse than none.
//
// Pricing is scoped to the pharmacy item/price-change approval queue
// (12-pharmacist.md §9.2, PharmacyItemProposalController) — the only
// pricing-adjacent backend that exists. 18-admin.md §7's fuller catalogue
// (payer-aware PriceListEntry versioning, bulk import) has no backend yet.
const MAIN_NAV: NavLeaf[] = [
  { to: '/admin/pricing', label: 'Pricing / price proposals', permission: 'PHARMACY_ITEM_APPROVE' },
  { to: '/admin/revenue', label: 'Revenue / invoice cancellations', permission: 'INVOICE_CANCEL_APPROVE' },
  { to: '/admin/payment-bypass-waivers', label: 'Payment bypass waivers', permission: 'PAYMENT_BYPASS_WAIVE' },
  { to: '/admin/patients', label: 'Patients', permission: 'PATIENT_SEARCH' },
  { to: '/admin/tat', label: 'Turnaround Time', permission: 'TAT_ANALYTICS_READ' },
  { to: '/admin/users', label: 'Users / add nurse / staff', permission: 'USER_MANAGE' },
  { to: '/admin/theatres', label: 'Theatres', permission: 'THEATRE_MANAGE' },
];

// EXPENSE_CREATE and EXPENSE_APPROVE are granted together to every role
// that holds either (ADMIN, ACCOUNTANT) — gating on EXPENSE_CREATE alone
// is enough to decide link visibility for both.
const EXPENSE_CATEGORIES: NavLeaf[] = [
  { to: '/admin/expenses/medication-pharmacy', label: 'Medication & pharmacy', permission: 'EXPENSE_CREATE' },
  { to: '/admin/expenses/medications', label: 'Medications', permission: 'EXPENSE_CREATE' },
  { to: '/admin/expenses/salaries', label: 'Salaries', permission: 'EXPENSE_CREATE' },
  { to: '/admin/expenses/utilities', label: 'Utilities', permission: 'EXPENSE_CREATE' },
  { to: '/admin/expenses/maintenance', label: 'Maintenance', permission: 'EXPENSE_CREATE' },
  { to: '/admin/expenses/equipment', label: 'Equipment', permission: 'EXPENSE_CREATE' },
  { to: '/admin/expenses/other', label: 'Other', permission: 'EXPENSE_CREATE' },
];

const AFTER_EXPENSES_NAV: NavLeaf[] = [
  { to: '/admin/expense-analytics', label: 'Expense analytics', permission: 'FINANCIAL_REPORT_READ' },
];

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
    <div className="admin-shell app-shell">
        <a className="skip-link" href="#workspace-main">Skip to content</a>
      <aside className="admin-sidebar app-sidebar">
        <WorkspaceBrand workspace="Administration" />
        <nav className="admin-nav app-nav" aria-label="Administration navigation">
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
        <div className="admin-sidebar-footer app-sidebar-footer">
          <div className="admin-user">{me?.username}</div>
          <button type="button" className="admin-logout app-logout" onClick={() => logout()}>
            Sign out
          </button>
        </div>
      </aside>
      <main id="workspace-main" tabIndex={-1} className="admin-content app-content">
        <div className="workspace-body"><Outlet /></div>
      </main>
    </div>
  );
}
