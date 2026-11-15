import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { PricingPage } from './PricingPage';
import { createCatalogItem, searchCatalogItems, updateCatalogItem } from '../../shared/api/catalogImport';

vi.mock('../../shared/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'test-token' }) }));
vi.mock('../../shared/auth/usePermission', () => ({ usePermission: () => true }));
vi.mock('../../shared/auth/useMe', () => ({ useMe: () => ({ data: { username: 'admin' } }) }));
vi.mock('../../shared/api/pricing', () => ({
  fetchPendingPharmacyItemProposals: vi.fn().mockResolvedValue([]), reviewPharmacyItemProposal: vi.fn(),
}));
vi.mock('../../shared/api/medicinePackaging', () => ({
  fetchPendingMedicinePackaging: vi.fn().mockResolvedValue([]), approveMedicinePackaging: vi.fn(), rejectMedicinePackaging: vi.fn(),
}));
vi.mock('../../shared/api/catalogImport', async importOriginal => ({
  ...await importOriginal<typeof import('../../shared/api/catalogImport')>(),
  searchCatalogItems: vi.fn(), listCatalogImports: vi.fn().mockResolvedValue([]),
  createCatalogItem: vi.fn().mockResolvedValue({ id: 'new', itemCode: 123, productCode: 'LAB0123' }),
  updateCatalogItem: vi.fn().mockResolvedValue(undefined),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(<MemoryRouter><QueryClientProvider client={client}><PricingPage /></QueryClientProvider></MemoryRouter>);
}

const item = { id: 'item-1', code: 'LAB0001', originalCode: 'LAB0001', itemCode: 1,
  name: 'Original test', type: 'LABORATORY', billingCategory: 'DIAGNOSTIC_EXAMINATIONS',
  classification: 'Chemistry', currentCashPriceMinor: null, currencyCode: 'TZS', isActive: true };

describe('admin catalogue items', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(searchCatalogItems).mockResolvedValue({ page: 0, pageSize: 50, totalCount: 1, items: [item] });
  });

  it('creates an unpriced item when the optional price is blank', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /add.*item/i }));
    fireEvent.change(screen.getByLabelText('Product name'), { target: { value: 'New test' } });
    expect(screen.getByLabelText('CASH price (TZS, optional)')).not.toBeRequired();
    fireEvent.click(screen.getByRole('button', { name: 'Create item' }));
    await waitFor(() => expect(createCatalogItem).toHaveBeenCalledWith(expect.objectContaining({
      name: 'New test', priceMinor: null,
    }), 'test-token'));
  });

  it('opens an unpriced item and saves all editable fields with the original snapshot', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    const dialog = within(screen.getByRole('dialog', { name: 'Edit catalogue item' }));
    fireEvent.change(dialog.getByLabelText('Product name'), { target: { value: 'Updated test' } });
    fireEvent.change(dialog.getByLabelText('Type'), { target: { value: 'RADIOLOGY' } });
    fireEvent.change(dialog.getByLabelText('Billing category'), { target: { value: 'OTHER_CHARGES' } });
    fireEvent.change(dialog.getByLabelText('Classification (optional)'), { target: { value: 'Imaging' } });
    fireEvent.change(dialog.getByLabelText('Status'), { target: { value: 'inactive' } });
    fireEvent.change(dialog.getByLabelText('CASH price (TZS, optional)'), { target: { value: '1500' } });
    fireEvent.click(dialog.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(updateCatalogItem).toHaveBeenCalledWith('item-1', {
      name: 'Original test', type: 'LABORATORY', billingCategory: 'DIAGNOSTIC_EXAMINATIONS',
      classification: 'Chemistry', isActive: true, priceMinor: null,
    }, { name: 'Updated test', type: 'RADIOLOGY', billingCategory: 'OTHER_CHARGES',
      classification: 'Imaging', isActive: false, priceMinor: 1500 }, 'test-token'));
  });
});
