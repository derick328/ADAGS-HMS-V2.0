import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Table } from './Table';
import type { TableColumn, TableSize, TableSortState, TableVariant } from './Table';
import styles from './Table.stories.module.css';

/**
 * Plain-React showcase for the Table primitive — not Storybook, just a route
 * that renders every variant / size / state for visual and keyboard review.
 * Mounted at /dev/components/table.
 */

interface Patient {
  id: string;
  name: string;
  ward: string;
  admittedAt: string;
  balance: string;
  balanceMinor: number;
}

const PATIENTS: Patient[] = [
  { id: 'p1', name: 'Amina Hassan', ward: 'Ward A1', admittedAt: '2026-08-22', balance: '0/=', balanceMinor: 0 },
  { id: 'p2', name: 'John Mushi', ward: 'Ward B2', admittedAt: '2026-08-24', balance: '48,000/=', balanceMinor: 48000 },
  { id: 'p3', name: 'Grace Kimaro', ward: 'Ward A1', admittedAt: '2026-08-19', balance: '12,500/=', balanceMinor: 12500 },
  { id: 'p4', name: 'Samuel Ally', ward: 'ICU', admittedAt: '2026-08-25', balance: '210,000/=', balanceMinor: 210000 },
];

const BASE_COLUMNS: TableColumn<Patient>[] = [
  { id: 'name', header: 'Patient', rowHeader: true, cell: (r) => r.name },
  { id: 'ward', header: 'Ward', cell: (r) => r.ward },
  { id: 'admittedAt', header: 'Admitted', cell: (r) => r.admittedAt },
  { id: 'balance', header: 'Balance', numeric: true, cell: (r) => r.balance },
];

const VARIANTS: TableVariant[] = ['default', 'bordered', 'striped'];
const SIZES: TableSize[] = ['sm', 'md', 'lg'];

function VariantSizeCell({ variant, size }: { variant: TableVariant; size: TableSize }) {
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>
        {variant} · {size}
      </p>
      <Table
        caption={`${variant} ${size} example`}
        captionHidden
        variant={variant}
        size={size}
        columns={BASE_COLUMNS}
        data={PATIENTS}
      />
    </div>
  );
}

function sortPatients(rows: Patient[], sort: TableSortState | null): Patient[] {
  if (!sort) return rows;
  const dir = sort.direction === 'asc' ? 1 : -1;
  return [...rows].sort((a, b) => {
    if (sort.columnId === 'balance') return (a.balanceMinor - b.balanceMinor) * dir;
    const av = String(a[sort.columnId as keyof Patient]);
    const bv = String(b[sort.columnId as keyof Patient]);
    return av.localeCompare(bv) * dir;
  });
}

function SortableCell() {
  const [sort, setSort] = useState<TableSortState | null>({ columnId: 'name', direction: 'asc' });
  const rows = useMemo(() => sortPatients(PATIENTS, sort), [sort]);
  const columns: TableColumn<Patient>[] = BASE_COLUMNS.map((c) =>
    c.id === 'ward' ? c : { ...c, sortable: true },
  );
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>sortable headers</p>
      <p className={styles.cellHint}>
        The component renders the control + aria-sort; this story owns the comparator. &ldquo;Ward&rdquo; is
        not sortable.
      </p>
      <Table caption="Patients" variant="bordered" sort={sort} onSortChange={setSort} columns={columns} data={rows} />
      <p className={styles.state}>
        sort: {sort ? `${sort.columnId} ${sort.direction}` : 'none'}
      </p>
    </div>
  );
}

function SelectableCell() {
  const [selected, setSelected] = useState<Set<string>>(new Set(['p2']));
  const toggle = (id: string) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>selected + interactive rows</p>
      <p className={styles.cellHint}>Click or focus a row and press Enter / Space to toggle selection.</p>
      <Table
        caption="Invoice lines"
        variant="striped"
        getRowId={(r) => r.id}
        selectedRowIds={selected}
        onRowClick={(_, id) => toggle(id)}
        columns={BASE_COLUMNS}
        data={PATIENTS}
      />
      <p className={styles.state}>selected: {[...selected].join(', ') || 'none'}</p>
    </div>
  );
}

function EmptyCell() {
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>empty state</p>
      <Table
        caption="Admitted patients"
        columns={BASE_COLUMNS}
        data={[]}
        emptyState="No patients are currently admitted"
      />
    </div>
  );
}

function FooterCell() {
  const total = PATIENTS.reduce((sum, p) => sum + p.balanceMinor, 0);
  return (
    <div className={styles.cell}>
      <p className={styles.cellLabel}>footer (tfoot)</p>
      <Table
        caption="Outstanding balances"
        variant="bordered"
        columns={BASE_COLUMNS}
        data={PATIENTS}
        footer={
          <tr>
            <th scope="row" colSpan={3}>
              Total
            </th>
            <td>{total.toLocaleString()}/=</td>
          </tr>
        }
      />
    </div>
  );
}

function OverflowCell() {
  const wideColumns: TableColumn<Patient>[] = [
    { id: 'name', header: 'Patient', rowHeader: true, cell: (r) => r.name },
    { id: 'ward', header: 'Ward', cell: (r) => r.ward },
    { id: 'admittedAt', header: 'Admitted', cell: (r) => r.admittedAt },
    { id: 'consultant', header: 'Consultant', cell: () => 'Dr. Mwakalinga' },
    { id: 'diagnosis', header: 'Working diagnosis', cell: () => 'Community-acquired pneumonia' },
    { id: 'nextReview', header: 'Next review', cell: () => '2026-08-27 08:00' },
    { id: 'balance', header: 'Balance', numeric: true, cell: (r) => r.balance },
  ];
  return (
    <div className={`${styles.cell} ${styles.narrow}`}>
      <p className={styles.cellLabel}>responsive horizontal overflow</p>
      <p className={styles.cellHint}>
        Constrained width. The table keeps real semantics; the wrapper is a focusable, labelled scroll
        region — Tab to it and use the arrow keys.
      </p>
      <Table caption="Ward round" columns={wideColumns} data={PATIENTS} />
    </div>
  );
}

export function TableStories() {
  return (
    <div className={styles.page}>
      <Link to="/dev/components" className={styles.back}>
        ← All components
      </Link>
      <h1 className={styles.title}>Table</h1>
      <p className={styles.subtitle}>
        Native table / thead / tbody / tfoot / th / td, never divs. Sortable headers via explicit props
        (no comparator inside), aria-sort wired, row hover and selected states, a labelled focusable
        scroll region for overflow, caption, footer and empty state. Motion removed under
        prefers-reduced-motion. Tokens only.
      </p>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Variants &times; sizes</h2>
        <p className={styles.sectionHint}>default · bordered · striped — each at sm / md / lg.</p>
        <div className={styles.grid}>
          {VARIANTS.map((variant) =>
            SIZES.map((size) => <VariantSizeCell key={`${variant}-${size}`} variant={variant} size={size} />),
          )}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Behaviour</h2>
        <p className={styles.sectionHint}>Sorting, selection, empty state, footer, overflow.</p>
        <div className={styles.grid}>
          <SortableCell />
          <SelectableCell />
          <EmptyCell />
          <FooterCell />
          <OverflowCell />
        </div>
      </section>
    </div>
  );
}
