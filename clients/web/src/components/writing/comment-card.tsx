import { Sparkles, UserRound } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { AnnotationStatus, type InlineAnnotation } from '@/api/generated/assessment';
import { ToneBadge } from '@/components/admin/tone-badge';
import { cn } from '@/lib/cn';

export interface Comment extends InlineAnnotation {
  /** Number of the marker in the text; general comments have none. */
  number?: number;
  byTutor?: boolean;
}

const CATEGORY_TAG: Record<string, string> = {
  GRAMMAR: 'bg-danger-soft text-danger',
  SPELLING: 'bg-danger-soft text-danger',
  VOCABULARY: 'bg-tag-vocabulary text-tag-vocabulary-fg',
};

/**
 * InlineComment card (docs/07-ux-ui.md §3.13): author, category, explanation, suggestion and the grammar unit as
 * text (docs/12 phase 5b decisions 11 and 13: English only, no link to the unit yet).
 */
export function CommentCard({
  comment,
  showStatus = false,
  active = false,
}: {
  comment: Comment;
  showStatus?: boolean;
  active?: boolean;
}) {
  const { t } = useTranslation();
  return (
    <article
      id={`comment-${comment.id}`}
      tabIndex={-1}
      aria-label={
        comment.number ? t('writing.review.commentN', { n: comment.number }) : t('writing.review.generalComment')
      }
      className={cn(
        'flex scroll-mt-24 flex-col gap-2 rounded-xl border bg-surface p-4 text-body text-ink focus-visible:outline-2 focus-visible:outline-focus-ring',
        active ? 'border-primary' : 'border-border',
      )}
    >
      <header className="flex flex-wrap items-center gap-2 text-caption">
        {comment.number !== undefined && (
          <span
            aria-hidden="true"
            className="grid size-5 place-items-center rounded-full bg-surface-sunken text-micro font-semibold text-ink"
          >
            {comment.number}
          </span>
        )}
        {comment.byTutor ? (
          <UserRound className="size-3.5 text-ink-muted" aria-hidden="true" />
        ) : (
          <Sparkles className="size-3.5 text-accent-on-soft" aria-hidden="true" />
        )}
        <span className="font-semibold">
          {comment.byTutor ? t('writing.review.tutor') : t('writing.review.toeflyTutor')}
        </span>
        <span
          className={cn(
            'rounded-full px-2 py-0.5 text-micro font-semibold',
            CATEGORY_TAG[comment.category] ?? 'bg-info-soft text-info',
          )}
        >
          {t(`writing.category.${comment.category}`, { defaultValue: comment.category })}
        </span>
        {showStatus && comment.status === AnnotationStatus.Fixed && (
          <ToneBadge tone="success">{t('writing.review.fixed')}</ToneBadge>
        )}
        {showStatus && comment.status === AnnotationStatus.StillPresent && (
          <ToneBadge tone="warning">{t('writing.review.stillPresent')}</ToneBadge>
        )}
      </header>
      {comment.anchorQuote && comment.start !== null && comment.start !== undefined && (
        <blockquote className="border-l-2 border-border-strong pl-2 text-caption text-ink-muted">
          “{comment.anchorQuote}”
        </blockquote>
      )}
      <p>{comment.message}</p>
      {comment.suggestion && (
        <div className="rounded-lg bg-success-soft px-3 py-2">
          <p className="text-micro font-semibold uppercase tracking-wide text-success">
            {t('writing.review.suggestion')}
          </p>
          <p className="text-body text-ink">{comment.suggestion}</p>
        </div>
      )}
      {comment.grammarTopic && (
        <p className="text-caption text-ink-muted">
          {t('writing.review.reviewUnit', {
            unit: comment.unitTitle ? `${comment.grammarTopic} — ${comment.unitTitle}` : comment.grammarTopic,
          })}
        </p>
      )}
    </article>
  );
}
