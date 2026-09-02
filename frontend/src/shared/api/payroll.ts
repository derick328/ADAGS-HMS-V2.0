import { authFetch } from './client';
import type { components } from './types';

export type PayrollBatchPreviewResponse = components['schemas']['PayrollBatchPreviewResponse'];
export type StaffLine = components['schemas']['StaffLine'];

/**
 * 18-admin.md §5.1: preview -> generate -> disburse. The preview is a
 * pure read of every staff row that would be paid for the given period
 * — one line per employee, with gross, deductions, and net. Gated on
 * PAYROLL_MANAGE (which ADMIN and ACCOUNTANT hold).
 */
export function fetchPayrollPreview(
  month: number,
  year: number,
  accessToken: string,
): Promise<PayrollBatchPreviewResponse> {
  return authFetch(`/api/finance/payroll/preview?month=${month}&year=${year}`, accessToken);
}
