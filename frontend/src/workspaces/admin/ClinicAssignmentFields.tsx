import { useEffect, useState } from 'react';
import type { ClinicAssignment, ClinicUnit, DepartmentCategory } from '../../shared/api/departments';

export function ClinicAssignmentFields({ categories, units, initial, onChange }: {
  categories: DepartmentCategory[]; units: ClinicUnit[]; initial?: ClinicAssignment[];
  onChange: (value: { unitIds: string[]; primaryUnitId: string | null; hodUnitId: string | null }) => void;
}) {
  const [categoryId, setCategoryId] = useState(units.find(u => u.id === (initial?.find(a => a.primary)?.departmentId ?? initial?.[0]?.departmentId))?.categoryId ?? '');
  const [unitIds, setUnitIds] = useState<string[]>(initial?.map(a => a.departmentId) ?? []);
  const [primaryUnitId, setPrimary] = useState(initial?.find(a => a.primary)?.departmentId ?? '');
  const [hodUnitId, setHod] = useState(initial?.find(a => a.hod)?.departmentId ?? '');
  useEffect(() => { onChange({ unitIds, primaryUnitId: primaryUnitId || null, hodUnitId: hodUnitId || null }); }, [unitIds, primaryUnitId, hodUnitId, onChange]);
  const active = units.filter(u => u.active);
  const available = active.filter(u => u.categoryId === categoryId);
  return <fieldset className="clinic-assignment-fields">
    <legend>Department assignment</legend>
    <label>Department category<select className="clinical-control" value={categoryId} onChange={e => setCategoryId(e.target.value)}>
      <option value="">Choose category…</option>{categories.filter(c => c.active).map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
    </select></label>
    <label>Specific clinic / unit<select className="clinical-control" value="" onChange={e => { if (e.target.value && !unitIds.includes(e.target.value)) { setUnitIds([...unitIds, e.target.value]); if (!primaryUnitId) setPrimary(e.target.value); } }} disabled={!categoryId}>
      <option value="">Add clinic / unit…</option>{available.filter(u => !unitIds.includes(u.id)).map(u => <option key={u.id} value={u.id}>{u.name}</option>)}
    </select></label>
    <div aria-label="Assigned clinics">{unitIds.map(id => { const unit = units.find(u => u.id === id); return <div key={id} className="clinic-assignment-row"><span>{unit?.name ?? id}{initial?.some(a => a.departmentId === id && a.hod) ? ' · Current HOD' : ''}</span><button type="button" className="btn btn-secondary" onClick={() => { setUnitIds(unitIds.filter(v => v !== id)); if (primaryUnitId === id) setPrimary(unitIds.find(v => v !== id) ?? ''); if (hodUnitId === id) setHod(''); }} disabled={initial?.some(a => a.departmentId === id && a.hod)}>Remove</button></div>; })}</div>
    {unitIds.length > 0 && <><label>Primary clinic / unit<select className="clinical-control" value={primaryUnitId} onChange={e => setPrimary(e.target.value)}><option value="">Choose primary…</option>{unitIds.map(id => <option key={id} value={id}>{units.find(u => u.id === id)?.name ?? id}</option>)}</select></label>
      <label>Is Head of Department?<select className="clinical-control" value={hodUnitId ? 'yes' : 'no'} onChange={e => setHod(e.target.value === 'yes' ? primaryUnitId : '')}><option value="no">No</option><option value="yes">Yes</option></select></label>
      {hodUnitId && <label>Head of which clinic / unit?<select className="clinical-control" value={hodUnitId} onChange={e => setHod(e.target.value)}>{unitIds.map(id => <option key={id} value={id}>{units.find(u => u.id === id)?.name ?? id}</option>)}</select></label>}</>}
  </fieldset>;
}
