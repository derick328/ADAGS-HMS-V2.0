/** Preview only. The server repeats conversion and cost validation at confirmation. */
export function calculateDeliveryLine(quantity: number, factor: number, unitCostMinor: number) {
  if (![quantity, factor, unitCostMinor].every(Number.isSafeInteger) ||
      quantity < 0 || factor < 1 || unitCostMinor < 0) {
    throw new RangeError('Invalid package quantity, conversion, or cost');
  }
  const baseQuantity = quantity * factor;
  const totalCostMinor = quantity * unitCostMinor;
  if (!Number.isSafeInteger(baseQuantity) || !Number.isSafeInteger(totalCostMinor)) {
    throw new RangeError('Converted quantity or cost exceeds the supported range');
  }
  return { baseQuantity, totalCostMinor };
}
