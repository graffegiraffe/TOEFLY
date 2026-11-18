// Formatting of the BandEstimate of assessment-service for the BandRing (docs/07-ux-ui.md §3.3, US-13.01).
import { formatBand } from './band';

export const SECTIONS = ['READING', 'LISTENING', 'WRITING', 'SPEAKING'] as const;
export type EstimateSection = (typeof SECTIONS)[number];

/** "4.0–4.5", or "4.5" when the range is a single band. */
export function formatBandRange(low: number, high: number): string {
  return low === high ? formatBand(low) : `${formatBand(low)}–${formatBand(high)}`;
}

/** "B2", or "B2 → C1" when the range crosses a CEFR level. */
export function formatCefrRange(low: string | null | undefined, high: string | null | undefined): string {
  if (!low && !high) return '';
  if (!low || !high || low === high) return (low ?? high)!;
  return `${low} → ${high}`;
}

/** "≈ 72–83 of 120". */
export function formatComparable(low: number, high: number): string {
  return low === high ? `≈ ${low} of 120` : `≈ ${low}–${high} of 120`;
}

/** Share of the ring filled by a band of the 1–6 scale. */
export function ringFraction(band: number | null | undefined): number {
  if (band === null || band === undefined || Number.isNaN(band)) return 0;
  return Math.max(0, Math.min(1, band / 6));
}

/** One-letter label of a section for the mini rings. */
export function sectionLetter(section: string): string {
  return section.charAt(0);
}

/** The i18n key of the hint for a section that needs more data. */
export function remainingHintKey(section: string, remaining: number): string {
  if (remaining <= 0) return 'home.band.hint.wait';
  switch (section) {
    case 'READING':
    case 'LISTENING':
      return 'home.band.hint.questions';
    case 'WRITING':
      return 'home.band.hint.essays';
    default:
      return 'home.band.hint.speaking';
  }
}
