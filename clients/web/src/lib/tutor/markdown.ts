/**
 * The Markdown subset of a tutor answer (docs/07-ux-ui.md §3.16): paragraphs, bulleted and numbered lists, bold,
 * italic and inline code. The answer is parsed into plain data and rendered as React elements, never as HTML, so a
 * streamed partial answer is always safe to show.
 */
export type Inline = { kind: 'text' | 'strong' | 'em' | 'code'; text: string };

export type Block = { kind: 'p'; inline: Inline[] } | { kind: 'ul' | 'ol'; items: Inline[][] };

const INLINE = /(\*\*[^*]+\*\*|`[^`]+`|\*[^*\s][^*]*\*|_[^_\s][^_]*_)/g;

export function parseInline(text: string): Inline[] {
  const out: Inline[] = [];
  let last = 0;
  for (const match of text.matchAll(INLINE)) {
    const token = match[0];
    const at = match.index;
    if (at > last) out.push({ kind: 'text', text: text.slice(last, at) });
    if (token.startsWith('**')) out.push({ kind: 'strong', text: token.slice(2, -2) });
    else if (token.startsWith('`')) out.push({ kind: 'code', text: token.slice(1, -1) });
    else out.push({ kind: 'em', text: token.slice(1, -1) });
    last = at + token.length;
  }
  if (last < text.length) out.push({ kind: 'text', text: text.slice(last) });
  return out;
}

const BULLET = /^\s*[-*•]\s+(.*)$/;
const NUMBER = /^\s*\d+[.)]\s+(.*)$/;

export function parseMarkdown(source: string): Block[] {
  const blocks: Block[] = [];
  let paragraph: string[] = [];
  let list = null as { kind: 'ul' | 'ol'; items: Inline[][] } | null;

  const flushParagraph = () => {
    if (paragraph.length > 0) blocks.push({ kind: 'p', inline: parseInline(paragraph.join(' ')) });
    paragraph = [];
  };
  const flushList = () => {
    if (list) blocks.push(list);
    list = null;
  };

  for (const line of source.replace(/\r\n?/g, '\n').split('\n')) {
    const bullet = BULLET.exec(line);
    const number = bullet ? null : NUMBER.exec(line);
    if (bullet || number) {
      flushParagraph();
      const kind = bullet ? 'ul' : 'ol';
      if (list?.kind !== kind) {
        flushList();
        list = { kind, items: [] };
      }
      list.items.push(parseInline((bullet ?? number)![1] ?? ''));
    } else if (line.trim() === '') {
      flushParagraph();
      flushList();
    } else {
      flushList();
      paragraph.push(line.trim());
    }
  }
  flushParagraph();
  flushList();
  return blocks;
}
