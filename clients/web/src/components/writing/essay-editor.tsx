import { Check } from 'lucide-react';
import { useEffect, useRef, useState, type ClipboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';
import { countWords, createPasteGuard } from '@/lib/writing';

interface EssayEditorProps {
  value: string;
  onChange: (text: string) => void;
  onBlur?: () => void;
  /** Academic Discussion shows "N/100" and the 100-word aim (AC-09.03.3). */
  minWords?: number;
  disabled?: boolean;
}

const COUNT_DELAY_MS = 300;

/**
 * EssayEditor (docs/07-ux-ui.md §3.12): plain text without spell check, autocorrect or autocomplete; paste only of
 * text copied inside the editor; a word counter updated after 300 ms.
 */
export function EssayEditor({ value, onChange, onBlur, minWords, disabled = false }: EssayEditorProps) {
  const { t } = useTranslation();
  const guard = useRef(createPasteGuard());
  const [blocked, setBlocked] = useState(false);
  const [words, setWords] = useState(() => countWords(value));

  useEffect(() => {
    const timer = setTimeout(() => setWords(countWords(value)), COUNT_DELAY_MS);
    return () => clearTimeout(timer);
  }, [value]);

  const remember = (event: ClipboardEvent<HTMLTextAreaElement>) => {
    const area = event.currentTarget;
    guard.current.remember(area.value.slice(area.selectionStart, area.selectionEnd));
  };

  const paste = (event: ClipboardEvent<HTMLTextAreaElement>) => {
    if (!guard.current.allows(event.clipboardData.getData('text/plain'))) {
      event.preventDefault();
      setBlocked(true);
    } else {
      setBlocked(false);
    }
  };

  const reached = minWords !== undefined && words >= minWords;
  return (
    <div className="flex flex-1 flex-col gap-2">
      <label htmlFor="essay" className="sr-only">
        {t('writing.editor.label')}
      </label>
      <textarea
        id="essay"
        value={value}
        disabled={disabled}
        onChange={(event) => {
          setBlocked(false);
          onChange(event.target.value);
        }}
        onBlur={onBlur}
        onCopy={remember}
        onCut={remember}
        onPaste={paste}
        onDrop={(event) => event.preventDefault()}
        spellCheck={false}
        autoCorrect="off"
        autoCapitalize="off"
        autoComplete="off"
        data-gramm="false"
        aria-describedby="essay-count"
        className="min-h-[280px] w-full flex-1 resize-y rounded-lg border border-input bg-surface-task p-4 text-body-lg leading-relaxed text-ink focus-visible:outline-2 focus-visible:outline-focus-ring"
      />
      <div className="flex flex-wrap items-center justify-between gap-2 text-caption">
        <span role="status" className={cn('min-h-5 text-warning', !blocked && 'sr-only')}>
          {blocked ? t('writing.editor.pasteBlocked') : ''}
        </span>
        <span
          id="essay-count"
          data-testid="word-count"
          className={cn(
            'ml-auto inline-flex items-center gap-1 font-semibold tabular-nums',
            minWords === undefined ? 'text-ink-muted' : reached ? 'text-success' : 'text-ink-muted',
          )}
        >
          {reached && <Check className="size-3.5" aria-hidden="true" />}
          {minWords === undefined
            ? t('writing.editor.wordCount', { count: words })
            : t('writing.editor.wordProgress', { count: words, min: minWords })}
        </span>
      </div>
      {minWords !== undefined && (
        <>
          <p className="text-caption text-ink-muted">{t('writing.editor.aim', { min: minWords })}</p>
          <span aria-live="polite" className="sr-only">
            {reached ? t('writing.editor.aimReached', { min: minWords }) : ''}
          </span>
        </>
      )}
    </div>
  );
}
