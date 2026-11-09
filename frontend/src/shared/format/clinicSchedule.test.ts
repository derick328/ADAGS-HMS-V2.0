import { describe, expect, it } from 'vitest';
import { clinicDateTimeLimits, scheduledClinicInstant } from './clinicSchedule';

describe('clinic visit date and time', () => {
  it('uses the next available facility-local minute, including at midnight', () => {
    expect(clinicDateTimeLimits(new Date('2026-11-09T20:59:30Z'))).toEqual({ date: '2026-11-10', time: '00:00' });
  });

  it('rejects earlier times today and accepts a later time', () => {
    const now = new Date('2026-11-09T10:30:20Z');
    expect(() => scheduledClinicInstant('2026-11-09', '13:30', now)).toThrow('Choose a future clinic visit date and time.');
    expect(scheduledClinicInstant('2026-11-09', '13:31', now)).toBe('2026-11-09T10:31:00.000Z');
  });

  it('rejects a previous calendar day', () => {
    expect(() => scheduledClinicInstant('2026-11-08', '23:59', new Date('2026-11-09T00:00:00Z'))).toThrow();
  });
});
