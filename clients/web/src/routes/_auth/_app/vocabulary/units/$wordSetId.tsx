import { useQuery } from '@tanstack/react-query';
import { createFileRoute, Link, notFound } from '@tanstack/react-router';
import { ChevronLeft } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useEnrollWordSet, wordSetQuery } from '@/api/vocabulary';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';

/** A Collins Vocabulary unit: the word table and "Add to my dictionary" (AC-15.04.1, AC-15.04.2). */
export const Route = createFileRoute('/_auth/_app/vocabulary/units/$wordSetId')({
  beforeLoad: ({ context }) => {
    if (!context.session.user.roles.includes('STUDENT')) {
      throw notFound();
    }
  },
  component: WordSetPage,
});

function WordSetPage() {
  const { t } = useTranslation();
  const { wordSetId } = Route.useParams();
  const set = useQuery(wordSetQuery(wordSetId));
  const enroll = useEnrollWordSet();

  if (set.isPending) return <div className="h-96 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />;
  if (set.isError) return <EmptyState title={t('vocabulary.units.notFound')} />;
  const unit = set.data;
  const missing = unit.wordsTotal - unit.inDictionary;
  return (
    <div className="flex flex-col gap-6">
      <Link
        to="/vocabulary"
        search={{ filter: 'units' }}
        className="inline-flex items-center gap-1 self-start text-body font-semibold text-link hover:text-link-hover hover:underline"
      >
        <ChevronLeft className="size-4" aria-hidden="true" />
        {t('vocabulary.units.back')}
      </Link>
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <p className="text-caption font-semibold uppercase tracking-wide text-tag-vocabulary-fg">
            {unit.sourceLabel} · {t(`vocabulary.theme.${unit.theme}`)}
          </p>
          <h1 className="text-title-1 font-semibold tracking-tight text-ink">{unit.title}</h1>
          <p className="text-body text-ink-muted">
            {t('vocabulary.units.progress', { words: unit.wordsTotal, learned: unit.learned })}
          </p>
        </div>
        {enroll.data ? (
          <p role="status" className="text-body font-semibold text-accent-on-soft">
            {t('vocabulary.units.added', { count: enroll.data.added })}
          </p>
        ) : missing > 0 ? (
          <Button onClick={() => enroll.mutate(unit.id)} disabled={enroll.isPending}>
            {t('vocabulary.units.add', { count: missing })}
          </Button>
        ) : (
          <p className="text-body text-ink-muted">{t('vocabulary.units.allIn')}</p>
        )}
      </header>
      <div className="overflow-x-auto rounded-xl border border-border bg-surface shadow-e1">
        <table className="w-full min-w-[560px] text-left text-body">
          <caption className="sr-only">{t('vocabulary.units.tableCaption', { unit: unit.sourceLabel })}</caption>
          <thead className="border-b border-border text-caption uppercase tracking-wide text-ink-muted">
            <tr>
              <th scope="col" className="px-5 py-3">
                {t('vocabulary.units.word')}
              </th>
              <th scope="col" className="px-5 py-3">
                {t('vocabulary.units.pos')}
              </th>
              <th scope="col" className="px-5 py-3">
                {t('vocabulary.units.definition')}
              </th>
              <th scope="col" className="px-5 py-3">
                {t('vocabulary.units.example')}
              </th>
            </tr>
          </thead>
          <tbody>
            {unit.words.map((word) => (
              <tr key={word.lemma} className="border-b border-border last:border-b-0">
                <th scope="row" className="px-5 py-3 font-semibold text-ink">
                  {word.lemma}
                  {word.ipa && <span className="block text-caption font-normal text-ink-muted">{word.ipa}</span>}
                </th>
                <td className="px-5 py-3 text-ink-muted">{word.partOfSpeech}</td>
                <td className="px-5 py-3 text-ink">{word.definitionEn}</td>
                <td className="px-5 py-3 italic text-ink-muted">{word.exampleEn}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
