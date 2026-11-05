import { OnboardingStep } from '@/api/generated';
import { examDateError, isExamSoon, isoDate, nextStep, previousStep, resumeStep, stepNumber } from './onboarding';

describe('onboarding steps', () => {
  it('AC-02.04.2 resumes at the first unfilled step', () => {
    expect(resumeStep({ nextStep: undefined })).toBe('consents');
    expect(resumeStep({ nextStep: OnboardingStep.Schedule })).toBe('schedule');
    expect(resumeStep({ nextStep: OnboardingStep.Goal })).toBe('goal');
  });

  it('walks through six steps', () => {
    expect(stepNumber('goal')).toBe(1);
    expect(stepNumber('diagnostic-intro')).toBe(6);
    expect(nextStep('consents')).toBe('diagnostic-intro');
    expect(nextStep('diagnostic-intro')).toBeNull();
    expect(previousStep('goal')).toBeNull();
    expect(previousStep('schedule')).toBe('exam-date');
  });
});

describe('exam date', () => {
  const today = new Date(2026, 10, 5);

  it('AC-02.01.3 rejects past dates and dates more than a year ahead', () => {
    expect(examDateError('2026-11-05', today)).toBe('Pick a date within the next 12 months');
    expect(examDateError('2027-11-06', today)).toBe('Pick a date within the next 12 months');
    expect(examDateError('not-a-date', today)).not.toBeNull();
    expect(examDateError('2027-11-05', today)).toBeNull();
    expect(examDateError('2026-11-06', today)).toBeNull();
  });

  it('warns when the exam is less than a week away', () => {
    expect(isExamSoon('2026-11-10', today)).toBe(true);
    expect(isExamSoon('2026-11-20', today)).toBe(false);
  });

  it('formats local dates', () => {
    expect(isoDate(new Date(2026, 0, 9))).toBe('2026-01-09');
  });
});
