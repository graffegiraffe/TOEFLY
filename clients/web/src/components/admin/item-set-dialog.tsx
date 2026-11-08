import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { adminItemSetQuery } from '@/api/content';
import { Button } from '@/components/ui/button';
import { humanize } from '@/lib/admin-content';
import { StatusBadge } from './status-badge';

interface Props {
  itemSetId: string | null;
  onClose: () => void;
}

/** Full item set with answer keys for moderation, in a native modal dialog (focus is trapped by the browser). */
export function ItemSetDialog({ itemSetId, onClose }: Props) {
  const { t } = useTranslation();
  const ref = useRef<HTMLDialogElement>(null);
  const { data } = useQuery({ ...adminItemSetQuery(itemSetId ?? ''), enabled: itemSetId !== null });

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog || typeof dialog.showModal !== 'function') {
      return;
    }
    if (itemSetId && !dialog.open) {
      dialog.showModal();
    } else if (!itemSetId && dialog.open) {
      dialog.close();
    }
  }, [itemSetId]);

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      aria-labelledby="item-set-dialog-title"
      className="m-auto w-[min(880px,94vw)] rounded-xl border border-border bg-surface p-0 text-ink shadow-e1 backdrop:bg-black/40"
    >
      {data && (
        <div className="flex max-h-[85vh] flex-col gap-4 overflow-auto p-6">
          <header className="flex flex-wrap items-start justify-between gap-3">
            <div className="flex min-w-0 flex-col gap-1">
              <h2 id="item-set-dialog-title" className="text-title-3 font-semibold">
                {data.topic}
              </h2>
              <p className="text-caption text-ink-muted">
                {humanize(data.taskType)} · {data.cefr ?? '—'} · {t('admin.detail.version')} {data.version}
              </p>
            </div>
            <StatusBadge status={data.status} />
          </header>
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-caption">
            <dt className="text-ink-muted">{t('admin.detail.source')}</dt>
            <dd>{data.source.attribution}</dd>
            <dt className="text-ink-muted">{t('admin.detail.items')}</dt>
            <dd>{data.itemCount}</dd>
            <dt className="text-ink-muted">{t('admin.detail.minutes')}</dt>
            <dd>{data.estimatedMinutes}</dd>
          </dl>
          <section aria-label={t('admin.detail.payload')}>
            <h3 className="mb-2 text-body font-semibold">{t('admin.detail.payload')}</h3>
            <pre className="max-h-[40vh] overflow-auto whitespace-pre-wrap rounded-lg bg-surface-sunken p-3 text-micro">
              {JSON.stringify(data.payload, null, 2)}
            </pre>
          </section>
          <div className="flex justify-end">
            <Button variant="secondary" onClick={onClose}>
              {t('admin.detail.close')}
            </Button>
          </div>
        </div>
      )}
    </dialog>
  );
}
