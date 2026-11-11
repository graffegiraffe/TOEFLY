import { createFileRoute, Link, notFound } from '@tanstack/react-router';
import { Check, ChevronRight, Info, NotebookPen, RefreshCw } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { Week } from '@/api/generated/planning';
import { useFinishWeekEarly } from '@/api/planning';
import { Cover } from '@/components/weeks/cover';
import { sectionCoverId, weekCoverId } from '@/lib/covers';
import { WeekLoader } from '@/components/weeks/week-loader';
import { WeekTable } from '@/components/weeks/week-table';
import { Button, buttonClass } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ProgressBar } from '@/components/ui/progress-bar';
import { dateOf, firstOpen, formatRange, minutes, SECTION_CARDS, sectionProgress, sectionSlug } from '@/lib/weeks';

/** Week page (docs/07-ux-ui.md §5.7, US-04.02): the digital copy of the tutor's page of a week. */
export const Route = createFileRoute('/_auth/_app/weeks/$weekNumber/')({
  beforeLoad: ({ context, params }) => {
    if (!context.session.user.roles.includes('STUDENT') || !/^\d+$/.test(params.weekNumber)) {
      throw notFound();
    }
  },
  component: WeekPage,
});

function WeekPage() {
  const { weekNumber } = Route.useParams();
  return <WeekLoader weekNumber={Number(weekNumber)}>{(week) => <WeekView week={week} />}</WeekLoader>;
}

function WeekView({ week }: { week: Week }) {
  const { t, i18n } = useTranslation();
  const finish = useFinishWeekEarly();
  const open = week.status === 'PUBLISHED' || week.status === 'IN_PROGRESS';
  const day = week.currentDayIndex ?? null;
  const todays = day ? week.sections.flatMap((block) => block.tasks).filter((task) => task.dayIndex === day) : [];
  const next = firstOpen(todays.slice().sort((a, b) => a.ordinal - b.ordinal));
  const progress = Number(week.progressPercent);
  const excluded = week.rebalance?.excludedTaskIds ?? [];
  const allTasks = week.sections.flatMap((block) => block.tasks);
  const excludedTitles = allTasks.filter((task) => excluded.includes(task.taskId)).map((task) => task.title);
  const lastDay = new Intl.DateTimeFormat(i18n.language, { month: 'short', day: 'numeric' }).format(dateOf(week, 7));
  return (
    <div className="flex flex-col gap-6">
      <Cover id={weekCoverId(week.weekNo, Boolean(week.mockExam))} className="h-36 rounded-2xl md:h-[200px]" />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-title-1 font-bold tracking-tight text-ink">
          {t('weeks.weekTitle', { number: week.weekNo })}
        </h1>
        {open && progress >= 100 && week.dayCount === 7 && (
          <Button variant="secondary" disabled={finish.isPending} onClick={() => finish.mutate(week.weekId)}>
            {t('weeks.finishEarly')}
          </Button>
        )}
      </div>
      <dl className="grid grid-cols-[120px_1fr] items-center gap-x-4 gap-y-3 text-body">
        <dt className="text-ink-muted">{t('weeks.date')}</dt>
        <dd className="text-ink">{formatRange(week.startDate, week.endDate, i18n.language)}</dd>
        <dt className="text-ink-muted">{t('weeks.totalProgress')}</dt>
        <dd className="flex max-w-xs items-center gap-3">
          <ProgressBar value={progress} label={t('weeks.totalProgress')} />
          <span className="font-semibold text-ink" data-testid="week-progress">
            {progress}%
          </span>
        </dd>
        {week.status === 'COMPLETED' && (
          <>
            <dt className="text-ink-muted">{t('weeks.statusLabel')}</dt>
            <dd className="text-ink">{t('weeks.weekStatus.COMPLETED')}</dd>
          </>
        )}
      </dl>
      {open && week.dayCount > 7 && day && (
        <Banner icon={<Info className="size-4" aria-hidden="true" />}>
          {t('weeks.extended', { day, next: week.weekNo + 1 })}
        </Banner>
      )}
      {week.status === 'COMPLETED' && (
        <Banner icon={<Check className="size-4" aria-hidden="true" />}>
          {t('weeks.closed', { number: week.weekNo, progress })}
        </Banner>
      )}
      {open && excluded.length > 0 && (
        <Banner icon={<RefreshCw className="size-4" aria-hidden="true" />}>
          <span>{t('weeks.rebalanced')} </span>
          <details className="inline">
            <summary className="inline cursor-pointer font-semibold underline">{t('weeks.seeChanges')}</summary>
            <p className="mt-1">{t('weeks.excluded', { count: excluded.length })}</p>
            <ul className="mt-1 list-disc pl-5">
              {excludedTitles.map((title) => (
                <li key={title}>{title}</li>
              ))}
            </ul>
          </details>
        </Banner>
      )}
      {open && (
        <div className="glass flex flex-wrap items-center gap-4 rounded-[20px] p-4" data-testid="today-strip">
          <div className="min-w-0 flex-1">
            {next ? (
              <>
                <div className="truncate font-semibold text-ink">{t('weeks.todayStrip', { title: next.title })}</div>
                <div className="text-caption text-ink-muted">
                  {t('weeks.todayStripMeta', {
                    section: t(`weeks.section.${next.section}`),
                    minutes: Math.round(minutes(todays)),
                    count: todays.length,
                  })}
                </div>
              </>
            ) : (
              <div className="text-body text-ink-muted">{t('weeks.nothingToday')}</div>
            )}
          </div>
          {next?.kind === 'ITEM_SET' && (
            <Link
              to="/tasks/$weekTaskId"
              params={{ weekTaskId: next.taskId }}
              search={{ week: week.weekId }}
              className={buttonClass('primary')}
            >
              {t('weeks.start')}
            </Link>
          )}
        </div>
      )}
      <section aria-labelledby="week-table">
        <h2 id="week-table" className="text-title-3 font-semibold text-ink">
          {t('weeks.weekTable')}
        </h2>
        <div className="mt-3">
          <WeekTable week={week} readOnly={!open} />
        </div>
      </section>
      <section aria-labelledby="sections">
        <h2 id="sections" className="text-title-3 font-semibold text-ink">
          {t('weeks.sections')}
        </h2>
        <div className="mt-3 grid grid-cols-2 gap-4 lg:grid-cols-3" data-testid="section-cards">
          {SECTION_CARDS.map((section) => {
            const { done, total } = sectionProgress(week, section);
            const complete = total > 0 && done === total;
            return (
              <Link
                key={section}
                to="/weeks/$weekNumber/$section"
                params={{ weekNumber: String(week.weekNo), section: sectionSlug(section) }}
                className="overflow-hidden rounded-xl border border-border bg-surface shadow-e1 transition-shadow hover:shadow-e2"
              >
                <Cover id={sectionCoverId(section)} className="aspect-[16/7]" />
                <div className="flex items-start gap-3 p-4">
                  <span
                    className={
                      complete
                        ? 'grid size-5 place-items-center rounded-md bg-brand-accent text-white'
                        : 'size-5 rounded-md border-2 border-border-strong'
                    }
                    aria-hidden="true"
                  >
                    {complete && <Check className="size-3" strokeWidth={3} />}
                  </span>
                  <div>
                    <div className="font-semibold text-ink">{t(`weeks.section.${section}`)}</div>
                    <div className="text-caption text-ink-muted">
                      {total === 0 ? '—' : t('weeks.sectionDone', { done, total })}
                    </div>
                  </div>
                </div>
              </Link>
            );
          })}
        </div>
      </section>
      <Card className="flex items-center gap-3">
        <span className="grid size-10 place-items-center rounded-xl bg-accent-soft text-accent-on-soft">
          <NotebookPen className="size-5" aria-hidden="true" />
        </span>
        <div className="flex-1">
          <div className="font-semibold text-ink">{t('weeks.weeklyReview')}</div>
          <div className="text-caption text-ink-muted">{t('weeks.weeklyReviewSoon', { date: lastDay })}</div>
        </div>
        <ChevronRight className="size-4 text-ink-muted" aria-hidden="true" />
      </Card>
    </div>
  );
}

function Banner({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <div className="flex items-start gap-2 rounded-xl bg-info-soft px-4 py-3 text-body text-info" role="status">
      <span className="mt-0.5">{icon}</span>
      <div className="flex-1">{children}</div>
    </div>
  );
}
