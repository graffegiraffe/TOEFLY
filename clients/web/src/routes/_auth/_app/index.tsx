import { Role } from '@/api/generated';
import { useQuery } from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';
import { ClipboardCheck, Target } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { planQuery, todayQuery } from '@/api/planning';
import { studyGoalQuery } from '@/api/queries';
import { TaskRow } from '@/components/weeks/task-row';
import { buttonClass } from '@/components/ui/button';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { bandHint } from '@/lib/band';

export const Route = createFileRoute('/_auth/_app/')({
  component: Home,
});

/**
 * Home (US-05.02): with a plan, the first tasks of Today and the progress of the week lead; before the plan exists the
 * diagnostic block stays (AC-02.04.4; the plan comes from the onboarding until phase 7, docs/12 phase 4 decision 1).
 */
function Home() {
  const { t } = useTranslation();
  const { profile } = Route.useRouteContext();
  const isStudent = profile.roles.includes(Role.Student);
  const goal = useQuery({ ...studyGoalQuery, enabled: isStudent });
  const plan = useQuery({ ...planQuery, enabled: isStudent });
  const today = useQuery({ ...todayQuery, enabled: isStudent && Boolean(plan.data) });
  const hasPlan = Boolean(plan.data);
  const name = profile.displayName?.split(' ')[0];
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-title-1 font-semibold tracking-tight text-ink">
        {name ? t('home.greeting', { name }) : t('home.greetingAnonymous')}
      </h1>
      {isStudent && hasPlan && today.data?.weekNo && (
        <Card className="flex flex-col gap-4 p-0" data-testid="home-today">
          <div className="flex items-center justify-between gap-3 px-6 pt-6">
            <h2 className="text-title-3 font-semibold text-ink">{t('home.todayTitle')}</h2>
            <Link to="/today" className={buttonClass('primary')}>
              {t('home.openToday')}
            </Link>
          </div>
          <div>
            {[...today.data.overdue, ...today.data.today]
              .map((entry) => entry.task)
              .slice(0, 3)
              .map((task) => (
                <TaskRow key={task.taskId} task={task} weekNo={today.data?.weekNo ?? 1} />
              ))}
          </div>
        </Card>
      )}
      {isStudent && !hasPlan && !plan.isPending && (
        <Card className="flex flex-col gap-4 md:flex-row md:items-center">
          <span className="grid size-12 flex-none place-items-center rounded-xl bg-accent-soft text-accent-on-soft">
            <ClipboardCheck className="size-6" aria-hidden="true" />
          </span>
          <div className="flex-1">
            <h2 className="text-title-3 font-semibold text-ink">{t('home.diagnosticTitle')}</h2>
            <p className="text-body text-ink-muted">{t('home.diagnosticText')}</p>
          </div>
          <Button disabled>{t('home.diagnosticButton')}</Button>
        </Card>
      )}
      {isStudent && (
        <div className="grid gap-4 md:grid-cols-2">
          <Card>
            <div className="mb-2 flex items-center gap-2 text-caption font-semibold uppercase tracking-wide text-ink-muted">
              <Target className="size-4" aria-hidden="true" />
              {t('home.goalTitle')}
            </div>
            {goal.data ? (
              <p className="text-title-2 font-semibold text-ink" data-testid="home-goal">
                {bandHint(goal.data.overallTarget)}
              </p>
            ) : (
              <div className="h-7 w-40 animate-pulse rounded bg-surface-sunken" aria-busy="true" />
            )}
          </Card>
          <Card>
            <div className="mb-2 text-caption font-semibold uppercase tracking-wide text-ink-muted">
              {t('home.estimate')}
            </div>
            <p className="text-title-2 font-semibold text-ink">{t('home.needMoreData')}</p>
            <p className="text-body text-ink-muted">{t('home.needMoreDataText')}</p>
          </Card>
        </div>
      )}
    </div>
  );
}
