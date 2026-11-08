import { useTranslation } from 'react-i18next';
import type { Extraction } from '@/api/generated/content';

/**
 * How a seed object was extracted and what the extractor warned about (docs/08-content-pipeline.md §4.7). Sets read
 * by a vision model (OCR_LLM) are never approved automatically, so the administrator checks them against the book.
 */
export function ExtractionNotes({ extraction }: { extraction?: Extraction }) {
  const { t } = useTranslation();
  if (!extraction) {
    return null;
  }
  const { method, warnings } = extraction;
  return (
    <section aria-label={t('admin.extraction.title')} className="flex flex-col gap-2">
      <h3 className="text-body font-semibold">{t('admin.extraction.title')}</h3>
      <p className="text-caption text-ink-muted">
        {t(`admin.extraction.method.${method}`)}
        {method === 'OCR_LLM' ? ` ${t('admin.extraction.checkAgainstBook')}` : ''}
      </p>
      {warnings.length === 0 ? (
        <p className="text-caption text-ink-muted">{t('admin.extraction.noWarnings')}</p>
      ) : (
        <ul className="flex list-disc flex-col gap-1 pl-5 text-caption text-ink">
          {warnings.map((warning) => (
            <li key={warning}>{warning}</li>
          ))}
        </ul>
      )}
    </section>
  );
}
