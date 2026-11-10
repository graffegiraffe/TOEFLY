/**
 * What a player reads from `Attempt.stimulus` and `AttemptItem.content` (payload schemas of
 * docs/03-domain-model.md §4 without keys). Every field is optional: the players degrade instead of failing.
 */
export interface Option {
  key: string;
  text: string;
}

export interface AudioRef {
  url?: string;
  durationSeconds?: number;
  origin?: string;
}

export interface ItemContent {
  prompt?: string;
  sentence?: string;
  word?: string;
  baseWord?: string;
  keyWord?: string;
  source?: string;
  hint?: string;
  template?: unknown;
  options?: Option[];
  subskill?: string;
  selection?: 'SINGLE' | 'MULTI';
  introSentence?: string;
  maxPoints?: number;
  exerciseType?: string;
  fragments?: Option[];
  // COMPLETE_THE_WORDS
  prefix?: string;
  missingLetters?: number;
  // BUILD_A_SENTENCE
  contextLine?: string;
  chunks?: { id: string; text: string }[];
  // READ_ACADEMIC_PASSAGE, LEGACY_READING
  targetWord?: { paragraph: number; charStart: number; charEnd: number; text: string };
  anchor?: { paragraph: number; charStart: number; charEnd: number; text: string };
  insertion?: { sentence: string; markers: { key: string; paragraph: number; afterSentence: number }[] };
  // LISTEN_CHOOSE_RESPONSE
  audio?: AudioRef;
}

export interface TemplatePart {
  kind: 'FIXED' | 'SLOT';
  text?: string;
  slotId?: string;
}

export interface Stimulus {
  title?: string;
  markup?: string;
  paragraphs?: string[];
  genre?: string;
  body?: string;
  headers?: Record<string, string>;
  messages?: { sender: string; time?: string; text: string }[];
  contextLine?: string;
  audio?: AudioRef;
  speakers?: { id: string; role: string; name?: string }[];
  transcript?: { speaker: string; text: string }[] | string;
  instructions?: string;
  unitRef?: { framework?: string; unitNo?: number; topicTitle?: string; theme?: string };
}

export function templateOf(item: ItemContent): TemplatePart[] {
  return Array.isArray(item.template) ? (item.template as TemplatePart[]) : [];
}

export function slotCount(item: ItemContent): number {
  return templateOf(item).filter((part) => part.kind === 'SLOT').length;
}
