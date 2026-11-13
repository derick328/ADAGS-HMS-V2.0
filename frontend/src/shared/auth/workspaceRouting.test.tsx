import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { HomeRedirect } from './HomeRedirect';
import { landingRouteFor } from './landingRoute';
import { RequireAdmin } from './RequireAdmin';
import { useMe } from './useMe';

vi.mock('./useMe', () => ({ useMe: vi.fn() }));

function mockMe(roleCodes: string[], permissions: string[] = []) {
  vi.mocked(useMe).mockReturnValue({
    data: {
      userId: 'test-user',
      username: 'clinician',
      mustChangePassword: false,
      scopes: roleCodes.map((roleCode) => ({ roleCode, scopeType: 'FACILITY' })),
      permissions,
    },
    isLoading: false,
    refetch: vi.fn(),
  } as unknown as ReturnType<typeof useMe>);
}

describe('workspace routing', () => {
  it('sends an active clinical officer to the doctor workspace', () => {
    mockMe(['CLINICAL_OFFICER'], ['CONSULTATION_CREATE']);
    render(<MemoryRouter initialEntries={['/']}><Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route path="/doctor" element={<p>Doctor workspace</p>} />
    </Routes></MemoryRouter>);
    expect(screen.getByText('Doctor workspace')).toBeInTheDocument();
  });

  it('redirects a clinical officer away from the admin shell', () => {
    mockMe(['CLINICAL_OFFICER'], ['CONSULTATION_CREATE']);
    render(<MemoryRouter initialEntries={['/admin']}><Routes>
      <Route path="/admin" element={<RequireAdmin><p>Admin workspace</p></RequireAdmin>} />
      <Route path="/" element={<HomeRedirect />} />
      <Route path="/doctor" element={<p>Doctor workspace</p>} />
    </Routes></MemoryRouter>);
    expect(screen.getByText('Doctor workspace')).toBeInTheDocument();
    expect(screen.queryByText('Admin workspace')).not.toBeInTheDocument();
  });

  it('does not send an account without a role to the admin shell', () => {
    expect(landingRouteFor([], [])).toBe('/access-pending');
    expect(landingRouteFor(['ADMIN'], ['USER_MANAGE'])).toBe('/admin');
    expect(landingRouteFor(['ACCOUNTANT'], ['PACKAGING_APPROVE'])).toBe('/admin/packaging-approvals');
    expect(landingRouteFor(['ACCOUNTANT'], ['PACKAGING_APPROVE', 'PAYMENT_CREATE'])).toBe('/admin/packaging-approvals');
  });

  it('allows an authorized packaging reviewer into the admin shell', () => {
    mockMe(['ACCOUNTANT'], ['PACKAGING_APPROVE']);
    render(<MemoryRouter initialEntries={['/admin']}><Routes>
      <Route path="/admin" element={<RequireAdmin><p>Admin workspace</p></RequireAdmin>} />
    </Routes></MemoryRouter>);
    expect(screen.getByText('Admin workspace')).toBeInTheDocument();
  });
});
