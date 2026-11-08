import { infiniteQueryOptions, queryOptions, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ReportStatus,
  type TaskType,
  type AdminItemSetPatch,
  type ResolveItemReportRequest,
} from './generated/content';
import { api } from './client';
import { cleanFilters, type ContentFilters } from '@/lib/admin-content';

const PAGE = 20;

export function adminItemSetsQuery(filters: ContentFilters) {
  const clean = cleanFilters(filters);
  return infiniteQueryOptions({
    queryKey: ['admin', 'item-sets', clean],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) =>
      api.contentAdmin.adminListItemSets({
        ...clean,
        taskType: clean.taskType as TaskType | undefined,
        cursor: pageParam,
        limit: PAGE,
      }),
    getNextPageParam: (last) => (last.page.hasMore ? last.page.nextCursor : undefined),
  });
}

export const adminItemSetQuery = (itemSetId: string) =>
  queryOptions({
    queryKey: ['admin', 'item-set', itemSetId],
    queryFn: () => api.contentAdmin.adminGetItemSet({ itemSetId }),
  });

export const adminReportsQuery = (status: ReportStatus | undefined) =>
  queryOptions({
    queryKey: ['admin', 'item-reports', status ?? 'ALL'],
    queryFn: () => api.contentAdmin.adminListItemReports({ status, limit: 100 }),
  });

export const sourcesQuery = queryOptions({
  queryKey: ['content', 'sources'],
  queryFn: () => api.contentSources.listSources(),
  staleTime: 5 * 60_000,
});

export function usePatchItemSet() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ itemSetId, patch }: { itemSetId: string; patch: AdminItemSetPatch }) =>
      api.contentAdmin.adminPatchItemSet({ itemSetId, adminItemSetPatch: patch }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['admin'] }),
  });
}

export function useResolveReport() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ reportId, request }: { reportId: string; request: ResolveItemReportRequest }) =>
      api.contentAdmin.adminResolveItemReport({ reportId, resolveItemReportRequest: request }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['admin'] }),
  });
}

export function useHideSource() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (sourceCode: string) => api.contentAdmin.adminHideSource({ sourceCode }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['admin'] }),
  });
}

export function useStartImport() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (request: { sourceCode?: string; dryRun: boolean }) =>
      api.contentAdmin.adminStartImport({ startImportRequest: request }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['admin'] }),
  });
}
