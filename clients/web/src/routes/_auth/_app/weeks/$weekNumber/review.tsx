import { useQuery } from '@tanstack/react-query';
import { createFileRoute, Link, notFound } from '@tanstack/react-router';
import { ChevronLeft, NotebookPen } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { Week } from '@/api/generated/planning';
import { isComposed, weeksQuery } from '@/api/planning';
import { weeklyReviewQuery } from '@/api/tutor';
import { WeeklyReviewView } from '@/components/review/weekly-review-view';
import { useTutor, useTutorScreen } from '@/components/tutor/use-tutor';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Cover } from '@/components/weeks/cover';
import { WeekLoader } from '@/components/weeks/week-loader';
import { WeekTabs } from '@/components/weeks/week-tabs';
import { weekCoverId } from '@/lib/covers';
import { formatRange } from '@/lib/weeks';

/** Weekly review page (docs/07-ux-ui.md §5.11 `/weeks/$weekNumber/review`, US-18.01, US-18.03). */
export const Route = createFileRoute('/_auth/_app/weeks/$weekNumber/review')({
  beforeLoad: ({ context, params }) => {
    if (!context.session.user.roles.includes('STUDENT') || !/^\d+$/.test(params.weekNumber)) {
      throw notFound();
    }
  },
  component: ReviewPage,
});

function ReviewPage() {
  const { weekNumber } = Route.useParams();
  return <WeekLoader weekNumber={Number(weekNumber)}>{(week) => <ReviewOfWeek week={week} />}</WeekLoader>;
}

function ReviewOfWeek({ week }: { week: Week }) {
  const { t, i18n } = useTranslation();
  const review = useQuery(weeklyReviewQuery(week.weekId));
  const weeks = useQuery(weeksQuery);
  const { openTutor } = useTutor();
  const nextWeek = weeks.data?.find((candidate) => candidate.weekNo === week.weekNo + 1);
  const label = t('tutor.context.weekReview', { number: week.weekNo });
  const tutorContext = review.data
    ? { type: 'WEEKLY_REVIEW' as const, ref: review.data.reviewId, label }
    : { type: 'WEEK' as const, ref: week.weekId, label: t('tutor.context.week', { number: week.weekNo }) };
  useTutorScreen(tutorContext);

  return (
    <div className="flex flex-col gap-5">
      <Link
        to="/weeks/$weekNumber"
        params={{ weekNumber: String(week.weekNo) }}
        className="inline-flex items-center gap-1 self-start text-body text-ink-muted hover:text-ink"
      >
        <ChevronLeft className="size-4" aria-hidden="true" />
        {t('weeklyReview.backToWeek', { number: week.weekNo })}
      </Link>
      <Cover id={weekCoverId(week.weekNo, Boolean(week.mockExam))} className="h-[120px] rounded-2xl" />
      <div className="flex flex-col gap-1">
        <h1 className="text-title-1 font-bold tracking-tight text-ink">
          {t('weeklyReview.title', { number: week.weekNo })}
        </h1>
        <p className="text-caption text-ink-muted">
          {t('weeklyReview.byline', { dates: formatRange(week.startDate, week.endDate, i18n.language) })}
        </p>
      </div>
      {week.status === 'COMPLETED' && <WeekTabs weekNo={week.weekNo} current="review" />}
      {review.isPending ? (
        <div className="flex max-w-[760px] flex-col gap-4" aria-busy="true">
          <span className="sr-only">{t('tutor.loading')}</span>
          <div className="h-28 rounded-xl bg-surface-sunken" />
          <div className="h-40 rounded-xl bg-surface-sunken" />
        </div>
      ) : review.isError ? (
        <Card className="flex max-w-[760px] flex-col items-start gap-3">
          <p className="text-body text-ink">{t('weeklyReview.loadError')}</p>
          <Button variant="secondary" onClick={() => void review.refetch()}>
            {t('weeklyReview.tryAgain')}
          </Button>
        </Card>
      ) : review.data ? (
        <WeeklyReviewView
          review={review.data}
          nextWeekReady={Boolean(nextWeek && isComposed(nextWeek))}
          onAskTutor={() => openTutor(tutorContext)}
        />
      ) : (
        <Card className="flex max-w-[760px] items-start gap-3" data-testid="review-preparing">
          <span className="grid size-10 flex-none place-items-center rounded-xl bg-accent-soft text-accent-on-soft">
            <NotebookPen className="size-5" aria-hidden="true" />
          </span>
          <div>
            <h2 className="text-title-3 font-semibold text-ink">{t('weeklyReview.preparing')}</h2>
            <p className="text-body text-ink-muted">{t('weeklyReview.preparingText')}</p>
          </div>
        </Card>
      )}
    </div>
  );
}
