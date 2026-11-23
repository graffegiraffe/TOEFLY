import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';

/** FSRS stability as four dots (docs/07-ux-ui.md §5.14). */
export function StabilityDots({ level }: { level: number }) {
  const { t } = useTranslation();
  return (
    <span className="inline-flex items-center gap-1" role="img" aria-label={t('vocabulary.strength', { level })}>
      {[1, 2, 3, 4].map((dot) => (
        <span
          key={dot}
          className={cn('size-2 rounded-full', dot <= level ? 'bg-tag-vocabulary-fg' : 'bg-surface-sunken')}
        />
      ))}
    </span>
  );
}
