import { Check } from 'lucide-react';
import { useRef, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';
import type { Option } from './content';

interface McqProps {
  options: Option[];
  value: string[];
  onChange: (optionIds: string[]) => void;
  label: string;
  /** Legacy prose summary and similar: several answers (US-07 legacy). */
  multiple?: number;
  disabled?: boolean;
}

/**
 * McqOption group of docs/07-ux-ui.md §3.11: a radiogroup with arrow keys and letter shortcuts; with `multiple`
 * a group of checkboxes limited to that many answers.
 */
export function Mcq({ options, value, onChange, label, multiple, disabled }: McqProps) {
  const { t } = useTranslation();
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const selected = new Set(value);

  const choose = (key: string) => {
    if (disabled) return;
    if (!multiple) {
      onChange([key]);
      return;
    }
    if (selected.has(key)) {
      onChange(value.filter((id) => id !== key));
    } else if (value.length < multiple) {
      onChange([...value, key]);
    }
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const index = refs.current.findIndex((el) => el === document.activeElement);
    if (!multiple && ['ArrowDown', 'ArrowRight', 'ArrowUp', 'ArrowLeft'].includes(event.key)) {
      event.preventDefault();
      const step = event.key === 'ArrowDown' || event.key === 'ArrowRight' ? 1 : -1;
      const next = (Math.max(index, 0) + step + options.length) % options.length;
      refs.current[next]?.focus();
      choose(options[next]!.key);
      return;
    }
    const letter = event.key.toUpperCase();
    const target = options.findIndex((option) => option.key.toUpperCase() === letter);
    if (target >= 0 && event.key.length === 1 && !event.metaKey && !event.ctrlKey && !event.altKey) {
      event.preventDefault();
      refs.current[target]?.focus();
      choose(options[target]!.key);
    }
  };

  const focusIndex = Math.max(
    0,
    options.findIndex((option) => selected.has(option.key)),
  );

  return (
    <div className="flex flex-col gap-2">
      {multiple && (
        <p className="text-caption text-ink-muted">
          {t('practice.multi.choose', { count: multiple })} · {t('practice.multi.selected', { count: value.length })}
        </p>
      )}
      <div
        role={multiple ? 'group' : 'radiogroup'}
        aria-label={label}
        onKeyDown={onKeyDown}
        className="flex flex-col gap-2"
      >
        {options.map((option, i) => {
          const checked = selected.has(option.key);
          return (
            <button
              key={option.key}
              ref={(el) => {
                refs.current[i] = el;
              }}
              type="button"
              role={multiple ? 'checkbox' : 'radio'}
              aria-checked={checked}
              aria-disabled={disabled || undefined}
              tabIndex={multiple || i === focusIndex ? 0 : -1}
              onClick={() => choose(option.key)}
              className={cn(
                'flex w-full items-start gap-3 rounded-[12px] border bg-surface-task px-4 py-3.5 text-left text-body text-ink transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus-ring',
                checked
                  ? 'border-2 border-primary bg-brand-50 dark:bg-accent-soft'
                  : 'border-border hover:border-border-strong',
              )}
            >
              <span
                aria-hidden="true"
                className={cn(
                  'mt-0.5 grid size-[18px] shrink-0 place-items-center border-2',
                  multiple ? 'rounded-[5px]' : 'rounded-full',
                  checked ? 'border-primary' : 'border-border-strong',
                )}
              >
                {checked &&
                  (multiple ? (
                    <Check className="size-3 text-primary" />
                  ) : (
                    <span className="size-2 rounded-full bg-primary" />
                  ))}
              </span>
              <span className="w-5 shrink-0 font-semibold text-ink-muted">{option.key}</span>
              <span className="min-w-0">{option.text}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
