import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import type { AiCall } from '@/api/generated/aigateway';
import { Button } from '@/components/ui/button';
import { humanize } from '@/lib/admin-content';
import { OUTCOME_TONE, promptLabel } from '@/lib/admin-ai';
import { ToneBadge } from './tone-badge';

interface Props {
  call: AiCall | null;
  onClose: () => void;
}

/** One call of the log with its masked request and response only; raw texts never reach the browser. */
export function AiCallDialog({ call, onClose }: Props) {
  const { t } = useTranslation();
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog || typeof dialog.showModal !== 'function') {
      return;
    }
    if (call && !dialog.open) {
      dialog.showModal();
    } else if (!call && dialog.open) {
      dialog.close();
    }
  }, [call]);

  const details: [string, string | null | undefined][] = call
    ? [
        ['caller', call.callerService],
        ['feature', call.feature],
        ['priority', call.priority ? humanize(call.priority) : null],
        ['guardVerdict', call.guardVerdict],
        ['errorCode', call.errorCode],
        ['traceId', call.traceId],
      ]
    : [];

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      aria-labelledby="ai-call-dialog-title"
      className="m-auto w-[min(880px,94vw)] rounded-xl border border-border bg-surface p-0 text-ink shadow-e1 backdrop:bg-black/40"
    >
      {call && (
        <div className="flex max-h-[85vh] flex-col gap-4 overflow-auto p-6">
          <header className="flex flex-wrap items-start justify-between gap-3">
            <div className="flex min-w-0 flex-col gap-1">
              <h2 id="ai-call-dialog-title" className="break-all text-title-3 font-semibold">
                {t('adminAi.calls.detail.title', { id: call.id })}
              </h2>
              <p className="text-caption text-ink-muted">
                {humanize(call.useCase)} · {promptLabel(call)} · {humanize(call.provider)} {call.model}
              </p>
            </div>
            <ToneBadge tone={OUTCOME_TONE[call.outcome]}>{t(`adminAi.calls.outcome.${call.outcome}`)}</ToneBadge>
          </header>
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-caption">
            {details
              .filter(([, value]) => value)
              .map(([key, value]) => (
                <div key={key} className="contents">
                  <dt className="text-ink-muted">{t(`adminAi.calls.detail.${key}`)}</dt>
                  <dd className="break-all">{value}</dd>
                </div>
              ))}
          </dl>
          <p className="text-caption text-ink-muted">{t('adminAi.calls.detail.maskedNote')}</p>
          <MaskedText label={t('adminAi.calls.detail.request')} text={call.requestMasked} />
          <MaskedText label={t('adminAi.calls.detail.response')} text={call.responseMasked} />
          <div className="flex justify-end">
            <Button variant="secondary" onClick={() => ref.current?.close()}>
              {t('adminAi.calls.detail.close')}
            </Button>
          </div>
        </div>
      )}
    </dialog>
  );
}

function MaskedText({ label, text }: { label: string; text: string | null | undefined }) {
  const { t } = useTranslation();
  return (
    <section aria-label={label} className="flex flex-col gap-1">
      <h3 className="text-caption font-semibold text-ink">{label}</h3>
      {text ? (
        <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-words rounded-lg bg-surface-sunken p-3 text-micro text-ink">
          {text}
        </pre>
      ) : (
        <p className="text-caption text-ink-muted">{t('adminAi.calls.detail.noText')}</p>
      )}
    </section>
  );
}
