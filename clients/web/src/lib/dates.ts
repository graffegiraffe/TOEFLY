// Dates in the English interface with the order of the browser region (AC-32.01.2): en-GB words, "6 Nov 2026"
// in Europe, "Nov 6, 2026" in the US.

function interfaceLocale(): string {
  try {
    const region = new Intl.Locale(navigator.language).maximize().region;
    return region ? `en-${region}` : 'en';
  } catch {
    return 'en';
  }
}

export function formatDate(date: Date, locale: string = interfaceLocale()): string {
  try {
    return new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric' }).format(date);
  } catch {
    return new Intl.DateTimeFormat('en', { day: 'numeric', month: 'short', year: 'numeric' }).format(date);
  }
}

/** Start of the local day. */
export function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

export function addDays(date: Date, days: number): Date {
  const next = startOfDay(date);
  next.setDate(next.getDate() + days);
  return next;
}

/** Bounds of an exam date: tomorrow to five years ahead (AC-02.01.3). */
export function examDateBounds(today: Date): { min: Date; max: Date } {
  const max = startOfDay(today);
  max.setFullYear(max.getFullYear() + 5);
  return { min: addDays(today, 1), max };
}
