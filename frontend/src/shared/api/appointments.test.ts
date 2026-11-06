import { describe, expect, it } from 'vitest';
import { clinicArrivalLabel } from './appointments';

describe('clinic arrival dates in Dar es Salaam', () => {
  const scheduled = '2026-11-20T06:00:00Z';

  it('recognizes arrivals before, on and after the booked local day', () => {
    expect(clinicArrivalLabel(scheduled, new Date('2026-11-15T06:00:00Z'))).toBe('Arrived Early');
    expect(clinicArrivalLabel(scheduled, new Date('2026-11-20T20:30:00Z'))).toBe('Due Today');
    expect(clinicArrivalLabel(scheduled, new Date('2026-11-25T06:00:00Z'))).toBe('Overdue');
  });
});
