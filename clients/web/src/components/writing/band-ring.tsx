import { Gauge } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { BandEstimate, SectionEstimate } from '@/api/generated/assessment';
import { Card } from '@/components/ui/card';
import { formatBand } from '@/lib/band';
import {
  formatBandRange,
  formatCefrRange,
  formatComparable,
  remainingHintKey,
  ringFraction,
  SECTIONS,
  sectionLetter,
} from '@/lib/band-estimate';
import { cn } from '@/lib/cn';

function Ring({
  size,
  stroke,
  fraction,
  dashed = false,
  children,
}: {
  size: number;
  stroke: number;
  fraction: number;
  dashed?: boolean;
  children?: ReactNode;
}) {
  const radius = (size - stroke) / 2;
  const length = 2 * Math.PI * radius;
  return (
    <div className="relative grid flex-none place-items-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true" className="-rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          strokeWidth={stroke}
          className="stroke-surface-sunken"
          strokeDasharray={dashed ? '4 6' : undefined}
        />
        {fraction > 0 && (
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="none"
            strokeWidth={stroke}
            strokeLinecap="round"
            className="stroke-brand-accent"
            strokeDasharray={`${length * fraction} ${length}`}
          />
        )}
      </svg>
      <div className="absolute inset-0 grid place-items-center">{children}</div>
    </div>
  );
}

/** BandRing of Home (docs/07-ux-ui.md §3.3, US-13.01): the overall range or "Need more data", and the four sections. */
export function BandRingCard({ estimate }: { estimate: BandEstimate | null | undefined }) {
  const { t } = useTranslation();
  const overall = estimate?.overall ?? null;
  const sections = SECTIONS.map(
    (section) =>
      estimate?.sections.find((item) => item.section === section) ??
      ({ section, status: 'NEED_MORE_DATA', n: 0, remaining: 0 } as SectionEstimate),
  );
  const missing = sections.filter((section) => section.status !== 'READY' || section.low === null);
  return (
    <Card className="flex flex-col gap-4" data-testid="band-ring">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-caption font-semibold uppercase tracking-wide text-ink-muted">{t('home.band.title')}</h2>
        <span className="rounded-full bg-accent-soft px-2 py-0.5 text-micro font-semibold text-accent-on-soft">
          {t('home.estimate')}
        </span>
      </div>
      <div className="flex flex-wrap items-center gap-5">
        {overall ? (
          <Ring size={120} stroke={10} fraction={ringFraction(overall.pointBand)}>
            <span className="text-title-1 font-semibold tabular-nums text-ink">{formatBand(overall.low)}</span>
          </Ring>
        ) : (
          <Ring size={120} stroke={10} fraction={0} dashed>
            <Gauge className="size-8 text-ink-muted" aria-hidden="true" />
          </Ring>
        )}
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          {overall ? (
            <>
              <p className="text-title-2 font-semibold text-ink" data-testid="band-overall">
                {t('home.band.estimated', { range: formatBandRange(overall.low, overall.high) })}
              </p>
              <p className="text-body text-ink-muted">
                {formatCefrRange(overall.cefrLow, overall.cefrHigh) || overall.cefr} ·{' '}
                {formatComparable(overall.comparableLow, overall.comparableHigh)}
              </p>
            </>
          ) : (
            <>
              <p className="text-title-2 font-semibold text-ink">{t('home.needMoreData')}</p>
              <ul className="flex flex-col gap-0.5 text-body text-ink-muted" data-testid="band-hints">
                {(estimate ? missing : []).map((section) => (
                  <li key={section.section}>
                    {t(remainingHintKey(section.section, section.remaining), {
                      count: section.remaining,
                      section: t(`weeks.section.${section.section}`),
                    })}
                  </li>
                ))}
                {!estimate && <li>{t('home.needMoreDataText')}</li>}
              </ul>
            </>
          )}
        </div>
      </div>
      <ul className="flex flex-wrap gap-4" aria-label={t('home.band.sections')}>
        {sections.map((section) => (
          <SectionRing key={section.section} estimate={section} />
        ))}
      </ul>
      <details className="text-caption text-ink-muted">
        <summary className="cursor-pointer font-semibold text-link hover:text-link-hover">{t('home.band.how')}</summary>
        <p className="mt-2">{t('home.band.howText')}</p>
      </details>
    </Card>
  );
}

function SectionRing({ estimate }: { estimate: SectionEstimate }) {
  const { t } = useTranslation();
  const name = t(`weeks.section.${estimate.section}`);
  const ready = estimate.status === 'READY' && estimate.low !== null && estimate.low !== undefined;
  const value = ready ? formatBandRange(estimate.low!, estimate.high ?? estimate.low!) : t('home.band.sectionNoData');
  return (
    <li
      className="flex items-center gap-2"
      aria-label={ready ? t('home.band.sectionReady', { section: name, range: value }) : `${name}: ${value}`}
    >
      <Ring
        size={48}
        stroke={6}
        fraction={ready ? ringFraction(estimate.pointBand ?? estimate.low) : 0}
        dashed={!ready}
      >
        <span className="text-caption font-semibold text-ink" aria-hidden="true">
          {sectionLetter(estimate.section)}
        </span>
      </Ring>
      <span className={cn('text-caption tabular-nums', ready ? 'text-ink' : 'text-ink-muted')} aria-hidden="true">
        {ready ? value : '—'}
      </span>
    </li>
  );
}
