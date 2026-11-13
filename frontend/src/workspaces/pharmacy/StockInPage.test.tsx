import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { StockInPage } from './StockInPage';

vi.mock('../../shared/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'test-token' }) }));
vi.mock('../../shared/auth/useMe', () => ({ useMe: () => ({ data: { username: 'pharmacist' } }) }));
vi.mock('../../shared/api/stores', () => ({ fetchMyPharmacyStore: async () => ({ id: 'store-1', name: 'Main pharmacy' }) }));
vi.mock('../../shared/api/suppliers', () => ({ fetchSuppliers: async () => [], createSupplier: vi.fn() }));
vi.mock('../../shared/api/medicinePurchaseCost', () => ({ fetchMedicinePurchaseCost: async () => null }));
vi.mock('../../shared/api/medicinePackaging', () => ({ fetchMedicinePackaging: async () => [
  { id: 'pack-1', itemId: 'medicine-1', unit: 'PACK_50', factor: 50, canSplit: true,
    version: 1, status: 'APPROVED', blistersPerPack: 5, unitsPerBlister: 10 },
] }));
vi.mock('../../shared/components/Combobox', () => ({
  Combobox: ({ label, onSelect }: { label: string; onSelect: (value: unknown) => void }) =>
    <button type="button" onClick={() => onSelect({ id: 'medicine-1', name: 'Medicine 1',
      strength: '500 mg', form: 'tablet', unit: 'TABLET' })}>{label}</button>,
}));

describe('pharmacy stock in pack counts', () => {
  it('shows blister and pill fields for the selected medicine pack and calculates accepted units', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={client}><StockInPage /></QueryClientProvider>);

    const medicine = screen.getByRole('heading', { name: 'Medicine 1' }).closest('.stock-in-line');
    expect(medicine).not.toBeNull();
    fireEvent.click(within(medicine as HTMLElement).getByRole('button', { name: 'Medicine' }));
    await waitFor(() => expect(within(medicine as HTMLElement).getByLabelText('Purchase unit')).toHaveTextContent('PACK_50'));
    fireEvent.change(within(medicine as HTMLElement).getByLabelText('Purchase unit'), { target: { value: 'PACK_50' } });

    expect(within(medicine as HTMLElement).getByLabelText('Blisters per pack')).toHaveValue(5);
    expect(within(medicine as HTMLElement).getByLabelText('Pills per blister (TABLET)')).toHaveValue(10);
    fireEvent.change(within(medicine as HTMLElement).getByLabelText('Accepted'), { target: { value: '2' } });
    expect(within(medicine as HTMLElement).getByText(/100 TABLET/)).toBeInTheDocument();
    expect(within(medicine as HTMLElement).getByText(/10 blisters/)).toBeInTheDocument();
  });
});
