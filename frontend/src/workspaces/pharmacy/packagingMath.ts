/** Preview only. The server repeats conversion and cost validation at confirmation. */
export function calculateDeliveryLine(quantity: number, factor: number, unitCostMinor: number | null) {
  if (![quantity, factor].every(Number.isSafeInteger) ||
      quantity < 0 || factor < 1 || unitCostMinor !== null && (!Number.isSafeInteger(unitCostMinor) || unitCostMinor < 0)) {
    throw new RangeError('Invalid package quantity, conversion, or cost');
  }
  const baseQuantity = quantity * factor;
  const totalCostMinor = unitCostMinor === null ? null : quantity * unitCostMinor;
  if (!Number.isSafeInteger(baseQuantity) || totalCostMinor !== null && !Number.isSafeInteger(totalCostMinor)) {
    throw new RangeError('Converted quantity or cost exceeds the supported range');
  }
  return { baseQuantity, totalCostMinor };
}
