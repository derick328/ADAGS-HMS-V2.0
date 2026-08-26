import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchPrescriptionQueue, type PrescriptionQueueRowResponse } from '../../shared/api/prescriptions';
import { useAuth } from '../../shared/auth/AuthProvider';
import { formatWaitingSince } from '../../shared/format/date';
import { QueueShell, type QueueRow, type QueueTab } from '../../shared/components/QueueShell';
import { bucketOf, comparePharmacyQueueRows, countByBucket, type PharmacyQueueTabKey } from './pharmacyQueueBuckets';

// 12-pharmacist.md §4: "Action queues only, no data entry." Five tabs over
// one flat queue response (surgery items deferred with the surgery
// module, no row exists for them here).
const TAB_ORDER: { key: PharmacyQueueTabKey; label: string }[] = [
  { key: 'ready', label: 'Ready to dispense' },
  { key: 'partiallyPayable', label: 'Partially payable' },
  { key: 'awaitingPayment', label: 'Awaiting payment' },
  { key: 'emergency', label: 'Emergency' },
  { key: 'inpatient', label: 'Inpatient / ward' },
];

export function DashboardPage() {
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<PharmacyQueueTabKey>('ready');

  const { data, isLoading } = useQuery({
    queryKey: ['prescriptions', 'queue'],
    queryFn: () => fetchPrescriptionQueue(accessToken as string),
    enabled: accessToken != null,
    refetchInterval: 10_000,
  });

  const rows = data ?? [];
  const tabs: QueueTab[] = TAB_ORDER.map(({ key, label }) => ({
    key,
    label,
    count: countByBucket(rows, key),
    urgent: key === 'emergency',
  }));

  const visibleRows = rows.filter((row) => bucketOf(row, rows) === activeTab).sort(comparePharmacyQueueRows);

  return (
    <QueueShell
      title="Dashboard"
      rows={visibleRows.map((row) => toQueueRow(row, activeTab, navigate))}
      tabs={tabs}
      activeTab={activeTab}
      onTabChange={(key) => setActiveTab(key as PharmacyQueueTabKey)}
      isLoading={isLoading}
      emptyMessage={emptyMessageFor(activeTab)}
    />
  );
}

function emptyMessageFor(tab: PharmacyQueueTabKey): string {
  switch (tab) {
    case 'ready':
      return 'No prescriptions ready to dispense.';
    case 'partiallyPayable':
      return 'No partially-paid prescriptions right now.';
    case 'awaitingPayment':
      return 'No prescriptions waiting on payment.';
    case 'emergency':
      return 'No emergency-bypass prescriptions right now.';
    case 'inpatient':
      return 'No inpatient prescriptions waiting.';
  }
}

function toQueueRow(
  row: PrescriptionQueueRowResponse,
  tab: PharmacyQueueTabKey,
  navigate: ReturnType<typeof useNavigate>,
): QueueRow {
  const remaining = row.remaining ?? 0;

  return {
    id: row.prescriptionLineId as string,
    isEmergency: tab === 'emergency',
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
    action: renderAction(row, tab, navigate),
  };
}

function renderAction(
  row: PrescriptionQueueRowResponse,
  tab: PharmacyQueueTabKey,
  navigate: ReturnType<typeof useNavigate>,
): React.ReactNode {
  if (tab === 'awaitingPayment' || (tab === 'partiallyPayable' && !row.gateSatisfied)) {
    return null; // §4: visible, not actionable — line-level gating not yet satisfied.
  }
  return (
    <button type="button" className="btn btn-primary" onClick={() => navigate(`/pharmacy/dispense/${row.prescriptionLineId}`)}>
      Dispense
    </button>
  );
}
