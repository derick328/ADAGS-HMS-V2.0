import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useFieldArray, useForm } from 'react-hook-form';
import { z } from 'zod';
import { proposeLabItem } from '../../shared/api/labItemProposals';
import { useAuth } from '../../shared/auth/AuthProvider';

// 15-lab-technician.md §10: proposals only. Price/specimen/container/TAT
// plus parameter definitions per §5.1 — a test approved with no parameters
// still generates a valid (empty) result form. is_sensitive/verification
// level is deliberately absent: set later by CLINICAL_LEAD, never
// self-assigned by the proposing technician.
const CLASSIFICATIONS = ['HAEMATOLOGY', 'CHEMISTRY', 'MICROBIOLOGY', 'SEROLOGY', 'MICROSCOPY', 'HISTOPATHOLOGY', 'OTHERS'] as const;

const schema = z.object({
  proposedName: z.string().min(1, 'Enter a test name'),
  classification: z.enum(CLASSIFICATIONS),
  customClassification: z.string().optional(),
  proposedPriceMinor: z.coerce.number({ invalid_type_error: 'Enter a price' }).min(0, 'Enter a price'),
  notes: z.string().optional(),
  specimenType: z.string().min(1, 'Enter the expected specimen type'),
  containerType: z.string().min(1, 'Enter the container type'),
  expectedTatHours: z.coerce.number({ invalid_type_error: 'Enter the expected turnaround' }).positive('Enter a turnaround greater than zero'),
  justification: z.string().min(1, 'Enter a justification'),
  parameters: z.array(z.object({ name: z.string().min(1, 'Enter a parameter name'), unit: z.string().optional(), method: z.string().optional() })),
});
type Values = z.infer<typeof schema>;

export function LabCataloguePage() {
  const { accessToken } = useAuth();
  const {
    register,
    control,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { classification: 'HAEMATOLOGY', parameters: [] } });
  const { fields, append, remove } = useFieldArray({ control, name: 'parameters' });
  const classification = watch('classification');

  const submit = useMutation({
    mutationFn: (values: Values) =>
      proposeLabItem(
        {
          proposedName: values.proposedName,
          proposedClassification: values.classification === 'OTHERS' ? values.customClassification || 'OTHERS' : values.classification,
          proposedPriceMinor: values.proposedPriceMinor,
          notes: values.notes || undefined,
          specimenType: values.specimenType,
          containerType: values.containerType,
          expectedTatHours: values.expectedTatHours,
          justification: values.justification,
          parameters: values.parameters.map((p) => ({ name: p.name, unit: p.unit || undefined, method: p.method || undefined })),
        },
        accessToken as string,
      ),
    onSuccess: () => reset({ classification: 'HAEMATOLOGY', parameters: [] }),
  });

  return (
    <div>
      <header style={{ marginBottom: 'var(--space-4)' }}>
        <h1 style={{ margin: 0, fontSize: 'var(--text-xl)', fontWeight: 'var(--weight-medium)' }}>Lab Catalogue</h1>
        <p style={{ color: 'var(--text-secondary)', margin: '4px 0 0' }}>
          Propose a new test for the catalogue. Goes to admin for approval — pricing and sensitivity are decided there.
        </p>
      </header>

      {submit.isSuccess && (
        <div style={{ background: 'var(--green-100)', color: 'var(--green-800)', padding: 'var(--space-3)', borderRadius: 'var(--radius)', marginBottom: 'var(--space-4)', maxWidth: 640 }}>
          Proposal submitted for review.
        </div>
      )}

      <form
        onSubmit={handleSubmit((v) => submit.mutate(v))}
        noValidate
        style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', maxWidth: 640, background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)' }}
      >
        <Field label="Product name" error={errors.proposedName?.message}>
          <input {...register('proposedName')} style={inputStyle} />
        </Field>

        <Field label="Classification" error={errors.customClassification?.message}>
          <select {...register('classification')} style={inputStyle}>
            {CLASSIFICATIONS.map((c) => (
              <option key={c} value={c}>
                {c === 'OTHERS' ? 'Other' : c.charAt(0) + c.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
          {classification === 'OTHERS' && (
            <input placeholder="Specify classification" {...register('customClassification')} style={{ ...inputStyle, marginTop: 'var(--space-2)' }} />
          )}
        </Field>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-3)' }}>
          <Field label="Price (TZS)" error={errors.proposedPriceMinor?.message}>
            <input type="text" inputMode="numeric" {...register('proposedPriceMinor')} style={inputStyle} />
          </Field>
          <Field label="Expected turnaround (hours)" error={errors.expectedTatHours?.message}>
            <input type="text" inputMode="numeric" {...register('expectedTatHours')} style={inputStyle} />
          </Field>
          <Field label="Specimen type" error={errors.specimenType?.message}>
            <input {...register('specimenType')} style={inputStyle} />
          </Field>
          <Field label="Container type" error={errors.containerType?.message}>
            <input {...register('containerType')} style={inputStyle} />
          </Field>
        </div>

        <Field label="Notes">
          <textarea rows={2} {...register('notes')} style={inputStyle} />
        </Field>

        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-2)' }}>
            <span style={{ fontSize: 'var(--text-sm)', fontWeight: 'var(--weight-medium)' }}>Parameters</span>
            <button type="button" className="btn btn-secondary" onClick={() => append({ name: '', unit: '', method: '' })}>
              Add parameter
            </button>
          </div>
          {fields.length === 0 && (
            <p style={{ color: 'var(--text-muted)', fontSize: 'var(--text-sm)' }}>
              No parameters — a test approved with none generates an empty result form (manual/qualitative tests).
            </p>
          )}
          {fields.map((field, index) => (
            <div key={field.id} style={{ display: 'flex', gap: 'var(--space-2)', marginBottom: 'var(--space-2)', alignItems: 'flex-start' }}>
              <div style={{ flex: 2 }}>
                <input placeholder="Name" {...register(`parameters.${index}.name` as const)} style={inputStyle} />
                {errors.parameters?.[index]?.name && <span className="field-error">{errors.parameters[index]?.name?.message}</span>}
              </div>
              <input placeholder="Unit" {...register(`parameters.${index}.unit` as const)} style={{ ...inputStyle, flex: 1 }} />
              <input placeholder="Method" {...register(`parameters.${index}.method` as const)} style={{ ...inputStyle, flex: 1 }} />
              <button type="button" className="btn btn-ghost" onClick={() => remove(index)}>
                Remove
              </button>
            </div>
          ))}
        </div>

        <Field label="Justification" error={errors.justification?.message}>
          <textarea rows={2} {...register('justification')} style={inputStyle} />
        </Field>

        {submit.error && (
          <div className="form-error" role="alert">
            Something went wrong. Try again.
          </div>
        )}

        <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
          {submit.isPending ? 'Submitting…' : 'Submit proposal'}
        </button>
      </form>
    </div>
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
