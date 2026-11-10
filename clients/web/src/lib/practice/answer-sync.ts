import { isAnswered, type Answer } from './answers';

/**
 * Autosave of answers (US-06.03): a change is saved within 2 s, one request at a time with the current stream
 * version in `If-Match`. A stale version (412) reloads the version and repeats; while offline the changes wait in
 * local storage and go out in order once the network is back.
 */
export interface AnswerTransport {
  save(itemId: string, answer: Answer, version: number): Promise<number>;
  clear(itemId: string, version: number): Promise<number>;
  currentVersion(): Promise<number>;
}

export interface Storage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export class StaleVersionError extends Error {}
export class OfflineError extends Error {}

export const DEBOUNCE_MS = 2000;

type Pending = Record<string, Answer | null>;

export class AnswerSync {
  private pending: Pending;
  private timers = new Map<string, ReturnType<typeof setTimeout>>();
  private running: Promise<void> | null = null;
  private listeners = new Set<() => void>();

  constructor(
    private readonly attemptId: string,
    private version: number,
    private readonly transport: AnswerTransport,
    private readonly storage: Storage | null = safeStorage(),
  ) {
    this.pending = this.load();
  }

  /** Answers saved locally but not yet on the server: from an earlier offline session or still waiting. */
  get unsynced(): Pending {
    return { ...this.pending };
  }

  get hasPending(): boolean {
    return Object.keys(this.pending).length > 0;
  }

  get currentVersion(): number {
    return this.version;
  }

  onChange(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  change(itemId: string, answer: Answer | null): void {
    this.pending[itemId] = isAnswered(answer) ? answer : null;
    this.persist();
    clearTimeout(this.timers.get(itemId));
    this.timers.set(
      itemId,
      setTimeout(() => {
        this.timers.delete(itemId);
        void this.flush();
      }, DEBOUNCE_MS),
    );
  }

  /** Sends everything now; resolves when the queue is empty or the network is gone. */
  flush(): Promise<void> {
    if (!this.running) {
      this.running = this.drain().finally(() => {
        this.running = null;
      });
    }
    return this.running;
  }

  /** Stops the timers, for example when the attempt is submitted with the remaining answers. */
  dispose(): void {
    this.timers.forEach((timer) => clearTimeout(timer));
    this.timers.clear();
  }

  /** Called after the submission took the remaining answers along. */
  forget(): void {
    this.dispose();
    this.pending = {};
    this.storage?.removeItem(this.key);
  }

  private async drain(): Promise<void> {
    for (;;) {
      const next = Object.entries(this.pending)[0];
      if (!next) {
        return;
      }
      const [itemId, answer] = next;
      try {
        this.version = await this.send(itemId, answer);
      } catch (error) {
        if (error instanceof StaleVersionError) {
          this.version = await this.transport.currentVersion();
          continue;
        }
        throw error;
      }
      if (this.pending[itemId] === answer) {
        delete this.pending[itemId];
        this.persist();
      }
    }
  }

  private send(itemId: string, answer: Answer | null): Promise<number> {
    return answer ? this.transport.save(itemId, answer, this.version) : this.transport.clear(itemId, this.version);
  }

  private get key(): string {
    return `toefly.answers.${this.attemptId}`;
  }

  private load(): Pending {
    try {
      const raw = this.storage?.getItem(this.key);
      return raw ? (JSON.parse(raw) as Pending) : {};
    } catch {
      return {};
    }
  }

  private persist(): void {
    try {
      if (this.hasPending) {
        this.storage?.setItem(this.key, JSON.stringify(this.pending));
      } else {
        this.storage?.removeItem(this.key);
      }
    } catch {
      // storage is a convenience: the answers are still in memory
    }
    this.listeners.forEach((listener) => listener());
  }
}

function safeStorage(): Storage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage;
  } catch {
    return null;
  }
}
