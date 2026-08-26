import { useQuery } from '@tanstack/react-query';
import { NavLink, Outlet } from 'react-router-dom';
import { fetchPendingLabStockRequests } from '../../shared/api/labStockRequests';
import { fetchPrescriptionQueue } from '../../shared/api/prescriptions';
import { fetchExpiringBatches, fetchLowStock } from '../../shared/api/stockReports';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import { bucketOf, countByBucket } from './pharmacyQueueBuckets';
import './PharmacyShell.css';

// 12-pharmacist.md §3: sidebar order as specified — Dashboard, History,
// Stock, Pharmacy Items, Lab Stock Requests, divider, Profile. Badges:
// dispense queue depth · combined low-stock and expiry alerts · pending
// lab requests. Same shell pattern as LabTechShell: one NavLink per item,
// each running its own usePermission check (renders null, never greyed
// out) and, where badged, its own polling query.
export function PharmacyShell() {
  const { logout } = useAuth();
  const { data: me } = useMe();

  return (
    <div className="pharmacy-shell-outer">
      <div className="pharmacy-shell">
        <aside className="pharmacy-sidebar">
          <div className="pharmacy-sidebar-brand">ADAGS</div>
          <nav className="pharmacy-nav">
            <DashboardLink />
            <DispensingQueueLink />
            <HistoryLink />
            <StockLink />
            <PharmacyItemsLink />
            <LabStockRequestsLink />

            <div className="nav-divider" role="separator" />

            <NavLink to="/pharmacy/profile" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
              Profile
            </NavLink>
          </nav>
          <div className="pharmacy-sidebar-footer">
            <div className="pharmacy-user">{me?.username}</div>
            <button type="button" className="pharmacy-logout" onClick={() => logout()}>
              Sign out
            </button>
          </div>
        </aside>
        <main className="pharmacy-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function DashboardLink() {
  const { accessToken } = useAuth();
  const canSee = usePermission('PRESCRIPTION_QUEUE_READ');

  const { data } = useQuery({
    queryKey: ['prescriptions', 'queue'],
    queryFn: () => fetchPrescriptionQueue(accessToken as string),
    enabled: canSee && accessToken != null,
    refetchInterval: 15_000,
  });

  if (!canSee) return null;

  const rows = data ?? [];
  const readyCount = countByBucket(rows, 'ready') + countByBucket(rows, 'partiallyPayable');
  const hasEmergency = rows.some((row) => bucketOf(row, rows) === 'emergency');

  return (
    <NavLink to="/pharmacy" end className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      <span className="nav-item-label">
        <span className="nav-item-label-text">
          Dashboard
          {hasEmergency && (
            <span className="nav-emergency-dot" aria-label="Emergency bypass waiting" title="Emergency bypass waiting" />
          )}
        </span>
        <span className="badge badge-neutral">{readyCount}</span>
      </span>
    </NavLink>
  );
}

function DispensingQueueLink() {
  const canSee = usePermission('PRESCRIPTION_QUEUE_READ');
  if (!canSee) return null;
  return (
    <NavLink
      to="/pharmacy/dispensing"
      className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}
    >
      Dispensing queue
    </NavLink>
  );
}

function HistoryLink() {
  const canSee = usePermission('DISPENSE_HISTORY_READ');
  if (!canSee) return null;
  return (
    <NavLink to="/pharmacy/history" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      History
    </NavLink>
  );
}

function StockLink() {
  const { accessToken } = useAuth();
  const canSee = usePermission('STOCK_READ');

  const { data: lowStock } = useQuery({
    queryKey: ['stock-reports', 'low-stock'],
    queryFn: () => fetchLowStock(accessToken as string),
    enabled: canSee && accessToken != null,
    refetchInterval: 60_000,
  });
  const { data: expiring } = useQuery({
    queryKey: ['stock-reports', 'expiring-batches'],
    queryFn: () => fetchExpiringBatches(accessToken as string),
    enabled: canSee && accessToken != null,
    refetchInterval: 60_000,
  });

  if (!canSee) return null;

  const alertCount = (lowStock ?? []).length + (expiring ?? []).length;

  return (
    <NavLink to="/pharmacy/stock" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      <span className="nav-item-label">
        <span>Stock</span>
        {alertCount > 0 && <span className="badge badge-warning">{alertCount}</span>}
      </span>
    </NavLink>
  );
}

function PharmacyItemsLink() {
  const canSee = usePermission('PHARMACY_ITEM_PROPOSE');
  if (!canSee) return null;
  return (
    <NavLink to="/pharmacy/items" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      Pharmacy Items
    </NavLink>
  );
}

function LabStockRequestsLink() {
  const { accessToken } = useAuth();
  const canSee = usePermission('LAB_STOCK_REQUEST_REVIEW');

  const { data } = useQuery({
    queryKey: ['lab-stock-requests', 'pending'],
    queryFn: () => fetchPendingLabStockRequests(accessToken as string),
    enabled: canSee && accessToken != null,
    refetchInterval: 15_000,
  });

  if (!canSee) return null;

  const pendingCount = (data ?? []).length;

  return (
    <NavLink to="/pharmacy/lab-stock-requests" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      <span className="nav-item-label">
        <span>Lab Stock Requests</span>
        {pendingCount > 0 && <span className="badge badge-neutral">{pendingCount}</span>}
      </span>
    </NavLink>
  );
}
