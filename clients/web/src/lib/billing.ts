import {
  CheckoutSessionStatusEnum,
  Entitlement,
  PaymentProviderCode,
  PlanTierCode,
  SubscriptionSourceEnum,
  SubscriptionStatus,
  type CheckoutSession,
  type EntitlementSet,
  type Subscription,
} from '@/api/generated/billing';

// Billing screens of docs/07-ux-ui.md §5.20 and the Paywall of §3.21: prices and rights always come from the API
// (AC-27.01.1); these helpers only format them.

/** "€12" for 1200 EUR, "€0" for free plans; decimals only when the amount has cents. */
export function formatPrice(minor: number, currency: string, locale = 'en'): string {
  const major = minor / 100;
  const cents = minor % 100 !== 0;
  try {
    return new Intl.NumberFormat(locale, {
      style: 'currency',
      currency,
      minimumFractionDigits: cents ? 2 : 0,
      maximumFractionDigits: cents ? 2 : 0,
    }).format(major);
  } catch {
    return `${major.toFixed(cents ? 2 : 0)} ${currency}`;
  }
}

/** "Dec 1": the short date of renewals and period ends. */
export function shortDate(date: Date, locale = 'en'): string {
  return new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', timeZone: 'UTC' }).format(date);
}

/** i18n key of a plan name: "Student Free", "Pro", "Tutor", "Tutor Plus". */
export function planNameKey(code: PlanTierCode): string {
  return `billing.plans.${code}`;
}

export type PlanLine =
  | { kind: 'free' }
  | { kind: 'seat' }
  | { kind: 'trial'; until: Date | null }
  | { kind: 'renews'; until: Date | null }
  | { kind: 'ends'; until: Date | null }
  | { kind: 'pastDue'; until: Date | null }
  | { kind: 'expired' };

/** What the headline of the current plan says (§5.20): "Student Free", "Pro · renews Dec 1", "Pro trial · ends …". */
export function planLine(subscription: Subscription): PlanLine {
  if (subscription.source === SubscriptionSourceEnum.Seat) {
    return { kind: 'seat' };
  }
  if (subscription.planTierCode === PlanTierCode.StudentFree || !subscription.status) {
    return { kind: 'free' };
  }
  switch (subscription.status) {
    case SubscriptionStatus.Trialing:
      return { kind: 'trial', until: subscription.trialEndsAt ?? subscription.currentPeriodEnd ?? null };
    case SubscriptionStatus.PastDue:
      return { kind: 'pastDue', until: subscription.graceUntil ?? null };
    case SubscriptionStatus.Expired:
      return { kind: 'expired' };
    case SubscriptionStatus.Canceled:
      return { kind: 'ends', until: subscription.currentPeriodEnd ?? null };
    default:
      return subscription.cancelAtPeriodEnd || subscription.provider === PaymentProviderCode.Manual
        ? { kind: 'ends', until: subscription.currentPeriodEnd ?? null }
        : { kind: 'renews', until: subscription.currentPeriodEnd ?? null };
  }
}

/** Whether the learner manages the subscription here: not on a tutor seat, not through the App Store (AC-27.04.5). */
export function managedHere(subscription: Subscription): boolean {
  return (
    subscription.source !== SubscriptionSourceEnum.Seat &&
    subscription.provider !== PaymentProviderCode.AppStore &&
    subscription.provider !== PaymentProviderCode.Manual
  );
}

/** "Cancel subscription" is offered for a paid web subscription that is not already cancelled (AC-27.05.2). */
export function canCancel(subscription: Subscription): boolean {
  return (
    managedHere(subscription) &&
    subscription.status === SubscriptionStatus.Active &&
    !subscription.cancelAtPeriodEnd &&
    subscription.planTierCode !== PlanTierCode.StudentFree
  );
}

export function canResume(subscription: Subscription): boolean {
  return (
    managedHere(subscription) &&
    subscription.cancelAtPeriodEnd &&
    (subscription.status === SubscriptionStatus.Active || subscription.status === SubscriptionStatus.Canceled)
  );
}

/** A plan can be bought when it is paid, for the role, and not the current paid plan; a trial can still be paid. */
export function canBuy(
  plan: { code: PlanTierCode; audience: string; priceMinor: number },
  subscription: Subscription,
  roles: readonly string[],
): boolean {
  if (plan.priceMinor === 0 || !managedHere(subscription)) return false;
  if (!roles.includes(plan.audience)) return false;
  const paid =
    subscription.status === SubscriptionStatus.Active ||
    subscription.status === SubscriptionStatus.PastDue ||
    subscription.status === SubscriptionStatus.Canceled;
  return !paid;
}

/** Tutor plan switch TUTOR ↔ TUTOR_PLUS on an active tutor subscription (AC-27.05.3–5). */
export function tutorSwitchTarget(subscription: Subscription): PlanTierCode | null {
  if (!managedHere(subscription) || subscription.status !== SubscriptionStatus.Active) return null;
  if (subscription.pendingPlanTierCode) return null;
  if (subscription.planTierCode === PlanTierCode.Tutor) return PlanTierCode.TutorPlus;
  if (subscription.planTierCode === PlanTierCode.TutorPlus) return PlanTierCode.Tutor;
  return null;
}

export interface Usage {
  used: number;
  limit: number;
  remaining: number;
  exhausted: boolean;
}

/** Free AI checks are shown only while the learner is on STUDENT_FREE (§1.3 rule 1). */
export function freeUsage(entitlements: EntitlementSet | undefined): Usage | null {
  if (!entitlements || entitlements.planTierCode !== PlanTierCode.StudentFree) return null;
  const { used, limit, remaining } = entitlements.freeAiChecks;
  return { used, limit, remaining, exhausted: remaining <= 0 };
}

// ---- checkout result (§5.20, AC-27.03.4) ----

export const POLL_INTERVAL_MS = 2000;
export const POLL_TIMEOUT_MS = 60_000;

export type CheckoutView =
  | { kind: 'missing' }
  | { kind: 'pending' }
  | { kind: 'slow' }
  | { kind: 'success'; plan: PlanTierCode }
  | { kind: 'failed'; message: string | null; reason: string | null };

/**
 * The state of the result screen from the polled session: rights exist only after the verified webhook and the saga,
 * so OPEN and PROCESSING both wait; past 60 s the screen says it takes longer and stops polling.
 */
export function checkoutView(session: CheckoutSession | undefined, elapsedMs: number, missing = false): CheckoutView {
  if (missing) return { kind: 'missing' };
  if (session?.status === CheckoutSessionStatusEnum.Completed) {
    return { kind: 'success', plan: session.planTierCode };
  }
  if (session?.status === CheckoutSessionStatusEnum.Failed || session?.status === CheckoutSessionStatusEnum.Expired) {
    return { kind: 'failed', message: session.failureMessage ?? null, reason: session.failureReason ?? null };
  }
  return elapsedMs >= POLL_TIMEOUT_MS ? { kind: 'slow' } : { kind: 'pending' };
}

export function keepPolling(view: CheckoutView): boolean {
  return view.kind === 'pending';
}

const SESSION_KEY = 'toefly.checkoutSession';

export function rememberCheckout(sessionId: string): void {
  try {
    sessionStorage.setItem(SESSION_KEY, sessionId);
  } catch {
    // private mode: the provider's ?session= still works
  }
}

/** The session to poll: `?session=` from the provider's return URL wins over the remembered one. */
export function checkoutSessionId(search: string): string | null {
  const fromUrl = new URLSearchParams(search).get('session');
  if (fromUrl) return fromUrl;
  try {
    return sessionStorage.getItem(SESSION_KEY);
  } catch {
    return null;
  }
}

export function forgetCheckout(): void {
  try {
    sessionStorage.removeItem(SESSION_KEY);
  } catch {
    // nothing to forget
  }
}

// ---- Paywall (§3.21) ----

/** Title key by the missing right: "Mock exams are part of Pro", "Get AI writing checks", "Unlock unlimited weeks". */
export function paywallTitleKey(entitlement: string | null | undefined): string {
  const known = Object.values(Entitlement) as string[];
  return entitlement && known.includes(entitlement)
    ? `billing.paywall.reason.${entitlement}`
    : 'billing.paywall.reason.DEFAULT';
}

/** A `403 entitlement-required` problem (docs/05 §2.9) names the right that is missing. */
export function requiredEntitlement(status: number, problem: unknown): string | null {
  if (status !== 403 || typeof problem !== 'object' || problem === null) return null;
  const body = problem as { code?: unknown; requiredEntitlement?: unknown; type?: unknown };
  const isEntitlement =
    body.code === 'ENTITLEMENT_REQUIRED' ||
    (typeof body.type === 'string' && body.type.endsWith('/entitlement-required'));
  if (!isEntitlement) return null;
  return typeof body.requiredEntitlement === 'string' ? body.requiredEntitlement : '';
}
