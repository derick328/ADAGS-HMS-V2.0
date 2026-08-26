import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Combobox } from '../../shared/components/Combobox';
import { fetchItemById, searchItems, type ItemSearchResultResponse } from '../../shared/api/items';
import {
  attestProcedureItem,
  fetchProcedureItems,
  prepareProcedureItem,
  reconcileProcedureItem,
  type ProcedureItemResponse,
  type ProcedurePhase,
} from '../../shared/api/procedureItems';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useMe } from '../../shared/auth/useMe';

const PHASES: ProcedurePhase[] = ['PRE_OP', 'INTRA_OP', 'POST_OP'];

// 04-procedure-bedday-rules.md §2: prepared vs used, priced from the
// catalogue by itemId — never typed. Intra-op items are necessarily
// deferred (the system infers it from phase, nobody marks it) — nothing
// in this screen needs to know that; it only prepares/reconciles/attests.
export function SurgeryEpisodeProcedureItems({ surgeryOrderId }: { surgeryOrderId: string }) {
  const { accessToken } = useAuth();
  const [showPrepareForm, setShowPrepareForm] = useState(false);
  const { data, isLoading } = useQuery({
    queryKey: ['procedure-items', surgeryOrderId],
    queryFn: () => fetchProcedureItems(surgeryOrderId, accessToken as string),
    enabled: accessToken != null,
  });
  const rows = data ?? [];

  return (
    <div>
      <button type="button" className="btn btn-primary" style={{ marginBottom: 'var(--space-3)' }} onClick={() => setShowPrepareForm((v) => !v)}>
        {showPrepareForm ? 'Cancel' : 'Prepare an item'}
      </button>

      {showPrepareForm && <PrepareForm surgeryOrderId={surgeryOrderId} onDone={() => setShowPrepareForm(false)} />}

      {isLoading ? (
        <p style={{ color: 'var(--text-muted)' }}>Loading…</p>
      ) : rows.length === 0 ? (
        <p style={{ color: 'var(--text-muted)' }}>No items prepared yet.</p>
      ) : (
        <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
          <table className="table">
            <caption className="sr-only">Procedure items</caption>
            <thead>
              <tr>
                <th>Item</th>
                <th>Phase</th>
                <th>Prepared</th>
                <th>Used / returned / wasted</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((item) => (
                <ProcedureItemRow key={item.id} surgeryOrderId={surgeryOrderId} item={item} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function ProcedureItemRow({ surgeryOrderId, item }: { surgeryOrderId: string; item: ProcedureItemResponse }) {
  const { accessToken } = useAuth();
  const { data: me } = useMe();
  const queryClient = useQueryClient();
  const [showReconcile, setShowReconcile] = useState(false);

  const { data: catalogItem } = useQuery({
    queryKey: ['items', item.itemId],
    queryFn: () => fetchItemById(item.itemId as string, accessToken as string),
    enabled: accessToken != null && item.itemId != null,
    staleTime: 60_000,
  });

  const attest = useMutation({
    mutationFn: () => attestProcedureItem(item.id as string, accessToken as string),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['procedure-items', surgeryOrderId] }),
  });

  const isReconciled = item.reconciledBy != null;
  const isAttested = item.attestedBy != null;
  const canSelfAttest = item.reconciledBy === me?.userId;

  return (
    <>
      <tr>
        <td>{catalogItem?.name ?? '…'}</td>
        <td>{item.phase}</td>
        <td>{item.quantityPrepared}</td>
        <td>
          {isReconciled ? `${item.quantityUsed ?? 0} / ${item.quantityReturned ?? 0} / ${item.quantityWasted ?? 0}` : '—'}
          {item.wastageReason && <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>{item.wastageReason}</div>}
        </td>
        <td>
          {isAttested ? (
            <span className="badge badge-success">Attested</span>
          ) : isReconciled ? (
            <span className="badge badge-warning">Awaiting attestation</span>
          ) : (
            <span className="badge badge-neutral">Prepared</span>
          )}
        </td>
        <td>
          {!isReconciled && (
            <button type="button" className="btn btn-secondary" onClick={() => setShowReconcile((v) => !v)}>
              {showReconcile ? 'Cancel' : 'Reconcile'}
            </button>
          )}
          {isReconciled && !isAttested && (
            <button
              type="button"
              className="btn btn-primary"
              disabled={attest.isPending || canSelfAttest}
              title={canSelfAttest ? 'The surgeon attests — the person who reconciled cannot also attest' : undefined}
              onClick={() => attest.mutate()}
            >
              {attest.isPending ? 'Attesting…' : 'Attest'}
            </button>
          )}
        </td>
      </tr>
      {showReconcile && (
        <tr>
          <td colSpan={6}>
            <ReconcileForm surgeryOrderId={surgeryOrderId} item={item} onDone={() => setShowReconcile(false)} />
          </td>
        </tr>
      )}
    </>
  );
}

const prepareSchema = z.object({
  itemId: z.string().min(1, 'Select an item'),
  phase: z.enum(['PRE_OP', 'INTRA_OP', 'POST_OP']),
  quantityPrepared: z.coerce.number().int().positive('Enter a quantity'),
  batchId: z.string().min(1, 'Enter a batch ID'),
});
type PrepareValues = z.infer<typeof prepareSchema>;

function PrepareForm({ surgeryOrderId, onDone }: { surgeryOrderId: string; onDone: () => void }) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<ItemSearchResultResponse | null>(null);
  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<PrepareValues>({ resolver: zodResolver(prepareSchema), defaultValues: { phase: 'PRE_OP', quantityPrepared: 1 } });

  const submit = useMutation({
    mutationFn: (values: PrepareValues) =>
      prepareProcedureItem(
        { surgeryOrderId, itemId: values.itemId, phase: values.phase, quantityPrepared: values.quantityPrepared, batchId: values.batchId },
        accessToken as string,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['procedure-items', surgeryOrderId] });
      onDone();
    },
  });

  return (
    <form
      onSubmit={handleSubmit((v) => submit.mutate(v))}
      noValidate
      style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', maxWidth: 560, background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)', marginBottom: 'var(--space-4)' }}
    >
      <div>
        <Combobox<ItemSearchResultResponse>
          label="Item"
          placeholder="Search the catalogue"
          search={(q) => searchItems(q, accessToken as string)}
          getKey={(i) => i.id as string}
          renderOption={(i) => (
            <span style={{ opacity: i.selectable === false ? 0.5 : 1 }}>
              {i.name} {i.strength && <span style={{ color: 'var(--text-muted)' }}>· {i.strength}</span>}
              {i.selectable === false && <span style={{ color: 'var(--critical)' }}> — {i.unselectableReason}</span>}
            </span>
          )}
          onSelect={(i) => {
            if (i.selectable === false) return;
            setSelected(i);
            setValue('itemId', i.id as string, { shouldValidate: true });
          }}
          clearOnSelect={false}
        />
        {selected && <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-secondary)', marginTop: 4 }}>Selected: {selected.name}</div>}
        {errors.itemId && <span className="field-error">{errors.itemId.message}</span>}
      </div>

      <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
        <label style={{ fontSize: 'var(--text-sm)', flex: 1 }}>
          Phase
          <select {...register('phase')} style={inputStyle}>
            {PHASES.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        </label>
        <label style={{ fontSize: 'var(--text-sm)', flex: 1 }}>
          Quantity prepared
          <input type="text" inputMode="numeric" {...register('quantityPrepared')} style={inputStyle} />
          {errors.quantityPrepared && <div className="field-error">{errors.quantityPrepared.message}</div>}
        </label>
      </div>

      <label style={{ fontSize: 'var(--text-sm)' }}>
        Batch ID
        <input type="text" {...register('batchId')} style={inputStyle} />
        {errors.batchId && <div className="field-error">{errors.batchId.message}</div>}
      </label>

      {submit.error && <div className="form-error">Something went wrong. Try again.</div>}
      <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
        {submit.isPending ? 'Preparing…' : 'Prepare'}
      </button>
    </form>
  );
}

const reconcileSchema = z
  .object({
    quantityUsed: z.coerce.number().int().min(0),
    quantityReturned: z.coerce.number().int().min(0),
    quantityWasted: z.coerce.number().int().min(0),
    wastageReason: z.string().optional(),
  })
  .refine((v) => v.quantityWasted === 0 || !!v.wastageReason?.trim(), {
    message: 'Enter a reason for the wastage',
    path: ['wastageReason'],
  });
type ReconcileValues = z.infer<typeof reconcileSchema>;

// §2.3: "quantity_prepared == quantity_used + quantity_returned + quantity_wasted" — enforced server-side; this form just collects the three counts.
function ReconcileForm({ surgeryOrderId, item, onDone }: { surgeryOrderId: string; item: ProcedureItemResponse; onDone: () => void }) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<ReconcileValues>({
    resolver: zodResolver(reconcileSchema),
    defaultValues: { quantityUsed: item.quantityPrepared ?? 0, quantityReturned: 0, quantityWasted: 0 },
  });
  const values = watch();
  const total = (values.quantityUsed ?? 0) + (values.quantityReturned ?? 0) + (values.quantityWasted ?? 0);
  const balanced = total === (item.quantityPrepared ?? 0);

  const submit = useMutation({
    mutationFn: (v: ReconcileValues) => reconcileProcedureItem(item.id as string, v, accessToken as string),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['procedure-items', surgeryOrderId] });
      onDone();
    },
  });

  return (
    <form
      onSubmit={handleSubmit((v) => submit.mutate(v))}
      noValidate
      style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', padding: 'var(--space-3)', background: 'var(--bg-raised)', border: '1px solid var(--border)', borderRadius: 'var(--radius)' }}
    >
      <div style={{ fontSize: 'var(--text-sm)', color: 'var(--text-muted)' }}>
        Prepared: {item.quantityPrepared} — used + returned + wasted must total {item.quantityPrepared}
        {!balanced && <span style={{ color: 'var(--critical)' }}> (currently {total})</span>}
      </div>
      <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
        <label style={{ fontSize: 'var(--text-sm)' }}>
          Used
          <input type="text" inputMode="numeric" {...register('quantityUsed')} style={inputStyle} />
        </label>
        <label style={{ fontSize: 'var(--text-sm)' }}>
          Returned
          <input type="text" inputMode="numeric" {...register('quantityReturned')} style={inputStyle} />
        </label>
        <label style={{ fontSize: 'var(--text-sm)' }}>
          Wasted
          <input type="text" inputMode="numeric" {...register('quantityWasted')} style={inputStyle} />
        </label>
      </div>
      {(values.quantityWasted ?? 0) > 0 && (
        <label style={{ fontSize: 'var(--text-sm)' }}>
          Wastage reason
          <input type="text" {...register('wastageReason')} style={inputStyle} />
          {errors.wastageReason && <div className="field-error">{errors.wastageReason.message}</div>}
        </label>
      )}
      {submit.error && <div className="form-error">Something went wrong. Try again.</div>}
      <button type="submit" className="btn btn-primary" disabled={submit.isPending} style={{ alignSelf: 'flex-start' }}>
        {submit.isPending ? 'Saving…' : 'Save reconciliation'}
      </button>
    </form>
  );
}

const inputStyle: React.CSSProperties = {
  display: 'block',
  width: '100%',
  marginTop: 4,
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};
