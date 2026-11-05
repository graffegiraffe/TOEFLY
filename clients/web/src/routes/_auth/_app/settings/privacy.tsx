import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ConsentType, ResponseError } from '@/api/generated';
import { consentsQuery, useRecordConsent, useRequestDeletion, useRequestExport } from '@/api/queries';
import { loginUrl, logout } from '@/api/session';
import { SaveStatus, SettingsCard, inputClass } from '@/components/settings/settings-ui';
import { Button, buttonClass } from '@/components/ui/button';

export const Route = createFileRoute('/_auth/_app/settings/privacy')({
  component: Privacy,
});

/** Settings → Privacy: consents, export and deletion (US-31.01, US-31.03, US-31.04). */
function Privacy() {
  const { t, i18n } = useTranslation();
  const { data } = useQuery(consentsQuery);
  const record = useRecordConsent();
  const exportData = useRequestExport();
  const deletion = useRequestDeletion();
  const [confirmation, setConfirmation] = useState('');
  const consentOf = (type: ConsentType) => data?.items.find((consent) => consent.consentType === type);
  const toggles: [ConsentType, string][] = [
    [ConsentType.AiProcessing, t('settings.privacy.aiProcessing')],
    [ConsentType.MarketingEmails, t('settings.privacy.marketing')],
  ];
  // The API answers 403 when the last sign-in is older than five minutes (AC-31.04.1).
  const needsReauth = deletion.error instanceof ResponseError && deletion.error.response.status === 403;
  const dateFormat = new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium' });
  return (
    <div className="flex flex-col gap-4">
      <SettingsCard title={t('settings.privacy.consents')}>
        <div className="flex w-full flex-col gap-4">
          {toggles.map(([type, label]) => {
            const consent = consentOf(type);
            return (
              <label key={type} className="flex items-center justify-between gap-4 text-body text-ink">
                <span className="flex flex-col">
                  {label}
                  {consent?.granted && (
                    <span className="text-caption text-ink-muted">
                      {t('settings.privacy.grantedOn', { date: dateFormat.format(consent.recordedAt) })}
                    </span>
                  )}
                </span>
                <input
                  type="checkbox"
                  role="switch"
                  className="size-4 accent-[var(--primary)]"
                  checked={consent?.granted ?? false}
                  onChange={(event) => record.mutate({ consentType: type, granted: event.target.checked })}
                />
              </label>
            );
          })}
          <SaveStatus saved={record.isSuccess} error={record.error} />
        </div>
      </SettingsCard>
      <SettingsCard title={t('settings.privacy.export')}>
        <Button variant="secondary" onClick={() => exportData.mutate()} disabled={exportData.isPending}>
          {t('settings.privacy.export')}
        </Button>
        {exportData.isSuccess && <p className="text-body text-ink-muted">{t('settings.privacy.exportRequested')}</p>}
        {exportData.isError && <p className="text-body text-danger">{t('errors.generic')}</p>}
      </SettingsCard>
      <SettingsCard title={t('settings.privacy.delete')} hint={t('settings.privacy.deleteHint')}>
        <div className="flex flex-wrap items-center gap-3">
          <input
            aria-label="Type DELETE"
            className={`${inputClass} w-40`}
            value={confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
          />
          <Button
            variant="destructive"
            disabled={confirmation !== 'DELETE' || deletion.isPending}
            onClick={() =>
              deletion.mutate(confirmation, {
                onSuccess: async () => window.location.assign(await logout()),
              })
            }
          >
            {t('settings.privacy.deleteConfirm')}
          </Button>
        </div>
        {needsReauth && (
          <div role="alert" className="flex flex-col items-start gap-2">
            <p className="text-body text-ink">{t('settings.privacy.deleteReauth')}</p>
            <a className={buttonClass('secondary')} href={loginUrl('/settings/privacy', { reauth: true })}>
              {t('settings.privacy.signInAgain')}
            </a>
          </div>
        )}
        {deletion.isError && !needsReauth && <p className="text-body text-danger">{t('errors.generic')}</p>}
      </SettingsCard>
    </div>
  );
}
