import { fireEvent, render, screen, waitFor, cleanup } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AcceptSamplePage } from './AcceptSamplePage';
import { fetchLabOrderQueue, type LabOrderQueueRowResponse } from '../../shared/api/labOrders';
import { collectSpecimen, type SpecimenResponse } from '../../shared/api/specimens';

const navigate = vi.fn();
vi.mock('../../shared/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'test' }) }));
vi.mock('react-router-dom', () => ({ useNavigate: () => navigate, useParams: () => ({ labOrderId: 'order-1' }) }));
vi.mock('../../shared/api/labOrders', () => ({ fetchLabOrderQueue: vi.fn() }));
vi.mock('../../shared/api/specimens', () => ({ collectSpecimen: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });

function show() {
  vi.mocked(fetchLabOrderQueue).mockResolvedValue([{
    labOrderId: 'order-1', patientName: 'Asha Juma', labTestName: 'Full blood count',
    specimenType: 'BLOOD', containerType: 'EDTA_TUBE',
  } as LabOrderQueueRowResponse]);
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <AcceptSamplePage />
  </QueryClientProvider>);
}

describe('sample acceptance handoff', () => {
  it('goes straight to result entry after an adequate sample is accepted', async () => {
    vi.mocked(collectSpecimen).mockResolvedValue({ status: 'IN_PROGRESS', accessionNo: 'ACC-1' } as SpecimenResponse);
    show();
    fireEvent.click(await screen.findByRole('button', { name: 'Accept & Enter Result' }));
    await waitFor(() => expect(collectSpecimen).toHaveBeenCalledWith(expect.objectContaining({
      labOrderId: 'order-1', sampleType: 'BLOOD', quality: 'ADEQUATE',
    }), 'test'));
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/lab/result-entry/order-1', { replace: true }));
    expect(screen.queryByText('Sample accepted')).not.toBeInTheDocument();
  });

  it('asks for a reason when the assessed sample type differs from the catalogue', async () => {
    show();
    const sampleType = await screen.findByLabelText('Assessed sample type');
    fireEvent.change(sampleType, { target: { value: 'Whole Blood (EDTA)' } });
    expect(screen.getByLabelText('Reason for using a different sample type')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Accept & Enter Result' }));
    expect(await screen.findByText('Explain why the sample type differs from the catalogue')).toBeInTheDocument();
    expect(collectSpecimen).not.toHaveBeenCalled();
  });

  it('records a quality issue and keeps a rejected specimen out of result entry', async () => {
    vi.mocked(collectSpecimen).mockResolvedValue({ status: 'REJECTED', rejectionReason: 'Clotted specimen' } as SpecimenResponse);
    show();
    fireEvent.change(await screen.findByLabelText('Quality'), { target: { value: 'CLOTTED' } });
    fireEvent.change(screen.getByLabelText('Quality note'), { target: { value: 'Clotted on arrival' } });
    fireEvent.click(screen.getByRole('button', { name: 'Reject sample' }));
    await waitFor(() => expect(collectSpecimen).toHaveBeenCalledWith(expect.objectContaining({
      sampleType: 'BLOOD', quality: 'CLOTTED', qualityNote: 'Clotted on arrival',
    }), 'test'));
    expect(await screen.findByText('Sample rejected')).toBeInTheDocument();
    expect(navigate).not.toHaveBeenCalledWith('/lab/result-entry/order-1', expect.anything());
  });
});
