import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { SaveStatus, SettingsCard } from '@/components/settings/settings-ui';
import { cn } from '@/lib/cn';
import {
  applyReduceTransparency,
  applyTheme,
  storedReduceTransparency,
  storedTheme,
  type ThemeChoice,
} from '@/lib/settings';

export const Route = createFileRoute('/_auth/_app/settings/appearance')({
  component: AppearanceSettings,
});

const THEMES: ThemeChoice[] = ['system', 'light', 'dark'];

/** Theme and reduced transparency, kept on this device and applied at once with "Saved" (docs/07 §5.19). */
function AppearanceSettings() {
  const { t } = useTranslation();
  const [theme, setTheme] = useState<ThemeChoice>(storedTheme);
  const [reduced, setReduced] = useState(storedReduceTransparency);
  const [saved, setSaved] = useState(false);
  const chooseTheme = (choice: ThemeChoice) => {
    setTheme(choice);
    applyTheme(choice);
    setSaved(true);
  };
  return (
    <div className="flex flex-col gap-4">
      <SettingsCard title={t('settings.appearance.theme')}>
        <div role="radiogroup" aria-label={t('settings.appearance.theme')} className="flex gap-1.5">
          {THEMES.map((choice) => (
            <button
              key={choice}
              type="button"
              role="radio"
              aria-checked={theme === choice}
              onClick={() => chooseTheme(choice)}
              className={cn(
                'h-10 rounded-lg border px-4 text-body font-semibold focus-visible:outline-2 focus-visible:outline-focus-ring',
                theme === choice ? 'border-primary bg-primary text-white' : 'border-border bg-surface text-ink',
              )}
            >
              {t(`settings.appearance.${choice}`)}
            </button>
          ))}
        </div>
      </SettingsCard>
      <SettingsCard
        title={t('settings.appearance.reduceTransparency')}
        hint={t('settings.appearance.reduceTransparencyHint')}
      >
        <label className="flex items-center gap-3 text-body text-ink">
          <input
            type="checkbox"
            role="switch"
            className="size-4 accent-[var(--primary)]"
            checked={reduced}
            onChange={(event) => {
              setReduced(event.target.checked);
              applyReduceTransparency(event.target.checked);
              setSaved(true);
            }}
          />
          {t('settings.appearance.reduceTransparency')}
        </label>
      </SettingsCard>
      <SaveStatus saved={saved} error={null} />
    </div>
  );
}
