import { describe, expect, it } from 'vitest';
import { hasFbpPeripheralSmearParameters } from './ResultEntryPage';

describe('result entry parameter availability', () => {
  it('allows the requested FBP with peripheral smear test', () => {
    expect(hasFbpPeripheralSmearParameters('Full Blood Picture (FBP) / + Peripheral Smear')).toBe(true);
    expect(hasFbpPeripheralSmearParameters('  full blood picture (fbp)  / + peripheral smear  ')).toBe(true);
  });

  it('keeps parameters unavailable for other lab requests', () => {
    expect(hasFbpPeripheralSmearParameters('Full Blood Picture (FBP)')).toBe(false);
    expect(hasFbpPeripheralSmearParameters('Peripheral Smear')).toBe(false);
    expect(hasFbpPeripheralSmearParameters('Full Blood Count')).toBe(false);
    expect(hasFbpPeripheralSmearParameters(undefined)).toBe(false);
  });
});
