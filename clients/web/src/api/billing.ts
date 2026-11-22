import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query';
import type {
  CheckoutSession,
  PaymentProviderCode,
  PlanTierCode,
  SagaInstance,
  SagaStatus,
  SubscriptionStatus,
} from './generated/billing';
import { api } from './client';
import { newKey } from './practice';

/**
 * Billing API (docs/05-api-contracts.md §6.12): plans, the current subscription, rights and payments. Rights appear
 * only after the provider's verified webhook and the payment saga, so the result screen polls the checkout session.
 */

export const planTiersQuery = queryOptions({
  queryKey: ['billing', 'plan-tiers'],
  queryFn: () => api.planTiers.listPlanTiers(),
  staleTime: 5 * 60_000,
});

export const subscriptionQuery = queryOptions({
  queryKey: ['billing', 'subscription'],
  queryFn: () => api.subscriptions.getCurrentSubscription(),
  retry: false,
});

export const entitlementsQuery = queryOptions({
  queryKey: ['billing', 'entitlements'],
  queryFn: () => api.entitlements.getMyEntitlements(),
  retry: false,
});

export const paymentsQuery = queryOptions({
  queryKey: ['billing', 'payments'],
  queryFn: () => api.subscriptions.listMyPayments(),
  retry: false,
});

export const checkoutSessionQuery = (sessionId: string) =>
  queryOptions({
    queryKey: ['billing', 'checkout-session', sessionId],
    queryFn: () => api.checkout.getCheckoutSession({ sessionId }),
    retry: false,
  });

export interface AdminSubscriptionFilter {
  status?: SubscriptionStatus;
  tier?: PlanTierCode;
  provider?: PaymentProviderCode;
  cursor?: string;
}

export const adminSubscriptionsQuery = (filter: AdminSubscriptionFilter) =>
  queryOptions({
    queryKey: ['billing', 'admin', 'subscriptions', filter],
    queryFn: () => api.billingAdmin.listSubscriptions({ ...filter, limit: 20 }),
    retry: false,
  });

export const adminSagasQuery = (status?: SagaStatus, cursor?: string) =>
  queryOptions({
    queryKey: ['billing', 'admin', 'sagas', status ?? null, cursor ?? null],
    queryFn: () => api.billingAdmin.listSagas({ status, cursor, limit: 20 }),
    retry: false,
  });

/** Everything that depends on the plan: rights, subscription, payments. Refreshed after a payment or a change. */
export function useRefreshBilling() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ['billing'] });
}

export function checkoutReturnUrl(): string {
  return `${window.location.origin}/billing/checkout/result`;
}

/** Creates the provider's hosted payment session; the caller then leaves for `redirectUrl` (AC-27.03.1). */
export function useCreateCheckout() {
  return useMutation({
    mutationFn: ({ planTierCode, provider }: { planTierCode: PlanTierCode; provider: PaymentProviderCode }) =>
      api.checkout.createCheckoutSession({
        idempotencyKey: newKey(),
        createCheckoutSessionRequest: { planTierCode, provider, returnUrl: checkoutReturnUrl() },
      }) as Promise<CheckoutSession>,
  });
}

export function useCancelSubscription() {
  const refresh = useRefreshBilling();
  return useMutation({
    mutationFn: (reason?: string) =>
      api.subscriptions.cancelSubscription({ idempotencyKey: newKey(), cancelSubscriptionRequest: { reason } }),
    onSuccess: () => refresh(),
  });
}

export function useResumeSubscription() {
  const refresh = useRefreshBilling();
  return useMutation({
    mutationFn: () => api.subscriptions.resumeSubscription(),
    onSuccess: () => refresh(),
  });
}

export function useChangePlan() {
  const refresh = useRefreshBilling();
  return useMutation({
    mutationFn: (planTierCode: PlanTierCode) =>
      api.subscriptions.changePlan({ idempotencyKey: newKey(), changePlanRequest: { planTierCode } }),
    onSuccess: () => refresh(),
  });
}

/** The provider's portal (change card) for Stripe; the caller leaves for its URL. */
export function useOpenPortal() {
  return useMutation({
    mutationFn: () =>
      api.checkout.createPortalSession({
        idempotencyKey: newKey(),
        createPortalSessionRequest: { returnUrl: `${window.location.origin}/billing` },
      }),
  });
}

export function useRetrySaga() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sagaId: string): Promise<SagaInstance> =>
      api.billingAdmin.retrySaga({ sagaId, idempotencyKey: newKey() }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['billing', 'admin', 'sagas'] }),
  });
}
