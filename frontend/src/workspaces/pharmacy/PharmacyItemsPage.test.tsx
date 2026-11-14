import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, within, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { PharmacyItemsPage } from './PharmacyItemsPage';
import { searchItems } from '../../shared/api/items';
import { fetchMedicinePackagingCatalogue, proposeMedicinePackaging } from '../../shared/api/medicinePackaging';
import { ApiError } from '../../shared/api/client';
import { proposeFormChange } from '../../shared/api/pharmacyItemProposals';

vi.mock('../../shared/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'test-token' }) }));
vi.mock('../../shared/auth/usePermission', () => ({ usePermission: () => true }));
vi.mock('../../shared/api/items', () => ({ searchItems: vi.fn() }));
vi.mock('../../shared/api/medicinePackaging', () => ({
  fetchMedicinePackaging: vi.fn().mockResolvedValue([]),
  fetchMedicinePackagingCatalogue: vi.fn().mockResolvedValue({ items: [], hasMore: false }),
  proposeMedicinePackaging: vi.fn().mockResolvedValue({}),
}));
vi.mock('../../shared/api/pharmacyItemProposals', () => ({
  proposeFormChange: vi.fn().mockResolvedValue({}),
  proposeNewItem: vi.fn().mockResolvedValue({}),
  proposePriceChange: vi.fn().mockResolvedValue({}),
}));

describe('pharmacy medicine packaging', () => {
  it('lists approved packaging and medicines with no approved package', async () => {
    const packaged = { id: 'medicine-1', code: 'MED-1', name: 'Amoxicillin', form: 'tablet', strength: '500 mg',
        baseUnit: 'tablet', active: true, packages: [{ id: 'pack-1', itemId: 'medicine-1', unit: 'PACK_50',
          factor: 50, canSplit: true, version: 1, status: 'APPROVED', blistersPerPack: 5, unitsPerBlister: 10 }] };
    const unpackaged = { id: 'medicine-2', code: 'MED-2', name: 'Paracetamol', form: 'tablet', strength: null,
      baseUnit: 'tablet', active: false, packages: [] };
    vi.mocked(fetchMedicinePackagingCatalogue).mockImplementation(async (_query, configuration) => ({
      items: configuration === 'CONFIGURED' ? [packaged] : configuration === 'UNCONFIGURED' ? [unpackaged] : [packaged, unpackaged],
      hasMore: false,
    }));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={client}><PharmacyItemsPage /></QueryClientProvider>);
    const panel = screen.getByRole('tabpanel');
    expect(within(panel).getByRole('heading', { name: 'Medicine packaging catalogue' })).toBeInTheDocument();
    const table = await within(panel).findByRole('table');
    expect(within(table).getByText('PACK_50')).toBeInTheDocument();
    const packagedRow = within(table).getByText('PACK_50').closest('tr') as HTMLElement;
    expect(within(packagedRow).getAllByRole('cell').slice(2).map((cell) => cell.textContent)).toEqual([
      'PACK_50', '5', '10', '50', 'Can split', 'Active', 'Edit',
    ]);
    expect(within(table).queryByText('No approved package')).not.toBeInTheDocument();
    fireEvent.change(within(panel).getByLabelText('Packaging status'), { target: { value: 'UNCONFIGURED' } });
    expect(await within(panel).findByText('No approved package')).toBeInTheDocument();
    expect(within(panel).queryByText('PACK_50')).not.toBeInTheDocument();
    fireEvent.change(within(panel).getByLabelText('Search by medicine name or code'), { target: { value: 'Amoxicillin' } });
    fireEvent.click(within(panel).getByRole('button', { name: 'Search' }));
    await waitFor(() => expect(fetchMedicinePackagingCatalogue).toHaveBeenCalledWith('Amoxicillin', 'ALL', 50, 0, 'test-token'));
  });

  it('prefills a configured package and proposes its replacement without changing the approved row', async () => {
    vi.mocked(fetchMedicinePackagingCatalogue).mockResolvedValue({ items: [{
      id: 'medicine-1', code: 'MED-1', name: 'Amoxicillin', form: 'tablet', strength: '500 mg',
      baseUnit: 'tablet', active: true, packages: [{ id: 'pack-1', itemId: 'medicine-1',
        unit: 'PACK_50', factor: 50, canSplit: true, version: 1, status: 'APPROVED',
        blistersPerPack: 5, unitsPerBlister: 10 }],
    }], hasMore: false });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={client}><PharmacyItemsPage /></QueryClientProvider>);
    fireEvent.click(await screen.findByRole('button', { name: 'Edit Amoxicillin PACK_50' }));
    const panel = screen.getByRole('tabpanel');
    expect(within(panel).getByRole('heading', { name: 'Edit Amoxicillin packaging' })).toBeInTheDocument();
    expect(within(panel).getByLabelText('Blisters per pack')).toHaveValue(5);
    expect(within(panel).getByLabelText('tablet per blister')).toHaveValue(10);
    fireEvent.change(within(panel).getByLabelText('Blisters per pack'), { target: { value: '6' } });
    fireEvent.change(within(panel).getByLabelText('Reason and manufacturer specification'),
      { target: { value: 'Manufacturer now supplies six blisters' } });
    fireEvent.click(within(panel).getByRole('button', { name: 'Submit edited packaging' }));
    await waitFor(() => expect(proposeMedicinePackaging).toHaveBeenCalledWith('medicine-1',
      expect.objectContaining({ unit: 'PACK_60', quantityInBaseUnits: 60,
        replacesPackagingId: 'pack-1', blistersPerPack: 6, unitsPerBlister: 10 }), 'test-token'));
    expect(await screen.findByRole('status')).toHaveTextContent('Edited packaging submitted for Admin approval');
    expect(screen.getByRole('tab', { name: 'Packaging overview' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.queryByRole('button', { name: 'Submit edited packaging' })).not.toBeInTheDocument();
  });

  it('searches all medicines from the configured default', async () => {
    vi.mocked(fetchMedicinePackagingCatalogue).mockResolvedValue({ items: [], hasMore: false });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={client}><PharmacyItemsPage /></QueryClientProvider>);
    const panel = screen.getByRole('tabpanel');
    await waitFor(() => expect(fetchMedicinePackagingCatalogue).toHaveBeenCalledWith('', 'CONFIGURED', 50, 0, 'test-token'));
    fireEvent.change(within(panel).getByLabelText('Search by medicine name or code'), { target: { value: 'Paracetamol' } });
    fireEvent.click(within(panel).getByRole('button', { name: 'Search' }));
    await waitFor(() => expect(fetchMedicinePackagingCatalogue).toHaveBeenCalledWith('Paracetamol', 'ALL', 50, 0, 'test-token'));
    expect(within(panel).getByLabelText('Packaging status')).toHaveValue('ALL');
  });

  it('shows blister and tablet counts and calculates units per pack', () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={client}><PharmacyItemsPage /></QueryClientProvider>);
    fireEvent.click(screen.getByRole('tab', { name: 'Packaging' }));
    const panel = screen.getByRole('tabpanel');
    fireEvent.change(within(panel).getByLabelText('Blisters per pack'), { target: { value: '5' } });
    fireEvent.change(within(panel).getByLabelText('Tablets/capsules per blister'), { target: { value: '10' } });
    expect(within(panel).getByRole('status')).toHaveTextContent('5 blisters × 10');
    expect(within(panel).getByRole('status')).toHaveTextContent('50 units per pack');
    fireEvent.change(within(panel).getByLabelText('Package type'), { target: { value: 'OTHER' } });
    expect(within(panel).getByLabelText('Package name')).toBeInTheDocument();
    expect(within(panel).queryByLabelText('Blisters per pack')).not.toBeInTheDocument();
  });

  it('lets the user choose a new dosage form through catalogue approval before packaging', async () => {
    vi.mocked(proposeFormChange).mockClear();
    vi.mocked(proposeMedicinePackaging).mockClear();
    vi.mocked(searchItems).mockResolvedValue([{ id: 'medicine-1', name: 'Amoxicillin', form: 'TABLET', unit: 'tablet',
      regulatorySchedule: 'PRESCRIPTION_ONLY', selectable: true }]);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={client}><PharmacyItemsPage /></QueryClientProvider>);
    fireEvent.click(screen.getByRole('tab', { name: 'Packaging' }));
    const panel = screen.getByRole('tabpanel');
    fireEvent.change(within(panel).getByRole('combobox', { name: 'Medicine' }), { target: { value: 'Amox' } });
    fireEvent.mouseDown(await within(panel).findByRole('option', { name: /Amoxicillin/ }));
    const form = within(panel).getByLabelText('Dosage form');
    expect(form).toHaveValue('TABLET');
    expect(within(form).getAllByRole('option').map(option => option.getAttribute('value'))).toEqual([
      '', 'CAPSULE', 'TABLET', 'CAPLET', 'SYRUP', 'INJECTION', 'CREAM', 'GEL', 'LOTION', 'PASTE',
    ]);
    fireEvent.change(form, { target: { value: 'SYRUP' } });
    expect(within(panel).getByRole('button', { name: 'Propose packaging' })).toBeDisabled();
    fireEvent.change(within(panel).getByLabelText('Base inventory unit for selected form'), { target: { value: 'ML' } });
    fireEvent.change(within(panel).getByLabelText('Reason and product evidence'),
      { target: { value: 'Manufacturer label confirms oral syrup' } });
    fireEvent.click(within(panel).getByRole('button', { name: 'Submit dosage form correction for approval' }));
    await waitFor(() => expect(proposeFormChange).toHaveBeenCalledWith({ itemId: 'medicine-1',
      proposedForm: 'SYRUP', proposedUnit: 'ML', justification: 'Manufacturer label confirms oral syrup' }, 'test-token'));
    expect(proposeMedicinePackaging).not.toHaveBeenCalled();
  });

  it('defaults syrup packaging to a bottle configuration', async () => {
    vi.mocked(searchItems).mockResolvedValue([{ id: 'medicine-syrup', name: 'Paracetamol syrup', form: 'SYRUP', unit: 'mL',
      regulatorySchedule: 'PRESCRIPTION_ONLY', selectable: true }]);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={client}><PharmacyItemsPage /></QueryClientProvider>);
    fireEvent.click(screen.getByRole('tab', { name: 'Packaging' }));
    const panel = screen.getByRole('tabpanel');
    fireEvent.change(within(panel).getByRole('combobox', { name: 'Medicine' }), { target: { value: 'Para' } });
    fireEvent.mouseDown(await within(panel).findByRole('option', { name: /Paracetamol syrup/ }));
    expect(within(panel).getByLabelText('Dosage form')).toHaveValue('SYRUP');
    expect(within(panel).getByLabelText('Package type')).toHaveValue('OTHER');
    expect(within(panel).getByPlaceholderText('BOTTLE_100')).toBeInTheDocument();
    expect(within(panel).getByLabelText('May dispense individual base units from this package')).not.toBeChecked();
    fireEvent.change(within(panel).getByLabelText('Package name'), { target: { value: 'BOTTLE_100' } });
    fireEvent.change(within(panel).getByLabelText('mL per package'), { target: { value: '100' } });
    fireEvent.change(within(panel).getByLabelText('Reason and manufacturer specification'),
      { target: { value: 'Manufacturer 100 mL bottle' } });
    fireEvent.click(within(panel).getByRole('button', { name: 'Propose packaging' }));
    await waitFor(() => expect(proposeMedicinePackaging).toHaveBeenCalledWith('medicine-syrup',
      expect.objectContaining({ unit: 'BOTTLE_100', quantityInBaseUnits: 100 }), 'test-token'));
  });

  it.each([[5, 10], [1, 1]])('accepts a classified inactive medicine with %i blisters and %i units per blister', async (blisters, units) => {
    vi.mocked(searchItems).mockResolvedValue([{ id: 'medicine-1', name: 'Amoxicillin', form: 'TABLET', unit: 'tablet',
      regulatorySchedule: 'PRESCRIPTION_ONLY', selectable: false, unselectableReason: 'Not yet active in the catalogue.' }]);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={client}><PharmacyItemsPage /></QueryClientProvider>);
    fireEvent.click(screen.getByRole('tab', { name: 'Packaging' }));
    const panel = screen.getByRole('tabpanel');
    fireEvent.change(within(panel).getByRole('combobox', { name: 'Medicine' }), { target: { value: 'Amox' } });
    const medicine = await within(panel).findByRole('option', { name: /Amoxicillin/ });
    expect(medicine).not.toHaveAttribute('aria-disabled', 'true');
    fireEvent.mouseDown(medicine);
    expect(within(panel).getByText(/Selected medicine:/)).toHaveTextContent('Amoxicillin');
    fireEvent.change(within(panel).getByLabelText('Blisters per pack'), { target: { value: String(blisters) } });
    fireEvent.change(within(panel).getByLabelText('tablet per blister'), { target: { value: String(units) } });
    fireEvent.change(within(panel).getByLabelText('Reason and manufacturer specification'), { target: { value: 'Manufacturer pack: 5 × 10' } });
    fireEvent.click(within(panel).getByRole('button', { name: 'Propose packaging' }));
    await waitFor(() => expect(proposeMedicinePackaging).toHaveBeenCalledWith('medicine-1',
      expect.objectContaining({ unit: `PACK_${blisters * units}`, quantityInBaseUnits: blisters * units,
        blistersPerPack: blisters, unitsPerBlister: units }), 'test-token'));
  });

  it('shows the server reason when a packaging proposal is rejected', async () => {
    vi.mocked(searchItems).mockResolvedValue([{ id: 'medicine-1', name: 'Amoxicillin', form: 'TABLET', unit: 'tablet',
      regulatorySchedule: 'PRESCRIPTION_ONLY', selectable: true }]);
    vi.mocked(proposeMedicinePackaging).mockRejectedValueOnce(new ApiError(400,
      { detail: 'A proposal for PACK_1 is already awaiting approval' }));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={client}><PharmacyItemsPage /></QueryClientProvider>);
    fireEvent.click(screen.getByRole('tab', { name: 'Packaging' }));
    const panel = screen.getByRole('tabpanel');
    fireEvent.change(within(panel).getByRole('combobox', { name: 'Medicine' }), { target: { value: 'Amox' } });
    fireEvent.mouseDown(await within(panel).findByRole('option', { name: /Amoxicillin/ }));
    fireEvent.change(within(panel).getByLabelText('Blisters per pack'), { target: { value: '1' } });
    fireEvent.change(within(panel).getByLabelText('tablet per blister'), { target: { value: '1' } });
    fireEvent.change(within(panel).getByLabelText('Reason and manufacturer specification'), { target: { value: 'Single tablet pack' } });
    fireEvent.click(within(panel).getByRole('button', { name: 'Propose packaging' }));
    expect(await within(panel).findByRole('alert')).toHaveTextContent('A proposal for PACK_1 is already awaiting approval');
  });
});


describe('dosage form correction', () => {
  async function selectImportedMedicine() {
    vi.mocked(proposeFormChange).mockClear();
    vi.mocked(searchItems).mockResolvedValue([{ id: 'imported-medicine', code: 'MED-42',
      name: 'Amoxicillin', strength: '500 mg', form: '—', unit: 'unit',
      regulatorySchedule: 'UNCLASSIFIED', selectable: false }]);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    render(<QueryClientProvider client={client}><PharmacyItemsPage /></QueryClientProvider>);
    fireEvent.click(screen.getByRole('tab', { name: 'Dosage form correction' }));
    const panel = screen.getByRole('tabpanel');
    fireEvent.change(within(panel).getByRole('combobox', { name: 'Medicine' }), { target: { value: 'Amox' } });
    fireEvent.mouseDown(await within(panel).findByRole('option', { name: /Amoxicillin/ }));
    return panel;
  }

  it('shows the selected imported medicine and submits a solid form with its specific base unit', async () => {
    const panel = await selectImportedMedicine();
    expect(within(panel).getByText(/Selected medicine:/)).toHaveTextContent('Amoxicillin · 500 mg (MED-42)');
    expect(within(panel).getByText(/Current dosage form:/)).toHaveTextContent('Not configured');
    expect(within(panel).getByLabelText('Base inventory unit for this presentation')).toHaveValue('');
    fireEvent.change(within(panel).getByLabelText('Proposed dosage form'), { target: { value: 'CAPSULE' } });
    expect(within(panel).getByLabelText('Base inventory unit for this presentation')).toHaveValue('CAPSULE');
    fireEvent.change(within(panel).getByLabelText('Reason and product evidence'), { target: { value: 'Manufacturer label confirms capsules' } });
    fireEvent.click(within(panel).getByRole('button', { name: 'Submit for approval' }));
    await waitFor(() => expect(proposeFormChange).toHaveBeenCalledWith({ itemId: 'imported-medicine',
      proposedForm: 'CAPSULE', proposedUnit: 'CAPSULE', justification: 'Manufacturer label confirms capsules' }, 'test-token'));
    expect(await within(panel).findByRole('status')).toHaveTextContent('Dosage form correction sent for Admin review.');
  });

  it('requires an explicit liquid unit and blocks generic or mismatched units before submitting', async () => {
    const panel = await selectImportedMedicine();
    const form = within(panel).getByLabelText('Proposed dosage form');
    const unit = within(panel).getByLabelText('Base inventory unit for this presentation');
    const button = within(panel).getByRole('button', { name: 'Submit for approval' });
    fireEvent.change(form, { target: { value: 'TABLET' } });
    fireEvent.change(form, { target: { value: 'SYRUP' } });
    expect(unit).toHaveValue('');
    fireEvent.change(within(panel).getByLabelText('Reason and product evidence'), { target: { value: 'Bottle label confirms syrup' } });
    for (const invalid of ['unit', 'TABLET', '—']) {
      fireEvent.change(unit, { target: { value: invalid } });
      expect(unit).toHaveAttribute('aria-invalid', 'true');
      expect(button).toBeDisabled();
      fireEvent.submit(button.closest('form')!);
    }
    expect(proposeFormChange).not.toHaveBeenCalled();
    fireEvent.change(unit, { target: { value: 'ml' } });
    fireEvent.click(button);
    await waitFor(() => expect(proposeFormChange).toHaveBeenCalledWith(expect.objectContaining({
      proposedForm: 'SYRUP', proposedUnit: 'ML',
    }), 'test-token'));
  });
});
