import { describe, expect, it } from 'vitest';
import { formatTatDuration, formatTatTimestamp, tatElapsedSeconds, tatLocalDate } from './tat';

describe('turnaround formatting', () => {
  it('keeps missing durations distinct from zero', () => {
    expect(formatTatDuration(null)).toBe('Not recorded');
    expect(formatTatDuration(0)).toBe('0 min');
    expect(formatTatDuration(3_900)).toBe('1 h 05 min');
  });

  it('uses the facility timezone for dates and timestamps', () => {
    const instant = new Date('2026-11-05T22:30:00Z');
    expect(tatLocalDate(instant, 'Africa/Dar_es_Salaam')).toBe('2026-11-06');
    expect(tatLocalDate(instant, 'UTC')).toBe('2026-11-05');
    expect(formatTatTimestamp(instant.toISOString(), 'Africa/Dar_es_Salaam')).toContain('6 Nov 2026');
  });

  it('omits missing and out-of-order intervals', () => {
    expect(tatElapsedSeconds('2026-11-05T08:00:00Z', '2026-11-05T08:12:00Z')).toBe(720);
    expect(tatElapsedSeconds('2026-11-05T08:12:00Z', '2026-11-05T08:00:00Z')).toBeNull();
    expect(tatElapsedSeconds(null, '2026-11-05T08:00:00Z')).toBeNull();
  });
});
