import { Tabs } from '../../shared/components/Tabs';
import { FeedbackState } from '../../shared/components/FeedbackState';
import { EmptyState } from '../../shared/components/EmptyState';
import { PackageOpen } from 'lucide-react';
import { PageHeader } from '../../shared/components/PageHeader';
import { SearchField } from '../../shared/components/SearchField';
import { DropdownSelect } from '../../shared/components/DropdownSelect';
import { QueryState } from '../../shared/components/WorkspacePage';
import { TableRegion } from '../../shared/components/TableRegion';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { fetchCurrentStock, fetchExpiringBatches, fetchLowStock, fetchStockValuation } from '../../shared/api/stockReports';
import { fetchStockReceipts } from '../../shared/api/stockReceipts';
import { fetchStockMovements } from '../../shared/api/stockMovements';
import { fetchAuthorizedStockStores } from '../../shared/api/stores';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { formatDate } from '../../shared/format/date';
import { formatMoney } from '../../shared/format/money';
import { formatDateTime } from '../../shared/format/date';
import { StockInPage } from './StockInPage';
import { StockAdjustmentsPage } from './StockAdjustmentsPage';

type Tab = 'current' | 'stockIn' | 'movements' | 'low' | 'expiring' | 'expired' | 'history' | 'adjustments' | 'valuation';

const TABS: { key: Tab; label: string }[] = [
  { key: 'current', label: 'Current stock' },
  { key: 'stockIn', label: 'Stock in' },
  { key: 'movements', label: 'Movements' },
  { key: 'low', label: 'Low stock' },
  { key: 'expiring', label: 'Expiring (90d)' },
  { key: 'expired', label: 'Expired medicines' },
  { key: 'history', label: 'Receiving history' },
  { key: 'adjustments', label: 'Adjustments' },
  { key: 'valuation', label: 'Valuation' },
];

// 12-pharmacist.md §7: current stock / low-stock alerts / batches expiring
// within 90 days / stock valuation, plus the restock form (§7.2).
export function StockPage() {
  const [tab, setTab] = useState<Tab>('current');
  const [visited, setVisited] = useState<Partial<Record<Tab, boolean>>>({ current: true });
  const canRestock = usePermission('STOCK_RESTOCK');
  const selectTab = (next: Tab) => {
    setVisited((current) => current[next] ? current : { ...current, [next]: true });
    setTab(next);
  };

  return (
    <div className="stock-workspace">
      <PageHeader title={<>Stock</>} description={<>Review available batches, expiry dates, and incoming stock.</>} />

      <Tabs aria-label="Stock views" variant="underline" value={tab} onChange={(value) => selectTab(value as Tab)}
        items={TABS.map(({ key, label }) => ({ id: key, label, disabled: key === 'stockIn' && !canRestock, content: visited[key] ? {
          current: <CurrentStockView onStockIn={() => selectTab('stockIn')} canRestock={canRestock} />, stockIn: canRestock ? <StockInPage /> : <FeedbackState>Stock receiving is unavailable for this account.</FeedbackState>, movements: <MovementsView />,
          low: <LowStockView />, expiring: <ExpiringView />, expired: <ExpiredView />, history: <ReceivingHistoryView />,
          adjustments: <StockAdjustmentsPage />,
          valuation: <ValuationView />,
        }[key] : null }))} />

    </div>
  );
}

function MovementsView() {
  const { accessToken } = useAuth();
  const [selectedStoreId, setSelectedStoreId] = useState('');
  const { data: stores } = useQuery({ queryKey: ['stores', 'authorized-stock'],
    queryFn: () => fetchAuthorizedStockStores(accessToken as string), enabled: accessToken != null });
  const storeId = selectedStoreId || stores?.[0]?.id || '';
  const { data, isLoading, isError, refetch } = useQuery({ queryKey: ['stock-movements', storeId],
    queryFn: () => fetchStockMovements(storeId, accessToken as string),
    enabled: accessToken != null && !!storeId });
  const rows = data ?? [];
  return <><StorePicker stores={stores ?? []} value={storeId} onChange={setSelectedStoreId} />
  <TableShell isLoading={isLoading} isError={isError} onRetry={() => void refetch()} isEmpty={rows.length === 0} emptyMessage="No movements recorded.">
    <thead><tr><th>Date</th><th>Medicine</th><th>Batch</th><th>Movement</th><th>Base units</th></tr></thead>
    <tbody>{rows.map((row) => <tr key={row.id}><td>{formatDateTime(row.occurredAt)}</td>
      <td>{row.itemName} · {row.itemCode}</td><td>{row.batchNumber}</td><td>{row.movementType}</td>
      <td className="tabular">{row.quantityDelta > 0 ? '+' : ''}{row.quantityDelta}</td></tr>)}</tbody>
  </TableShell></>;
}

function ReceivingHistoryView() {
  const { accessToken } = useAuth();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selectedStoreId, setSelectedStoreId] = useState('');
  const { data: stores } = useQuery({ queryKey: ['stores', 'authorized-stock'],
    queryFn: () => fetchAuthorizedStockStores(accessToken as string), enabled: accessToken != null });
  const storeId = selectedStoreId || stores?.[0]?.id || '';
  const { data, isLoading, isError, refetch } = useQuery({ queryKey: ['stock-receipts', storeId],
    queryFn: () => fetchStockReceipts(storeId, accessToken as string),
    enabled: accessToken != null && !!storeId });
  const rows = data ?? [];
  const selected = rows.find((row) => row.id === selectedId);
  return <>
    <StorePicker stores={stores ?? []} value={storeId} onChange={(id) => { setSelectedStoreId(id); setSelectedId(null); }} />
    <TableShell isLoading={isLoading} isError={isError} onRetry={() => void refetch()} isEmpty={rows.length === 0} emptyMessage="No confirmed deliveries.">
      <thead><tr><th>Receipt</th><th>Supplier</th><th>Invoice</th><th>Delivery note</th><th>Delivery date</th><th>Confirmed</th><th>Received by</th><th>Lines</th><th>Value</th><th>Status</th></tr></thead>
      <tbody>{rows.map((row) => <tr key={row.id}>
        <td><button type="button" className="btn btn-ghost" onClick={() => setSelectedId(row.id)}>{row.receiptNumber}</button></td>
        <td>{row.supplierName}</td><td>{row.invoiceNumber}</td><td>{row.deliveryNoteNumber}</td>
        <td>{formatDate(row.deliveryDate)}</td><td>{formatDateTime(row.confirmedAt)}</td><td>{row.receivedByName ?? row.receivedBy}</td>
        <td className="tabular">{row.lines.length}</td><td className="money">{row.totalReceivedCostMinor == null ? 'Unknown' : formatMoney(row.totalReceivedCostMinor, row.currencyCode)}</td>
        <td><span className="badge badge-success">Confirmed</span></td></tr>)}</tbody>
    </TableShell>
    {selected && <section className="stock-in-card" aria-label="Receipt detail">
      <div className="stock-in-section-heading"><h3>{selected.receiptNumber}</h3>
        <button type="button" className="btn btn-secondary" onClick={() => window.print()}>Print receipt</button></div>
      <p>{selected.supplierName} · Invoice {selected.invoiceNumber} ({formatDate(selected.invoiceDate)})
        {' '}· Delivery note {selected.deliveryNoteNumber} · Delivered {formatDate(selected.deliveryDate)}
        {' '}· Received by {selected.receivedByName ?? selected.receivedBy}</p>
      {selected.notes && <p>Notes: {selected.notes}</p>}
      <TableRegion label="Receipt lines"><table className="table"><thead><tr><th>Medicine</th><th>Batch</th><th>Purchased unit</th><th>Cost per purchase unit</th><th>Ordered</th><th>Received</th><th>Accepted base</th><th>Rejected base</th><th>Missing</th><th>Reason</th><th>Accepted value</th></tr></thead>
        <tbody>{selected.lines.map((line) => <tr key={line.id}>
          <td>{line.itemName}</td><td>{line.batchNumber} · exp {formatDate(line.expiryDate)}</td>
          <td>{line.purchaseUnit} = {line.factor} {line.baseUnit}{line.blistersPerPack != null && line.unitsPerBlister != null && <div>{line.blistersPerPack} blisters × {line.unitsPerBlister} {line.baseUnit} per blister</div>}</td><td>{line.unitPurchaseCostMinor == null ? 'Unknown' : formatMoney(line.unitPurchaseCostMinor, selected.currencyCode)}</td><td>{line.orderedQuantity ?? '—'}</td><td>{line.receivedQuantity}</td>
          <td>{line.acceptedBaseQuantity} {line.baseUnit}</td><td>{line.rejectedBaseQuantity} {line.baseUnit}</td><td>{line.missingQuantity}</td>
          <td>{line.discrepancyReason ?? '—'}</td>
          <td>{line.acceptedCostMinor == null ? 'Unknown' : formatMoney(line.acceptedCostMinor, selected.currencyCode)}</td>
        </tr>)}</tbody></table></TableRegion>
      <p>Accepted inventory value: {selected.totalAcceptedCostMinor == null ? 'Unknown' : formatMoney(selected.totalAcceptedCostMinor, selected.currencyCode)}</p>
    </section>}
  </>;
}

function StorePicker({ stores, value, onChange }: { stores: { id?: string; name?: string }[];
  value: string; onChange: (id: string) => void }) {
  return <label className="stock-store-picker">
    Store <select className="clinical-control" value={value} onChange={(e) => onChange(e.target.value)}>
      {stores.map((store) => <option key={store.id} value={store.id}>{store.name}</option>)}
    </select>
  </label>;
}

function CurrentStockView({ onStockIn, canRestock }: { onStockIn: () => void; canRestock: boolean }) {
  const { accessToken } = useAuth();
  const [search, setSearch] = useState('');
  const [storeFilter, setStoreFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['stock-reports', 'current-stock'],
    queryFn: () => fetchCurrentStock(accessToken as string),
    enabled: accessToken != null,
  });
  const rows = (data ?? []).filter((row) =>
    (!storeFilter || row.storeId === storeFilter) &&
    (!statusFilter || row.stockStatus === statusFilter) &&
    (!search || `${row.itemName} ${row.itemCode}`.toLowerCase().includes(search.toLowerCase())));
  const totalsByItem = new Map<string, number>();
  (data ?? []).filter((row) => !storeFilter || row.storeId === storeFilter).forEach((row) => {
    if (row.itemId) totalsByItem.set(row.itemId, (totalsByItem.get(row.itemId) ?? 0) + (row.quantity ?? 0));
  });

  return (
    <>
    <div className="stock-toolbar">
      <label>Search medicine <SearchField value={search} onChange={(e) => setSearch(e.target.value)} onClear={() => setSearch('')} placeholder="Name or code" /></label>
      <DropdownSelect label="Store" value={storeFilter} onChange={setStoreFilter} options={[{ value: '', label: 'All authorized stores' }, ...[...new Map((data ?? []).filter((row) => row.storeId).map((row) => [row.storeId, row.storeName])).entries()].map(([id, name]) => ({ value: id ?? '', label: name ?? id ?? 'Store' }))]} />
      <DropdownSelect label="Status" value={statusFilter} onChange={setStatusFilter} options={[{ value: '', label: 'All statuses' }, ...[...new Set((data ?? []).map((row) => row.stockStatus).filter((status): status is string => Boolean(status)))].map((status) => ({ value: status, label: status }))]} />
    </div>
    <TableShell isLoading={isLoading} isError={isError} onRetry={() => void refetch()} isEmpty={rows.length === 0} emptyMessage={search || storeFilter || statusFilter ? 'No stock matches these filters.' : 'No stock on hand.'} action={canRestock && !search && !storeFilter && !statusFilter ? <button type="button" className="btn btn-primary" onClick={onStockIn}>Stock in</button> : undefined}>
      <thead>
        <tr>
          <th>Item</th>
          <th>Total in stock</th>
          <th>Strength</th>
          <th>Batch</th>
          <th>Expiry</th>
          <th>Store</th>
          <th>Physical quantity</th>
          <th>Available</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i}>
            <td>{r.itemName} <span style={{ color: 'var(--text-muted)' }}>· {r.itemCode}</span></td>
            <td className="tabular">{totalsByItem.get(r.itemId ?? '') ?? 0} {r.baseUnit}</td>
            <td>{r.strength ?? '—'}</td>
            <td style={{ fontFamily: 'var(--font-mono)' }}>{r.batchNumber}</td>
            <td className="tabular">{formatDate(r.expiryDate)}</td>
            <td>{r.storeName}</td>
            <td className="tabular">{r.quantity} {r.baseUnit}</td>
            <td className="tabular">{r.availableQuantity ?? (r.stockStatus === 'ACTIVE' ? r.quantity : 0)} {r.baseUnit}</td>
            <td><span className={r.stockStatus === 'ACTIVE' ? 'badge badge-success' : r.stockStatus === 'EXPIRED' ? 'badge badge-critical' : 'badge badge-warning'}>
              {r.stockStatus === 'ACTIVE' ? 'Available' : r.stockStatus}</span></td>
          </tr>
        ))}
      </tbody>
    </TableShell>
    </>
  );
}

function LowStockView() {
  const { accessToken } = useAuth();
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['stock-reports', 'low-stock'],
    queryFn: () => fetchLowStock(accessToken as string),
    enabled: accessToken != null,
  });
  const rows = data ?? [];

  return (
    <TableShell isLoading={isLoading} isError={isError} onRetry={() => void refetch()} isEmpty={rows.length === 0} emptyMessage="Nothing is below its reorder level.">
      <thead>
        <tr>
          <th>Item</th>
          <th>On hand</th>
          <th>Reorder level</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i} className="row-warning">
            <td>{r.itemName} <span style={{ color: 'var(--text-muted)' }}>· {r.itemCode}</span></td>
            <td className="tabular">{r.totalQuantity}</td>
            <td className="tabular">{r.reorderLevel}</td>
          </tr>
        ))}
      </tbody>
    </TableShell>
  );
}

function ExpiringView() {
  const { accessToken } = useAuth();
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['stock-reports', 'expiring-batches'],
    queryFn: () => fetchExpiringBatches(accessToken as string),
    enabled: accessToken != null,
  });
  const rows = data ?? [];

  return (
    <TableShell isLoading={isLoading} isError={isError} onRetry={() => void refetch()} isEmpty={rows.length === 0} emptyMessage="Nothing expiring within 90 days.">
      <thead>
        <tr>
          <th>Item</th>
          <th>Batch</th>
          <th>Expiry</th>
          <th>Quantity</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i} className="row-warning">
            <td>{r.itemName} <span style={{ color: 'var(--text-muted)' }}>· {r.itemCode}</span></td>
            <td style={{ fontFamily: 'var(--font-mono)' }}>{r.batchNumber}</td>
            <td className="tabular">{formatDate(r.expiryDate)}</td>
            <td className="tabular">{r.quantity}</td>
          </tr>
        ))}
      </tbody>
    </TableShell>
  );
}

function ExpiredView() {
  const { accessToken } = useAuth();
  const [search, setSearch] = useState('');
  const [storeFilter, setStoreFilter] = useState('');
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['stock-reports', 'current-stock'],
    queryFn: () => fetchCurrentStock(accessToken as string),
    enabled: accessToken != null,
  });
  const expired = (data ?? []).filter((row) => row.stockStatus === 'EXPIRED');
  const rows = expired.filter((row) =>
    (!storeFilter || row.storeId === storeFilter) &&
    (!search || `${row.itemName} ${row.itemCode} ${row.batchNumber}`.toLowerCase().includes(search.toLowerCase())));

  return <>
    <div className="stock-toolbar">
      <label>Search medicine <SearchField value={search} onChange={(e) => setSearch(e.target.value)} onClear={() => setSearch('')} placeholder="Name, code or batch" /></label>
      <DropdownSelect label="Store" value={storeFilter} onChange={setStoreFilter} options={[{ value: '', label: 'All authorized stores' }, ...[...new Map(expired.filter((row) => row.storeId).map((row) => [row.storeId, row.storeName])).entries()].map(([id, name]) => ({ value: id ?? '', label: name ?? id ?? 'Store' }))]} />
    </div>
    <TableShell isLoading={isLoading} isError={isError} onRetry={() => void refetch()} isEmpty={rows.length === 0} emptyMessage="No expired medicines in stock.">
      <thead><tr><th>Medicine</th><th>Batch</th><th>Expiry</th><th>Store</th><th>Quantity on hand</th><th>Status</th></tr></thead>
      <tbody>{rows.map((row) => <tr key={`${row.storeId}-${row.batchId}`}>
        <td>{row.itemName} <span style={{ color: 'var(--text-muted)' }}>· {row.itemCode}</span></td>
        <td>{row.batchNumber}</td><td className="tabular">{formatDate(row.expiryDate)}</td>
        <td>{row.storeName}</td><td className="tabular">{row.quantity} {row.baseUnit}</td>
        <td><span className="badge badge-critical">Expired</span></td>
      </tr>)}</tbody>
    </TableShell>
  </>;
}

function ValuationView() {
  const { accessToken } = useAuth();
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['stock-reports', 'valuation'],
    queryFn: () => fetchStockValuation(accessToken as string),
    enabled: accessToken != null,
  });
  const rows = data ?? [];

  return (
    <TableShell isLoading={isLoading} isError={isError} onRetry={() => void refetch()} isEmpty={rows.length === 0} emptyMessage="No valuation to show.">
      <thead>
        <tr>
          <th>Store</th>
          <th>Total value</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i}>
            <td>{r.storeName}</td>
            <td className="tabular">{r.totalValueAmountMinor == null ? 'Unknown cost in stock' : formatMoney(r.totalValueAmountMinor, r.currencyCode)}</td>
          </tr>
        ))}
      </tbody>
    </TableShell>
  );
}

function TableShell({ isLoading, isError, onRetry, isEmpty, emptyMessage, action, children }: { isLoading: boolean; isError: boolean; onRetry: () => void; isEmpty: boolean; emptyMessage: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="stock-table-shell">
      {isLoading ? (
        <FeedbackState loading>Loading stock data…</FeedbackState>
      ) : isError ? (
        <QueryState title="Unable to load stock data" description="Check the connection and try again." onRetry={onRetry} />
      ) : isEmpty ? (
        <div className="stock-empty"><EmptyState icon={PackageOpen} title={emptyMessage} description={emptyMessage === 'No stock on hand.' ? 'Received and accepted medicine batches will appear here.' : undefined} />{action}</div>
      ) : (
        <TableRegion label="Stock report"><table className="table">
          <caption className="sr-only">Stock report</caption>
          {children}
        </table></TableRegion>
      )}
    </div>
  );
}
