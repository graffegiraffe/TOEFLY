import { useQuery, useQueryClient } from '@tanstack/react-query';
import { createFileRoute, Navigate, notFound, useNavigate } from '@tanstack/react-router';
import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { AttemptStatus } from '@/api/generated/practice';
import { api } from '@/api/client';
import { weekQuery } from '@/api/planning';
import { attemptQuery, newKey, reviewQuery } from '@/api/practice';
import { TaskPlayer } from '@/components/practice/task-player';
import { EmptyState } from '@/components/ui/empty-state';

const search = z.object({ week: z.string().optional() });

/**
 * A task of the week in the player (docs/07-ux-ui.md §4.4 `/tasks/$weekTaskId`): continues the open attempt of the
 * task or starts one with its weekTaskId; the review of phase 3 follows the submission (docs/12, phase 4, decision 9).
 */
export const Route = createFileRoute('/_auth/_app/tasks/$weekTaskId')({
  validateSearch: search,
  beforeLoad: ({ context }) => {
    if (!context.session.user.roles.includes('STUDENT')) {
      throw notFound();
    }
  },
  component: WeekTaskPage,
});

function WeekTaskPage() {
  const { t } = useTranslation();
  const { weekTaskId } = Route.useParams();
  const { week: weekId } = Route.useSearch();
  const week = useQuery({ ...weekQuery(weekId ?? 'none'), enabled: Boolean(weekId) });
  const task = week.data?.sections.flatMap((block) => block.tasks).find((item) => item.taskId === weekTaskId);
  const ready = Boolean(task && task.kind === 'ITEM_SET' && task.itemSetId && task.status !== 'DONE');

  const attempt = useQuery({
    // Outside the 'practice' keys: the invalidation after a submission must not start a second attempt.
    queryKey: ['week-task-attempt', weekTaskId],
    enabled: ready,
    staleTime: Infinity,
    gcTime: 0, // a later visit asks again instead of showing a finished attempt
    queryFn: async () => {
      const open = await api.attempts.listAttempts({ weekTaskId, status: [AttemptStatus.InProgress], limit: 1 });
      const existing = open.items[0];
      if (existing) {
        return api.attempts.getAttempt({ attemptId: existing.attemptId });
      }
      return api.attempts.startAttempt({
        idempotencyKey: newKey(),
        startAttemptRequest: {
          itemSetId: task?.itemSetId ?? '',
          weekTaskId,
          timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        },
      });
    },
  });

  if (!weekId) {
    return <EmptyState title={t('weeks.taskNotFound')} />;
  }
  if (week.isPending || (ready && attempt.isPending)) {
    return <div className="h-96 animate-pulse rounded-xl bg-surface-sunken" aria-busy="true" />;
  }
  if (!task || !week.data) {
    return <EmptyState title={t('weeks.taskNotFound')} />;
  }
  if (task.kind !== 'ITEM_SET') {
    return <EmptyState title={t('weeks.notPlayable')} />;
  }
  if (task.status === 'DONE' && task.lastAttemptId) {
    return <Navigate to="/review/attempts/$attemptId" params={{ attemptId: task.lastAttemptId }} replace />;
  }
  if (attempt.isError || !attempt.data) {
    return <EmptyState title={t('practice.player.notFound')} />;
  }
  return <WeekTaskPlayer attempt={attempt.data} weekNo={week.data.weekNo} />;
}

function WeekTaskPlayer({ attempt, weekNo }: { attempt: Parameters<typeof TaskPlayer>[0]['attempt']; weekNo: number }) {
  const client = useQueryClient();
  const navigate = useNavigate();
  const seeded = useRef(false);
  useEffect(() => {
    if (!seeded.current) {
      seeded.current = true;
      client.setQueryData(attemptQuery(attempt.attemptId).queryKey, attempt);
      void client.invalidateQueries({ queryKey: ['planning'] });
    }
  }, [attempt, client]);
  return (
    <TaskPlayer
      attempt={attempt}
      onExit={() => void navigate({ to: '/weeks/$weekNumber', params: { weekNumber: String(weekNo) } })}
      onSubmitted={(review) => {
        client.setQueryData(reviewQuery(attempt.attemptId).queryKey, review);
        void client.invalidateQueries({ queryKey: ['practice'] });
        void client.invalidateQueries({ queryKey: ['planning'] });
        void navigate({
          to: '/review/attempts/$attemptId',
          params: { attemptId: attempt.attemptId },
          replace: true,
        });
      }}
    />
  );
}
