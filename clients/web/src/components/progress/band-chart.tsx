import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Section, WeekPoint } from '@/api/generated/analytics';
import { linePath, scale } from '@/lib/analytics';
import { formatBand } from '@/lib/band';
import { cn } from '@/lib/cn';
import { ChartFrame, headClass, tableClass } from './chart-frame';

const WIDTH = 640;
const HEIGHT = 200;
const PAD = 28;
const SECTIONS: Section[] = ['READING', 'LISTENING', 'WRITING', 'SPEAKING'] as Section[];
const MARKERS: Record<string, string> = {
  READING: 'circle',
  LISTENING: 'square',
  WRITING: 'diamond',
  SPEAKING: 'triangle',
};

function Marker({ x, y, shape, color }: { x: number; y: number; shape: string; color: string }) {
  if (shape === 'square') return <rect x={x - 3.5} y={y - 3.5} width={7} height={7} fill={color} />;
  if (shape === 'diamond')
    return <path d={`M${x},${y - 5} L${x + 5},${y} L${x},${y + 5} L${x - 5},${y} Z`} fill={color} />;
  if (shape === 'triangle') return <path d={`M${x},${y - 5} L${x + 5},${y + 4} L${x - 5},${y + 4} Z`} fill={color} />;
  return <circle cx={x} cy={y} r={4} fill={color} />;
}

/**
 * Band over the weeks (AC-13.02.1): the overall estimate as a range band, sections as switchable lines with distinct
 * markers, the target as a horizontal line; a week without an estimate breaks the line (AC-13.02.2).
 */
export function BandChart({ weeks, target }: { weeks: WeekPoint[]; target?: number | null }) {
  const { t } = useTranslation();
  const [shown, setShown] = useState<Set<string>>(new Set());
  const innerWidth = WIDTH - PAD * 2;
  const innerHeight = HEIGHT - PAD * 2;
  const y = (band: number) => PAD + innerHeight - ((band - 1) / 5) * innerHeight;
  const overall = scale(
    weeks.map((w) => w.overall?.point),
    innerWidth,
    innerHeight,
    1,
    6,
  ).map((p) => ({ x: p.x + PAD, y: p.y === null ? null : p.y + PAD }));
  const bandArea = weeks
    .map((w, i) => ({ w, x: overall[i]?.x ?? 0 }))
    .filter(({ w }) => w.overall)
    .map(({ w, x }) => ({ x, low: y(w.overall!.low), high: y(w.overall!.high) }));
  const sectionLine = (section: string) =>
    scale(
      weeks.map((w) => w.sections.find((s) => s.section === section)?.point),
      innerWidth,
      innerHeight,
      1,
      6,
    ).map((p) => ({ x: p.x + PAD, y: p.y === null ? null : p.y + PAD }));
  const toggle = (section: string) =>
    setShown((current) => {
      const next = new Set(current);
      if (next.has(section)) next.delete(section);
      else next.add(section);
      return next;
    });
  const chart =
    weeks.length === 0 ? (
      <p className="text-body text-ink-muted">{t('analytics.band.empty')}</p>
    ) : (
      <div className="flex flex-col gap-3">
        <svg
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          className="h-auto w-full"
          role="img"
          aria-label={t('analytics.band.chartLabel')}
        >
          {[1, 2, 3, 4, 5, 6].map((band) => (
            <g key={band}>
              <line x1={PAD} x2={WIDTH - PAD} y1={y(band)} y2={y(band)} className="stroke-border" strokeWidth={1} />
              <text x={4} y={y(band) + 4} className="fill-ink-muted text-[11px]">
                {band}
              </text>
            </g>
          ))}
          {bandArea.map((area) => (
            <rect
              key={area.x}
              x={area.x - 9}
              y={area.high}
              width={18}
              height={Math.max(2, area.low - area.high)}
              rx={4}
              className="fill-accent-soft"
            />
          ))}
          {target && (
            <line
              x1={PAD}
              x2={WIDTH - PAD}
              y1={y(target)}
              y2={y(target)}
              strokeDasharray="6 4"
              strokeWidth={1.5}
              className="stroke-warning"
              data-testid="band-target"
            />
          )}
          <path d={linePath(overall)} fill="none" strokeWidth={2.5} className="stroke-brand-accent" />
          {overall.map((p, i) =>
            p.y === null ? null : <circle key={i} cx={p.x} cy={p.y} r={4} className="fill-brand-accent" />,
          )}
          {SECTIONS.filter((s) => shown.has(s)).map((section) => {
            const color = `var(--tag-${section.toLowerCase()}-fg)`;
            const points = sectionLine(section);
            return (
              <g key={section}>
                <path d={linePath(points)} fill="none" strokeWidth={1.5} stroke={color} />
                {points.map((p, i) =>
                  p.y === null ? null : (
                    <Marker key={i} x={p.x} y={p.y} shape={MARKERS[section] ?? 'circle'} color={color} />
                  ),
                )}
              </g>
            );
          })}
          {weeks.map((w, i) => (
            <text
              key={w.weekNo}
              x={overall[i]?.x ?? 0}
              y={HEIGHT - 6}
              textAnchor="middle"
              className="fill-ink-muted text-[11px]"
            >
              {t('analytics.band.weekShort', { number: w.weekNo })}
            </text>
          ))}
        </svg>
        <div className="flex flex-wrap gap-2" role="group" aria-label={t('analytics.band.sections')}>
          {SECTIONS.map((section) => (
            <button
              key={section}
              type="button"
              aria-pressed={shown.has(section)}
              onClick={() => toggle(section)}
              className={cn(
                'rounded-full border px-3 py-1 text-caption font-semibold focus-visible:outline-2 focus-visible:outline-focus-ring',
                shown.has(section)
                  ? 'border-transparent bg-accent-soft text-accent-on-soft'
                  : 'border-border text-ink-muted',
              )}
            >
              {t(`weeks.section.${section}`)}
            </button>
          ))}
        </div>
        <p className="text-caption text-ink-muted">{t('home.estimate')}</p>
      </div>
    );
  const table = (
    <table className={tableClass}>
      <thead>
        <tr className={headClass}>
          <th scope="col">{t('analytics.band.week')}</th>
          <th scope="col">{t('analytics.band.overall')}</th>
          {SECTIONS.map((section) => (
            <th key={section} scope="col">
              {t(`weeks.section.${section}`)}
            </th>
          ))}
          <th scope="col">{t('analytics.band.progress')}</th>
        </tr>
      </thead>
      <tbody>
        {weeks.map((w) => (
          <tr key={w.weekNo} className="border-t border-border">
            <td>{t('analytics.band.weekShort', { number: w.weekNo })}</td>
            <td>{w.overall ? `${formatBand(w.overall.low)}–${formatBand(w.overall.high)}` : t('home.needMoreData')}</td>
            {SECTIONS.map((section) => {
              const point = w.sections.find((s) => s.section === section)?.point;
              return <td key={section}>{point === undefined ? '—' : formatBand(point)}</td>;
            })}
            <td>{Math.round(w.progressPercent)}%</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
  return <ChartFrame title={t('analytics.band.title')} chart={chart} table={table} testId="band-chart" />;
}
