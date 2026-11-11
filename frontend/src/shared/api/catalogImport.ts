import { ApiError, authFetch, authFetchVoid, type ProblemDetailBody } from './client';

// Phase 5/9 catalogue import. Types are hand-written here as a
// bridge — once the backend rebuild regenerates api/openapi.yaml and
// openapi-typescript refreshes shared/api/types.ts, migrate these to
// components['schemas']['CatalogImportPreviewResponse'] etc.

export interface CatalogImportPreview {
  batchId: string;
  rowCount: number;
  newItemCount: number;
  priceChangeCount: number;
  metadataChangeCount: number;
  unchangedCount: number;
  duplicateCodeCount: number;
  // priceMinor null = unpriced row (blank / negative in the source
  // .xlsx). The item's metadata still lands; no price entry is created.
  //
  // `code` is the effective code that lands in service_item.code — it
  // may carry a "#<externalRefId>" suffix on rows whose raw PRODUCT
  // CODE collided with another row in the same file. `originalCode` is
  // the raw value the spreadsheet contained.
  newItems: {
    excelRow: number;
    code: string;
    originalCode: string;
    name: string;
    priceMinor: number | null;
  }[];
  priceChanges: {
    excelRow: number;
    code: string;
    originalCode: string;
    name: string;
    previousMinor: number;
    newMinor: number;
  }[];
  metadataChanges: {
    excelRow: number;
    code: string;
    originalCode: string;
    name: string;
    changedFields: string[];
  }[];
  // One entry per raw PRODUCT CODE that appeared more than once in the
  // file. Informational — the importer accepts the rows and rewrites
  // subsequent effective codes; this list lets the admin see the
  // rewrites before hitting Apply. Empty on a clean file.
  duplicateCodes: {
    originalCode: string;
    occurrences: { excelRow: number; effectiveCode: string; name: string }[];
  }[];
}

export interface CatalogItem {
  id: string;
  code: string;
  originalCode: string;
  name: string;
  classification?: string;
  type?: string;
  billingCategory?: string;
  currentCashPriceMinor?: number | null;
  currencyCode: string;
  isActive: boolean;
}

export interface CatalogPriceChange {
  entryId: string;
  itemId: string;
  code: string;
  name: string;
  priceMinor: number;
  previousPriceMinor: number | null;
  changedBy: string;
  changedAt: string;
  validTo: string | null;
}

export function updateCatalogPrice(itemId: string, expectedPriceMinor: number, newPriceMinor: number, accessToken: string): Promise<CatalogPriceChange> {
  return authFetch(`/api/admin/catalog/items/${itemId}/price`, accessToken, {
    method: 'PUT', body: JSON.stringify({ expectedPriceMinor, newPriceMinor }),
  });
}

export function fetchCatalogPriceHistory(itemId: string, accessToken: string): Promise<CatalogPriceChange[]> {
  return authFetch(`/api/admin/catalog/items/${itemId}/price-history`, accessToken);
}

export async function downloadCatalog(accessToken: string): Promise<void> {
  const response = await fetch('/api/admin/catalog/items/export', {
    credentials: 'include', headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) throw new ApiError(response.status);
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `ADAGS_Price_Catalogue_${new Date().toISOString().slice(0, 10)}.xlsx`;
  anchor.click();
  URL.revokeObjectURL(url);
}

export interface CatalogItemPage {
  page: number;
  pageSize: number;
  totalCount: number;
  items: CatalogItem[];
}

export interface CatalogItemsSearchParams {
  q?: string;
  type?: string;
  billingCategory?: string;
  activeOnly?: boolean;
  page?: number;
  pageSize?: number;
}

export const DEACTIVATE_ALL_CONFIRMATION = 'DEACTIVATE ALL CATALOGUE ITEMS';

export interface CatalogImportBatch {
  id: string;
  originalFilename: string;
  status: 'PREVIEWED' | 'APPLIED' | 'CANCELLED';
  rowCount: number;
  newItemCount: number;
  priceChangeCount: number;
  metadataChangeCount: number;
  unchangedCount: number;
  uploadedBy: string;
  uploadedAt: string;
  appliedBy?: string;
  appliedAt?: string;
}

export async function previewCatalogImport(file: File, accessToken: string): Promise<CatalogImportPreview> {
  const form = new FormData();
  form.append('file', file);
  // Multipart requests must NOT set Content-Type manually — the browser
  // adds the boundary. authFetch would inject application/json because
  // there's a body; fall through to a plain fetch with credentials.
  const response = await fetch('/api/admin/catalog/import/preview', {
    method: 'POST',
    credentials: 'include',
    headers: { Authorization: `Bearer ${accessToken}` },
    body: form,
  });
  if (!response.ok) {
    let body: ProblemDetailBody | null = null;
    try {
      body = (await response.json()) as ProblemDetailBody;
    } catch {
      // no-op
    }
    throw new ApiError(response.status, body);
  }
  return response.json() as Promise<CatalogImportPreview>;
}

export function applyCatalogImport(batchId: string, accessToken: string): Promise<CatalogImportBatch> {
  return authFetch(`/api/admin/catalog/import/${batchId}/apply`, accessToken, { method: 'POST' });
}

export function cancelCatalogImport(batchId: string, accessToken: string): Promise<void> {
  return authFetchVoid(`/api/admin/catalog/import/${batchId}/cancel`, accessToken, { method: 'POST' });
}

export function listCatalogImports(accessToken: string): Promise<CatalogImportBatch[]> {
  return authFetch('/api/admin/catalog/import/batches', accessToken);
}

export function searchCatalogItems(
  params: CatalogItemsSearchParams,
  accessToken: string,
): Promise<CatalogItemPage> {
  const query = new URLSearchParams();
  if (params.q && params.q.trim() !== '') query.set('q', params.q.trim());
  if (params.type) query.set('type', params.type);
  if (params.billingCategory) query.set('billingCategory', params.billingCategory);
  if (params.activeOnly === false) query.set('activeOnly', 'false');
  if (params.page != null) query.set('page', String(params.page));
  if (params.pageSize != null) query.set('pageSize', String(params.pageSize));
  const qs = query.toString();
  return authFetch(`/api/admin/catalog/items${qs ? `?${qs}` : ''}`, accessToken);
}

export function deactivateAllCatalogItems(
  confirmationPhrase: string,
  accessToken: string,
): Promise<{ deactivatedCount: number }> {
  return authFetch('/api/admin/catalog/items/deactivate-all', accessToken, {
    method: 'POST',
    body: JSON.stringify({ confirmationPhrase }),
  });
}
