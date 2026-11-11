import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { Week } from '@/api/generated/planning';
import { weekIdOf, weekQuery, weeksQuery } from '@/api/planning';
import { EmptyState } from '@/components/ui/empty-state';

/** Finds the week by its number in the gallery, then loads it (docs/12, phase 4, decision 9). */
export function WeekLoader({ weekNumber, children }: { weekNumber: number; children: (week: Week) => ReactNode }) {
  const { t } = useTranslation();
  const weeks = useQuery(weeksQuery);
  const weekId = weeks.data ? weekIdOf(weeks.data, weekNumber) : undefined;
  const week = useQuery({ ...weekQuery(weekId ?? 'none'), enabled: Boolean(weekId) });

  if (weeks.isPending || (weekId && week.isPending)) {
    return <div className="h-96 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />;
  }
  if (!weekId || week.isError || !week.data) {
    return <EmptyState title={t('weeks.notFound')} />;
  }
  return <>{children(week.data)}</>;
}
