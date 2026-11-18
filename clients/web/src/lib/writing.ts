// Logic of the Writing screens (docs/07-ux-ui.md §3.12, §3.13, §3.15, §5.10): words, paste guard, annotated
// segments, the word diff between versions and the rubric score.

export const WRITING_TYPES = ['WRITE_EMAIL', 'WRITE_ACADEMIC_DISCUSSION'] as const;
export type WritingType = (typeof WRITING_TYPES)[number];

export function isWritingType(taskType: string | null | undefined): taskType is WritingType {
  return (WRITING_TYPES as readonly string[]).includes(taskType ?? '');
}

/** The ETS time limits: 7 minutes for an email, 10 for an academic discussion. */
export function defaultTimeLimit(taskType: string): number {
  return taskType === 'WRITE_EMAIL' ? 420 : 600;
}

export const DISCUSSION_MIN_WORDS = 100;
/** Below this an email asks "Your response is very short. Submit anyway?" (AC-09.02.3). */
export const EMAIL_SHORT_WORDS = 20;

/** Words as a reader counts them: whitespace-separated tokens with at least one letter or digit. */
export function countWords(text: string): number {
  return text
    .trim()
    .split(/\s+/)
    .filter((token) => /[\p{L}\p{N}]/u.test(token)).length;
}

// ---------------------------------------------------------------------------------------------------------------
// Paste guard: only text copied or cut inside the same editor may be pasted (docs/07-ux-ui.md §3.12).

function normalizeClip(text: string): string {
  return text.replace(/\r\n?/g, '\n').trim();
}

export interface PasteGuard {
  /** Remembers what the learner copied or cut inside the editor. */
  remember(text: string): void;
  /** Whether a paste of this clipboard text is allowed. */
  allows(text: string): boolean;
}

export function createPasteGuard(): PasteGuard {
  let copied = '';
  return {
    remember(text) {
      copied = normalizeClip(text);
    },
    allows(text) {
      const clip = normalizeClip(text);
      return clip.length === 0 || (copied.length > 0 && clip === copied);
    },
  };
}

// ---------------------------------------------------------------------------------------------------------------
// Inline comments: the essay split into segments by UTF-16 offsets (InlineAnnotation.start/end).

export interface Anchored {
  id: string;
  start?: number | null;
  end?: number | null;
  anchorQuote: string;
}

export interface Range {
  id: string;
  start: number;
  end: number;
}

/**
 * Where an annotation sits in the text, or null for a general comment. A range that does not match its quote (the
 * text changed, a model miscounted) falls back to the first occurrence of the quote.
 */
export function rangeOf(text: string, annotation: Anchored): Range | null {
  const { start, end, anchorQuote } = annotation;
  if (start === null || start === undefined || end === null || end === undefined) {
    return null;
  }
  const valid = start >= 0 && end > start && end <= text.length;
  if (valid && (!anchorQuote || text.slice(start, end) === anchorQuote)) {
    return { id: annotation.id, start, end };
  }
  if (anchorQuote) {
    const found = text.indexOf(anchorQuote);
    if (found >= 0) {
      return { id: annotation.id, start: found, end: found + anchorQuote.length };
    }
  }
  return valid ? { id: annotation.id, start, end } : null;
}

export interface Segment {
  text: string;
  start: number;
  /** Annotations covering this segment, in the order they were given. */
  ids: string[];
  /** Annotations that end with this segment: their markers follow it. */
  endsHere: string[];
}

/** Splits the text at every annotation boundary; overlapping annotations share the segments they cover. */
export function splitAnnotated(text: string, annotations: readonly Anchored[]): Segment[] {
  const ranges = annotations.map((annotation) => rangeOf(text, annotation)).filter((r): r is Range => r !== null);
  const cuts = new Set<number>([0, text.length]);
  for (const range of ranges) {
    cuts.add(range.start);
    cuts.add(range.end);
  }
  const points = [...cuts].sort((a, b) => a - b);
  const segments: Segment[] = [];
  for (let i = 0; i < points.length - 1; i++) {
    const start = points[i]!;
    const end = points[i + 1]!;
    if (end <= start) continue;
    segments.push({
      text: text.slice(start, end),
      start,
      ids: ranges.filter((range) => range.start <= start && range.end >= end).map((range) => range.id),
      endsHere: ranges.filter((range) => range.end === end).map((range) => range.id),
    });
  }
  return segments;
}

// ---------------------------------------------------------------------------------------------------------------
// Word diff between two versions ("Compare with Draft", AC-11.05.2).

export type DiffOp = { type: 'same' | 'added' | 'removed'; text: string };

/** Words with the whitespace after them, so the diff renders back into the same text. */
function tokens(text: string): string[] {
  return text.match(/\S+\s*/g) ?? [];
}

/** Word-level diff by the longest common subsequence; neighbouring tokens of one kind are merged. */
export function wordDiff(before: string, after: string): DiffOp[] {
  const a = tokens(before);
  const b = tokens(after);
  const key = (token: string) => token.trimEnd();
  const n = a.length;
  const m = b.length;
  const lcs: Uint32Array[] = Array.from({ length: n + 1 }, () => new Uint32Array(m + 1));
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      lcs[i]![j] = key(a[i]!) === key(b[j]!) ? lcs[i + 1]![j + 1]! + 1 : Math.max(lcs[i + 1]![j]!, lcs[i]![j + 1]!);
    }
  }
  const ops: DiffOp[] = [];
  const push = (type: DiffOp['type'], text: string) => {
    const last = ops[ops.length - 1];
    if (last && last.type === type) last.text += text;
    else ops.push({ type, text });
  };
  let i = 0;
  let j = 0;
  while (i < n && j < m) {
    if (key(a[i]!) === key(b[j]!)) {
      push('same', b[j]!);
      i++;
      j++;
    } else if (lcs[i + 1]![j]! >= lcs[i]![j + 1]!) {
      push('removed', a[i++]!);
    } else {
      push('added', b[j++]!);
    }
  }
  while (i < n) push('removed', a[i++]!);
  while (j < m) push('added', b[j++]!);
  return ops;
}

// ---------------------------------------------------------------------------------------------------------------
// Rubric score (docs/07-ux-ui.md §3.15): 0–5 with a step of 0.5.

/** "4", "4.5". */
export function formatScore(score: number): string {
  return Number.isInteger(score) ? String(score) : score.toFixed(1);
}

/** "+1", "−0.5", "±0". */
export function formatDelta(delta: number): string {
  if (delta === 0) return '±0';
  return `${delta > 0 ? '+' : '−'}${formatScore(Math.abs(delta))}`;
}

export type StepState = 'active' | 'half' | 'off';

/** The state of division 0–5 of the bar: the score's division is active; a .5 score half-lights both neighbours. */
export function stepState(step: number, score: number): StepState {
  if (score === step) return 'active';
  if (!Number.isInteger(score) && (step === Math.floor(score) || step === Math.ceil(score))) return 'half';
  return 'off';
}

/** Rubric criteria of docs/06-ai-agent.md §5.4.3 in their display order. */
export const CRITERIA = [
  'taskAchievement',
  'development',
  'organization',
  'languageRange',
  'accuracy',
  'conventions',
] as const;

export function criterionOrder(criterion: string): number {
  const index = (CRITERIA as readonly string[]).indexOf(criterion);
  return index < 0 ? CRITERIA.length : index;
}

/** The i18n key of a criterion; for an academic discussion `conventions` reads "Contribution". */
export function criterionKey(criterion: string, taskType: string): string {
  if (criterion === 'conventions' && taskType === 'WRITE_ACADEMIC_DISCUSSION') return 'contribution';
  return (CRITERIA as readonly string[]).includes(criterion) ? criterion : 'other';
}

// ---------------------------------------------------------------------------------------------------------------
// Comment filters and colours (docs/07-ux-ui.md §3.13).

export type CommentFilter = 'ALL' | 'TUTOR' | 'GRAMMAR' | 'VOCABULARY' | 'SPELLING' | 'COHERENCE';

const FILTER_CATEGORIES: Record<Exclude<CommentFilter, 'ALL' | 'TUTOR'>, string[]> = {
  GRAMMAR: ['GRAMMAR'],
  VOCABULARY: ['VOCABULARY'],
  SPELLING: ['SPELLING'],
  COHERENCE: ['COHERENCE', 'TASK_RESPONSE'],
};

export function matchesFilter(filter: CommentFilter, comment: { category: string; byTutor?: boolean }): boolean {
  if (filter === 'ALL') return true;
  if (filter === 'TUTOR') return Boolean(comment.byTutor);
  return FILTER_CATEGORIES[filter].includes(comment.category);
}

/** Wavy underline colour of a category. */
export function underlineClass(category: string): string {
  switch (category) {
    case 'GRAMMAR':
    case 'SPELLING':
      return 'decoration-danger';
    case 'VOCABULARY':
      return 'decoration-tag-vocabulary-fg';
    default:
      return 'decoration-info';
  }
}
