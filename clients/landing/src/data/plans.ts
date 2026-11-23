/**
 * Plan tiers shown on the pricing section.
 *
 * Static copy of the billing catalogue: docs/00-README.md §5.3, docs/01-product-vision.md §6.1 and the seed in
 * services/billing-service (V202611210000__create_billing.sql, `GET /api/v1/plan-tiers`). Keep the codes and prices
 * in sync with billing-service until the landing reads them at build time.
 */
export type PlanTierCode = 'STUDENT_FREE' | 'STUDENT_PRO' | 'TUTOR' | 'TUTOR_PLUS';

export interface Plan {
  code: PlanTierCode;
  name: string;
  audience: 'student' | 'tutor';
  /** Price in minor units (cents), VAT included for EU consumers. */
  priceMinor: number;
  currency: 'EUR';
  interval: 'month' | null;
  summary: string;
  features: readonly string[];
  cta: { label: string; path: string };
  highlighted?: boolean;
}

export const plans: readonly Plan[] = [
  {
    code: 'STUDENT_FREE',
    name: 'Free',
    audience: 'student',
    priceMinor: 0,
    currency: 'EUR',
    interval: null,
    summary: 'See how the method works before you pay.',
    features: [
      'Diagnostic test and a personal plan',
      'Your first week with all sections',
      '10 AI checks of Writing and Speaking',
      'Weekly review of your first week',
    ],
    cta: { label: 'Start free', path: '/sign-up' },
  },
  {
    code: 'STUDENT_PRO',
    name: 'Pro',
    audience: 'student',
    priceMinor: 1200,
    currency: 'EUR',
    interval: 'month',
    summary: 'Everything, without limits. The first 7 days are free.',
    features: [
      'A new week every week, up to your exam date',
      'Unlimited AI checks of Writing and Speaking',
      'Adaptive mock exams on weeks 4, 8 and the last one',
      'Toefly Tutor chat with memory of your mistakes',
    ],
    cta: { label: 'Try Pro for 7 days', path: '/sign-up' },
    highlighted: true,
  },
  {
    code: 'TUTOR',
    name: 'Tutor',
    audience: 'tutor',
    priceMinor: 2900,
    currency: 'EUR',
    interval: 'month',
    summary: 'For tutors with a small group of students.',
    features: [
      'Up to 10 students, each gets Pro',
      'AI drafts of every week for you to approve',
      'First-pass AI checks and comments in the text',
      '2 GB for your own books and materials',
    ],
    cta: { label: 'Start as a tutor', path: '/sign-up?role=tutor' },
  },
  {
    code: 'TUTOR_PLUS',
    name: 'Tutor Plus',
    audience: 'tutor',
    priceMinor: 5900,
    currency: 'EUR',
    interval: 'month',
    summary: 'The same workspace for a bigger practice.',
    features: ['Up to 30 students, each gets Pro', 'Everything in Tutor', '10 GB for your own books and materials'],
    cta: { label: 'Start as a tutor', path: '/sign-up?role=tutor' },
  },
];

export function formatPrice(plan: Plan): string {
  return new Intl.NumberFormat('en-IE', {
    style: 'currency',
    currency: plan.currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(plan.priceMinor / 100);
}
