import { formatMoney, formatMoneyCompact } from '../../format/money';
import { ratio } from './chartScale';

export interface RankedBarRow {
  id: string;
  label: string;
  sublabel?: string;
  /** The bar's length. */
  value: number;
  /** Shown beside the bar as context — a quantity, a count of tests. */
  meta?: string;
  /** Optional second figure rendered as a lighter inset bar behind `value`. */
  secondaryValue?: number;
}

/**
 * Horizontal ranked bars — top medicines, top lab tests, revenue by service.
 * Rendered as a list of CSS bars rather than SVG: the labels are long clinical
 * names that need real text wrapping and selectable text, which a foreignObject
 * inside an SVG does badly.
 *
 * Accessibility: the bars are presentational, and the actual figures are in the
 * adjacent text of every row, so a screen reader reads name → amount → meta
 * without needing the geometry. Colour is never the only carrier — each series
 * is labelled in the legend and the exact amounts are written out.
 */
export function RankedBarChart({
  rows,
  currencyCode,
  valueLabel,
  secondaryLabel,
  onSelect,
}: {
  rows: RankedBarRow[];
  currencyCode?: string | null;
  valueLabel: string;
  secondaryLabel?: string;
  onSelect?: (id: string) => void;
}) {
  const max = Math.max(1, ...rows.map((r) => Math.max(r.value, r.secondaryValue ?? 0)));
  // Through `ratio`, not raw arithmetic: a refund-heavy row can carry a
  // negative value, and a negative CSS width is silently dropped by the
  // browser, leaving a bar that reads as "no data" rather than as zero.
  const widthOf = (value: number) => `${ratio(value, max) * 100}%`;
  return (
    <div className="ranked-chart">
      {secondaryLabel && (
        <p className="chart-legend">
          <span className="chart-swatch chart-swatch--primary" aria-hidden="true" />
          {valueLabel}
          <span className="chart-swatch chart-swatch--secondary" aria-hidden="true" />
          {secondaryLabel}
        </p>
      )}
      <ol className="ranked-list">
        {rows.map((row, index) => (
          <li key={row.id} className="ranked-row">
            <span className="ranked-rank" aria-hidden="true">
              {index + 1}
            </span>
            <div className="ranked-body">
              <div className="ranked-head">
                <span className="ranked-label">
                  {onSelect ? (
                    <button type="button" className="link-button" onClick={() => onSelect(row.id)}>
                      {row.label}
                    </button>
                  ) : (
                    row.label
                  )}
                  {row.sublabel && <small>{row.sublabel}</small>}
                </span>
                <span className="ranked-value money tabular" title={formatMoney(row.value, currencyCode)}>
                  {formatMoneyCompact(row.value, currencyCode)}
                </span>
              </div>
              <div className="ranked-track">
                {row.secondaryValue != null && (
                  <span
                    className="ranked-fill ranked-fill--secondary"
                    style={{ width: widthOf(row.secondaryValue) }}
                  />
                )}
                <span className="ranked-fill" style={{ width: widthOf(row.value) }} />
              </div>
              {row.meta && <p className="ranked-meta">{row.meta}</p>}
            </div>
          </li>
        ))}
      </ol>
    </div>
  );
}
