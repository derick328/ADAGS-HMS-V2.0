import type { PrescriptionQueueRowResponse } from '../../shared/api/prescriptions';

// The queue API returns only lines currently authorized by the billing gate.
// A partially paid order contributes its paid lines; unpaid lines appear
// after reception records their payment.
export type PharmacyQueueTabKey = 'ready' | 'emergency' | 'inpatient';

export function bucketOf(row: PrescriptionQueueRowResponse, _rows: PrescriptionQueueRowResponse[]): PharmacyQueueTabKey {
  // §4: "Emergency | Active PaymentBypass; dispensable now" — sorts and
  // buckets first, above every other consideration, same rule QueueShell's
  // compareQueueRows enforces for isEmergency rows everywhere else.
  if (row.isEmergencyBypass) return 'emergency';
  // §4: "Inpatient / ward | Deposit-backed; dispensable without prior payment."
  if (row.isInpatient) return 'inpatient';

  return 'ready';
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
