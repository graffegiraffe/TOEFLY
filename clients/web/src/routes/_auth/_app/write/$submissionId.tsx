import { useQuery, useQueryClient } from '@tanstack/react-query';
import { createFileRoute, Navigate, notFound, useNavigate } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import { currentVersionOf, submissionQuery, writingItemSetQuery } from '@/api/assessment';
import { SubmissionStatus } from '@/api/generated/assessment';
import { EmptyState } from '@/components/ui/empty-state';
import { WritingEditor } from '@/components/writing/writing-editor';

/** The Writing editor for the open version of a submission (docs/07-ux-ui.md §5.9 items 9–10, §6.3). */
export const Route = createFileRoute('/_auth/_app/write/$submissionId')({
  beforeLoad: ({ context }) => {
    if (!context.session.user.roles.includes('STUDENT')) {
      throw notFound();
    }
  },
  component: WritePage,
});

function WritePage() {
  const { t } = useTranslation();
  const { submissionId } = Route.useParams();
  const navigate = useNavigate();
  const client = useQueryClient();
  const query = useQuery({ ...submissionQuery(submissionId), staleTime: 0 });
  const itemSet = useQuery({
    ...writingItemSetQuery(query.data?.submission.itemSetId ?? 'none'),
    enabled: Boolean(query.data),
  });

  if (query.isPending || (query.data && itemSet.isPending)) {
    return <div className="h-96 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />;
  }
  if (query.isError) {
    return <EmptyState title={t('writing.notFound')} />;
  }
  const { submission, version } = query.data;
  const draft = currentVersionOf(submission);
  if (!draft || draft.status !== SubmissionStatus.Draft) {
    return <Navigate to="/review/submissions/$submissionId" params={{ submissionId }} replace />;
  }
  return (
    <WritingEditor
      key={`${submissionId}-${draft.versionNo}`}
      submission={submission}
      version={version}
      draft={draft}
      itemSet={itemSet.data}
      onExit={() => void navigate({ to: submission.weekTaskId ? '/weeks' : '/practice' })}
      onSubmitted={(next) => {
        client.setQueryData(submissionQuery(submissionId).queryKey, { submission: next, version: next.version });
        void navigate({ to: '/review/submissions/$submissionId', params: { submissionId }, replace: true });
      }}
    />
  );
}
