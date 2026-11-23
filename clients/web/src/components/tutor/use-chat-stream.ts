import { useCallback, useEffect, useRef, useState } from 'react';
import { ChatRequestError, postChatMessage, resumeChatStream, stopChatStream, type ScreenContext } from '@/api/tutor';
import { newKey } from '@/api/practice';
import { initialReply, isActive, replyReducer, type Reply, type ReplyEvent } from '@/lib/tutor/reply';
import { readSse, SseParser } from '@/lib/tutor/sse';

/** A message of this session that the history from the service does not hold yet. */
export interface Turn {
  key: string;
  threadId: string;
  text: string;
  context: ScreenContext | null | undefined;
  reply: Reply;
}

export interface StreamOptions {
  /** How long a dropped stream is resumed with Last-Event-ID before Retry is shown (AC-17.01.4). */
  resumeWindowMs?: number;
  /** Pauses between resume attempts; the last one repeats. */
  backoffMs?: number[];
  /** Called once an answer has ended (done, stopped, failed): the history is refetched. */
  onSettled?: (threadId: string) => void;
}

interface Live {
  key: string;
  threadId: string;
  messageId: string | null;
  parser: SseParser;
  controller: AbortController;
}

type Outcome = 'complete' | 'dropped' | 'aborted';

const wait = (ms: number, signal: AbortSignal) =>
  new Promise<void>((resolve) => {
    const timer = setTimeout(resolve, ms);
    signal.addEventListener('abort', () => {
      clearTimeout(timer);
      resolve();
    });
  });

/**
 * Sends chat messages and follows the SSE answers (docs/06-ai-agent.md §4.9): tokens are appended as they arrive,
 * a dropped connection is resumed with Last-Event-ID for up to 30 s, Stop aborts the stream and asks the service
 * to stop the generation. One answer streams at a time.
 */
export function useChatStream({
  resumeWindowMs = 30_000,
  backoffMs = [1000, 2000, 4000],
  onSettled,
}: StreamOptions = {}) {
  const [turns, setTurns] = useState<Turn[]>([]);
  const live = useRef<Live | null>(null);
  const turnsRef = useRef(turns);
  useEffect(() => {
    turnsRef.current = turns;
  }, [turns]);
  const settled = useRef(onSettled);
  useEffect(() => {
    settled.current = onSettled;
  }, [onSettled]);

  useEffect(() => () => live.current?.controller.abort(), []);

  const dispatch = useCallback((key: string, event: ReplyEvent) => {
    setTurns((all) =>
      all.map((turn) => (turn.key === key ? { ...turn, reply: replyReducer(turn.reply, event) } : turn)),
    );
  }, []);

  /** Reads one response body; tells whether the answer ended, the connection dropped or Stop was pressed. */
  const pump = useCallback(
    async (current: Live, body: ReadableStream<Uint8Array>): Promise<Outcome> => {
      let complete = false;
      try {
        await readSse(
          body,
          (event) => {
            if (event.event === 'meta' || event.event === 'done') {
              try {
                const id = (JSON.parse(event.data) as { messageId?: unknown }).messageId;
                if (typeof id === 'string') current.messageId = id;
              } catch {
                // not JSON: nothing to keep
              }
            }
            if (event.event === 'done' || event.event === 'error') complete = true;
            dispatch(current.key, { type: 'sse', event });
          },
          current.parser,
        );
      } catch {
        return current.controller.signal.aborted ? 'aborted' : 'dropped';
      }
      if (current.controller.signal.aborted) return 'aborted';
      return complete ? 'complete' : 'dropped';
    },
    [dispatch],
  );

  /** Resumes a dropped answer from the last event until it ends or the window closes. */
  const resume = useCallback(
    async (current: Live): Promise<Outcome> => {
      const { signal } = current.controller;
      const deadline = Date.now() + resumeWindowMs;
      let attempt = 0;
      dispatch(current.key, { type: 'connection-lost' });
      while (!signal.aborted && current.messageId && Date.now() < deadline) {
        const pause = backoffMs[Math.min(attempt, backoffMs.length - 1)] ?? 1000;
        attempt += 1;
        await wait(Math.min(pause, Math.max(0, deadline - Date.now())), signal);
        if (signal.aborted) return 'aborted';
        let body: ReadableStream<Uint8Array>;
        try {
          body = await resumeChatStream(current.threadId, current.messageId, current.parser.lastEventId, signal);
        } catch (error) {
          if (signal.aborted) return 'aborted';
          if (error instanceof ChatRequestError && error.problem.status === 404) break;
          continue; // still offline
        }
        dispatch(current.key, { type: 'resumed' });
        const outcome = await pump(current, body);
        if (outcome !== 'dropped') return outcome;
        dispatch(current.key, { type: 'connection-lost' });
      }
      if (signal.aborted) return 'aborted';
      dispatch(current.key, { type: 'interrupted' });
      return 'dropped';
    },
    [backoffMs, dispatch, pump, resumeWindowMs],
  );

  const finish = useCallback((current: Live, outcome: Outcome) => {
    if (live.current === current) live.current = null;
    if (outcome === 'complete') settled.current?.(current.threadId);
  }, []);

  const start = useCallback(
    async (turn: Turn) => {
      live.current?.controller.abort();
      const current: Live = {
        key: turn.key,
        threadId: turn.threadId,
        messageId: null,
        parser: new SseParser(),
        controller: new AbortController(),
      };
      live.current = current;
      let body: ReadableStream<Uint8Array>;
      try {
        body = await postChatMessage(turn.threadId, turn.text, turn.context, current.controller.signal);
      } catch (error) {
        if (!current.controller.signal.aborted) {
          dispatch(turn.key, {
            type: 'failed',
            problem: error instanceof ChatRequestError ? error.problem : { status: 0 },
          });
        }
        finish(current, 'aborted');
        return;
      }
      let outcome = await pump(current, body);
      if (outcome === 'dropped') outcome = await resume(current);
      finish(current, outcome);
    },
    [dispatch, finish, pump, resume],
  );

  /** Sends a message to a thread and streams the answer. */
  const send = useCallback(
    (threadId: string, text: string, context: ScreenContext | null | undefined) => {
      const turn: Turn = { key: newKey(), threadId, text, context, reply: initialReply };
      setTurns((all) => [...all, turn]);
      void start(turn);
    },
    [start],
  );

  /** Stop: the partial text stays; the service saves it as INTERRUPTED. */
  const stop = useCallback(() => {
    const current = live.current;
    if (!current) return;
    current.controller.abort();
    dispatch(current.key, { type: 'stopped' });
    live.current = null;
    if (current.messageId) {
      void stopChatStream(current.threadId, current.messageId).then(() => settled.current?.(current.threadId));
    }
  }, [dispatch]);

  /** Retry after an interrupted answer: resume it, or send the message again if the service lost it. */
  const retry = useCallback(
    (key: string, messageId: string | null, lastEventId: string | null) => {
      const turn = turnsRef.current.find((candidate) => candidate.key === key);
      if (!turn) return;
      if (!messageId) {
        setTurns((all) => all.map((item) => (item.key === key ? { ...item, reply: initialReply } : item)));
        void start({ ...turn, reply: initialReply });
        return;
      }
      live.current?.controller.abort();
      const parser = new SseParser();
      if (lastEventId) parser.feed(`id: ${lastEventId}\n\n`);
      const current: Live = { key, threadId: turn.threadId, messageId, parser, controller: new AbortController() };
      live.current = current;
      void (async () => {
        let body: ReadableStream<Uint8Array>;
        try {
          body = await resumeChatStream(turn.threadId, messageId, lastEventId, current.controller.signal);
        } catch (error) {
          if (error instanceof ChatRequestError && error.problem.status === 404) {
            setTurns((all) => all.map((item) => (item.key === key ? { ...item, reply: initialReply } : item)));
            void start({ ...turn, reply: initialReply });
            return;
          }
          finish(current, await resume(current));
          return;
        }
        dispatch(key, { type: 'resumed' });
        let outcome = await pump(current, body);
        if (outcome === 'dropped') outcome = await resume(current);
        finish(current, outcome);
      })();
    },
    [dispatch, finish, pump, resume, start],
  );

  const setActionStatus = useCallback(
    (key: string, actionId: string, status: 'CONFIRMED' | 'EXPIRED') =>
      dispatch(key, { type: 'action-status', actionId, status }),
    [dispatch],
  );

  /** Forgets the turns of a thread once its history holds them. */
  const forget = useCallback((keys: readonly string[]) => {
    if (keys.length === 0) return;
    setTurns((all) => all.filter((turn) => !keys.includes(turn.key)));
  }, []);

  const streaming = turns.some((turn) => isActive(turn.reply.status));
  return { turns, send, stop, retry, streaming, setActionStatus, forget };
}
