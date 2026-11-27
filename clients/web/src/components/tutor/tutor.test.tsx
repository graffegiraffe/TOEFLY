import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import '@/i18n';
import type { ScreenContext } from '@/api/tutor';

const createChatThread = vi.fn();
const listChatThreads = vi.fn();
const listChatMessages = vi.fn();
const patchChatThread = vi.fn();
const confirmPendingAction = vi.fn();
const getMyEntitlements = vi.fn();
vi.mock('@/api/client', () => ({
  api: {
    chatThreads: {
      createChatThread: (...a: unknown[]) => createChatThread(...a),
      listChatThreads: (...a: unknown[]) => listChatThreads(...a),
      listChatMessages: (...a: unknown[]) => listChatMessages(...a),
      patchChatThread: (...a: unknown[]) => patchChatThread(...a),
    },
    pendingActions: { confirmPendingAction: (...a: unknown[]) => confirmPendingAction(...a) },
    entitlements: { getMyEntitlements: (...a: unknown[]) => getMyEntitlements(...a) },
  },
}));

import { onPaywall } from '@/lib/paywall';
import { TutorLauncher } from './tutor-launcher';
import { TutorPanel } from './tutor-panel';
import { TutorProvider } from './tutor-provider';
import { useHideTutor, useTutor } from './use-tutor';

const API = 'http://api.toefly.localhost:8080/api/v1';
const THREAD = '11111111-1111-4111-8111-111111111111';
const CONTEXT: ScreenContext = {
  type: 'ATTEMPT_REVIEW',
  ref: '22222222-2222-4222-8222-222222222222',
  itemId: '33333333-3333-4333-8333-333333333333',
  label: 'Question 3 · Read in Daily Life',
};

const event = (name: string, data: unknown, id?: number) =>
  `${id !== undefined ? `id: ${id}\n` : ''}event: ${name}\ndata: ${JSON.stringify(data)}\n\n`;

/** A response body the test writes to, event by event. */
function liveStream() {
  const encoder = new TextEncoder();
  let controller!: ReadableStreamDefaultController<Uint8Array>;
  const body = new ReadableStream<Uint8Array>({ start: (c) => void (controller = c) });
  return {
    response: new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
    push: (text: string) => act(() => controller.enqueue(encoder.encode(text))),
    close: () => act(() => controller.close()),
  };
}

const problem = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify({ status, title: 'Refused', type: 'about:blank', ...body }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });

interface Call {
  url: string;
  method: string;
  headers: Headers;
  body: unknown;
}
let calls: Call[] = [];
let respond: (call: Call) => Response | Promise<Response>;

beforeEach(() => {
  calls = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string, init: RequestInit = {}) => {
      const call: Call = {
        url: input,
        method: init.method ?? 'GET',
        headers: new Headers(init.headers),
        body: typeof init.body === 'string' ? JSON.parse(init.body) : undefined,
      };
      calls.push(call);
      return respond(call);
    }),
  );
  createChatThread.mockResolvedValue({
    threadId: THREAD,
    title: 'Question 3',
    language: 'EN',
    createdAt: new Date(),
    messageCount: 0,
  });
  listChatThreads.mockResolvedValue({ items: [], page: { hasMore: false, limit: 50 } });
  listChatMessages.mockResolvedValue({ items: [], page: { hasMore: false, limit: 100 } });
  getMyEntitlements.mockResolvedValue({ entitlements: ['AI_TUTOR_CHAT'], entitlementsVersion: 1 });
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

function Opener({ context }: { context?: ScreenContext }) {
  const { openTutor } = useTutor();
  return (
    <button type="button" onClick={() => openTutor(context)}>
      Ask tutor
    </button>
  );
}

function Exam() {
  useHideTutor(true);
  return null;
}

function setup(children: ReactNode = <Opener context={CONTEXT} />) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <TutorProvider>
        {children}
        <TutorLauncher />
        <TutorPanel streamOptions={{ resumeWindowMs: 400, backoffMs: [20] }} />
      </TutorProvider>
    </QueryClientProvider>,
  );
}

async function openAndSend(text = 'Why is B wrong?') {
  const user = userEvent.setup();
  await user.click(screen.getByRole('button', { name: 'Ask tutor' }));
  const input = await screen.findByRole('textbox', { name: 'Message' });
  await user.type(input, `${text}{Enter}`);
  return user;
}

describe('Toefly Tutor panel (US-17.01)', () => {
  it('AC-17.01.1, AC-17.01.2 Ask tutor opens a chat with the context; the answer streams token by token', async () => {
    const stream = liveStream();
    respond = () => stream.response;
    setup();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Ask tutor' }));
    expect(await screen.findByTestId('context-chip')).toHaveTextContent('Context: Question 3 · Read in Daily Life');
    const input = screen.getByRole('textbox', { name: 'Message' });
    expect(input).toHaveAttribute('maxLength', '2000');
    await user.type(input, 'Why is B wrong?');
    expect(screen.getByTestId('message-counter')).toHaveTextContent('15/2000');
    await user.keyboard('{Enter}');

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(createChatThread).toHaveBeenCalledWith(
      expect.objectContaining({
        createChatThreadRequest: {
          title: null,
          context: { type: 'ATTEMPT_REVIEW', ref: CONTEXT.ref, itemId: CONTEXT.itemId },
        },
      }),
    );
    expect(calls[0]).toMatchObject({ url: `${API}/chat-threads/${THREAD}/messages`, method: 'POST' });
    expect(calls[0]!.body).toEqual({ text: 'Why is B wrong?' }); // the thread carries the context
    expect(calls[0]!.headers.get('Accept')).toBe('text/event-stream');
    expect(screen.getByText('Why is B wrong?')).toBeInTheDocument();
    expect(await screen.findByTestId('thinking')).toHaveTextContent('Thinking…');

    await stream.push(event('meta', { messageId: 'm1', intent: 'EXPLAIN_MISTAKE', language: 'EN' }, 1));
    await stream.push(
      event('tool', { name: 'getAttemptReview', status: 'started', label: 'Looking at your answers' }, 2),
    );
    expect(await screen.findByTestId('tool-line')).toHaveTextContent('Looking at your answers');
    await stream.push(event('token', { t: "Let's look at **option B**." }, 3));
    expect(await screen.findByText('option B')).toContainHTML('strong');
    expect(screen.getByTestId('caret')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Stop' })).toBeInTheDocument();
    await stream.push(': ping\n\n');
    await stream.push(event('token', { t: ' What does the text say?' }, 4));
    expect(await screen.findByText(/What does the text say\?/)).toBeInTheDocument();

    listChatMessages.mockResolvedValue({
      items: [
        { messageId: 'u1', role: 'USER', text: 'Why is B wrong?', status: 'COMPLETE', createdAt: new Date() },
        {
          messageId: 'm1',
          role: 'ASSISTANT',
          text: "Let's look at **option B**. What does the text say?",
          status: 'COMPLETE',
          createdAt: new Date(),
        },
      ],
      page: { hasMore: false, limit: 100 },
    });
    await stream.push(event('done', { messageId: 'm1' }, 5));
    await stream.close();
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Stop' })).not.toBeInTheDocument());
    await waitFor(() => expect(screen.queryByTestId('caret')).not.toBeInTheDocument());
    expect(screen.getAllByTestId('tutor-answer')).toHaveLength(1);
    expect(screen.getByRole('status')).toHaveTextContent('Toefly Tutor answered.');
  });

  it('AC-17.01.2 the learner can remove the context chip', async () => {
    respond = () => new Response(event('done', { messageId: 'm1' }), { status: 200 });
    setup();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Ask tutor' }));
    await user.click(await screen.findByRole('button', { name: 'Remove context' }));
    expect(screen.queryByTestId('context-chip')).not.toBeInTheDocument();
    await user.type(screen.getByRole('textbox', { name: 'Message' }), 'Hello{Enter}');
    await waitFor(() =>
      expect(createChatThread).toHaveBeenCalledWith(
        expect.objectContaining({ createChatThreadRequest: { title: null, context: null } }),
      ),
    );
  });

  it('Stop aborts the stream, keeps the partial text and stops the generation', async () => {
    const stream = liveStream();
    respond = (call) => (call.method === 'DELETE' ? new Response(null, { status: 204 }) : stream.response);
    setup();
    const user = await openAndSend();
    await stream.push(event('meta', { messageId: 'm1' }, 1));
    await stream.push(event('token', { t: 'Half of the' }, 2));
    await user.click(await screen.findByRole('button', { name: 'Stop' }));
    expect(screen.getByText('Stopped')).toBeInTheDocument();
    expect(screen.getByText('Half of the')).toBeInTheDocument();
    await waitFor(() =>
      expect(calls.at(-1)).toMatchObject({
        method: 'DELETE',
        url: `${API}/chat-threads/${THREAD}/messages/m1/stream`,
      }),
    );
    expect(screen.getByRole('button', { name: 'Send' })).toBeInTheDocument();
  });

  it('AC-17.01.4 a dropped stream resumes from the last event with Last-Event-ID', async () => {
    respond = (call) =>
      call.method === 'POST'
        ? new Response(event('meta', { messageId: 'm1' }, 1) + event('token', { t: 'First part,' }, 2), {
            status: 200,
          })
        : new Response(event('token', { t: ' second part.' }, 3) + event('done', { messageId: 'm1' }, 4), {
            status: 200,
          });
    setup();
    await openAndSend();
    expect(await screen.findByText('First part, second part.')).toBeInTheDocument();
    const resume = calls.find((call) => call.method === 'GET');
    expect(resume).toMatchObject({ url: `${API}/chat-threads/${THREAD}/messages/m1/stream` });
    expect(resume!.headers.get('Last-Event-ID')).toBe('2');
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Stop' })).not.toBeInTheDocument());
  });

  it('AC-17.01.4 without a connection for the whole window the partial answer stays with Retry', async () => {
    let online = false;
    respond = (call) => {
      if (call.method === 'POST') {
        return new Response(event('meta', { messageId: 'm1' }, 1) + event('token', { t: 'Partial' }, 2), {
          status: 200,
        });
      }
      if (!online) throw new TypeError('Failed to fetch');
      return new Response(event('token', { t: ' answer.' }, 3) + event('done', { messageId: 'm1' }, 4), {
        status: 200,
      });
    };
    setup();
    const user = await openAndSend();
    expect(await screen.findByText('The answer was interrupted.', {}, { timeout: 2000 })).toBeInTheDocument();
    expect(screen.getByText('Partial')).toBeInTheDocument();
    online = true;
    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('Partial answer.')).toBeInTheDocument();
    expect(calls.at(-1)!.headers.get('Last-Event-ID')).toBe('2');
  });

  it('AC-17.04.4 too many messages: 429 with the rate limit message', async () => {
    respond = () => problem(429, { code: 'RATE_LIMITED' });
    setup();
    await openAndSend();
    expect(await screen.findByRole('alert')).toHaveTextContent(
      "You're sending messages too fast. Try again in a minute",
    );
  });

  it('AC-17.05.2 during an exam the service refuses with 409', async () => {
    respond = () => problem(409, { code: 'EXAM_IN_PROGRESS' });
    setup();
    await openAndSend();
    expect(await screen.findByRole('alert')).toHaveTextContent('Chat is unavailable during an exam.');
  });

  it('AC-17.01.5 a 403 entitlement-required from the stream opens the Paywall', async () => {
    const paywall = vi.fn();
    const off = onPaywall(paywall);
    respond = () => problem(403, { code: 'ENTITLEMENT_REQUIRED', requiredEntitlement: 'AI_TUTOR_CHAT' });
    setup();
    await openAndSend();
    await waitFor(() => expect(paywall).toHaveBeenCalledWith('AI_TUTOR_CHAT'));
    off();
  });

  it('AC-17.01.5 without the right the button opens the Paywall instead of the panel', async () => {
    getMyEntitlements.mockResolvedValue({ entitlements: [], entitlementsVersion: 1 });
    const paywall = vi.fn();
    const off = onPaywall(paywall);
    setup(null);
    await userEvent.setup().click(screen.getByRole('button', { name: 'AI tutor' }));
    await waitFor(() => expect(paywall).toHaveBeenCalledWith('AI_TUTOR_CHAT'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    off();
  });

  it('AC-17.05.1 the button is hidden in an exam flow', () => {
    setup(<Exam />);
    expect(screen.queryByRole('button', { name: 'AI tutor' })).not.toBeInTheDocument();
  });

  it('AC-17.01.3 the button opens the last thread; the thread list starts a new chat', async () => {
    listChatThreads.mockResolvedValue({
      items: [
        {
          threadId: THREAD,
          title: 'Articles in my essay',
          language: 'EN',
          createdAt: new Date('2026-11-20T10:00:00Z'),
          messageCount: 2,
        },
      ],
      page: { hasMore: false, limit: 50 },
    });
    listChatMessages.mockResolvedValue({
      items: [
        { messageId: 'u1', role: 'USER', text: 'Do I need "the" here?', status: 'COMPLETE', createdAt: new Date() },
        {
          messageId: 'm1',
          role: 'ASSISTANT',
          text: 'What kind of noun is it?',
          status: 'COMPLETE',
          createdAt: new Date(),
        },
      ],
      page: { hasMore: false, limit: 100 },
    });
    patchChatThread.mockResolvedValue({});
    setup(null);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'AI tutor' }));
    const panel = await screen.findByRole('dialog', { name: 'Toefly Tutor' });
    await user.click(within(panel).getByRole('button', { name: 'Chats' }));
    await user.click(await within(panel).findByRole('button', { name: /Articles in my essay/ }));
    expect(await within(panel).findByText('What kind of noun is it?')).toBeInTheDocument();

    // AC-17.06: the explanation language of this thread
    await user.click(within(panel).getByRole('button', { name: 'RU, explain in Russian' }));
    expect(patchChatThread).toHaveBeenCalledWith({ threadId: THREAD, chatThreadPatch: { language: 'RU' } });
    expect(within(panel).getByRole('button', { name: 'RU, explain in Russian' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );

    await user.click(within(panel).getByRole('button', { name: 'Chats' }));
    await user.click(within(panel).getByRole('button', { name: 'New chat' }));
    expect(within(panel).getByText('Ask about your tasks, your mistakes or your plan.')).toBeInTheDocument();
  });

  it('an action card asks before the agent adds a task to the week', async () => {
    confirmPendingAction.mockResolvedValue({ actionId: 'a1', type: 'ASSIGN_EXTRA_TASK', status: 'CONFIRMED' });
    const stream = liveStream();
    respond = () => stream.response;
    setup();
    const user = await openAndSend('Give me more articles practice');
    await stream.push(event('meta', { messageId: 'm1' }, 1));
    await stream.push(event('token', { t: 'Here is a short set.' }, 2));
    await stream.push(
      event('action', {
        actionId: 'a1',
        type: 'ASSIGN_EXTRA_TASK',
        title: 'Articles practice, 12 min',
        confirmUrl: '/api/v1/pending-actions/a1:confirm',
      }),
    );
    const card = await screen.findByTestId('action-card');
    expect(card).toHaveTextContent('Articles practice, 12 min');
    await user.click(within(card).getByRole('button', { name: 'Add to my week' }));
    expect(confirmPendingAction).toHaveBeenCalledWith({ actionId: 'a1' });
    expect(await within(card).findByText('Added to your week')).toBeInTheDocument();
  });

  it('AC-17.04.3 retract replaces the partial answer', async () => {
    respond = () =>
      new Response(
        event('meta', { messageId: 'm1' }, 1) +
          event('token', { t: 'Something that is not fine' }, 2) +
          event('retract', { reason: 'OUTPUT_GUARD', replacement: "I can't help with that." }, 3) +
          event('done', { messageId: 'm1' }, 4),
        { status: 200 },
      );
    setup();
    await openAndSend();
    expect(await screen.findByText("I can't help with that.")).toBeInTheDocument();
    expect(screen.queryByText('Something that is not fine')).not.toBeInTheDocument();
  });
});
