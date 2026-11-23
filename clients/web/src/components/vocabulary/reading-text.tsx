import { useTranslation } from 'react-i18next';

interface ReadingStimulus {
  title?: string;
  paragraphs?: string[];
  text?: string;
  body?: string;
}

/** The passage of a Reading task in the review, folded, so its words can be selected (US-15.01). */
export function ReadingText({ stimulus }: { stimulus: unknown }) {
  const { t } = useTranslation();
  const value = (stimulus ?? {}) as ReadingStimulus;
  const paragraphs = Array.isArray(value.paragraphs)
    ? value.paragraphs
    : [value.text, value.body]
        .filter((part): part is string => typeof part === 'string')
        .flatMap((part) => part.split(/\n{2,}/));
  if (paragraphs.length === 0) return null;
  return (
    <details className="group rounded-xl border border-border bg-surface shadow-e1" data-testid="reading-text">
      <summary className="cursor-pointer list-none p-4 text-body font-semibold text-ink focus-visible:outline-2 focus-visible:outline-focus-ring">
        {value.title ? t('vocabulary.text.titled', { title: value.title }) : t('vocabulary.text.title')}
        <span className="ml-2 text-caption font-normal text-ink-muted">{t('vocabulary.text.hint')}</span>
      </summary>
      <div className="flex flex-col gap-3 border-t border-border p-4">
        {paragraphs.map((paragraph, index) => (
          <p key={index} className="text-body-lg leading-relaxed text-ink">
            {paragraph}
          </p>
        ))}
      </div>
    </details>
  );
}
