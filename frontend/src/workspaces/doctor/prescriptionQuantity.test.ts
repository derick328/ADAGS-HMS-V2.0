import { describe, expect, it } from 'vitest';
import { automaticSupply, baseUnitForForm, dosageForm, dosageFormLabel, isAutomaticForm, isLiquidOralForm } from './prescriptionQuantity';

describe('prescription supply policy', () => {
  it('calculates whole solid-dose supplies for regular schedules and STAT', () => {
    expect(automaticSupply(1, 'OD', 5)).toBe(5);
    expect(automaticSupply(2, 'BD', 3)).toBe(12);
    expect(automaticSupply(1, 'TDS', 7)).toBe(21);
    expect(automaticSupply(2, 'QDS', 5)).toBe(40);
    expect(automaticSupply(2, 'STAT', 0)).toBe(2);
  });

  it('does not invent a supply for PRN or invalid doses', () => {
    expect(automaticSupply(1, 'PRN', 3)).toBeNull();
    expect(automaticSupply(1, 'OTHER', 7)).toBeNull();
    expect(automaticSupply(0.5, 'BD', 3)).toBeNull();
    expect(automaticSupply(1, 'OD', 0)).toBeNull();
  });

  it('recognizes catalogue forms without treating manual forms as automatic', () => {
    expect(dosageForm(' Capsule ')).toBe('CAPSULE');
    expect(isAutomaticForm('caplet')).toBe(true);
    expect(isAutomaticForm('syrup')).toBe(false);
    expect(isAutomaticForm('injection')).toBe(false);
  });

  it('keeps tablet and suspension presentations distinct with the right supply policy', () => {
    expect(dosageForm('Tablets')).toBe('TABLETS');
    expect(dosageForm('Oral Tablets')).toBe('ORAL_TABLETS');
    expect(dosageForm('Suspension')).toBe('SUSPENSION');
    expect(dosageForm('Oral Suspension')).toBe('ORAL_SUSPENSION');
    expect(dosageFormLabel('ORAL_TABLETS')).toBe('Oral Tablets');
    expect(baseUnitForForm('ORAL_TABLETS')).toBe('TABLET');
    expect(isAutomaticForm('Oral Tablets')).toBe(true);
    expect(isLiquidOralForm('Oral Suspension')).toBe(true);
    expect(isAutomaticForm('Suspension')).toBe(false);
  });
});
