import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchLabOrderQueue, type LabOrderQueueRowResponse } from '../../shared/api/labOrders';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatWaitingSince } from '../../shared/format/date';
import { QueueShell, type QueueRow, type QueueTab } from '../../shared/components/QueueShell';
import { bucketOf, compareLabQueueRows, countByBucket, type LabQueueTabKey } from './labQueueBuckets';

// 15-lab-technician.md §3: five sub-tabs over one flat queue response.
// Sort within a tab per §3.2 (urgency, then time); emergency rows already
// have their own tab, so no separate isEmergency flag is needed on top of
// the bucket split itself.
const TAB_ORDER: { key: LabQueueTabKey; label: string }[] = [
  { key: 'ready', label: 'Ready to process' },
  { key: 'inProgress', label: 'In progress' },
  { key: 'awaitingPayment', label: 'Awaiting payment' },
  { key: 'emergency', label: 'Emergency (bypass)' },
  { key: 'overdue', label: 'Overdue' },
];

export function TestQueuePage() {
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<LabQueueTabKey>('ready');

  const { data, isLoading } = useQuery({
    queryKey: ['lab-orders', 'queue'],
    queryFn: () => fetchLabOrderQueue(accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 10_000,
  });

  const rows = data ?? [];
  const tabs: QueueTab[] = TAB_ORDER.map(({ key, label }) => ({
    key,
    label,
    count: countByBucket(rows, key),
    urgent: key === 'emergency' || key === 'overdue',
  }));

  const visibleRows = rows.filter((row) => bucketOf(row) === activeTab).sort(compareLabQueueRows);

  return (
    <QueueShell
      title="Test Queue"
      rows={visibleRows.map((row) => toQueueRow(row, activeTab, navigate))}
      tabs={tabs}
      activeTab={activeTab}
      onTabChange={(key) => setActiveTab(key as LabQueueTabKey)}
      isLoading={isLoading}
      emptyMessage={emptyMessageFor(activeTab)}
    />
  );
}

function emptyMessageFor(tab: LabQueueTabKey): string {
  switch (tab) {
    case 'ready':
      return 'No specimens waiting to be accepted.';
    case 'inProgress':
      return 'No specimens currently being processed.';
    case 'awaitingPayment':
      return 'No orders waiting on payment.';
    case 'emergency':
      return 'No emergency-bypass orders right now.';
    case 'overdue':
      return 'Nothing is overdue.';
  }
}

function toQueueRow(
  row: LabOrderQueueRowResponse,
  tab: LabQueueTabKey,
  navigate: ReturnType<typeof useNavigate>,
): QueueRow {
  const severity = row.urgency === 'STAT' ? 'critical' : row.urgency === 'URGENT' ? 'warning' : 'normal';

  return {
    id: row.labOrderId as string,
    isEmergency: tab === 'emergency',
    severity,
    severityLabel: row.urgency ?? undefined,
    primary: (
      <>
        {row.patientName ?? 'Unknown patient'}
        {row.hospitalNumber && <span style={{ color: 'var(--text-muted)' }}> · {row.hospitalNumber}</span>}
      </>
    ),
    secondary: (
      <>
        {row.labTestName}
        {row.accessionNo && (
          <span style={{ marginLeft: 'var(--space-2)', fontFamily: 'var(--font-mono)', fontSize: 'var(--text-xs)' }}>
            {row.accessionNo}
          </span>
        )}
      </>
    ),
    meta: formatWaitingSince(row.orderedAt),
    action: renderAction(row, tab, navigate),
  };
}

function renderAction(
  row: LabOrderQueueRowResponse,
  tab: LabQueueTabKey,
  navigate: ReturnType<typeof useNavigate>,
): React.ReactNode {
  if (tab === 'awaitingPayment') {
    return null; // §3.1: "visible but not actionable."
  }
  if (row.specimenStatus === 'IN_PROGRESS') {
    return (
      <button type="button" className="btn btn-primary" onClick={() => navigate(`/lab/result-entry/${row.labOrderId}`)}>
        Enter result
      </button>
    );
  }
  return (
    <button type="button" className="btn btn-primary" onClick={() => navigate(`/lab/accept/${row.labOrderId}`)}>
      Accept sample
    </button>
  );
}
