import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { sourcesQuery, useHideSource, useStartImport } from '@/api/content';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';

export function SourcesPanel() {
  const { t } = useTranslation();
  const { data } = useQuery(sourcesQuery);
  const hide = useHideSource();
  const [hiddenCount, setHiddenCount] = useState<number | null>(null);
  return (
    <div className="flex flex-col gap-6">
      <section aria-labelledby="sources-heading" className="flex flex-col gap-3">
        <h2 id="sources-heading" className="sr-only">
          {t('admin.tabs.sources')}
        </h2>
        {data && data.items.length === 0 && <EmptyState title={t('admin.sources.empty')} />}
        <ul className="flex flex-col gap-3">
          {data?.items.map((source) => (
            <li
              key={source.code}
              className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-surface p-4"
            >
              <div className="min-w-0">
                <p className="font-semibold text-ink">{source.title}</p>
                <p className="text-caption text-ink-muted">{source.attribution}</p>
              </div>
              <Button
                variant="destructive"
                disabled={hide.isPending}
                onClick={() => {
                  if (window.confirm(t('admin.sources.confirm', { title: source.title }))) {
                    hide.mutate(source.code, { onSuccess: (result) => setHiddenCount(result.hidden) });
                  }
                }}
              >
                {t('admin.sources.hideAll')}
              </Button>
            </li>
          ))}
        </ul>
        {hiddenCount !== null && (
          <p role="status" className="text-caption text-ink">
            {t('admin.sources.hidden', { count: hiddenCount })}
          </p>
        )}
        {hide.isError && (
          <p role="alert" className="text-danger">
            {t('admin.error')}
          </p>
        )}
      </section>
      <ImportBox />
    </div>
  );
}

function ImportBox() {
  const { t } = useTranslation();
  const run = useStartImport();
  const [dryRun, setDryRun] = useState(true);
  return (
    <section
      aria-labelledby="import-heading"
      className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4"
    >
      <h2 id="import-heading" className="text-body font-semibold">
        {t('admin.sources.importTitle')}
      </h2>
      <label className="flex items-center gap-2 text-caption">
        <input type="checkbox" checked={dryRun} onChange={(e) => setDryRun(e.target.checked)} />
        {t('admin.sources.dryRun')}
      </label>
      <div>
        <Button variant="secondary" disabled={run.isPending} onClick={() => run.mutate({ dryRun })}>
          {t('admin.sources.run')}
        </Button>
      </div>
      <ul role="status" className="text-caption text-ink">
        {run.data?.sources.map((s) => (
          <li key={s.sourceCode}>
            {t('admin.sources.importResult', {
              code: s.sourceCode,
              status: s.status,
              created: s.created,
              updated: s.updated,
              unchanged: s.unchanged,
              hidden: s.hidden,
            })}
            {s.error ? ` — ${s.error}` : ''}
          </li>
        ))}
      </ul>
      {run.isError && (
        <p role="alert" className="text-danger">
          {t('admin.error')}
        </p>
      )}
    </section>
  );
}
