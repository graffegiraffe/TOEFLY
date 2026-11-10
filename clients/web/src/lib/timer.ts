/** Practice timer (docs/07-ux-ui.md §3.6): counts down to the ETS norm, then up as overtime. */
export function formatClock(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const minutes = Math.floor(seconds / 60);
  return `${String(minutes).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
}

export function timerText(normSeconds: number, elapsedSeconds: number): { text: string; overtime: boolean } {
  const left = normSeconds - elapsedSeconds;
  return left >= 0 ? { text: formatClock(left), overtime: false } : { text: `+${formatClock(-left)}`, overtime: true };
}

/**
 * Pace against the ETS norm (docs/03-domain-model.md §13.4): a deviation of more than 30% is highlighted.
 * Returns the signed percent, or null when within the norm.
 */
export function paceDeviation(activeSeconds: number, normSeconds: number): number | null {
  if (normSeconds <= 0 || activeSeconds <= 0) {
    return null;
  }
  const ratio = activeSeconds / normSeconds;
  return Math.abs(ratio - 1) > 0.3 ? Math.round((ratio - 1) * 100) : null;
}
