import type { OnboardingState } from '@/api/generated';

// Steps of the onboarding route /onboarding/$step (docs/07-ux-ui.md §4.4, §5.3).
export const ONBOARDING_STEPS = [
  'goal',
  'exam-date',
  'schedule',
  'explanation-language',
  'consents',
  'diagnostic-intro',
] as const;

export type OnboardingStepSlug = (typeof ONBOARDING_STEPS)[number];

const SLUG_OF_API_STEP: Record<string, OnboardingStepSlug> = {
  GOAL: 'goal',
  EXAM_DATE: 'exam-date',
  SCHEDULE: 'schedule',
  EXPLANATION_LANGUAGE: 'explanation-language',
};

export function isOnboardingStep(value: string): value is OnboardingStepSlug {
  return (ONBOARDING_STEPS as readonly string[]).includes(value);
}

/** The first step without saved values; consents follow once every data step is filled (AC-02.04.2). */
export function resumeStep(state: Pick<OnboardingState, 'nextStep'>): OnboardingStepSlug {
  return state.nextStep ? (SLUG_OF_API_STEP[state.nextStep] ?? 'goal') : 'consents';
}

export function stepNumber(step: OnboardingStepSlug): number {
  return ONBOARDING_STEPS.indexOf(step) + 1;
}

export function nextStep(step: OnboardingStepSlug): OnboardingStepSlug | null {
  return ONBOARDING_STEPS[ONBOARDING_STEPS.indexOf(step) + 1] ?? null;
}

export function previousStep(step: OnboardingStepSlug): OnboardingStepSlug | null {
  const index = ONBOARDING_STEPS.indexOf(step);
  return index > 0 ? (ONBOARDING_STEPS[index - 1] ?? null) : null;
}

export const MINUTE_OPTIONS = [15, 30, 45, 60, 90, 120, 180] as const;

/** Local date as yyyy-mm-dd. */
export function isoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** An exam date is after today and at most five years ahead (AC-02.01.3). */
export function examDateError(value: string, today: Date): string | null {
  const date = new Date(`${value}T00:00:00`);
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const latest = new Date(start);
  latest.setFullYear(latest.getFullYear() + 5);
  if (Number.isNaN(date.getTime()) || date <= start || date > latest) {
    return 'Pick a date within the next 5 years';
  }
  return null;
}

/** Fewer than 7 days to the exam (docs/07-ux-ui.md §5.3). */
export function isExamSoon(value: string, today: Date): boolean {
  const date = new Date(`${value}T00:00:00`);
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return (date.getTime() - start.getTime()) / 86_400_000 < 7;
}

export function browserTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

export function timeZones(): string[] {
  const supported = (Intl as { supportedValuesOf?: (key: string) => string[] }).supportedValuesOf;
  return supported ? supported('timeZone') : [browserTimeZone()];
}
