import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ExplanationLanguage, type Preferences } from '@/api/generated';
import { preferencesQuery, useUpdatePreferences } from '@/api/queries';
import { SettingsForm } from '@/components/settings/settings-ui';
import { cn } from '@/lib/cn';

export const Route = createFileRoute('/_auth/_app/settings/learning')({
  component: LearningSettings,
});

function LearningSettings() {
  const { data } = useQuery(preferencesQuery);
  if (!data) {
    return null;
  }
  return <LearningForm preferences={data.data} etag={data.etag} />;
}

/** Explanation language EN/RU; a change publishes preferences-updated (AC-32.02.1). */
function LearningForm({ preferences, etag }: { preferences: Preferences; etag: string }) {
  const { t } = useTranslation();
  const update = useUpdatePreferences();
  const [language, setLanguage] = useState(preferences.explanationLanguage);
  const [saved, setSaved] = useState(false);
  const options = [
    { value: ExplanationLanguage.En, label: t('onboarding.language.english') },
    { value: ExplanationLanguage.Ru, label: t('onboarding.language.russian') },
  ];
  return (
    <SettingsForm
      title={t('settings.tabs.learning')}
      dirty={language !== preferences.explanationLanguage}
      pending={update.isPending}
      saved={saved}
      error={update.error}
      onSave={() =>
        update.mutate(
          { etag, preferences: { ...preferences, explanationLanguage: language } },
          { onSuccess: () => setSaved(true) },
        )
      }
    >
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1 text-caption font-semibold text-ink">{t('settings.learning.language')}</legend>
        <p className="-mt-1 mb-1 text-caption text-ink-muted">{t('settings.learning.languageHint')}</p>
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
    </SettingsForm>
  );
}
