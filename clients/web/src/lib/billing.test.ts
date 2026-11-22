import { describe, expect, it } from 'vitest';
import {
  CheckoutSessionStatusEnum,
  PaymentProviderCode,
  PlanTierCode,
  ProviderMode,
  SubscriptionSourceEnum,
  SubscriptionStatus,
  type CheckoutSession,
  type EntitlementSet,
  type Subscription,
} from '@/api/generated/billing';
import {
  canBuy,
  canCancel,
  canResume,
  checkoutSessionId,
  checkoutView,
  forgetCheckout,
  formatPrice,
  freeUsage,
  keepPolling,
  paywallTitleKey,
  planLine,
  POLL_TIMEOUT_MS,
  rememberCheckout,
  requiredEntitlement,
  shortDate,
  tutorSwitchTarget,
} from './billing';

const free: Subscription = {
  planTierCode: PlanTierCode.StudentFree,
  source: SubscriptionSourceEnum.None,
  status: null,
  cancelAtPeriodEnd: false,
  testMode: true,
};

const pro = (patch: Partial<Subscription> = {}): Subscription => ({
  id: 's1',
  planTierCode: PlanTierCode.StudentPro,
  source: SubscriptionSourceEnum.Subscription,
  status: SubscriptionStatus.Active,
  provider: PaymentProviderCode.Stripe,
  currentPeriodEnd: new Date('2026-12-01T10:00:00Z'),
  cancelAtPeriodEnd: false,
  testMode: true,
  ...patch,
});

const session = (patch: Partial<CheckoutSession> = {}): CheckoutSession => ({
  id: 'c1',
  provider: PaymentProviderCode.Stripe,
  planTierCode: PlanTierCode.StudentPro,
  status: CheckoutSessionStatusEnum.Open,
  redirectUrl: 'https://checkout.stripe.test/c1',
  expiresAt: new Date('2026-11-21T11:00:00Z'),
  testMode: true,
  ...patch,
});

describe('docs/07 §5.20 billing formatting', () => {
  it('AC-27.01.1 formats prices of the API without hard-coded amounts', () => {
    expect(formatPrice(1200, 'EUR')).toBe('€12');
    expect(formatPrice(0, 'EUR')).toBe('€0');
    expect(formatPrice(1250, 'EUR')).toBe('€12.50');
    expect(shortDate(new Date('2026-12-01T10:00:00Z'))).toBe('Dec 1');
  });

  it('says Free, renews, trial, ends, past due and seat', () => {
    expect(planLine(free)).toEqual({ kind: 'free' });
    expect(planLine(pro()).kind).toBe('renews');
    expect(planLine(pro({ cancelAtPeriodEnd: true })).kind).toBe('ends');
    expect(
      planLine(pro({ status: SubscriptionStatus.Trialing, provider: null, trialEndsAt: new Date('2026-11-28') })),
    ).toEqual({ kind: 'trial', until: new Date('2026-11-28') });
    expect(planLine(pro({ status: SubscriptionStatus.PastDue, graceUntil: new Date('2026-12-08') })).kind).toBe(
      'pastDue',
    );
    expect(planLine(pro({ source: SubscriptionSourceEnum.Seat, tutorId: 't1' }))).toEqual({ kind: 'seat' });
  });

  it('AC-27.05.2 cancels an active web subscription once, resumes a cancelled one', () => {
    expect(canCancel(pro())).toBe(true);
    expect(canCancel(pro({ cancelAtPeriodEnd: true }))).toBe(false);
    expect(canResume(pro({ cancelAtPeriodEnd: true }))).toBe(true);
    expect(canCancel(free)).toBe(false);
    // AC-27.04.5: App Store subscriptions are managed in the App Store.
    expect(canCancel(pro({ provider: PaymentProviderCode.AppStore }))).toBe(false);
  });

  it('offers paid plans of the role only, also during the trial, never on a seat', () => {
    const proTier = { code: PlanTierCode.StudentPro, audience: 'STUDENT', priceMinor: 1200 };
    const tutorTier = { code: PlanTierCode.Tutor, audience: 'TUTOR', priceMinor: 2900 };
    const freeTier = { code: PlanTierCode.StudentFree, audience: 'STUDENT', priceMinor: 0 };
    expect(canBuy(proTier, free, ['STUDENT'])).toBe(true);
    expect(canBuy(freeTier, free, ['STUDENT'])).toBe(false);
    expect(canBuy(tutorTier, free, ['STUDENT'])).toBe(false);
    expect(canBuy(proTier, pro({ status: SubscriptionStatus.Trialing }), ['STUDENT'])).toBe(true);
    expect(canBuy(proTier, pro(), ['STUDENT'])).toBe(false);
    expect(canBuy(proTier, pro({ source: SubscriptionSourceEnum.Seat }), ['STUDENT'])).toBe(false);
  });

  it('AC-27.05.3/5 switches Tutor and Tutor Plus unless a change is pending', () => {
    expect(tutorSwitchTarget(pro({ planTierCode: PlanTierCode.Tutor }))).toBe(PlanTierCode.TutorPlus);
    expect(tutorSwitchTarget(pro({ planTierCode: PlanTierCode.TutorPlus }))).toBe(PlanTierCode.Tutor);
    expect(
      tutorSwitchTarget(pro({ planTierCode: PlanTierCode.TutorPlus, pendingPlanTierCode: PlanTierCode.Tutor })),
    ).toBeNull();
    expect(tutorSwitchTarget(pro())).toBeNull();
  });

  it('§1.3 shows free AI checks on Student Free only', () => {
    const rights = (code: PlanTierCode): EntitlementSet => ({
      userId: 'u1',
      entitlementsVersion: 1,
      planTierCode: code,
      entitlements: [],
      grants: [],
      freeAiChecks: { limit: 10, used: 10, remaining: 0 },
      changedAt: new Date(),
    });
    expect(freeUsage(rights(PlanTierCode.StudentFree))).toEqual({ used: 10, limit: 10, remaining: 0, exhausted: true });
    expect(freeUsage(rights(PlanTierCode.StudentPro))).toBeNull();
  });
});

describe('AC-27.03.4 checkout result polling', () => {
  it('waits for OPEN and PROCESSING, then says it takes longer after 60 s', () => {
    expect(checkoutView(session(), 1000)).toEqual({ kind: 'pending' });
    expect(checkoutView(session({ status: CheckoutSessionStatusEnum.Processing }), 5000).kind).toBe('pending');
    expect(checkoutView(session(), POLL_TIMEOUT_MS)).toEqual({ kind: 'slow' });
    expect(checkoutView(undefined, 1000)).toEqual({ kind: 'pending' });
    expect(keepPolling({ kind: 'pending' })).toBe(true);
    expect(keepPolling({ kind: 'slow' })).toBe(false);
  });

  it('ends with the plan or with the reason of the provider (AC-27.03.5)', () => {
    expect(checkoutView(session({ status: CheckoutSessionStatusEnum.Completed }), 1000)).toEqual({
      kind: 'success',
      plan: PlanTierCode.StudentPro,
    });
    expect(
      checkoutView(
        session({
          status: CheckoutSessionStatusEnum.Failed,
          failureReason: 'card_declined',
          failureMessage: 'Your card was declined.',
        }),
        1000,
      ),
    ).toEqual({ kind: 'failed', reason: 'card_declined', message: 'Your card was declined.' });
    expect(checkoutView(undefined, 0, true)).toEqual({ kind: 'missing' });
  });

  it('polls the session of ?session= first, then the remembered one', () => {
    forgetCheckout();
    expect(checkoutSessionId('')).toBeNull();
    rememberCheckout('remembered');
    expect(checkoutSessionId('')).toBe('remembered');
    expect(checkoutSessionId('?session=from-url')).toBe('from-url');
    forgetCheckout();
  });
});

describe('docs/07 §3.21 Paywall', () => {
  it('reads the missing right of 403 entitlement-required only', () => {
    expect(requiredEntitlement(403, { code: 'ENTITLEMENT_REQUIRED', requiredEntitlement: 'MOCK_EXAM' })).toBe(
      'MOCK_EXAM',
    );
    expect(requiredEntitlement(403, { type: 'https://toefly.app/problems/entitlement-required' })).toBe('');
    expect(requiredEntitlement(403, { code: 'FORBIDDEN' })).toBeNull();
    expect(requiredEntitlement(404, { code: 'ENTITLEMENT_REQUIRED' })).toBeNull();
  });

  it('titles the dialog by the missing right', () => {
    expect(paywallTitleKey('MOCK_EXAM')).toBe('billing.paywall.reason.MOCK_EXAM');
    expect(paywallTitleKey('SOMETHING_NEW')).toBe('billing.paywall.reason.DEFAULT');
    expect(paywallTitleKey(null)).toBe('billing.paywall.reason.DEFAULT');
  });

  it('knows the provider modes of the contract', () => {
    expect(Object.values(ProviderMode)).toEqual(['TEST', 'LIVE']);
  });
});
