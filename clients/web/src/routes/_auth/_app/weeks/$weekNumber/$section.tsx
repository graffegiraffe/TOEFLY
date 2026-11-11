import { createFileRoute, Link, notFound } from '@tanstack/react-router';
import { ChevronLeft, ExternalLink } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { Week, WeekTask } from '@/api/generated/planning';
import { useCompleteTask, useSkipTask, useUncompleteTask } from '@/api/planning';
import { Cover } from '@/components/weeks/cover';
import { sectionCoverId, weekCoverId } from '@/lib/covers';
import { TaskStatus } from '@/components/weeks/task-status';
import { WeekLoader } from '@/components/weeks/week-loader';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ProgressBar } from '@/components/ui/progress-bar';
import { dateOf, sectionFromSlug, sectionProgress, tasksOf } from '@/lib/weeks';

/** Section page (docs/07-ux-ui.md §5.8): the tasks of one section by day; units and resources are marked by hand. */
export const Route = createFileRoute('/_auth/_app/weeks/$weekNumber/$section')({
  beforeLoad: ({ context, params }) => {
    if (
      !context.session.user.roles.includes('STUDENT') ||
      !/^\d+$/.test(params.weekNumber) ||
      !sectionFromSlug(params.section)
    ) {
      throw notFound();
    }
  },
  component: SectionPage,
});

function SectionPage() {
  const { weekNumber, section } = Route.useParams();
  return (
    <WeekLoader weekNumber={Number(weekNumber)}>
      {(week) => <SectionView week={week} section={sectionFromSlug(section) ?? 'READING'} />}
    </WeekLoader>
  );
}

function SectionView({ week, section }: { week: Week; section: string }) {
  const { t, i18n } = useTranslation();
  const tasks = tasksOf(week, section);
  const { done, total } = sectionProgress(week, section);
  const open = week.status === 'PUBLISHED' || week.status === 'IN_PROGRESS';
  const days = [...new Set(tasks.map((task) => task.dayIndex))].sort((a, b) => a - b);
  const heading = (day: number) => {
    const date = dateOf(week, day);
    return t('weeks.dayHeading', {
      weekday: new Intl.DateTimeFormat(i18n.language, { weekday: 'long' }).format(date).toUpperCase(),
      date: new Intl.DateTimeFormat(i18n.language, { month: 'short', day: 'numeric' }).format(date),
    });
  };
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-5">
      <Link
        to="/weeks/$weekNumber"
        params={{ weekNumber: String(week.weekNo) }}
        className="inline-flex items-center gap-1 self-start text-body text-ink-muted hover:text-ink"
      >
        <ChevronLeft className="size-4" aria-hidden="true" />
        {t('weeks.weekTitle', { number: week.weekNo })}
      </Link>
      <Cover
        id={section === 'RESOURCES' ? weekCoverId(week.weekNo, Boolean(week.mockExam)) : sectionCoverId(section)}
        className="h-[120px] rounded-2xl"
      />
      <h1 className="text-title-1 font-bold tracking-tight text-ink">
        {t(`weeks.section.${section}`)} · {t('weeks.weekTitle', { number: week.weekNo })}
      </h1>
      <div className="flex items-center gap-3">
        <span className="text-ink-muted">{t('weeks.sectionDone', { done, total })}</span>
        <div className="w-48">
          <ProgressBar value={total ? (100 * done) / total : 0} label={t('weeks.totalProgress')} />
        </div>
      </div>
      {days.map((day) => (
        <section key={day} aria-label={heading(day)}>
          <div className="text-caption font-semibold tracking-wide text-ink-muted">{heading(day)}</div>
          <Card className="mt-2 p-0">
            {tasks
              .filter((task) => task.dayIndex === day)
              .map((task) => (
                <SectionTask key={task.taskId} task={task} open={open} />
              ))}
          </Card>
        </section>
      ))}
    </div>
  );
}

function SectionTask({ task, open }: { task: WeekTask; open: boolean }) {
  const { t } = useTranslation();
  const complete = useCompleteTask();
  const uncomplete = useUncompleteTask();
  const skip = useSkipTask();
  const busy = complete.isPending || uncomplete.isPending || skip.isPending;
  const manual = task.kind === 'GRAMMAR_UNIT' || task.kind === 'VOCABULARY_UNIT' || task.kind === 'EXTERNAL_RESOURCE';
  const hint =
    task.kind === 'EXTERNAL_RESOURCE'
      ? t('weeks.resourceHint')
      : manual
        ? t('weeks.unitHint')
        : t('weeks.minutes', { count: task.estimatedMinutes });
  const title =
    task.kind === 'ITEM_SET' ? (
      <Link
        to="/tasks/$weekTaskId"
        params={{ weekTaskId: task.taskId }}
        search={{ week: task.weekId }}
        className="font-semibold text-ink hover:underline"
      >
        {task.title}
      </Link>
    ) : task.kind === 'EXTERNAL_RESOURCE' && task.resourceUrl ? (
      <a
        href={task.resourceUrl}
        target="_blank"
        rel="noreferrer"
        className="inline-flex items-center gap-1 font-semibold text-ink hover:underline"
      >
        {task.title}
        <ExternalLink className="size-3.5" aria-hidden="true" />
      </a>
    ) : (
      <span className="font-semibold text-ink">{task.title}</span>
    );
  return (
    <div
      className="flex flex-wrap items-center gap-3 border-b border-border px-4 py-3 last:border-b-0"
      data-testid="section-task"
    >
      <div className="min-w-0 flex-1">
        <div className="truncate">{title}</div>
        <div className="text-caption text-ink-muted">{hint}</div>
      </div>
      {open && manual && task.status !== 'DONE' && (
        <Button variant="secondary" className="h-9 px-3" disabled={busy} onClick={() => complete.mutate(task.taskId)}>
          {t('weeks.markDone')}
        </Button>
      )}
      {open && task.kind === 'EXTERNAL_RESOURCE' && task.status === 'DONE' && (
        <Button variant="ghost" className="h-9 px-3" disabled={busy} onClick={() => uncomplete.mutate(task.taskId)}>
          {t('weeks.undo')}
        </Button>
      )}
      {open && (task.status === 'PENDING' || task.status === 'IN_PROGRESS') && (
        <Button variant="ghost" className="h-9 px-3" disabled={busy} onClick={() => skip.mutate(task.taskId)}>
          {t('weeks.skip')}
        </Button>
      )}
      <TaskStatus status={task.status} />
    </div>
  );
}
