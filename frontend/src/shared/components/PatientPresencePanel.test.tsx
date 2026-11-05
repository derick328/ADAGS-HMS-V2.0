import { render, screen, cleanup } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PatientPresencePanel } from './PatientPresencePanel';
import { fetchPresence } from '../api/presence';
vi.mock('../auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'test' }) }));
vi.mock('../auth/useFacilityScope', () => ({ useAdminFacilityId: () => 'facility' }));
vi.mock('../auth/usePermission', () => ({ usePermission: () => false }));
vi.mock('../api/presence', () => ({ fetchPresence: vi.fn(), fetchVisitSummaries: vi.fn(), confirmDeparture: vi.fn(), correctDeparture: vi.fn() }));
afterEach(cleanup);
function show() { render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><PatientPresencePanel /></QueryClientProvider>); }
describe('administrative presence', () => {
 it.each([[1, 'Awaiting triage', 'First visit'], [3, 'Doctor consultation', 'Revisit · 3'], [2, 'Surgery in progress', 'Revisit · 2'], [4, 'Ward care · General A', 'Revisit · 4']])('shows backend visit %s and stage', async (visitNumber, stage, label) => {
  vi.mocked(fetchPresence).mockResolvedValue([{ encounterId: 'encounter', patientName: 'Example', hospitalNo: 'H1', stage: String(stage), visitNumber: Number(visitNumber), presence: 'Receiving care — expected present' }]);
  show(); expect(await screen.findByText(String(stage))).toBeInTheDocument(); expect(screen.getByText(String(label))).toBeInTheDocument();
  expect(screen.getByText(/Departure unconfirmed/)).toBeInTheDocument(); expect(screen.queryByRole('button', { name: 'Confirm departure' })).not.toBeInTheDocument();
  expect(screen.queryByText(/diagnosis|medication|laboratory result/i)).not.toBeInTheDocument();
 });
 it('does not report an API failure as an empty facility', async () => {
  vi.mocked(fetchPresence).mockRejectedValue(new Error('denied')); show();
  expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded'); expect(screen.queryByText('No matching encounters.')).not.toBeInTheDocument();
 });
});
