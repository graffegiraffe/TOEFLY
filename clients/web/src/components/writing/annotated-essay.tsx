import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';
import { matchesFilter, splitAnnotated, underlineClass, type CommentFilter } from '@/lib/writing';
import { CommentCard, type Comment } from './comment-card';

const FILTERS: CommentFilter[] = ['ALL', 'TUTOR', 'GRAMMAR', 'VOCABULARY', 'SPELLING', 'COHERENCE'];

/**
 * The essay with InlineComments (docs/07-ux-ui.md §3.13): wavy underlines by category, numbered markers, the
 * comment column of 320px on lg+ and the list under the essay on smaller screens; filter chips above the text.
 */
export function AnnotatedEssay({ text, comments }: { text: string; comments: readonly Comment[] }) {
  const { t } = useTranslation();
  const [filter, setFilter] = useState<CommentFilter>('ALL');
  const [active, setActive] = useState<string | null>(null);
  const hasTutor = comments.some((comment) => comment.byTutor);
  const visible = comments.filter((comment) => matchesFilter(filter, comment));
  const visibleIds = new Set(visible.map((comment) => comment.id));
  const byId = new Map(comments.map((comment) => [comment.id, comment]));
  const segments = useMemo(
    () =>
      splitAnnotated(
        text,
        comments.filter((comment) => comment.number !== undefined),
      ),
    [text, comments],
  );

  const open = (id: string) => {
    setActive(id);
    const card = document.getElementById(`comment-${id}`);
    card?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    card?.focus({ preventScroll: true });
  };

  return (
    <section aria-labelledby="essay-title" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="essay-title" className="text-title-3 font-semibold text-ink">
          {t('writing.review.essay')}
        </h2>
        <div role="group" aria-label={t('writing.review.filter')} className="flex flex-wrap gap-1.5">
          {FILTERS.filter((id) => id !== 'TUTOR' || hasTutor).map((id) => (
            <button
              key={id}
              type="button"
              aria-pressed={filter === id}
              onClick={() => setFilter(id)}
              className={cn(
                'rounded-full border px-3 py-1 text-caption font-semibold focus-visible:outline-2 focus-visible:outline-focus-ring',
                filter === id
                  ? 'border-primary bg-accent-soft text-accent-on-soft'
                  : 'border-border text-ink-muted hover:border-border-strong hover:text-ink',
              )}
            >
              {t(`writing.filter.${id}`)}
            </button>
          ))}
        </div>
      </div>
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div
          className="whitespace-pre-wrap rounded-xl border border-border bg-surface-task p-5 text-body-lg leading-loose text-ink"
          data-testid="essay-text"
        >
          {segments.map((segment) => {
            const shown = segment.ids.filter((id) => visibleIds.has(id));
            const first = shown[0] ? byId.get(shown[0]) : undefined;
            return (
              <span key={segment.start}>
                <span
                  data-comment={shown.length > 0 ? shown.join(' ') : undefined}
                  className={cn(
                    first && 'underline decoration-wavy decoration-2 underline-offset-4',
                    first && underlineClass(first.category),
                    first?.byTutor && 'bg-tag-reading no-underline',
                    shown.some((id) => id === active) && 'bg-accent-soft',
                  )}
                >
                  {segment.text}
                </span>
                {segment.endsHere
                  .filter((id) => visibleIds.has(id))
                  .map((id) => {
                    const comment = byId.get(id)!;
                    return (
                      <button
                        key={id}
                        type="button"
                        onClick={() => open(id)}
                        aria-label={t('writing.review.openComment', { n: comment.number })}
                        className="mx-0.5 inline-grid size-5 -translate-y-1.5 place-items-center rounded-full bg-surface-sunken align-baseline text-micro font-semibold leading-none text-ink hover:bg-accent-soft focus-visible:outline-2 focus-visible:outline-focus-ring"
                      >
                        {comment.number}
                      </button>
                    );
                  })}
              </span>
            );
          })}
        </div>
        <div className="flex flex-col gap-3" aria-label={t('writing.review.comments')} role="region">
          {visible.length === 0 && <p className="text-caption text-ink-muted">{t('writing.review.noComments')}</p>}
          {visible.map((comment) => (
            <CommentCard key={comment.id} comment={comment} active={comment.id === active} />
          ))}
        </div>
      </div>
    </section>
  );
}
