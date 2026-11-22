import { FlaskConical } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useCancelSubscription, useOpenPortal, useResumeSubscription } from '@/api/billing';
import {
  PaymentProviderCode,
  SagaStatus,
  SubscriptionStatus,
  type EntitlementSet,
  type Subscription,
} from '@/api/generated/billing';
import { ToneBadge } from '@/components/admin/tone-badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ProgressBar } from '@/components/ui/progress-bar';
import type { Tone } from '@/lib/admin-ai';
import { canCancel, canResume, freeUsage, planLine, planNameKey, shortDate } from '@/lib/billing';
import { CancelDialog } from './cancel-dialog';

const STATUS_TONE: Record<SubscriptionStatus, Tone> = {
  [SubscriptionStatus.Trialing]: 'accent',
  [SubscriptionStatus.Active]: 'success',
  [SubscriptionStatus.PastDue]: 'warning',
  [SubscriptionStatus.Canceled]: 'neutral',
  [SubscriptionStatus.Expired]: 'neutral',
};

interface Props {
  subscription: Subscription;
  entitlements?: EntitlementSet;
}

/** The current plan of §5.20: headline, status, provider, usage of free AI checks and the cancel/resume actions. */
export function CurrentPlan({ subscription, entitlements }: Props) {
  const { t } = useTranslation();
  const [confirming, setConfirming] = useState(false);
  const cancel = useCancelSubscription();
  const resume = useResumeSubscription();
  const portal = useOpenPortal();

  const plan = t(planNameKey(subscription.planTierCode));
  const line = planLine(subscription);
  const date = (value: Date | null | undefined) => (value ? shortDate(value) : '—');
  const headline = (() => {
    switch (line.kind) {
      case 'free':
        return t('billing.current.free');
      case 'seat':
        return t('billing.current.seat', { plan });
      case 'trial':
        return t('billing.current.trial', { plan, date: date(line.until) });
      case 'renews':
        return t('billing.current.renews', { plan, date: date(line.until) });
      case 'ends':
        return t('billing.current.ends', { plan, date: date(line.until) });
      case 'pastDue':
        return t('billing.current.pastDue', { plan });
      case 'expired':
        return t('billing.current.expired', { plan });
    }
  })();
  const usage = freeUsage(entitlements);
  const until = date(subscription.currentPeriodEnd);

  return (
    <Card className="flex flex-col gap-4" data-testid="current-plan">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1">
          <span className="text-caption font-semibold uppercase tracking-wide text-ink-muted">
            {t('billing.current.label')}
          </span>
          <h2 className="text-title-2 font-semibold text-ink" data-testid="plan-headline">
            {headline}
          </h2>
          {subscription.provider && subscription.provider !== PaymentProviderCode.Manual && (
            <p className="text-caption text-ink-muted">
              {t('billing.current.provider', { provider: t(`billing.providers.${subscription.provider}`) })}
            </p>
          )}
          {subscription.provider === PaymentProviderCode.Manual && (
            <p className="text-caption text-ink-muted">{t('billing.current.manual')}</p>
          )}
          {subscription.pendingPlanTierCode && (
            <p className="text-caption text-ink-muted">
              {t('billing.current.pendingChange', {
                plan: t(planNameKey(subscription.pendingPlanTierCode)),
                date: until,
              })}
            </p>
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          {subscription.status && (
            <ToneBadge tone={STATUS_TONE[subscription.status]}>{t(`billing.status.${subscription.status}`)}</ToneBadge>
          )}
          {subscription.testMode && subscription.provider && (
            <ToneBadge tone="warning">
              <FlaskConical className="mr-1 size-3" aria-hidden="true" />
              {t('billing.current.testMode')}
            </ToneBadge>
          )}
        </div>
      </div>

      {line.kind === 'seat' && <p className="text-body text-ink-muted">{t('billing.current.seatHint')}</p>}
      {line.kind === 'pastDue' && (
        <p role="alert" className="text-body text-warning">
          {t('billing.current.pastDueHint', { plan, date: date(line.until) })}
        </p>
      )}
      {subscription.sagaStatus === SagaStatus.Running && (
        <p role="status" className="text-body text-ink-muted">
          {t('billing.current.activating')}
        </p>
      )}
      {subscription.provider === PaymentProviderCode.AppStore && (
        <div className="flex flex-col gap-1">
          <p className="text-body font-semibold text-ink">{t('billing.current.appStore')}</p>
          <p className="text-caption text-ink-muted">{t('billing.current.appStoreHint')}</p>
        </div>
      )}

      {usage && (
        <div className="flex flex-col gap-2" data-testid="ai-usage">
          <div className="flex items-baseline justify-between gap-2 text-body">
            <span className="font-semibold text-ink">
              {t('billing.usage.label', { used: usage.used, limit: usage.limit })}
            </span>
          </div>
          <ProgressBar
            value={Math.min(100, (usage.used / Math.max(1, usage.limit)) * 100)}
            label={t('billing.usage.label', { used: usage.used, limit: usage.limit })}
          />
          {usage.exhausted && <p className="text-caption text-warning">{t('billing.usage.exhausted')}</p>}
        </div>
      )}

      {(canCancel(subscription) || canResume(subscription) || subscription.provider === PaymentProviderCode.Stripe) && (
        <div className="flex flex-wrap gap-2 border-t border-border pt-4">
          {subscription.provider === PaymentProviderCode.Stripe &&
            subscription.status !== SubscriptionStatus.Expired && (
              <Button
                variant="secondary"
                disabled={portal.isPending}
                onClick={() =>
                  portal.mutate(undefined, { onSuccess: (session) => window.location.assign(session.url) })
                }
              >
                {t('billing.portal')}
              </Button>
            )}
          {canResume(subscription) && (
            <Button variant="secondary" onClick={() => resume.mutate()} disabled={resume.isPending}>
              {t('billing.cancel.resume')}
            </Button>
          )}
          {canCancel(subscription) && (
            <Button variant="ghost" onClick={() => setConfirming(true)}>
              {t('billing.cancel.button')}
            </Button>
          )}
        </div>
      )}
      {subscription.cancelAtPeriodEnd && (
        <p role="status" className="text-caption text-ink-muted">
          {t('billing.cancel.done', { date: until })}
        </p>
      )}

      <CancelDialog
        open={confirming}
        plan={plan}
        until={until}
        pending={cancel.isPending}
        onClose={() => setConfirming(false)}
        onConfirm={() => cancel.mutate(undefined, { onSettled: () => setConfirming(false) })}
      />
    </Card>
  );
}
