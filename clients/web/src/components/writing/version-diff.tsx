import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { wordDiff } from '@/lib/writing';

/** "Compare with Draft" (docs/07-ux-ui.md §5.10): removed words struck through in danger, added underlined in success. */
export function VersionDiff({ before, after }: { before: string; after: string }) {
  const { t } = useTranslation();
  const ops = useMemo(() => wordDiff(before, after), [before, after]);
  return (
    <div
      className="whitespace-pre-wrap rounded-xl border border-border bg-surface-task p-5 text-body leading-relaxed text-ink"
      data-testid="version-diff"
    >
      {ops.map((op, index) =>
        op.type === 'same' ? (
          <span key={index}>{op.text}</span>
        ) : op.type === 'removed' ? (
          <del key={index} className="text-danger line-through">
            <span className="sr-only">{t('writing.review.removed')} </span>
            {op.text}
          </del>
        ) : (
          <ins key={index} className="text-success underline decoration-2 underline-offset-2">
            <span className="sr-only">{t('writing.review.added')} </span>
            {op.text}
          </ins>
        ),
      )}
    </div>
  );
}
