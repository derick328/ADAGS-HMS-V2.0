import { Tabs } from '../../shared/components/Tabs';
import { PageHeader } from '../../shared/components/PageHeader';
import { FormField as Field } from '../../shared/components/FormField';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Combobox } from '../../shared/components/Combobox';
import { searchItems, type ItemSearchResultResponse } from '../../shared/api/items';
import { proposeNewItem, proposePriceChange } from '../../shared/api/pharmacyItemProposals';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { fetchMedicinePackaging, proposeMedicinePackaging } from '../../shared/api/medicinePackaging';
import './StockInPage.css';

type Tab = 'newItem' | 'priceChange' | 'packaging';

// 12-pharmacist.md §9.2: new items and price changes both go through admin
// approval — a pharmacist can never set a live price or activate an item
// directly (root CLAUDE.md "Billing": "nobody types a price"). Category
// and type are forced to MEDICINES AND CONSUMABLES / PHARMACY server-side.
export function PharmacyItemsPage() {
  const [tab, setTab] = useState<Tab>('newItem');

  return (
    <div className="staff-page-stack pharmacy-items-page">
      <PageHeader title={<>Pharmacy items</>} description={<>Propose a new item or a price change. Both go to admin for approval.</>} />

      <Tabs aria-label="Proposal type" variant="underline" value={tab} onChange={(value) => setTab(value as Tab)} items={[
        { id: 'newItem', label: 'New item', content: <NewItemForm /> },
        { id: 'priceChange', label: 'Price change', content: <PriceChangeForm /> },
        { id: 'packaging', label: 'Packaging', content: <PackagingProposalForm /> },
      ]} />

    </div>
  );
}

function PackagingProposalForm() {
  const { accessToken } = useAuth();
  const canPropose = usePermission('PACKAGING_PROPOSE');
  const queryClient = useQueryClient();
  const [item, setItem] = useState<ItemSearchResultResponse | null>(null);
  const [unit, setUnit] = useState('');
  const [factor, setFactor] = useState('');
  const [canSplit, setCanSplit] = useState(true);
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const { data: approved } = useQuery({ queryKey: ['medicine-packaging', item?.id],
    queryFn: () => fetchMedicinePackaging(item?.id as string, accessToken as string),
    enabled: item?.id != null && accessToken != null });
  const submit = useMutation({ mutationFn: () => proposeMedicinePackaging(item?.id as string,
    { unit: unit.trim().toUpperCase(), quantityInBaseUnits: Number(factor), canSplit,
      reason: reason.trim() }, accessToken as string),
    onSuccess: () => { setUnit(''); setFactor(''); setReason('');
      queryClient.invalidateQueries({ queryKey: ['medicine-packaging', item?.id] }); } });
  if (!canPropose) return <p>Packaging proposals require PACKAGING_PROPOSE permission.</p>;
  return <div className="stock-in-card pharmacy-proposal-form">
    <p>Conversions are specific to each catalogue product. An authorized second person approves a proposal before it can be used.</p>
    <Combobox<ItemSearchResultResponse> label="Medicine" placeholder="Search catalogue"
      search={(query) => searchItems(query, accessToken as string)} getKey={(option) => option.id as string}
      renderOption={(option) => <span>{option.name} {option.strength}</span>}
      onSelect={setItem} />
    {item && <><p>Base unit: <strong>{item.unit ?? 'Not configured'}</strong></p>
      <p>Approved packages: {(approved ?? []).map((p) => `${p.unit} = ${p.factor} ${item.unit}`).join(' · ') || 'none'}</p></>}
    <div className="stock-in-grid">
      <label>Packaging unit <input className="clinical-control" value={unit} onChange={(e) => setUnit(e.target.value)} placeholder="BLISTER, PACK, BOX…" /></label>
      <label>Base units per package <input className="clinical-control" type="number" min="2" value={factor} onChange={(e) => setFactor(e.target.value)} /></label>
      <label><input type="checkbox" checked={canSplit} onChange={(e) => setCanSplit(e.target.checked)} /> May dispense individual base units from this package</label>
      <label>Reason and manufacturer specification <textarea className="clinical-control" value={reason} onChange={(e) => setReason(e.target.value)} /></label>
    </div>
    {error && <p className="field-error" role="alert">{error}</p>}
    {submit.error && <p className="field-error" role="alert">Could not submit packaging proposal.</p>}
    {submit.isSuccess && <p role="status">Packaging proposal submitted for approval.</p>}
    <button type="button" className="btn btn-primary" disabled={submit.isPending} onClick={() => {
      if (!item?.unit || !unit.trim() || !Number.isSafeInteger(Number(factor)) || Number(factor) <= 1 || !reason.trim()) {
        setError('Select a medicine and enter a package unit, whole conversion factor, and reason.'); return;
      }
      setError(''); submit.mutate();
    }}>Propose packaging</button>
  </div>;
}

const newItemSchema = z.object({
  proposedName: z.string().min(1, 'Enter an item name'),
  proposedClassification: z.string().min(1, 'Enter a classification'),
  proposedForm: z.string().min(1, 'Enter a form (e.g. tablet, capsule, injection)'),
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
          <input aria-label="e.g. Tablet" className="clinical-control" placeholder="e.g. Tablet" {...register('proposedForm')} style={inputStyle} />
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
