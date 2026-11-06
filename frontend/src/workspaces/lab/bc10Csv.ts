/** Header-level adapter based on the BC-10 columns supplied by the user. */
export const BC10_MEASUREMENT_CODES = [
  'WBC', 'Lymph#', 'Mid#', 'Gran#', 'Lymph%', 'Mid%', 'Gran%',
  'RBC', 'HGB', 'HCT', 'MCV', 'MCH', 'MCHC', 'RDW-CV', 'RDW-SD',
  'PLT', 'MPV', 'PDW', 'PCT', 'P-LCR',
] as const;

export interface AnalyserMeasurement {
  sourceCode: string;
  rawValue: string;
  value: number | null;
  unit: string | null;
  sourceRow: number;
}

export interface Bc10Preview {
  format: 'BC-10 header profile';
  patientId: string;
  drawDate: string;
  drawTime: string;
  background: string;
  measurements: AnalyserMeasurement[];
  unmatchedColumns: string[];
}

export type CsvImportResult =
  | { supported: false; reason: string }
  | { supported: true; preview: Bc10Preview };

function parseCsvRows(text: string): string[][] | null {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = '';
  let quoted = false;
  for (let index = 0; index < text.length; index++) {
    const char = text[index];
    if (char === '"') {
      if (quoted && text[index + 1] === '"') { cell += '"'; index++; }
      else if (quoted || cell === '') quoted = !quoted;
      else return null;
    } else if (char === ',' && !quoted) { row.push(cell); cell = ''; }
    else if ((char === '\n' || char === '\r') && !quoted) {
      if (char === '\r' && text[index + 1] === '\n') index++;
      row.push(cell); cell = '';
      if (row.some(value => value !== '')) rows.push(row);
      row = [];
    } else cell += char;
  }
  if (quoted) return null;
  row.push(cell);
  if (row.some(value => value !== '')) rows.push(row);
  return rows;
}

const requiredMetadata = ['Patient ID', 'Draw Date', 'Draw Time', 'Background'];
const normalize = (value: string) => value.trim().toUpperCase();

export function parseBc10Csv(text: string): CsvImportResult {
  if (text.length > 2_000_000) return { supported: false, reason: 'CSV exceeds the 2 MB import limit.' };
  const rows = parseCsvRows(text.replace(/^\uFEFF/, ''));
  if (!rows || rows.length < 2) return { supported: false, reason: 'CSV is empty or malformed.' };
  const headers = rows[0].map(value => value.trim());
  if (new Set(headers.map(normalize)).size !== headers.length)
    return { supported: false, reason: 'CSV contains duplicate column names.' };
  const byHeader = new Map(headers.map((header, index) => [normalize(header), index]));
  if (requiredMetadata.some(name => !byHeader.has(normalize(name))) ||
      !BC10_MEASUREMENT_CODES.some(code => byHeader.has(normalize(code))))
    return { supported: false, reason: 'Unsupported BC-10 CSV headers.' };
  if (rows.length !== 2 || rows[1].length !== headers.length)
    return { supported: false, reason: 'Select an export with exactly one complete patient row.' };
  const get = (name: string) => rows[1][byHeader.get(normalize(name)) as number]?.trim() ?? '';
  const known = new Set([...requiredMetadata, ...BC10_MEASUREMENT_CODES].map(normalize));
  const measurements = BC10_MEASUREMENT_CODES.filter(code => byHeader.has(normalize(code))).map(code => {
    const rawValue = get(code);
    const value = /^(?:\d+\.?\d*|\.\d+)$/.test(rawValue) ? Number(rawValue) : null;
    return { sourceCode: code, rawValue, value: value != null && Number.isFinite(value) ? value : null,
      unit: null, sourceRow: 2 };
  });
  return { supported: true, preview: {
    format: 'BC-10 header profile', patientId: get('Patient ID'), drawDate: get('Draw Date'),
    drawTime: get('Draw Time'), background: get('Background'), measurements,
    unmatchedColumns: headers.filter(header => !known.has(normalize(header))),
  } };
}
