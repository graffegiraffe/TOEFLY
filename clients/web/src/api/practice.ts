import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query';
import { type TaskType as ContentTaskType } from './generated/content';
import {
  AttemptStatus,
  ResponseError,
  type Attempt,
  type AttemptReview,
  type HeartbeatRequest,
  SubmitAttemptRequestReasonEnum,
} from './generated/practice';
import { api } from './client';
import { toApi, type Answer } from '@/lib/practice/answers';
import { OfflineError, StaleVersionError, type AnswerTransport } from '@/lib/practice/answer-sync';

/** Practice API (docs/05-api-contracts.md §7.1): attempts with optimistic locking and idempotent commands. */

export function versionOf(etag: string | null | undefined, fallback = 0): number {
  const match = etag?.match(/(\d+)/);
  return match ? Number(match[1]) : fallback;
}

const etag = (version: number) => `"${version}"`;

export const itemSetsQuery = (taskType: string) =>
  queryOptions({
    queryKey: ['practice', 'item-sets', taskType],
    queryFn: () => api.contentItemSets.listItemSets({ taskType: taskType as ContentTaskType, limit: 50 }),
    staleTime: 60_000,
  });

export const attemptQuery = (attemptId: string) =>
  queryOptions({
    queryKey: ['practice', 'attempt', attemptId],
    queryFn: () => api.attempts.getAttempt({ attemptId }),
    staleTime: Infinity,
  });

export const reviewQuery = (attemptId: string) =>
  queryOptions({
    queryKey: ['practice', 'review', attemptId],
    queryFn: () => api.attempts.getAttemptReview({ attemptId }),
  });

export const openAttemptsQuery = queryOptions({
  queryKey: ['practice', 'attempts', 'open'],
  queryFn: () => api.attempts.listAttempts({ status: [AttemptStatus.InProgress], limit: 20 }),
});

export const recentAttemptsQuery = queryOptions({
  queryKey: ['practice', 'attempts', 'recent'],
  queryFn: () => api.attempts.listAttempts({ status: [AttemptStatus.Scored], limit: 10 }),
});

export const abilitiesQuery = queryOptions({
  queryKey: ['practice', 'abilities'],
  queryFn: () => api.abilities.listAbilities({}),
});

export function newKey(): string {
  return crypto.randomUUID();
}

export function useStartAttempt() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (itemSetId: string) =>
      api.attempts.startAttempt({
        idempotencyKey: newKey(),
        startAttemptRequest: { itemSetId, timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone },
      }),
    onSuccess: (attempt) => {
      client.setQueryData(attemptQuery(attempt.attemptId).queryKey, attempt);
      void client.invalidateQueries({ queryKey: ['practice', 'attempts'] });
    },
  });
}

export function useAbandonAttempt() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (attemptId: string) => api.attempts.abandonAttempt({ attemptId, idempotencyKey: newKey() }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['practice', 'attempts'] }),
  });
}

function isNetworkError(error: unknown): boolean {
  return !(error instanceof ResponseError) && (error instanceof TypeError || (error as Error)?.name === 'FetchError');
}

async function translate<T>(call: () => Promise<T>): Promise<T> {
  try {
    return await call();
  } catch (error) {
    if (error instanceof ResponseError && error.response.status === 412) {
      throw new StaleVersionError();
    }
    if (isNetworkError(error)) {
      throw new OfflineError();
    }
    throw error;
  }
}

export function answerTransport(attemptId: string): AnswerTransport {
  return {
    save: (itemId, answer: Answer, version) =>
      translate(async () => {
        const state = await api.attempts.recordAnswer({
          attemptId,
          itemId,
          ifMatch: etag(version),
          recordAnswerRequest: { answer: toApi(answer), clientTimestamp: new Date() },
        });
        return state.attemptVersion;
      }),
    clear: (itemId, version) =>
      translate(async () => {
        const response = await api.attempts.clearAnswerRaw({ attemptId, itemId, ifMatch: etag(version) });
        return versionOf(response.raw.headers.get('ETag'), version + 1);
      }),
    currentVersion: async () => {
      const response = await api.attempts.getAttemptRaw({ attemptId });
      const attempt = await response.value();
      return attempt.version ?? versionOf(response.raw.headers.get('ETag'));
    },
  };
}

export async function sendHeartbeat(attemptId: string, request: HeartbeatRequest): Promise<void> {
  try {
    await api.attempts.sendAttemptHeartbeat({ attemptId, heartbeatRequest: request });
  } catch {
    // a lost heartbeat only loses its seconds (AC-16.01.6); the next one carries on
  }
}

export async function submitAttempt(
  attemptId: string,
  idempotencyKey: string,
  finalAnswers: { itemId: string; answer: Answer }[],
  reason: SubmitAttemptRequestReasonEnum = SubmitAttemptRequestReasonEnum.User,
): Promise<AttemptReview> {
  return translate(() =>
    api.attempts.submitAttempt({
      attemptId,
      idempotencyKey,
      submitAttemptRequest: {
        finalAnswers: finalAnswers.map(({ itemId, answer }) => ({ itemId, answer: toApi(answer) })),
        clientSubmittedAt: new Date(),
        reason,
      },
    }),
  );
}

export function attemptVersion(attempt: Attempt): number {
  return attempt.version ?? 0;
}
