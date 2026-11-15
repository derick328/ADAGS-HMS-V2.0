import { describe, expect, it } from 'vitest';
import { stockTotalDisplay } from './stockDisplay';
import type { CurrentStockResponse } from '../../shared/api/stockReports';

const row = (overrides: Partial<CurrentStockResponse>): CurrentStockResponse => ({
  itemId: 'medicine-1', quantity: 0, baseUnit: 'mL', form: 'SYRUP',
  purchaseUnit: 'BOTTLE_100', packageFactor: 100, ...overrides,
});

describe('current stock totals', () => {
  it('counts liquid stock in bottles while preserving a partial bottle in base units', () => {
    expect(stockTotalDisplay([row({ quantity: 200 }), row({ quantity: 50 })])).toBe('2 bottles + 50 mL');
  });

  it('keeps tablet stock in base units and does not invent bottles for old batches', () => {
    expect(stockTotalDisplay([row({ quantity: 100, form: 'ORAL_TABLETS', baseUnit: 'TABLET' })])).toBe('100 TABLET');
    expect(stockTotalDisplay([row({ quantity: 75, purchaseUnit: undefined, packageFactor: undefined })])).toBe('75 mL');
  });

  it('uses the actual package unit for non-solid stock kept in vials', () => {
    expect(stockTotalDisplay([row({ quantity: 30, form: 'INJECTION', baseUnit: 'mL',
      purchaseUnit: 'VIAL_10', packageFactor: 10 })])).toBe('3 vials');
  });
});
