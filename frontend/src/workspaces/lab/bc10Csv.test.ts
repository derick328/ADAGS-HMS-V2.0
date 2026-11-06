import { describe, expect, it } from 'vitest';
import { parseBc10Csv } from './bc10Csv';

describe('BC-10 header preview', () => {
  it('recognizes supplied columns and keeps source identity and numeric values', () => {
    const result = parseBc10Csv('Patient ID,Draw Date,Draw Time,Background,WBC,HGB\nH-1,06/11/2026,12:30,WB,6.5,12.3');
    expect(result.supported).toBe(true);
    if (result.supported) {
      expect(result.preview.patientId).toBe('H-1');
      expect(result.preview.background).toBe('WB');
      expect(result.preview.measurements.map(m => m.value)).toEqual([6.5, 12.3]);
      expect(result.preview.measurements.every(m => m.unit === null)).toBe(true);
    }
  });

  it('marks invalid measurements without inventing a numeric value', () => {
    const result = parseBc10Csv('Patient ID,Draw Date,Draw Time,Background,WBC\nH-1,06/11/2026,12:30,WB,not-a-number');
    expect(result.supported).toBe(true);
    if (result.supported) expect(result.preview.measurements[0].value).toBeNull();
  });

  it('rejects unknown, duplicate and oversized files', () => {
    expect(parseBc10Csv('Parameter,Value,Unit\nHGB,12.3,g/dL')).toMatchObject({ supported: false });
    expect(parseBc10Csv('Patient ID,Patient ID,WBC\nH-1,H-1,5')).toMatchObject({ supported: false });
    expect(parseBc10Csv('x'.repeat(2_000_001))).toMatchObject({ supported: false });
  });
});
