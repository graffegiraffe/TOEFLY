import { infiniteQueryOptions, queryOptions, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import { callQueryParams, fromDateInput, toAiAdminError, type CallFilters } from '@/lib/admin-ai';

/** AI administration (docs/12-implementation-plan.md, phase 5a decision 10): prompts, calls, usage, providers. */

const PAGE = 20;

export const aiPromptsQuery = queryOptions({
  queryKey: ['admin', 'ai', 'prompts'],
  queryFn: () => api.aiAdmin.listPrompts(),
});

export const aiPromptVersionsQuery = (promptKey: string) =>
  queryOptions({
    queryKey: ['admin', 'ai', 'prompts', promptKey, 'versions'],
    queryFn: () => api.aiAdmin.listPromptVersions({ promptKey }),
  });

export function aiCallsQuery(filters: CallFilters) {
  const params = callQueryParams(filters);
  return infiniteQueryOptions({
    queryKey: ['admin', 'ai', 'calls', filters],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => api.aiAdmin.listAiCalls({ ...params, cursor: pageParam, limit: PAGE }),
    getNextPageParam: (last) => (last.page.hasMore ? (last.page.nextCursor ?? undefined) : undefined),
  });
}

export const aiProvidersQuery = queryOptions({
  queryKey: ['admin', 'ai', 'providers'],
  queryFn: () => api.aiAdmin.listProviderStatuses(),
  refetchInterval: 30_000,
});

export const aiUsageQuery = (from: string, to: string) =>
  queryOptions({
    queryKey: ['admin', 'ai', 'usage', from, to],
    queryFn: () => api.aiAdmin.getAiUsage({ from: fromDateInput(from)!, to: fromDateInput(to)! }),
    enabled: fromDateInput(from) !== undefined && fromDateInput(to) !== undefined && from <= to,
  });

/** Activates a version or rolls back to an archived one; `409 EVALS_NOT_PASSED` arrives as an AiAdminError. */
export function useActivatePromptVersion() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async ({ promptKey, version }: { promptKey: string; version: number }) => {
      try {
        return await api.aiAdmin.activatePromptVersion({ promptKey, version, idempotencyKey: crypto.randomUUID() });
      } catch (error) {
        throw await toAiAdminError(error);
      }
    },
    onSuccess: () => client.invalidateQueries({ queryKey: ['admin', 'ai', 'prompts'] }),
  });
}

/** «AI → Evals» (docs/06-ai-agent.md §7.5): runs, newest first; the detail carries thresholds and cases. */
export const aiEvalRunsQuery = (promptKey: string) =>
  queryOptions({
    queryKey: ['admin', 'ai', 'eval-runs', promptKey],
    queryFn: () => api.aiAdmin.listEvalRuns({ promptKey: promptKey || undefined, limit: 100 }),
  });

export const aiEvalRunQuery = (runId: string) =>
  queryOptions({
    queryKey: ['admin', 'ai', 'eval-run', runId],
    queryFn: () => api.aiAdmin.getEvalRun({ runId }),
    staleTime: Infinity, // a recorded run does not change
  });
