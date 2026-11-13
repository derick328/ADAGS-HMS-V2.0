import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { PackagingApprovalsPage } from './PricingPage';
import { approveMedicinePackaging, fetchPendingMedicinePackaging, rejectMedicinePackaging } from '../../shared/api/medicinePackaging';

vi.mock('../../shared/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'test-token' }) }));
vi.mock('../../shared/auth/usePermission', () => ({ usePermission: () => true }));
vi.mock('../../shared/auth/useMe', () => ({ useMe: () => ({ data: { username: 'reviewer' } }) }));
vi.mock('../../shared/api/medicinePackaging', () => ({
  fetchPendingMedicinePackaging: vi.fn(),
  approveMedicinePackaging: vi.fn().mockResolvedValue({}),
  rejectMedicinePackaging: vi.fn().mockResolvedValue({}),
}));

describe('packaging approvals', () => {
  it('allows approval of another person’s proposal and hides actions on one’s own', async () => {
    vi.mocked(fetchPendingMedicinePackaging).mockResolvedValue([
      { packaging: { id: 'other-pack', itemId: 'medicine-1', unit: 'PACK_3', factor: 3,
          canSplit: true, version: 1, status: 'PROPOSED' }, itemName: 'Amoxicillin',
        baseUnit: 'tablet', reason: 'Three tablets', proposedBy: 'pharmacist' },
      { packaging: { id: 'own-pack', itemId: 'medicine-2', unit: 'PACK_5', factor: 5,
          canSplit: true, version: 1, status: 'PROPOSED' }, itemName: 'Paracetamol',
        baseUnit: 'tablet', reason: 'Five tablets', proposedBy: 'reviewer' },
    ]);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={client}><PackagingApprovalsPage /></QueryClientProvider>);

    expect(await screen.findByText('Your proposal needs another reviewer')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Approve' })).toHaveLength(1);
    fireEvent.click(screen.getByRole('button', { name: 'Approve' }));
    await waitFor(() => expect(approveMedicinePackaging).toHaveBeenCalledWith('other-pack', 'test-token'));
  });

  it('shows the medicine and lets a second reviewer reject with a reason', async () => {
    vi.mocked(fetchPendingMedicinePackaging).mockResolvedValue([{
      packaging: { id: 'pack-3', itemId: 'medicine-1', unit: 'PACK_3', factor: 3,
        canSplit: true, version: 1, status: 'PROPOSED', blistersPerPack: 1, unitsPerBlister: 3 },
      itemName: 'Amoxicillin', baseUnit: 'tablet', reason: 'Manufacturer specifies three tablets',
      proposedBy: 'pharmacist',
    }]);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={client}><PackagingApprovalsPage /></QueryClientProvider>);

    expect(await screen.findByText('Amoxicillin')).toBeInTheDocument();
    expect(screen.getByText('Manufacturer specifies three tablets')).toBeInTheDocument();
    expect(screen.getByText('pharmacist')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Reject' }));
    fireEvent.change(screen.getByLabelText('Reason for rejection'),
      { target: { value: 'Package label shows four tablets' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirm rejection' }));

    await waitFor(() => expect(rejectMedicinePackaging).toHaveBeenCalledWith(
      'pack-3', 'Package label shows four tablets', 'test-token'));
  });
});
