import type { ReactNode } from 'react';

/**
 * The queue shell. Used by the triage queue, doctor queue, lab worklist,
 * pharmacy dispense queue, ward patient list, and expected returns — roughly
 * twelve screens across six roles.
 *
 * It exists as one component because the clinical safety rules must not be
 * re-decided per screen:
 *
 *  - EMERGENCY rows sort first, always, above every other consideration.
 *  - Severity is shown by icon AND position AND a left bar — never colour
 *    alone. A colour-only cue on a critical patient fails for colour-blind
 *    staff and on a sunlit ward tablet.
 *  - An empty queue says what that means, not "no data".
 *  - Every count badge is clickable. A number a user cannot act on is a dead end.
 *
 * Queues are server state. Pass rows from TanStack Query and let the WebSocket
 * invalidate — a patient who pays at reception must appear on the doctor's
 * queue without a refresh.
 */

export type Severity = 'critical' | 'warning' | 'normal';

export interface QueueRow {
  id: string;
  /** Sorts above everything else, regardless of severity or waiting time. */
  isEmergency?: boolean;
  severity?: Severity;
  /** Short label for the severity, e.g. "IMMEDIATE", "URGENT". Screen readers
   *  and colour-blind users depend on this, not the colour. */
  severityLabel?: string;
  primary: ReactNode;
  secondary?: ReactNode;
  /** Right-aligned metadata: waiting time, turnaround, amount owed. */
  meta?: ReactNode;
  action?: ReactNode;
  /** Rendered inline, prominently. Allergies and critical flags go here —
   *  never in a collapsed panel. */
  alert?: ReactNode;
}

export interface QueueTab {
  key: string;
  label: string;
  count: number;
  /** Draws attention when the count is non-zero — unacknowledged critical
   *  results, overdue doses. */
  urgent?: boolean;
}

interface QueueShellProps {
  title: string;
  rows: QueueRow[];
  tabs?: QueueTab[];
  activeTab?: string;
  onTabChange?: (key: string) => void;
  /** Shown when rows is empty. Say what an empty queue means in this context:
   *  "No patients waiting for triage", not "No results". */
  emptyMessage: string;
  isLoading?: boolean;
  /** Rendered above the table — filters, a search box, a date range. */
  toolbar?: ReactNode;
}

const SEVERITY_CLASS: Record<Severity, string> = {
  critical: 'row-critical',
  warning: 'row-warning',
  normal: '',
};

export function QueueShell({
  title,
  rows,
  tabs,
  activeTab,
  onTabChange,
  emptyMessage,
  isLoading,
  toolbar,
}: QueueShellProps) {
  return (
    <section aria-labelledby="queue-title">
      <header
        style={{
          display: 'flex',
          alignItems: 'baseline',
          gap: 'var(--space-3)',
          marginBottom: 'var(--space-4)',
        }}
      >
        <h1
          id="queue-title"
          style={{
            margin: 0,
            fontSize: 'var(--text-xl)',
            fontWeight: 'var(--weight-medium)',
          }}
        >
          {title}
        </h1>
        <span style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
          {rows.length} {rows.length === 1 ? 'patient' : 'patients'}
        </span>
      </header>

      {tabs && tabs.length > 0 && (
        <nav
          aria-label="Queue filters"
          style={{
            display: 'flex',
            gap: 'var(--space-1)',
            borderBottom: '1px solid var(--border)',
            marginBottom: 'var(--space-4)',
          }}
        >
          {tabs.map((tab) => {
            const isActive = tab.key === activeTab;
            return (
              <button
                key={tab.key}
                onClick={() => onTabChange?.(tab.key)}
                aria-current={isActive ? 'page' : undefined}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 'var(--space-2)',
                  padding: 'var(--space-2) var(--space-3)',
                  border: 'none',
                  background: 'none',
                  font: 'inherit',
                  cursor: 'pointer',
                  color: isActive ? 'var(--text-primary)' : 'var(--text-muted)',
                  fontWeight: isActive
                    ? 'var(--weight-medium)'
                    : 'var(--weight-normal)',
                  // The signal green marks the active tab. Same role it plays
                  // in the sidebar — consistency is what makes it read as brand.
                  boxShadow: isActive
                    ? 'inset 0 -2px 0 var(--green-signal)'
                    : 'none',
                }}
              >
                {tab.label}
                {tab.count > 0 && (
                  <span
                    className={
                      tab.urgent ? 'badge badge-critical' : 'badge badge-neutral'
                    }
                  >
                    {tab.count}
                  </span>
                )}
              </button>
            );
          })}
        </nav>
      )}

      {toolbar && <div style={{ marginBottom: 'var(--space-3)' }}>{toolbar}</div>}

      <div
        style={{
          background: 'var(--bg-surface)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-lg)',
          overflow: 'hidden',
        }}
      >
        {isLoading ? (
          <QueueEmpty message="Loading…" />
        ) : rows.length === 0 ? (
          <QueueEmpty message={emptyMessage} />
        ) : (
          <table className="table">
            <caption className="sr-only">{title}</caption>
            <tbody>
              {rows.map((row) => (
                <tr
                  key={row.id}
                  className={
                    row.isEmergency
                      ? 'row-critical'
                      : SEVERITY_CLASS[row.severity ?? 'normal']
                  }
                >
                  <td style={{ width: 32, paddingRight: 0 }}>
                    {/* Icon carries the meaning, not the row colour. */}
                    {row.isEmergency ? (
                      <span
                        aria-label="Emergency"
                        title="Emergency"
                        style={{ color: 'var(--critical)', fontSize: 16 }}
                      >
                        ⬤
                      </span>
                    ) : row.severity === 'critical' ? (
                      <span
                        aria-label={row.severityLabel ?? 'Critical'}
                        style={{ color: 'var(--critical)', fontSize: 16 }}
                      >
                        ▲
                      </span>
                    ) : row.severity === 'warning' ? (
                      <span
                        aria-label={row.severityLabel ?? 'Urgent'}
                        style={{ color: 'var(--warning)', fontSize: 16 }}
                      >
                        ▲
                      </span>
                    ) : null}
                  </td>

                  <td>
                    <div style={{ fontWeight: 'var(--weight-medium)' }}>
                      {row.primary}
                    </div>
                    {row.secondary && (
                      <div
                        style={{
                          color: 'var(--text-muted)',
                          fontSize: 'var(--text-sm)',
                          marginTop: 2,
                        }}
                      >
                        {row.secondary}
                      </div>
                    )}
                    {/* Alerts render inline and unmissable. Allergies on a
                        prescribing queue must never be behind a click. */}
                    {row.alert && (
                      <div
                        style={{
                          marginTop: 'var(--space-2)',
                          padding: '2px var(--space-2)',
                          background: 'var(--critical-bg)',
                          color: 'var(--critical)',
                          borderRadius: 'var(--radius-sm)',
                          fontSize: 'var(--text-sm)',
                          display: 'inline-block',
                        }}
                      >
                        {row.alert}
                      </div>
                    )}
                  </td>

                  <td
                    className="tabular"
                    style={{
                      textAlign: 'right',
                      color: 'var(--text-secondary)',
                      fontSize: 'var(--text-sm)',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {row.meta}
                  </td>

                  <td style={{ width: 1, whiteSpace: 'nowrap' }}>{row.action}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}

function QueueEmpty({ message }: { message: string }) {
  return (
    <div
      style={{
        padding: 'var(--space-10) var(--space-4)',
        textAlign: 'center',
        color: 'var(--text-muted)',
      }}
    >
      {message}
    </div>
  );
}

/**
 * Sort comparator every queue must use. Emergency first, then clinical
 * severity, then the queue's own ordering (usually waiting time).
 *
 * Kept here rather than in each screen so no queue can accidentally place a
 * booked appointment or a paid patient above an emergency.
 */
export function compareQueueRows<T extends { isEmergency?: boolean; severityRank?: number; tiebreak?: number }>(
  a: T,
  b: T,
): number {
  if (!!a.isEmergency !== !!b.isEmergency) return a.isEmergency ? -1 : 1;
  const sa = a.severityRank ?? 99;
  const sb = b.severityRank ?? 99;
  if (sa !== sb) return sa - sb;
  return (a.tiebreak ?? 0) - (b.tiebreak ?? 0);
}
