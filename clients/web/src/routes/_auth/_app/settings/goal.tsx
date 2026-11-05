import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { StudyGoal } from '@/api/generated';
import { useChangeStudyGoal, versionedStudyGoalQuery } from '@/api/queries';
import { BandPicker } from '@/components/onboarding/onboarding-wizard';
import { SettingsForm } from '@/components/settings/settings-ui';
import { Calendar } from '@/components/ui/calendar';
import { bandHint } from '@/lib/band';
import { cn } from '@/lib/cn';
import { examDateBounds } from '@/lib/dates';
import { examDateError, isoDate } from '@/lib/onboarding';
import { goalChangeAppliesNextWeek } from '@/lib/settings';

export const Route = createFileRoute('/_auth/_app/settings/goal')({
  component: GoalSettings,
});

function GoalSettings() {
  const { data } = useQuery(versionedStudyGoalQuery);
  if (!data) {
    return null;
  }
  return <GoalForm goal={data.data} etag={data.etag} />;
}

/** Target and exam date after the onboarding (US-02.05); future weeks follow the new goal. */
function GoalForm({ goal, etag }: { goal: StudyGoal; etag: string }) {
  const { t } = useTranslation();
  const change = useChangeStudyGoal();
  const [overall, setOverall] = useState(goal.overallTarget);
  const [notBooked, setNotBooked] = useState(!goal.examBooked);
  const savedDate = goal.examDate ? isoDate(goal.examDate) : '';
  const [date, setDate] = useState(savedDate);
  const [notice, setNotice] = useState<string | null>(null);
  const bounds = examDateBounds(new Date());
  const error = !notBooked && date ? examDateError(date, new Date()) : null;
  const dirty = overall !== goal.overallTarget || notBooked !== !goal.examBooked || (!notBooked && date !== savedDate);

  const save = () => {
    const examDate = notBooked ? null : date;
    const nextWeek = goalChangeAppliesNextWeek(
      { examDate: goal.examBooked ? savedDate : null, minutesPerDay: goal.minutesPerDay },
      { examDate, minutesPerDay: goal.minutesPerDay },
    );
    change.mutate(
      {
        etag,
        request: {
          // Section targets set in the onboarding stay unless the overall target changes.
          ...(overall !== goal.overallTarget || !goal.sectionTargets
            ? { overallTarget: overall }
            : { sectionTargets: goal.sectionTargets }),
          examBooked: !notBooked,
          ...(examDate ? { examDate: new Date(`${examDate}T00:00:00`) } : {}),
          minutesPerDay: goal.minutesPerDay,
        },
      },
      { onSuccess: () => setNotice(nextWeek ? t('settings.goal.appliesNextWeek') : t('settings.saved')) },
    );
  };

  return (
    <SettingsForm
      title={t('settings.tabs.goal')}
      dirty={dirty && error === null && (notBooked || date !== '')}
      pending={change.isPending}
      saved={notice !== null}
      savedText={notice ?? undefined}
      error={change.error}
      onSave={save}
    >
      <BandPicker label="Overall target" value={overall} onChange={setOverall} />
      <p className="-mt-2 text-body font-semibold text-accent-on-soft" data-testid="band-hint">
        {bandHint(overall)}
      </p>
      <Calendar
        label={t('onboarding.examDate.date')}
        value={date ? new Date(`${date}T00:00:00`) : undefined}
        onChange={(picked) => setDate(picked ? isoDate(picked) : '')}
        min={bounds.min}
        max={bounds.max}
        disabled={notBooked}
        describedBy="goal-date-message"
      />
      <p id="goal-date-message" className={cn('-mt-3 text-caption', error ? 'text-danger' : 'text-warning')}>
        {error ?? t('settings.goal.warning')}
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
    </SettingsForm>
  );
}
