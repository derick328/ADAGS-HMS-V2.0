// Shared axis maths for the hand-built SVG charts in this folder. Kept out
// of the components so the tick logic is testable on its own and identical
// across every chart — an axis that rounds differently per chart makes two
// charts of the same data look like two different datasets.

/**
 * A "nice" upper bound and evenly spaced ticks for a value axis that always
 * starts at zero (money charts must: a truncated money axis exaggerates
 * every difference on it).
 *
 * Returns max 1 for an all-zero series so callers can divide by it safely
 * and render a flat baseline instead of dividing by zero.
 */
export function moneyTicks(maxValue: number, tickCount = 4): { max: number; ticks: number[] } {
  if (!Number.isFinite(maxValue) || maxValue <= 0) {
    return { max: 1, ticks: [0] };
  }
  const rawStep = maxValue / tickCount;
  const magnitude = 10 ** Math.floor(Math.log10(rawStep));
  const normalized = rawStep / magnitude;
  const niceStep = (normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10) * magnitude;
  const max = Math.ceil(maxValue / niceStep) * niceStep;
  const ticks: number[] = [];
  for (let value = 0; value <= max + niceStep / 2; value += niceStep) {
    ticks.push(Math.round(value));
  }
  return { max, ticks };
}

/** Fraction of the axis a value occupies, clamped so a bad total can't overflow the plot. */
export function ratio(value: number, max: number): number {
  if (max <= 0) return 0;
  return Math.min(1, Math.max(0, value / max));
}

/**
 * Every nth label, chosen so a dense axis stays readable instead of
 * overlapping into illegibility. Always keeps the first and last.
 */
export function labelStride(pointCount: number, maxLabels = 8): number {
  if (pointCount <= maxLabels) return 1;
  return Math.ceil(pointCount / maxLabels);
}
