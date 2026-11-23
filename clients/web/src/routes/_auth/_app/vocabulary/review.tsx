import { useMutation } from '@tanstack/react-query';
import { createFileRoute, notFound, useNavigate } from '@tanstack/react-router';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { completeReviewSession, gradeCard, startReviewSession, useRefreshAfterReview } from '@/api/vocabulary';
import { EmptyState } from '@/components/ui/empty-state';
import { ReviewSessionView } from '@/components/vocabulary/review-session';

const search = z.object({ weekTaskId: z.string().uuid().optional() });

/** FSRS review session (docs/07-ux-ui.md §5.14, US-15.03); `weekTaskId` links it to VOCABULARY_REVIEW of the week. */
export const Route = createFileRoute('/_auth/_app/vocabulary/review')({
  validateSearch: search,
  beforeLoad: ({ context }) => {
    if (!context.session.user.roles.includes('STUDENT')) {
      throw notFound();
    }
  },
  component: ReviewPage,
});

function ReviewPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { weekTaskId } = Route.useSearch();
  const refresh = useRefreshAfterReview();
  // AC-15.03.4: an empty day is completed at once, so the week task of the day can be closed.
  const start = useMutation({
    mutationFn: async () => {
      const session = await startReviewSession(weekTaskId);
      if (session.cards.length > 0) return session;
      const completed = await completeReviewSession(session.id);
      void refresh();
      return completed;
    },
  });
  const { mutate } = start;

  useEffect(() => {
    mutate();
  }, [mutate]);

  if (start.isError) return <EmptyState title={t('errors.generic')} />;
  if (!start.data) {
    return (
      <div
        className="mx-auto h-80 w-full max-w-[520px] animate-pulse rounded-[20px] bg-surface-sunken"
        aria-busy="true"
      />
    );
  }
  const session = start.data;
  return (
    <ReviewSessionView
      session={session}
      grade={(card, rating, sequenceNo, elapsedMs) => gradeCard(card, rating, session.id, sequenceNo, elapsedMs)}
      complete={() => completeReviewSession(session.id).finally(() => void refresh())}
      onDone={() => void navigate({ to: '/vocabulary' })}
    />
  );
}
