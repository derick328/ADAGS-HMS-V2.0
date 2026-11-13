import { useQuery } from '@tanstack/react-query';
import { BedDouble, Check, Inbox, PillBottle, Siren } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchPrescriptionQueue, type PrescriptionQueueRowResponse } from '../../shared/api/prescriptions';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { Badge } from '../../shared/components/Badge';
import type { BadgeVariant } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { formatWaitingSince } from '../../shared/format/date';
import { QueueShell, type QueueFilter } from '../../shared/shells/QueueShell';
import { bucketOf, type PharmacyQueueTabKey } from './pharmacyQueueBuckets';

// 12-pharmacist.md §4. One patient per row (the flat /queue response is one
// row per prescription *line* — grouped here by patientId, same "server
// returns the data, client owns the shape" pattern as the Dashboard's tab
// split). The row's priority badge and the priority filter are the existing
// emergency / inpatient / payable category from bucketOf — prescriptions
// carry no triage priority. Dispensing itself stays on its dedicated page
// (/pharmacy/dispense/:prescriptionLineId): batch/FEFO selection, the inline
// allergy record, and the idempotency key all live there.

interface PatientGroup {
  patientId: string;
  patientName: string;
  hospitalNumber?: string;
  wardName?: string;
  lines: PrescriptionQueueRowResponse[];
  category: PharmacyQueueTabKey;
  waitingSince?: string;
}

// Most urgent category first, then oldest prescription first — the order a
// pharmacist works the queue in.
const CATEGORY_RANK: Record<PharmacyQueueTabKey, number> = {
  emergency: 0,
  inpatient: 1,
  ready: 2,
};

const CATEGORY_META: Record<
  PharmacyQueueTabKey,
  { label: string; variant: BadgeVariant; icon: LucideIcon }
> = {
  emergency: { label: 'Emergency', variant: 'danger', icon: Siren },
  inpatient: { label: 'Inpatient', variant: 'info', icon: BedDouble },
  ready: { label: 'Ready', variant: 'success', icon: Check },
};

const CATEGORY_FILTER_OPTIONS = (Object.keys(CATEGORY_RANK) as PharmacyQueueTabKey[]).map((key) => ({
  value: key,
  label: CATEGORY_META[key].label,
}));

function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

function groupByPatient(rows: PrescriptionQueueRowResponse[]): PatientGroup[] {
  const byPatient = new Map<string, PatientGroup>();

  for (const row of rows) {
    const patientId = row.patientId;
    if (!patientId) continue;

    const category = bucketOf(row, rows);
    let group = byPatient.get(patientId);
    if (!group) {
      group = {
        patientId,
        patientName: row.patientName ?? 'Unknown patient',
        hospitalNumber: row.hospitalNumber,
        wardName: row.wardName ?? undefined,
        lines: [],
        category,
        waitingSince: row.prescribedAt,
      };
      byPatient.set(patientId, group);
    }

    group.lines.push(row);
    if (CATEGORY_RANK[category] < CATEGORY_RANK[group.category]) group.category = category;
    if (!group.wardName && row.wardName) group.wardName = row.wardName;
    if (row.prescribedAt && (!group.waitingSince || row.prescribedAt < group.waitingSince)) {
      group.waitingSince = row.prescribedAt;
    }
  }

  return [...byPatient.values()].sort((a, b) => {
    const byCategory = CATEGORY_RANK[a.category] - CATEGORY_RANK[b.category];
    if (byCategory !== 0) return byCategory;
    return new Date(a.waitingSince ?? 0).getTime() - new Date(b.waitingSince ?? 0).getTime();
  });
}

function distinct(values: (string | undefined | null)[]): string[] {
  return [...new Set(values.filter((v): v is string => Boolean(v)))].sort();
}

export function DispensingQueue() {
  const { accessToken } = useAuth();
  const navigate = useNavigate();
  const canRead = usePermission('PRESCRIPTION_QUEUE_READ');

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [filters, setFilters] = useState<Record<string, string>>({});

  const { data, isLoading, isError, refetch, isRefetching } = useQuery({
    queryKey: ['prescriptions', 'queue'],
    queryFn: () => fetchPrescriptionQueue(accessToken as string),
    enabled: canRead && accessToken != null,
    refetchInterval: 10_000,
  });

  const rows = useMemo(() => data ?? [], [data]);
  const groups = useMemo(() => groupByPatient(rows), [rows]);

  const wardOptions = useMemo(
    () => distinct(rows.map((r) => r.wardName)).map((w) => ({ value: w, label: w })),
    [rows],
  );
  const medicationTypeOptions = useMemo(
    () => distinct(rows.map((r) => r.itemClassification)).map((c) => ({ value: c, label: titleCase(c) })),
    [rows],
  );

  const visibleGroups = useMemo(
    () =>
      groups.filter((g) => {
        if (filters.ward && g.wardName !== filters.ward) return false;
        if (filters.category && g.category !== filters.category) return false;
        if (filters.medicationType && !g.lines.some((l) => l.itemClassification === filters.medicationType)) {
          return false;
        }
        return true;
      }),
    [groups, filters],
  );

  const emergencyCount = groups.filter((g) => g.category === 'emergency').length;

  const filterConfig: QueueFilter[] = [
    { id: 'ward', type: 'select', label: 'Ward', placeholder: 'All wards', options: wardOptions },
    { id: 'category', type: 'select', label: 'Priority', placeholder: 'All priorities', options: CATEGORY_FILTER_OPTIONS },
    {
      id: 'medicationType',
      type: 'select',
      label: 'Medication type',
      placeholder: 'All medication types',
      options: medicationTypeOptions,
    },
  ];

  const status = !canRead || isError ? 'error' : isLoading ? 'loading' : 'idle';

  return (
    <QueueShell<PatientGroup>
      title="Dispensing queue"
      counts={
        <>
          <Badge size="sm">
            {visibleGroups.length} {visibleGroups.length === 1 ? 'patient' : 'patients'}
          </Badge>
          {emergencyCount > 0 && (
            <Badge size="sm" variant="danger" leadingIcon={Siren}>
              {emergencyCount} emergency
            </Badge>
          )}
        </>
      }
      onRefresh={() => refetch()}
      refreshing={isRefetching}
      items={visibleGroups}
      getRowId={(g) => g.patientId}
      renderRow={(g) => {
        const meta = CATEGORY_META[g.category];
        const count = g.lines.length;
        return {
          primary: g.patientName,
          secondary: [
            `${count} ${count === 1 ? 'prescription' : 'prescriptions'}`,
            g.hospitalNumber,
            g.wardName,
          ]
            .filter(Boolean)
            .join(' · '),
          trailing: (
            <>
              <Badge size="sm" variant={meta.variant} leadingIcon={meta.icon}>
                {meta.label}
              </Badge>
              {formatWaitingSince(g.waitingSince)}
            </>
          ),
        };
      }}
      detailTitle={(g) => g.patientName}
      renderDetail={(g) =>
        g.lines.map((line) => {
          const dispensable =
            g.category === 'emergency' || g.category === 'inpatient' || Boolean(line.gateSatisfied);
          const unit = line.itemForm ? line.itemForm.toLowerCase() : 'unit';
          const detail = [
            line.dosageInstructions,
            line.itemForm && titleCase(line.itemForm),
            `${line.remaining ?? 0} of ${line.quantityPrescribed ?? 0} ${unit}s remaining`,
          ]
            .filter(Boolean)
            .join(' · ');
          return (
            <Card
              key={line.prescriptionLineId}
              variant="outlined"
              header={line.itemName ?? 'Unknown item'}
              trailingAction={
                <Badge size="sm" variant={dispensable ? 'neutral' : 'warning'}>
                  {dispensable ? 'Ready' : 'Awaiting payment'}
                </Badge>
              }
              footer={
                <Button
                  size="sm"
                  variant={dispensable ? 'primary' : 'secondary'}
                  disabled={!dispensable}
                  leadingIcon={dispensable ? PillBottle : undefined}
                  onClick={() => navigate(`/pharmacy/dispense/${line.prescriptionLineId}`)}
                >
                  {dispensable ? 'Dispense' : 'Awaiting payment'}
                </Button>
              }
            >
              {detail}
            </Card>
          );
        })
      }
      selectedId={selectedId}
      onSelectChange={setSelectedId}
      filters={filterConfig}
      filterValues={filters}
      onFilterChange={setFilters}
      status={status}
      errorTitle={canRead ? "Couldn't load the dispensing queue" : 'You do not have access to the dispensing queue'}
      errorDescription={
        canRead ? 'Check your connection and try again.' : 'Ask an administrator for the pharmacy dispensing role.'
      }
      onRetry={canRead ? () => refetch() : undefined}
      emptyIcon={Inbox}
      emptyTitle="No prescriptions waiting to be dispensed"
      emptyDescription="Prescriptions appear here once their payment or coverage is confirmed."
    />
  );
}
