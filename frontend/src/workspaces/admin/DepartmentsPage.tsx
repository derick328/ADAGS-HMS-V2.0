import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { PageHeader } from '../../shared/components/PageHeader';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';
import { appointHod, createClinic, fetchClinicCategories, fetchClinicStaff, fetchManagedClinics, updateClinic, vacateHod, type ClinicUnit } from '../../shared/api/departments';
import './DepartmentsPage.css';

export function DepartmentsPage() {
  const { accessToken } = useAuth(); const token = accessToken as string; const { data: me } = useMe();
  const cache = useQueryClient(); const categories = useQuery({ queryKey: ['clinic-categories'], queryFn: () => fetchClinicCategories(token), enabled: !!token });
  const clinics = useQuery({ queryKey: ['managed-clinics'], queryFn: () => fetchManagedClinics(token), enabled: !!token });
  const [search, setSearch] = useState(''); const [categoryFilter, setCategoryFilter] = useState(''); const [expanded, setExpanded] = useState<string[]>([]);
  const [create, setCreate] = useState(false);
  const facilityId = me?.scopes?.find(s => s.scopeType === 'FACILITY')?.scopeId ?? '00000000-0000-0000-0000-000000000001';
  const refresh = () => { cache.invalidateQueries({ queryKey: ['managed-clinics'] }); cache.invalidateQueries({ queryKey: ['clinic-catalogue'] }); };
  const add = useMutation({ mutationFn: (value: Record<string, unknown>) => createClinic({ ...value, facilityId }, token), onSuccess: () => { refresh(); setCreate(false); } });
  return <div className="departments-page"><PageHeader title="Clinical departments & clinics" description="Manage service units, assigned staff, and heads of department." />
    <div className="clinical-filter-bar"><label>Search clinics<input className="clinical-control" value={search} onChange={e => setSearch(e.target.value)} placeholder="Name or code" /></label><label>Category<select className="clinical-control" value={categoryFilter} onChange={e => setCategoryFilter(e.target.value)}><option value="">All categories</option>{categories.data?.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label><button type="button" className="btn btn-primary" onClick={() => setCreate(!create)}>{create ? 'Cancel' : 'Add clinic'}</button></div>
    {create && <ClinicEditor categories={categories.data ?? []} pending={add.isPending} error={add.error?.message} onSave={v => add.mutate(v)} />}
    {clinics.isLoading || categories.isLoading ? <p role="status">Loading departments…</p> : clinics.isError || categories.isError ? <p role="alert">Departments could not be loaded.</p> : categories.data?.map(category => {
      if (categoryFilter && categoryFilter !== category.id) return null;
      const rows = (clinics.data ?? []).filter(u => u.categoryId === category.id && `${u.name} ${u.code}`.toLowerCase().includes(search.toLowerCase()));
      const open = expanded.includes(category.id) || !!search || !!categoryFilter;
      return <section className="department-category" key={category.id}><button type="button" className="department-category-toggle" aria-expanded={open} onClick={() => setExpanded(open ? expanded.filter(id => id !== category.id) : [...expanded, category.id])}>{category.name} <span>{rows.length} units {open ? '▾' : '▸'}</span></button>
        {open && (rows.length ? <div className="department-units">{rows.map(unit => <ClinicCard key={unit.id} unit={unit} token={token} refresh={refresh} />)}</div> : <p className="department-empty">No clinics match this filter.</p>)}
      </section>;
    })}
  </div>;
}

function ClinicCard({ unit, token, refresh }: { unit: ClinicUnit; token: string; refresh: () => void }) {
  const [editing, setEditing] = useState(false); const [showStaff, setShowStaff] = useState(false); const [choice, setChoice] = useState('');
  const staff = useQuery({ queryKey: ['clinic-staff', unit.id], queryFn: () => fetchClinicStaff(unit.id, token), enabled: showStaff || editing });
  const save = useMutation({ mutationFn: (v: Record<string, unknown>) => updateClinic(unit.id, v, token), onSuccess: () => { refresh(); setEditing(false); } });
  const hod = useMutation({ mutationFn: (id: string) => id ? appointHod(unit.id, id, token) : vacateHod(unit.id, token), onSuccess: refresh });
  return <article className="department-card"><div className="department-card-head"><div><h3>{unit.name}</h3><span className="badge badge-neutral">{unit.unitType.toLowerCase()}</span> <span className={unit.active ? 'badge badge-success' : 'badge badge-warning'}>{unit.active ? 'Active' : 'Inactive'}</span></div><div><strong>{unit.hodUsername ?? 'HOD Not Assigned'}</strong></div></div>
    {unit.description && <p>{unit.description}</p>}
    <div className="department-card-actions"><button type="button" className="btn btn-secondary" onClick={() => setShowStaff(!showStaff)} aria-expanded={showStaff}>Assigned staff</button><button type="button" className="btn btn-secondary" onClick={() => { setEditing(!editing); setShowStaff(true); }}>{editing ? 'Cancel edit' : 'Edit clinic'}</button></div>
    {showStaff && (staff.isLoading ? <p role="status">Loading staff…</p> : staff.data?.length ? <ul>{staff.data.map(s => <li key={s.userId}>{s.username}{s.primary ? ' · primary' : ''}{s.active ? '' : ' · inactive'}</li>)}</ul> : <p>No staff assigned.</p>)}
    {editing && <><ClinicEditor unit={unit} pending={save.isPending} error={save.error?.message} onSave={v => save.mutate(v)} /><div className="department-hod-controls"><label>Head of Department<select className="clinical-control" value={choice} onChange={e => setChoice(e.target.value)}><option value="">Choose assigned staff…</option>{staff.data?.filter(s => s.active).map(s => <option key={s.userId} value={s.userId}>{s.username}</option>)}</select></label><button type="button" className="btn btn-primary" disabled={!choice || hod.isPending} onClick={() => { if (!unit.hodUserId || window.confirm(`Replace ${unit.hodUsername} as HOD? The previous appointment remains in history.`)) hod.mutate(choice); }}>Appoint HOD</button>{unit.hodUserId && <button type="button" className="btn btn-secondary" disabled={hod.isPending} onClick={() => { if (window.confirm(`Leave ${unit.name} without a HOD?`)) hod.mutate(''); }}>Record vacancy</button>}</div>{hod.error && <p className="form-error" role="alert">{hod.error.message}</p>}</>}
  </article>;
}

function ClinicEditor({ categories, unit, pending, error, onSave }: { categories?: { id: string; name: string }[]; unit?: ClinicUnit; pending: boolean; error?: string; onSave: (v: Record<string, unknown>) => void }) {
  const [categoryId, setCategory] = useState(''); const [code, setCode] = useState(''); const [name, setName] = useState(unit?.name ?? ''); const [description, setDescription] = useState(unit?.description ?? ''); const [unitType, setType] = useState(unit?.unitType ?? 'CONSULTATION'); const [consultationEnabled, setConsultation] = useState(unit?.consultationEnabled ?? true); const [active, setActive] = useState(unit?.active ?? true);
  return <form className="clinic-editor" onSubmit={e => { e.preventDefault(); onSave(unit ? { name, description, unitType, consultationEnabled, active } : { categoryId, code, name, description, unitType, consultationEnabled }); }}>
    {!unit && <><label>Category<select required className="clinical-control" value={categoryId} onChange={e => setCategory(e.target.value)}><option value="">Choose category…</option>{categories?.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label><label>Code<input required className="clinical-control" value={code} onChange={e => setCode(e.target.value)} /></label></>}
    <label>Name<input required className="clinical-control" value={name} onChange={e => setName(e.target.value)} /></label><label>Description<textarea className="clinical-control" value={description} onChange={e => setDescription(e.target.value)} /></label>
    <label>Unit type<select className="clinical-control" value={unitType} onChange={e => { setType(e.target.value); setConsultation(e.target.value === 'CONSULTATION'); }}>{['CONSULTATION','WARD','DIAGNOSTIC','PHARMACY','THEATRE','SUPPORT'].map(t => <option key={t} value={t}>{t}</option>)}</select></label><label><input type="checkbox" checked={consultationEnabled} disabled={unitType !== 'CONSULTATION'} onChange={e => setConsultation(e.target.checked)} /> Doctor consultation clinic</label>{unit && <label><input type="checkbox" checked={active} onChange={e => setActive(e.target.checked)} /> Active</label>}
    {error && <p className="form-error" role="alert">{error}</p>}<button type="submit" className="btn btn-primary" disabled={pending}>{pending ? 'Saving…' : unit ? 'Save clinic' : 'Create clinic'}</button>
  </form>;
}
