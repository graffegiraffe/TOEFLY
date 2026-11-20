import { queryOptions, useMutation } from '@tanstack/react-query';
import type { ProjectionName, ProjectionRebuildJob } from './generated/analytics';
import { api } from './client';
import { newKey } from './practice';

/**
 * Analytics API (docs/05-api-contracts.md §6.14): read models of the dashboards, built from events. Every answer
 * carries `asOf`, the moment of the last applied event (AC-05.02.3), so the screens show how fresh they are.
 */

export const studentDashboardQuery = queryOptions({
  queryKey: ['analytics', 'dashboard'],
  queryFn: () => api.dashboards.getStudentDashboard({}),
  retry: false,
});

export const streakQuery = queryOptions({
  queryKey: ['analytics', 'streak'],
  queryFn: () => api.streaks.getMyStreak(),
  retry: false,
});

/** Time of `[from, to)`; without dates the service returns the last seven days. */
export const timeReportQuery = (from?: Date, to?: Date) =>
  queryOptions({
    queryKey: ['analytics', 'time-report', from?.toISOString() ?? null, to?.toISOString() ?? null],
    queryFn: () => api.activity.getTimeReport({ from, to }),
    retry: false,
  });

export const productMetricsQuery = (from?: Date, to?: Date) =>
  queryOptions({
    queryKey: ['analytics', 'admin', 'product', from?.toISOString() ?? null, to?.toISOString() ?? null],
    queryFn: () => api.analyticsAdmin.getProductMetrics({ from, to }),
    retry: false,
  });

export const aiUsageReportQuery = (from?: Date, to?: Date) =>
  queryOptions({
    queryKey: ['analytics', 'admin', 'ai-usage', from?.toISOString() ?? null, to?.toISOString() ?? null],
    queryFn: () => api.analyticsAdmin.getAiUsageReport({ from, to }),
    retry: false,
  });

export const rebuildJobQuery = (jobId: string) =>
  queryOptions({
    queryKey: ['analytics', 'admin', 'rebuild', jobId],
    queryFn: () => api.projections.getProjectionRebuild({ jobId }),
    refetchInterval: (query) => (query.state.data?.status === 'RUNNING' ? 1000 : false),
  });

/** Starts a rebuild of a read model from the journal (POST with an Idempotency-Key). */
export function useRebuildProjection() {
  return useMutation({
    mutationFn: (projection: ProjectionName): Promise<ProjectionRebuildJob> =>
      api.projections.rebuildProjection({ projection, idempotencyKey: newKey() }),
  });
}
