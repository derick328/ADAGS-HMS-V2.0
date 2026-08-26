import type { LabOrderQueueRowResponse } from '../../shared/api/labOrders';

// 15-lab-technician.md §3.1: the five sub-tabs, as a single bucketing
// function so the sidebar's "ready to process" badge and the Test Queue's
// own tab counts can never disagree (frontend/CLAUDE.md: "every count
// badge is clickable and leads to the filtered list" — the list it leads
// to must be counted the same way).
export type LabQueueTabKey = 'ready' | 'inProgress' | 'awaitingPayment' | 'emergency' | 'overdue';

export function bucketOf(row: LabOrderQueueRowResponse): LabQueueTabKey {
  // §3.1: "Emergency | Active PaymentBypass — processable now, charges
  // accrue (L3)." Sorts and buckets first, above every other consideration —
  // same rule QueueShell's compareQueueRows enforces for isEmergency rows
  // everywhere else in this codebase.
  if (row.isEmergencyBypass) return 'emergency';
  // §3.3: past expected turnaround, still not resulted.
  if (row.overdue) return 'overdue';
  // §L1/§L2: gate not satisfied — visible, not actionable.
  if (!row.gateSatisfied) return 'awaitingPayment';
  // §4.3: a REJECTED specimen needs recollection — same "ready to accept"
  // bucket as an order with no specimen yet, never re-charged (the line is
  // already durably authorized).
  if (row.specimenStatus == null || row.specimenStatus === 'REJECTED') return 'ready';
  return 'inProgress';
}

export function countByBucket(rows: LabOrderQueueRowResponse[], bucket: LabQueueTabKey): number {
  return rows.filter((row) => bucketOf(row) === bucket).length;
}

// §3.2: "ORDER BY (encounter.type = 'EMERGENCY') DESC, urgency ASC,
// gate_satisfied_at ASC." Emergency rows already have their own tab, but
// within a tab the same urgency-then-time ordering applies — STAT before
// URGENT before ROUTINE, then oldest order first.
const URGENCY_RANK: Record<string, number> = { STAT: 0, URGENT: 1, ROUTINE: 2 };

export function compareLabQueueRows(a: LabOrderQueueRowResponse, b: LabOrderQueueRowResponse): number {
  const urgencyDiff = (URGENCY_RANK[a.urgency ?? 'ROUTINE'] ?? 9) - (URGENCY_RANK[b.urgency ?? 'ROUTINE'] ?? 9);
  if (urgencyDiff !== 0) return urgencyDiff;
  return new Date(a.orderedAt ?? 0).getTime() - new Date(b.orderedAt ?? 0).getTime();
}
