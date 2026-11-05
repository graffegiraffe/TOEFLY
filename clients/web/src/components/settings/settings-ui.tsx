import type { FormEvent, ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { ResponseError } from '@/api/generated';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';

export const inputClass = 'h-11 rounded-lg border border-input bg-surface px-3 text-body text-ink';
export const labelClass = 'flex flex-col gap-1 text-caption font-semibold text-ink';

/** A settings form; "Save changes" sits at the bottom and is active only with changes (docs/07 §5.19). */
export function SettingsForm({
  title,
  children,
  dirty,
  pending,
  saved,
  savedText,
  error,
  onSave,
}: {
  title: string;
  children: ReactNode;
  dirty: boolean;
  pending: boolean;
  saved: boolean;
  /** Replaces "Saved", e.g. "Changes apply from next week" (AC-02.05.1). */
  savedText?: string;
  error: unknown;
  onSave: () => void;
}) {
  const { t } = useTranslation();
  const submit = (event: FormEvent) => {
    event.preventDefault();
    onSave();
  };
  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <Card className="flex flex-col gap-5">
        <h2 className="text-title-3 font-semibold text-ink">{title}</h2>
        {children}
      </Card>
      <div className="flex items-center gap-3">
        <Button type="submit" disabled={!dirty || pending}>
          {t('settings.save')}
        </Button>
        <SaveStatus saved={saved && !dirty} text={savedText} error={error} />
      </div>
    </form>
  );
}

export function SaveStatus({ saved, text, error }: { saved: boolean; text?: string; error: unknown }) {
  const { t } = useTranslation();
  if (error) {
    const conflict = error instanceof ResponseError && error.response.status === 412;
    return (
      <p role="alert" className="text-body text-danger">
        {conflict ? t('settings.conflict') : t('errors.generic')}
      </p>
    );
  }
  return (
    <p role="status" className="text-body text-ink-muted">
      {saved ? (text ?? t('settings.saved')) : ''}
    </p>
  );
}

export function SettingsCard({ title, hint, children }: { title: string; hint?: string; children: ReactNode }) {
  return (
    <Card className="flex flex-col items-start gap-3">
      <h2 className="text-title-3 font-semibold text-ink">{title}</h2>
      {hint && <p className="text-body text-ink-muted">{hint}</p>}
      {children}
    </Card>
  );
}
