import { describe, expect, it } from 'vitest';
import { chatErrorKind } from './problems';
import { initialReply, isActive, replyReducer, withoutEmoji, type Reply, type ReplyEvent } from './reply';
import { parseInline, parseMarkdown } from './markdown';

const sse = (event: string, data: unknown, id: string | null = null): ReplyEvent => ({
  type: 'sse',
  event: { event, data: JSON.stringify(data), id },
});

const run = (events: ReplyEvent[], from: Reply = initialReply) => events.reduce(replyReducer, from);

describe('reply state of a streamed answer (docs/06 §4.9)', () => {
  it('AC-17.01.1 meta, tool, tokens and done build the answer', () => {
    const reply = run([
      sse('meta', { messageId: 'm1', intent: 'EXPLAIN_MISTAKE', language: 'EN' }, '1'),
      sse('tool', { name: 'getAttemptReview', status: 'started', label: 'Looking at your answers' }, '2'),
      sse('tool', { name: 'getAttemptReview', status: 'finished', label: 'Looked at your answers' }, '3'),
      sse('token', { t: "Let's look" }, '4'),
      sse('token', { t: ' at option B.' }, '5'),
    ]);
    expect(reply).toMatchObject({
      messageId: 'm1',
      intent: 'EXPLAIN_MISTAKE',
      text: "Let's look at option B.",
      status: 'streaming',
      lastEventId: '5',
      tools: [{ name: 'getAttemptReview', label: 'Looked at your answers', finished: true }],
    });
    expect(isActive(reply.status)).toBe(true);
    const done = replyReducer(reply, sse('done', { messageId: 'm1' }, '6'));
    expect(done.status).toBe('done');
    expect(isActive(done.status)).toBe(false);
  });

  it('shows Thinking until the first token', () => {
    const reply = run([sse('meta', { messageId: 'm1' })]);
    expect(reply.status).toBe('thinking');
    expect(reply.text).toBe('');
  });

  it('keeps an action card once and marks it confirmed', () => {
    const action = { actionId: 'a1', type: 'ASSIGN_EXTRA_TASK', title: 'Articles practice, 12 min' };
    const reply = run([
      sse('action', action),
      sse('action', action),
      { type: 'action-status', actionId: 'a1', status: 'CONFIRMED' },
    ]);
    expect(reply.actions).toEqual([{ ...action, status: 'CONFIRMED' }]);
  });

  it('AC-17.04.3 retract replaces the partial text with the refusal', () => {
    const reply = run([
      sse('token', { t: 'Something unsafe' }),
      sse('retract', { reason: 'OUTPUT_GUARD', replacement: "I can't help with that." }),
    ]);
    expect(reply.text).toBe("I can't help with that.");
    expect(reply.status).toBe('retracted');
  });

  it('error carries the problem; Stop keeps the partial text', () => {
    expect(run([sse('error', { status: 503, code: 'AI_UNAVAILABLE', title: 'AI unavailable' })])).toMatchObject({
      status: 'failed',
      problem: { status: 503, code: 'AI_UNAVAILABLE' },
    });
    const stopped = run([sse('token', { t: 'Half an' }), { type: 'stopped' }]);
    expect(stopped).toMatchObject({ status: 'stopped', text: 'Half an' });
  });

  it('AC-17.01.4 a dropped stream reconnects, then is interrupted; a replay after done changes nothing', () => {
    const lost = run([sse('token', { t: 'Part' }, '3'), { type: 'connection-lost' }]);
    expect(lost.status).toBe('reconnecting');
    expect(run([{ type: 'resumed' }], lost).status).toBe('streaming');
    expect(run([{ type: 'interrupted' }], lost)).toMatchObject({
      status: 'interrupted',
      text: 'Part',
      lastEventId: '3',
    });
    const done = run([sse('done', {}, '9'), sse('token', { t: ' again' }, '10')], lost);
    expect(done.text).toBe('Part');
    expect(done.status).toBe('done');
  });

  it('strips emoji from tokens (docs/06 §4.1)', () => {
    expect(withoutEmoji('Great \u{1F389} job ✅')).toBe('Great  job ');
    expect(run([sse('token', { t: 'Nice \u{1F44D}' })]).text).toBe('Nice ');
  });
});

describe('chat refusals', () => {
  it('AC-17.04.4, AC-17.05.2 map Problem Details to messages', () => {
    expect(chatErrorKind({ status: 429, code: 'RATE_LIMITED' })).toBe('rateLimited');
    expect(chatErrorKind({ status: 409, code: 'EXAM_IN_PROGRESS' })).toBe('exam');
    expect(chatErrorKind({ status: 409, type: 'https://toefly.app/problems/exam-in-progress' } as never)).toBe('exam');
    expect(chatErrorKind({ status: 409, code: 'MESSAGE_IN_PROGRESS' })).toBe('busy');
    expect(chatErrorKind({ status: 403, code: 'ENTITLEMENT_REQUIRED' })).toBe('entitlement');
    expect(chatErrorKind({ status: 503 })).toBe('unavailable');
    expect(chatErrorKind({ status: 500 })).toBe('generic');
  });
});

describe('Markdown subset of an answer (docs/07 §3.16)', () => {
  it('parses bold, italic and code without HTML', () => {
    expect(parseInline('Use **present simple** after *if*, not `will` <b>')).toEqual([
      { kind: 'text', text: 'Use ' },
      { kind: 'strong', text: 'present simple' },
      { kind: 'text', text: ' after ' },
      { kind: 'em', text: 'if' },
      { kind: 'text', text: ', not ' },
      { kind: 'code', text: 'will' },
      { kind: 'text', text: ' <b>' },
    ]);
  });

  it('splits paragraphs and lists', () => {
    const blocks = parseMarkdown('First line\ncontinues.\n\n- one\n- two\n1. first\n\nEnd');
    expect(blocks.map((block) => block.kind)).toEqual(['p', 'ul', 'ol', 'p']);
    expect(blocks[0]).toEqual({ kind: 'p', inline: [{ kind: 'text', text: 'First line continues.' }] });
    expect(blocks[1]).toMatchObject({ items: [[{ text: 'one' }], [{ text: 'two' }]] });
  });
});
