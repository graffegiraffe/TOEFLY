import { useTranslation } from 'react-i18next';
import type { DayBar } from '@/lib/analytics';
import { formatDuration, sectionColor, WEEK_SECTIONS } from '@/lib/analytics';
import { ChartFrame, headClass, tableClass } from './chart-frame';

const HEIGHT = 160;

/** Weekly activity (AC-16.02.1): minutes by day, each bar split by the sections of the week. */
export function ActivityChart({ bars, goalMinutes }: { bars: DayBar[]; goalMinutes: number }) {
  const { t, i18n } = useTranslation();
  const weekday = new Intl.DateTimeFormat(i18n.language, { weekday: 'short', timeZone: 'UTC' });
  const dateOf = (iso: string) => new Date(`${iso}T00:00:00Z`);
  const max = Math.max(goalMinutes * 60, ...bars.map((bar) => bar.total), 60);
  const goalY = HEIGHT - ((goalMinutes * 60) / max) * HEIGHT;
  const present = WEEK_SECTIONS.filter((section) =>
    bars.some((bar) => bar.sections.some((s) => s.section === section)),
  );
  const chart = (
    <div className="flex flex-col gap-3">
      <div className="relative" style={{ height: HEIGHT }}>
        <div
          className="absolute inset-x-0 border-t border-dashed border-border-strong"
          style={{ top: goalY }}
          aria-hidden="true"
        />
        <span
          className="absolute right-0 z-10 -translate-y-full rounded bg-surface px-1 pb-0.5 text-caption text-ink-muted"
          style={{ top: goalY }}
          aria-hidden="true"
        >
          {t('analytics.activity.goalLine', { count: goalMinutes })}
        </span>
        <ol className="absolute inset-0 flex items-end gap-2" aria-label={t('analytics.activity.title')}>
          {bars.map((bar) => (
            <li
              key={bar.date}
              className="flex h-full flex-1 flex-col justify-end"
              aria-label={`${weekday.format(dateOf(bar.date))}: ${formatDuration(bar.total)}`}
            >
              <div className="flex flex-col-reverse overflow-hidden rounded-t-md">
                {bar.sections.map((part) => (
                  <div
                    key={part.section}
                    style={{ height: (part.seconds / max) * HEIGHT, background: sectionColor(part.section) }}
                  />
                ))}
              </div>
            </li>
          ))}
        </ol>
      </div>
      <div className="flex gap-2" aria-hidden="true">
        {bars.map((bar) => (
          <span
            key={bar.date}
            className={`flex-1 text-center text-caption ${bar.goalMet ? 'font-semibold text-ink' : 'text-ink-muted'}`}
          >
            {weekday.format(dateOf(bar.date))}
          </span>
        ))}
      </div>
      {present.length > 0 && (
        <ul className="flex flex-wrap gap-x-4 gap-y-1" aria-label={t('analytics.chart.legend')}>
          {present.map((section) => (
            <li key={section} className="flex items-center gap-1.5 text-caption text-ink-muted">
              <span className="size-2.5 rounded-sm" style={{ background: sectionColor(section) }} aria-hidden="true" />
              {t(`weeks.section.${section}`)}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
  const table = (
    <table className={tableClass}>
      <thead>
        <tr className={headClass}>
          <th scope="col">{t('analytics.activity.day')}</th>
          <th scope="col">{t('analytics.activity.total')}</th>
          <th scope="col">{t('analytics.activity.sections')}</th>
          <th scope="col">{t('analytics.activity.goal')}</th>
        </tr>
      </thead>
      <tbody>
        {bars.map((bar) => (
          <tr key={bar.date} className="border-t border-border">
            <td>{weekday.format(dateOf(bar.date))}</td>
            <td>{formatDuration(bar.total)}</td>
            <td>
              {bar.sections
                .map((part) => `${t(`weeks.section.${part.section}`)} ${formatDuration(part.seconds)}`)
                .join(', ') || '—'}
            </td>
            <td>{bar.goalMet ? t('analytics.activity.goalMet') : '—'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
  return <ChartFrame title={t('analytics.activity.title')} chart={chart} table={table} testId="activity-chart" />;
}
