import { useQuery } from '@tanstack/react-query';
import { createFileRoute, Link, notFound } from '@tanstack/react-router';
import { ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { WeekSummary } from '@/api/generated/planning';
import { isComposed, planQuery, weeksQuery } from '@/api/planning';
import { Cover } from '@/components/weeks/cover';
import { weekCoverId } from '@/lib/covers';
import { buttonClass } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { ProgressBar } from '@/components/ui/progress-bar';
import { cn } from '@/lib/cn';
import { formatRange } from '@/lib/weeks';

/** Weeks (docs/07-ux-ui.md §5.6, AC-04.01.3, AC-04.09.1): the current week first, then the next ones, then the past. */
export const Route = createFileRoute('/_auth/_app/weeks/')({
  beforeLoad: ({ context }) => {
    if (!context.session.user.roles.includes('STUDENT')) {
      throw notFound();
    }
  },
  component: WeeksPage,
});

function WeeksPage() {
  const { t, i18n } = useTranslation();
  const plan = useQuery(planQuery);
  const weeks = useQuery(weeksQuery);

  if (plan.isPending || weeks.isPending) {
    return <div className="h-96 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />;
  }
  if (!plan.data || weeks.isError) {
    return <EmptyState title={t('weeks.noPlanTitle')} text={t('weeks.noPlanText')} />;
  }
  const all = weeks.data;
  const open = all.filter((week) => week.status === 'IN_PROGRESS' || week.status === 'PUBLISHED');
  const current = open.slice(0, 1);
  const next = all.filter((week) => !current.includes(week) && week.status !== 'COMPLETED');
  const past = all.filter((week) => week.status === 'COMPLETED').reverse();
  const exam = plan.data.examDate
    ? new Intl.DateTimeFormat(i18n.language, { month: 'short', day: 'numeric' }).format(plan.data.examDate)
    : null;
  const caption = [
    t('weeks.planCaption', { count: plan.data.weeksCount }),
    exam ? t('weeks.examOn', { date: exam }) : null,
  ]
    .filter(Boolean)
    .join(' · ');
  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-title-1 font-semibold tracking-tight text-ink">{t('weeks.title')}</h1>
          <p className="text-body text-ink-muted">{caption}</p>
        </div>
        <Link to="/settings/goal" className={buttonClass('ghost')}>
          {t('weeks.changeExamDate')}
        </Link>
      </header>
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3" data-testid="week-grid">
        {[...current, ...next].map((week) => (
          <WeekCard key={week.weekNo} week={week} current={current.includes(week)} />
        ))}
      </div>
      {past.length > 0 && (
        <details className="group">
          <summary className="flex cursor-pointer items-center gap-2 text-title-3 font-semibold text-ink">
            <ChevronRight className="size-4 transition-transform group-open:rotate-90" aria-hidden="true" />
            {t('weeks.completedWeeks')} <span className="text-body font-normal text-ink-muted">({past.length})</span>
          </summary>
          <div className="mt-4 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {past.map((week) => (
              <WeekCard key={week.weekNo} week={week} current={false} />
            ))}
          </div>
        </details>
      )}
    </div>
  );
}

function WeekCard({ week, current }: { week: WeekSummary; current: boolean }) {
  const { t, i18n } = useTranslation();
  const composed = isComposed(week);
  const done = week.status === 'COMPLETED';
  const badge = current ? t('weeks.current') : done ? t('weeks.done') : t('weeks.upcoming');
  const body = (
    <>
      <Cover id={weekCoverId(week.weekNo, week.mockExam)} className="aspect-video rounded-t-xl" />
      <span
        className={cn(
          'absolute left-3 top-3 rounded-full px-2.5 py-0.5 text-[11px] font-semibold',
          current || done ? 'bg-accent-soft text-accent-on-soft' : 'bg-surface text-ink-muted',
        )}
      >
        {badge}
      </span>
      <div className="flex flex-col gap-1 p-4">
        <div className="flex items-center justify-between gap-2">
          <span className="text-title-3 font-semibold text-ink">{t('weeks.weekTitle', { number: week.weekNo })}</span>
          {week.mockExam && (
            <span className="rounded-full bg-primary px-2 py-0.5 text-[11px] font-semibold text-primary-foreground">
              {t('weeks.mockExam')}
            </span>
          )}
        </div>
        <span className="text-caption text-ink-muted">{formatRange(week.startDate, week.endDate, i18n.language)}</span>
        {composed ? (
          <div className="mt-2">
            <ProgressBar value={week.progressPercent ?? 0} label={t('weeks.totalProgress')} />
          </div>
        ) : (
          <span className="mt-1 text-caption text-ink-muted">{t('weeks.willBeBuilt')}</span>
        )}
      </div>
    </>
  );
  const className = 'relative overflow-hidden rounded-xl border border-border bg-surface shadow-e1';
  return composed ? (
    <Link
      to="/weeks/$weekNumber"
      params={{ weekNumber: String(week.weekNo) }}
      className={cn(className, 'transition-shadow hover:shadow-e2')}
      data-testid="week-card"
    >
      {body}
    </Link>
  ) : (
    <div className={className} data-testid="week-card">
      {body}
    </div>
  );
}
