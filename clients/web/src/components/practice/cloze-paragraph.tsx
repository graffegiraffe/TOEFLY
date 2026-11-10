import { useMemo, useRef, type ChangeEvent, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { acceptsLetter, cellsOf, parseCloze, textOf } from '@/lib/practice/cloze';

interface ClozeParagraphProps {
  markup: string;
  /** Cells per gap, in gap order. */
  lengths: number[];
  values: string[];
  onChange: (gap: number, text: string) => void;
}

/**
 * ClozeInput of docs/07-ux-ui.md §3.9 (US-07.01): one cell per missing letter; typing moves on, Backspace in an
 * empty cell goes back, arrows move between cells and Tab between gaps.
 */
export function ClozeParagraph({ markup, lengths, values, onChange }: ClozeParagraphProps) {
  const { t } = useTranslation();
  const segments = useMemo(() => parseCloze(markup), [markup]);
  const cells = useRef(new Map<string, HTMLInputElement | null>());
  const total = lengths.length;

  const focus = (gap: number, cell: number) => {
    if (gap < 0 || gap >= total) return false;
    const length = lengths[gap] ?? 1;
    const target = cells.current.get(`${gap}-${Math.max(0, Math.min(cell, length - 1))}`);
    target?.focus();
    target?.select();
    return Boolean(target);
  };

  const setLetter = (gap: number, cell: number, letter: string) => {
    const current = cellsOf(values[gap] ?? '', lengths[gap] ?? 1);
    current[cell] = letter;
    onChange(gap, textOf(current));
  };

  const advance = (gap: number, cell: number) => {
    if (cell + 1 < (lengths[gap] ?? 1)) focus(gap, cell + 1);
    else focus(gap + 1, 0);
  };

  const onKeyDown = (gap: number, cell: number) => (event: KeyboardEvent<HTMLInputElement>) => {
    const letters = cellsOf(values[gap] ?? '', lengths[gap] ?? 1);
    if (event.metaKey || event.ctrlKey || event.altKey) return;
    if (acceptsLetter(event.key)) {
      event.preventDefault();
      setLetter(gap, cell, event.key);
      advance(gap, cell);
    } else if (event.key === 'Backspace') {
      event.preventDefault();
      if (letters[cell]) {
        setLetter(gap, cell, '');
      } else if (cell > 0) {
        setLetter(gap, cell - 1, '');
        focus(gap, cell - 1);
      } else if (gap > 0) {
        focus(gap - 1, (lengths[gap - 1] ?? 1) - 1);
      }
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      if (cell > 0) focus(gap, cell - 1);
      else focus(gap - 1, (lengths[gap - 1] ?? 1) - 1);
    } else if (event.key === 'ArrowRight') {
      event.preventDefault();
      advance(gap, cell);
    } else if (event.key.length === 1) {
      event.preventDefault(); // AC-07.01.3: no digits, spaces or other alphabets
    }
  };

  // Touch keyboards may not send key events: take the last typed character.
  const onInput = (gap: number, cell: number) => (event: ChangeEvent<HTMLInputElement>) => {
    const letter = event.target.value.slice(-1);
    if (acceptsLetter(letter)) {
      setLetter(gap, cell, letter);
      advance(gap, cell);
    } else if (event.target.value === '') {
      setLetter(gap, cell, '');
    }
  };

  return (
    <p className="mx-auto max-w-[68ch] text-body-lg leading-[2.4] text-ink">
      {segments.map((segment, i) => {
        if (segment.kind === 'text') {
          return <span key={i}>{segment.text}</span>;
        }
        const gap = segment.index;
        const length = lengths[gap] ?? 1;
        const letters = cellsOf(values[gap] ?? '', length);
        return (
          <span key={i} className="whitespace-nowrap" data-gap={gap}>
            <span>{segment.prefix}</span>
            {letters.map((letter, cell) => (
              <input
                key={cell}
                ref={(el) => {
                  cells.current.set(`${gap}-${cell}`, el);
                }}
                value={letter}
                onChange={onInput(gap, cell)}
                onKeyDown={onKeyDown(gap, cell)}
                onFocus={(event) => event.target.select()}
                tabIndex={cell === 0 ? 0 : -1}
                inputMode="text"
                maxLength={2}
                autoComplete="off"
                autoCorrect="off"
                autoCapitalize="off"
                spellCheck={false}
                aria-label={t('practice.cloze.gap', {
                  n: gap + 1,
                  total,
                  prefix: segment.prefix,
                  letter: cell + 1,
                  length,
                })}
                className="mx-px w-[0.95em] border-0 border-b-[1.5px] border-border-strong bg-transparent p-0 text-center font-medium text-ink caret-primary focus:border-primary focus:bg-accent-soft focus:outline-none"
              />
            ))}
          </span>
        );
      })}
    </p>
  );
}
