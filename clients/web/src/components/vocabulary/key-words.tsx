import { useQuery } from '@tanstack/react-query';
import { Sparkles } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { keyWordsQuery, useSaveWords } from '@/api/vocabulary';
import { Button } from '@/components/ui/button';

/**
 * "Add new words" of the review (docs/07-ux-ui.md §5.10, US-15.02): 5–10 key academic words of the text chosen by
 * ai-gateway, all ticked; "Add N words" saves them with their sentences in one action.
 */
export function KeyWords({ itemSetId, sourceLabel }: { itemSetId: string; sourceLabel?: string }) {
  const { t } = useTranslation();
  const words = useQuery(keyWordsQuery(itemSetId));
  const save = useSaveWords();
  const [unticked, setUnticked] = useState<Set<string>>(new Set());

  if (words.isPending) {
    return <div className="h-24 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />;
  }
  const items = words.data?.items ?? [];
  const chosen = items.filter((word) => !unticked.has(word.lemma));
  return (
    <section
      aria-labelledby="key-words"
      className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-6 shadow-e1"
      data-testid="key-words"
    >
      <h2 id="key-words" className="flex items-center gap-2 text-title-3 font-semibold text-ink">
        <Sparkles className="size-5 text-tag-vocabulary-fg" aria-hidden="true" />
        {t('vocabulary.keyWords.title')}
      </h2>
      {words.isError && <p className="text-body text-ink-muted">{t('vocabulary.keyWords.unavailable')}</p>}
      {!words.isError && items.length === 0 && !save.data && (
        <p className="text-body text-ink-muted">{t('vocabulary.keyWords.none')}</p>
      )}
      {save.data ? (
        <p role="status" className="text-body font-semibold text-accent-on-soft">
          {t('vocabulary.keyWords.added', { count: save.data.added })}
        </p>
      ) : (
        items.length > 0 && (
          <>
            <ul className="flex flex-col gap-2">
              {items.map((word) => (
                <li key={word.lemma}>
                  <label className="flex cursor-pointer items-start gap-3">
                    <input
                      type="checkbox"
                      className="mt-1 size-4 accent-[var(--primary)]"
                      checked={!unticked.has(word.lemma)}
                      onChange={(event) => {
                        const next = new Set(unticked);
                        if (event.target.checked) next.delete(word.lemma);
                        else next.add(word.lemma);
                        setUnticked(next);
                      }}
                    />
                    <span>
                      <span className="font-semibold text-ink">{word.lemma}</span>
                      {word.partOfSpeech && (
                        <span className="ml-2 text-caption text-ink-muted">{word.partOfSpeech}</span>
                      )}
                      {word.definitionEn && <span className="block text-body text-ink-muted">{word.definitionEn}</span>}
                    </span>
                  </label>
                </li>
              ))}
            </ul>
            <Button
              className="self-start"
              disabled={chosen.length === 0 || save.isPending}
              onClick={() =>
                save.mutate({
                  words: chosen.map((word) => ({ term: word.lemma, context: word.sentence })),
                  itemSetId,
                  sourceLabel,
                })
              }
            >
              {t('vocabulary.keyWords.add', { count: chosen.length })}
            </Button>
          </>
        )
      )}
    </section>
  );
}
