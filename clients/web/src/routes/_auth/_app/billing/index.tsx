import { useQuery } from '@tanstack/react-query';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { entitlementsQuery, paymentsQuery, planTiersQuery, subscriptionQuery } from '@/api/billing';
import { PlanTierCode, type PlanTier } from '@/api/generated/billing';
import { CheckoutDialog } from '@/components/billing/checkout-dialog';
import { CurrentPlan } from '@/components/billing/current-plan';
import { PaymentHistory } from '@/components/billing/payment-history';
import { PlanCards } from '@/components/billing/plan-cards';
import { SeatsPanel } from '@/components/billing/seats-panel';
import { EmptyState } from '@/components/ui/empty-state';
import { canBuy } from '@/lib/billing';

const search = z.object({ retry: z.enum(PlanTierCode).optional() });

/**
 * Billing (docs/07-ux-ui.md §5.20): the current plan, free AI checks, plan cards from the API, payment history,
 * cancellation and, for tutors, the seats. `?retry=<plan>` reopens the checkout after a failed payment.
 */
export const Route = createFileRoute('/_auth/_app/billing/')({
  validateSearch: search,
  component: BillingPage,
});

function BillingPage() {
  const { t } = useTranslation();
  const { session } = Route.useRouteContext();
  const { retry } = Route.useSearch();
  const navigate = useNavigate();
  const roles = session.user.roles;
  const plans = useQuery(planTiersQuery);
  const subscription = useQuery(subscriptionQuery);
  const entitlements = useQuery(entitlementsQuery);
  const payments = useQuery(paymentsQuery);
  const [choice, setChoice] = useState<PlanTier | null>(null);

  const current = subscription.data;
  const retryPlan =
    retry && current
      ? (plans.data?.items.find((item) => item.code === retry && canBuy(item, current, roles)) ?? null)
      : null;
  const dialogPlan = choice ?? retryPlan;
  const closeDialog = () => {
    setChoice(null);
    if (retry) void navigate({ to: '/billing', search: {}, replace: true });
  };

  const failed = plans.isError || subscription.isError;
  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-title-1 font-semibold tracking-tight text-ink">{t('billing.title')}</h1>
        <p className="text-body text-ink-muted">{t('billing.subtitle')}</p>
      </header>
      {failed && <EmptyState title={t('billing.loadFailed')} />}
      {subscription.data && <CurrentPlan subscription={subscription.data} entitlements={entitlements.data} />}
      {subscription.data?.seats && <SeatsPanel subscription={subscription.data} />}
      {plans.data && subscription.data && (
        <PlanCards plans={plans.data.items} subscription={subscription.data} roles={roles} onChoose={setChoice} />
      )}
      {payments.data && <PaymentHistory payments={payments.data.items} />}
      <CheckoutDialog
        key={dialogPlan?.code ?? 'none'}
        plan={dialogPlan}
        providers={plans.data?.providers ?? []}
        onClose={closeDialog}
      />
    </div>
  );
}
