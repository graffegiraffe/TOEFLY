import { Link } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import type { VocabularyEntry } from '@/api/generated/vocabulary';
import { stabilityLevel } from '@/lib/vocabulary';
import { StabilityDots } from './stability-dots';

/** A row of "My words": word, part of speech, short definition, FSRS dots and the source. */
export function WordRow({ entry }: { entry: VocabularyEntry }) {
  const { t } = useTranslation();
  const source = entry.contexts.at(-1)?.sourceLabel;
  return (
    <li>
      <Link
        to="/vocabulary/$entryId"
        params={{ entryId: entry.id }}
        className="flex items-center gap-4 border-b border-border px-5 py-3 last:border-b-0 hover:bg-surface-sunken focus-visible:outline-2 focus-visible:outline-focus-ring"
      >
        <span className="min-w-0 flex-1">
          <span className="flex items-baseline gap-2">
            <span className="text-title-3 font-semibold text-ink">{entry.lemma}</span>
            {entry.data.partOfSpeech && <span className="text-caption text-ink-muted">{entry.data.partOfSpeech}</span>}
            {entry.card.suspended && (
              <span className="text-caption font-semibold text-warning">{t('vocabulary.suspended')}</span>
            )}
          </span>
          <span className="block truncate text-body text-ink-muted">
            {entry.data.definitionEn ?? t('vocabulary.definitionPending')}
          </span>
          {source && <span className="block text-caption text-ink-muted">{t('vocabulary.from', { source })}</span>}
        </span>
        <StabilityDots level={stabilityLevel(entry.status, entry.card.stability)} />
      </Link>
    </li>
  );
}
