import { afterEach, describe, expect, it, vi } from 'vitest';
import { granularityLabel, resolveRange, toLocalIsoDate } from './dateRange';

// Date filtering decides which money appears on a financial report, so these
// tests pin the two things that silently corrupt it: a UTC conversion that
// shifts the local calendar day, and a preset whose bounds are off by one.
//
// The clock is frozen at 01:30 local deliberately. Dar es Salaam is UTC+3, so
// toISOString() at that moment reports the PREVIOUS day — the exact bug that
// would make "Today" query yesterday.
const EARLY_MORNING = new Date(2026, 9, 6, 1, 30, 0); // 6 Oct 2026, 01:30 local
const NO_CUSTOM = { from: '', to: '' };

function freezeClock(at: Date) {
  vi.useFakeTimers();
  vi.setSystemTime(at);
}

afterEach(() => {
  vi.useRealTimers();
});

describe('toLocalIsoDate', () => {
  it('uses the local calendar date, not the UTC one', () => {
    expect(toLocalIsoDate(EARLY_MORNING)).toBe('2026-10-06');
  });

  it('zero-pads month and day', () => {
    expect(toLocalIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
  });

  it('does not drift at the last instant of a month', () => {
    expect(toLocalIsoDate(new Date(2026, 8, 30, 23, 59, 59))).toBe('2026-09-30');
  });
});

describe('resolveRange', () => {
  it('TODAY is a single day, both bounds the local date', () => {
    freezeClock(EARLY_MORNING);
    expect(resolveRange('TODAY', NO_CUSTOM)).toEqual({ from: '2026-10-06', to: '2026-10-06' });
  });

  it('LAST_7 spans seven days inclusive, not eight', () => {
    freezeClock(EARLY_MORNING);
    expect(resolveRange('LAST_7', NO_CUSTOM)).toEqual({ from: '2026-09-30', to: '2026-10-06' });
  });

  it('MTD starts on the first of the current month', () => {
    freezeClock(EARLY_MORNING);
    expect(resolveRange('MTD', NO_CUSTOM)).toEqual({ from: '2026-10-01', to: '2026-10-06' });
  });

  it('LAST_30 spans thirty days inclusive', () => {
    freezeClock(EARLY_MORNING);
    expect(resolveRange('LAST_30', NO_CUSTOM)).toEqual({ from: '2026-09-07', to: '2026-10-06' });
  });

  it('YTD starts on 1 January of the current year', () => {
    freezeClock(EARLY_MORNING);
    expect(resolveRange('YTD', NO_CUSTOM)).toEqual({ from: '2026-01-01', to: '2026-10-06' });
  });

  it('CUSTOM passes the caller range through untouched', () => {
    freezeClock(EARLY_MORNING);
    const custom = { from: '2026-03-01', to: '2026-03-31' };
    expect(resolveRange('CUSTOM', custom)).toEqual(custom);
  });

  it('crosses a month boundary backwards correctly', () => {
    freezeClock(new Date(2026, 9, 2, 12, 0, 0)); // 2 Oct
    expect(resolveRange('LAST_7', NO_CUSTOM).from).toBe('2026-09-26');
  });

  it('crosses a year boundary backwards correctly', () => {
    freezeClock(new Date(2026, 0, 3, 12, 0, 0)); // 3 Jan
    expect(resolveRange('LAST_30', NO_CUSTOM).from).toBe('2025-12-05');
    expect(resolveRange('YTD', NO_CUSTOM).from).toBe('2026-01-01');
  });

  it('handles a leap day without shifting', () => {
    freezeClock(new Date(2028, 1, 29, 10, 0, 0)); // 29 Feb 2028
    expect(resolveRange('TODAY', NO_CUSTOM)).toEqual({ from: '2028-02-29', to: '2028-02-29' });
  });
});

// Must mirror TrendGranularity.forSpan on the server, which is what actually
// chooses the buckets. A label that disagrees with the data is a lie on the axis.
describe('granularityLabel', () => {
  it('calls a month-or-shorter span daily', () => {
    expect(granularityLabel({ from: '2026-10-01', to: '2026-10-06' })).toBe('Daily');
    expect(granularityLabel({ from: '2026-09-05', to: '2026-10-06' })).toBe('Daily');
  });

  it('calls a span over 31 days weekly', () => {
    expect(granularityLabel({ from: '2026-08-01', to: '2026-10-06' })).toBe('Weekly');
  });

  it('calls a span over 182 days monthly', () => {
    expect(granularityLabel({ from: '2026-01-01', to: '2026-10-06' })).toBe('Monthly');
  });

  it('matches the server at the exact 31-day and 182-day boundaries', () => {
    expect(granularityLabel({ from: '2026-01-01', to: '2026-02-01' })).toBe('Daily'); // 31 days
    expect(granularityLabel({ from: '2026-01-01', to: '2026-02-02' })).toBe('Weekly'); // 32 days
    expect(granularityLabel({ from: '2026-01-01', to: '2026-07-02' })).toBe('Weekly'); // 182 days
    expect(granularityLabel({ from: '2026-01-01', to: '2026-07-03' })).toBe('Monthly'); // 183 days
  });
});
