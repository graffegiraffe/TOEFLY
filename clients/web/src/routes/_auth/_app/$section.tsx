import { createFileRoute, notFound } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import { PLACEHOLDER_SECTIONS } from '@/components/shell/navigation';
import { EmptyState } from '@/components/ui/empty-state';

/** Sections of later phases show their empty state until their screens exist. */
export const Route = createFileRoute('/_auth/_app/$section')({
  beforeLoad: ({ params }) => {
    if (!(PLACEHOLDER_SECTIONS as readonly string[]).includes(params.section)) {
      throw notFound();
    }
  },
  component: Section,
});

function Section() {
  const { t } = useTranslation();
  const { section } = Route.useParams();
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-title-1 font-semibold capitalize tracking-tight text-ink">{section.replace('-', ' ')}</h1>
      <EmptyState title={t('section.emptyTitle')} text={t('section.emptyText')} />
    </div>
  );
}
