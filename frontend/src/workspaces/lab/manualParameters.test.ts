import { describe, expect, it } from 'vitest';
import { approvedParameterInsertIndex } from './manualParameters';

const catalogue = [
  { parameterId: 'WBC', sortOrder: 1 },
  { parameterId: 'HGB', sortOrder: 2 },
  { parameterId: 'PLT', sortOrder: 3 },
];

describe('manual approved parameter rows', () => {
  it('inserts a restored parameter in catalogue order', () => {
    expect(approvedParameterInsertIndex(catalogue, [{ parameterId: 'WBC' }, { parameterId: 'PLT' }], 'HGB')).toBe(1);
  });

  it('refuses duplicate or uncatalogued identities', () => {
    expect(approvedParameterInsertIndex(catalogue, [{ parameterId: 'WBC' }], 'WBC')).toBeNull();
    expect(approvedParameterInsertIndex(catalogue, [], 'UNRECOGNIZED')).toBeNull();
  });
});
