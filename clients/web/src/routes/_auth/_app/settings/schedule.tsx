import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Preferences } from '@/api/generated';
import { preferencesQuery, useUpdatePreferences } from '@/api/queries';
import { inputClass, labelClass, SettingsForm } from '@/components/settings/settings-ui';
import { cn } from '@/lib/cn';
import { MINUTE_OPTIONS } from '@/lib/onboarding';

export const Route = createFileRoute('/_auth/_app/settings/schedule')({
  component: ScheduleSettings,
});

function ScheduleSettings() {
  const { data } = useQuery(preferencesQuery);
  if (!data) {
    return null;
  }
  return <ScheduleForm preferences={data.data} etag={data.etag} />;
}

/** Minutes a day and the reminder time (US-02.02, US-02.05); new minutes apply from next week (AC-02.05.1). */
function ScheduleForm({ preferences, etag }: { preferences: Preferences; etag: string }) {
  const { t } = useTranslation();
  const update = useUpdatePreferences();
  const [minutes, setMinutes] = useState(preferences.minutesPerDay);
  const [time, setTime] = useState(preferences.preferredStudyTime);
  const [notice, setNotice] = useState<string | null>(null);
  const dirty = minutes !== preferences.minutesPerDay || time !== preferences.preferredStudyTime;
  const save = () =>
    update.mutate(
      { etag, preferences: { ...preferences, minutesPerDay: minutes, preferredStudyTime: time } },
      {
        onSuccess: () =>
          setNotice(minutes !== preferences.minutesPerDay ? t('settings.goal.appliesNextWeek') : t('settings.saved')),
      },
    );
  return (
    <SettingsForm
      title={t('settings.tabs.schedule')}
      dirty={dirty}
      pending={update.isPending}
      saved={notice !== null}
      savedText={notice ?? undefined}
      error={update.error}
      onSave={save}
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
        <p className="rounded-lg bg-warning-soft px-3 py-2 text-caption text-warning">
          {t('onboarding.schedule.fifteenWarning')}
        </p>
      )}
      <label className={labelClass}>
        {t('onboarding.schedule.reminder')}
        <input type="time" className={cn(inputClass, 'w-40')} value={time} onChange={(e) => setTime(e.target.value)} />
      </label>
    </SettingsForm>
  );
}
