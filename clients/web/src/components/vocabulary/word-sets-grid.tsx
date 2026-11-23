import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import { wordSetsQuery } from '@/api/vocabulary';
import { EmptyState } from '@/components/ui/empty-state';

/** Collins Vocabulary units as cards: theme, "20 words · 8 learned" (docs/07-ux-ui.md §5.14, US-15.04). */
export function WordSetsGrid() {
  const { t } = useTranslation();
  const sets = useQuery(wordSetsQuery);
  if (sets.isPending) {
    return <div className="h-40 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />;
  }
  if (sets.isError) return <EmptyState title={t('errors.generic')} />;
  if (sets.data.items.length === 0) {
    return <EmptyState title={t('vocabulary.units.emptyTitle')} text={t('vocabulary.units.emptyText')} />;
  }
  return (
    <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3" data-testid="word-sets">
      {sets.data.items.map((set) => (
        <li key={set.id}>
          <Link
            to="/vocabulary/units/$wordSetId"
            params={{ wordSetId: set.id }}
            className="flex h-full flex-col gap-2 rounded-xl border border-border bg-surface p-5 shadow-e1 hover:border-tag-vocabulary-fg focus-visible:outline-2 focus-visible:outline-focus-ring"
          >
            <span className="text-caption font-semibold uppercase tracking-wide text-tag-vocabulary-fg">
              {t(`vocabulary.theme.${set.theme}`)}
            </span>
            <span className="text-title-3 font-semibold text-ink">
              {t('vocabulary.units.unit', { n: set.unitNo })} · {set.title}
            </span>
            <span className="text-body text-ink-muted">
              {t('vocabulary.units.progress', { words: set.wordsTotal, learned: set.learned })}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
