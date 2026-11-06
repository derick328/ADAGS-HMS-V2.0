export interface ApprovedParameter {
  parameterId?: string;
  sortOrder?: number;
}

/** Returns the catalogue position for a new approved row, or null when unavailable or already selected. */
export function approvedParameterInsertIndex(
  catalogue: readonly ApprovedParameter[],
  selected: readonly { parameterId: string }[],
  parameterId: string,
): number | null {
  const definition = catalogue.find(parameter => parameter.parameterId === parameterId);
  if (!definition || selected.some(row => row.parameterId === parameterId)) return null;
  const order = definition.sortOrder ?? Number.MAX_SAFE_INTEGER;
  const index = selected.findIndex(row => {
    const other = catalogue.find(parameter => parameter.parameterId === row.parameterId);
    return (other?.sortOrder ?? Number.MAX_SAFE_INTEGER) > order;
  });
  return index < 0 ? selected.length : index;
}
