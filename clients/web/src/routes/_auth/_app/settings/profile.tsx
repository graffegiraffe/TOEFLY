import { useQuery, useQueryClient } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Preferences, UserProfile } from '@/api/generated';
import { preferencesQuery, useRenameProfile, useUpdatePreferences, versionedProfileQuery } from '@/api/queries';
import { inputClass, labelClass, SettingsForm } from '@/components/settings/settings-ui';
import { timeZones } from '@/lib/onboarding';

export const Route = createFileRoute('/_auth/_app/settings/profile')({
  component: ProfileSettings,
});

function ProfileSettings() {
  const profile = useQuery(versionedProfileQuery);
  const preferences = useQuery(preferencesQuery);
  if (!profile.data || !preferences.data) {
    return null;
  }
  return <ProfileForm profile={profile.data.data} preferences={preferences.data.data} />;
}

/** Name, email (read only) and time zone; a new time zone publishes preferences-updated (AC-02.03.3). */
function ProfileForm({ profile, preferences }: { profile: UserProfile; preferences: Preferences }) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const rename = useRenameProfile();
  const update = useUpdatePreferences();
  const [name, setName] = useState(profile.displayName ?? '');
  const [zone, setZone] = useState(preferences.timezone);
  const nameChanged = name.trim() !== (profile.displayName ?? '');
  const zoneChanged = zone !== preferences.timezone;
  const [saved, setSaved] = useState(false);

  const save = async () => {
    setSaved(false);
    // Name and preferences share the profile version, so they are saved one after another with fresh ETags.
    if (nameChanged) {
      const { etag } = await client.fetchQuery({ ...versionedProfileQuery, staleTime: 0 });
      await rename.mutateAsync({ displayName: name.trim(), etag });
    }
    if (zoneChanged) {
      const { etag } = await client.fetchQuery({ ...preferencesQuery, staleTime: 0 });
      await update.mutateAsync({ preferences: { ...preferences, timezone: zone }, etag });
    }
    setSaved(true);
  };

  const zones = timeZones();
  return (
    <SettingsForm
      title={t('settings.tabs.profile')}
      dirty={nameChanged || zoneChanged}
      pending={rename.isPending || update.isPending}
      saved={saved}
      error={rename.error ?? update.error}
      onSave={() => void save().catch(() => undefined)}
    >
      <label className={labelClass}>
        {t('settings.profile.name')}
        <input className={inputClass} value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
      </label>
      <label className={labelClass}>
        {t('settings.profile.email')}
        <input
          className={`${inputClass} text-ink-muted`}
          value={profile.email}
          readOnly
          aria-describedby="email-hint"
        />
        <span id="email-hint" className="font-normal text-ink-muted">
          {t('settings.profile.emailHint')}
        </span>
      </label>
      <label className={labelClass}>
        {t('settings.profile.timezone')}
        <select className={inputClass} value={zone} onChange={(e) => setZone(e.target.value)}>
          {(zones.includes(zone) ? zones : [zone, ...zones]).map((id) => (
            <option key={id} value={id}>
              {id}
            </option>
          ))}
        </select>
      </label>
    </SettingsForm>
  );
}
