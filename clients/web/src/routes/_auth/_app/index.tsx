import { Role } from '@/api/generated';
import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { ClipboardCheck, Target } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { studyGoalQuery } from '@/api/queries';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { bandHint } from '@/lib/band';

export const Route = createFileRoute('/_auth/_app/')({
  component: Home,
});

/** Home before the diagnostic: the diagnostic is the main block, no plan yet (AC-02.04.4). */
function Home() {
  const { t } = useTranslation();
  const { profile } = Route.useRouteContext();
  const isStudent = profile.roles.includes(Role.Student);
  const goal = useQuery({ ...studyGoalQuery, enabled: isStudent });
  const name = profile.displayName?.split(' ')[0];
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-title-1 font-semibold tracking-tight text-ink">
        {name ? t('home.greeting', { name }) : t('home.greetingAnonymous')}
      </h1>
      {isStudent && (
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
