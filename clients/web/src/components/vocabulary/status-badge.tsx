import { useTranslation } from 'react-i18next';
import type { VocabularyStatus } from '@/api/generated/vocabulary';

/** New, Learning, Review, Mastered (docs/03 §11.5). */
export function StatusBadge({ status }: { status: VocabularyStatus }) {
  const { t } = useTranslation();
  return (
    <span className="rounded-full bg-tag-vocabulary px-2 py-0.5 text-caption font-semibold text-tag-vocabulary-fg">
      {t(`vocabulary.status.${status}`)}
    </span>
  );
}
