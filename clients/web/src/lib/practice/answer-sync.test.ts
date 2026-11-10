import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Answer } from './answers';
import { AnswerSync, DEBOUNCE_MS, OfflineError, StaleVersionError, type AnswerTransport } from './answer-sync';

class MemoryStorage {
  data = new Map<string, string>();
  getItem(key: string) {
    return this.data.get(key) ?? null;
  }
  setItem(key: string, value: string) {
    this.data.set(key, value);
  }
  removeItem(key: string) {
    this.data.delete(key);
  }
}

function transport(serverVersion = { value: 3 }) {
  const calls: string[] = [];
  const t: AnswerTransport & { calls: string[]; offline: boolean } = {
    calls,
    offline: false,
    async save(itemId, answer, version) {
      if (t.offline) throw new OfflineError();
      if (version !== serverVersion.value) throw new StaleVersionError();
      calls.push(`save ${itemId} ${JSON.stringify(answer)} @${version}`);
      return ++serverVersion.value;
    },
    async clear(itemId, version) {
      if (version !== serverVersion.value) throw new StaleVersionError();
      calls.push(`clear ${itemId} @${version}`);
      return ++serverVersion.value;
    },
    async currentVersion() {
      return serverVersion.value;
    },
  };
  return t;
}

const a: Answer = { type: 'CHOICE', optionIds: ['A'] };
const b: Answer = { type: 'CHOICE', optionIds: ['B'] };

describe('US-06.03 autosave', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('AC-06.03.1: the last change of an item is saved after 2 s, once', async () => {
    const t = transport();
    const sync = new AnswerSync('at1', 3, t, new MemoryStorage());
    sync.change('i1', a);
    await vi.advanceTimersByTimeAsync(1000);
    sync.change('i1', b);
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS);
    expect(t.calls).toEqual([`save i1 ${JSON.stringify(b)} @3`]);
    expect(sync.currentVersion).toBe(4);
    expect(sync.hasPending).toBe(false);
  });

  it('AC-06.03.1: a stale version is reloaded and the save repeated', async () => {
    const server = { value: 7 };
    const t = transport(server);
    const sync = new AnswerSync('at1', 5, t, new MemoryStorage());
    sync.change('i1', a);
    sync.change('i2', { type: 'TEXT', text: ' ' });
    await sync.flush();
    expect(t.calls).toEqual([`save i1 ${JSON.stringify(a)} @7`, 'clear i2 @8']);
  });

  it('AC-06.03.1: offline changes stay in storage and go out later in order', async () => {
    const storage = new MemoryStorage();
    const t = transport();
    t.offline = true;
    const sync = new AnswerSync('at1', 3, t, storage);
    sync.change('i1', a);
    sync.change('i2', b);
    await expect(sync.flush()).rejects.toBeInstanceOf(OfflineError);
    sync.dispose();

    const restored = new AnswerSync('at1', 3, t, storage);
    expect(restored.unsynced).toEqual({ i1: a, i2: b });
    t.offline = false;
    await restored.flush();
    expect(t.calls).toEqual([`save i1 ${JSON.stringify(a)} @3`, `save i2 ${JSON.stringify(b)} @4`]);
    expect(storage.data.size).toBe(0);
  });

  it('forgets what the submission took along', () => {
    const storage = new MemoryStorage();
    const sync = new AnswerSync('at1', 3, transport(), storage);
    const listener = vi.fn();
    sync.onChange(listener);
    sync.change('i1', a);
    sync.forget();
    expect(sync.unsynced).toEqual({});
    expect(storage.data.size).toBe(0);
    expect(listener).toHaveBeenCalled();
  });
});
