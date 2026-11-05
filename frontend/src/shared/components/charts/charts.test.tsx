import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { CategoryBarChart, type CategoryBar } from './CategoryBarChart';
import { RankedBarChart, type RankedBarRow } from './RankedBarChart';
import { TrendAreaChart, type TrendPoint } from './TrendAreaChart';

// The eight revenue categories, in the order the server returns them.
// Radiology must be present and distinct from both laboratory and surgery —
// the classification mistake this split exists to prevent.
const CATEGORIES: CategoryBar[] = [
  { id: 'CONSULTATION', label: 'Consultation / OPD', primary: 20_000, secondary: 20_000 },
  { id: 'SURGERY', label: 'Surgery', primary: 0, secondary: 700_000 },
  { id: 'THEATRE_PROCEDURE', label: 'Theatre procedures', primary: 0, secondary: 90_000 },
  { id: 'PHARMACY', label: 'Pharmacy / medication', primary: 0, secondary: 0 },
  { id: 'LABORATORY', label: 'Laboratory', primary: 0, secondary: 15_000 },
  { id: 'RADIOLOGY', label: 'Radiology / imaging', primary: 0, secondary: 120_000 },
  { id: 'INPATIENT', label: 'Inpatient / ward', primary: 0, secondary: 0 },
  { id: 'OTHER', label: 'Other services', primary: 0, secondary: 0 },
];

describe('CategoryBarChart', () => {
  it('renders every category, including ones that earned nothing', () => {
    render(<CategoryBarChart bars={CATEGORIES} currencyCode="TZS" primaryLabel="Collected" secondaryLabel="Billed" />);
    const table = screen.getByRole('table');
    for (const bar of CATEGORIES) {
      expect(within(table).getByRole('rowheader', { name: bar.label })).toBeInTheDocument();
    }
  });

  it('keeps radiology separate from laboratory and surgery', () => {
    render(<CategoryBarChart bars={CATEGORIES} currencyCode="TZS" primaryLabel="Collected" secondaryLabel="Billed" />);
    const radiology = screen.getByRole('rowheader', { name: 'Radiology / imaging' }).closest('tr');
    const laboratory = screen.getByRole('rowheader', { name: 'Laboratory' }).closest('tr');

    expect(within(radiology as HTMLElement).getByText('120,000/=')).toBeInTheDocument();
    expect(within(laboratory as HTMLElement).getByText('15,000/=')).toBeInTheDocument();
    expect(within(laboratory as HTMLElement).queryByText('120,000/=')).toBeNull();
  });

  it('exposes both series per bar to assistive tech, not colour alone', () => {
    render(<CategoryBarChart bars={CATEGORIES} currencyCode="TZS" primaryLabel="Collected" secondaryLabel="Billed" />);
    expect(
      screen.getByRole('button', { name: 'Surgery: Billed 700,000/=, Collected 0/=' }),
    ).toBeInTheDocument();
  });

  it('renders an all-zero period without crashing or drawing phantom bars', () => {
    const empty = CATEGORIES.map((bar) => ({ ...bar, primary: 0, secondary: 0 }));
    const { container } = render(
      <CategoryBarChart bars={empty} currencyCode="TZS" primaryLabel="Collected" secondaryLabel="Billed" />,
    );
    const bars = container.querySelectorAll('.category-bar');
    bars.forEach((bar) => expect(bar.getAttribute('height')).toBe('0'));
  });

  it('survives an empty dataset', () => {
    const { container } = render(
      <CategoryBarChart bars={[]} currencyCode="TZS" primaryLabel="Collected" secondaryLabel="Billed" />,
    );
    expect(container.querySelectorAll('.category-bar')).toHaveLength(0);
  });

  it('does not emit NaN geometry for a negative value', () => {
    const negative: CategoryBar[] = [{ id: 'X', label: 'Refund heavy', primary: -5_000, secondary: 10_000 }];
    const { container } = render(
      <CategoryBarChart bars={negative} currencyCode="TZS" primaryLabel="Collected" secondaryLabel="Billed" />,
    );
    container.querySelectorAll('rect').forEach((rect) => {
      expect(rect.getAttribute('y') ?? '0').not.toContain('NaN');
      expect(Number(rect.getAttribute('height'))).toBeGreaterThanOrEqual(0);
    });
  });
});

describe('TrendAreaChart', () => {
  const points: TrendPoint[] = [
    { bucket: '2026-10-05', billed: 10_000, collected: 10_000 },
    { bucket: '2026-10-06', billed: 830_000, collected: 10_000 },
  ];

  it('publishes every exact figure in a hidden table', () => {
    render(<TrendAreaChart points={points} currencyCode="TZS" granularityLabel="Daily" />);
    const table = screen.getByRole('table');
    expect(within(table).getByText('830,000/=')).toBeInTheDocument();
    expect(table).toHaveTextContent('05 Oct 2026');
  });

  it('labels the table per day, not "per daily"', () => {
    render(<TrendAreaChart points={points} currencyCode="TZS" granularityLabel="Daily" />);
    expect(screen.getByRole('table')).toHaveAccessibleName(/per day$/);
  });

  it('renders a single-point series without dividing by zero', () => {
    const { container } = render(
      <TrendAreaChart points={[points[0]]} currencyCode="TZS" granularityLabel="Daily" />,
    );
    container.querySelectorAll('path').forEach((path) => {
      expect(path.getAttribute('d')).not.toContain('NaN');
    });
  });

  it('renders an all-zero series as a flat baseline, not an empty chart', () => {
    const zeros: TrendPoint[] = points.map((p) => ({ ...p, billed: 0, collected: 0 }));
    const { container } = render(<TrendAreaChart points={zeros} currencyCode="TZS" granularityLabel="Daily" />);
    container.querySelectorAll('path').forEach((path) => {
      expect(path.getAttribute('d')).not.toContain('NaN');
    });
    expect(screen.getAllByText('0/=').length).toBeGreaterThan(0);
  });
});

describe('RankedBarChart', () => {
  const rows: RankedBarRow[] = [
    { id: '1', label: 'Meropenem 1g inj', sublabel: '1g · INJECTION', value: 80_000, secondaryValue: 80_000, meta: 'Qty billed 1 (not dispensed)' },
    { id: '2', label: 'Paracetamol 500mg tab', value: 10_000, secondaryValue: 10_000, meta: 'Qty billed 2 (not dispensed)' },
  ];

  it('ranks highest first and shows the quantity basis', () => {
    render(<RankedBarChart rows={rows} currencyCode="TZS" valueLabel="Collected" secondaryLabel="Billed" />);
    const items = screen.getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Meropenem 1g inj');
    expect(items[0]).toHaveTextContent('not dispensed');
  });

  it('shows strength and form when the stock catalogue matched', () => {
    render(<RankedBarChart rows={rows} currencyCode="TZS" valueLabel="Collected" secondaryLabel="Billed" />);
    expect(screen.getByText('1g · INJECTION')).toBeInTheDocument();
  });

  it('renders an empty ranking without crashing', () => {
    render(<RankedBarChart rows={[]} currencyCode="TZS" valueLabel="Collected" secondaryLabel="Billed" />);
    expect(screen.queryAllByRole('listitem')).toHaveLength(0);
  });

  // A negative CSS width is dropped by the browser rather than clamped, so
  // the bar would silently vanish instead of reading as zero. Assert the
  // clamped value explicitly, not merely the absence of a minus sign.
  it('clamps a negative value to a zero-width bar', () => {
    const negative: RankedBarRow[] = [{ id: '1', label: 'Reversed', value: -5_000, secondaryValue: 0 }];
    const { container } = render(
      <RankedBarChart rows={negative} currencyCode="TZS" valueLabel="Collected" secondaryLabel="Billed" />,
    );
    const fill = container.querySelector('.ranked-fill') as HTMLElement;
    expect(fill.style.width).toBe('0%');
  });

  it('never exceeds a full-width bar', () => {
    const rowsWithPeak: RankedBarRow[] = [{ id: '1', label: 'Top', value: 100, secondaryValue: 100 }];
    const { container } = render(
      <RankedBarChart rows={rowsWithPeak} currencyCode="TZS" valueLabel="Collected" secondaryLabel="Billed" />,
    );
    const fill = container.querySelector('.ranked-fill') as HTMLElement;
    expect(fill.style.width).toBe('100%');
  });
});
