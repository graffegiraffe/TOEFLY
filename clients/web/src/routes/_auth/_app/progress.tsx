import { Role } from '@/api/generated';
import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { CalendarClock, CheckCircle2, Clock, Flame } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { streakQuery, studentDashboardQuery, timeReportQuery } from '@/api/analytics';
import { bandEstimateQuery } from '@/api/assessment';
import { ActivityChart } from '@/components/progress/activity-chart';
import { BandChart } from '@/components/progress/band-chart';
import { Freshness } from '@/components/progress/freshness';
import { PaceTable } from '@/components/progress/pace-table';
import { StreakCard } from '@/components/progress/streak-card';
import { Card } from '@/components/ui/card';
import { EmptyState } from '@/components/ui/empty-state';
import { BandRingCard } from '@/components/writing/band-ring';
import { dailyBars, formatDuration } from '@/lib/analytics';

/**
 * Progress dashboard (docs/07-ux-ui.md §5.13): band over the weeks, weekly activity by section, streak, time of the
 * week, tasks done and time against ETS norms. Everything comes from the read models of analytics-service with the
 * time of their last event; the band ring is the current estimate of assessment-service.
 */
export const Route = createFileRoute('/_auth/_app/progress')({
  component: Progress,
});

function Tile({ icon, label, value, testId }: { icon: ReactNode; label: string; value: string; testId: string }) {
  return (
    <Card className="flex flex-col gap-1 p-5" data-testid={testId}>
      <span className="flex items-center gap-2 text-caption font-semibold uppercase tracking-wide text-ink-muted">
        {icon}
        {label}
      </span>
      <span className="text-title-2 font-semibold text-ink">{value}</span>
    </Card>
  );
}

function Progress() {
  const { t } = useTranslation();
  const { profile } = Route.useRouteContext();
  const isStudent = profile.roles.includes(Role.Student);
  const dashboard = useQuery({ ...studentDashboardQuery, enabled: isStudent });
  const streak = useQuery({ ...streakQuery, enabled: isStudent });
  const report = useQuery({ ...timeReportQuery(), enabled: isStudent });
  const estimate = useQuery({ ...bandEstimateQuery, enabled: isStudent });

  if (!isStudent) {
    return (
      <div className="flex flex-col gap-6">
        <h1 className="text-title-1 font-semibold tracking-tight text-ink">{t('analytics.progress.title')}</h1>
        <EmptyState title={t('section.emptyTitle')} text={t('section.emptyText')} />
      </div>
    );
  }
  if (dashboard.isError || streak.isError || report.isError) {
    return (
      <div className="flex flex-col gap-6">
        <h1 className="text-title-1 font-semibold tracking-tight text-ink">{t('analytics.progress.title')}</h1>
        <p role="alert" className="text-body text-danger">
          {t('analytics.progress.loadError')}
        </p>
      </div>
    );
  }
  const data = dashboard.data;
  const week = data?.currentWeek;
  const weekPoint = data?.weeks.find((w) => w.weekNo === week?.weekNo);
  const bars = report.data ? dailyBars(report.data.byDay, report.data.from, report.data.to) : [];
  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-title-1 font-semibold tracking-tight text-ink">{t('analytics.progress.title')}</h1>
        {data && <Freshness asOf={data.asOf} />}
      </header>

      <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.6fr)]">
        {estimate.isPending ? (
          <div className="h-56 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />
        ) : (
          <BandRingCard estimate={estimate.data} />
        )}
        {streak.data ? (
          <StreakCard streak={streak.data} />
        ) : (
          <div className="h-56 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />
        )}
      </div>

      {data ? (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <Tile
            testId="tile-streak"
            icon={<Flame className="size-4" aria-hidden="true" />}
            label={t('analytics.tiles.streak')}
            value={t('analytics.streak.days', { count: data.streak.current })}
          />
          <Tile
            testId="tile-time"
            icon={<Clock className="size-4" aria-hidden="true" />}
            label={t('analytics.tiles.timeThisWeek')}
            value={formatDuration(data.weekActiveSeconds)}
          />
          <Tile
            testId="tile-tasks"
            icon={<CheckCircle2 className="size-4" aria-hidden="true" />}
            label={t('analytics.tiles.tasksDone')}
            value={week ? `${weekPoint?.tasksDone ?? 0} / ${week.tasksTotal}` : '—'}
          />
          <Tile
            testId="tile-exam"
            icon={<CalendarClock className="size-4" aria-hidden="true" />}
            label={t('analytics.tiles.exam')}
            value={
              data.examCountdownDays === null || data.examCountdownDays === undefined
                ? '—'
                : t('analytics.tiles.days', { count: data.examCountdownDays })
            }
          />
        </div>
      ) : (
        <div className="h-24 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />
      )}

      {data && <BandChart weeks={data.weeks} target={data.targets.overall} />}
      {report.data && (
        <ActivityChart bars={bars} goalMinutes={streak.data?.goalMinutes ?? data?.todayGoal.goalMinutes ?? 60} />
      )}
      {report.data && (
        <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
          <PaceTable rows={report.data.byTaskType} />
          <Card className="flex flex-col gap-3" data-testid="time-by-section">
            <h2 className="text-title-3 font-semibold text-ink">{t('analytics.time.title')}</h2>
            <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-1.5 text-body">
              {report.data.bySection.map((row) => (
                <div key={row.section} className="contents">
                  <dt className="text-ink-muted">{t(`weeks.section.${row.section}`)}</dt>
                  <dd className="text-right font-semibold text-ink">{formatDuration(row.activeSeconds)}</dd>
                </div>
              ))}
              <div className="contents">
                <dt className="border-t border-border pt-1.5 text-ink">{t('analytics.time.total')}</dt>
                <dd className="border-t border-border pt-1.5 text-right font-semibold text-ink">
                  {formatDuration(report.data.totalSeconds)}
                </dd>
              </div>
              <div className="contents">
                <dt className="text-ink-muted">{t('analytics.time.plan')}</dt>
                <dd className="text-right text-ink-muted">{formatDuration(report.data.plannedSeconds)}</dd>
              </div>
            </dl>
          </Card>
        </div>
      )}
    </div>
  );
}
