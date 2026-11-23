import { useQuery } from '@tanstack/react-query';
import { createFileRoute, Link, notFound, useNavigate } from '@tanstack/react-router';
import { Search } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { dueQuery, entriesQuery, type EntryFilter } from '@/api/vocabulary';
import { EntryOrigin, VocabularyStatus } from '@/api/generated/vocabulary';
import { buttonClass } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { WordRow } from '@/components/vocabulary/word-row';
import { WordSetsGrid } from '@/components/vocabulary/word-sets-grid';
import { cn } from '@/lib/cn';

const FILTERS = ['all', 'due', 'mine', 'units', 'learned'] as const;
type Filter = (typeof FILTERS)[number];

const search = z.object({
  q: z.string().max(80).optional(),
  filter: z.enum(FILTERS).optional(),
});

/**
 * Vocabulary (docs/07-ux-ui.md §5.14, US-15.06): search, chips "Due today (12)", "My words", "Collins units",
 * "Learned", rows with the FSRS dots and the source, and "Review 12 words".
 */
export const Route = createFileRoute('/_auth/_app/vocabulary/')({
  validateSearch: search,
  beforeLoad: ({ context }) => {
    if (!context.session.user.roles.includes('STUDENT')) {
      throw notFound();
    }
  },
  component: VocabularyPage,
});

function filterOf(filter: Filter, q: string | undefined): EntryFilter {
  const base: EntryFilter = q ? { q } : {};
  switch (filter) {
    case 'due':
      return { ...base, due: true };
    case 'mine':
      return { ...base, origin: EntryOrigin.Tap };
    case 'learned':
      return { ...base, status: VocabularyStatus.Mastered };
    default:
      return base;
  }
}

function VocabularyPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { q, filter = 'all' } = Route.useSearch();
  const [text, setText] = useState(q ?? '');
  const due = useQuery(dueQuery);
  const entries = useQuery({ ...entriesQuery(filterOf(filter, q)), enabled: filter !== 'units' });
  const toReview = due.data?.items.length ?? 0;

  useEffect(() => {
    const timer = setTimeout(() => {
      const value = text.trim() || undefined;
      if (value !== q) void navigate({ to: '/vocabulary', search: { q: value, filter }, replace: true });
    }, 250);
    return () => clearTimeout(timer);
  }, [text, q, filter, navigate]);

  const chip = (value: Filter) => (
    <button
      key={value}
      type="button"
      aria-pressed={filter === value}
      onClick={() => void navigate({ to: '/vocabulary', search: { q, filter: value } })}
      className={cn(
        'rounded-full border px-3 py-1 text-caption font-semibold focus-visible:outline-2 focus-visible:outline-focus-ring',
        filter === value
          ? 'border-tag-vocabulary-fg bg-tag-vocabulary text-tag-vocabulary-fg'
          : 'border-border text-ink-muted hover:bg-surface-sunken',
      )}
    >
      {value === 'due'
        ? t('vocabulary.filter.due', { count: due.data?.dueCount ?? 0 })
        : t(`vocabulary.filter.${value}`)}
    </button>
  );

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="text-title-1 font-semibold tracking-tight text-ink">{t('vocabulary.title')}</h1>
          <p className="text-body text-ink-muted">{t('vocabulary.subtitle')}</p>
        </div>
        {toReview > 0 && (
          <Link to="/vocabulary/review" className={buttonClass('primary')} data-testid="review-button">
            {t('vocabulary.reviewWords', { count: toReview })}
          </Link>
        )}
      </header>

      <div className="flex flex-col gap-3">
        {filter !== 'units' && (
          <label className="relative block max-w-md">
            <span className="sr-only">{t('vocabulary.search')}</span>
            <Search
              className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-muted"
              aria-hidden="true"
            />
            <input
              type="search"
              value={text}
              onChange={(event) => setText(event.target.value)}
              placeholder={t('vocabulary.search')}
              className="h-11 w-full rounded-lg border border-border bg-surface pl-9 pr-3 text-body text-ink placeholder:text-ink-muted focus-visible:outline-2 focus-visible:outline-focus-ring"
            />
          </label>
        )}
        <div className="flex flex-wrap gap-2" role="group" aria-label={t('vocabulary.filters')}>
          {FILTERS.map(chip)}
        </div>
      </div>

      {filter === 'units' ? (
        <WordSetsGrid />
      ) : entries.isPending ? (
        <div className="h-64 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />
      ) : entries.isError ? (
        <EmptyState title={t('errors.generic')} />
      ) : entries.data.items.length === 0 ? (
        <EmptyState
          title={q || filter !== 'all' ? t('vocabulary.emptyFilterTitle') : t('vocabulary.emptyTitle')}
          text={q || filter !== 'all' ? undefined : t('vocabulary.emptyText')}
        />
      ) : (
        <section aria-label={t('vocabulary.listLabel')}>
          <p className="mb-2 text-caption text-ink-muted">{t('vocabulary.total', { count: entries.data.total })}</p>
          <ul className="rounded-xl border border-border bg-surface shadow-e1" data-testid="word-list">
            {entries.data.items.map((entry) => (
              <WordRow key={entry.id} entry={entry} />
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
