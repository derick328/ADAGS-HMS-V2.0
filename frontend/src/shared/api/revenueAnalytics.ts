import { authFetch } from './client';
import type { components } from './types';

// Generated from api/openapi.yaml — see frontend/CLAUDE.md "API types are
// generated, never written". springdoc emits every field optional, so the
// callers below treat each figure as possibly undefined and format through
// formatMoney, which already coerces null to 0.
export type RevenueSummary = components['schemas']['RevenueSummary'];
export type CategoryRevenueLine = components['schemas']['CategoryRevenueLine'];
export type RevenueTrendPoint = components['schemas']['RevenueTrendPoint'];
export type TopServiceItemLine = components['schemas']['TopServiceItemLine'];
export type FinancialAdjustments = components['schemas']['FinancialAdjustments'];
export type RevenueCategoryKey = NonNullable<CategoryRevenueLine['category']>;
export type TrendGranularity = 'DAY' | 'WEEK' | 'MONTH';

export interface AnalyticsRange {
  from: string;
  to: string;
}

function params(range: AnalyticsRange, facilityId: string, extra?: Record<string, string>): string {
  const search = new URLSearchParams({ from: range.from, to: range.to, facilityId, ...extra });
  return search.toString();
}

export function fetchRevenueSummary(
  range: AnalyticsRange,
  facilityId: string,
  accessToken: string,
): Promise<RevenueSummary> {
  return authFetch(`/api/reporting/revenue/summary?${params(range, facilityId)}`, accessToken);
}

export function fetchRevenueByCategory(
  range: AnalyticsRange,
  facilityId: string,
  accessToken: string,
): Promise<CategoryRevenueLine[]> {
  return authFetch(`/api/reporting/revenue/by-category?${params(range, facilityId)}`, accessToken);
}

/** Granularity omitted lets the server pick DAY / WEEK / MONTH from the span. */
export function fetchRevenueTrend(
  range: AnalyticsRange,
  facilityId: string,
  accessToken: string,
  granularity?: TrendGranularity,
): Promise<RevenueTrendPoint[]> {
  const extra = granularity ? { granularity } : undefined;
  return authFetch(`/api/reporting/revenue/trend?${params(range, facilityId, extra)}`, accessToken);
}

/**
 * Credit notes and write-offs applied in the period, plus the refunds already
 * netted out of collected cash. Invoice-level: these cannot be attributed to a
 * service category and must never be netted off one.
 */
export function fetchRevenueAdjustments(
  range: AnalyticsRange,
  facilityId: string,
  accessToken: string,
): Promise<FinancialAdjustments> {
  return authFetch(`/api/reporting/revenue/adjustments?${params(range, facilityId)}`, accessToken);
}

export function fetchTopRevenueItems(
  range: AnalyticsRange,
  facilityId: string,
  category: RevenueCategoryKey,
  limit: number,
  accessToken: string,
): Promise<TopServiceItemLine[]> {
  return authFetch(
    `/api/reporting/revenue/top-items?${params(range, facilityId, { category, limit: String(limit) })}`,
    accessToken,
  );
}
