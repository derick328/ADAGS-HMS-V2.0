import { authFetch } from './client';

export interface VisitPayment {
  id: string; amountMinor: number; method: string; status: string; receivedAt: string; reference?: string | null;
}
export interface VisitInvoice {
  id: string; number?: string | null; state: string; payerCategory: string; currencyCode: string;
  grossMinor: number; patientPortionMinor: number; insurancePortionMinor: number;
  patientPaidMinor: number; patientOutstandingMinor: number; insuranceOutstandingMinor: number;
  payments: VisitPayment[];
}
export interface PatientVisit {
  encounterId: string; visitedAt: string; department: string; clinician?: string | null; status: string; invoices: VisitInvoice[];
}
export function fetchPatientVisits(patientId: string, facilityId: string, token: string): Promise<PatientVisit[]> {
  return authFetch(`/api/patient-visits/${patientId}?${new URLSearchParams({ facilityId })}`, token);
}
