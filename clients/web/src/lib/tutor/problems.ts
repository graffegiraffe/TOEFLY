import type { ReplyProblem } from './reply';

/**
 * Refusals of the chat before the model runs come as Problem Details (docs/05 §2.9, tutor-agent-service.yaml):
 * the slug comes from `code` (`EXAM_IN_PROGRESS`) or from the tail of `type` (`…/exam-in-progress`).
 */
export function problemSlug(problem: ReplyProblem & { type?: string }): string | null {
  if (problem.code) return problem.code.toLowerCase().replace(/_/g, '-');
  if (problem.type) return problem.type.split('/').pop() ?? null;
  return null;
}

export type ChatErrorKind =
  'entitlement' | 'exam' | 'busy' | 'rateLimited' | 'guardrail' | 'tooLong' | 'unavailable' | 'notFound' | 'generic';

/** The message the panel shows for a refused or failed request (AC-17.04.4, AC-17.05.2). */
export function chatErrorKind(problem: ReplyProblem & { type?: string }): ChatErrorKind {
  const slug = problemSlug(problem);
  switch (problem.status) {
    case 403:
      return slug === 'entitlement-required' ? 'entitlement' : 'generic';
    case 409:
      if (slug === 'exam-in-progress') return 'exam';
      if (slug === 'message-in-progress') return 'busy';
      return 'generic';
    case 429:
      return 'rateLimited';
    case 422:
      return 'guardrail';
    case 400:
      return slug === 'validation-failed' ? 'tooLong' : 'generic';
    case 404:
      return 'notFound';
    case 503:
      return 'unavailable';
    default:
      if (slug === 'ai-unavailable' || slug === 'dependency-unavailable') return 'unavailable';
      return 'generic';
  }
}

/** Reads a Problem Details body; anything else becomes a problem with the status only. */
export async function readProblem(response: Response): Promise<ReplyProblem & { type?: string }> {
  try {
    const body = (await response.clone().json()) as Record<string, unknown>;
    return {
      status: response.status,
      code: typeof body.code === 'string' ? body.code : undefined,
      title: typeof body.title === 'string' ? body.title : undefined,
      detail: typeof body.detail === 'string' ? body.detail : undefined,
      type: typeof body.type === 'string' ? body.type : undefined,
    };
  } catch {
    return { status: response.status };
  }
}
