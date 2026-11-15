import { decimalQuantity } from '../../shared/quantity';
/** Preview only. The server repeats conversion and cost validation at confirmation. */
export function calculateDeliveryLine(quantity: number, factor: number, unitCostMinor: number | null) {
  if (!Number.isSafeInteger(quantity) || decimalQuantity(factor) == null ||
      quantity < 0 || factor <= 0 || unitCostMinor !== null && (!Number.isSafeInteger(unitCostMinor) || unitCostMinor < 0)) {
    throw new RangeError('Invalid package quantity, conversion, or cost');
  }
  const baseQuantity = decimalQuantity(quantity * factor);
  const totalCostMinor = unitCostMinor === null ? null : quantity * unitCostMinor;
  if (baseQuantity == null || totalCostMinor !== null && !Number.isSafeInteger(totalCostMinor)) {
    throw new RangeError('Converted quantity or cost exceeds the supported range');
  }
  return { baseQuantity, totalCostMinor };
}
