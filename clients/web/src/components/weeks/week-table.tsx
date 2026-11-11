import { Link } from '@tanstack/react-router';
import { Check } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { Week, WeekTask } from '@/api/generated/planning';
import { useCompleteTask, useUncompleteTask } from '@/api/planning';
import { cn } from '@/lib/cn';
import { dateOf, SECTION_ORDER, SECTION_TAG, sectionSlug, tasksOf } from '@/lib/weeks';

/**
 * The week table of the tutor's page (docs/07-ux-ui.md §5.7): one row, a column per section, tags of tasks in the
 * order of days with a day dot; a resource opens in a new tab and has its own "done" box.
 */
export function WeekTable({ week, readOnly }: { week: Week; readOnly: boolean }) {
  const { t } = useTranslation();
  return (
    <>
      <div className="hidden overflow-x-auto rounded-xl border border-border bg-surface md:block">
        <table className="w-full min-w-[960px] table-fixed border-collapse text-left">
          <thead>
            <tr className="border-b border-border text-caption font-semibold text-ink-muted">
              <th scope="col" className="w-10 px-3 py-2">
                #
              </th>
              {SECTION_ORDER.map((section) => (
                <th key={section} scope="col" className="px-3 py-2">
                  {t(`weeks.section.${section}`)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            <tr className="align-top">
              <td className="px-3 py-3 font-semibold text-ink">{week.weekNo}</td>
              {SECTION_ORDER.map((section) => (
                <td key={section} className="px-2 py-3">
                  <div className="flex flex-col gap-1.5">
                    {tasksOf(week, section).map((task) => (
                      <TaskTag key={task.taskId} week={week} task={task} readOnly={readOnly} />
                    ))}
                  </div>
                </td>
              ))}
            </tr>
          </tbody>
        </table>
      </div>
      <div className="flex flex-col gap-4 md:hidden">
        {SECTION_ORDER.map((section) => (
          <div key={section}>
            <span className={cn('rounded-full px-2 py-0.5 text-[11px] font-semibold', SECTION_TAG[section])}>
              {t(`weeks.section.${section}`)}
            </span>
            <div className="mt-2 flex flex-wrap gap-1.5">
              {tasksOf(week, section).map((task) => (
                <TaskTag key={task.taskId} week={week} task={task} readOnly={readOnly} />
              ))}
            </div>
          </div>
        ))}
      </div>
    </>
  );
}

function TaskTag({ week, task, readOnly }: { week: Week; task: WeekTask; readOnly: boolean }) {
  const { t, i18n } = useTranslation();
  const complete = useCompleteTask();
  const uncomplete = useUncompleteTask();
  const day = new Intl.DateTimeFormat(i18n.language, { weekday: 'short', month: 'short', day: 'numeric' }).format(
    dateOf(week, task.dayIndex),
  );
  const done = task.status === 'DONE';
  const skipped = task.status === 'SKIPPED';
  const className = cn(
    'inline-flex min-h-5 items-start gap-1.5 rounded-md px-2 py-1 text-[12px] leading-4 font-semibold hover:underline',
    SECTION_TAG[task.section],
    task.legacy && 'border border-dashed border-border-strong',
    skipped && 'line-through',
  );
  const inner = (
    <>
      {done ? (
        <Check className="mt-0.5 size-3 flex-none" strokeWidth={3} aria-hidden="true" />
      ) : (
        <span className="mt-1.5 size-1.5 flex-none rounded-full bg-current opacity-55" aria-hidden="true" />
      )}
      <span className="text-left">{task.title}</span>
    </>
  );
  const title = `${day} · ${t('weeks.minutes', { count: task.estimatedMinutes })} · ${t(`weeks.status.${task.status}`)}`;
  if (task.kind === 'EXTERNAL_RESOURCE' && task.resourceUrl) {
    return (
      <span className="flex items-start gap-1.5">
        <input
          type="checkbox"
          className="mt-1 size-3.5 accent-brand-accent"
          checked={done}
          disabled={readOnly || complete.isPending || uncomplete.isPending}
          aria-label={t('weeks.markResource', { title: task.title })}
          onChange={() => (done ? uncomplete.mutate(task.taskId) : complete.mutate(task.taskId))}
        />
        <a href={task.resourceUrl} target="_blank" rel="noreferrer" className={className} title={title}>
          {inner}
        </a>
      </span>
    );
  }
  if (task.kind === 'ITEM_SET') {
    return (
      <Link
        to="/tasks/$weekTaskId"
        params={{ weekTaskId: task.taskId }}
        search={{ week: task.weekId }}
        className={className}
        title={title}
      >
        {inner}
      </Link>
    );
  }
  return (
    <Link
      to="/weeks/$weekNumber/$section"
      params={{ weekNumber: String(week.weekNo), section: sectionSlug(task.section) }}
      className={className}
      title={title}
    >
      {inner}
    </Link>
  );
}
