import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { adminItemSetsQuery, sourcesQuery, usePatchItemSet } from '@/api/content';
import { ItemStatus, TaskType, type AdminItemSetRow } from '@/api/generated/content';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { actionsFor, humanize, TARGET_STATUS, type ContentFilters, type StatusAction } from '@/lib/admin-content';
import { ItemSetDialog } from './item-set-dialog';
import { StatusBadge } from './status-badge';

const selectClass =
  'h-10 rounded-lg border border-border bg-surface px-3 text-body text-ink focus-visible:outline-2 focus-visible:outline-focus-ring';

export function ItemSetsPanel() {
  const { t } = useTranslation();
  const [filters, setFilters] = useState<ContentFilters>({});
  const [viewing, setViewing] = useState<string | null>(null);
  const query = useInfiniteQuery(adminItemSetsQuery(filters));
  const sources = useQuery(sourcesQuery);
  const rows = query.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <div className="flex flex-col gap-4">
      <form
        className="flex flex-wrap items-end gap-3"
        aria-label={t('admin.tabs.itemSets')}
        onSubmit={(event) => event.preventDefault()}
      >
        <label className="flex flex-col gap-1 text-caption font-semibold">
          {t('admin.filters.status')}
          <select
            className={selectClass}
            value={filters.status ?? ''}
            onChange={(e) =>
              setFilters({ ...filters, status: (e.target.value || undefined) as ItemStatus | undefined })
            }
          >
            <option value="">{t('admin.filters.all')}</option>
            {Object.values(ItemStatus).map((status) => (
              <option key={status} value={status}>
                {t(`admin.status.${status}`)}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-caption font-semibold">
          {t('admin.filters.taskType')}
          <select
            className={selectClass}
            value={filters.taskType ?? ''}
            onChange={(e) => setFilters({ ...filters, taskType: e.target.value || undefined })}
          >
            <option value="">{t('admin.filters.all')}</option>
            {Object.values(TaskType).map((type) => (
              <option key={type} value={type}>
                {humanize(type)}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-caption font-semibold">
          {t('admin.filters.source')}
          <select
            className={selectClass}
            value={filters.sourceCode ?? ''}
            onChange={(e) => setFilters({ ...filters, sourceCode: e.target.value || undefined })}
          >
            <option value="">{t('admin.filters.all')}</option>
            {sources.data?.items.map((source) => (
              <option key={source.code} value={source.code}>
                {source.title}
              </option>
            ))}
          </select>
        </label>
        <label className="flex items-center gap-2 pb-2 text-caption">
          <input
            type="checkbox"
            checked={filters.legacy ?? false}
            onChange={(e) => setFilters({ ...filters, legacy: e.target.checked })}
          />
          {t('admin.filters.legacy')}
        </label>
        <label className="flex items-center gap-2 pb-2 text-caption">
          <input
            type="checkbox"
            checked={filters.reported ?? false}
            onChange={(e) => setFilters({ ...filters, reported: e.target.checked })}
          />
          {t('admin.filters.reported')}
        </label>
      </form>

      {query.isError && (
        <p role="alert" className="text-danger">
          {t('admin.error')}
        </p>
      )}
      {!query.isPending && rows.length === 0 ? (
        <EmptyState title={t('admin.empty')} />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full min-w-[720px] border-collapse text-left text-caption">
            <caption className="sr-only">{t('admin.tabs.itemSets')}</caption>
            <thead className="bg-surface-sunken text-ink-muted">
              <tr>
                {(['topic', 'type', 'source', 'status', 'reports', 'actions'] as const).map((column) => (
                  <th key={column} scope="col" className="px-3 py-2 font-semibold">
                    {t(`admin.columns.${column}`)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <Row key={row.id} row={row} onView={() => setViewing(row.id)} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      {query.hasNextPage && (
        <div className="flex justify-center">
          <Button variant="secondary" onClick={() => void query.fetchNextPage()} disabled={query.isFetchingNextPage}>
            {t('admin.loadMore')}
          </Button>
        </div>
      )}
      <ItemSetDialog itemSetId={viewing} onClose={() => setViewing(null)} />
    </div>
  );
}

function Row({ row, onView }: { row: AdminItemSetRow; onView: () => void }) {
  const { t } = useTranslation();
  const patch = usePatchItemSet();
  const act = (action: StatusAction) => patch.mutate({ itemSetId: row.id, patch: { status: TARGET_STATUS[action] } });
  return (
    <tr className="border-t border-border align-top">
      <td className="px-3 py-2">
        <div className="font-semibold text-ink">{row.topic}</div>
        <div className="text-ink-muted">
          {row.cefr ?? '—'} · v{row.version}
          {row.hiddenReason ? ` · ${humanize(row.hiddenReason)}` : ''}
          {row.extractionWarnings ? ` · ${t('admin.units.warnings', { count: row.extractionWarnings })}` : ''}
        </div>
      </td>
      <td className="px-3 py-2">{humanize(row.taskType)}</td>
      <td className="px-3 py-2">{row.sourceCode}</td>
      <td className="px-3 py-2">
        <StatusBadge status={row.status} />
      </td>
      <td className="px-3 py-2">{row.openReports}</td>
      <td className="px-3 py-2">
        <div className="flex flex-wrap gap-2">
          <Button variant="ghost" className="h-8 px-3 text-caption" onClick={onView}>
            {t('admin.action.view')}
          </Button>
          {actionsFor(row.status).map((action) => (
            <Button
              key={action}
              variant={action === 'approve' ? 'secondary' : 'ghost'}
              className="h-8 px-3 text-caption"
              disabled={patch.isPending}
              onClick={() => act(action)}
            >
              {action === 'approve' && row.status === ItemStatus.Hidden
                ? t('admin.action.restore')
                : t(`admin.action.${action}`)}
            </Button>
          ))}
        </div>
        {patch.isError && (
          <p role="alert" className="mt-1 text-danger">
            {t('admin.error')}
          </p>
        )}
      </td>
    </tr>
  );
}

export type { AdminItemSetRow };
