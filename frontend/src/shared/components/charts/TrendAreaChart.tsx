import { useId, useState } from 'react';
import { formatMoney, formatMoneyCompact } from '../../format/money';
import { labelStride, moneyTicks, ratio } from './chartScale';

export interface TrendPoint {
  /** ISO date of the bucket start. */
  bucket: string;
  billed: number;
  collected: number;
}

const VIEW_W = 720;
const VIEW_H = 220;
const PAD_L = 56;
const PAD_R = 12;
const PAD_T = 12;
const PAD_B = 28;

/**
 * Billed vs collected over time, as a two-series area chart.
 *
 * Hand-built SVG rather than a charting dependency: the two series are the
 * whole chart, the axis rules are in chartScale, and this keeps a hospital
 * deployment free of a transitive dependency tree for one line chart. It also
 * means both series read from the same token palette as the rest of the app.
 *
 * The value axis always starts at zero — a money axis that doesn't exaggerates
 * every movement on it.
 *
 * Accessibility: role="img" with a summary label for the shape, plus a visually
 * hidden table carrying every exact figure, so the data is reachable without
 * reading pixels. The hover readout is mirrored on keyboard focus of each point.
 */
export function TrendAreaChart({
  points,
  currencyCode,
  granularityLabel,
}: {
  points: TrendPoint[];
  currencyCode?: string | null;
  /** "Daily" / "Weekly" / "Monthly" — the server picked the buckets. */
  granularityLabel: string;
}) {
  const titleId = useId();
  const [active, setActive] = useState<number | null>(null);

  const peak = Math.max(0, ...points.flatMap((p) => [p.billed, p.collected]));
  const { max, ticks } = moneyTicks(peak);
  const plotW = VIEW_W - PAD_L - PAD_R;
  const plotH = VIEW_H - PAD_T - PAD_B;
  const x = (index: number) => PAD_L + (points.length === 1 ? plotW / 2 : (index / (points.length - 1)) * plotW);
  const y = (value: number) => PAD_T + plotH - ratio(value, max) * plotH;
  const stride = labelStride(points.length);
  const activePoint = active == null ? null : points[active];

  const line = (key: 'billed' | 'collected') =>
    points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p[key]).toFixed(1)}`).join(' ');
  const area = (key: 'billed' | 'collected') =>
    `${line(key)} L${x(points.length - 1).toFixed(1)},${(PAD_T + plotH).toFixed(1)} L${x(0).toFixed(1)},${(
      PAD_T + plotH
    ).toFixed(1)} Z`;

  return (
    <figure className="trend-chart">
      <p className="chart-legend" id={titleId}>
        <span className="chart-swatch chart-swatch--secondary" aria-hidden="true" />
        Billed
        <span className="chart-swatch chart-swatch--primary" aria-hidden="true" />
        Collected
        <span className="chart-legend-meta">{granularityLabel}</span>
      </p>

      <div className="trend-plot">
        <svg
          viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
          preserveAspectRatio="none"
          role="img"
          aria-labelledby={titleId}
          className="trend-svg"
        >
          {ticks.map((tick) => (
            <g key={tick}>
              <line x1={PAD_L} x2={VIEW_W - PAD_R} y1={y(tick)} y2={y(tick)} className="chart-gridline" />
              <text x={PAD_L - 8} y={y(tick) + 4} textAnchor="end" className="chart-axis-text">
                {formatMoneyCompact(tick, currencyCode)}
              </text>
            </g>
          ))}

          <path d={area('billed')} className="trend-area trend-area--billed" />
          <path d={area('collected')} className="trend-area trend-area--collected" />
          <path d={line('billed')} className="trend-line trend-line--billed" />
          <path d={line('collected')} className="trend-line trend-line--collected" />

          {points.map((point, index) => (
            <g key={point.bucket}>
              {index % stride === 0 || index === points.length - 1 ? (
                <text x={x(index)} y={VIEW_H - 8} textAnchor="middle" className="chart-axis-text">
                  {shortDate(point.bucket)}
                </text>
              ) : null}
              {/* One focusable hit target per bucket: the tooltip is reachable
                  by Tab, not only by hovering a 4px dot. */}
              <rect
                x={x(index) - plotW / Math.max(points.length, 1) / 2}
                y={PAD_T}
                width={Math.max(plotW / Math.max(points.length, 1), 6)}
                height={plotH}
                className="trend-hit"
                tabIndex={0}
                role="button"
                aria-label={`${longDate(point.bucket)}: billed ${formatMoney(
                  point.billed,
                  currencyCode,
                )}, collected ${formatMoney(point.collected, currencyCode)}`}
                onMouseEnter={() => setActive(index)}
                onMouseLeave={() => setActive((current) => (current === index ? null : current))}
                onFocus={() => setActive(index)}
                onBlur={() => setActive((current) => (current === index ? null : current))}
              />
              {active === index && (
                <>
                  <line x1={x(index)} x2={x(index)} y1={PAD_T} y2={PAD_T + plotH} className="trend-crosshair" />
                  <circle cx={x(index)} cy={y(point.collected)} r={4} className="trend-dot trend-dot--collected" />
                  <circle cx={x(index)} cy={y(point.billed)} r={4} className="trend-dot trend-dot--billed" />
                </>
              )}
            </g>
          ))}
        </svg>

        {activePoint && (
          <div className="chart-tooltip" role="status">
            <strong>{longDate(activePoint.bucket)}</strong>
            <span>
              Billed <span className="money tabular">{formatMoney(activePoint.billed, currencyCode)}</span>
            </span>
            <span>
              Collected <span className="money tabular">{formatMoney(activePoint.collected, currencyCode)}</span>
            </span>
          </div>
        )}
      </div>

      <table className="visually-hidden">
        <caption>Revenue billed and collected per {bucketNoun(granularityLabel)}</caption>
        <thead>
          <tr>
            <th scope="col">Period</th>
            <th scope="col">Billed</th>
            <th scope="col">Collected</th>
          </tr>
        </thead>
        <tbody>
          {points.map((point) => (
            <tr key={point.bucket}>
              <th scope="row">{longDate(point.bucket)}</th>
              <td>{formatMoney(point.billed, currencyCode)}</td>
              <td>{formatMoney(point.collected, currencyCode)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}

// "Daily" labels the series; "per day" labels the table's rows. Reusing the
// adjective gives a screen reader "per daily".
function bucketNoun(granularityLabel: string): string {
  if (granularityLabel === 'Weekly') return 'week';
  if (granularityLabel === 'Monthly') return 'month';
  return 'day';
}

// Facility-local rendering of a server-sent date (frontend/CLAUDE.md). The
// bucket is a plain calendar date, so it is parsed as local midnight rather
// than UTC — appending T00:00 avoids the Date(string) UTC-shift that would
// show every bucket a day early west of Dar es Salaam.
function shortDate(iso: string): string {
  return new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short' }).format(new Date(`${iso}T00:00`));
}

function longDate(iso: string): string {
  return new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }).format(
    new Date(`${iso}T00:00`),
  );
}
