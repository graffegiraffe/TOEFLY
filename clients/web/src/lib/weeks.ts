import type { Week, WeekSection, WeekTask } from '@/api/generated/planning';

/** Sections in the order of the week table (docs/07-ux-ui.md §5.7). */
export const SECTION_ORDER: WeekSection[] = [
  'READING',
  'LISTENING',
  'WRITING',
  'SPEAKING',
  'VOCABULARY',
  'GRAMMAR',
  'RESOURCES',
] as WeekSection[];

/** The six cards of the gallery: resources stay a column of the table only. */
export const SECTION_CARDS = SECTION_ORDER.filter((section) => section !== 'RESOURCES');

export const SECTION_TAG: Record<string, string> = {
  READING: 'bg-tag-reading text-tag-reading-fg',
  LISTENING: 'bg-tag-listening text-tag-listening-fg',
  WRITING: 'bg-tag-writing text-tag-writing-fg',
  SPEAKING: 'bg-tag-speaking text-tag-speaking-fg',
  VOCABULARY: 'bg-tag-vocabulary text-tag-vocabulary-fg',
  GRAMMAR: 'bg-tag-grammar text-tag-grammar-fg',
  RESOURCES: 'bg-tag-resources text-tag-resources-fg',
};

export function sectionSlug(section: string): string {
  return section.toLowerCase();
}

export function sectionFromSlug(slug: string): WeekSection | undefined {
  return SECTION_ORDER.find((section) => sectionSlug(section) === slug);
}

export function tasksOf(week: Week, section: string): WeekTask[] {
  return (week.sections.find((block) => block.section === section)?.tasks ?? [])
    .slice()
    .sort((a, b) => a.dayIndex - b.dayIndex || a.ordinal - b.ordinal);
}

/** Tasks that count: skipped ones by a rebalance, the tutor or the plan leave the section total (AC-04.02.3). */
export function counted(tasks: readonly WeekTask[]): WeekTask[] {
  return tasks.filter(
    (task) =>
      !(
        task.status === 'SKIPPED' &&
        (task.skippedReason === 'REBALANCE' ||
          task.skippedReason === 'TUTOR' ||
          task.skippedReason === 'NO_ENTITLEMENT')
      ),
  );
}

export function sectionProgress(week: Week, section: string): { done: number; total: number } {
  const tasks = counted(tasksOf(week, section));
  return { done: tasks.filter((task) => task.status === 'DONE').length, total: tasks.length };
}

/** The generated client parses a `date` as local midnight of that day, so days are read in local time. */
export function dateOf(week: Week, dayIndex: number): Date {
  const start = week.startDate;
  return new Date(start.getFullYear(), start.getMonth(), start.getDate() + dayIndex - 1);
}

/** "Nov 10 – Nov 16, 2026" in the learner's locale; dates are local days, not instants. */
export function formatRange(start: Date, end: Date, locale = 'en-US'): string {
  const day = (date: Date) => new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const short = new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric' });
  const year = new Intl.DateTimeFormat(locale, { year: 'numeric' });
  return `${short.format(day(start))} – ${short.format(day(end))}, ${year.format(day(end))}`;
}

/** The first open task of today, for the Today strip and the Start button. */
export function firstOpen(tasks: readonly WeekTask[]): WeekTask | undefined {
  return tasks.find((task) => task.status === 'PENDING' || task.status === 'IN_PROGRESS');
}

export function minutes(tasks: readonly WeekTask[]): number {
  return tasks.reduce((sum, task) => sum + task.estimatedMinutes, 0);
}

/** Where a task opens: the player for item sets, the section page for units and materials, the link for resources. */
export function taskLink(task: WeekTask): { kind: 'player' | 'external' | 'section'; href?: string } {
  if (task.kind === 'ITEM_SET') {
    return { kind: 'player' };
  }
  if (task.kind === 'EXTERNAL_RESOURCE' && task.resourceUrl) {
    return { kind: 'external', href: task.resourceUrl };
  }
  return { kind: 'section' };
}
