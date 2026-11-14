import { authFetch } from './client';

export interface MedicineRouteConfiguration { routes: string[]; concentration?: string | null }

export function fetchMedicineRoutes(itemId: string, accessToken: string): Promise<MedicineRouteConfiguration> {
  return authFetch(`/api/medicine-routes/items/${itemId}`, accessToken);
}

export function configureMedicineRoutes(itemId: string, routes: string[], concentration: string | null,
  reason: string, accessToken: string): Promise<MedicineRouteConfiguration> {
  return authFetch(`/api/medicine-routes/items/${itemId}`, accessToken,
    { method: 'POST', body: JSON.stringify({ routes, concentration, reason }) });
}
