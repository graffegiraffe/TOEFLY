import { Check } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { PlanTier, Subscription } from '@/api/generated/billing';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { canBuy, formatPrice, planNameKey } from '@/lib/billing';
import { cn } from '@/lib/cn';

interface Props {
  plans: PlanTier[];
  subscription: Subscription;
  roles: readonly string[];
  onChoose: (plan: PlanTier) => void;
}

/** Plan cards from `PlanTier` of the API (AC-27.01.1): learners see Free and Pro, tutors Tutor and Tutor Plus. */
export function PlanCards({ plans, subscription, roles, onChoose }: Props) {
  const { t } = useTranslation();
  const visible = plans.filter((plan) => roles.includes(plan.audience));
  return (
    <section aria-labelledby="plans-title" className="flex flex-col gap-3">
      <h2 id="plans-title" className="text-title-3 font-semibold text-ink">
        {t('billing.tiers.title')}
      </h2>
      <ul className="grid gap-4 sm:grid-cols-2">
        {visible.map((plan) => {
          const current = plan.code === subscription.planTierCode;
          return (
            <li key={plan.code}>
              <Card
                data-testid={`plan-${plan.code}`}
                className={cn('flex h-full flex-col gap-4', current && 'border-brand-accent')}
              >
                <div className="flex items-start justify-between gap-2">
                  <h3 className="text-title-3 font-semibold text-ink">{t(planNameKey(plan.code))}</h3>
                  {current && (
                    <span className="rounded-full bg-accent-soft px-2 py-0.5 text-micro font-semibold text-accent-on-soft">
                      {t('billing.tiers.current')}
                    </span>
                  )}
                </div>
                <p className="flex items-baseline gap-1">
                  <span className="text-title-1 font-semibold tracking-tight text-ink">
                    {formatPrice(plan.priceMinor, plan.currency)}
                  </span>
                  <span className="text-caption text-ink-muted">{t('billing.tiers.perMonth')}</span>
                </p>
                <ul className="flex flex-1 flex-col gap-1.5 text-body text-ink">
                  {plan.seatLimit != null && <Feature>{t('billing.tiers.seats', { count: plan.seatLimit })}</Feature>}
                  {plan.freeAiCheckLimit != null && (
                    <Feature>{t('billing.tiers.freeChecks', { count: plan.freeAiCheckLimit })}</Feature>
                  )}
                  {plan.entitlements.map((entitlement) => (
                    <Feature key={entitlement}>{t(`billing.entitlements.${entitlement}`)}</Feature>
                  ))}
                  {plan.trialDays > 0 && <Feature muted>{t('billing.tiers.trial', { count: plan.trialDays })}</Feature>}
                </ul>
                {canBuy(plan, subscription, roles) && (
                  <Button
                    onClick={() => onChoose(plan)}
                    aria-label={`${t('billing.tiers.upgrade')}: ${t(planNameKey(plan.code))}`}
                  >
                    {t('billing.tiers.upgrade')}
                  </Button>
                )}
              </Card>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function Feature({ children, muted = false }: { children: React.ReactNode; muted?: boolean }) {
  return (
    <li className={cn('flex items-start gap-2', muted && 'text-ink-muted')}>
      <Check className="mt-0.5 size-4 flex-none text-brand-accent" aria-hidden="true" />
      <span>{children}</span>
    </li>
  );
}
