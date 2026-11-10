import { useQuery, useQueryClient } from '@tanstack/react-query';
import { createFileRoute, Navigate, notFound, useNavigate } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import { attemptQuery, reviewQuery } from '@/api/practice';
import { TaskPlayer } from '@/components/practice/task-player';
import { EmptyState } from '@/components/ui/empty-state';

/** Task player outside a week (docs/07-ux-ui.md §4.4 `/practice/$attemptId`). */
export const Route = createFileRoute('/_auth/_app/practice/$attemptId')({
  beforeLoad: ({ context }) => {
    if (!context.session.user.roles.includes('STUDENT')) {
      throw notFound();
    }
  },
  component: PracticeAttempt,
});

function PracticeAttempt() {
  const { t } = useTranslation();
  const { attemptId } = Route.useParams();
  const navigate = useNavigate();
  const client = useQueryClient();
  const attempt = useQuery(attemptQuery(attemptId));

  if (attempt.isPending) {
    return <div className="h-96 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />;
  }
  if (attempt.isError) {
    return <EmptyState title={t('practice.player.notFound')} />;
  }
  if (attempt.data.status !== 'IN_PROGRESS') {
    return attempt.data.status === 'SCORED' ? (
      <Navigate to="/review/attempts/$attemptId" params={{ attemptId }} replace />
    ) : (
      <Navigate to="/practice" replace />
    );
  }
  return (
    <TaskPlayer
      attempt={attempt.data}
      onExit={() => void navigate({ to: '/practice' })}
      onSubmitted={(review) => {
        client.setQueryData(reviewQuery(attemptId).queryKey, review);
        void client.invalidateQueries({ queryKey: ['practice', 'attempt', attemptId] });
        void client.invalidateQueries({ queryKey: ['practice', 'attempts'] });
        void client.invalidateQueries({ queryKey: ['practice', 'abilities'] });
        void navigate({ to: '/review/attempts/$attemptId', params: { attemptId }, replace: true });
      }}
    />
  );
}
