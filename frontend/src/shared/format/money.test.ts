import { describe, expect, it } from 'vitest';
import { formatMoney, formatMoneyCompact } from './money';

// Money arrives as a minor-unit integer and must never be divided by hand in
// a component. These tests pin the Tanzanian convention (12,000/=) and the
// compact axis form, including the cases a money axis actually hits: zero,
// the exact threshold between forms, and negatives.
describe('formatMoney', () => {
  it('formats TZS with thousands separators and the local suffix', () => {
    expect(formatMoney(1_250_000, 'TZS')).toBe('1,250,000/=');
  });

  it('treats a missing currency as TZS', () => {
    expect(formatMoney(12_000, null)).toBe('12,000/=');
    expect(formatMoney(12_000, undefined)).toBe('12,000/=');
  });

  it('renders a null or undefined amount as zero rather than NaN', () => {
    expect(formatMoney(null, 'TZS')).toBe('0/=');
    expect(formatMoney(undefined, 'TZS')).toBe('0/=');
  });

  it('appends a non-TZS currency code instead of the slash-equals', () => {
    expect(formatMoney(5_000, 'USD')).toBe('5,000 USD');
  });

  it('keeps a negative amount negative', () => {
    expect(formatMoney(-4_500, 'TZS')).toBe('-4,500/=');
  });
});

describe('formatMoneyCompact', () => {
  it('leaves small amounts exact — they already fit an axis', () => {
    expect(formatMoneyCompact(0, 'TZS')).toBe('0/=');
    expect(formatMoneyCompact(9_999, 'TZS')).toBe('9,999/=');
  });

  it('switches to thousands at exactly 10,000', () => {
    expect(formatMoneyCompact(10_000, 'TZS')).toBe('10k/=');
    expect(formatMoneyCompact(450_000, 'TZS')).toBe('450k/=');
  });

  it('switches to millions and billions at their thresholds', () => {
    expect(formatMoneyCompact(1_000_000, 'TZS')).toBe('1M/=');
    expect(formatMoneyCompact(1_250_000, 'TZS')).toBe('1.3M/=');
    expect(formatMoneyCompact(2_000_000_000, 'TZS')).toBe('2B/=');
  });

  it('drops a trailing .0 rather than printing "1.0M"', () => {
    expect(formatMoneyCompact(1_000_000, 'TZS')).not.toContain('.0');
  });

  it('compacts negatives by magnitude, keeping the sign', () => {
    expect(formatMoneyCompact(-2_500_000, 'TZS')).toBe('-2.5M/=');
    expect(formatMoneyCompact(-15_000, 'TZS')).toBe('-15k/=');
  });

  it('carries a non-TZS code through the compact form', () => {
    expect(formatMoneyCompact(2_000_000, 'USD')).toBe('2M USD');
  });
});
