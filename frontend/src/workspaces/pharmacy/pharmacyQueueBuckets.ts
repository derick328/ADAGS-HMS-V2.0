import type { PrescriptionQueueRowResponse } from '../../shared/api/prescriptions';

// 12-pharmacist.md §4: five dashboard queues over one flat queue response
// (surgery items are deferred with the surgery module and carry no row
// here). "Ready to dispense" / "partially payable" / "awaiting payment"
// are properties of the *order*, not the line — gating itself stays
// per-line (root CLAUDE.md "Billing": "gating is per invoice line, never
// per invoice"), so this groups rows by prescriptionOrderId to tell the
// three apart, same "server returns the data, client owns the tab split"
// pattern as labQueueBuckets.ts.
export type PharmacyQueueTabKey = 'ready' | 'partiallyPayable' | 'awaitingPayment' | 'emergency' | 'inpatient';

export function bucketOf(row: PrescriptionQueueRowResponse, rows: PrescriptionQueueRowResponse[]): PharmacyQueueTabKey {
  // §4: "Emergency | Active PaymentBypass; dispensable now" — sorts and
  // buckets first, above every other consideration, same rule QueueShell's
  // compareQueueRows enforces for isEmergency rows everywhere else.
  if (row.isEmergencyBypass) return 'emergency';
  // §4: "Inpatient / ward | Deposit-backed; dispensable without prior payment."
  if (row.isInpatient) return 'inpatient';

  const orderLines = rows.filter((r) => r.prescriptionOrderId === row.prescriptionOrderId);
  const satisfiedCount = orderLines.filter((r) => r.gateSatisfied).length;
  if (satisfiedCount === orderLines.length) return 'ready';
  if (satisfiedCount === 0) return 'awaitingPayment';
  return 'partiallyPayable';
}

export function countByBucket(rows: PrescriptionQueueRowResponse[], bucket: PharmacyQueueTabKey): number {
  return rows.filter((row) => bucketOf(row, rows) === bucket).length;
}

// Oldest-prescribed-first within a tab — a patient who has been waiting
// longer should surface first, same tiebreak TestQueuePage applies after
// its own urgency sort.
export function comparePharmacyQueueRows(a: PrescriptionQueueRowResponse, b: PrescriptionQueueRowResponse): number {
  return new Date(a.prescribedAt ?? 0).getTime() - new Date(b.prescribedAt ?? 0).getTime();
}
