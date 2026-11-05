import type { ReactNode } from 'react';
import { AlertTriangle, BarChart3 } from 'lucide-react';

/**
 * The shell every analytics panel on /admin and /admin/revenue shares: compact
 * header, an explicit basis line, and one of four states — loading, error,
 * genuinely-empty, or content.
 *
 * `basis` is not decoration. Every figure on these screens is either a period
 * flow or a point-in-time balance, and either billed or collected; a chart that
 * doesn't say which is a chart an admin can misread into a decision. Callers
 * must pass it.
 */
export function ChartCard({
  title,
  basis,
  action,
  isLoading,
  isError,
  onRetry,
  isEmpty,
  emptyMessage,
  children,
  span = 1,
}: {
  title: string;
  basis: string;
  action?: ReactNode;
  isLoading?: boolean;
  isError?: boolean;
  onRetry?: () => void;
  isEmpty?: boolean;
  emptyMessage?: string;
  children: ReactNode;
  span?: 1 | 2;
}) {
  return (
    <section className={`analytics-card${span === 2 ? ' analytics-card--wide' : ''}`}>
      <header className="analytics-card-head">
        <div>
          <h3>{title}</h3>
          <p className="analytics-basis">{basis}</p>
        </div>
        {action}
      </header>
      {isError ? (
        <div className="analytics-state" role="alert">
          <AlertTriangle size={18} aria-hidden="true" />
          <p>This chart could not be loaded.</p>
          {onRetry && (
            <button type="button" className="btn btn-secondary" onClick={onRetry}>
              Retry
            </button>
          )}
        </div>
      ) : isLoading ? (
        <div className="analytics-state" role="status">
          <span className="analytics-skeleton" aria-hidden="true" />
          <p>Loading…</p>
        </div>
      ) : isEmpty ? (
        // A real empty state, never a sample chart: no revenue in the period
        // is a finding an admin needs to see as itself.
        <div className="analytics-state" role="status">
          <BarChart3 size={18} aria-hidden="true" />
          <p>{emptyMessage ?? 'No activity in this period.'}</p>
        </div>
      ) : (
        children
      )}
    </section>
  );
}
