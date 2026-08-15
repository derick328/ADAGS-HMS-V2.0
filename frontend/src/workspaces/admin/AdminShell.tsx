import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import './AdminShell.css';

interface NavItem {
  to: string;
  label: string;
  permission: string;
}

// Placeholder nav — docs/spec/18-admin.md (the real admin sidebar spec)
// doesn't exist in this repo yet. Items below are inferred from the ADMIN
// capabilities in docs/spec/01-architecture.md §6.3 and the permissions
// V2__permissions_and_roles.sql actually seeds. Expect this to be rebuilt
// once 18-admin.md is added.
const NAV_ITEMS: NavItem[] = [
  { to: '/admin/users', label: 'Users & roles', permission: 'USER_MANAGE' },
  { to: '/admin/facilities', label: 'Facilities', permission: 'FACILITY_CONFIG' },
  { to: '/admin/audit-log', label: 'Audit log', permission: 'AUDIT_LOG_READ' },
];

function NavItemLink({ item }: { item: NavItem }) {
  const canSee = usePermission(item.permission);
  if (!canSee) {
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
          {NAV_ITEMS.map((item) => (
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
