import { useQuery } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { type FormEvent, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  ConsentType,
  ExplanationLanguage,
  type OnboardingRequest,
  type OnboardingState,
  OnboardingStep,
} from '@/api/generated';
import { consentsQuery, onboardingQuery, useRecordConsent, useSaveOnboarding } from '@/api/queries';
import { Button } from '@/components/ui/button';
import { Calendar } from '@/components/ui/calendar';
import { GlassPanel } from '@/components/ui/card';
import { Logo } from '@/components/ui/logo';
import { Mesh } from '@/components/ui/mesh';
import { ProgressBar } from '@/components/ui/progress-bar';
import { BANDS, bandHint, formatBand, overallOf } from '@/lib/band';
import { cn } from '@/lib/cn';
import { examDateBounds } from '@/lib/dates';
import {
  browserTimeZone,
  examDateError,
  isExamSoon,
  isoDate,
  MINUTE_OPTIONS,
  nextStep,
  ONBOARDING_STEPS,
  type OnboardingStepSlug,
  previousStep,
  stepNumber,
  timeZones,
} from '@/lib/onboarding';

const SECTIONS = ['reading', 'listening', 'writing', 'speaking'] as const;
type SectionTargets = Record<(typeof SECTIONS)[number], number>;

/** Onboarding on a glass card over mesh-auth; every step is saved before moving on (docs/07-ux-ui.md §5.3). */
export function OnboardingWizard({ step }: { step: OnboardingStepSlug }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { data: state } = useQuery(onboardingQuery);
  const save = useSaveOnboarding();
  const goTo = (target: OnboardingStepSlug | null) =>
    target ? navigate({ to: '/onboarding/$step', params: { step: target } }) : navigate({ to: '/' });

  const submit = async (request: OnboardingRequest) => {
    const saved = await save.mutateAsync(request);
    if (saved.status === 'COMPLETED') {
      await navigate({ to: '/' });
      return;
    }
    await goTo(nextStep(step));
  };

  if (!state) {
    return null;
  }
  const back = previousStep(step);
  return (
    <div className="relative grid min-h-dvh place-items-center bg-canvas px-4 py-10">
      <Mesh variant="auth" />
      <GlassPanel className="relative z-[1] flex w-full max-w-[560px] flex-col gap-6 p-8">
        <div className="flex items-center justify-between">
          <Logo />
          <span className="text-caption text-ink-muted">
            {t('onboarding.step', { current: stepNumber(step), total: ONBOARDING_STEPS.length })}
          </span>
        </div>
        <ProgressBar value={(stepNumber(step) / ONBOARDING_STEPS.length) * 100} label="Onboarding progress" />
        <StepForm key={step} step={step} state={state} onSubmit={submit} onAdvance={() => goTo(nextStep(step))} />
        {save.isError && (
          <p role="alert" className="text-body text-danger">
            {save.error instanceof Error ? t('errors.generic') : t('errors.generic')}
          </p>
        )}
        {back && (
          <Button variant="ghost" className="self-start" onClick={() => void goTo(back)}>
            {t('onboarding.back')}
          </Button>
        )}
      </GlassPanel>
    </div>
  );
}

interface StepProps {
  step: OnboardingStepSlug;
  state: OnboardingState;
  onSubmit: (request: OnboardingRequest) => Promise<void>;
  onAdvance: () => Promise<void> | void;
}

function StepForm(props: StepProps) {
  switch (props.step) {
    case 'goal':
      return <GoalStep {...props} />;
    case 'exam-date':
      return <ExamDateStep {...props} />;
    case 'schedule':
      return <ScheduleStep {...props} />;
    case 'explanation-language':
      return <LanguageStep {...props} />;
    case 'consents':
      return <ConsentsStep {...props} />;
    case 'diagnostic-intro':
      return <DiagnosticIntroStep {...props} />;
  }
}

function StepShell({
  title,
  subtitle,
  onSubmit,
  disabled,
  children,
  submitLabel,
}: {
  title: string;
  subtitle?: string;
  onSubmit: () => Promise<void> | void;
  disabled?: boolean;
  children: React.ReactNode;
  submitLabel?: string;
}) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);
  const handle = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    try {
      await onSubmit();
    } finally {
      setBusy(false);
    }
  };
  return (
    <form className="flex flex-col gap-5" onSubmit={(event) => void handle(event)} noValidate>
      <div className="flex flex-col gap-1">
        <h1 className="text-title-2 font-semibold text-ink">{title}</h1>
        {subtitle && <p className="text-body text-ink-muted">{subtitle}</p>}
      </div>
      {children}
      <Button type="submit" block disabled={disabled || busy}>
        {submitLabel ?? t('onboarding.continue')}
      </Button>
    </form>
  );
}

export function BandPicker({
  value,
  onChange,
  label,
}: {
  value: number;
  onChange: (band: number) => void;
  label: string;
}) {
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="mb-1 text-caption font-semibold text-ink">{label}</legend>
      <div className="grid grid-cols-6 gap-1.5 sm:grid-cols-11" role="radiogroup" aria-label={label}>
        {BANDS.map((band) => (
          <button
            key={band}
            type="button"
            role="radio"
            aria-checked={value === band}
            onClick={() => onChange(band)}
            className={cn(
              'h-10 rounded-lg border text-body font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-focus-ring',
              value === band
                ? 'border-primary bg-primary text-white'
                : 'border-border bg-surface text-ink hover:border-border-strong',
            )}
          >
            {formatBand(band)}
          </button>
        ))}
      </div>
    </fieldset>
  );
}

function GoalStep({ state, onSubmit }: StepProps) {
  const { t } = useTranslation();
  const [overall, setOverall] = useState<number>(state.overallTarget ?? 4.5);
  const [perSection, setPerSection] = useState(Boolean(state.sectionTargets));
  const [sections, setSections] = useState<SectionTargets>({
    reading: state.sectionTargets?.reading ?? overall,
    listening: state.sectionTargets?.listening ?? overall,
    writing: state.sectionTargets?.writing ?? overall,
    speaking: state.sectionTargets?.speaking ?? overall,
  });
  const effective = perSection ? overallOf(SECTIONS.map((section) => sections[section])) : overall;
  return (
    <StepShell
      title={t('onboarding.goal.title')}
      subtitle={t('onboarding.goal.subtitle')}
      onSubmit={() => onSubmit(perSection ? { sectionTargets: sections } : { overallTarget: overall })}
    >
      {!perSection && <BandPicker label="Overall target" value={overall} onChange={setOverall} />}
      <p className="text-body-lg font-semibold text-accent-on-soft" data-testid="band-hint">
        {perSection ? t('onboarding.goal.overallReadOnly', { band: bandHint(effective) }) : bandHint(effective)}
      </p>
      <p className="-mt-3 text-caption text-ink-muted">{t('onboarding.goal.comparableNote')}</p>
      <label className="flex items-center gap-2 text-body text-ink">
        <input
          type="checkbox"
          className="size-4 accent-[var(--primary)]"
          checked={perSection}
          onChange={(event) => setPerSection(event.target.checked)}
        />
        {t('onboarding.goal.perSection')}
      </label>
      {perSection && (
        <div className="grid gap-3 sm:grid-cols-2">
          {SECTIONS.map((section) => (
            <label key={section} className="flex flex-col gap-1 text-caption font-semibold capitalize text-ink">
              {section}
              <select
                className="h-10 rounded-lg border border-input bg-surface px-3 text-body text-ink"
                value={sections[section]}
                onChange={(event) => setSections({ ...sections, [section]: Number(event.target.value) })}
              >
                {BANDS.map((band) => (
                  <option key={band} value={band}>
                    {formatBand(band)}
                  </option>
                ))}
              </select>
            </label>
          ))}
        </div>
      )}
    </StepShell>
  );
}

function ExamDateStep({ state, onSubmit }: StepProps) {
  const { t } = useTranslation();
  const today = new Date();
  const bounds = examDateBounds(today);
  const [notBooked, setNotBooked] = useState(state.examBooked === false);
  const [date, setDate] = useState(state.examDate ? isoDate(state.examDate) : '');
  const error = !notBooked && date ? examDateError(date, today) : null;
  return (
    <StepShell
      title={t('onboarding.examDate.title')}
      disabled={!notBooked && (!date || error !== null)}
      onSubmit={() =>
        onSubmit(notBooked ? { examBooked: false } : { examBooked: true, examDate: new Date(`${date}T00:00:00`) })
      }
    >
      <Calendar
        label={t('onboarding.examDate.date')}
        value={date ? new Date(`${date}T00:00:00`) : undefined}
        onChange={(picked) => setDate(picked ? isoDate(picked) : '')}
        min={bounds.min}
        max={bounds.max}
        disabled={notBooked}
        describedBy="exam-date-message"
      />
      <p id="exam-date-message" className={cn('-mt-3 text-caption', error ? 'text-danger' : 'text-warning')}>
        {error ?? (!notBooked && date && isExamSoon(date, today) ? t('onboarding.examDate.soon') : '')}
      </p>
      <label className="flex items-center gap-2 text-body text-ink">
        <input
          type="checkbox"
          className="size-4 accent-[var(--primary)]"
          checked={notBooked}
          onChange={(event) => setNotBooked(event.target.checked)}
        />
        {t('onboarding.examDate.notBooked')}
      </label>
      {notBooked && <p className="-mt-3 text-caption text-ink-muted">{t('onboarding.examDate.notBookedHint')}</p>}
    </StepShell>
  );
}

function ScheduleStep({ state, onSubmit }: StepProps) {
  const { t } = useTranslation();
  const [minutes, setMinutes] = useState(state.minutesPerDay || 60);
  const [time, setTime] = useState(state.preferredStudyTime || '19:00');
  return (
    <StepShell
      title={t('onboarding.schedule.title')}
      onSubmit={() => onSubmit({ minutesPerDay: minutes, preferredStudyTime: time })}
    >
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1 text-caption font-semibold text-ink">{t('onboarding.schedule.minutes')}</legend>
        <div className="grid grid-cols-4 gap-1.5 sm:grid-cols-7" role="radiogroup">
          {MINUTE_OPTIONS.map((option) => (
            <button
              key={option}
              type="button"
              role="radio"
              aria-checked={minutes === option}
              onClick={() => setMinutes(option)}
              className={cn(
                'h-10 rounded-lg border text-body font-semibold focus-visible:outline-2 focus-visible:outline-focus-ring',
                minutes === option ? 'border-primary bg-primary text-white' : 'border-border bg-surface text-ink',
              )}
            >
              {option}
            </button>
          ))}
        </div>
      </fieldset>
      {minutes === 15 && (
        <p role="status" className="rounded-lg bg-warning-soft px-3 py-2 text-caption text-warning">
          {t('onboarding.schedule.fifteenWarning')}
        </p>
      )}
      <label className="flex flex-col gap-1 text-caption font-semibold text-ink">
        {t('onboarding.schedule.reminder')}
        <input
          type="time"
          className="h-11 w-40 rounded-lg border border-input bg-surface px-3 text-body text-ink"
          value={time}
          onChange={(event) => setTime(event.target.value)}
        />
      </label>
    </StepShell>
  );
}

function LanguageStep({ state, onSubmit }: StepProps) {
  const { t } = useTranslation();
  const [language, setLanguage] = useState<ExplanationLanguage>(state.explanationLanguage ?? ExplanationLanguage.En);
  const [zone, setZone] = useState(
    state.filledSteps.includes(OnboardingStep.ExplanationLanguage) ? state.timezone : browserTimeZone(),
  );
  const options = [
    { value: ExplanationLanguage.En, label: t('onboarding.language.english') },
    { value: ExplanationLanguage.Ru, label: t('onboarding.language.russian') },
  ];
  return (
    <StepShell
      title={t('onboarding.language.title')}
      subtitle={t('onboarding.language.subtitle')}
      onSubmit={() => onSubmit({ explanationLanguage: language, timezone: zone })}
    >
      <fieldset className="flex flex-col gap-2">
        <legend className="sr-only">{t('onboarding.language.title')}</legend>
        {options.map((option) => (
          <label
            key={option.value}
            className={cn(
              'flex h-12 items-center gap-3 rounded-lg border px-4 text-body text-ink',
              language === option.value ? 'border-primary bg-accent-soft' : 'border-border bg-surface',
            )}
          >
            <input
              type="radio"
              name="language"
              className="size-4 accent-[var(--primary)]"
              checked={language === option.value}
              onChange={() => setLanguage(option.value)}
            />
            {option.label}
          </label>
        ))}
      </fieldset>
      <label className="flex flex-col gap-1 text-caption font-semibold text-ink">
        {t('onboarding.language.timezone')}
        <select
          className="h-11 rounded-lg border border-input bg-surface px-3 text-body text-ink"
          value={zone}
          onChange={(event) => setZone(event.target.value)}
        >
          {timeZones().map((id) => (
            <option key={id} value={id}>
              {id}
            </option>
          ))}
        </select>
      </label>
    </StepShell>
  );
}

function ConsentsStep({ onAdvance }: StepProps) {
  const { t } = useTranslation();
  const { data } = useQuery(consentsQuery);
  const record = useRecordConsent();
  const answered = (type: ConsentType) => data?.items.find((consent) => consent.consentType === type);
  const [ai, setAi] = useState(answered(ConsentType.AiProcessing)?.granted ?? true);
  const [emails, setEmails] = useState(answered(ConsentType.MarketingEmails)?.granted ?? false);
  return (
    <StepShell
      title={t('onboarding.consents.title')}
      onSubmit={async () => {
        await record.mutateAsync({ consentType: ConsentType.AiProcessing, granted: ai });
        await record.mutateAsync({ consentType: ConsentType.MarketingEmails, granted: emails });
        await onAdvance();
      }}
    >
      <label className="flex items-start gap-3 rounded-lg border border-border bg-surface p-4">
        <input
          type="checkbox"
          role="switch"
          className="mt-1 size-4 accent-[var(--primary)]"
          checked={ai}
          onChange={(event) => setAi(event.target.checked)}
        />
        <span className="flex flex-col gap-1">
          <span className="text-body font-semibold text-ink">{t('onboarding.consents.ai')}</span>
          <span className="text-caption text-ink-muted">{t('onboarding.consents.aiHint')}</span>
        </span>
      </label>
      <label className="flex items-center gap-3 rounded-lg border border-border bg-surface p-4">
        <input
          type="checkbox"
          role="switch"
          className="size-4 accent-[var(--primary)]"
          checked={emails}
          onChange={(event) => setEmails(event.target.checked)}
        />
        <span className="text-body font-semibold text-ink">{t('onboarding.consents.emails')}</span>
      </label>
    </StepShell>
  );
}

function DiagnosticIntroStep({ state, onSubmit }: StepProps) {
  const { t } = useTranslation();
  const rows: [string, string][] = [
    [t('onboarding.diagnostic.summaryGoal'), state.overallTarget ? bandHint(state.overallTarget) : '—'],
    [
      t('onboarding.diagnostic.summaryExam'),
      state.examBooked && state.examDate
        ? state.examDate.toLocaleDateString('en-GB')
        : t('onboarding.diagnostic.noDate'),
    ],
    [t('onboarding.diagnostic.summaryTime'), `${state.minutesPerDay} min`],
  ];
  return (
    <StepShell
      title={t('onboarding.diagnostic.title')}
      subtitle={t('onboarding.diagnostic.subtitle')}
      submitLabel={t('onboarding.diagnostic.start')}
      onSubmit={() => onSubmit({ complete: true })}
    >
      <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 rounded-lg bg-surface p-4 text-body">
        {rows.map(([label, value]) => (
          <div key={label} className="contents">
            <dt className="text-ink-muted">{label}</dt>
            <dd className="font-semibold text-ink">{value}</dd>
          </div>
        ))}
      </dl>
    </StepShell>
  );
}
