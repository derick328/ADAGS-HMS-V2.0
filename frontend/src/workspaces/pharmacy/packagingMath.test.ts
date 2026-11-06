import { describe, expect, it } from 'vitest';
import { calculateDeliveryLine } from './packagingMath';

describe('medicine delivery preview', () => {
  it('converts 10 three-tablet packs and preserves the purchase total', () => {
    expect(calculateDeliveryLine(10, 3, 3000)).toEqual({ baseQuantity: 30, totalCostMinor: 30000 });
  });

  it('converts four boxes without rounding their purchase cost', () => {
    expect(calculateDeliveryLine(4, 100, 12000)).toEqual({ baseQuantity: 400, totalCostMinor: 48000 });
  });

  it('rejects unsafe integer multiplication before showing a misleading preview', () => {
    expect(() => calculateDeliveryLine(Number.MAX_SAFE_INTEGER, 3, 1)).toThrow(RangeError);
  });
});
