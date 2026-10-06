import type { ReactNode } from 'react';
import { ArrowUpRight } from 'lucide-react';
import { Link } from 'react-router-dom';

/** A real operational metric. Callers own its query, formatting, and permissions. */
export function MetricCard({ label, value, hint, to, tone = 'neutral' }: {
  label: string; value: ReactNode; hint?: ReactNode; to?: string; tone?: 'neutral' | 'warning';
}) {
  const body = <><div className="metric-label">{label}{to && <ArrowUpRight size={18} aria-hidden="true" />}</div>
    <div className="metric-value tabular">{value}</div>{hint && <div className="metric-hint">{hint}</div>}</>;
  const className = `metric-card metric-card--${tone}`;
  return to ? <Link to={to} className={className}>{body}</Link> : <div className={className}>{body}</div>;
}
