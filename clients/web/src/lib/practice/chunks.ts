/**
 * Build a Sentence (AC-09.01.2, AC-09.01.3): fragments move between the pool and the slots. A slot holds one
 * fragment id or null; the pool is every fragment that is not in a slot.
 */
export type Slots = (string | null)[];

export function emptySlots(count: number): Slots {
  return Array.from({ length: count }, () => null);
}

export function slotsFrom(fragmentIds: (string | null)[] | undefined, count: number): Slots {
  const slots = emptySlots(count);
  fragmentIds?.slice(0, count).forEach((id, i) => {
    slots[i] = id ?? null;
  });
  return slots;
}

export function pool(chunkIds: string[], slots: Slots): string[] {
  const used = new Set(slots.filter((id): id is string => id !== null));
  return chunkIds.filter((id) => !used.has(id));
}

export function firstFree(slots: Slots): number {
  return slots.indexOf(null);
}

/**
 * Puts a fragment into a slot. A fragment already in another slot moves; a fragment that occupied the target goes
 * where the moving one came from (a swap), or back to the pool when the moving one came from the pool.
 */
export function place(slots: Slots, chunkId: string, target: number): Slots {
  if (target < 0 || target >= slots.length) {
    return slots;
  }
  const next = [...slots];
  const from = next.indexOf(chunkId);
  const occupant = next[target];
  if (from === target) {
    return slots;
  }
  if (from >= 0) {
    next[from] = occupant ?? null;
  }
  next[target] = chunkId;
  return next;
}

/** Tap on a pool fragment: first free slot; nothing happens when every slot is taken. */
export function placeInFirstFree(slots: Slots, chunkId: string): Slots {
  const free = firstFree(slots);
  return free < 0 ? slots : place(slots, chunkId, free);
}

export function remove(slots: Slots, chunkId: string): Slots {
  return slots.map((id) => (id === chunkId ? null : id));
}
