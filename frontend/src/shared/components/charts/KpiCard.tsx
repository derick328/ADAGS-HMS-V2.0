import type { ReactNode } from 'react';
import { ArrowUpRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';

/**
 * A compact financial KPI — deliberately smaller than MetricCard, which is
 * sized for a two-or-three-tile operational dashboard and becomes oversized at
 * five across. `basis` carries the one-line definition (period flow vs.
 * point-in-time, billed vs. collected) that keeps the number honest.
 *
 * `to` is only accepted where a real detail view exists; a tile that navigates
 * nowhere should not look clickable.
 */
export function KpiCard({
  label,
  value,
  basis,
  icon: Icon,
  tone = 'neutral',
  to,
}: {
  label: string;
  value: ReactNode;
  basis?: string;
  icon?: LucideIcon;
  tone?: 'neutral' | 'positive' | 'warning';
  to?: string;
}) {
  const body = (
    <>
      <span className="kpi-head">
        {Icon && (
          <span className="kpi-icon" aria-hidden="true">
            <Icon size={15} />
          </span>
        )}
        <span className="kpi-label">{label}</span>
        {to && <ArrowUpRight size={14} aria-hidden="true" className="kpi-arrow" />}
      </span>
      {/* tabular, not .money: that token right-aligns for table columns,
          which leaves a card value floating away from its own label. Same
          choice MetricCard makes for the same reason. */}
      <span className="kpi-value tabular">{value}</span>
      {basis && <span className="kpi-basis">{basis}</span>}
    </>
  );
  const className = `kpi-card kpi-card--${tone}`;
  return to ? (
    <Link to={to} className={className}>
      {body}
    </Link>
  ) : (
    <div className={className}>{body}</div>
  );
}
