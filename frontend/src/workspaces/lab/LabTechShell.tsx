import { useQuery } from '@tanstack/react-query';
import { NavLink, Outlet } from 'react-router-dom';
import { fetchLabOrderQueue } from '../../shared/api/labOrders';
import { fetchMyLabStockRequests } from '../../shared/api/labStockRequests';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { usePermission } from '../../shared/auth/usePermission';
import { countByBucket } from './labQueueBuckets';
import './LabTechShell.css';

// 15-lab-technician.md §2: sidebar order as specified — Test Queue, Lab
// Catalogue, Stock Requests, My History, divider, Profile. Same shell
// pattern as DoctorShell/TriageNurseShell: one NavLink component per item,
// each running its own usePermission check (renders null, never greyed
// out) and, where badged, its own polling query.
export function LabTechShell() {
  const { logout } = useAuth();
  const { data: me } = useMe();

  return (
    <div className="lab-shell-outer">
      <div className="lab-shell">
        <aside className="lab-sidebar">
          <div className="lab-sidebar-brand">ADAGS</div>
          <nav className="lab-nav">
            <TestQueueLink />
            <LabCatalogueLink />
            <StockRequestsLink />
            <MyHistoryLink />

            <div className="nav-divider" role="separator" />

            <NavLink to="/lab/profile" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
              Profile
            </NavLink>
          </nav>
          <div className="lab-sidebar-footer">
            <div className="lab-user">{me?.username}</div>
            <button type="button" className="lab-logout" onClick={() => logout()}>
              Sign out
            </button>
          </div>
        </aside>
        <main className="lab-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

// Ready-to-process count, matching TestQueuePage's own tab count exactly —
// both read fetchLabOrderQueue and bucket with the same countByBucket.
function TestQueueLink() {
  const { accessToken } = useAuth();
  const canSee = usePermission('LAB_QUEUE_READ');

  const { data } = useQuery({
    queryKey: ['lab-orders', 'queue'],
    queryFn: () => fetchLabOrderQueue(accessToken as string),
    enabled: canSee && accessToken != null,
    refetchInterval: 15_000,
  });

  if (!canSee) return null;

  const readyCount = countByBucket(data ?? [], 'ready');
  const hasEmergency = (data ?? []).some((row) => row.isEmergencyBypass);

  return (
    <NavLink to="/lab" end className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      <span className="nav-item-label">
        <span className="nav-item-label-text">
          Test Queue
          {hasEmergency && (
            <span className="nav-emergency-dot" aria-label="Emergency specimen waiting" title="Emergency specimen waiting" />
          )}
        </span>
        <span className="badge badge-neutral">{readyCount}</span>
      </span>
    </NavLink>
  );
}

function LabCatalogueLink() {
  const canSee = usePermission('LAB_ITEM_PROPOSE');
  if (!canSee) return null;
  return (
    <NavLink to="/lab/catalogue" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      Lab Catalogue
    </NavLink>
  );
}

function StockRequestsLink() {
  const { accessToken } = useAuth();
  const canSee = usePermission('LAB_STOCK_REQUEST_CREATE');

  const { data } = useQuery({
    queryKey: ['lab-stock-requests', 'mine'],
    queryFn: () => fetchMyLabStockRequests(accessToken as string),
    enabled: canSee && accessToken != null,
    refetchInterval: 15_000,
  });

  if (!canSee) return null;

  const pendingCount = (data ?? []).filter((r) => r.status === 'PENDING').length;

  return (
    <NavLink to="/lab/stock-requests" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      <span className="nav-item-label">
        <span>Stock Requests</span>
        {pendingCount > 0 && <span className="badge badge-neutral">{pendingCount}</span>}
      </span>
    </NavLink>
  );
}

function MyHistoryLink() {
  const canSee = usePermission('LAB_RESULT_READ');
  if (!canSee) return null;
  return (
    <NavLink to="/lab/history" className={({ isActive }) => (isActive ? 'nav-item nav-item-active' : 'nav-item')}>
      My History
    </NavLink>
  );
}
