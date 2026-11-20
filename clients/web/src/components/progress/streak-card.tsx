import { Check, Flame } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { Streak } from '@/api/generated/analytics';
import { Card } from '@/components/ui/card';
import { formatDuration, isoDate } from '@/lib/analytics';
import { cn } from '@/lib/cn';

function GoalRing({ fraction, label }: { fraction: number; label: string }) {
  const size = 72;
  const stroke = 7;
  const radius = (size - stroke) / 2;
  const length = 2 * Math.PI * radius;
  const clamped = Math.max(0, Math.min(1, fraction));
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(clamped * 100)}
      className="relative grid size-[72px] flex-none place-items-center"
    >
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true" className="-rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          strokeWidth={stroke}
          className="stroke-surface-sunken"
        />
        {clamped > 0 && (
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="none"
            strokeWidth={stroke}
            strokeLinecap="round"
            className="stroke-brand-accent"
            strokeDasharray={`${length * clamped} ${length}`}
          />
        )}
      </svg>
      <span className="absolute text-body font-semibold text-ink">{Math.round(clamped * 100)}%</span>
    </div>
  );
}

/**
 * Streak and the goal of the day (US-19.01, US-19.02): the current and record streak, "Today done" as soon as the goal
 * is met, the ring of the daily goal and a calendar of the last 30 days.
 */
export function StreakCard({ streak, compact = false }: { streak: Streak; compact?: boolean }) {
  const { t, i18n } = useTranslation();
  const day = new Intl.DateTimeFormat(i18n.language, { day: 'numeric', month: 'short', timeZone: 'UTC' });
  return (
    <Card className="flex flex-col gap-4" data-testid="streak-card">
      <div className="flex items-start gap-4">
        <GoalRing fraction={streak.todayGoalProgress} label={t('analytics.streak.goalRing')} />
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <div className="flex items-center gap-2 text-caption font-semibold uppercase tracking-wide text-ink-muted">
            <Flame className="size-4" aria-hidden="true" />
            {t('analytics.streak.title')}
          </div>
          <p className="text-title-2 font-semibold text-ink" data-testid="streak-current">
            {t('analytics.streak.days', { count: streak.current })}
          </p>
          <p className="text-caption text-ink-muted">
            {t('analytics.streak.record', { count: streak.longest })} ·{' '}
            {t('analytics.streak.goal', {
              done: formatDuration(streak.todayActiveSeconds),
              goal: streak.goalMinutes,
            })}
          </p>
          {streak.todayDone && (
            <span
              className="mt-1 inline-flex w-fit items-center gap-1 rounded-full bg-success-soft px-2.5 py-0.5 text-caption font-semibold text-success"
              data-testid="today-done"
            >
              <Check className="size-3.5" aria-hidden="true" />
              {t('analytics.streak.todayDone')}
            </span>
          )}
        </div>
      </div>
      {!compact && (
        <ol
          aria-label={t('analytics.streak.calendar')}
          className="grid grid-cols-10 gap-1.5"
          data-testid="streak-calendar"
        >
          {streak.days.map((entry) => {
            const label = `${day.format(entry.date)}: ${
              entry.done ? t('analytics.streak.dayDone') : t('analytics.streak.dayMissed')
            }`;
            return (
              <li
                key={isoDate(entry.date)}
                aria-label={label}
                title={label}
                className={cn(
                  'aspect-square rounded-md border',
                  entry.done ? 'border-transparent bg-brand-accent' : 'border-border bg-surface-sunken',
                )}
              />
            );
          })}
        </ol>
      )}
    </Card>
  );
}
