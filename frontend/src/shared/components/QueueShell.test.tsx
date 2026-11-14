import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { QueueShell } from './QueueShell';

describe('shared patient queue layout', () => {
  it('keeps patient details, waiting time, emergency marker and action in distinct cells', () => {
    const open = vi.fn();
    render(<div className="app-content staff-workspace"><QueueShell title="Waiting" emptyMessage="No patients waiting" rows={[{
      id: 'patient-1', isEmergency: true,
      primary: 'Elson Gabriel Kisimbo', secondary: 'MAIN-2026-000013', meta: '73h 50m',
      action: <button type="button" className="btn btn-primary" onClick={open}>Continue consultation</button>,
    }]} /></div>);

    const row = screen.getByRole('row');
    expect(row).toHaveClass('row-critical');
    expect(within(row).getByLabelText('Emergency')).toBeVisible();
    expect(within(row).getByText('Elson Gabriel Kisimbo').closest('td')).toHaveClass('queue-details');
    expect(within(row).getByText('73h 50m').closest('td')).toHaveClass('queue-waiting');
    const action = within(row).getByRole('button', { name: 'Continue consultation' });
    expect(action.closest('td')).toHaveClass('queue-action');
    fireEvent.click(action);
    expect(open).toHaveBeenCalledOnce();
  });

  it('keeps long labels available to keyboard users', () => {
    render(<div className="app-content staff-workspace"><QueueShell title="Waiting" emptyMessage="No patients waiting" rows={[{
      id: 'patient-2', primary: 'Patient With An Exceptionally Long Name That Needs To Wrap',
      action: <button type="button" className="btn btn-secondary">View prescription details</button>,
    }]} /></div>);
    const action = screen.getByRole('button', { name: 'View prescription details' });
    action.focus();
    expect(action).toHaveFocus();
    expect(screen.getByText('Patient With An Exceptionally Long Name That Needs To Wrap')).toBeVisible();
  });
});
