import { Table2 } from 'lucide-react';
import { useId, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Card } from '@/components/ui/card';

/** A chart with its title and the "Show as table" alternative every chart has (WCAG 1.1.1, docs/07 §5.13). */
export function ChartFrame({
  title,
  chart,
  table,
  testId,
  aside,
}: {
  title: string;
  chart: ReactNode;
  table: ReactNode;
  testId: string;
  aside?: ReactNode;
}) {
  const { t } = useTranslation();
  const [asTable, setAsTable] = useState(false);
  const id = useId();
  return (
    <Card className="flex flex-col gap-4" data-testid={testId} aria-labelledby={id} role="region">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id={id} className="text-title-3 font-semibold text-ink">
          {title}
        </h2>
        <div className="flex items-center gap-3">
          {aside}
          <button
            type="button"
            aria-pressed={asTable}
            onClick={() => setAsTable((value) => !value)}
            className="inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-caption font-semibold text-link hover:text-link-hover focus-visible:outline-2 focus-visible:outline-focus-ring"
          >
            <Table2 className="size-4" aria-hidden="true" />
            {asTable ? t('analytics.chart.showChart') : t('analytics.chart.showTable')}
          </button>
        </div>
      </div>
      {asTable ? (
        <div className="overflow-x-auto" tabIndex={0}>
          {table}
        </div>
      ) : (
        chart
      )}
    </Card>
  );
}

export const tableClass = 'w-full text-left text-body [&_td]:py-1.5 [&_td]:pr-4 [&_th]:pb-2 [&_th]:pr-4';
export const headClass = 'text-caption font-semibold uppercase tracking-wide text-ink-muted';
