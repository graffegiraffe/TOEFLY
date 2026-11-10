/**
 * Reading passages (US-07.03): sentences for the insert-sentence markers and ranges for the highlighted word.
 * A marker stands after sentence `afterSentence` (0-based) of its paragraph (docs/03-domain-model.md §4.3).
 */
export interface Marker {
  key: string;
  paragraph: number;
  afterSentence: number;
}

export function splitSentences(paragraph: string): string[] {
  const parts = paragraph.match(/[^.!?]+(?:[.!?]+["')\]]*|$)\s*/g);
  return (parts ?? [paragraph]).map((part) => part.trim()).filter(Boolean);
}

export type PassagePiece = { kind: 'text'; text: string; highlight?: boolean } | { kind: 'marker'; key: string };

export interface Highlight {
  paragraph: number;
  charStart: number;
  charEnd: number;
}

/**
 * Pieces of one paragraph: its text, the highlighted range and the markers between sentences. Markers that point
 * past the last sentence are placed at its end, so a slightly different split never hides a marker.
 */
export function paragraphPieces(
  text: string,
  paragraph: number,
  markers: Marker[] = [],
  highlight?: Highlight | null,
): PassagePiece[] {
  const own = markers.filter((marker) => marker.paragraph === paragraph);
  const hl = highlight && highlight.paragraph === paragraph ? highlight : null;
  if (own.length === 0) {
    return withHighlight(text, 0, hl);
  }
  const sentences = splitSentences(text);
  const pieces: PassagePiece[] = [];
  let offset = 0;
  sentences.forEach((sentence, i) => {
    const start = text.indexOf(sentence, offset);
    offset = start >= 0 ? start + sentence.length : offset;
    pieces.push(...withHighlight(`${i > 0 ? ' ' : ''}${sentence}`, Math.max(0, start - (i > 0 ? 1 : 0)), hl));
    const last = i === sentences.length - 1;
    own
      .filter((marker) => marker.afterSentence === i || (last && marker.afterSentence > i))
      .forEach((marker) => pieces.push({ kind: 'marker', key: marker.key }));
  });
  return pieces;
}

function withHighlight(text: string, base: number, hl: Highlight | null): PassagePiece[] {
  if (!hl) {
    return [{ kind: 'text', text }];
  }
  const from = Math.max(0, hl.charStart - base);
  const to = Math.min(text.length, hl.charEnd - base);
  if (to <= 0 || from >= text.length || from >= to) {
    return [{ kind: 'text', text }];
  }
  return [
    { kind: 'text', text: text.slice(0, from) },
    { kind: 'text', text: text.slice(from, to), highlight: true },
    { kind: 'text', text: text.slice(to) },
  ].filter((piece) => piece.text.length > 0) as PassagePiece[];
}
