import type { Page } from '@playwright/test';
import { API } from './practice-stub';

/**
 * A billing backend for the billing screens (docs/05-api-contracts.md §6.12): a Student Free learner with 10 of 10
 * AI checks used buys Pro with Stripe in test mode. The hosted checkout of the provider is a stub that sends the
 * browser straight back to the result page; the session stays OPEN for the first poll (webhook not yet in), then
 * COMPLETED, or FAILED with the provider's reason when `decline` is set. billing-service itself is covered by its
 * component tests.
 */
export const PROVIDER = 'https://checkout.stripe.test';

export interface BillingStubState {
  checkouts: { planTierCode: string; provider: string; returnUrl: string }[];
  cancellations: number;
  paid: boolean;
}

interface Options {
  roles?: string[];
  decline?: boolean;
  subscribed?: boolean;
}

const PERIOD_END = '2026-12-21T10:00:00Z';

const tiers = {
  items: [
    {
      code: 'STUDENT_FREE',
      audience: 'STUDENT',
      priceMinor: 0,
      currency: 'EUR',
      interval: 'MONTH',
      trialDays: 0,
      freeAiCheckLimit: 10,
      entitlements: ['AI_WRITING_CHECK', 'AI_SPEAKING_CHECK', 'SPEAKING', 'WEEKLY_REVIEW'],
    },
    {
      code: 'STUDENT_PRO',
      audience: 'STUDENT',
      priceMinor: 1200,
      currency: 'EUR',
      interval: 'MONTH',
      trialDays: 7,
      entitlements: ['UNLIMITED_WEEKS', 'AI_WRITING_CHECK', 'AI_SPEAKING_CHECK', 'MOCK_EXAM', 'AI_TUTOR_CHAT'],
    },
    {
      code: 'TUTOR',
      audience: 'TUTOR',
      priceMinor: 2900,
      currency: 'EUR',
      interval: 'MONTH',
      trialDays: 0,
      seatLimit: 10,
      entitlements: ['TUTOR_WORKSPACE', 'TUTOR_MATERIALS'],
    },
  ],
  providers: [
    { code: 'STRIPE', mode: 'TEST' },
    { code: 'BEPAID', mode: 'TEST' },
  ],
};

const PRO_RIGHTS = [
  'UNLIMITED_WEEKS',
  'AI_WRITING_CHECK',
  'AI_SPEAKING_CHECK',
  'SPEAKING',
  'MOCK_EXAM',
  'AI_TUTOR_CHAT',
];

export async function stubBilling(page: Page, options: Options = {}): Promise<BillingStubState> {
  const roles = options.roles ?? ['STUDENT'];
  const state: BillingStubState = { checkouts: [], cancellations: 0, paid: options.subscribed ?? false };
  let polls = 0;
  let canceled = false;

  const subscription = () =>
    state.paid
      ? {
          id: 'sub-1',
          planTierCode: 'STUDENT_PRO',
          source: 'SUBSCRIPTION',
          status: 'ACTIVE',
          provider: 'STRIPE',
          currentPeriodStart: '2026-11-21T10:00:00Z',
          currentPeriodEnd: PERIOD_END,
          cancelAtPeriodEnd: canceled,
          testMode: true,
          sagaStatus: 'COMPLETED',
        }
      : { planTierCode: 'STUDENT_FREE', source: 'NONE', status: null, cancelAtPeriodEnd: false, testMode: true };

  const entitlements = () => ({
    userId: 'u1',
    entitlementsVersion: state.paid ? 2 : 1,
    planTierCode: state.paid ? 'STUDENT_PRO' : 'STUDENT_FREE',
    entitlements: state.paid ? PRO_RIGHTS : ['AI_WRITING_CHECK', 'AI_SPEAKING_CHECK', 'SPEAKING', 'WEEKLY_REVIEW'],
    grants: [],
    freeAiChecks: { limit: 10, used: 10, remaining: 0 },
    changedAt: new Date().toISOString(),
  });

  const session = (status: string) => ({
    id: 'cs-1',
    provider: 'STRIPE',
    planTierCode: 'STUDENT_PRO',
    status,
    redirectUrl: `${PROVIDER}/pay/cs-1`,
    expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
    testMode: true,
    failureReason: status === 'FAILED' ? 'card_declined' : null,
    failureMessage: status === 'FAILED' ? 'Your card was declined.' : null,
  });

  // The provider's hosted page: straight back to the return URL, as Stripe does after a test payment.
  await page.route(`${PROVIDER}/**`, async (route) => {
    const back = state.checkouts.at(-1)?.returnUrl ?? '/';
    await route.fulfill({ status: 302, headers: { location: `${back}?session=cs-1` } });
  });

  await page.route(`${API}/**`, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const json = (body: unknown, status = 200) =>
      route.fulfill({
        status,
        contentType: status >= 400 ? 'application/problem+json' : 'application/json',
        body: JSON.stringify(body),
        headers: {
          'access-control-allow-origin': request.headers()['origin'] ?? '*',
          'access-control-allow-credentials': 'true',
          'access-control-allow-headers': '*',
          'access-control-allow-methods': '*',
        },
      });
    if (request.method() === 'OPTIONS') return json({});
    if (path === '/auth/session') {
      return json({ authenticated: true, user: { id: 'u1', roles, emailVerified: true }, csrfToken: 't' });
    }
    if (path === '/api/v1/me') {
      return json({
        id: 'u1',
        email: 'learner@toefly.test',
        displayName: 'Lea Learner',
        roles,
        onboardingStatus: 'COMPLETED',
        createdAt: '2026-11-01T10:00:00Z',
      });
    }
    if (path === '/api/v1/plan-tiers') return json(tiers);
    if (path === '/api/v1/subscriptions/current') return json(subscription());
    if (path === '/api/v1/entitlements') return json(entitlements());
    if (path === '/api/v1/payments') {
      return json({
        items: state.paid
          ? [
              {
                id: 'pay-1',
                provider: 'STRIPE',
                planTierCode: 'STUDENT_PRO',
                amountMinor: 1200,
                currency: 'EUR',
                status: 'SUCCEEDED',
                paidAt: '2026-11-21T10:00:00Z',
                receiptUrl: 'https://pay.stripe.test/receipts/pay-1',
                testMode: true,
                createdAt: '2026-11-21T10:00:00Z',
              },
            ]
          : [],
      });
    }
    if (path === '/api/v1/checkout-sessions' && request.method() === 'POST') {
      if (!request.headers()['idempotency-key']) {
        return json({ status: 428, code: 'IDEMPOTENCY_KEY_REQUIRED', title: 'Idempotency key required' }, 428);
      }
      state.checkouts.push(request.postDataJSON() as BillingStubState['checkouts'][number]);
      polls = 0;
      return json(session('OPEN'), 201);
    }
    if (path === '/api/v1/checkout-sessions/cs-1') {
      polls += 1;
      if (polls < 2) return json(session('OPEN'));
      if (options.decline) return json(session('FAILED'));
      state.paid = true;
      return json(session('COMPLETED'));
    }
    if (path === '/api/v1/subscriptions/current/cancellation' && request.method() === 'POST') {
      state.cancellations += 1;
      canceled = true;
      return json(subscription());
    }
    return json(
      { type: 'https://toefly.app/problems/not-found', title: 'Not found', status: 404, code: 'NOT_FOUND' },
      404,
    );
  });
  return state;
}
