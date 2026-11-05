import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ConsentType } from '@/api/generated';
import { consentsQuery, useRecordConsent, useRequestDeletion, useRequestExport } from '@/api/queries';
import { logout } from '@/api/session';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';

export const Route = createFileRoute('/_auth/_app/settings/privacy')({
  component: Privacy,
});

/** Settings → Privacy: consents, export and deletion (US-31.01, US-31.03, US-31.04). */
function Privacy() {
  const { t } = useTranslation();
  const { data } = useQuery(consentsQuery);
  const record = useRecordConsent();
  const exportData = useRequestExport();
  const deletion = useRequestDeletion();
  const [confirmation, setConfirmation] = useState('');
  const granted = (type: ConsentType) => data?.items.find((consent) => consent.consentType === type)?.granted ?? false;
  const toggles: [ConsentType, string][] = [
    [ConsentType.AiProcessing, t('settings.aiProcessing')],
    [ConsentType.MarketingEmails, t('settings.marketing')],
  ];
  return (
    <div className="flex max-w-[820px] flex-col gap-6">
      <h1 className="text-title-1 font-semibold tracking-tight text-ink">{t('settings.title')}</h1>
      <Card className="flex flex-col gap-4">
        {toggles.map(([type, label]) => (
          <label key={type} className="flex items-center justify-between gap-4 text-body text-ink">
            {label}
            <input
              type="checkbox"
              role="switch"
              className="size-4 accent-[var(--primary)]"
              checked={granted(type)}
              onChange={(event) => record.mutate({ consentType: type, granted: event.target.checked })}
            />
          </label>
        ))}
      </Card>
      <Card className="flex flex-col items-start gap-3">
        <Button variant="secondary" onClick={() => exportData.mutate()} disabled={exportData.isPending}>
          {t('settings.export')}
        </Button>
        {exportData.isSuccess && <p className="text-body text-ink-muted">{t('settings.exportRequested')}</p>}
        {exportData.isError && <p className="text-body text-danger">{t('errors.generic')}</p>}
      </Card>
      <Card className="flex flex-col gap-3">
        <h2 className="text-title-3 font-semibold text-ink">{t('settings.delete')}</h2>
        <p className="text-body text-ink-muted">{t('settings.deleteHint')}</p>
        <div className="flex flex-wrap items-center gap-3">
          <input
            aria-label="Type DELETE"
            className="h-11 w-40 rounded-lg border border-input bg-surface px-3 text-body text-ink"
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
            {t('settings.deleteConfirm')}
          </Button>
        </div>
        {deletion.isError && <p className="text-body text-danger">{t('errors.generic')}</p>}
      </Card>
    </div>
  );
}
