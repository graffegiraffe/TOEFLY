import { useQuery } from '@tanstack/react-query';
import { createFileRoute, Link, notFound } from '@tanstack/react-router';
import { ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { todayQuery } from '@/api/planning';
import { TaskRow } from '@/components/weeks/task-row';
import { buttonClass } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { EmptyState } from '@/components/ui/empty-state';
import { ProgressBar } from '@/components/ui/progress-bar';
import { firstOpen } from '@/lib/weeks';

/** Today (docs/07-ux-ui.md §5.5, US-05.01): overdue tasks first, then the tasks of the day, then "Get ahead". */
export const Route = createFileRoute('/_auth/_app/today')({
  beforeLoad: ({ context }) => {
    if (!context.session.user.roles.includes('STUDENT')) {
      throw notFound();
    }
  },
  component: TodayPage,
});

function TodayPage() {
  const { t, i18n } = useTranslation();
  const today = useQuery(todayQuery);

  if (today.isPending) {
    return <div className="h-96 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />;
  }
  if (today.isError) {
    return <EmptyState title={t('errors.generic')} />;
  }
  const data = today.data;
  const date = new Intl.DateTimeFormat(i18n.language, { weekday: 'long', month: 'short', day: 'numeric' }).format(
    data.date, // local midnight of the learner's day (the generated client parses dates so)
  );
  if (!data.weekId || !data.weekNo) {
    return (
      <div className="flex flex-col gap-6">
        <h1 className="text-title-1 font-semibold tracking-tight text-ink">{t('today.title')}</h1>
        <EmptyState title={t('today.noWeek')} text={t('today.noWeekText')} />
      </div>
    );
  }
  const tasks = data.today.map((entry) => entry.task);
  const overdue = data.overdue;
  const next = firstOpen([...overdue.map((entry) => entry.task), ...tasks]);
  const allDone = tasks.length > 0 && overdue.length === 0 && !next;
  const goal = Math.max(1, Math.round(data.estimatedMinutes));
  return (
    <div className="flex flex-col gap-6">
      <header>
        <div className="text-caption text-ink-muted">
          {t('today.caption', { date, week: data.weekNo, day: data.dayIndex })}
        </div>
        <h1 className="text-title-1 font-semibold tracking-tight text-ink">{t('today.title')}</h1>
      </header>
      <Card className="flex items-center gap-4">
        <div className="flex-1">
          <div className="text-caption font-semibold uppercase tracking-wide text-ink-muted">{t('today.goal')}</div>
          <div className="text-title-3 font-semibold text-ink">
            {t('today.goalValue', { done: data.doneMinutes, goal })}
          </div>
        </div>
        <div className="w-40">
          <ProgressBar value={(100 * data.doneMinutes) / goal} label={t('today.goal')} />
        </div>
      </Card>
      {overdue.length > 0 && (
        <section aria-labelledby="overdue">
          <h2 id="overdue" className="text-title-3 font-semibold text-ink">
            {t('today.overdue')}
          </h2>
          <Card className="mt-3 p-0">
            {overdue.map((entry) => (
              <TaskRow
                key={entry.task.taskId}
                task={entry.task}
                weekNo={data.weekNo ?? 1}
                note={t('today.overdueNote', { count: entry.overdueDays ?? 0 })}
              />
            ))}
          </Card>
        </section>
      )}
      <section aria-labelledby="today-tasks">
        <div className="flex items-center justify-between gap-3">
          <h2 id="today-tasks" className="text-title-3 font-semibold text-ink">
            {t('today.tasksTitle', {
              minutes: Math.round(tasks.reduce((sum, task) => sum + task.estimatedMinutes, 0)),
            })}
          </h2>
          {next && <TaskStart task={next} />}
        </div>
        {allDone ? (
          <Card className="mt-3">
            <h3 className="text-title-3 font-semibold text-ink">{t('today.doneTitle')}</h3>
            <p className="text-body text-ink-muted">{t('today.doneText')}</p>
          </Card>
        ) : null}
        {tasks.length > 0 && (
          <Card className="mt-3 p-0" data-testid="today-list">
            {tasks.map((task) => (
              <TaskRow key={task.taskId} task={task} weekNo={data.weekNo ?? 1} />
            ))}
          </Card>
        )}
      </section>
      {allDone && (data.getAhead?.length ?? 0) > 0 && (
        <section aria-labelledby="get-ahead">
          <h2 id="get-ahead" className="text-title-3 font-semibold text-ink">
            {t('today.getAhead')}
          </h2>
          <Card className="mt-3 p-0">
            {(data.getAhead ?? []).map((entry) => (
              <TaskRow key={entry.task.taskId} task={entry.task} weekNo={data.weekNo ?? 1} />
            ))}
          </Card>
        </section>
      )}
      <Link
        to="/weeks/$weekNumber"
        params={{ weekNumber: String(data.weekNo) }}
        className="inline-flex items-center gap-1 self-start text-body font-semibold text-link hover:text-link-hover hover:underline"
      >
        {t('today.openWeek')}
        <ChevronRight className="size-4" aria-hidden="true" />
      </Link>
    </div>
  );
}

function TaskStart({ task }: { task: import('@/api/generated/planning').WeekTask }) {
  const { t } = useTranslation();
  if (task.kind === 'ITEM_SET') {
    return (
      <Link
        to="/tasks/$weekTaskId"
        params={{ weekTaskId: task.taskId }}
        search={{ week: task.weekId }}
        className={buttonClass('primary')}
      >
        {t('today.start')}
      </Link>
    );
  }
  return null;
}
