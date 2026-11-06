import { authFetch } from './client';

export interface StockReceiptLineRequest {
  itemId: string;
  purchaseUnit: string;
  orderedQuantity?: number;
  receivedQuantity: number;
  acceptedQuantity: number;
  rejectedQuantity: number;
  batchNumber: string;
  expiryDate: string;
  unitPurchaseCostMinor: number;
  discrepancyReason?: string;
  notes?: string;
}

export interface StockReceiptRequest {
  supplierId: string;
  storeId: string;
  invoiceNumber: string;
  deliveryNoteNumber: string;
  invoiceDate: string;
  deliveryDate: string;
  notes?: string;
  idempotencyKey: string;
  lines: StockReceiptLineRequest[];
}

export interface StockReceiptLine {
  id: string;
  itemId: string;
  itemName: string;
  baseUnit: string;
  purchaseUnit: string;
  factor: number;
  receivedQuantity: number;
  acceptedQuantity: number;
  rejectedQuantity: number;
  missingQuantity: number;
  acceptedBaseQuantity: number;
  rejectedBaseQuantity: number;
  receivedCostMinor: number;
  acceptedCostMinor: number;
  batchNumber: string;
  expiryDate: string;
  orderedQuantity: number | null;
  unitPurchaseCostMinor: number;
  discrepancyReason: string | null;
  notes: string | null;
}

export interface StockReceipt {
  id: string;
  receiptNumber: string;
  facilityId: string;
  storeId: string;
  supplierId: string;
  supplierName: string;
  receivedBy: string;
  receivedByName: string | null;
  invoiceNumber: string;
  deliveryNoteNumber: string;
  invoiceDate: string;
  deliveryDate: string;
  notes: string | null;
  totalReceivedCostMinor: number;
  totalAcceptedCostMinor: number;
  currencyCode: string;
  confirmedAt: string;
  lines: StockReceiptLine[];
}

export function confirmStockReceipt(body: StockReceiptRequest, token: string): Promise<StockReceipt> {
  return authFetch('/api/stock-receipts', token, { method: 'POST', body: JSON.stringify(body) });
}

export function fetchStockReceipts(storeId: string, token: string): Promise<StockReceipt[]> {
  return authFetch(`/api/stock-receipts?storeId=${encodeURIComponent(storeId)}`, token);
}

export function fetchStockReceipt(id: string, token: string): Promise<StockReceipt> {
  return authFetch(`/api/stock-receipts/${id}`, token);
}
