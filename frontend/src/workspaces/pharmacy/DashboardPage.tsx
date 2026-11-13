import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchPrescriptionQueue, type PrescriptionQueueRowResponse } from '../../shared/api/prescriptions';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatWaitingSince } from '../../shared/format/date';
import { QueueShell, type QueueRow, type QueueTab } from '../../shared/components/QueueShell';
import { bucketOf, comparePharmacyQueueRows, countByBucket, type PharmacyQueueTabKey } from './pharmacyQueueBuckets';

// Only authorized prescriptions appear in the pharmacy queue.
const TAB_ORDER: { key: PharmacyQueueTabKey; label: string }[] = [
  { key: 'ready', label: 'Ready to dispense' },
  { key: 'emergency', label: 'Emergency' },
  { key: 'inpatient', label: 'Inpatient / ward' },
];

export function DashboardPage() {
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<PharmacyQueueTabKey>('ready');
  const userPickedTab = useRef(false);

  const { data, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['prescriptions', 'queue'],
    queryFn: () => fetchPrescriptionQueue(accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 5_000,
  });

  const rows = data ?? [];
  const tabs: QueueTab[] = TAB_ORDER.map(({ key, label }) => ({
    key,
    label,
    count: countByBucket(rows, key),
    urgent: key === 'emergency',
  }));

  // Auto-land on the first non-empty tab if 'ready' is empty — otherwise
  // a pharmacist opening the dashboard right after payment sees "No
  // prescriptions ready" when the row is in Emergency or Inpatient.
  // Runs only until the user picks a tab themselves, so the auto-switch
  // never yanks the view out from under them once they're navigating.
  useEffect(() => {
    if (userPickedTab.current) return;
    if (rows.length === 0) return;
    if (countByBucket(rows, activeTab) > 0) return;
    const firstNonEmpty = TAB_ORDER.find(({ key }) => countByBucket(rows, key) > 0);
    if (firstNonEmpty && firstNonEmpty.key !== activeTab) setActiveTab(firstNonEmpty.key);
  }, [rows, activeTab]);

  const visibleRows = rows.filter((row) => bucketOf(row, rows) === activeTab).sort(comparePharmacyQueueRows);

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'flex-end', padding: 'var(--space-2) var(--space-4) 0' }}>
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => refetch()}
          disabled={isFetching}
          title="Poll runs every 5s; click to fetch now"
        >
          {isFetching ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>
      <QueueShell
        title="Dashboard"
        rows={visibleRows.map((row) => toQueueRow(row, navigate))}
        tabs={tabs}
        activeTab={activeTab}
        onTabChange={(key) => {
          userPickedTab.current = true;
          setActiveTab(key as PharmacyQueueTabKey);
        }}
        isLoading={isLoading}
        emptyMessage={emptyMessageFor(activeTab)}
      />
    </>
  );
}

function emptyMessageFor(tab: PharmacyQueueTabKey): string {
  switch (tab) {
    case 'ready':
      return 'No prescriptions ready to dispense.';
    case 'emergency':
      return 'No emergency-bypass prescriptions right now.';
    case 'inpatient':
      return 'No inpatient prescriptions waiting.';
  }
}

function toQueueRow(
  row: PrescriptionQueueRowResponse,
  navigate: ReturnType<typeof useNavigate>,
): QueueRow {
  const remaining = row.remaining ?? 0;

  return {
    id: row.prescriptionLineId as string,
    isEmergency: Boolean(row.isEmergencyBypass),
    primary: (
      <>
        {row.patientName ?? 'Unknown patient'}
        {row.hospitalNumber && <span style={{ color: 'var(--text-muted)' }}> · {row.hospitalNumber}</span>}
      </>
    ),
    secondary: (
      <>
        {row.itemName} — {remaining} {remaining === 1 ? 'unit' : 'units'} remaining
        {row.dosageInstructions && (
          <span style={{ marginLeft: 'var(--space-2)', color: 'var(--text-muted)' }}>{row.dosageInstructions}</span>
        )}
      </>
    ),
    meta: formatWaitingSince(row.prescribedAt),
    action: renderAction(row, navigate),
  };
}

function renderAction(
  row: PrescriptionQueueRowResponse,
  navigate: ReturnType<typeof useNavigate>,
): React.ReactNode {
  return (
    <button type="button" className="btn btn-primary" onClick={() => navigate(`/pharmacy/dispense/${row.prescriptionLineId}`)}>
      Dispense
    </button>
  );
}
