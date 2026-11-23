import { useQuery } from '@tanstack/react-query';
import { createFileRoute, Link, Navigate, useNavigate } from '@tanstack/react-router';
import { AlertTriangle, ArrowLeft, Hourglass, MessageCircle } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import {
  createSubmission,
  currentVersionOf,
  submissionQuery,
  useCreateVersion,
  useSubmissionEvents,
  writingItemSetQuery,
} from '@/api/assessment';
import { AttemptMode, SubmissionStatus, type Submission, type SubmissionVersion } from '@/api/generated/assessment';
import type { ItemSet } from '@/api/generated/content';
import { useTutor, useTutorScreen } from '@/components/tutor/use-tutor';
import { Button, buttonClass } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { EmptyState } from '@/components/ui/empty-state';
import { AnnotatedEssay } from '@/components/writing/annotated-essay';
import { CommentCard } from '@/components/writing/comment-card';
import { reviewComments } from '@/components/writing/comments';
import { modelAnswerOf } from '@/components/writing/content';
import { RubricScore, RubricScoreSkeleton } from '@/components/writing/rubric-score';
import { VersionDiff } from '@/components/writing/version-diff';
import { cn } from '@/lib/cn';

const search = z.object({ version: z.coerce.number().int().min(1).max(3).optional() });

/** After this long the waiting line changes (AC-11.03.6). */
const SLOW_MS = 60_000;

const CHECKING = new Set<string>([SubmissionStatus.Submitted, SubmissionStatus.Evaluating]);
const EVALUATED = new Set<string>([SubmissionStatus.Evaluated, SubmissionStatus.TutorReviewed]);

/** Writing review (docs/07-ux-ui.md §5.10 «Writing», US-11.03, US-11.05, US-11.06). */
export const Route = createFileRoute('/_auth/_app/review/submissions/$submissionId')({
  validateSearch: search,
  component: SubmissionReviewPage,
});

function SubmissionReviewPage() {
  const { t } = useTranslation();
  const { submissionId } = Route.useParams();
  const { version: requested } = Route.useSearch();
  const query = useQuery(submissionQuery(submissionId));
  const itemSet = useQuery({
    ...writingItemSetQuery(query.data?.submission.itemSetId ?? 'none'),
    enabled: Boolean(query.data),
  });

  if (query.isPending) {
    return <div className="h-96 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />;
  }
  if (query.isError) {
    return <EmptyState title={t('writing.notFound')} />;
  }
  const { submission } = query.data;
  const versions = [...submission.versions].sort((a, b) => a.versionNo - b.versionNo);
  const reviewable = versions.filter((version) => version.status !== SubmissionStatus.Draft);
  if (reviewable.length === 0) {
    return <Navigate to="/write/$submissionId" params={{ submissionId }} replace />;
  }
  const selected = reviewable.find((version) => version.versionNo === requested) ?? reviewable[reviewable.length - 1]!;
  return (
    <SubmissionReview
      submission={submission}
      versions={versions}
      reviewable={reviewable}
      selected={selected}
      itemSet={itemSet.data}
    />
  );
}

interface ReviewProps {
  submission: Submission;
  versions: SubmissionVersion[];
  reviewable: SubmissionVersion[];
  selected: SubmissionVersion;
  itemSet: ItemSet | undefined;
}

function SubmissionReview({ submission, versions, reviewable, selected, itemSet }: ReviewProps) {
  const { t } = useTranslation();
  const checking = CHECKING.has(selected.status);
  const deferred = selected.status === SubmissionStatus.EvaluationDeferred;
  useSubmissionEvents(submission.id, checking || deferred);
  const slow = useSlow(checking, selected.submittedAt ?? undefined);
  const previous = versions.find((version) => version.versionNo === selected.versionNo - 1);
  const title = itemSet?.topic
    ? `${t(`practice.taskType.${submission.taskType}`)}: ${itemSet.topic}`
    : t(`practice.taskType.${submission.taskType}`);
  const { openTutor } = useTutor();
  const tutorContext = {
    type: 'SUBMISSION_REVIEW' as const,
    ref: submission.id,
    label: t('tutor.context.essay', { version: t(`writing.version.${selected.versionNo}`) }),
  };
  useTutorScreen(tutorContext);

  return (
    <div className="flex flex-col gap-6">
      <Link
        to="/practice"
        className="inline-flex items-center gap-1 self-start text-caption font-semibold text-link hover:text-link-hover"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        {t('review.backToPractice')}
      </Link>
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-title-1 font-semibold tracking-tight text-ink">{title}</h1>
        {reviewable.length > 1 && (
          <nav aria-label={t('writing.review.versions')} className="flex rounded-lg bg-surface-sunken p-1">
            {reviewable.map((version) => (
              <Link
                key={version.versionNo}
                to="/review/submissions/$submissionId"
                params={{ submissionId: submission.id }}
                search={{ version: version.versionNo }}
                replace
                aria-current={version.versionNo === selected.versionNo ? 'page' : undefined}
                className={cn(
                  'rounded-md px-3 py-1.5 text-caption font-semibold focus-visible:outline-2 focus-visible:outline-focus-ring',
                  version.versionNo === selected.versionNo
                    ? 'bg-surface text-ink shadow-e1'
                    : 'text-ink-muted hover:text-ink',
                )}
              >
                {t(`writing.version.${version.versionNo}`)}
              </Link>
            ))}
          </nav>
        )}
      </header>

      {deferred && (
        <div role="status" className="flex items-start gap-3 rounded-xl bg-warning-soft px-4 py-3 text-body text-ink">
          <AlertTriangle className="mt-0.5 size-5 flex-none text-warning" aria-hidden="true" />
          <p>{t('writing.review.deferred')}</p>
        </div>
      )}

      {checking && <RubricScoreSkeleton slow={slow} />}
      {checking || deferred ? (
        <section aria-labelledby="essay-title" className="flex flex-col gap-3">
          <h2 id="essay-title" className="flex items-center gap-2 text-title-3 font-semibold text-ink">
            {checking && <Hourglass className="size-5 text-ink-muted" aria-hidden="true" />}
            {t('writing.review.essay')}
          </h2>
          <div
            className="whitespace-pre-wrap rounded-xl border border-border bg-surface-task p-5 text-body-lg leading-loose text-ink"
            data-testid="essay-text"
          >
            {selected.text}
          </div>
        </section>
      ) : (
        <EvaluatedVersion submission={submission} version={selected} previous={previous} />
      )}

      {previous && EVALUATED.has(selected.status) && <PreviousVersion version={previous} current={selected} />}

      <ModelAnswer itemSet={itemSet} />

      <div className="flex flex-wrap items-start gap-3">
        <Actions submission={submission} selected={selected} />
        {EVALUATED.has(selected.status) && (
          <Button variant="secondary" onClick={() => openTutor(tutorContext)}>
            <MessageCircle className="size-4" aria-hidden="true" />
            {t('review.askTutor')}
          </Button>
        )}
      </div>
    </div>
  );
}

/** True once the check has run for more than a minute since the submission (or since the screen opened). */
function useSlow(active: boolean, since: Date | undefined): boolean {
  const [opened] = useState(() => Date.now());
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [active]);
  const start = since ? since.getTime() : opened;
  return active && now - start > SLOW_MS;
}

function EvaluatedVersion({
  submission,
  version,
  previous,
}: {
  submission: Submission;
  version: SubmissionVersion;
  previous: SubmissionVersion | undefined;
}) {
  const { t } = useTranslation();
  const evaluation = version.tutorEvaluation ?? version.evaluation;
  if (!evaluation) {
    return <EmptyState title={t('writing.review.noEvaluation')} />;
  }
  const ai = version.evaluation ?? evaluation;
  const score = version.effectiveScore ?? evaluation.finalScore;
  const comments = reviewComments(version);
  const spelling = comments.filter((comment) => comment.category === 'SPELLING').length;
  return (
    <>
      <RubricScore
        score={score}
        taskType={submission.taskType}
        criteria={evaluation.criteria.length > 0 ? evaluation.criteria : ai.criteria}
        scoreDelta={previous ? version.scoreDelta : null}
        previousLabel={previous ? t(`writing.version.${previous.versionNo}`) : undefined}
        tutorScore={version.tutorEvaluation ? version.tutorEvaluation.finalScore : null}
      />
      {(ai.strengths.length > 0 || ai.priorities.length > 0) && (
        <div className="grid gap-4 md:grid-cols-2">
          <SummaryList title={t('writing.review.whatWorked')} items={ai.strengths} />
          <SummaryList title={t('writing.review.whatToFix')} items={ai.priorities} />
        </div>
      )}
      <AnnotatedEssay text={version.text} comments={comments} />
      {spelling > 0 && (
        <p className="text-caption text-ink-muted">{t('writing.review.spelling', { count: spelling })}</p>
      )}
    </>
  );
}

function SummaryList({ title, items }: { title: string; items: string[] }) {
  return (
    <Card className="flex flex-col gap-2 p-5">
      <h2 className="text-body font-semibold text-ink">{title}</h2>
      <ul className="flex list-disc flex-col gap-1 pl-5 text-body text-ink">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </Card>
  );
}

/** Attempt 2+: the diff with the previous version and the fate of its comments (AC-11.05.2). */
function PreviousVersion({ version, current }: { version: SubmissionVersion; current: SubmissionVersion }) {
  const { t } = useTranslation();
  const label = t(`writing.version.${version.versionNo}`);
  const comments = reviewComments(version).filter((comment) => comment.status !== 'OPEN');
  return (
    <section aria-labelledby="previous-title" className="flex flex-col gap-3">
      <h2 id="previous-title" className="text-title-3 font-semibold text-ink">
        {t('writing.review.compareWith', { version: label })}
      </h2>
      <details className="rounded-xl border border-border bg-surface p-4" open>
        <summary className="cursor-pointer text-body font-semibold text-ink">{t('writing.review.changes')}</summary>
        <div className="mt-3">
          <VersionDiff before={version.text} after={current.text} />
        </div>
      </details>
      {comments.length > 0 && (
        <div className="flex flex-col gap-3">
          <h3 className="text-body font-semibold text-ink">
            {t('writing.review.previousComments', { version: label })}
          </h3>
          <div className="grid gap-3 md:grid-cols-2">
            {comments.map((comment) => (
              <CommentCard key={comment.id} comment={comment} showStatus />
            ))}
          </div>
        </div>
      )}
    </section>
  );
}

function ModelAnswer({ itemSet }: { itemSet: ItemSet | undefined }) {
  const { t } = useTranslation();
  const answer = modelAnswerOf(itemSet);
  if (!answer) return null;
  return (
    <details className="rounded-xl border border-border bg-surface p-5" data-testid="model-answer">
      <summary className="cursor-pointer text-body font-semibold text-ink">{t('writing.review.modelAnswer')}</summary>
      <p className="mt-3 whitespace-pre-wrap text-body text-ink">{answer.text}</p>
      <p className="mt-2 text-caption text-ink-muted">
        {t('writing.review.modelSource', {
          source: t(`writing.modelSource.${answer.source ?? 'OTHER'}`, { defaultValue: answer.source }),
        })}
        {itemSet?.source?.attribution ? ` · ${itemSet.source.attribution}` : ''}
      </p>
    </details>
  );
}

/** Revision (AC-11.05.1, .3, .4): the next attempt, the open draft or a new prompt. */
function Actions({ submission, selected }: { submission: Submission; selected: SubmissionVersion }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const createVersion = useCreateVersion(submission.id);
  const [again, setAgain] = useState<'idle' | 'pending' | 'error'>('idle');
  const current = currentVersionOf(submission);
  const latestEvaluated = current && EVALUATED.has(current.status) && selected.versionNo === current.versionNo;
  const openDraft = current?.status === SubmissionStatus.Draft && submission.currentVersionNo > 1;

  if (submission.mode === AttemptMode.Exam) {
    return (
      <div className="flex flex-wrap gap-3">
        <Button
          variant="secondary"
          disabled={again === 'pending'}
          onClick={() => {
            setAgain('pending');
            createSubmission(submission.itemSetId).then(
              (next) => void navigate({ to: '/write/$submissionId', params: { submissionId: next.id } }),
              () => setAgain('error'),
            );
          }}
        >
          {t('writing.review.practiceAgain')}
        </Button>
        {again === 'error' && (
          <p role="alert" className="text-caption text-danger">
            {t('errors.generic')}
          </p>
        )}
      </div>
    );
  }
  if (openDraft) {
    return (
      <Link
        to="/write/$submissionId"
        params={{ submissionId: submission.id }}
        className={cn(buttonClass('primary'), 'self-start')}
      >
        {t('writing.review.continueAttempt', { version: t(`writing.version.${submission.currentVersionNo}`) })}
      </Link>
    );
  }
  if (!latestEvaluated) return null;
  if (submission.currentVersionNo >= 3) {
    return (
      <Link
        to="/practice"
        search={{ type: submission.taskType }}
        className={cn(buttonClass('secondary'), 'self-start')}
      >
        {t('writing.review.newPrompt')}
      </Link>
    );
  }
  const next = submission.currentVersionNo + 1;
  return (
    <div className="flex flex-wrap items-center gap-3">
      <Button
        disabled={createVersion.isPending}
        onClick={() =>
          createVersion.mutate(undefined, {
            onSuccess: () => void navigate({ to: '/write/$submissionId', params: { submissionId: submission.id } }),
          })
        }
      >
        {t('writing.review.startAttempt', { version: t(`writing.version.${next}`) })}
      </Button>
      {createVersion.isError && (
        <p role="alert" className="text-caption text-danger">
          {t('errors.generic')}
        </p>
      )}
    </div>
  );
}
