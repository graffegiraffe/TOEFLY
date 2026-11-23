import { describe, expect, it } from 'vitest';
import { readSse, SseParser, type SseEvent } from './sse';

function streamOf(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
}

describe('SSE parser (docs/06 §4.9)', () => {
  it('reads event, data and id lines and dispatches on a blank line', () => {
    const parser = new SseParser();
    const events = parser.feed(
      'id: 1\nevent: meta\ndata: {"messageId":"m1"}\n\nid: 2\nevent: token\ndata: {"t":"Hi"}\n\n',
    );
    expect(events).toEqual([
      { event: 'meta', data: '{"messageId":"m1"}', id: '1' },
      { event: 'token', data: '{"t":"Hi"}', id: '2' },
    ]);
    expect(parser.lastEventId).toBe('2');
  });

  it('joins events split across chunks, including a CRLF split in the middle', () => {
    const parser = new SseParser();
    const out: SseEvent[] = [];
    for (const chunk of ['event: tok', 'en\r', '\ndata: {"t":"Let', '\'s"}\r\n', '\r\n'])
      out.push(...parser.feed(chunk));
    expect(out).toEqual([{ event: 'token', data: '{"t":"Let\'s"}', id: null }]);
  });

  it('skips comments such as the keep-alive ping and counts them', () => {
    const parser = new SseParser();
    expect(parser.feed(': ping\n\n: ping\n\n')).toEqual([]);
    expect(parser.comments).toBe(2);
  });

  it('joins several data lines with a newline and names a nameless event "message"', () => {
    const parser = new SseParser();
    expect(parser.feed('data: one\ndata: two\n\n')).toEqual([{ event: 'message', data: 'one\ntwo', id: null }]);
  });

  it('keeps the value without the single leading space, reads retry and ignores unknown fields', () => {
    const parser = new SseParser();
    expect(parser.feed('retry: 3000\nfoo: bar\nevent:done\ndata:  {}\n\n')).toEqual([
      { event: 'done', data: ' {}', id: null },
    ]);
    expect(parser.retry).toBe(3000);
  });

  it('keeps the last id for events without one and drops an event without data', () => {
    const parser = new SseParser();
    expect(parser.feed('id: 7\nevent: meta\n\n')).toEqual([]);
    expect(parser.feed('event: token\ndata: {"t":"a"}\n\n')).toEqual([{ event: 'token', data: '{"t":"a"}', id: '7' }]);
  });

  it('readSse decodes a byte stream and drops an unfinished last event', async () => {
    const events: SseEvent[] = [];
    await readSse(
      streamOf(['id: 1\nevent: token\ndata: {"t":"Пр', 'ивет"}\n\n', 'event: token\ndata: {"t":"x"}']),
      (e) => events.push(e),
    );
    expect(events).toEqual([{ event: 'token', data: '{"t":"Привет"}', id: '1' }]);
  });
});
