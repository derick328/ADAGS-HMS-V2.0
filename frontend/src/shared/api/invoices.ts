import { authFetch } from './client';
import type { components } from './types';

export type InvoiceResponse = components['schemas']['InvoiceResponse'];
export type InvoiceState = NonNullable<InvoiceResponse['state']>;
export type InvoiceLineResponse = components['schemas']['InvoiceLineResponse'];
export type CreateInvoiceRequest = components['schemas']['CreateInvoiceRequest'];
export type IssueInvoiceRequest = components['schemas']['IssueInvoiceRequest'];

export interface SearchInvoicesParams {
  patientId?: string;
  encounterId?: string;
  states?: InvoiceState[];
  createdFrom?: string;
  createdTo?: string;
  outstandingOnly?: boolean;
}

export function searchInvoices(params: SearchInvoicesParams, accessToken: string): Promise<InvoiceResponse[]> {
  const query = new URLSearchParams();
  if (params.patientId) query.set('patientId', params.patientId);
  if (params.encounterId) query.set('encounterId', params.encounterId);
  params.states?.forEach((state) => query.append('states', state));
  if (params.createdFrom) query.set('createdFrom', params.createdFrom);
  if (params.createdTo) query.set('createdTo', params.createdTo);
  if (params.outstandingOnly) query.set('outstandingOnly', 'true');
  return authFetch(`/api/invoices?${query.toString()}`, accessToken);
}

export function fetchInvoiceById(id: string, accessToken: string): Promise<InvoiceResponse> {
  return authFetch(`/api/invoices/${id}`, accessToken);
}

export function fetchInvoiceLines(id: string, accessToken: string): Promise<InvoiceLineResponse[]> {
  return authFetch(`/api/invoices/${id}/lines`, accessToken);
}

/** 05-receptionist.md §7.2/§7.3: creates a DRAFT — freely editable, not a financial record until issued. */
export function createInvoice(body: CreateInvoiceRequest, accessToken: string): Promise<InvoiceResponse> {
  return authFetch('/api/invoices', accessToken, { method: 'POST', body: JSON.stringify(body) });
}

/** The separate explicit Issue step — assigns the gapless invoice number and locks the lines. */
export function issueInvoice(id: string, body: IssueInvoiceRequest, accessToken: string): Promise<InvoiceResponse> {
  return authFetch(`/api/invoices/${id}/issue`, accessToken, { method: 'POST', body: JSON.stringify(body) });
}
