import { decimalQuantity } from '../../shared/quantity';
import type { CurrentStockResponse } from '../../shared/api/stockReports';
import { isAutomaticForm } from '../doctor/prescriptionQuantity';

type PackageGroup = { unit: string; factor: number; quantity: number };

function packageName(unit: string, count: number): string {
  const kind = unit.split('_')[0]?.toLowerCase() ?? 'package';
  const name = ['bottle', 'vial', 'ampoule', 'tube', 'pack'].includes(kind) ? kind : 'package';
  return `${count} ${name}${count === 1 ? '' : 's'}`;
}

/** Stock balances remain in base units; only the current-stock total is presented in purchase packages. */
export function stockTotalDisplay(rows: CurrentStockResponse[]): string {
  if (rows.length === 0) return '0';
  const baseUnit = rows[0].baseUnit ?? 'units';
  const totalBase = rows.reduce((sum, row) => (decimalQuantity(sum + (row.quantity ?? 0)) ?? sum + (row.quantity ?? 0)), 0);
  if (isAutomaticForm(rows[0].form)) return `${totalBase} ${baseUnit}`;

  const groups = new Map<string, PackageGroup>();
  let unpackagedBase = 0;
  for (const row of rows) {
    const quantity = row.quantity ?? 0;
    const factor = row.packageFactor;
    if (!row.purchaseUnit || !factor || factor <= 0 || row.purchaseUnit.toUpperCase() === baseUnit.toUpperCase()) {
      unpackagedBase = decimalQuantity(unpackagedBase + quantity) ?? unpackagedBase + quantity;
      continue;
    }
    const key = `${row.purchaseUnit}|${factor}`;
    const group = groups.get(key) ?? { unit: row.purchaseUnit, factor, quantity: 0 };
    group.quantity = decimalQuantity(group.quantity + quantity) ?? group.quantity + quantity;
    groups.set(key, group);
  }
  const parts = [...groups.values()].flatMap((group) => {
    const whole = Math.floor(decimalQuantity(group.quantity / group.factor) ?? group.quantity / group.factor);
    const remainder = decimalQuantity(group.quantity - whole * group.factor) ?? group.quantity - whole * group.factor;
    return [whole ? packageName(group.unit, whole) : '', remainder ? `${remainder} ${baseUnit}` : ''].filter(Boolean);
  });
  if (unpackagedBase) parts.push(`${unpackagedBase} ${baseUnit}`);
  return parts.length ? parts.join(' + ') : `0 ${baseUnit}`;
}
