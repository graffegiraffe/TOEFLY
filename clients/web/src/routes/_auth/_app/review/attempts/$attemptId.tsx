import { useQuery } from '@tanstack/react-query';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import { attemptQuery, reviewQuery, useStartAttempt } from '@/api/practice';
import { ReviewView } from '@/components/practice/review-view';
import { EmptyState } from '@/components/ui/empty-state';
import { KeyWords } from '@/components/vocabulary/key-words';
import { ReadingText } from '@/components/vocabulary/reading-text';
import { WordLookupArea } from '@/components/vocabulary/word-lookup';

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
  // US-15.01, US-15.02: words of the reviewed text can be selected and saved; key words of Reading and Listening.
  const stimulus = attempt.data.stimulus as { title?: string } | undefined;
  const textual = review.data.section === 'READING' || review.data.section === 'LISTENING';
  return (
    <WordLookupArea itemSetId={review.data.itemSetId} sourceLabel={stimulus?.title}>
      <div className="flex flex-col gap-6">
        {review.data.section === 'READING' && <ReadingText stimulus={attempt.data.stimulus} />}
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
        {textual && <KeyWords itemSetId={review.data.itemSetId} sourceLabel={stimulus?.title} />}
      </div>
    </WordLookupArea>
  );
}
