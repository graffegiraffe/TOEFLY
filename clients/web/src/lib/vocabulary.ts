/**
 * Vocabulary logic of the web client (docs/07-ux-ui.md §5.14, US-15.01, US-15.03): labels of FSRS intervals, the
 * stability indicator, the cloze format, turning a text selection into a term, and the queue of a review session.
 */

const MINUTE = 60;
const HOUR = 3600;
const DAY = 86_400;

/** AC-15.03.2: "10m", "1d", "3d", "8d" under the grade buttons. */
export function formatInterval(seconds: number): string {
  if (seconds <= 0) return 'now';
  if (seconds < HOUR) return `${Math.max(1, Math.round(seconds / MINUTE))}m`;
  if (seconds < DAY) return `${Math.round(seconds / HOUR)}h`;
  const days = Math.round(seconds / DAY);
  if (days < 30) return `${days}d`;
  if (days < 365) return `${Math.round(days / 30)}mo`;
  return `${Math.round(days / 365)}y`;
}

/** "12 reviewed · next in 6 h". */
export function nextInLabel(seconds: number): string {
  if (seconds < HOUR) return `${Math.max(1, Math.round(seconds / MINUTE))} min`;
  if (seconds < DAY) return `${Math.round(seconds / HOUR)} h`;
  const days = Math.round(seconds / DAY);
  return days === 1 ? '1 day' : `${days} days`;
}

/** 0–4 dots of the list row: New 0, Learning 1, Review by stability, Mastered (S ≥ 90) 4. */
export function stabilityLevel(status: string, stability: number): number {
  if (status === 'NEW') return 0;
  if (status === 'LEARNING') return 1;
  if (status === 'MASTERED' || stability >= 90) return 4;
  return stability >= 21 ? 3 : 2;
}

export interface Cloze {
  before: string;
  after: string;
}

/** The sentence with the word (or its inflected form) cut out; null when the word is not in it. */
export function clozeOf(sentence: string | null | undefined, lemma: string): Cloze | null {
  if (!sentence) return null;
  const stem = lemma.length > 4 ? lemma.slice(0, lemma.length - 1) : lemma;
  const pattern = new RegExp(`\\b${escapeRegExp(stem)}[\\p{L}'-]*`, 'iu');
  const match = pattern.exec(sentence);
  if (!match) return null;
  return { before: sentence.slice(0, match.index), after: sentence.slice(match.index + match[0].length) };
}

const LETTERS = /^\p{L}[\p{L}\p{M}'’ -]*$/u;

/** AC-15.01.1: a selection is a word or a phrase of up to four words; anything else is ignored. */
export function selectionTerm(raw: string): string | null {
  const text = raw
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/^[^\p{L}]+|[^\p{L}]+$/gu, '');
  if (!text || text.length > 80 || !LETTERS.test(text) || text.split(' ').length > 4) {
    return null;
  }
  return text;
}

/** The sentence of {@code text} that contains the term, at most 300 characters (the context of the entry). */
export function sentenceAround(text: string, term: string): string | undefined {
  const sentences = text.replace(/\s+/g, ' ').split(/(?<=[.!?])\s+/);
  const needle = term.toLowerCase();
  const sentence = sentences.find((candidate) => candidate.toLowerCase().includes(needle));
  return sentence ? sentence.trim().slice(0, 300) : undefined;
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export interface QueueCard {
  id: string;
}

export interface GradeOutcome {
  /** The card stays in LEARNING or RELEARNING (Again, Hard, the first Good of a new card). */
  learning: boolean;
  dueInSeconds: number;
}

/**
 * The cards of one session (AC-15.03.5): a card still in learning comes back after its step (1–10 min); when only
 * waiting cards are left the earliest is shown at once, so the session never stalls.
 */
export class ReviewQueue<T extends QueueCard> {
  private readonly waiting: { card: T; at: number }[] = [];
  private readonly ahead: T[];
  private readonly seen = new Set<string>();
  private readonly total: number;
  private shown = 0;

  constructor(cards: readonly T[]) {
    this.ahead = [...cards];
    this.total = new Set(cards.map((card) => card.id)).size;
  }

  current(now: number = Date.now()): T | undefined {
    const ready = this.waiting.findIndex((entry) => entry.at <= now);
    if (ready >= 0 && (this.ahead.length === 0 || ready === 0)) {
      return this.waiting[ready]?.card;
    }
    if (this.ahead.length > 0) return this.ahead[0];
    return this.waiting[0]?.card;
  }

  /** Records the grade of {@code card} given at {@code now} (ms). */
  answer(card: QueueCard, outcome: GradeOutcome, now: number = Date.now()): void {
    this.shown++;
    this.seen.add(card.id);
    const aheadIndex = this.ahead.findIndex((candidate) => candidate.id === card.id);
    const waitingIndex = this.waiting.findIndex((entry) => entry.card.id === card.id);
    const removed =
      aheadIndex >= 0 ? this.ahead.splice(aheadIndex, 1)[0] : this.waiting.splice(waitingIndex, 1)[0]?.card;
    if (removed && outcome.learning) {
      this.waiting.push({ card: removed, at: now + outcome.dueInSeconds * 1000 });
      this.waiting.sort((a, b) => a.at - b.at);
    }
  }

  /** Number of the next showing in the session (idempotency of the grade, INV-VOC-04). */
  sequence(): number {
    return this.shown;
  }

  finished(): boolean {
    return this.ahead.length === 0 && this.waiting.length === 0;
  }

  progress(): { done: number; total: number } {
    return { done: this.seen.size, total: this.total };
  }
}
