import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useChangePlan } from '@/api/billing';
import { PlanTierCode, ResponseError, type Subscription } from '@/api/generated/billing';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ProgressBar } from '@/components/ui/progress-bar';
import { planNameKey, shortDate, tutorSwitchTarget } from '@/lib/billing';
import { formatDate } from '@/lib/dates';

/** Seats of a tutor plan and the switch between Tutor and Tutor Plus (AC-27.05.3–5, INV-BIL-06). */
export function SeatsPanel({ subscription }: { subscription: Subscription }) {
  const { t } = useTranslation();
  const change = useChangePlan();
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null);
  const seats = subscription.seats;
  if (!seats) return null;
  const target = tutorSwitchTarget(subscription);
  const upgrade = target === PlanTierCode.TutorPlus;

  const switchPlan = async () => {
    if (!target) return;
    setMessage(null);
    try {
      const updated = await change.mutateAsync(target);
      const plan = t(planNameKey(target));
      setMessage({
        error: false,
        text: updated.pendingPlanTierCode
          ? t('billing.seats.scheduled', {
              plan,
              date: updated.currentPeriodEnd ? shortDate(updated.currentPeriodEnd) : '—',
            })
          : t('billing.seats.switched', { plan }),
      });
    } catch (failure) {
      const inUse = failure instanceof ResponseError && failure.response.status === 409;
      setMessage({ error: true, text: inUse ? t('billing.seats.seatsInUse') : t('errors.generic') });
    }
  };

  return (
    <Card className="flex flex-col gap-4" data-testid="seats-panel">
      <div className="flex flex-col gap-2">
        <h2 className="text-title-3 font-semibold text-ink">{t('billing.seats.title')}</h2>
        <p className="text-body text-ink">{t('billing.seats.used', { used: seats.used, limit: seats.limit })}</p>
        <ProgressBar
          value={(seats.used / Math.max(1, seats.limit)) * 100}
          label={t('billing.seats.used', { used: seats.used, limit: seats.limit })}
        />
      </div>
      <ul className="grid gap-2 sm:grid-cols-2" aria-label={t('billing.seats.title')}>
        {seats.seats.map((seat) => (
          <li
            key={seat.index}
            className="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-caption"
          >
            <span className="font-semibold text-ink">{t('billing.seats.seat', { index: seat.index })}</span>
            {seat.studentId ? (
              <span className="truncate text-ink">
                {t('billing.seats.student', { id: seat.studentId.slice(0, 8) })}
                {seat.assignedAt && (
                  <span className="text-ink-muted">
                    {' '}
                    · {t('billing.seats.since', { date: formatDate(seat.assignedAt) })}
                  </span>
                )}
              </span>
            ) : (
              <span className="text-ink-muted">{t('billing.seats.free')}</span>
            )}
          </li>
        ))}
      </ul>
      {target && (
        <div className="flex flex-col gap-2 border-t border-border pt-4">
          <p className="text-caption text-ink-muted">
            {upgrade ? t('billing.seats.upgradeHint') : t('billing.seats.downgradeHint')}
          </p>
          <Button
            variant={upgrade ? 'primary' : 'secondary'}
            className="self-start"
            onClick={() => void switchPlan()}
            disabled={change.isPending}
          >
            {t('billing.seats.switchTo', { plan: t(planNameKey(target)) })}
          </Button>
        </div>
      )}
      {message && (
        <p
          role={message.error ? 'alert' : 'status'}
          className={message.error ? 'text-body text-danger' : 'text-body text-ink'}
        >
          {message.text}
        </p>
      )}
    </Card>
  );
}
