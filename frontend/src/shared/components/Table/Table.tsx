import { ArrowDown, ArrowUp, ChevronsUpDown } from 'lucide-react';
import { useId } from 'react';
import type { KeyboardEvent, ReactNode } from 'react';
import styles from './Table.module.css';

/**
 * ADAGS table primitive. Tokens only — see STYLE_GUIDE.md §2, §3, §6, §8, and
 * the canonical `.table` rules in tokens.css, whose values this mirrors.
 *
 * Data-driven (columns + rows) so the native markup — `table > caption /
 * thead / tbody / tfoot`, `th[scope]`, `aria-sort` — is assembled once and
 * can't be wired wrong by a caller. It is never a stack of divs.
 *
 * Sorting is presentational only: a sortable header renders a real `<button>`
 * and the correct `aria-sort`, and reports the next `{ columnId, direction }`
 * through `onSortChange`. You sort `data` yourself and pass `sort` back for
 * the indicator. The component contains no comparator.
 *
 * Horizontal overflow is handled by a focusable, labelled scroll region
 * around the table, so a wide table stays keyboard-scrollable without
 * touching the table semantics.
 *
 * @example Read-only table with a caption
 * ```tsx
 * <Table
 *   caption="Admitted patients"
 *   columns={[
 *     { id: 'name', header: 'Patient', cell: (r) => r.name },
 *     { id: 'ward', header: 'Ward', cell: (r) => r.ward },
 *     { id: 'balance', header: 'Balance', numeric: true, cell: (r) => r.balance },
 *   ]}
 *   data={patients}
 *   emptyState="No patients are currently admitted"
 * />
 * ```
 *
 * @example Sortable headers (you own the comparator)
 * ```tsx
 * const [sort, setSort] = useState<TableSortState | null>({ columnId: 'name', direction: 'asc' });
 * const rows = useMemo(() => sortRows(patients, sort), [patients, sort]);
 * <Table
 *   caption="Patients"
 *   variant="bordered"
 *   sort={sort}
 *   onSortChange={setSort}
 *   columns={[
 *     { id: 'name', header: 'Patient', sortable: true, cell: (r) => r.name },
 *     { id: 'admittedAt', header: 'Admitted', sortable: true, cell: (r) => r.admittedAt },
 *   ]}
 *   data={rows}
 * />
 * ```
 *
 * @example Selectable rows with a footer
 * ```tsx
 * <Table
 *   caption="Invoice lines"
 *   variant="striped"
 *   size="sm"
 *   getRowId={(r) => r.id}
 *   selectedRowIds={selected}
 *   onRowClick={(r, id) => toggle(id)}
 *   columns={columns}
 *   data={lines}
 *   footer={
 *     <tr>
 *       <td colSpan={2}>Total</td>
 *       <td>{total}</td>
 *     </tr>
 *   }
 * />
 * ```
 */

export type TableVariant = 'default' | 'bordered' | 'striped';
export type TableSize = 'sm' | 'md' | 'lg';
export type TableAlign = 'left' | 'center' | 'right';
export type SortDirection = 'asc' | 'desc';

export interface TableSortState {
  columnId: string;
  direction: SortDirection;
}

export interface TableColumn<T> {
  /** Stable key; the sort key reported to `onSortChange`. */
  id: string;
  header: ReactNode;
  cell: (row: T, rowIndex: number) => ReactNode;
  /** Show a sort control and wire `aria-sort`. The component never sorts `data`. */
  sortable?: boolean;
  /** Horizontal alignment of the header and its cells. @default 'left' */
  align?: TableAlign;
  /** Right-align and use tabular figures. Shorthand for money / quantity columns. */
  numeric?: boolean;
  /** Render this column's body cells as `<th scope="row">` instead of `<td>`. */
  rowHeader?: boolean;
  /** Accessible name for the sort button when `header` isn't plain text. */
  sortLabel?: string;
}

export interface TableProps<T> {
  columns: TableColumn<T>[];
  data: T[];
  /** Stable id per row, for selection and React keys. @default the row index */
  getRowId?: (row: T, index: number) => string;

  caption?: ReactNode;
  /** Keep the caption for screen readers but hide it visually. @default false */
  captionHidden?: boolean;
  /** Names the scroll region when there is no caption. */
  regionLabel?: string;

  /** @default 'default' */
  variant?: TableVariant;
  /** @default 'md' */
  size?: TableSize;

  /** Current sort — controlled. `null` means unsorted. */
  sort?: TableSortState | null;
  /** Fired when a sortable header is activated, with the next sort state. */
  onSortChange?: (next: TableSortState) => void;

  /** Selected row ids → `aria-selected` and selected-row styling. */
  selectedRowIds?: ReadonlySet<string> | readonly string[];
  onRowClick?: (row: T, id: string) => void;

  /** Body replacement when `data` is empty. Say what the emptiness means in
   *  context, not "No data". */
  emptyState?: ReactNode;
  /** `<tfoot>` content — supply your own `<tr>` / `<td>`. */
  footer?: ReactNode;

  /** class on the scroll container. */
  className?: string;
  /** class on the `<table>`. */
  tableClassName?: string;
}

const cx = (...parts: Array<string | false | undefined>) => parts.filter(Boolean).join(' ');

function alignClass(column: TableColumn<unknown>): string | undefined {
  if (column.numeric) return styles.numeric;
  if (column.align === 'center') return styles.alignCenter;
  if (column.align === 'right') return styles.alignRight;
  return undefined;
}

export function Table<T>({
  columns,
  data,
  getRowId,
  caption,
  captionHidden = false,
  regionLabel,
  variant = 'default',
  size = 'md',
  sort,
  onSortChange,
  selectedRowIds,
  onRowClick,
  emptyState = 'No rows to show',
  footer,
  className,
  tableClassName,
}: TableProps<T>) {
  const captionId = useId();

  const selectionEnabled = selectedRowIds !== undefined;
  const selectedSet =
    selectedRowIds instanceof Set
      ? selectedRowIds
      : new Set<string>(selectedRowIds ?? []);

  const rowId = (row: T, index: number) => getRowId?.(row, index) ?? String(index);

  const handleSort = (column: TableColumn<T>) => {
    if (!column.sortable || !onSortChange) return;
    const isActive = sort?.columnId === column.id;
    const direction: SortDirection = isActive && sort?.direction === 'asc' ? 'desc' : 'asc';
    onSortChange({ columnId: column.id, direction });
  };

  const handleRowKeyDown = (event: KeyboardEvent<HTMLTableRowElement>, row: T, id: string) => {
    if (!onRowClick) return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onRowClick(row, id);
    }
  };

  // The scroll region only becomes a focusable landmark when it has a name.
  const regionName = caption ? { 'aria-labelledby': captionId } : regionLabel ? { 'aria-label': regionLabel } : null;

  return (
    <div
      className={cx(styles.scroll, variant === 'bordered' && styles.scrollBordered, className)}
      {...(regionName ? { role: 'region', tabIndex: 0, ...regionName } : {})}
    >
      <table className={cx(styles.table, styles[variant], styles[size], tableClassName)}>
        {caption && (
          <caption className={captionHidden ? 'sr-only' : styles.caption}>{caption}</caption>
        )}

        <thead>
          <tr>
            {columns.map((column) => {
              const isActiveSort = sort?.columnId === column.id;
              const ariaSort = column.sortable
                ? isActiveSort
                  ? sort?.direction === 'asc'
                    ? 'ascending'
                    : 'descending'
                  : 'none'
                : undefined;
              const SortIcon = !isActiveSort ? ChevronsUpDown : sort?.direction === 'asc' ? ArrowUp : ArrowDown;

              return (
                <th
                  key={column.id}
                  scope="col"
                  aria-sort={ariaSort}
                  className={cx(alignClass(column as TableColumn<unknown>))}
                >
                  {column.sortable && onSortChange ? (
                    <button
                      type="button"
                      className={styles.sortButton}
                      onClick={() => handleSort(column)}
                      aria-label={column.sortLabel}
                    >
                      <span>{column.header}</span>
                      <SortIcon
                        size={16}
                        strokeWidth={2}
                        aria-hidden="true"
                        className={cx(styles.sortIcon, !isActiveSort && styles.sortIconInactive)}
                      />
                    </button>
                  ) : (
                    column.header
                  )}
                </th>
              );
            })}
          </tr>
        </thead>

        <tbody>
          {data.length === 0 ? (
            <tr>
              <td className={styles.emptyCell} colSpan={columns.length}>
                {emptyState}
              </td>
            </tr>
          ) : (
            data.map((row, index) => {
              const id = rowId(row, index);
              const isSelected = selectedSet.has(id);
              const interactive = Boolean(onRowClick);
              return (
                <tr
                  key={id}
                  className={cx(isSelected && styles.selected, interactive && styles.rowInteractive)}
                  aria-selected={selectionEnabled ? isSelected : undefined}
                  tabIndex={interactive ? 0 : undefined}
                  onClick={interactive ? () => onRowClick?.(row, id) : undefined}
                  onKeyDown={interactive ? (event) => handleRowKeyDown(event, row, id) : undefined}
                >
                  {columns.map((column) => {
                    const content = column.cell(row, index);
                    const cellClass = alignClass(column as TableColumn<unknown>);
                    return column.rowHeader ? (
                      <th key={column.id} scope="row" className={cellClass}>
                        {content}
                      </th>
                    ) : (
                      <td key={column.id} className={cellClass}>
                        {content}
                      </td>
                    );
                  })}
                </tr>
              );
            })
          )}
        </tbody>

        {footer && <tfoot>{footer}</tfoot>}
      </table>
    </div>
  );
}
