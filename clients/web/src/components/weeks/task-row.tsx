import { Link } from '@tanstack/react-router';
import { ExternalLink } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { WeekTask } from '@/api/generated/planning';
import { cn } from '@/lib/cn';
import { SECTION_TAG, sectionSlug, taskLink } from '@/lib/weeks';
import { TaskStatus } from './task-status';

interface TaskRowProps {
  task: WeekTask;
  weekNo: number;
  /** Shown under the title before the time, for example "2 days overdue". */
  note?: string;
}

/** TaskRow (docs/07-ux-ui.md §3): title, section tag, estimated time, status; opens the task. */
export function TaskRow({ task, weekNo, note }: TaskRowProps) {
  const { t } = useTranslation();
  const link = taskLink(task);
  const meta = [note, t('weeks.minutes', { count: task.estimatedMinutes }), task.legacy ? 'legacy' : null]
    .filter(Boolean)
    .join(' · ');
  const body = (
    <>
      <div className="min-w-0 flex-1">
        <div className="truncate font-semibold text-ink">{task.title}</div>
        <div className="text-caption text-ink-muted">{meta}</div>
      </div>
      <span
        className={cn('hidden rounded-full px-2 py-0.5 text-[11px] font-semibold sm:inline', SECTION_TAG[task.section])}
      >
        {t(`weeks.section.${task.section}`)}
      </span>
      {link.kind === 'external' && <ExternalLink className="size-4 text-ink-muted" aria-hidden="true" />}
      <TaskStatus status={task.status} />
    </>
  );
  const rowClass =
    'flex items-center gap-3 border-b border-border px-4 py-3 last:border-b-0 hover:bg-surface-sunken focus-visible:outline-2';
  if (link.kind === 'player') {
    return (
      <Link
        to="/tasks/$weekTaskId"
        params={{ weekTaskId: task.taskId }}
        search={{ week: task.weekId }}
        className={rowClass}
        data-testid="task-row"
      >
        {body}
      </Link>
    );
  }
  if (link.kind === 'external') {
    return (
      <a href={link.href} target="_blank" rel="noreferrer" className={rowClass} data-testid="task-row">
        {body}
      </a>
    );
  }
  return (
    <Link
      to="/weeks/$weekNumber/$section"
      params={{ weekNumber: String(weekNo), section: sectionSlug(task.section) }}
      className={rowClass}
      data-testid="task-row"
    >
      {body}
    </Link>
  );
}
