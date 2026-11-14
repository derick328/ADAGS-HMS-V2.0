import { describe, expect, it } from 'vitest';
import type { PrescriptionQueueRowResponse } from '../../shared/api/prescriptions';
import { bucketOf, countByBucket } from './pharmacyQueueBuckets';

describe('pharmacy payment buckets', () => {
  it('shows unpaid outpatient lines while keeping paid lines ready', () => {
    const paid = { gateSatisfied: true, isEmergencyBypass: false, isInpatient: false } as PrescriptionQueueRowResponse;
    const unpaid = { ...paid, gateSatisfied: false };
    const rows = [paid, unpaid];

    expect(bucketOf(unpaid, rows)).toBe('unpaid');
    expect(countByBucket(rows, 'unpaid')).toBe(1);
    expect(countByBucket(rows, 'ready')).toBe(1);
  });

  it('keeps emergency and inpatient rows in their priority buckets', () => {
    const emergency = { gateSatisfied: true, isEmergencyBypass: true, isInpatient: false } as PrescriptionQueueRowResponse;
    const inpatient = { gateSatisfied: true, isEmergencyBypass: false, isInpatient: true } as PrescriptionQueueRowResponse;

    expect(bucketOf(emergency, [emergency])).toBe('emergency');
    expect(bucketOf(inpatient, [inpatient])).toBe('inpatient');
  });
});
