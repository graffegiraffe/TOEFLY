import { useTranslation } from 'react-i18next';
import type { TaskTypeTime } from '@/api/generated/analytics';
import { Card } from '@/components/ui/card';
import { formatDuration, pace } from '@/lib/analytics';
import { cn } from '@/lib/cn';
import { headClass, tableClass } from './chart-frame';

/** Time against ETS norms by task type (AC-16.02.2): a deviation over 30% is highlighted. */
export function PaceTable({ rows }: { rows: TaskTypeTime[] }) {
  const { t } = useTranslation();
  const sorted = [...rows].sort((a, b) => b.activeSeconds - a.activeSeconds);
  return (
    <Card className="flex flex-col gap-4" data-testid="pace-table">
      <div>
        <h2 className="text-title-3 font-semibold text-ink">{t('analytics.pace.title')}</h2>
        <p className="text-caption text-ink-muted">{t('analytics.pace.subtitle')}</p>
      </div>
      {sorted.length === 0 ? (
        <p className="text-body text-ink-muted">{t('analytics.pace.empty')}</p>
      ) : (
        <div className="overflow-x-auto" tabIndex={0}>
          <table className={tableClass}>
            <thead>
              <tr className={headClass}>
                <th scope="col">{t('analytics.pace.type')}</th>
                <th scope="col">{t('analytics.pace.time')}</th>
                <th scope="col">{t('analytics.pace.norm')}</th>
                <th scope="col">{t('analytics.pace.pace')}</th>
              </tr>
            </thead>
            <tbody>
              {sorted.map((row) => {
                const value = pace(row.paceRatio, row.verdict);
                return (
                  <tr key={row.taskType} className="border-t border-border">
                    <td className="font-semibold text-ink">{t(`practice.taskType.${row.taskType}`)}</td>
                    <td>{formatDuration(row.activeSeconds)}</td>
                    <td>{row.normSeconds > 0 ? formatDuration(row.normSeconds) : '—'}</td>
                    <td>
                      <span
                        className={cn(
                          'inline-flex rounded-full px-2.5 py-0.5 text-caption font-semibold',
                          value.kind === 'slower' && 'bg-warning-soft text-warning',
                          value.kind === 'faster' && 'bg-info-soft text-info',
                          value.kind === 'onPace' && 'bg-success-soft text-success',
                          value.kind === 'noNorm' && 'text-ink-muted',
                        )}
                      >
                        {t(`analytics.pace.${value.kind}`, { percent: value.percent })}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}
