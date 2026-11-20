import { RefreshCw } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { freshness } from '@/lib/analytics';

/** "Updated 3 min ago": the read model is eventually consistent, so the screen says how fresh it is (AC-05.02.3). */
export function Freshness({ asOf, now = new Date() }: { asOf?: Date | null; now?: Date }) {
  const { t } = useTranslation();
  const value = freshness(asOf, now);
  return (
    <p className="flex items-center gap-1.5 text-caption text-ink-muted" data-testid="freshness">
      <RefreshCw className="size-3.5" aria-hidden="true" />
      {t(`analytics.freshness.${value.key}`, { count: value.count })}
    </p>
  );
}
