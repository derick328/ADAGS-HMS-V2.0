import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { type LucideIcon, Activity, ChevronDown, ClipboardList, Clock3, CreditCard, Hospital, LayoutDashboard, LogOut, Menu, Package, Receipt, Settings, Tags, UserRound, UserRoundCog, Users, Wallet, X } from 'lucide-react';
import { WorkspaceBrand } from '../../shared/components/WorkspaceBrand';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import './AdminShell.css';

interface NavLeaf { to: string; label: string; icon: LucideIcon; permission?: string; end?: boolean }
interface NavGroup { title: string; items: NavLeaf[]; collapsible?: boolean }

// Routes and permission codes are the existing admin contract. Grouping only changes presentation.
const NAV: NavGroup[] = [
  { title: 'Overview', items: [
    { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
    { to: '/admin/patients', label: 'Patients', icon: Users, permission: 'PATIENT_SEARCH' },
    { to: '/admin/tat', label: 'Turnaround time', icon: Clock3, permission: 'TAT_ANALYTICS_READ' },
  ] },
  { title: 'Finance', items: [
    { to: '/admin/revenue', label: 'Revenue & cancellations', icon: Wallet, permission: 'INVOICE_CANCEL_APPROVE' },
    { to: '/admin/pricing', label: 'Pricing & proposals', icon: Tags, permission: 'PHARMACY_ITEM_APPROVE' },
    { to: '/admin/payment-bypass-waivers', label: 'Payment bypass waivers', icon: CreditCard, permission: 'PAYMENT_BYPASS_WAIVE' },
    { to: '/admin/expense-analytics', label: 'Expense analytics', icon: Activity, permission: 'FINANCIAL_REPORT_READ' },
  ], collapsible: true },
  { title: 'Expense management', items: [
    { to: '/admin/expenses/medication-pharmacy', label: 'Medication & pharmacy', icon: Package, permission: 'EXPENSE_CREATE' },
    { to: '/admin/expenses/medications', label: 'Medications', icon: Receipt, permission: 'EXPENSE_CREATE' },
    { to: '/admin/expenses/salaries', label: 'Salaries', icon: Receipt, permission: 'EXPENSE_CREATE' },
    { to: '/admin/expenses/utilities', label: 'Utilities', icon: Receipt, permission: 'EXPENSE_CREATE' },
    { to: '/admin/expenses/maintenance', label: 'Maintenance', icon: Receipt, permission: 'EXPENSE_CREATE' },
    { to: '/admin/expenses/equipment', label: 'Equipment', icon: Receipt, permission: 'EXPENSE_CREATE' },
    { to: '/admin/expenses/other', label: 'Other expenses', icon: Receipt, permission: 'EXPENSE_CREATE' },
  ], collapsible: true },
  { title: 'Hospital management', items: [
    { to: '/admin/users', label: 'Users & staff', icon: UserRoundCog, permission: 'USER_MANAGE' },
    { to: '/admin/departments', label: 'Clinical departments', icon: Hospital, permission: 'USER_MANAGE' },
    { to: '/admin/theatres', label: 'Theatres', icon: Hospital, permission: 'THEATRE_MANAGE' },
  ], collapsible: true },
  { title: 'Administration', items: [
    { to: '/admin/audit-log', label: 'Audit log', icon: ClipboardList, permission: 'AUDIT_LOG_READ' },
    { to: '/admin/facility-settings', label: 'Facility settings', icon: Settings, permission: 'FACILITY_CONFIG' },
    { to: '/admin/profile', label: 'Profile', icon: UserRound },
  ], collapsible: true },
];

function NavItem({ item, onNavigate }: { item: NavLeaf; onNavigate: () => void }) {
  const canSee = usePermission(item.permission ?? '__NONE__');
  if (item.permission && !canSee) return null;
  const IconComponent = item.icon;
  return <NavLink to={item.to} end={item.end} onClick={onNavigate} className={({ isActive }) => `nav-item${isActive ? ' nav-item-active' : ''}`}>
    <IconComponent size={18} aria-hidden={true} /><span>{item.label}</span>
  </NavLink>;
}

function NavigationGroup({ group, pathname, onNavigate }: { group: NavGroup; pathname: string; onNavigate: () => void }) {
  const active = group.items.some(item => pathname === item.to || (!item.end && pathname.startsWith(`${item.to}/`)));
  const [expanded, setExpanded] = useState(!group.collapsible || active);
  useEffect(() => { if (active) setExpanded(true); }, [active]);
  const id = `admin-nav-${group.title.toLowerCase().replaceAll(' ', '-')}`;
  return <div className="admin-nav-group">
    {group.collapsible ? <button type="button" className="admin-nav-heading admin-nav-toggle" aria-expanded={expanded} aria-controls={id} onClick={() => setExpanded(value => active ? true : !value)}>
      <span>{group.title}</span><ChevronDown size={15} aria-hidden="true" className={expanded ? 'admin-chevron-open' : ''} />
    </button> : <div className="admin-nav-heading">{group.title}</div>}
    {expanded && <div id={id} className="admin-nav-items">{group.items.map(item => <NavItem key={item.to} item={item} onNavigate={onNavigate} />)}</div>}
  </div>;
}

export function AdminShell() {
  const { logout } = useAuth();
  const { data: me } = useMe();
  const { pathname } = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const closeDrawer = () => { setDrawerOpen(false); document.querySelector<HTMLButtonElement>('.admin-mobile-toggle')?.focus(); };
  useEffect(() => { setDrawerOpen(false); }, [pathname]);
  useEffect(() => {
    if (!drawerOpen) return;
    const close = (event: KeyboardEvent) => { if (event.key === 'Escape') { setDrawerOpen(false); document.querySelector<HTMLButtonElement>('.admin-mobile-toggle')?.focus(); } };
    window.addEventListener('keydown', close);
    return () => window.removeEventListener('keydown', close);
  }, [drawerOpen]);
  return <div className="admin-shell app-shell">
    <a className="skip-link" href="#workspace-main">Skip to content</a>
    <header className="admin-mobile-header"><WorkspaceBrand workspace="Administration" /><button type="button" className="admin-mobile-toggle" aria-label="Open navigation" aria-expanded={drawerOpen} aria-controls="admin-sidebar" onClick={() => setDrawerOpen(true)}><Menu size={22} /></button></header>
    {drawerOpen && <button type="button" className="admin-drawer-scrim" aria-label="Close navigation" onClick={closeDrawer} />}
    <aside id="admin-sidebar" className={`admin-sidebar app-sidebar${drawerOpen ? ' admin-sidebar-open' : ''}`}>
      <div className="admin-brand-row"><WorkspaceBrand workspace="Administration" /><button type="button" className="admin-drawer-close" aria-label="Close navigation" onClick={closeDrawer}><X size={20} /></button></div>
      <nav className="admin-nav app-nav" aria-label="Administration navigation">{NAV.map(group => <NavigationGroup key={group.title} group={group} pathname={pathname} onNavigate={() => { setDrawerOpen(false); if (window.matchMedia('(max-width: 900px)').matches) document.getElementById('workspace-main')?.focus(); }} />)}</nav>
      <div className="admin-sidebar-footer app-sidebar-footer"><div className="admin-user"><span className="admin-user-avatar" aria-hidden="true">{me?.username?.slice(0, 1).toUpperCase() || 'A'}</span><span><strong>{me?.username || 'Administrator'}</strong><small>Administration</small></span></div><button type="button" className="admin-logout app-logout" onClick={logout}><LogOut size={17} aria-hidden="true" />Sign out</button></div>
    </aside>
    <main id="workspace-main" tabIndex={-1} className="admin-content app-content"><div className="workspace-body"><Outlet /></div></main>
  </div>;
}
