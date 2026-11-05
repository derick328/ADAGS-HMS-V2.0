import { useId, useState } from 'react';
import { formatMoney, formatMoneyCompact } from '../../format/money';
import { moneyTicks, ratio } from './chartScale';

export interface CategoryBar {
  id: string;
  label: string;
  /** Rendered as the solid, foreground bar. */
  primary: number;
  /** Rendered behind it, lighter — the comparison series. */
  secondary: number;
}

const VIEW_W = 720;
const VIEW_H = 240;
const PAD_L = 56;
const PAD_R = 12;
const PAD_T = 12;
const PAD_B = 46;

/**
 * Revenue by service category — two series per category (billed behind,
 * collected in front) so the gap between what was charged and what was
 * actually received is the visible feature rather than something the reader
 * has to compute across two charts.
 *
 * Same conventions as TrendAreaChart: zero-based money axis, hand-built SVG on
 * the shared token palette, hover readout mirrored on keyboard focus, and a
 * visually hidden table carrying the exact figures.
 */
export function CategoryBarChart({
  bars,
  currencyCode,
  primaryLabel,
  secondaryLabel,
}: {
  bars: CategoryBar[];
  currencyCode?: string | null;
  primaryLabel: string;
  secondaryLabel: string;
}) {
  const titleId = useId();
  const [active, setActive] = useState<number | null>(null);

  const peak = Math.max(0, ...bars.flatMap((b) => [b.primary, b.secondary]));
  const { max, ticks } = moneyTicks(peak);
  const plotW = VIEW_W - PAD_L - PAD_R;
  const plotH = VIEW_H - PAD_T - PAD_B;
  const slot = plotW / Math.max(bars.length, 1);
  const barWidth = Math.min(44, slot * 0.56);
  const y = (value: number) => PAD_T + plotH - ratio(value, max) * plotH;
  const activeBar = active == null ? null : bars[active];

  return (
    <figure className="category-chart">
      <p className="chart-legend" id={titleId}>
        <span className="chart-swatch chart-swatch--secondary" aria-hidden="true" />
        {secondaryLabel}
        <span className="chart-swatch chart-swatch--primary" aria-hidden="true" />
        {primaryLabel}
      </p>

      <div className="category-plot">
        <svg viewBox={`0 0 ${VIEW_W} ${VIEW_H}`} preserveAspectRatio="none" role="img" aria-labelledby={titleId}>
          {ticks.map((tick) => (
            <g key={tick}>
              <line x1={PAD_L} x2={VIEW_W - PAD_R} y1={y(tick)} y2={y(tick)} className="chart-gridline" />
              <text x={PAD_L - 8} y={y(tick) + 4} textAnchor="end" className="chart-axis-text">
                {formatMoneyCompact(tick, currencyCode)}
              </text>
            </g>
          ))}

          {bars.map((bar, index) => {
            const centre = PAD_L + slot * index + slot / 2;
            const baseline = PAD_T + plotH;
            return (
              <g
                key={bar.id}
                tabIndex={0}
                role="button"
                aria-label={`${bar.label}: ${secondaryLabel} ${formatMoney(
                  bar.secondary,
                  currencyCode,
                )}, ${primaryLabel} ${formatMoney(bar.primary, currencyCode)}`}
                className="category-group"
                onMouseEnter={() => setActive(index)}
                onMouseLeave={() => setActive((current) => (current === index ? null : current))}
                onFocus={() => setActive(index)}
                onBlur={() => setActive((current) => (current === index ? null : current))}
              >
                <rect
                  x={centre - barWidth / 2}
                  y={y(bar.secondary)}
                  width={barWidth}
                  height={Math.max(baseline - y(bar.secondary), bar.secondary > 0 ? 2 : 0)}
                  className="category-bar category-bar--secondary"
                />
                <rect
                  x={centre - barWidth / 2 + barWidth * 0.22}
                  y={y(bar.primary)}
                  width={barWidth * 0.56}
                  height={Math.max(baseline - y(bar.primary), bar.primary > 0 ? 2 : 0)}
                  className="category-bar category-bar--primary"
                />
                <text x={centre} y={VIEW_H - 26} textAnchor="middle" className="chart-axis-text">
                  {firstWord(bar.label)}
                </text>
                <text x={centre} y={VIEW_H - 12} textAnchor="middle" className="chart-axis-text chart-axis-text--sub">
                  {restWords(bar.label)}
                </text>
              </g>
            );
          })}
        </svg>

        {activeBar && (
          <div className="chart-tooltip" role="status">
            <strong>{activeBar.label}</strong>
            <span>
              {secondaryLabel} <span className="money tabular">{formatMoney(activeBar.secondary, currencyCode)}</span>
            </span>
            <span>
              {primaryLabel} <span className="money tabular">{formatMoney(activeBar.primary, currencyCode)}</span>
            </span>
          </div>
        )}
      </div>

      <table className="visually-hidden">
        <caption>Revenue by service category</caption>
        <thead>
          <tr>
            <th scope="col">Category</th>
            <th scope="col">{secondaryLabel}</th>
            <th scope="col">{primaryLabel}</th>
          </tr>
        </thead>
        <tbody>
          {bars.map((bar) => (
            <tr key={bar.id}>
              <th scope="row">{bar.label}</th>
              <td>{formatMoney(bar.secondary, currencyCode)}</td>
              <td>{formatMoney(bar.primary, currencyCode)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}

// Category names are two or three words ("Theatre procedures", "Pharmacy /
// medication"); splitting them across two axis lines keeps them readable at
// seven bars wide without rotating the labels.
function firstWord(label: string): string {
  return label.split(' ')[0];
}

function restWords(label: string): string {
  return label.split(' ').slice(1).join(' ');
}
