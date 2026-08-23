import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type CSSProperties } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import {
  approveExpense,
  fetchExpensesByStatus,
  recordExpense,
  submitExpense,
  voidExpense,
  withdrawExpense,
  type ExpenseStatus,
} from '../../shared/api/finance';
import { useAuth } from '../../shared/auth/AuthProvider';
import { usePermission } from '../../shared/auth/usePermission';
import { formatMoney } from '../../shared/format/money';
import { QueueShell, type QueueRow, type QueueTab } from '../../shared/components/QueueShell';

// 18-admin.md §8: one screen per sidebar category, all backed by the same
// generic /api/finance/expenses — category is a free-text field on the
// backend (no enum, no per-category filter endpoint), so this page fixes
// category to its own label on record and filters the fetched status
// list client-side. Each of the 7 sidebar routes renders this component
// with a different `category` prop.
const TABS: { status: ExpenseStatus; label: string }[] = [
  { status: 'PENDING_APPROVAL', label: 'Pending approval' },
  { status: 'DRAFT', label: 'Draft' },
  { status: 'APPROVED', label: 'Approved' },
  { status: 'VOID', label: 'Void' },
];

const recordSchema = z.object({
  title: z.string().min(1, 'Enter what this expense was for'),
  amountMinor: z.coerce.number().int().positive('Enter an amount greater than zero'),
  expenseDate: z.string().min(1, 'Enter the date of the expense'),
  paymentMethod: z.enum(['CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'MOBILE_MONEY', 'BANK_TRANSFER', 'CHEQUE']),
  paidTo: z.string().min(1, 'Enter who was paid'),
  receiptNumber: z.string().optional(),
  notes: z.string().optional(),
});
type RecordFormValues = z.infer<typeof recordSchema>;

export function ExpensesCategoryPage({ category }: { category: string }) {
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const canCreate = usePermission('EXPENSE_CREATE');
  const canApprove = usePermission('EXPENSE_APPROVE');
  const [activeStatus, setActiveStatus] = useState<ExpenseStatus>('PENDING_APPROVAL');
  const [showForm, setShowForm] = useState(false);
  const [voidingId, setVoidingId] = useState<string | null>(null);
  const [voidReason, setVoidReason] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['expenses', activeStatus],
    queryFn: () => fetchExpensesByStatus(activeStatus, accessToken as string),
    enabled: accessToken != null,
  });

  const expenses = (data ?? []).filter((expense) => expense.category === category);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['expenses'] });

  const record = useMutation({
    mutationFn: (values: RecordFormValues) =>
      recordExpense({ ...values, category }, accessToken as string),
    onSuccess: () => {
      setShowForm(false);
      invalidate();
    },
  });
  const submit = useMutation({
    mutationFn: (id: string) => submitExpense(id, accessToken as string),
    onSuccess: invalidate,
  });
  const withdraw = useMutation({
    mutationFn: (id: string) => withdrawExpense(id, accessToken as string),
    onSuccess: invalidate,
  });
  const approve = useMutation({
    mutationFn: (id: string) => approveExpense(id, {}, accessToken as string),
    onSuccess: invalidate,
  });
  const voidMutation = useMutation({
    mutationFn: (vars: { id: string; voidReason: string }) =>
      voidExpense(vars.id, { voidReason: vars.voidReason }, accessToken as string),
    onSuccess: () => {
      setVoidingId(null);
      setVoidReason('');
      invalidate();
    },
  });

  const tabs: QueueTab[] = TABS.map((tab) => ({
    key: tab.status,
    label: tab.label,
    count: tab.status === activeStatus ? expenses.length : 0,
  }));

  const rows: QueueRow[] = expenses.map((expense) => ({
    id: expense.id ?? '',
    primary: expense.title,
    secondary: `${expense.paidTo} · ${expense.expenseDate}`,
    meta: <span className="money">{formatMoney(expense.amountMinor, expense.currencyCode)}</span>,
    action: (
      <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
        {activeStatus === 'DRAFT' && canCreate && (
          <button type="button" className="btn btn-secondary" onClick={() => submit.mutate(expense.id as string)}>
            Submit
          </button>
        )}
        {activeStatus === 'PENDING_APPROVAL' && canCreate && (
          <button type="button" className="btn btn-ghost" onClick={() => withdraw.mutate(expense.id as string)}>
            Withdraw
          </button>
        )}
        {activeStatus === 'PENDING_APPROVAL' && canApprove && (
          <button type="button" className="btn btn-primary" onClick={() => approve.mutate(expense.id as string)}>
            Approve
          </button>
        )}
        {(activeStatus === 'DRAFT' || activeStatus === 'PENDING_APPROVAL' || activeStatus === 'APPROVED') &&
          canApprove && (
            <button type="button" className="btn btn-danger" onClick={() => setVoidingId(expense.id ?? null)}>
              Void
            </button>
          )}
      </div>
    ),
  }));

  const voidingExpense = expenses.find((e) => e.id === voidingId);

  return (
    <div>
      <QueueShell
        title={category}
        rows={rows}
        tabs={tabs}
        activeTab={activeStatus}
        onTabChange={(key) => setActiveStatus(key as ExpenseStatus)}
        isLoading={isLoading}
        emptyMessage={`No ${TABS.find((t) => t.status === activeStatus)?.label.toLowerCase()} expenses in ${category}`}
        toolbar={
          canCreate && (
            <button type="button" className="btn btn-primary" onClick={() => setShowForm((v) => !v)}>
              {showForm ? 'Cancel' : 'Record expense'}
            </button>
          )
        }
      />

      {showForm && (
        <RecordExpenseForm
          isSubmitting={record.isPending}
          onSubmit={(values) => record.mutate(values)}
        />
      )}

      {voidingExpense && (
        <div
          style={{
            marginTop: 'var(--space-4)',
            background: 'var(--bg-surface)',
            border: '1px solid var(--border)',
            borderRadius: 'var(--radius-lg)',
            padding: 'var(--space-4)',
            maxWidth: 480,
          }}
        >
          <h2 style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--weight-medium)', marginTop: 0 }}>
            Void “{voidingExpense.title}”
          </h2>
          <label htmlFor="void-reason" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>
            Reason
          </label>
          <textarea
            id="void-reason"
            value={voidReason}
            onChange={(e) => setVoidReason(e.target.value)}
            rows={2}
            style={{
              width: '100%',
              marginTop: 'var(--space-1)',
              padding: 'var(--space-2)',
              border: '1px solid var(--border-strong)',
              borderRadius: 'var(--radius)',
              font: 'inherit',
            }}
          />
          <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-3)' }}>
            <button
              type="button"
              className="btn btn-danger"
              disabled={voidReason.trim() === '' || voidMutation.isPending}
              onClick={() => voidMutation.mutate({ id: voidingExpense.id as string, voidReason })}
            >
              Confirm void
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                setVoidingId(null);
                setVoidReason('');
              }}
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function RecordExpenseForm({
  isSubmitting,
  onSubmit,
}: {
  isSubmitting: boolean;
  onSubmit: (values: RecordFormValues) => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RecordFormValues>({ resolver: zodResolver(recordSchema) });

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      noValidate
      style={{
        marginTop: 'var(--space-4)',
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        padding: 'var(--space-4)',
        maxWidth: 480,
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-3)',
      }}
    >
      <div>
        <label htmlFor="expense-title" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>
          Title
        </label>
        <input id="expense-title" {...register('title')} style={fieldStyle} />
        {errors.title && <span className="field-error">{errors.title.message}</span>}
      </div>

      <div>
        <label htmlFor="expense-amount" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>
          Amount (TZS)
        </label>
        <input id="expense-amount" type="number" min={1} {...register('amountMinor')} style={fieldStyle} />
        {errors.amountMinor && <span className="field-error">{errors.amountMinor.message}</span>}
      </div>

      <div>
        <label htmlFor="expense-date" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>
          Date
        </label>
        <input id="expense-date" type="date" {...register('expenseDate')} style={fieldStyle} />
        {errors.expenseDate && <span className="field-error">{errors.expenseDate.message}</span>}
      </div>

      <div>
        <label htmlFor="expense-payment-method" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>
          Payment method
        </label>
        <select id="expense-payment-method" {...register('paymentMethod')} style={fieldStyle}>
          <option value="CASH">Cash</option>
          <option value="DEBIT_CARD">Debit card</option>
          <option value="CREDIT_CARD">Credit card</option>
          <option value="MOBILE_MONEY">Mobile money</option>
          <option value="BANK_TRANSFER">Bank transfer</option>
          <option value="CHEQUE">Cheque</option>
        </select>
      </div>

      <div>
        <label htmlFor="expense-paid-to" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>
          Paid to
        </label>
        <input id="expense-paid-to" {...register('paidTo')} style={fieldStyle} />
        {errors.paidTo && <span className="field-error">{errors.paidTo.message}</span>}
      </div>

      <div>
        <label htmlFor="expense-receipt" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>
          Receipt number (optional)
        </label>
        <input id="expense-receipt" {...register('receiptNumber')} style={fieldStyle} />
      </div>

      <div>
        <label htmlFor="expense-notes" style={{ display: 'block', fontSize: 'var(--text-sm)' }}>
          Notes (optional)
        </label>
        <textarea id="expense-notes" rows={2} {...register('notes')} style={fieldStyle} />
      </div>

      <button type="submit" className="btn btn-primary" disabled={isSubmitting} style={{ alignSelf: 'flex-start' }}>
        {isSubmitting ? 'Recording…' : 'Record expense'}
      </button>
    </form>
  );
}

const fieldStyle: CSSProperties = {
  width: '100%',
  marginTop: 'var(--space-1)',
  padding: 'var(--space-2)',
  border: '1px solid var(--border-strong)',
  borderRadius: 'var(--radius)',
  font: 'inherit',
};
