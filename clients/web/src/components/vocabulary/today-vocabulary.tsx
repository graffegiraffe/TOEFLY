import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { Languages } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { dueQuery } from '@/api/vocabulary';
import { buttonClass } from '@/components/ui/button';
import { Card } from '@/components/ui/card';

/**
 * The vocabulary review of the day on Today (AC-15.03.1): the cards due by the end of the learner's local day plus the
 * new cards. Hidden when nothing is due; until planning shows VOCABULARY_REVIEW tasks the count comes from
 * vocabulary-service directly.
 */
export function TodayVocabulary() {
  const { t } = useTranslation();
  const due = useQuery(dueQuery);
  const count = due.data?.items.length ?? 0;
  if (!due.data || count === 0) return null;
  return (
    <Card className="flex flex-wrap items-center gap-4" data-testid="today-vocabulary">
      <span className="flex size-10 items-center justify-center rounded-lg bg-tag-vocabulary text-tag-vocabulary-fg">
        <Languages className="size-5" aria-hidden="true" />
      </span>
      <div className="min-w-0 flex-1">
        <h2 className="text-title-3 font-semibold text-ink">{t('vocabulary.today.title')}</h2>
        <p className="text-body text-ink-muted">
          {t('vocabulary.today.text', { due: due.data.dueCount, fresh: due.data.newCount })}
        </p>
      </div>
      <Link to="/vocabulary/review" className={buttonClass('primary')}>
        {t('vocabulary.today.start', { count })}
      </Link>
    </Card>
  );
}
