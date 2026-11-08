import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { adminReportsQuery, useResolveReport } from '@/api/content';
import { ReportStatus, ResolveItemReportRequestOutcomeEnum as Outcome, type ItemReport } from '@/api/generated/content';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { humanize } from '@/lib/admin-content';

export function ReportsPanel() {
  const { t } = useTranslation();
  const [status, setStatus] = useState<ReportStatus>(ReportStatus.Open);
  const { data, isError } = useQuery(adminReportsQuery(status));
  return (
    <div className="flex flex-col gap-4">
      <div role="group" aria-label={t('admin.tabs.reports')} className="flex gap-2">
        {([ReportStatus.Open, ReportStatus.Resolved, ReportStatus.Dismissed] as const).map((value) => (
          <Button
            key={value}
            variant={status === value ? 'secondary' : 'ghost'}
            aria-pressed={status === value}
            onClick={() => setStatus(value)}
          >
            {t(`admin.reports.${value.toLowerCase()}`)}
          </Button>
        ))}
      </div>
      {isError && (
        <p role="alert" className="text-danger">
          {t('admin.error')}
        </p>
      )}
      {data && data.items.length === 0 && <EmptyState title={t('admin.reports.empty')} />}
      <ul className="flex flex-col gap-3">
        {data?.items.map((report) => (
          <ReportCard key={report.id} report={report} />
        ))}
      </ul>
    </div>
  );
}

function ReportCard({ report }: { report: ItemReport }) {
  const { t } = useTranslation();
  const resolve = useResolveReport();
  const decide = (outcome: Outcome, hideItemSet = false) =>
    resolve.mutate({ reportId: report.id, request: { outcome, hideItemSet } });
  return (
    <li className="rounded-xl border border-border bg-surface p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-semibold text-ink">
          {humanize(report.reason)} ·{' '}
          <span className="font-normal text-ink-muted">
            {report.itemOrdinal
              ? t('admin.reports.item', { ordinal: report.itemOrdinal })
              : t('admin.reports.wholeSet')}
          </span>
        </p>
        <time className="text-caption text-ink-muted" dateTime={report.createdAt.toISOString()}>
          {report.createdAt.toLocaleString()}
        </time>
      </div>
      <p className="mt-1 break-all text-micro text-ink-muted">{report.itemSetId}</p>
      {report.comment && <p className="mt-2 text-body text-ink">{report.comment}</p>}
      {report.status === ReportStatus.Open && (
        <div className="mt-3 flex flex-wrap gap-2">
          <Button variant="ghost" disabled={resolve.isPending} onClick={() => decide(Outcome.Dismissed)}>
            {t('admin.reports.dismiss')}
          </Button>
          <Button variant="secondary" disabled={resolve.isPending} onClick={() => decide(Outcome.Resolved)}>
            {t('admin.reports.resolve')}
          </Button>
          <Button variant="destructive" disabled={resolve.isPending} onClick={() => decide(Outcome.Resolved, true)}>
            {t('admin.reports.resolveAndHide')}
          </Button>
        </div>
      )}
      {resolve.isError && (
        <p role="alert" className="mt-2 text-danger">
          {t('admin.error')}
        </p>
      )}
    </li>
  );
}
