import type { SubmissionVersion } from '@/api/generated/assessment';
import { rangeOf } from '@/lib/writing';
import type { Comment } from './comment-card';

/**
 * The comments of a version: AI annotations and those of a tutor, anchored ones numbered in the order of the text,
 * general ones (no range) after them without a number.
 */
export function reviewComments(version: SubmissionVersion): Comment[] {
  const all: Comment[] = [
    ...(version.evaluation?.annotations ?? []).map((annotation) => ({ ...annotation, byTutor: false })),
    ...(version.tutorEvaluation?.annotations ?? []).map((annotation) => ({ ...annotation, byTutor: true })),
  ];
  const anchored = all
    .map((comment) => ({ comment, range: rangeOf(version.text, comment) }))
    .filter((entry) => entry.range !== null)
    .sort((a, b) => a.range!.start - b.range!.start || a.range!.end - b.range!.end)
    .map(({ comment }, index) => ({ ...comment, number: index + 1 }));
  const anchoredIds = new Set(anchored.map((comment) => comment.id));
  const general = all
    .filter((comment) => !anchoredIds.has(comment.id))
    .map((comment) => ({ ...comment, start: null, end: null }));
  return [...anchored, ...general];
}
