import type { SseEvent } from './sse';

/**
 * The state of one streamed answer of Toefly Tutor (docs/06-ai-agent.md §4.9, docs/07-ux-ui.md §3.16): `meta`
 * names the message, `tool` shows a status line, `token` adds text, `action` adds a card to confirm, `retract`
 * replaces the partial text with the guard's refusal, `done` ends it, `error` carries a problem.
 */
export type ReplyStatus =
  /** Sent, no token yet: "Thinking…". */
  | 'thinking'
  | 'streaming'
  /** The connection dropped; the client is resuming with Last-Event-ID (AC-17.01.4). */
  | 'reconnecting'
  | 'done'
  | 'retracted'
  /** The learner pressed Stop; the partial text stays. */
  | 'stopped'
  /** Not resumed within 30 s: the partial text and Retry. */
  | 'interrupted'
  | 'failed';

export interface ReplyTool {
  name: string;
  label: string;
  finished: boolean;
}

export interface ReplyAction {
  actionId: string;
  type: string;
  title: string;
  status: 'PENDING' | 'CONFIRMED' | 'EXPIRED';
}

export interface ReplyProblem {
  status?: number;
  code?: string;
  title?: string;
  detail?: string;
}

export interface Reply {
  messageId: string | null;
  text: string;
  status: ReplyStatus;
  tools: ReplyTool[];
  actions: ReplyAction[];
  intent: string | null;
  language: string | null;
  lastEventId: string | null;
  problem: ReplyProblem | null;
}

export type ReplyEvent =
  | { type: 'sse'; event: SseEvent }
  | { type: 'connection-lost' }
  | { type: 'interrupted' }
  | { type: 'stopped' }
  | { type: 'failed'; problem: ReplyProblem }
  | { type: 'resumed' }
  | { type: 'action-status'; actionId: string; status: ReplyAction['status'] };

export const initialReply: Reply = {
  messageId: null,
  text: '',
  status: 'thinking',
  tools: [],
  actions: [],
  intent: null,
  language: null,
  lastEventId: null,
  problem: null,
};

const FINAL: ReadonlySet<ReplyStatus> = new Set(['done', 'retracted', 'stopped', 'failed']);

export function isFinal(status: ReplyStatus): boolean {
  return FINAL.has(status);
}

/** Whether the answer is still coming: the composer shows Stop instead of Send. */
export function isActive(status: ReplyStatus): boolean {
  return status === 'thinking' || status === 'streaming' || status === 'reconnecting';
}

function json(data: string): Record<string, unknown> {
  try {
    const value: unknown = JSON.parse(data);
    return value && typeof value === 'object' ? (value as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

const str = (value: unknown): string | null => (typeof value === 'string' ? value : null);

/** Emoji never reach the screen (docs/06 §4.1): the service strips them, the client does it again. */
export function withoutEmoji(text: string): string {
  return text.replace(/[\p{Extended_Pictographic}\u{FE0F}\u{200D}]/gu, '');
}

function onSse(state: Reply, { event, data, id }: SseEvent): Reply {
  const next = { ...state, lastEventId: id ?? state.lastEventId };
  // Events after the end are replays of a resumed stream: they change nothing.
  if (isFinal(state.status) && event !== 'action') return next;
  const body = json(data);
  switch (event) {
    case 'meta':
      return {
        ...next,
        messageId: str(body.messageId) ?? next.messageId,
        intent: str(body.intent) ?? next.intent,
        language: str(body.language) ?? next.language,
      };
    case 'tool': {
      const name = str(body.name) ?? 'tool';
      const label = str(body.label) ?? name;
      const finished = body.status === 'finished';
      const open = next.tools.findIndex((tool) => tool.name === name && !tool.finished);
      const tools =
        open >= 0
          ? next.tools.map((tool, i) => (i === open ? { ...tool, label, finished } : tool))
          : [...next.tools, { name, label, finished }];
      return { ...next, tools };
    }
    case 'token': {
      const piece = withoutEmoji(str(body.t) ?? '');
      return { ...next, text: next.text + piece, status: 'streaming' };
    }
    case 'action': {
      const actionId = str(body.actionId);
      if (!actionId || next.actions.some((action) => action.actionId === actionId)) return next;
      const action: ReplyAction = {
        actionId,
        type: str(body.type) ?? 'ASSIGN_EXTRA_TASK',
        title: str(body.title) ?? '',
        status: 'PENDING',
      };
      return { ...next, actions: [...next.actions, action] };
    }
    case 'retract':
      return { ...next, text: withoutEmoji(str(body.replacement) ?? ''), status: 'retracted', actions: [] };
    case 'done':
      return {
        ...next,
        messageId: str(body.messageId) ?? next.messageId,
        status: 'done',
        tools: next.tools.map((tool) => ({ ...tool, finished: true })),
      };
    case 'error':
      return {
        ...next,
        status: 'failed',
        problem: {
          status: typeof body.status === 'number' ? body.status : undefined,
          code: str(body.code) ?? undefined,
          title: str(body.title) ?? undefined,
          detail: str(body.detail) ?? undefined,
        },
      };
    default:
      return next;
  }
}

export function replyReducer(state: Reply, action: ReplyEvent): Reply {
  switch (action.type) {
    case 'sse':
      return onSse(state, action.event);
    case 'connection-lost':
      return isFinal(state.status) ? state : { ...state, status: 'reconnecting' };
    case 'resumed':
      return state.status === 'reconnecting' || state.status === 'interrupted'
        ? { ...state, status: state.text ? 'streaming' : 'thinking' }
        : state;
    case 'interrupted':
      return isFinal(state.status) ? state : { ...state, status: 'interrupted' };
    case 'stopped':
      return isFinal(state.status) ? state : { ...state, status: 'stopped' };
    case 'failed':
      return { ...state, status: 'failed', problem: action.problem };
    case 'action-status':
      return {
        ...state,
        actions: state.actions.map((item) =>
          item.actionId === action.actionId ? { ...item, status: action.status } : item,
        ),
      };
  }
}
