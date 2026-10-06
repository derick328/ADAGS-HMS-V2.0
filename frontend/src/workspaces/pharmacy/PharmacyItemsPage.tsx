import { Tabs } from '../../shared/components/Tabs';
import { PageHeader } from '../../shared/components/PageHeader';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Combobox } from '../../shared/components/Combobox';
import { searchItems, type ItemSearchResultResponse } from '../../shared/api/items';
import { proposeNewItem, proposePriceChange } from '../../shared/api/pharmacyItemProposals';
import { useAuth } from '../../shared/auth/AuthProvider';

type Tab = 'newItem' | 'priceChange';

// 12-pharmacist.md §9.2: new items and price changes both go through admin
// approval — a pharmacist can never set a live price or activate an item
// directly (root CLAUDE.md "Billing": "nobody types a price"). Category
// and type are forced to MEDICINES AND CONSUMABLES / PHARMACY server-side.
export function PharmacyItemsPage() {
  const [tab, setTab] = useState<Tab>('newItem');

  return (
    <div>
      <PageHeader title={<>Pharmacy items</>} description={<>Propose a new item or a price change. Both go to admin for approval.</>} />

      <Tabs aria-label="Proposal type" variant="underline" value={tab} onChange={(value) => setTab(value as Tab)} unmountInactivePanels items={[
        { id: 'newItem', label: 'New item', content: <NewItemForm /> },
        { id: 'priceChange', label: 'Price change', content: <PriceChangeForm /> },
      ]} />

    </div>
  );
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
    <form
      onSubmit={handleSubmit((v) => submit.mutate(v))}
      noValidate
      style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', maxWidth: 560, background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)' }}
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
    <form
      onSubmit={handleSubmit((v) => submit.mutate(v))}
      noValidate
      style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', maxWidth: 560, background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)' }}
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

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: 'var(--text-sm)', marginBottom: 4 }}>{label}</label>
      {children}
      {error && <span className="field-error">{error}</span>}
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};
