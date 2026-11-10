import { useMutation } from '@tanstack/react-query';
import { Flag } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api } from '@/api/client';
import { ReportReason } from '@/api/generated/content';
import { Button } from '@/components/ui/button';

const REASONS = [
  ReportReason.WrongKey,
  ReportReason.Unclear,
  ReportReason.AudioProblem,
  ReportReason.Inappropriate,
  ReportReason.Typo,
  ReportReason.Other,
];

/** “Report” of the task player (docs/07-ux-ui.md §5.9): an ItemReport in content-service (US-11.08). */
export function ReportButton({ itemSetId, itemOrdinal }: { itemSetId: string; itemOrdinal?: number }) {
  const { t } = useTranslation();
  const ref = useRef<HTMLDialogElement>(null);
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<ReportReason>(ReportReason.WrongKey);
  const [comment, setComment] = useState('');
  const report = useMutation({
    mutationFn: () =>
      api.contentReports.createItemReport({
        createItemReportRequest: { itemSetId, itemOrdinal, reason, comment: comment.trim() || undefined },
      }),
    onSuccess: () => setOpen(false),
  });

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog || typeof dialog.showModal !== 'function') return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <>
      <Button variant="ghost" onClick={() => setOpen(true)}>
        <Flag className="size-4" aria-hidden="true" />
        {report.isSuccess ? t('practice.report.sent') : t('practice.report.open')}
      </Button>
      <dialog
        ref={ref}
        onClose={() => setOpen(false)}
        aria-labelledby="report-title"
        className="m-auto w-[min(480px,94vw)] rounded-xl border border-border bg-surface p-6 text-ink shadow-modal backdrop:bg-black/40"
      >
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            report.mutate();
          }}
        >
          <h2 id="report-title" className="text-title-3 font-semibold">
            {t('practice.report.title')}
          </h2>
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-body font-semibold">{t('practice.report.reason')}</legend>
            {REASONS.map((value) => (
              <label key={value} className="flex items-center gap-2 text-body">
                <input
                  type="radio"
                  name="reason"
                  value={value}
                  checked={reason === value}
                  onChange={() => setReason(value)}
                  className="accent-[var(--color-primary)]"
                />
                {t(`practice.report.reasons.${value}`)}
              </label>
            ))}
          </fieldset>
          <label className="flex flex-col gap-1 text-body">
            {t('practice.report.comment')}
            <textarea
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              maxLength={1000}
              rows={3}
              className="rounded-lg border border-border-strong bg-surface p-2 text-body text-ink"
            />
          </label>
          {report.isError && (
            <p role="alert" className="text-caption text-danger">
              {t('practice.player.error')}
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setOpen(false)}>
              {t('practice.report.cancel')}
            </Button>
            <Button type="submit" disabled={report.isPending}>
              {t('practice.report.send')}
            </Button>
          </div>
        </form>
      </dialog>
    </>
  );
}
