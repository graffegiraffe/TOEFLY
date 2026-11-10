import { useQuery } from '@tanstack/react-query';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import { attemptQuery, reviewQuery, useStartAttempt } from '@/api/practice';
import { ReviewView } from '@/components/practice/review-view';
import { EmptyState } from '@/components/ui/empty-state';

/** Review of a closed task (docs/07-ux-ui.md §5.10, US-11.01). */
export const Route = createFileRoute('/_auth/_app/review/attempts/$attemptId')({
  component: AttemptReviewPage,
});

function AttemptReviewPage() {
  const { t } = useTranslation();
  const { attemptId } = Route.useParams();
  const navigate = useNavigate();
  const review = useQuery(reviewQuery(attemptId));
  const attempt = useQuery({ ...attemptQuery(attemptId), staleTime: 0 });
  const start = useStartAttempt();

  if (review.isPending || attempt.isPending) {
    return <div className="h-96 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />;
  }
  if (review.isError || attempt.isError) {
    return <EmptyState title={t('review.notScored')} />;
  }
  return (
    <ReviewView
      review={review.data}
      attempt={attempt.data}
      tryingAgain={start.isPending}
      onBack={() => void navigate({ to: '/practice' })}
      onTryAgain={() =>
        start.mutate(review.data.itemSetId, {
          onSuccess: (next) => void navigate({ to: '/practice/$attemptId', params: { attemptId: next.attemptId } }),
        })
      }
    />
  );
}
