import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { ClinicAssignmentFields } from './ClinicAssignmentFields';

const categories = [
  { id: 'general', code: 'GENERAL', name: 'General Clinical Departments', active: true },
  { id: 'mch', code: 'MCH', name: 'Maternal & Child Health', active: true },
];
const units = [
  { id: 'opd', facilityId: 'main', categoryId: 'general', code: 'OPD', name: 'Outpatient Department (OPD)', description: null, unitType: 'CONSULTATION', consultationEnabled: true, active: true, hodUserId: null, hodUsername: null },
  { id: 'anc', facilityId: 'main', categoryId: 'mch', code: 'ANC', name: 'Antenatal Clinic (ANC)', description: null, unitType: 'CONSULTATION', consultationEnabled: true, active: true, hodUserId: null, hodUsername: null },
];

describe('ClinicAssignmentFields', () => {
  it('restores saved primary and HOD assignments on edit', () => {
    render(<ClinicAssignmentFields categories={categories} units={units} initial={[{ departmentId: 'anc', primary: true, hod: true }]} onChange={vi.fn()} />);
    expect(screen.getByLabelText('Department category')).toHaveValue('mch');
    expect(screen.getByLabelText('Primary clinic / unit')).toHaveValue('anc');
    expect(screen.getByLabelText('Is Head of Department?')).toHaveValue('yes');
    expect(screen.getByLabelText('Head of which clinic / unit?')).toHaveValue('anc');
  });

  it('filters units by selected category and emits assigned primary unit', () => {
    const onChange = vi.fn();
    render(<ClinicAssignmentFields categories={categories} units={units} onChange={onChange} />);
    const category = screen.getByLabelText('Department category');
    const picker = screen.getByLabelText('Specific clinic / unit');
    fireEvent.change(category, { target: { value: 'mch' } });
    expect(picker).toHaveTextContent('Antenatal Clinic (ANC)');
    expect(picker).not.toHaveTextContent('Outpatient Department (OPD)');
    fireEvent.change(picker, { target: { value: 'anc' } });
    expect(onChange).toHaveBeenLastCalledWith({ unitIds: ['anc'], primaryUnitId: 'anc', hodUnitId: null });
  });
});
