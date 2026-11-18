import { queryOptions, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import {
  AttemptMode,
  ResponseError,
  SubmissionStatus,
  type Submission,
  type SubmissionVersion,
} from './generated/assessment';
import { api } from './client';
import { API_URL } from './config';
import { newKey, versionOf } from './practice';

/**
 * Assessment API (docs/05-api-contracts.md §6.5): Writing submissions with versions, their evaluations, the SSE
 * stream of the check and the band estimate. POSTs carry an Idempotency-Key, the draft PUT the aggregate ETag.
 */

export interface VersionedSubmission {
  submission: Submission;
  /** The aggregate version for If-Match. */
  version: number;
}

async function fetchSubmission(submissionId: string): Promise<VersionedSubmission> {
  const response = await api.submissions.getSubmissionRaw({ submissionId });
  const submission = await response.value();
  return { submission, version: versionOf(response.raw.headers.get('ETag'), submission.version) };
}

export const submissionQuery = (submissionId: string) =>
  queryOptions({
    queryKey: ['assessment', 'submission', submissionId],
    queryFn: () => fetchSubmission(submissionId),
  });

/** The item set of a submission: task text and model answers come from content-service (decision 14). */
export const writingItemSetQuery = (itemSetId: string) =>
  queryOptions({
    queryKey: ['content', 'item-set', itemSetId],
    queryFn: () => api.contentItemSets.getItemSet({ itemSetId }),
    staleTime: 10 * 60_000,
  });

export const draftSubmissionsQuery = queryOptions({
  queryKey: ['assessment', 'submissions', 'drafts'],
  queryFn: () => api.submissions.listSubmissions({ status: SubmissionStatus.Draft, limit: 20 }),
  retry: false,
});

export const bandEstimateQuery = queryOptions({
  queryKey: ['assessment', 'band-estimate'],
  queryFn: async () => {
    try {
      return await api.bandEstimates.getCurrentBandEstimate({});
    } catch (error) {
      if (error instanceof ResponseError && error.response.status === 404) {
        return null; // nothing recorded yet
      }
      throw error;
    }
  },
  retry: false,
});

export function currentVersionOf(submission: Submission): SubmissionVersion | undefined {
  return submission.versions.find((version) => version.versionNo === submission.currentVersionNo);
}

export function createSubmission(itemSetId: string, weekTaskId?: string): Promise<Submission> {
  return api.submissions.createSubmission({
    idempotencyKey: newKey(),
    createSubmissionRequest: { itemSetId, weekTaskId: weekTaskId ?? null, mode: AttemptMode.Practice },
  });
}

export function useCreateSubmission() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ itemSetId }: { itemSetId: string }) => createSubmission(itemSetId),
    onSuccess: (submission) => {
      client.setQueryData(submissionQuery(submission.id).queryKey, { submission, version: submission.version });
      void client.invalidateQueries({ queryKey: ['assessment', 'submissions'] });
    },
  });
}

export class DraftConflictError extends Error {}

/** Saves the draft text; a stale ETag (412) reloads the aggregate version and repeats once. */
export async function saveDraft(
  submissionId: string,
  version: number,
  text: string,
  elapsedSeconds: number,
): Promise<number> {
  const put = async (ifMatch: number) => {
    const response = await api.submissions.saveDraftRaw({
      submissionId,
      ifMatch: `"${ifMatch}"`,
      saveDraftRequest: { text, elapsedSeconds: Math.max(0, Math.round(elapsedSeconds)) },
    });
    return versionOf(response.raw.headers.get('ETag'), ifMatch + 1);
  };
  try {
    return await put(version);
  } catch (error) {
    if (error instanceof ResponseError && error.response.status === 412) {
      const fresh = await fetchSubmission(submissionId);
      if (currentVersionOf(fresh.submission)?.status !== SubmissionStatus.Draft) {
        throw new DraftConflictError('The draft was already submitted');
      }
      return put(fresh.version);
    }
    throw error;
  }
}

export function useSubmitSubmission(submissionId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (idempotencyKey: string) => api.submissions.submitSubmission({ submissionId, idempotencyKey }),
    onSuccess: (submission) => {
      client.setQueryData(submissionQuery(submissionId).queryKey, { submission, version: submission.version });
      void client.invalidateQueries({ queryKey: ['assessment', 'submissions'] });
      void client.invalidateQueries({ queryKey: ['planning'] });
    },
  });
}

/** Starts Attempt 2 or 3 with the text of the previous version. */
export function useCreateVersion(submissionId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => api.submissions.createVersion({ submissionId, idempotencyKey: newKey() }),
    // the editor reads the submission next: it must already show the new draft
    onSuccess: () => client.fetchQuery({ ...submissionQuery(submissionId), staleTime: 0 }),
  });
}

const STREAM_EVENTS = ['evaluation.status', 'evaluation.progress', 'evaluation.deferred', 'evaluation.completed'];
export const POLL_MS = 3000;

/**
 * Follows the check of a submission (AC-11.03.1): the SSE stream `GET /submissions/{id}/events` refreshes the
 * submission on every event. The session cookie authenticates it, so a plain EventSource with credentials is
 * enough. If the stream fails, the submission is polled every 3 s instead. Returns whether polling is on.
 */
export function useSubmissionEvents(submissionId: string, active: boolean): boolean {
  const client = useQueryClient();
  const [polling, setPolling] = useState(() => typeof EventSource === 'undefined');

  useEffect(() => {
    if (!active || typeof EventSource === 'undefined') return;
    const refresh = () => void client.invalidateQueries({ queryKey: submissionQuery(submissionId).queryKey });
    const source = new EventSource(`${API_URL}/api/v1/submissions/${encodeURIComponent(submissionId)}/events`, {
      withCredentials: true,
    });
    let failures = 0;
    const onEvent = (event: MessageEvent) => {
      failures = 0;
      refresh();
      if (event.type === 'evaluation.completed') source.close();
    };
    for (const name of STREAM_EVENTS) source.addEventListener(name, onEvent);
    source.onerror = () => {
      failures += 1;
      // a closed stream or repeated reconnects: poll instead
      if (source.readyState === EventSource.CLOSED || failures >= 2) {
        source.close();
        refresh();
        setPolling(true);
      }
    };
    return () => source.close();
  }, [active, client, submissionId]);

  useQuery({
    ...submissionQuery(submissionId),
    enabled: active && polling,
    refetchInterval: active && polling ? POLL_MS : false,
  });

  return active && polling;
}
