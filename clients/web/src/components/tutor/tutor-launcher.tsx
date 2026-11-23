import { Sparkles } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useTutor } from './use-tutor';

/** TutorFab (docs/07-ux-ui.md §2.6): a 48px glass circle bottom right; hidden in exam flows (AC-17.05.1). */
export function TutorLauncher() {
  const { t } = useTranslation();
  const tutor = useTutor();
  if (tutor.hidden || tutor.open) return null;
  return (
    <button
      type="button"
      className="glass fixed bottom-[22px] right-[22px] z-20 grid size-12 place-items-center rounded-full text-brand-700 dark:text-brand-500"
      aria-label={t('nav.aiTutor')}
      title={t('nav.aiTutor')}
      onClick={() => tutor.openTutor()}
      data-testid="tutor-fab"
    >
      <Sparkles className="size-5" aria-hidden="true" />
    </button>
  );
}
