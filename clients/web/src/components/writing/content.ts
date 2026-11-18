import type { ItemSet } from '@/api/generated/content';

/**
 * What the Writing screens read from the item set payload (content/schemas/payload/WRITE_EMAIL and
 * WRITE_ACADEMIC_DISCUSSION). Every field is optional: the screens degrade instead of failing.
 */
export interface Post {
  name?: string;
  post?: string;
}

export interface WritingStimulus {
  // WRITE_ACADEMIC_DISCUSSION
  course?: string;
  professor?: Post;
  students?: Post[];
  minWords?: number;
  // WRITE_EMAIL
  scenario?: string;
  recipient?: { name?: string; relationship?: string };
  subject?: string;
  bullets?: { id: string; text: string }[];
  timeLimitSeconds?: number;
}

export interface ModelAnswer {
  text: string;
  source?: string;
}

export function stimulusOf(itemSet: ItemSet | undefined): WritingStimulus {
  const payload = itemSet?.payload as { stimulus?: WritingStimulus } | undefined;
  return payload?.stimulus ?? {};
}

/** The model answer of the prompt: `items[0].modelAnswers[0]` (docs/12 phase 5b decision 14). */
export function modelAnswerOf(itemSet: ItemSet | undefined): ModelAnswer | undefined {
  const payload = itemSet?.payload as { items?: { modelAnswers?: ModelAnswer[] }[] } | undefined;
  const answer = payload?.items?.[0]?.modelAnswers?.[0];
  return answer?.text ? answer : undefined;
}

/** "DG" for "Dr. Gupta", "N" for "Nora". */
export function initialsOf(name: string | undefined): string {
  const words = (name ?? '')
    .replace(/^(dr|prof|mr|mrs|ms)\.?\s+/i, '')
    .split(/\s+/)
    .filter(Boolean);
  return words
    .slice(0, 2)
    .map((word) => word.charAt(0).toUpperCase())
    .join('');
}
