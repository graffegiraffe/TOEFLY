import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query';
import { reportRefusal } from '@/lib/paywall';
import { readProblem } from '@/lib/tutor/problems';
import type { ReplyProblem } from '@/lib/tutor/reply';
import {
  ResponseError,
  type ChatContext,
  type ChatContextType,
  type ChatThread,
  type ExplanationLanguage,
  type PendingActionResult,
  type WeeklyReview,
} from './generated/tutoragent';
import { api } from './client';
import { API_URL } from './config';
import { newKey } from './practice';
import { currentCsrfToken } from './session';

/**
 * tutor-agent-service API (docs/05-api-contracts.md §6.8): chat threads and their history, the SSE answer stream,
 * pending actions of the agent and weekly reviews. The stream goes over POST with the CSRF header, so it is read
 * with fetch and parsed by lib/tutor/sse.ts instead of EventSource.
 */

/** What a screen tells the tutor about itself (AC-17.01.2): a reference, never the text. */
export interface ScreenContext {
  type: `${ChatContextType}`;
  ref?: string | null;
  itemId?: string | null;
  /** Text of the chip, e.g. "Question 3 · Read in Daily Life". */
  label: string;
}

export const threadsQuery = queryOptions({
  queryKey: ['tutor', 'threads'],
  queryFn: () => api.chatThreads.listChatThreads({ limit: 50 }),
  retry: false,
});

export const messagesQuery = (threadId: string) =>
  queryOptions({
    queryKey: ['tutor', 'messages', threadId],
    queryFn: () => api.chatThreads.listChatMessages({ threadId, limit: 100 }),
    retry: false,
  });

/** The review of a week, or null while it is being prepared (404 or an empty page). */
export const weeklyReviewQuery = (weekId: string) =>
  queryOptions({
    queryKey: ['tutor', 'weekly-review', weekId],
    queryFn: async (): Promise<WeeklyReview | null> => {
      try {
        const page = await api.weeklyReviews.listWeeklyReviews({ weekId, limit: 1 });
        return page.items.find((review) => review.weekId === weekId) ?? page.items[0] ?? null;
      } catch (error) {
        if (error instanceof ResponseError && error.response.status === 404) return null;
        throw error;
      }
    },
    retry: false,
  });

export function toChatContext(context: ScreenContext): ChatContext {
  return { type: context.type as ChatContextType, ref: context.ref ?? null, itemId: context.itemId ?? null };
}

export function useCreateThread() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ context, title }: { context: ScreenContext | null; title?: string }): Promise<ChatThread> =>
      api.chatThreads.createChatThread({
        idempotencyKey: newKey(),
        createChatThreadRequest: { title: title ?? null, context: context ? toChatContext(context) : null },
      }),
    onSuccess: () => client.invalidateQueries({ queryKey: threadsQuery.queryKey }),
  });
}

export function useThreadLanguage() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ threadId, language }: { threadId: string; language: ExplanationLanguage }) =>
      api.chatThreads.patchChatThread({ threadId, chatThreadPatch: { language } }),
    onSuccess: () => client.invalidateQueries({ queryKey: threadsQuery.queryKey }),
  });
}

export class ActionExpiredError extends Error {}

/** Confirms an agent action, e.g. an extra task for the week; the week is refreshed afterwards. */
export function useConfirmAction() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (actionId: string): Promise<PendingActionResult> => {
      try {
        return await api.pendingActions.confirmPendingAction({ actionId });
      } catch (error) {
        if (error instanceof ResponseError && error.response.status === 409) throw new ActionExpiredError();
        throw error;
      }
    },
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['planning'] });
    },
  });
}

/** A refusal of the stream before it starts (403, 409, 429…), as Problem Details. */
export class ChatRequestError extends Error {
  constructor(readonly problem: ReplyProblem & { type?: string }) {
    super(problem.title ?? `HTTP ${problem.status ?? '?'}`);
  }
}

function headers(extra: Record<string, string>): Headers {
  const out = new Headers({ Accept: 'text/event-stream', ...extra });
  const token = currentCsrfToken();
  if (token) {
    out.set('X-CSRF-Token', token);
    out.set('X-Client-Platform', 'web');
  }
  return out;
}

async function streamResponse(response: Response): Promise<ReadableStream<Uint8Array>> {
  if (!response.ok || !response.body) {
    await reportRefusal(response); // 403 entitlement-required opens the Paywall
    throw new ChatRequestError(await readProblem(response));
  }
  return response.body;
}

const base = `${API_URL}/api/v1`;

/** `POST /chat-threads/{id}/messages`: sends the message and returns the SSE body of the answer. */
export async function postChatMessage(
  threadId: string,
  text: string,
  context: ScreenContext | null | undefined,
  signal: AbortSignal,
): Promise<ReadableStream<Uint8Array>> {
  const body: Record<string, unknown> = { text };
  if (context !== undefined) body.context = context ? toChatContext(context) : null;
  const response = await fetch(`${base}/chat-threads/${encodeURIComponent(threadId)}/messages`, {
    method: 'POST',
    credentials: 'include',
    headers: headers({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
    signal,
  });
  return streamResponse(response);
}

/** `GET …/messages/{messageId}/stream` with Last-Event-ID: the events after it, then the rest of the answer. */
export async function resumeChatStream(
  threadId: string,
  messageId: string,
  lastEventId: string | null,
  signal: AbortSignal,
): Promise<ReadableStream<Uint8Array>> {
  const response = await fetch(
    `${base}/chat-threads/${encodeURIComponent(threadId)}/messages/${encodeURIComponent(messageId)}/stream`,
    {
      method: 'GET',
      credentials: 'include',
      headers: headers(lastEventId ? { 'Last-Event-ID': lastEventId } : {}),
      signal,
    },
  );
  return streamResponse(response);
}

/** `DELETE …/stream`: stops the generation; the service keeps the partial text as INTERRUPTED. */
export async function stopChatStream(threadId: string, messageId: string): Promise<void> {
  await fetch(`${base}/chat-threads/${encodeURIComponent(threadId)}/messages/${encodeURIComponent(messageId)}/stream`, {
    method: 'DELETE',
    credentials: 'include',
    headers: headers({}),
  }).catch(() => undefined);
}
