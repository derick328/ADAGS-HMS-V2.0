import { RANGE_PRESETS, toLocalIsoDate, type DateRange, type RangePreset } from './dateRange';

/** Period chips for the financial reports. Range maths lives in dateRange.ts. */
export function RangeFilter({
  preset,
  custom,
  onPresetChange,
  onCustomChange,
}: {
  preset: RangePreset;
  custom: DateRange;
  onPresetChange: (preset: RangePreset) => void;
  onCustomChange: (range: DateRange) => void;
}) {
  const today = toLocalIsoDate(new Date());
  return (
    <div className="range-filter" role="group" aria-label="Reporting period">
      {RANGE_PRESETS.map((option) => (
        <button
          key={option.key}
          type="button"
          className="range-chip"
          aria-pressed={preset === option.key}
          onClick={() => onPresetChange(option.key)}
        >
          {option.label}
        </button>
      ))}
      <button
        type="button"
        className="range-chip"
        aria-pressed={preset === 'CUSTOM'}
        onClick={() => onPresetChange('CUSTOM')}
      >
        Custom
      </button>
      {preset === 'CUSTOM' && (
        <div className="range-custom">
          <label>
            From
            <input
              type="date"
              value={custom.from}
              max={custom.to || today}
              onChange={(event) => onCustomChange({ ...custom, from: event.target.value })}
            />
          </label>
          <label>
            To
            <input
              type="date"
              value={custom.to}
              min={custom.from}
              max={today}
              onChange={(event) => onCustomChange({ ...custom, to: event.target.value })}
            />
          </label>
        </div>
      )}
    </div>
  );
}
