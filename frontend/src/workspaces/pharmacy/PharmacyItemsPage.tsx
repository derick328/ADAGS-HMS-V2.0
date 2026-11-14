import { Tabs } from '../../shared/components/Tabs';
import { PageHeader } from '../../shared/components/PageHeader';
import { FormField as Field } from '../../shared/components/FormField';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Combobox } from '../../shared/components/Combobox';
import { TableRegion } from '../../shared/components/TableRegion';
import { searchItems, type ItemSearchResultResponse } from '../../shared/api/items';
import { proposeNewItem, proposePriceChange, proposeFormChange } from '../../shared/api/pharmacyItemProposals';
import { dosageForm, dosageFormLabel, dosageForms, isAutomaticForm } from '../doctor/prescriptionQuantity';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { fetchMedicinePackaging, fetchMedicinePackagingCatalogue, proposeMedicinePackaging,
  type PackagingConfigurationFilter, type MedicinePackaging, type MedicinePackagingCatalogueItem } from '../../shared/api/medicinePackaging';
import { ApiError } from '../../shared/api/client';
import './StockInPage.css';

type Tab = 'overview' | 'newItem' | 'priceChange' | 'formChange' | 'packaging';
type PackagingEdit = { item: MedicinePackagingCatalogueItem; pack: MedicinePackaging };

function cannotConfigurePackaging(option: ItemSearchResultResponse) {
  return !option.id || !option.unit || option.regulatorySchedule === 'CONTROLLED' ||
    option.regulatorySchedule === 'UNCLASSIFIED';
}

// 12-pharmacist.md §9.2: new items and price changes both go through admin
// approval — a pharmacist can never set a live price or activate an item
// directly (root CLAUDE.md "Billing": "nobody types a price"). Category
// and type are forced to MEDICINES AND CONSUMABLES / PHARMACY server-side.
export function PharmacyItemsPage() {
  const [tab, setTab] = useState<Tab>('overview');
  const [packagingEdit, setPackagingEdit] = useState<PackagingEdit | null>(null);
  const [packagingNotice, setPackagingNotice] = useState('');

  return (
    <div className="staff-page-stack pharmacy-items-page">
      <PageHeader title={<>Pharmacy items</>} description={<>View medicine packaging and propose changes for Admin approval.</>} />
      {packagingNotice && <p role="status">{packagingNotice}</p>}

      <Tabs aria-label="Proposal type" variant="underline" value={tab} onChange={(value) => setTab(value as Tab)} items={[
        { id: 'overview', label: 'Packaging overview', content: <PackagingOverview onEdit={(item, pack) => {
          setPackagingNotice('');
          setPackagingEdit({ item, pack }); setTab('packaging');
        }} /> },
        { id: 'newItem', label: 'New item', content: <NewItemForm /> },
        { id: 'priceChange', label: 'Price change', content: <PriceChangeForm /> },
        { id: 'formChange', label: 'Dosage form correction', content: <FormChangeForm /> },
        { id: 'packaging', label: 'Packaging', content: <PackagingProposalForm key={packagingEdit?.pack.id ?? 'new'}
          edit={packagingEdit} onCancelEdit={() => setPackagingEdit(null)} onEditedSubmitted={() => {
            setPackagingEdit(null);
            setPackagingNotice('Edited packaging submitted for Admin approval. The approved package stays in use until approval.');
            setTab('overview');
          }} /> },
      ]} />

    </div>
  );
}

const PACKAGING_PAGE_SIZE = 50;

function PackagingOverview({ onEdit }: { onEdit: (item: MedicinePackagingCatalogueItem, pack: MedicinePackaging) => void }) {
  const { accessToken } = useAuth();
  const canPropose = usePermission('PACKAGING_PROPOSE');
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [configuration, setConfiguration] = useState<PackagingConfigurationFilter>('CONFIGURED');
  const [page, setPage] = useState(0);
  const catalogue = useQuery({
    queryKey: ['medicine-packaging', 'catalogue', query, configuration, page],
    queryFn: () => fetchMedicinePackagingCatalogue(query, configuration, PACKAGING_PAGE_SIZE,
      page * PACKAGING_PAGE_SIZE, accessToken as string),
    enabled: !!accessToken,
  });
  return <section className="stock-in-card pharmacy-proposal-form">
    <h3>Medicine packaging catalogue</h3>
    <p>Approved package definitions are shown for each medicine. Use the filter to find medicines that still need packaging configured.</p>
    <label htmlFor="packaging-configuration-filter">Packaging status</label>
    <select id="packaging-configuration-filter" className="clinical-control" value={configuration}
      onChange={(event) => { setPage(0); setConfiguration(event.target.value as PackagingConfigurationFilter); }}>
      <option value="CONFIGURED">Configured</option>
      <option value="UNCONFIGURED">Not configured</option>
      <option value="ALL">All medicines</option>
    </select>
    <form onSubmit={(event) => { event.preventDefault(); setPage(0);
      const nextQuery = search.trim();
      if (nextQuery) setConfiguration('ALL');
      setQuery(nextQuery); }}>
      <label htmlFor="packaging-catalogue-search">Search by medicine name or code</label>
      <div className="stock-in-actions">
        <input id="packaging-catalogue-search" className="clinical-control" value={search}
          onChange={(event) => setSearch(event.target.value)} />
        <button type="submit" className="btn btn-secondary">Search</button>
      </div>
    </form>
    {catalogue.isLoading && <p role="status">Loading medicines…</p>}
    {catalogue.isError && <p role="alert">Could not load medicines. <button type="button" className="link-button" onClick={() => catalogue.refetch()}>Retry</button></p>}
    {catalogue.data && <>
      {catalogue.data.items.length === 0 ? <p>No medicines found.</p> :
        <TableRegion label="Medicine packaging catalogue"><table className="table">
          <thead><tr><th>Medicine</th><th>Base unit</th><th>Package unit</th><th>Blisters per pack</th>
            <th>Units per blister</th><th>Base units per package</th><th>Dispensing</th><th>Catalogue status</th><th>Action</th></tr></thead>
          <tbody>{catalogue.data.items.flatMap((item) =>
            (item.packages.length ? item.packages : [null]).map((pack) => <tr key={`${item.id}-${pack?.id ?? 'none'}`}>
            <td><strong>{item.name}</strong>{item.strength && ` · ${item.strength}`}{item.form && <div>{item.form}</div>}<small>{item.code}</small></td>
            <td>{item.baseUnit}</td>
            <td>{pack?.unit ?? 'No approved package'}</td>
            <td>{pack?.blistersPerPack ?? '—'}</td>
            <td>{pack?.unitsPerBlister ?? '—'}</td>
            <td>{pack?.factor ?? '—'}</td>
            <td>{pack ? (pack.canSplit ? 'Can split' : 'Whole package only') : '—'}</td>
            <td>{item.active ? 'Active' : 'Inactive'}</td>
            <td>{pack && canPropose && <button type="button" className="btn btn-secondary"
              onClick={() => onEdit(item, pack)} aria-label={`Edit ${item.name} ${pack.unit}`}>Edit</button>}</td>
          </tr>))}</tbody>
        </table></TableRegion>}
      <div className="stock-in-actions">
        <button type="button" className="btn btn-secondary" disabled={page === 0 || catalogue.isFetching}
          onClick={() => setPage(value => value - 1)}>Previous</button>
        <span>Page {page + 1}</span>
        <button type="button" className="btn btn-secondary" disabled={!catalogue.data.hasMore || catalogue.isFetching}
          onClick={() => setPage(value => value + 1)}>Next</button>
      </div>
    </>}
  </section>;
}

function PackagingProposalForm({ edit, onCancelEdit, onEditedSubmitted }: {
  edit: PackagingEdit | null;
  onCancelEdit: () => void;
  onEditedSubmitted: () => void;
}) {
  const { accessToken } = useAuth();
  const canPropose = usePermission('PACKAGING_PROPOSE');
  const queryClient = useQueryClient();
  const [item, setItem] = useState<ItemSearchResultResponse | null>(edit ? {
    id: edit.item.id, code: edit.item.code, name: edit.item.name, unit: edit.item.baseUnit,
    strength: edit.item.strength ?? undefined, form: edit.item.form,
  } : null);
  const [selectedForm, setSelectedForm] = useState<string>(dosageForm(edit?.item.form) ?? '');
  const [correctedBaseUnit, setCorrectedBaseUnit] = useState(edit?.item.baseUnit ?? '');
  const [formReason, setFormReason] = useState('');
  const [unit, setUnit] = useState(edit?.pack.unit ?? '');
  const [factor, setFactor] = useState(edit ? String(edit.pack.factor) : '');
  const [presentation, setPresentation] = useState<'BLISTER_PACK' | 'OTHER'>(
    edit ? (edit.pack.blistersPerPack != null ? 'BLISTER_PACK' : 'OTHER') : 'BLISTER_PACK');
  const [blistersPerPack, setBlistersPerPack] = useState(edit?.pack.blistersPerPack != null ? String(edit.pack.blistersPerPack) : '');
  const [unitsPerBlister, setUnitsPerBlister] = useState(edit?.pack.unitsPerBlister != null ? String(edit.pack.unitsPerBlister) : '');
  const [canSplit, setCanSplit] = useState(edit?.pack.canSplit ?? true);
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const canProposeForm = usePermission('PHARMACY_ITEM_PROPOSE');
  const presentationNeedsApproval = !!item &&
    (selectedForm !== (dosageForm(item.form) ?? '') || correctedBaseUnit.trim().toUpperCase() !== item.unit?.trim().toUpperCase());
  const displayBaseUnit = presentationNeedsApproval ? correctedBaseUnit.trim() || 'base units' : item?.unit ?? 'base units';
  const { data: approved } = useQuery({ queryKey: ['medicine-packaging', item?.id],
    queryFn: () => fetchMedicinePackaging(item?.id as string, accessToken as string),
    enabled: item?.id != null && accessToken != null });
  const blisterTotal = Number(blistersPerPack) * Number(unitsPerBlister);
  const submit = useMutation({ mutationFn: () => proposeMedicinePackaging(item?.id as string,
    presentation === 'BLISTER_PACK'
      ? { unit: `PACK_${blisterTotal}`, quantityInBaseUnits: blisterTotal, canSplit,
          blistersPerPack: Number(blistersPerPack), unitsPerBlister: Number(unitsPerBlister), reason: reason.trim(),
          replacesPackagingId: edit?.pack.id ?? undefined }
      : { unit: unit.trim().toUpperCase(), quantityInBaseUnits: Number(factor), canSplit,
          reason: reason.trim(), replacesPackagingId: edit?.pack.id ?? undefined }, accessToken as string),
    onSuccess: () => { if (!edit) { setUnit(''); setFactor(''); setBlistersPerPack(''); setUnitsPerBlister(''); setReason(''); }
      queryClient.invalidateQueries({ queryKey: ['medicine-packaging', item?.id] });
      queryClient.invalidateQueries({ queryKey: ['medicine-packaging', 'catalogue'] });
      if (edit) onEditedSubmitted(); } });
  const submitFormCorrection = useMutation({
    mutationFn: () => proposeFormChange({ itemId: item?.id as string, proposedForm: selectedForm,
      proposedUnit: correctedBaseUnit.trim().toUpperCase(), justification: formReason.trim() }, accessToken as string),
    onSuccess: () => setFormReason(''),
  });
  if (!canPropose) return <p>Packaging proposals require PACKAGING_PROPOSE permission.</p>;
  return <div className="stock-in-card pharmacy-proposal-form">
    <h3>{edit ? `Edit ${edit.item.name} packaging` : 'Configure how this medicine is packaged'}</h3>
    <p>Enter the manufacturer's package counts here. A second authorized person reviews the proposal in Admin → Packaging approvals before it appears in Stock In or dispensing.</p>
    {edit && <button type="button" className="btn btn-secondary" onClick={onCancelEdit}>Cancel edit</button>}
    {!edit && <Combobox<ItemSearchResultResponse> label="Medicine" placeholder="Search catalogue"
      search={(query) => searchItems(query, accessToken as string)} getKey={(option) => option.id as string}
      renderOption={(option) => <span>{option.name} {option.strength} {cannotConfigurePackaging(option) && ` — ${option.unselectableReason ?? 'Base unit not configured'}`}</span>}
      isOptionDisabled={cannotConfigurePackaging}
      onSelect={(selected) => {
        submitFormCorrection.reset();
        setItem(selected);
        setSelectedForm(dosageForm(selected.form) ?? '');
        setCorrectedBaseUnit(selected.unit ?? '');
        setPresentation(isAutomaticForm(selected.form) ? 'BLISTER_PACK' : 'OTHER');
        setCanSplit(isAutomaticForm(selected.form));
        setFormReason('');
        setError('');
      }} />}
    {item && <><p>Selected medicine: <strong>{item.name}{item.strength ? ` · ${item.strength}` : ''}</strong></p>
      <p>Base unit: <strong>{item.unit ?? 'Not configured'}</strong></p>
      <p>Approved packages: {(approved ?? []).map((p) => `${p.unit} = ${p.factor} ${item.unit}`).join(' · ') || 'none'}</p></>}
    <div className="stock-in-grid">
      <label>Dosage form <select className="clinical-control" value={selectedForm} onChange={(event) => {
        submitFormCorrection.reset();
        const nextForm = event.target.value;
        setSelectedForm(nextForm);
        setPresentation(isAutomaticForm(nextForm) ? 'BLISTER_PACK' : 'OTHER');
        setCanSplit(isAutomaticForm(nextForm));
        setError('');
      }} disabled={!item}>
        <option value="">Select dosage form</option>
        {dosageForms.map(form => <option key={form} value={form}>{dosageFormLabel(form)}</option>)}
      </select></label>
    </div>
    {presentationNeedsApproval && <section aria-label="Dosage form correction" className="stock-in-card">
      <p>Current catalogue presentation: <strong>{dosageFormLabel(item.form)} · {item.unit}</strong>. The selected form or base unit requires a separate Admin approved catalogue correction before packaging can be submitted for it.</p>
      <div className="stock-in-grid">
        <label>Reason and product evidence <textarea className="clinical-control" value={formReason}
          onChange={event => setFormReason(event.target.value)} /></label>
      </div>
      {submitFormCorrection.isSuccess && <p role="status">Dosage form correction submitted. Wait for Admin approval, then reselect this medicine to configure its packaging.</p>}
      {submitFormCorrection.error && <p role="alert" className="field-error">{submitFormCorrection.error instanceof ApiError
        ? submitFormCorrection.error.body?.detail ?? submitFormCorrection.error.message
        : 'Could not submit the dosage form correction.'}</p>}
      <button type="button" className="btn btn-secondary" disabled={!canProposeForm || !selectedForm || !correctedBaseUnit.trim() || !formReason.trim() || submitFormCorrection.isPending || submitFormCorrection.isSuccess}
        onClick={() => submitFormCorrection.mutate()}>Submit dosage form correction for approval</button>
      {!canProposeForm && <small>Dosage form corrections require PHARMACY_ITEM_PROPOSE permission.</small>}
    </section>}
    <div className="stock-in-grid">
      <label>Base inventory unit for selected form <input className="clinical-control" value={correctedBaseUnit}
        onChange={event => { submitFormCorrection.reset(); setCorrectedBaseUnit(event.target.value); }} disabled={!item}
        placeholder="e.g. TABLET, ML, VIAL, G" /></label>
      {item && <small>Base unit changes require a new product once packaging, stock, or prescriptions exist.</small>}
      <label>Package type <select className="clinical-control" value={presentation} onChange={(e) => setPresentation(e.target.value as 'BLISTER_PACK' | 'OTHER')}>
        <option value="BLISTER_PACK">Pack containing blisters</option><option value="OTHER">Bottle, vial, ampoule, tube, or other</option>
      </select></label>
      {presentation === 'BLISTER_PACK' ? <>
        <label>Blisters per pack <input className="clinical-control" type="number" min="1" step="1" value={blistersPerPack} onChange={(e) => setBlistersPerPack(e.target.value)} /></label>
        <label>{item ? displayBaseUnit : 'Tablets/capsules'} per blister <input className="clinical-control" type="number" min="1" step="1" value={unitsPerBlister} onChange={(e) => setUnitsPerBlister(e.target.value)} /></label>
      </> : <>
        <label>Package name <input className="clinical-control" value={unit} onChange={(e) => setUnit(e.target.value)}
          placeholder={selectedForm === 'SYRUP' ? 'BOTTLE_100' : selectedForm === 'INJECTION' ? 'VIAL_10' :
            ['CREAM', 'GEL', 'LOTION', 'PASTE'].includes(selectedForm) ? 'TUBE_30' : 'PACK_50'} /></label>
        <label>{item ? displayBaseUnit : 'Base units'} per package <input className="clinical-control" type="number" min="1" step="1" value={factor} onChange={(e) => setFactor(e.target.value)} /></label>
      </>}
      <label><input type="checkbox" checked={canSplit} onChange={(e) => setCanSplit(e.target.checked)} /> May dispense individual base units from this package</label>
      <label>Reason and manufacturer specification <textarea className="clinical-control" value={reason} onChange={(e) => setReason(e.target.value)} /></label>
    </div>
    {presentation === 'BLISTER_PACK' && <p className="stock-in-calculation" role="status">
      {blistersPerPack || '0'} blisters × {unitsPerBlister || '0'} {item ? displayBaseUnit : 'units'} per blister = <strong>{Number.isSafeInteger(blisterTotal) ? blisterTotal : 'Invalid'} {item ? displayBaseUnit : 'units'} per pack</strong>
    </p>}
    {error && <p className="field-error" role="alert">{error}</p>}
    {submit.error && <p className="field-error" role="alert">
      {submit.error instanceof ApiError
        ? submit.error.status === 403
          ? 'You do not have permission to propose packaging.'
          : submit.error.body?.detail ?? `Could not submit packaging proposal (HTTP ${submit.error.status}).`
        : 'Could not submit packaging proposal. Check your connection and try again.'}
    </p>}
    {submit.isSuccess && <p role="status">Packaging proposal submitted for approval. A separate blister unit is proposed when each blister contains more than one base unit.</p>}
    <button type="button" className="btn btn-primary" disabled={submit.isPending || presentationNeedsApproval} onClick={() => {
      const invalidBlister = presentation === 'BLISTER_PACK' &&
        (!Number.isSafeInteger(Number(blistersPerPack)) || Number(blistersPerPack) < 1 ||
         !Number.isSafeInteger(Number(unitsPerBlister)) || Number(unitsPerBlister) < 1 ||
         !Number.isSafeInteger(blisterTotal) || blisterTotal < 1);
      const invalidOther = presentation === 'OTHER' &&
        (!unit.trim() || !Number.isSafeInteger(Number(factor)) || Number(factor) < 1);
      if (!item?.id || !item.unit) { setError('Select a medicine from the search results.'); return; }
      if (!selectedForm) { setError('Select a dosage form for this medicine.'); return; }
      if (presentationNeedsApproval) { setError('Wait for the dosage form or base-unit correction to be approved before proposing packaging.'); return; }
      if (invalidBlister) { setError('Enter positive whole numbers for blisters per pack and units per blister.'); return; }
      if (invalidOther) { setError('Enter a package name and a positive whole number of base units per package.'); return; }
      if (!reason.trim()) { setError('Provide the reason and manufacturer specification.'); return; }
      setError(''); submit.mutate();
    }}>{edit ? 'Submit edited packaging' : 'Propose packaging'}</button>
  </div>;
}

const newItemSchema = z.object({
  proposedName: z.string().min(1, 'Enter an item name'),
  proposedClassification: z.string().min(1, 'Enter a classification'),
  proposedForm: z.enum(dosageForms),
  proposedUnit: z.string().min(1, 'Enter a unit'),
  proposedPriceMinor: z.coerce.number({ invalid_type_error: 'Enter a price' }).min(0, 'Enter a price'),
  justification: z.string().min(1, 'Enter a justification'),
});
type NewItemValues = z.infer<typeof newItemSchema>;

function NewItemForm() {
  const { accessToken } = useAuth();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<NewItemValues>({ resolver: zodResolver(newItemSchema) });

  const submit = useMutation({
    mutationFn: (values: NewItemValues) => proposeNewItem(values, accessToken as string),
    onSuccess: () => reset(),
  });

  return (
    <form className="pharmacy-proposal-form"
      onSubmit={handleSubmit((v) => submit.mutate(v))}
      noValidate
    >
      {submit.isSuccess && (
        <div style={{ background: 'var(--green-100)', color: 'var(--green-800)', padding: 'var(--space-3)', borderRadius: 'var(--radius)' }}>
          Proposal submitted for review.
        </div>
      )}

      <Field label="Item name" error={errors.proposedName?.message}>
        <input className="clinical-control" {...register('proposedName')} style={inputStyle} />
      </Field>
      <div className="clinical-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
        <Field label="Classification" error={errors.proposedClassification?.message}>
          <input aria-label="e.g. Antibiotic" className="clinical-control" placeholder="e.g. Antibiotic" {...register('proposedClassification')} style={inputStyle} />
        </Field>
        <Field label="Form" error={errors.proposedForm?.message}>
          <select aria-label="Dosage form" className="clinical-control" {...register('proposedForm')} style={inputStyle}>
            <option value="">Select dosage form</option>
            {dosageForms.map(form => <option key={form} value={form}>{form[0] + form.slice(1).toLowerCase()}</option>)}
          </select>
        </Field>
        <Field label="Unit" error={errors.proposedUnit?.message}>
          <input aria-label="e.g. tablet, ml" className="clinical-control" placeholder="e.g. tablet, ml" {...register('proposedUnit')} style={inputStyle} />
        </Field>
        <Field label="Proposed price (TZS minor units)" error={errors.proposedPriceMinor?.message}>
          <input className="clinical-control" type="text" inputMode="numeric" {...register('proposedPriceMinor')} style={inputStyle} />
        </Field>
      </div>
      <Field label="Justification" error={errors.justification?.message}>
        <textarea className="clinical-control" rows={2} {...register('justification')} style={inputStyle} />
      </Field>

      {submit.error && <div className="form-error">Something went wrong. Try again.</div>}

      <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
        {submit.isPending ? 'Submitting…' : 'Submit proposal'}
      </button>
    </form>
  );
}

function FormChangeForm() {
  const { accessToken } = useAuth();
  const [item, setItem] = useState<ItemSearchResultResponse | null>(null);
  const [form, setForm] = useState('');
  const [baseUnit, setBaseUnit] = useState('');
  const [reason, setReason] = useState('');
  const normalizedUnit = baseUnit.trim().toUpperCase();
  const solidUnit = isAutomaticForm(normalizedUnit);
  const unitError = !normalizedUnit || ['UNIT', '—', '-'].includes(normalizedUnit)
    ? 'Enter a specific base inventory unit, such as TABLET, ML, VIAL, or G.'
    : (isAutomaticForm(form) && normalizedUnit !== form) || (form && !isAutomaticForm(form) && solidUnit)
      ? 'Use the matching solid-dose unit, or a specific unit for this presentation such as ML, VIAL, or G.'
      : undefined;
  const unchanged = form === dosageForm(item?.form) && normalizedUnit === item?.unit?.trim().toUpperCase();
  const canSubmit = !!item?.id && !!form && !unitError && !!reason.trim() && !unchanged;
  const submit = useMutation({
    mutationFn: () => proposeFormChange({ itemId: item?.id as string, proposedForm: form,
      proposedUnit: baseUnit.trim().toUpperCase(),
      justification: reason.trim() }, accessToken as string),
    onSuccess: () => { setItem(null); setForm(''); setBaseUnit(''); setReason(''); },
  });
  return <form className="stock-in-card pharmacy-proposal-form" onSubmit={event => {
    event.preventDefault();
    if (canSubmit && !submit.isPending) submit.mutate();
  }}>
    <h3>Correct a medicine dosage form</h3>
    <p>A second authorized person reviews this correction. Existing prescriptions retain their original form.</p>
    <Combobox<ItemSearchResultResponse> label="Medicine" placeholder="Search catalogue"
      search={query => searchItems(query, accessToken as string)} getKey={option => option.id as string}
      renderOption={option => <span>{option.name} · {dosageForm(option.form) ? dosageFormLabel(option.form) : 'Form not configured'} · {option.strength ?? ''}</span>}
      isOptionDisabled={option => !option.id || option.regulatorySchedule === 'CONTROLLED'}
      onSelect={selected => {
        submit.reset();
        setItem(selected); setForm(''); setReason('');
        const unit = selected.unit?.trim().toUpperCase() ?? '';
        setBaseUnit(['UNIT', '—', '-'].includes(unit) ? '' : unit);
      }} />
    {item && <>
      <p>Selected medicine: <strong>{item.name}{item.strength ? ` · ${item.strength}` : ''}</strong>{item.code && ` (${item.code})`}</p>
      <p>Current dosage form: <strong>{dosageForm(item.form) ? dosageFormLabel(item.form) : 'Not configured'}</strong></p>
    </>}
    <Field label="Proposed dosage form">
      <select className="clinical-control" value={form} onChange={event => {
        submit.reset();
        const nextForm = event.target.value;
        setForm(nextForm);
        if (isAutomaticForm(nextForm)) setBaseUnit(nextForm);
        else if (isAutomaticForm(baseUnit)) setBaseUnit('');
      }} style={inputStyle}>
        <option value="">Select dosage form</option>
        {dosageForms.map(value => <option key={value} value={value}>{value[0] + value.slice(1).toLowerCase()}</option>)}
      </select>
    </Field>
    <Field label="Base inventory unit for this presentation" error={item && form ? unitError : undefined}>
      <input className="clinical-control" value={baseUnit} onChange={event => { submit.reset(); setBaseUnit(event.target.value); }} placeholder="e.g. TABLET, ML, VIAL, G" style={inputStyle} />
      <small>Changing the base unit is allowed only before packaging, stock, or prescriptions exist.</small>
    </Field>
    <Field label="Reason and product evidence">
      <textarea className="clinical-control" value={reason} onChange={event => setReason(event.target.value)} style={inputStyle} />
    </Field>
    {submit.isSuccess && <p role="status">Dosage form correction sent for Admin review.</p>}
    {submit.isError && <p role="alert" className="field-error">{submit.error instanceof ApiError ? submit.error.message : 'Could not submit the correction.'}</p>}
    <button className="btn btn-primary" disabled={submit.isPending || !canSubmit}>
      Submit for approval
    </button>
  </form>;
}

const priceChangeSchema = z.object({
  itemId: z.string().min(1, 'Select an item'),
  proposedPriceMinor: z.coerce.number({ invalid_type_error: 'Enter a price' }).min(0, 'Enter a price'),
  justification: z.string().min(1, 'Enter a justification — required so the approving admin has something to approve on'),
});
type PriceChangeValues = z.infer<typeof priceChangeSchema>;

function PriceChangeForm() {
  const { accessToken } = useAuth();
  const [selectedItem, setSelectedItem] = useState<ItemSearchResultResponse | null>(null);
  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors },
  } = useForm<PriceChangeValues>({ resolver: zodResolver(priceChangeSchema) });

  const submit = useMutation({
    mutationFn: (values: PriceChangeValues) => proposePriceChange(values, accessToken as string),
    onSuccess: () => {
      reset();
      setSelectedItem(null);
    },
  });

  return (
    <form className="pharmacy-proposal-form"
      onSubmit={handleSubmit((v) => submit.mutate(v))}
      noValidate
    >
      {submit.isSuccess && (
        <div style={{ background: 'var(--green-100)', color: 'var(--green-800)', padding: 'var(--space-3)', borderRadius: 'var(--radius)' }}>
          Proposal submitted for review.
        </div>
      )}

      <div>
        <Combobox<ItemSearchResultResponse>
          label="Item"
          placeholder="Search the formulary"
          search={(q) => searchItems(q, accessToken as string)}
          getKey={(i) => i.id as string}
          renderOption={(i) => (
            <span>
              {i.name} {i.strength && <span style={{ color: 'var(--text-muted)' }}>· {i.strength}</span>}
            </span>
          )}
          onSelect={(i) => {
            setSelectedItem(i);
            setValue('itemId', i.id as string, { shouldValidate: true });
          }}
          clearOnSelect={false}
        />
        {selectedItem && <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginTop: 4 }}>Selected: {selectedItem.name}</div>}
        {errors.itemId && <span className="field-error">{errors.itemId.message}</span>}
      </div>

      <Field label="Proposed price (TZS minor units)" error={errors.proposedPriceMinor?.message}>
        <input className="clinical-control" type="text" inputMode="numeric" {...register('proposedPriceMinor')} style={inputStyle} />
      </Field>
      <Field label="Justification" error={errors.justification?.message}>
        <textarea className="clinical-control" rows={2} {...register('justification')} style={inputStyle} />
      </Field>

      {submit.error && <div className="form-error">Something went wrong. Try again.</div>}

      <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
        {submit.isPending ? 'Submitting…' : 'Submit proposal'}
      </button>
    </form>
  );
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};
