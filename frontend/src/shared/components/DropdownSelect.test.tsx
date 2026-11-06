import { fireEvent, render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { DropdownSelect } from './DropdownSelect';

it('supports keyboard selection and keeps disabled choices unavailable', () => {
  const onChange = vi.fn();
  render(<DropdownSelect label="Disposition" value="" onChange={onChange} options={[
    { value: '', label: 'Select disposition' },
    { value: 'DISCHARGE', label: 'Discharge' },
    { value: 'ADMIT', label: 'Admit', disabled: true },
    { value: 'REFER', label: 'Refer' },
  ]} />);

  const trigger = screen.getByRole('combobox', { name: /disposition/i });
  fireEvent.keyDown(trigger, { key: 'ArrowDown' });
  expect(trigger).toHaveAttribute('aria-expanded', 'true');
  fireEvent.keyDown(trigger, { key: 'ArrowDown' });
  fireEvent.keyDown(trigger, { key: 'ArrowDown' });
  fireEvent.keyDown(trigger, { key: 'Enter' });
  expect(onChange).toHaveBeenCalledWith('REFER');
  expect(trigger).toHaveAttribute('aria-expanded', 'false');
});
