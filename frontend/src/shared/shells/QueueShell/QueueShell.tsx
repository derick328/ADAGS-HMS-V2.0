import { AlertTriangle, Inbox, RefreshCw, Search } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { KeyboardEvent, ReactNode } from 'react';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import type { EmptyStateAction } from '../../components/EmptyState';
import { Input } from '../../components/Input';
import { Modal } from '../../components/Modal';
import { Select } from '../../components/Select';
import { Skeleton } from '../../components/Skeleton';
import styles from './QueueShell.module.css';

/**
 * QueueShell — the layout for a screen where a user works through a live list:
 * triage queue, pharmacy dispensing queue, lab worklist. Header (title +
 * counts + refresh) · filter bar · a scrollable list of actionable rows · a
 * detail pane that opens beside the list on desktop and as a full-screen
 * `Modal` on mobile.
 *
 * Composition only — it draws nothing new. Rows, badges, buttons, empty and
 * loading states are all the existing primitives; the module CSS is layout
 * (grid / flex / scroll / the 900px breakpoint) over spacing tokens plus the
 * hairline surface separators and row states already defined in tokens.css.
 *
 * Generic over the row type `T`. Selection is controlled (`selectedId` +
 * `onSelectChange`) or uncontrolled (`defaultSelectedId`); same for the filter
 * bar. The row list is one tab stop — ArrowUp / ArrowDown / Home / End move
 * focus between rows, Enter / Space opens the focused row (native to the row
 * `<button>`), and the selected row carries `aria-current`.
 *
 * Because each row is a single `<button>`, secondary per-row actions belong in
 * the detail pane, not inside the row.
 *
 * `renderRow` returns row *slots* — `{ primary, secondary?, trailing? }` — and
 * the shell lays them out (primary + secondary stacked left, `trailing`
 * right-aligned and wrapping). Callers never hand-roll row layout.
 * `renderDetail` returns free content; the shell spaces its top-level children
 * and wraps it identically in the desktop pane and the mobile `Modal`.
 *
 * @example Minimal
 * ```tsx
 * <QueueShell<LabOrder>
 *   title="Lab worklist"
 *   items={orders}
 *   getRowId={(o) => o.id}
 *   renderRow={(o) => ({ primary: o.patientName, secondary: o.testName, trailing: <Badge>{o.priority}</Badge> })}
 *   renderDetail={(o) => <LabDetail order={o} />}
 *   emptyTitle="No samples waiting"
 * />
 * ```
 *
 * @example With counts, refresh, filters and status wired to a query
 * ```tsx
 * <QueueShell<Rx>
 *   title="Dispensing queue"
 *   counts={<Badge size="sm">{rows.length} to dispense</Badge>}
 *   onRefresh={() => refetch()}
 *   refreshing={isRefetching}
 *   items={rows}
 *   getRowId={(r) => r.lineId}
 *   renderRow={(r) => ({
 *     primary: r.patientName,
 *     secondary: `${r.lineCount} prescriptions`,
 *     trailing: <><Badge variant="danger">{r.priority}</Badge> {r.waiting}</>,
 *   })}
 *   renderDetail={(r) => <RxDetail rx={r} />}
 *   detailTitle={(r) => r.patientName}
 *   selectedId={selectedId}
 *   onSelectChange={setSelectedId}
 *   filters={[{ id: 'ward', type: 'select', label: 'Ward', placeholder: 'All wards', options: wardOptions }]}
 *   filterValues={filters}
 *   onFilterChange={setFilters}
 *   status={queryStatus}
 *   onRetry={() => refetch()}
 * />
 * ```
 *
 * @example Uncontrolled selection, custom empty action
 * ```tsx
 * <QueueShell<Patient>
 *   title="Triage queue"
 *   defaultSelectedId={null}
 *   items={patients}
 *   getRowId={(p) => p.id}
 *   renderRow={(p) => ({ primary: p.name, secondary: p.complaint })}
 *   renderDetail={(p) => <TriageSummary patient={p} />}
 *   emptyIcon={Inbox}
 *   emptyTitle="No patients waiting for triage"
 *   emptyAction={{ label: 'Register a walk-in', leadingIcon: Plus, onClick: openIntake }}
 * />
 * ```
 */

export interface QueueFilterOption {
  value: string;
  label: string;
}

export interface QueueFilter {
  /** Stable key; also the key in the `filterValues` record. */
  id: string;
  /** Accessible name for the control. */
  label: string;
  type: 'search' | 'select';
  placeholder?: string;
  /** For `type: 'select'`. */
  options?: QueueFilterOption[];
}

export type QueueStatus = 'idle' | 'loading' | 'error';

/** The row slots `renderRow` returns. The shell arranges them; the caller never lays a row out. */
export interface QueueRowContent {
  /** The headline line — patient name, order title. Shown at medium weight. */
  primary: ReactNode;
  /** A muted line beneath it — a count, a complaint, an id. Truncates. */
  secondary?: ReactNode;
  /** A right-aligned cluster — status `Badge`s, a waiting time. Wraps under the main text on a narrow row. */
  trailing?: ReactNode;
}

export interface QueueShellProps<T> {
  title: ReactNode;
  /** Rendered beside the title — usually `<Badge>` tallies, each linking to the filtered list. */
  counts?: ReactNode;
  /** Extra header buttons, placed left of the Refresh button. */
  headerActions?: ReactNode;
  /** Activated by the Refresh button. Omit to hide it. */
  onRefresh?: () => void;
  /** Shows the Refresh button in its loading state. */
  refreshing?: boolean;

  items: T[];
  getRowId: (item: T) => string;
  renderRow: (item: T, state: { selected: boolean; active: boolean }) => QueueRowContent;
  renderDetail: (item: T) => ReactNode;
  /** Heading for the detail pane / title for the mobile modal. */
  detailTitle?: (item: T) => ReactNode;

  selectedId?: string | null;
  defaultSelectedId?: string | null;
  onSelectChange?: (id: string | null, item: T | null) => void;

  filters?: QueueFilter[];
  filterValues?: Record<string, string>;
  onFilterChange?: (values: Record<string, string>) => void;

  /** @default 'idle' */
  status?: QueueStatus;
  errorTitle?: string;
  errorDescription?: ReactNode;
  onRetry?: () => void;
  /** Skeleton rows shown while loading with no items yet. @default 6 */
  loadingRowCount?: number;

  emptyTitle?: string;
  emptyDescription?: ReactNode;
  emptyIcon?: LucideIcon;
  emptyAction?: EmptyStateAction;

  /** Accessible name for the row list. @default `title` when it is a string. */
  listLabel?: string;
  className?: string;
}

const MOBILE_QUERY = '(max-width: 899px)';
const NAV_KEYS = ['ArrowDown', 'ArrowUp', 'Home', 'End'];

function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() =>
    typeof window !== 'undefined' && 'matchMedia' in window ? window.matchMedia(query).matches : false,
  );
  useEffect(() => {
    const mql = window.matchMedia(query);
    const onChange = (event: MediaQueryListEvent) => setMatches(event.matches);
    mql.addEventListener('change', onChange);
    return () => mql.removeEventListener('change', onChange);
  }, [query]);
  return matches;
}

export function QueueShell<T>({
  title,
  counts,
  headerActions,
  onRefresh,
  refreshing = false,
  items,
  getRowId,
  renderRow,
  renderDetail,
  detailTitle,
  selectedId,
  defaultSelectedId = null,
  onSelectChange,
  filters,
  filterValues,
  onFilterChange,
  status = 'idle',
  errorTitle = "Couldn't load this queue",
  errorDescription = 'Check your connection and try again.',
  onRetry,
  loadingRowCount = 6,
  emptyTitle = 'Nothing in this queue',
  emptyDescription,
  emptyIcon,
  emptyAction,
  listLabel,
  className,
}: QueueShellProps<T>) {
  const isMobile = useMediaQuery(MOBILE_QUERY);

  // ── selection ────────────────────────────────────────────────────────────
  const selectionControlled = selectedId !== undefined;
  const [internalSelected, setInternalSelected] = useState<string | null>(defaultSelectedId);
  const activeSelectedId = selectionControlled ? selectedId ?? null : internalSelected;
  const selectedItem = useMemo(
    () => items.find((item) => getRowId(item) === activeSelectedId) ?? null,
    [items, getRowId, activeSelectedId],
  );

  const select = useCallback(
    (id: string | null) => {
      if (!selectionControlled) setInternalSelected(id);
      onSelectChange?.(id, id == null ? null : items.find((item) => getRowId(item) === id) ?? null);
    },
    [selectionControlled, onSelectChange, items, getRowId],
  );

  // ── filter bar ───────────────────────────────────────────────────────────
  const filtersControlled = filterValues !== undefined;
  const [internalFilters, setInternalFilters] = useState<Record<string, string>>({});
  const activeFilters = filtersControlled ? filterValues : internalFilters;

  const setFilter = useCallback(
    (id: string, value: string) => {
      const next = { ...activeFilters, [id]: value };
      if (!filtersControlled) setInternalFilters(next);
      onFilterChange?.(next);
    },
    [activeFilters, filtersControlled, onFilterChange],
  );

  // ── row keyboard navigation (roving tabindex) ────────────────────────────
  const [focusIndex, setFocusIndex] = useState(0);
  const rowButtons = useRef<(HTMLButtonElement | null)[]>([]);
  const safeFocusIndex = Math.min(focusIndex, Math.max(0, items.length - 1));

  const moveFocus = (nextIndex: number) => {
    setFocusIndex(nextIndex);
    const button = rowButtons.current[nextIndex];
    button?.focus();
    button?.scrollIntoView({ block: 'nearest' });
  };

  const onListKeyDown = (event: KeyboardEvent<HTMLUListElement>) => {
    if (!NAV_KEYS.includes(event.key) || items.length === 0) return;
    event.preventDefault();
    const last = items.length - 1;
    if (event.key === 'ArrowDown') moveFocus(Math.min(last, safeFocusIndex + 1));
    else if (event.key === 'ArrowUp') moveFocus(Math.max(0, safeFocusIndex - 1));
    else if (event.key === 'Home') moveFocus(0);
    else moveFocus(last);
  };

  const showSkeleton = status === 'loading' && items.length === 0;
  const showEmpty = status === 'idle' && items.length === 0;
  const splitLayout = !isMobile && selectedItem != null && status === 'idle' && items.length > 0;
  const resolvedListLabel = listLabel ?? (typeof title === 'string' ? title : 'Queue');

  // ── body ─────────────────────────────────────────────────────────────────
  let body: ReactNode;
  if (status === 'error') {
    body = (
      <div className={styles.centered}>
        <EmptyState
          role="alert"
          icon={AlertTriangle}
          title={errorTitle}
          description={errorDescription}
          primaryAction={
            onRetry ? { label: 'Try again', leadingIcon: RefreshCw, onClick: onRetry } : undefined
          }
        />
      </div>
    );
  } else if (showSkeleton) {
    body = (
      <div className={styles.listPane} aria-busy="true">
        <p className="sr-only" role="status">
          Loading {resolvedListLabel}
        </p>
        {Array.from({ length: loadingRowCount }, (_, i) => (
          <div key={i} className={styles.skeletonRow}>
            <Skeleton width="1/2" />
            <Skeleton width="3/4" />
          </div>
        ))}
      </div>
    );
  } else if (showEmpty) {
    body = (
      <div className={styles.centered}>
        <EmptyState
          icon={emptyIcon ?? Inbox}
          title={emptyTitle}
          description={emptyDescription}
          primaryAction={emptyAction}
        />
      </div>
    );
  } else {
    body = (
      <>
        <ul
          className={styles.list}
          role="list"
          aria-label={resolvedListLabel}
          aria-busy={status === 'loading' || undefined}
          onKeyDown={onListKeyDown}
        >
          {items.map((item, index) => {
            const id = getRowId(item);
            const selected = id === activeSelectedId;
            const active = index === safeFocusIndex;
            const { primary, secondary, trailing } = renderRow(item, { selected, active });
            return (
              <li key={id} className={styles.rowItem}>
                <button
                  type="button"
                  ref={(el) => {
                    rowButtons.current[index] = el;
                  }}
                  className={[styles.row, selected ? styles.rowSelected : ''].filter(Boolean).join(' ')}
                  tabIndex={active ? 0 : -1}
                  aria-current={selected ? 'true' : undefined}
                  onClick={() => {
                    setFocusIndex(index);
                    select(id);
                  }}
                >
                  <span className={styles.rowMain}>
                    <span className={styles.rowPrimary}>{primary}</span>
                    {secondary != null && <span className={styles.rowSecondary}>{secondary}</span>}
                  </span>
                  {trailing != null && <span className={styles.rowTrailing}>{trailing}</span>}
                </button>
              </li>
            );
          })}
        </ul>

        {!isMobile && (
          <aside className={styles.detailPane} aria-label="Details">
            {selectedItem ? (
              <>
                {detailTitle && <h2 className={styles.detailTitle}>{detailTitle(selectedItem)}</h2>}
                <div className={styles.detailBody}>{renderDetail(selectedItem)}</div>
              </>
            ) : (
              <p className={styles.detailEmpty}>Select a row to see its details.</p>
            )}
          </aside>
        )}
      </>
    );
  }

  return (
    <section className={[styles.shell, className].filter(Boolean).join(' ')} aria-label={resolvedListLabel}>
      <header className={styles.header}>
        <h1 className={styles.title}>{title}</h1>
        {counts && <div className={styles.counts}>{counts}</div>}
        <div className={styles.headerSpacer} />
        {headerActions && <div className={styles.headerActions}>{headerActions}</div>}
        {onRefresh && (
          <Button
            variant="secondary"
            size="sm"
            leadingIcon={RefreshCw}
            loading={refreshing || status === 'loading'}
            onClick={onRefresh}
          >
            Refresh
          </Button>
        )}
      </header>

      {filters && filters.length > 0 && (
        <div className={styles.filters}>
          {filters.map((filter) =>
            filter.type === 'search' ? (
              <Input
                key={filter.id}
                className={styles.filterControl}
                size="sm"
                leadingIcon={Search}
                aria-label={filter.label}
                placeholder={filter.placeholder ?? filter.label}
                value={activeFilters[filter.id] ?? ''}
                onChange={(event) => setFilter(filter.id, event.target.value)}
              />
            ) : (
              <Select
                key={filter.id}
                className={styles.filterControl}
                size="sm"
                aria-label={filter.label}
                placeholder={filter.placeholder}
                options={filter.options ?? []}
                value={activeFilters[filter.id] ?? ''}
                onChange={(event) => setFilter(filter.id, event.target.value)}
              />
            ),
          )}
        </div>
      )}

      <div className={[styles.body, splitLayout ? styles.bodySplit : ''].filter(Boolean).join(' ')}>{body}</div>

      {isMobile && selectedItem && (
        <Modal
          open
          onClose={() => select(null)}
          size="lg"
          title={detailTitle ? detailTitle(selectedItem) : 'Details'}
        >
          <div className={styles.detailBody}>{renderDetail(selectedItem)}</div>
        </Modal>
      )}
    </section>
  );
}
