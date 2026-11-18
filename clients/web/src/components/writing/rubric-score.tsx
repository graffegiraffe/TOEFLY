import { useTranslation } from 'react-i18next';
import type { CriterionScore } from '@/api/generated/assessment';
import { ToneBadge } from '@/components/admin/tone-badge';
import { Card } from '@/components/ui/card';
import { cn } from '@/lib/cn';
import { criterionKey, criterionOrder, formatDelta, formatScore, stepState } from '@/lib/writing';

interface RubricScoreProps {
  score: number;
  taskType: string;
  criteria: readonly CriterionScore[];
  scoreDelta?: number | null;
  previousLabel?: string;
  tutorScore?: number | null;
}

const STEPS = [0, 1, 2, 3, 4, 5];

/** RubricScore (docs/07-ux-ui.md §3.15): the score of 5 with a six-step bar and the rubric criteria. */
export function RubricScore({ score, taskType, criteria, scoreDelta, previousLabel, tutorScore }: RubricScoreProps) {
  const { t } = useTranslation();
  const sorted = [...criteria].sort((a, b) => criterionOrder(a.criterion) - criterionOrder(b.criterion));
  return (
    <Card className="grid gap-6 md:grid-cols-[200px_minmax(0,1fr)]" data-testid="rubric-score">
      <div className="flex flex-col gap-3">
        <p className="flex items-baseline gap-2">
          <span
            className="text-[56px] font-semibold leading-none tracking-tight text-ink tabular-nums"
            data-testid="score-value"
          >
            {formatScore(score)}
          </span>
          <span className="text-body text-ink-muted">{t('writing.review.ofFive')}</span>
        </p>
        <div
          role="img"
          aria-label={t('writing.review.scoreLabel', { score: formatScore(score) })}
          className="flex gap-1"
        >
          {STEPS.map((step) => {
            const state = stepState(step, score);
            return (
              <span
                key={step}
                className={cn(
                  'h-2 flex-1 rounded-full',
                  state === 'active' && 'bg-primary',
                  state === 'half' && 'bg-[linear-gradient(90deg,var(--primary)_50%,var(--surface-sunken)_50%)]',
                  state === 'off' && 'bg-surface-sunken',
                )}
              />
            );
          })}
        </div>
        <div className="flex justify-between text-micro text-ink-muted" aria-hidden="true">
          {STEPS.map((step) => (
            <span key={step}>{step}</span>
          ))}
        </div>
        <div className="flex flex-wrap gap-2">
          {scoreDelta !== null && scoreDelta !== undefined && (
            <ToneBadge tone={scoreDelta > 0 ? 'success' : scoreDelta < 0 ? 'danger' : 'neutral'}>
              <span data-testid="score-delta">
                {t('writing.review.delta', { delta: formatDelta(scoreDelta), version: previousLabel })}
              </span>
            </ToneBadge>
          )}
          {tutorScore !== null && tutorScore !== undefined && (
            <ToneBadge tone="accent">{t('writing.review.tutorScore', { score: formatScore(tutorScore) })}</ToneBadge>
          )}
        </div>
      </div>
      <ul className="flex flex-col divide-y divide-border" aria-label={t('writing.review.criteria')}>
        {sorted.map((criterion) => (
          <li key={criterion.criterion} className="flex flex-col gap-1 py-2 first:pt-0 last:pb-0">
            <div className="flex items-center justify-between gap-3">
              <span className="text-body font-semibold text-ink">
                {t(`writing.criteria.${criterionKey(criterion.criterion, taskType)}`, {
                  defaultValue: criterion.criterion,
                })}
              </span>
              <span
                className="flex items-center gap-1"
                role="img"
                aria-label={t('writing.review.level', { level: criterion.level })}
              >
                {STEPS.slice(1).map((dot) => (
                  <span
                    key={dot}
                    className={cn(
                      'size-2.5 rounded-full border',
                      dot <= criterion.level ? 'border-primary bg-primary' : 'border-border-strong bg-transparent',
                    )}
                  />
                ))}
                <span className="ml-1 text-caption tabular-nums text-ink-muted" aria-hidden="true">
                  {criterion.level}/5
                </span>
              </span>
            </div>
            {criterion.note && <p className="text-caption text-ink-muted">{criterion.note}</p>}
          </li>
        ))}
      </ul>
    </Card>
  );
}

/** RubricScore while the AI check runs: a skeleton with the waiting line. */
export function RubricScoreSkeleton({ slow }: { slow: boolean }) {
  const { t } = useTranslation();
  return (
    <Card className="flex flex-col gap-4" aria-busy="true" data-testid="rubric-evaluating">
      <div className="flex items-center gap-4">
        <div className="h-14 w-20 animate-pulse rounded-lg bg-surface-sunken" />
        <div className="flex flex-1 flex-col gap-2">
          <div className="h-3 w-2/3 animate-pulse rounded bg-surface-sunken" />
          <div className="h-3 w-1/2 animate-pulse rounded bg-surface-sunken" />
        </div>
      </div>
      <p role="status" className="text-body text-ink">
        {slow ? t('writing.review.slow') : t('writing.review.checking')}
      </p>
    </Card>
  );
}
