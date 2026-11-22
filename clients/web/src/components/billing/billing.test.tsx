import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import '@/i18n';
import {
  CheckoutSessionStatusEnum,
  PaymentProviderCode,
  PaymentStatusEnum,
  PlanTierAudienceEnum,
  PlanTierCode,
  PlanTierIntervalEnum,
  ProviderMode,
  SubscriptionSourceEnum,
  SubscriptionStatus,
  type PlanTier,
  type Subscription,
} from '@/api/generated/billing';

const createCheckoutSession = vi.fn();
const getCheckoutSession = vi.fn();
const getMyEntitlements = vi.fn();
const cancelSubscription = vi.fn();
const changePlan = vi.fn();
const listPlanTiers = vi.fn();
vi.mock('@/api/client', () => ({
  api: {
    checkout: {
      createCheckoutSession: (...a: unknown[]) => createCheckoutSession(...a),
      getCheckoutSession: (...a: unknown[]) => getCheckoutSession(...a),
    },
    entitlements: { getMyEntitlements: (...a: unknown[]) => getMyEntitlements(...a) },
    subscriptions: {
      cancelSubscription: (...a: unknown[]) => cancelSubscription(...a),
      changePlan: (...a: unknown[]) => changePlan(...a),
    },
    planTiers: { listPlanTiers: (...a: unknown[]) => listPlanTiers(...a) },
  },
}));
vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children: ReactNode; to: string }) => <a href={to}>{children}</a>,
  useNavigate: () => vi.fn(),
}));

import { ResponseError } from '@/api/generated/billing';
import { openPaywall } from '@/lib/paywall';
import { CheckoutDialog } from './checkout-dialog';
import { CheckoutResult } from './checkout-result';
import { CurrentPlan } from './current-plan';
import { PaymentHistory } from './payment-history';
import { PlanCards } from './plan-cards';
import { PaywallHost } from './paywall';
import { SeatsPanel } from './seats-panel';

beforeAll(() => {
  // jsdom has no modal dialogs: open them as non-modal ones.
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    this.removeAttribute('open');
    this.dispatchEvent(new Event('close'));
  };
});

function wrap(children: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{children}</QueryClientProvider>);
}

const tier = (code: PlanTierCode, priceMinor: number, audience = PlanTierAudienceEnum.Student): PlanTier => ({
  code,
  audience,
  priceMinor,
  currency: 'EUR',
  interval: PlanTierIntervalEnum.Month,
  trialDays: code === PlanTierCode.StudentPro ? 7 : 0,
  freeAiCheckLimit: code === PlanTierCode.StudentFree ? 10 : null,
  seatLimit: audience === PlanTierAudienceEnum.Tutor ? 10 : null,
  entitlements: code === PlanTierCode.StudentPro ? ['MOCK_EXAM' as never] : [],
});

const free: Subscription = {
  planTierCode: PlanTierCode.StudentFree,
  source: SubscriptionSourceEnum.None,
  status: null,
  cancelAtPeriodEnd: false,
  testMode: true,
};

const active: Subscription = {
  id: 's1',
  planTierCode: PlanTierCode.StudentPro,
  source: SubscriptionSourceEnum.Subscription,
  status: SubscriptionStatus.Active,
  provider: PaymentProviderCode.Stripe,
  currentPeriodEnd: new Date('2026-12-01T10:00:00Z'),
  cancelAtPeriodEnd: false,
  testMode: true,
};

describe('Billing screens (docs/07 §5.20)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it('AC-27.01.1 plan cards come from the API with Upgrade for the learner only', async () => {
    const choose = vi.fn();
    wrap(
      <PlanCards
        plans={[
          tier(PlanTierCode.StudentFree, 0),
          tier(PlanTierCode.StudentPro, 1200),
          tier(PlanTierCode.Tutor, 2900, PlanTierAudienceEnum.Tutor),
        ]}
        subscription={free}
        roles={['STUDENT']}
        onChoose={choose}
      />,
    );
    expect(screen.queryByTestId('plan-TUTOR')).toBeNull();
    const card = screen.getByTestId('plan-STUDENT_PRO');
    expect(card).toHaveTextContent('€12');
    expect(card).toHaveTextContent('Full mock exams');
    expect(within(screen.getByTestId('plan-STUDENT_FREE')).getByText('Current plan')).toBeVisible();
    await userEvent.click(within(card).getByRole('button', { name: 'Upgrade: Pro' }));
    expect(choose).toHaveBeenCalledWith(expect.objectContaining({ code: PlanTierCode.StudentPro }));
  });

  it('shows the free AI checks of Student Free', () => {
    wrap(
      <CurrentPlan
        subscription={free}
        entitlements={{
          userId: 'u1',
          entitlementsVersion: 1,
          planTierCode: PlanTierCode.StudentFree,
          entitlements: [],
          grants: [],
          freeAiChecks: { limit: 10, used: 3, remaining: 7 },
          changedAt: new Date(),
        }}
      />,
    );
    expect(screen.getByTestId('plan-headline')).toHaveTextContent('Student Free');
    expect(screen.getByTestId('ai-usage')).toHaveTextContent('AI checks: 3 of 10');
  });

  it('AC-27.05.2 cancel asks first and keeps Pro until the period end', async () => {
    cancelSubscription.mockResolvedValue({ ...active, cancelAtPeriodEnd: true });
    wrap(<CurrentPlan subscription={active} />);
    expect(screen.getByTestId('plan-headline')).toHaveTextContent('Pro · renews Dec 1');
    await userEvent.click(screen.getByRole('button', { name: 'Cancel subscription' }));
    const dialog = screen.getByRole('dialog', { name: 'Cancel your subscription?' });
    expect(dialog).toHaveTextContent('You’ll keep Pro until Dec 1');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel subscription' }));
    await waitFor(() => expect(cancelSubscription).toHaveBeenCalledTimes(1));
    expect(cancelSubscription.mock.calls[0]![0]).toHaveProperty('idempotencyKey');
  });

  it('AC-27.04.5 an App Store subscription says Manage in the App Store, no cancel button', () => {
    wrap(<CurrentPlan subscription={{ ...active, provider: PaymentProviderCode.AppStore }} />);
    expect(screen.getByText('Manage in the App Store')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Cancel subscription' })).toBeNull();
  });

  it('AC-27.01.3, AC-27.03.1 checkout shows the providers in order, the test badge, and leaves for the provider', async () => {
    createCheckoutSession.mockResolvedValue({ id: 'c1', redirectUrl: 'https://checkout.stripe.test/c1' });
    const navigate = vi.fn();
    wrap(
      <CheckoutDialog
        plan={tier(PlanTierCode.StudentPro, 1200)}
        providers={[
          { code: PaymentProviderCode.Stripe, mode: ProviderMode.Test },
          { code: PaymentProviderCode.Bepaid, mode: ProviderMode.Live },
        ]}
        onClose={vi.fn()}
        navigate={navigate}
      />,
    );
    const radios = screen.getAllByRole('radio');
    expect(radios.map((radio) => radio.closest('label')?.textContent)).toEqual(['Card (Stripe)', 'Card (bePaid)']);
    expect(screen.getByTestId('test-mode-badge')).toHaveTextContent('Test mode — no real charges');
    await userEvent.click(screen.getByRole('radio', { name: 'Card (bePaid)' }));
    expect(screen.queryByTestId('test-mode-badge')).toBeNull();
    await userEvent.click(screen.getByRole('radio', { name: 'Card (Stripe)' }));
    await userEvent.click(screen.getByRole('button', { name: 'Continue to payment' }));
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('https://checkout.stripe.test/c1'));
    expect(createCheckoutSession.mock.calls[0]![0]).toMatchObject({
      createCheckoutSessionRequest: { planTierCode: 'STUDENT_PRO', provider: 'STRIPE' },
    });
    expect(sessionStorage.getItem('toefly.checkoutSession')).toBe('c1');
  });

  it('AC-27.03.4 the result waits for the webhook, then shows Pro and its rights', async () => {
    getCheckoutSession
      .mockResolvedValueOnce({ id: 'c1', status: CheckoutSessionStatusEnum.Open, planTierCode: 'STUDENT_PRO' })
      .mockResolvedValue({ id: 'c1', status: CheckoutSessionStatusEnum.Completed, planTierCode: 'STUDENT_PRO' });
    getMyEntitlements.mockResolvedValue({ planTierCode: 'STUDENT_PRO', entitlements: ['MOCK_EXAM', 'AI_TUTOR_CHAT'] });
    wrap(<CheckoutResult sessionId="c1" />);
    expect(await screen.findByRole('status')).toHaveTextContent('Confirming your payment…');
    expect(await screen.findByText('You’re on Pro', {}, { timeout: 5000 })).toBeVisible();
    expect(await screen.findByTestId('unlocked')).toHaveTextContent('Full mock exams');
  });

  it('AC-27.03.5 a declined payment shows the reason of the provider', async () => {
    getCheckoutSession.mockResolvedValue({
      id: 'c1',
      status: CheckoutSessionStatusEnum.Failed,
      planTierCode: 'STUDENT_PRO',
      failureMessage: 'Your card was declined.',
    });
    wrap(<CheckoutResult sessionId="c1" />);
    expect(await screen.findByText('Payment didn’t go through')).toBeVisible();
    expect(screen.getByTestId('failure-message')).toHaveTextContent('Your card was declined.');
    expect(screen.getByRole('link', { name: 'Try again' })).toBeVisible();
    expect(screen.getByRole('link', { name: 'Choose another method' })).toBeVisible();
  });

  it('lists payments with receipts and the test mark', () => {
    wrap(
      <PaymentHistory
        payments={[
          {
            id: 'p1',
            provider: PaymentProviderCode.Stripe,
            planTierCode: PlanTierCode.StudentPro,
            amountMinor: 1200,
            currency: 'EUR',
            status: PaymentStatusEnum.Succeeded,
            paidAt: new Date('2026-11-01T10:00:00Z'),
            receiptUrl: 'https://pay.stripe.test/r/p1',
            testMode: true,
            createdAt: new Date('2026-11-01T10:00:00Z'),
          },
        ]}
      />,
    );
    const history = screen.getByTestId('payment-history');
    expect(history).toHaveTextContent('€12');
    expect(history).toHaveTextContent('Paid');
    expect(history).toHaveTextContent('Test payment');
    expect(within(history).getByRole('link', { name: 'Receipt' })).toHaveAttribute(
      'href',
      'https://pay.stripe.test/r/p1',
    );
  });

  it('AC-27.05.4 Tutor Plus with more than 10 students cannot switch to Tutor', async () => {
    changePlan.mockRejectedValue(new ResponseError(new Response(null, { status: 409 })));
    wrap(
      <SeatsPanel
        subscription={{
          ...active,
          planTierCode: PlanTierCode.TutorPlus,
          seats: {
            limit: 30,
            used: 12,
            seats: [{ index: 1, studentId: 'aaaaaaaa-1111', assignedAt: new Date() }, { index: 2 }],
          },
        }}
      />,
    );
    expect(screen.getByTestId('seats-panel')).toHaveTextContent('12 of 30 seats used');
    await userEvent.click(screen.getByRole('button', { name: 'Switch to Tutor' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Remove students to 10 or fewer to switch to Tutor');
  });

  it('§3.21 a refused request opens the Paywall named after the right', async () => {
    listPlanTiers.mockResolvedValue({ items: [tier(PlanTierCode.StudentPro, 1200)], providers: [] });
    wrap(<PaywallHost />);
    act(() => openPaywall('MOCK_EXAM'));
    const dialog = await screen.findByRole('dialog', { name: 'Mock exams are part of Pro' });
    expect(await within(dialog).findByText('€12 / month')).toBeVisible();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Not now' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  });
});
