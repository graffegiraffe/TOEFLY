import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import type { Answer } from '@/lib/practice/answers';
import { cn } from '@/lib/cn';
import type { ItemContent } from './content';
import { Mcq } from './mcq';

interface ExerciseItemProps {
  item: ItemContent;
  ordinal: number;
  answer: Answer | null;
  onAnswer: (answer: Answer | null) => void;
}

const inputClass =
  'h-10 w-full max-w-md rounded-lg border border-border-strong bg-surface px-3 text-body text-ink focus:border-primary focus:outline-2 focus:outline-focus-ring';

/** One point of a grammar or vocabulary exercise (docs/07-ux-ui.md §5.9, items 13 and 14). */
export function ExerciseItem({ item, ordinal, answer, onAnswer }: ExerciseItemProps) {
  const { t } = useTranslation();
  const id = useId();
  const type = item.exerciseType ?? (item.options ? 'MULTIPLE_CHOICE' : 'GAP_FILL');
  const label = `${ordinal}.`;

  if (item.options && item.options.length > 0) {
    const prompt = item.word ?? item.sentence ?? item.prompt ?? '';
    return (
      <fieldset className="flex flex-col gap-3">
        <legend className="mb-2 text-body font-semibold text-ink">
          {label} {prompt}
        </legend>
        <Mcq
          options={item.options}
          value={answer?.type === 'CHOICE' ? answer.optionIds : []}
          onChange={(ids) => onAnswer({ type: 'CHOICE', optionIds: ids })}
          label={`${t('practice.exercise.choose')} ${ordinal}`}
        />
      </fieldset>
    );
  }

  if (type === 'ERROR_CORRECTION') {
    const pair = answer?.type === 'MAPPING' ? answer.pairs[0] : undefined;
    return (
      <fieldset className="flex flex-col gap-3">
        <legend className="mb-1 text-body font-semibold text-ink">
          {label} {t('practice.exercise.chooseError')}
        </legend>
        <div className="flex flex-wrap gap-1 text-body-lg">
          {(item.fragments ?? []).map((fragment) => (
            <button
              key={fragment.key}
              type="button"
              aria-pressed={pair?.key === fragment.key}
              onClick={() => onAnswer({ type: 'MAPPING', pairs: [{ key: fragment.key, value: pair?.value ?? '' }] })}
              className={cn(
                'rounded px-1 text-ink hover:bg-surface-sunken focus-visible:outline-2 focus-visible:outline-focus-ring',
                pair?.key === fragment.key && 'bg-danger-soft underline decoration-danger decoration-2',
              )}
            >
              {fragment.text}
            </button>
          ))}
        </div>
        {pair && (
          <label className="flex flex-col gap-1 text-caption text-ink-muted" htmlFor={id}>
            {t('practice.exercise.correction')}
            <input
              id={id}
              className={inputClass}
              value={pair.value}
              autoComplete="off"
              spellCheck={false}
              onChange={(event) => onAnswer({ type: 'MAPPING', pairs: [{ key: pair.key, value: event.target.value }] })}
            />
          </label>
        )}
      </fieldset>
    );
  }

  const text = answer?.type === 'TEXT' ? answer.text : '';
  const template = typeof item.template === 'string' ? item.template : undefined;
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={id} className="flex flex-col gap-1 text-body text-ink">
        <span className="font-semibold">
          {label} {item.source ?? item.sentence ?? item.prompt}
        </span>
        {item.baseWord && (
          <span className="text-caption text-ink-muted">
            {t('practice.exercise.baseWord', { word: item.baseWord })}
          </span>
        )}
        {item.keyWord && (
          <span className="text-caption text-ink-muted">{t('practice.exercise.keyWord', { word: item.keyWord })}</span>
        )}
        {template && <span className="text-body text-ink">{template}</span>}
        {item.hint && <span className="text-caption text-ink-muted">{item.hint}</span>}
      </label>
      <input
        id={id}
        className={inputClass}
        value={text}
        placeholder={t('practice.exercise.answer')}
        autoComplete="off"
        autoCorrect="off"
        autoCapitalize="off"
        spellCheck={false}
        onChange={(event) => onAnswer({ type: 'TEXT', text: event.target.value })}
      />
    </div>
  );
}
