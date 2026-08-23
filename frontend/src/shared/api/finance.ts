import { authFetch } from './client';
import type { components } from './types';

export type ExpenseResponse = components['schemas']['ExpenseResponse'];
export type ExpenseStatus = NonNullable<ExpenseResponse['status']>;
export type RecordExpenseRequest = components['schemas']['RecordExpenseRequest'];
export type ApproveExpenseRequest = components['schemas']['ApproveExpenseRequest'];
export type VoidExpenseRequest = components['schemas']['VoidExpenseRequest'];
export type ExpenseCategoryTotal = components['schemas']['ExpenseCategoryTotal'];
export type ExpenseMonthlyTotal = components['schemas']['ExpenseMonthlyTotal'];

export function fetchExpensesByStatus(status: ExpenseStatus, accessToken: string): Promise<ExpenseResponse[]> {
  return authFetch(`/api/finance/expenses?status=${status}`, accessToken);
}

export function recordExpense(body: RecordExpenseRequest, accessToken: string): Promise<ExpenseResponse> {
  return authFetch('/api/finance/expenses', accessToken, { method: 'POST', body: JSON.stringify(body) });
}

export function submitExpense(id: string, accessToken: string): Promise<ExpenseResponse> {
  return authFetch(`/api/finance/expenses/${id}/submit`, accessToken, { method: 'POST' });
}

export function withdrawExpense(id: string, accessToken: string): Promise<ExpenseResponse> {
  return authFetch(`/api/finance/expenses/${id}/withdraw`, accessToken, { method: 'POST' });
}

export function approveExpense(
  id: string,
  body: ApproveExpenseRequest,
  accessToken: string,
): Promise<ExpenseResponse> {
  return authFetch(`/api/finance/expenses/${id}/approve`, accessToken, { method: 'POST', body: JSON.stringify(body) });
}

export function voidExpense(id: string, body: VoidExpenseRequest, accessToken: string): Promise<ExpenseResponse> {
  return authFetch(`/api/finance/expenses/${id}/void`, accessToken, { method: 'POST', body: JSON.stringify(body) });
}

export function fetchExpensesByCategory(
  from: string,
  to: string,
  accessToken: string,
): Promise<ExpenseCategoryTotal[]> {
  return authFetch(`/api/reporting/expenses/by-category?from=${from}&to=${to}`, accessToken);
}

export function fetchExpensesMonthlyTrend(
  from: string,
  to: string,
  accessToken: string,
): Promise<ExpenseMonthlyTotal[]> {
  return authFetch(`/api/reporting/expenses/monthly-trend?from=${from}&to=${to}`, accessToken);
}
