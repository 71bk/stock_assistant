import { http } from '../utils/http';

export interface AnalyticsSummary {
  totalUsers: number;
  newUsers: number;
  dau: number;
  wau: number;
  mau: number;
  pageViews: number;
  sessions: number;
}

export interface AnalyticsDailyTrend {
  date: string;
  newUsers: number;
  activeUsers: number;
  pageViews: number;
}

export interface AnalyticsPageMetric {
  route: string;
  views: number;
  uniqueUsers: number;
  sessions: number;
}

export interface ApiEndpointMetric {
  method: string;
  uri: string;
  requestCount: number;
}

export interface ApiTrafficPoint {
  time: string;
  requestCount: number;
}

export interface ApiTraffic {
  available: boolean;
  requestCount: number;
  clientErrorCount: number;
  serverErrorCount: number;
  errorRatePercent: number;
  p95LatencyMs: number;
  topEndpoints: ApiEndpointMetric[];
  trend: ApiTrafficPoint[];
  warning?: string | null;
}

export interface AiUsageBreakdown {
  key: string;
  inputTokens: number;
  outputTokens: number;
  cachedTokens: number;
  callCount: number;
  failedCallCount: number;
}

export interface AiModelUsage {
  provider: string;
  model: string;
  operation: string;
  inputTokens: number;
  outputTokens: number;
  cachedTokens: number;
  callCount: number;
  failedCallCount: number;
}

export interface AiTokenTrendPoint {
  time: string;
  inputTokens: number;
  outputTokens: number;
  cachedTokens: number;
}

export interface AiUsage {
  available: boolean;
  inputTokens: number;
  outputTokens: number;
  cachedTokens: number;
  callCount: number;
  failedCallCount: number;
  failureRatePercent: number;
  p95DurationMs: number;
  ocrFallbackCount: number;
  ocrRetryCount: number;
  embeddingRetryCount: number;
  embeddingBatchFallbackCount: number;
  byProvider: AiUsageBreakdown[];
  byOperation: AiUsageBreakdown[];
  models: AiModelUsage[];
  trend: AiTokenTrendPoint[];
  warning?: string | null;
}

interface AnalyticsQuery {
  from: string;
  to: string;
  timezone?: string;
}

interface PageViewEvent {
  eventId: string;
  sessionId: string;
  eventType: 'PAGE_VIEW';
  route: string;
  occurredAt: string;
}

const withTimezone = (query: AnalyticsQuery) => ({
  ...query,
  timezone: query.timezone || 'Asia/Taipei',
});

export const analyticsApi = {
  getSummary: (query: AnalyticsQuery) =>
    http.get<AnalyticsSummary>('/admin/analytics/summary', {
      params: withTimezone(query),
    }),

  getUserTrend: (query: AnalyticsQuery) =>
    http.get<AnalyticsDailyTrend[]>('/admin/analytics/users/trend', {
      params: withTimezone(query),
    }),

  getTopPages: (query: AnalyticsQuery) =>
    http.get<AnalyticsPageMetric[]>('/admin/analytics/pages', {
      params: withTimezone(query),
    }),

  getApiTraffic: (query: AnalyticsQuery) =>
    http.get<ApiTraffic>('/admin/analytics/api-traffic', {
      params: withTimezone(query),
      timeout: 20000,
    }),

  getAiUsage: (query: AnalyticsQuery) =>
    http.get<AiUsage>('/admin/analytics/ai-usage', {
      params: withTimezone(query),
      timeout: 20000,
    }),

  trackPageView: (event: PageViewEvent) =>
    http.post<{ accepted: number }>('/analytics/events', {
      events: [event],
    }),
};
