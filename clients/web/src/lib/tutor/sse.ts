/**
 * A Server-Sent Events parser for streams read with fetch (docs/06-ai-agent.md §4.9). EventSource cannot POST or
 * send the CSRF header, so the chat reads the response body itself. The parser follows the WHATWG rules: lines end
 * with CRLF, LF or CR; `:` starts a comment; `data:` lines join with a newline; a blank line dispatches the event;
 * an event without data is dropped; `id` persists as the last event id.
 */
export interface SseEvent {
  /** `message` when the stream gives no name. */
  event: string;
  data: string;
  /** The last event id seen so far, for `Last-Event-ID` on resume. */
  id: string | null;
}

export class SseParser {
  private buffer = '';
  private event = '';
  private data: string[] = [];
  private lastId: string | null = null;
  private pendingCr = false;
  /** Comments received (`: ping` keeps the connection alive). */
  comments = 0;
  retry: number | null = null;

  /** Feeds a decoded chunk and returns the events it completes. */
  feed(chunk: string): SseEvent[] {
    let text = chunk;
    if (this.pendingCr && text.startsWith('\n')) {
      text = text.slice(1); // the LF of a CRLF split between chunks
    }
    this.pendingCr = false;
    this.buffer += text;
    const out: SseEvent[] = [];
    let start = 0;
    for (let i = 0; i < this.buffer.length; i++) {
      const char = this.buffer[i];
      if (char !== '\n' && char !== '\r') continue;
      this.line(this.buffer.slice(start, i), out);
      if (char === '\r') {
        if (i + 1 < this.buffer.length) {
          if (this.buffer[i + 1] === '\n') i++;
        } else {
          this.pendingCr = true;
        }
      }
      start = i + 1;
    }
    this.buffer = this.buffer.slice(start);
    return out;
  }

  /** Ends the stream: an event without its blank line is incomplete and dropped, as the standard says. */
  end(): SseEvent[] {
    this.buffer = '';
    this.event = '';
    this.data = [];
    return [];
  }

  get lastEventId(): string | null {
    return this.lastId;
  }

  private line(line: string, out: SseEvent[]) {
    if (line === '') {
      if (this.data.length > 0) {
        out.push({ event: this.event || 'message', data: this.data.join('\n'), id: this.lastId });
      }
      this.event = '';
      this.data = [];
      return;
    }
    if (line.startsWith(':')) {
      this.comments += 1;
      return;
    }
    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    let value = colon === -1 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    switch (field) {
      case 'event':
        this.event = value;
        break;
      case 'data':
        this.data.push(value);
        break;
      case 'id':
        if (!value.includes('\0')) this.lastId = value;
        break;
      case 'retry':
        if (/^\d+$/.test(value)) this.retry = Number(value);
        break;
      default:
        break; // unknown fields are ignored
    }
  }
}

/** Reads a response body as SSE and calls `onEvent` for each event; resolves when the body ends. */
export async function readSse(
  body: ReadableStream<Uint8Array>,
  onEvent: (event: SseEvent) => void,
  parser: SseParser = new SseParser(),
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      parser.feed(decoder.decode(value, { stream: true })).forEach(onEvent);
    }
    parser.feed(decoder.decode()).forEach(onEvent);
    parser.end();
  } finally {
    reader.releaseLock();
  }
}
