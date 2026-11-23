import { Link } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';

/** The tabs of a closed week (AC-18.03.1): the week itself and its review. */
export function WeekTabs({ weekNo, current }: { weekNo: number; current: 'week' | 'review' }) {
  const { t } = useTranslation();
  const tab = (active: boolean) =>
    active
      ? 'rounded-md bg-surface px-4 py-1.5 text-body font-semibold text-ink shadow-e1'
      : 'rounded-md px-4 py-1.5 text-body text-ink-muted hover:text-ink';
  return (
    <nav aria-label={t('weeklyReview.tabs')} className="flex self-start rounded-lg bg-surface-sunken p-1">
      <Link
        to="/weeks/$weekNumber"
        params={{ weekNumber: String(weekNo) }}
        aria-current={current === 'week' ? 'page' : undefined}
        className={tab(current === 'week')}
      >
        {t('weeklyReview.weekTab')}
      </Link>
      <Link
        to="/weeks/$weekNumber/review"
        params={{ weekNumber: String(weekNo) }}
        aria-current={current === 'review' ? 'page' : undefined}
        className={tab(current === 'review')}
      >
        {t('weeklyReview.tab')}
      </Link>
    </nav>
  );
}
