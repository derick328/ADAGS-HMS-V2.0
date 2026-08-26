import { authFetch } from './client';
import type { components } from './types';

export type WardResponse = components['schemas']['WardResponse'];
export type BedResponse = components['schemas']['BedResponse'];
export type BedStatus = NonNullable<BedResponse['status']>;

export function fetchWardById(id: string, accessToken: string): Promise<WardResponse> {
  return authFetch(`/api/wards/${id}`, accessToken);
}

/** Screen 3's bed map — every bed in the ward, occupied or not. */
export function fetchWardBeds(wardId: string, accessToken: string): Promise<BedResponse[]> {
  return authFetch(`/api/wards/${wardId}/beds`, accessToken);
}

/** 17-ward-nurse.md §8.1: the bed picker's own list — free beds only. */
export function fetchFreeBeds(wardId: string, accessToken: string): Promise<BedResponse[]> {
  return authFetch(`/api/wards/${wardId}/beds/free`, accessToken);
}
