import { useQuery } from '@tanstack/react-query';
import { createFileRoute, Link, notFound, useNavigate } from '@tanstack/react-router';
import { ArrowRight, Clock } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { itemSetsQuery, openAttemptsQuery, recentAttemptsQuery, useStartAttempt } from '@/api/practice';
import { Button, buttonClass } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { EmptyState } from '@/components/ui/empty-state';
import { cn } from '@/lib/cn';

/** Closed task types by section; the open types arrive with their phases (docs/12 §4, phases 5 and 6). */
export const PRACTICE_GROUPS = [
  { id: 'READING', types: ['COMPLETE_THE_WORDS', 'READ_DAILY_LIFE', 'READ_ACADEMIC_PASSAGE'] },
  {
    id: 'LISTENING',
    types: ['LISTEN_CHOOSE_RESPONSE', 'LISTEN_CONVERSATION', 'LISTEN_ANNOUNCEMENT', 'LISTEN_ACADEMIC_TALK'],
  },
  { id: 'WRITING', types: ['BUILD_A_SENTENCE'] },
  { id: 'GRAMMAR', types: ['GRAMMAR_EXERCISE'] },
  { id: 'VOCABULARY', types: ['VOCAB_EXERCISE'] },
  { id: 'LEGACY', types: ['LEGACY_READING', 'LEGACY_LISTENING'] },
] as const;

const TYPES = PRACTICE_GROUPS.flatMap((group) => group.types) as string[];

const search = z.object({ type: z.string().optional() });

/** Practice by task type (docs/07-ux-ui.md §4.4 `/practice`). */
export const Route = createFileRoute('/_auth/_app/practice/')({
  validateSearch: search,
  beforeLoad: ({ context }) => {
    if (!context.session.user.roles.includes('STUDENT')) {
      throw notFound();
    }
  },
  component: PracticeHub,
});

function PracticeHub() {
  const { t } = useTranslation();
  const { type: requested } = Route.useSearch();
  const type = requested && TYPES.includes(requested) ? requested : 'READ_ACADEMIC_PASSAGE';
  const navigate = useNavigate();
  const open = useQuery(openAttemptsQuery);
  const recent = useQuery(recentAttemptsQuery);
  const sets = useQuery(itemSetsQuery(type));
  const start = useStartAttempt();

  const begin = (itemSetId: string) =>
    start.mutate(itemSetId, {
      onSuccess: (attempt) => void navigate({ to: '/practice/$attemptId', params: { attemptId: attempt.attemptId } }),
    });

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-title-1 font-semibold tracking-tight text-ink">{t('practice.title')}</h1>
        <p className="text-body text-ink-muted">{t('practice.subtitle')}</p>
      </header>

      {open.data && open.data.items.length > 0 && (
        <Card className="flex flex-col gap-3">
          <h2 className="text-title-3 font-semibold text-ink">{t('practice.continueTitle')}</h2>
          <ul className="flex flex-col divide-y divide-border">
            {open.data.items.map((attempt) => (
              <li key={attempt.attemptId} className="flex items-center gap-3 py-2">
                <span className="flex-1 text-body text-ink">{t(`practice.taskType.${attempt.taskType}`)}</span>
                <Link
                  to="/practice/$attemptId"
                  params={{ attemptId: attempt.attemptId }}
                  className={buttonClass('secondary')}
                >
                  {t('practice.continue')}
                </Link>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <nav aria-label={t('practice.title')} className="flex flex-col gap-3">
        {PRACTICE_GROUPS.map((group) => (
          <div key={group.id} className="flex flex-wrap items-center gap-2">
            <span className="w-24 text-caption font-semibold uppercase tracking-wide text-ink-muted">
              {t(`practice.sections.${group.id}`)}
            </span>
            {group.types.map((id) => (
              <Link
                key={id}
                to="/practice"
                search={{ type: id }}
                aria-current={id === type ? 'page' : undefined}
                className={cn(
                  'rounded-full border px-3 py-1.5 text-caption font-semibold focus-visible:outline-2 focus-visible:outline-focus-ring',
                  id === type
                    ? 'border-primary bg-accent-soft text-accent-on-soft'
                    : 'border-border text-ink-muted hover:border-border-strong hover:text-ink',
                )}
              >
                {t(`practice.taskType.${id}`)}
              </Link>
            ))}
          </div>
        ))}
      </nav>

      <section aria-labelledby="sets-title" className="flex flex-col gap-3">
        <h2 id="sets-title" className="text-title-3 font-semibold text-ink">
          {t(`practice.taskType.${type}`)}
        </h2>
        {sets.isPending && <div className="h-40 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />}
        {sets.isError && (
          <p role="alert" className="text-body text-danger">
            {t('practice.loadError')}
          </p>
        )}
        {sets.data && sets.data.items.length === 0 && <EmptyState title={t('practice.noSets')} />}
        {sets.data && sets.data.items.length > 0 && (
          <ul className="grid gap-3 md:grid-cols-2">
            {sets.data.items.map((set) => (
              <li key={set.id}>
                <Card className="flex h-full flex-col gap-3 p-5">
                  <div className="flex-1">
                    <h3 className="text-body font-semibold text-ink">{set.topic}</h3>
                    <p className="mt-1 flex flex-wrap items-center gap-x-3 text-caption text-ink-muted">
                      <span>{t('practice.questions', { count: set.itemCount })}</span>
                      <span className="inline-flex items-center gap-1">
                        <Clock className="size-3" aria-hidden="true" />
                        {t('practice.minutes', { count: Math.max(1, Math.round(set.estimatedMinutes)) })}
                      </span>
                      {set.cefr && <span>{set.cefr}</span>}
                    </p>
                    <p className="mt-1 text-micro text-ink-subtle">{set.source.attribution}</p>
                  </div>
                  <Button
                    variant="secondary"
                    className="self-start"
                    onClick={() => begin(set.id)}
                    disabled={start.isPending}
                    aria-label={`${t('practice.start')}: ${set.topic}`}
                  >
                    {start.isPending && start.variables === set.id ? t('practice.starting') : t('practice.start')}
                    <ArrowRight className="size-4" aria-hidden="true" />
                  </Button>
                </Card>
              </li>
            ))}
          </ul>
        )}
        {start.isError && (
          <p role="alert" className="text-body text-danger">
            {t('practice.player.error')}
          </p>
        )}
      </section>

      {recent.data && recent.data.items.length > 0 && (
        <Card className="flex flex-col gap-3">
          <h2 className="text-title-3 font-semibold text-ink">{t('practice.recentTitle')}</h2>
          <ul className="flex flex-col divide-y divide-border">
            {recent.data.items.map((attempt) => (
              <li key={attempt.attemptId} className="flex items-center gap-3 py-2">
                <span className="flex-1 text-body text-ink">{t(`practice.taskType.${attempt.taskType}`)}</span>
                {attempt.score && (
                  <span className="text-body font-semibold text-ink">
                    {attempt.score.correct}/{attempt.score.total}
                  </span>
                )}
                <Link
                  to="/review/attempts/$attemptId"
                  params={{ attemptId: attempt.attemptId }}
                  className="text-caption font-semibold text-link hover:text-link-hover"
                >
                  {t('practice.review')}
                </Link>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}
