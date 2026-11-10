import type { AnswerValue } from '@/api/generated/practice';

/**
 * An answer as the players keep it: plain arrays instead of the generated `Set`, so it can be compared, stored
 * and sent again from the offline queue (docs/05-api-contracts.md §7.1, `AnswerValue`).
 */
export type Answer =
  | { type: 'CHOICE'; optionIds: string[] }
  | { type: 'TEXT'; text: string }
  | { type: 'SEQUENCE'; fragmentIds: (string | null)[] }
  | { type: 'MAPPING'; pairs: { key: string; value: string }[] };

interface RawAnswer {
  type: string;
  optionIds?: Iterable<string>;
  text?: string;
  fragmentIds?: (string | null)[];
  pairs?: { key: string; value: string }[];
}

export function fromApi(value: AnswerValue | null | undefined): Answer | null {
  const raw = value as RawAnswer | null | undefined;
  switch (raw?.type) {
    case 'CHOICE':
      return { type: 'CHOICE', optionIds: [...(raw.optionIds ?? [])] };
    case 'TEXT':
      return { type: 'TEXT', text: raw.text ?? '' };
    case 'SEQUENCE':
      return { type: 'SEQUENCE', fragmentIds: [...(raw.fragmentIds ?? [])] };
    case 'MAPPING':
      return { type: 'MAPPING', pairs: (raw.pairs ?? []).map((pair) => ({ key: pair.key, value: pair.value })) };
    default:
      return null;
  }
}

export function toApi(answer: Answer): AnswerValue {
  switch (answer.type) {
    case 'CHOICE':
      return { type: 'CHOICE', optionIds: new Set(answer.optionIds) } as AnswerValue;
    case 'TEXT':
      return { type: 'TEXT', text: answer.text } as AnswerValue;
    case 'SEQUENCE':
      return { type: 'SEQUENCE', fragmentIds: answer.fragmentIds } as unknown as AnswerValue;
    case 'MAPPING':
      return { type: 'MAPPING', pairs: answer.pairs } as AnswerValue;
  }
}

/** An empty answer is no answer: the player clears it instead of saving it. */
export function isAnswered(answer: Answer | null | undefined): answer is Answer {
  if (!answer) {
    return false;
  }
  switch (answer.type) {
    case 'CHOICE':
      return answer.optionIds.length > 0;
    case 'TEXT':
      return answer.text.trim().length > 0;
    case 'SEQUENCE':
      return answer.fragmentIds.some((id) => id !== null);
    case 'MAPPING':
      return answer.pairs.some((pair) => pair.value.trim().length > 0);
  }
}

export function sameAnswer(a: Answer | null | undefined, b: Answer | null | undefined): boolean {
  return JSON.stringify(a ?? null) === JSON.stringify(b ?? null);
}

/** Text of an answer for the review: option letters, typed text or the assembled fragments. */
export function answerText(answer: Answer | null | undefined, chunkText?: (id: string) => string): string {
  if (!answer) {
    return '';
  }
  switch (answer.type) {
    case 'CHOICE':
      return answer.optionIds.join(', ');
    case 'TEXT':
      return answer.text;
    case 'SEQUENCE':
      return answer.fragmentIds.map((id) => (id === null ? '___' : (chunkText?.(id) ?? id))).join(' ');
    case 'MAPPING':
      return answer.pairs.map((pair) => pair.value).join(', ');
  }
}
