import { describe, expect, it } from 'vitest';
import { labelStride, moneyTicks, ratio } from './chartScale';

// A money axis that misbehaves misrepresents money. The cases below are the
// ones a revenue chart genuinely hits: an empty period (all zeros), a single
// enormous charge, and the degenerate inputs that would otherwise produce
// NaN geometry or an infinite tick loop.
describe('moneyTicks', () => {
  it('always starts at zero so differences are not exaggerated', () => {
    const { ticks } = moneyTicks(840_000);
    expect(ticks[0]).toBe(0);
  });

  it('rounds the top of the axis up to a readable step', () => {
    const { max, ticks } = moneyTicks(840_000);
    expect(max).toBeGreaterThanOrEqual(840_000);
    expect(ticks).toContain(max);
    expect(ticks.length).toBeGreaterThan(1);
  });

  it('produces evenly spaced ticks', () => {
    const { ticks } = moneyTicks(1_000_000);
    const steps = ticks.slice(1).map((tick, index) => tick - ticks[index]);
    expect(new Set(steps).size).toBe(1);
  });

  it('returns a safe divisor for an all-zero period instead of dividing by zero', () => {
    const { max, ticks } = moneyTicks(0);
    expect(max).toBe(1);
    expect(ticks).toEqual([0]);
  });

  it('treats a negative peak as empty rather than inverting the axis', () => {
    expect(moneyTicks(-500).max).toBe(1);
  });

  it('does not hang or produce NaN on non-finite input', () => {
    expect(moneyTicks(Number.NaN).max).toBe(1);
    expect(moneyTicks(Number.POSITIVE_INFINITY).max).toBe(1);
  });

  it('stays bounded for a single very large charge', () => {
    const { ticks } = moneyTicks(987_654_321);
    expect(ticks.length).toBeLessThanOrEqual(12);
    expect(ticks.every(Number.isFinite)).toBe(true);
  });
});

describe('ratio', () => {
  it('maps a value onto its share of the axis', () => {
    expect(ratio(50, 100)).toBe(0.5);
  });

  it('clamps above 1 so a bad total cannot overflow the plot', () => {
    expect(ratio(150, 100)).toBe(1);
  });

  it('clamps below 0 so a negative cannot draw outside the plot', () => {
    expect(ratio(-20, 100)).toBe(0);
  });

  it('returns 0 when the axis has no extent', () => {
    expect(ratio(10, 0)).toBe(0);
  });
});

describe('labelStride', () => {
  it('labels every point when they all fit', () => {
    expect(labelStride(6)).toBe(1);
  });

  it('thins labels on a dense axis', () => {
    expect(labelStride(365)).toBeGreaterThan(1);
    expect(Math.ceil(365 / labelStride(365))).toBeLessThanOrEqual(8);
  });

  it('never returns a stride of zero, which would label nothing', () => {
    expect(labelStride(0)).toBeGreaterThanOrEqual(1);
  });
});
