import { useQueryClient } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Role } from '@/api/generated';
import { ACCOUNT_CONSOLE_URL } from '@/api/config';
import { useBecomeTutor, useSignOutEverywhere } from '@/api/queries';
import { loginUrl } from '@/api/session';
import { SettingsCard } from '@/components/settings/settings-ui';
import { Button, buttonClass } from '@/components/ui/button';

export const Route = createFileRoute('/_auth/_app/settings/account')({
  component: AccountSettings,
});

/** Linked sign-ins, password and MFA in Keycloak; sign out of all devices (AC-01.05.4); tutor workspace. */
function AccountSettings() {
  const { t } = useTranslation();
  const { profile } = Route.useRouteContext();
  const client = useQueryClient();
  const signOutEverywhere = useSignOutEverywhere();
  const becomeTutor = useBecomeTutor();
  const [confirming, setConfirming] = useState(false);
  const [tutorTerms, setTutorTerms] = useState(false);
  const signInAgain = () => {
    client.clear();
    window.location.assign(loginUrl('/'));
  };
  const isTutor = profile.roles.includes(Role.Tutor);
  return (
    <div className="flex flex-col gap-4">
      <SettingsCard title={t('settings.account.security')} hint={t('settings.account.securityHint')}>
        <a className={buttonClass('secondary')} href={ACCOUNT_CONSOLE_URL} target="_blank" rel="noreferrer">
          {t('settings.account.openAccount')}
        </a>
      </SettingsCard>
      <SettingsCard title={t('settings.account.devices')} hint={t('settings.account.devicesHint')}>
        {confirming ? (
          <div role="alertdialog" aria-label={t('settings.account.signOutEverywhere')} className="flex flex-col gap-3">
            <p className="text-body text-ink">{t('settings.account.signOutConfirm')}</p>
            <div className="flex gap-2">
              <Button
                variant="destructive"
                disabled={signOutEverywhere.isPending}
                onClick={() => signOutEverywhere.mutate(undefined, { onSuccess: signInAgain })}
              >
                {t('settings.account.signOutEverywhere')}
              </Button>
              <Button variant="ghost" onClick={() => setConfirming(false)}>
                {t('onboarding.back')}
              </Button>
            </div>
          </div>
        ) : (
          <Button variant="secondary" onClick={() => setConfirming(true)}>
            {t('settings.account.signOutEverywhere')}
          </Button>
        )}
        {signOutEverywhere.isError && <p className="text-body text-danger">{t('errors.generic')}</p>}
      </SettingsCard>
      {profile.roles.includes(Role.Student) && (
        <SettingsCard title={t('settings.account.tutor')} hint={t('settings.account.tutorHint')}>
          {isTutor ? (
            <p className="text-body text-ink">{t('settings.account.alreadyTutor')}</p>
          ) : (
            <>
              <label className="flex items-center gap-2 text-body text-ink">
                <input
                  type="checkbox"
                  className="size-4 accent-[var(--primary)]"
                  checked={tutorTerms}
                  onChange={(event) => setTutorTerms(event.target.checked)}
                />
                {t('settings.account.tutorTerms')}
              </label>
              <Button
                variant="secondary"
                disabled={!tutorTerms || becomeTutor.isPending}
                onClick={() => becomeTutor.mutate(undefined, { onSuccess: signInAgain })}
              >
                {t('settings.account.becomeTutor')}
              </Button>
              {becomeTutor.isError && <p className="text-body text-danger">{t('errors.generic')}</p>}
            </>
          )}
        </SettingsCard>
      )}
    </div>
  );
}
