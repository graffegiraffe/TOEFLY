import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query';
import { ResponseError, type Week, type WeekSummary, WeekStatus } from './generated/planning';
import { api } from './client';
import { newKey, versionOf } from './practice';

/** Planning API (docs/05-api-contracts.md §6.7, §7.2): the plan, weeks, Today and the commands on tasks. */

export const planQuery = queryOptions({
  queryKey: ['planning', 'plan'],
  queryFn: async () => {
    try {
      return await api.studyPlans.getCurrentStudyPlan({});
    } catch (error) {
      if (error instanceof ResponseError && error.response.status === 404) {
        return null; // no plan yet: the onboarding event is still on its way
      }
      throw error;
    }
  },
});

export const weeksQuery = queryOptions({
  queryKey: ['planning', 'weeks'],
  queryFn: async () => (await api.studyPlans.listCurrentPlanWeeks()).items,
});

export const weekQuery = (weekId: string) =>
  queryOptions({
    queryKey: ['planning', 'week', weekId],
    queryFn: async () => {
      const response = await api.weeks.getWeekRaw({ weekId });
      const week = await response.value();
      return { ...week, version: versionOf(response.raw.headers.get('ETag'), week.version) };
    },
  });

export const todayQuery = queryOptions({
  queryKey: ['planning', 'today'],
  queryFn: () => api.today.getToday({}),
});

/** A week the learner can open: composed, published or done (AC-04.02.4). */
export function isComposed(week: WeekSummary): boolean {
  return week.status !== WeekStatus.Draft && week.status !== WeekStatus.AwaitingTutorApproval;
}

/** `/weeks/$weekNumber` finds the week id in the gallery (docs/12, phase 4, decision 9). */
export function weekIdOf(weeks: readonly WeekSummary[], weekNumber: number): string | undefined {
  const week = weeks.find((candidate) => candidate.weekNo === weekNumber);
  return week && isComposed(week) ? week.weekId : undefined;
}

function useInvalidatePlanning() {
  const client = useQueryClient();
  return () => client.invalidateQueries({ queryKey: ['planning'] });
}

export function useCompleteTask() {
  const invalidate = useInvalidatePlanning();
  return useMutation({
    mutationFn: (taskId: string) =>
      api.weekTasks.completeWeekTask({ taskId, idempotencyKey: newKey(), completeWeekTaskRequest: {} }),
    onSuccess: invalidate,
  });
}

export function useUncompleteTask() {
  const invalidate = useInvalidatePlanning();
  return useMutation({
    mutationFn: (taskId: string) => api.weekTasks.uncompleteWeekTask({ taskId }),
    onSuccess: invalidate,
  });
}

export function useSkipTask() {
  const invalidate = useInvalidatePlanning();
  return useMutation({
    mutationFn: (taskId: string) =>
      api.weekTasks.skipWeekTask({ taskId, idempotencyKey: newKey(), skipWeekTaskRequest: {} }),
    onSuccess: invalidate,
  });
}

export function useMoveTask(week: Week) {
  const invalidate = useInvalidatePlanning();
  return useMutation({
    mutationFn: ({ taskId, dayIndex }: { taskId: string; dayIndex: number }) =>
      api.weeks.patchWeekTask({
        weekId: week.weekId,
        taskId,
        ifMatch: `"${week.version}"`,
        weekTaskPatch: { dayIndex },
      }),
    onSuccess: invalidate,
  });
}

export function useFinishWeekEarly() {
  const invalidate = useInvalidatePlanning();
  return useMutation({
    mutationFn: (weekId: string) => api.weeks.finishWeekEarly({ weekId, idempotencyKey: newKey() }),
    onSuccess: invalidate,
  });
}
