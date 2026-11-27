import type { BandSnapshot, SectionStats, TaskTypeScore } from '@/api/generated/tutoragent';

/** "4 h 10 min", "45 min" (docs/07-ux-ui.md §5.11 "14 of 18 tasks · 4 h 10 min"). */
export function formatMinutes(total: number): string {
  const minutes = Math.max(0, Math.round(total));
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${rest} min`;
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
}

/** Done share of a section, 0–100, for its progress bar. */
export function sectionPercent(stats: SectionStats): number {
  return stats.tasksTotal > 0 ? Math.round((100 * stats.tasksDone) / stats.tasksTotal) : 0;
}

/** "+0.5", "−0.5" with a true minus sign; null for no change. */
export function bandDelta(delta: number | null | undefined): string | null {
  if (delta === null || delta === undefined || Math.abs(delta) < 0.05) return null;
  return `${delta > 0 ? '+' : '−'}${Math.abs(delta).toFixed(1)}`;
}

export const BAND_SECTIONS = ['reading', 'listening', 'writing', 'speaking'] as const;

/** The sections of the snapshot that have a value, in the exam order. */
export function bandRows(snapshot: BandSnapshot): { section: (typeof BAND_SECTIONS)[number]; value: number }[] {
  return BAND_SECTIONS.flatMap((section) => {
    const value = snapshot[section];
    return typeof value === 'number' ? [{ section, value }] : [];
  });
}

/** The result of a task type: accuracy for closed tasks, the 0–5 average for Writing and Speaking. */
export function scoreOf(score: TaskTypeScore): { kind: 'accuracy' | 'average'; value: string } | null {
  if (typeof score.accuracyPercent === 'number') {
    return { kind: 'accuracy', value: String(Math.round(score.accuracyPercent)) };
  }
  if (typeof score.averageScore === 'number') {
    return { kind: 'average', value: score.averageScore.toFixed(1) };
  }
  return null;
}

/** `GRAMMAR.SUBJECT_VERB_AGREEMENT` → "Subject verb agreement" when the pattern has no title of its own. */
export function subcategoryTitle(code: string): string {
  const tail = code.includes('.') ? code.slice(code.lastIndexOf('.') + 1) : code;
  const words = tail.toLowerCase().replace(/_/g, ' ').trim();
  return words ? words[0]!.toUpperCase() + words.slice(1) : code;
}
