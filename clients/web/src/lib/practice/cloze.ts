/**
 * Complete the Words (docs/03-domain-model.md §4.1, US-07.01). The learner sees the paragraph markup with each gap
 * as `prefix{_}`; the gaps follow the items in ordinal order.
 */
export type ClozeSegment = { kind: 'text'; text: string } | { kind: 'gap'; index: number; prefix: string };

const GAP = /([\p{L}'-]*)\{[^}]*\}/gu;

export function parseCloze(markup: string): ClozeSegment[] {
  const segments: ClozeSegment[] = [];
  let last = 0;
  let index = 0;
  for (const match of markup.matchAll(GAP)) {
    const start = match.index ?? 0;
    if (start > last) {
      segments.push({ kind: 'text', text: markup.slice(last, start) });
    }
    segments.push({ kind: 'gap', index: index++, prefix: match[1] ?? '' });
    last = start + match[0].length;
  }
  if (last < markup.length) {
    segments.push({ kind: 'text', text: markup.slice(last) });
  }
  return segments;
}

/** AC-07.01.3: a cell takes only a Latin letter, an apostrophe or a hyphen. */
export function acceptsLetter(key: string): boolean {
  return /^[A-Za-z'-]$/.test(key);
}

/** Letters typed into the cells of one gap; empty cells are kept as spaces so positions do not shift. */
export function cellsOf(text: string, length: number): string[] {
  return Array.from({ length }, (_, i) => {
    const letter = text[i];
    return letter && letter !== ' ' ? letter : '';
  });
}

export function textOf(cells: string[]): string {
  return cells
    .map((letter) => letter || ' ')
    .join('')
    .trimEnd();
}
