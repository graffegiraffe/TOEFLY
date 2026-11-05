import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query';
import { ConsentRequestSourceEnum, type ConsentType, type OnboardingRequest } from './generated';
import { api } from './client';
import { fetchSession } from './session';

export const sessionQuery = queryOptions({
  queryKey: ['session'],
  queryFn: fetchSession,
  retry: false,
  staleTime: 60_000,
});

/** The first call creates the profile (AC-01.01.5). */
export const profileQuery = queryOptions({
  queryKey: ['me'],
  queryFn: () => api.profile.getMe(),
  staleTime: 30_000,
});

export const onboardingQuery = queryOptions({
  queryKey: ['me', 'onboarding'],
  queryFn: () => api.onboarding.getOnboarding(),
});

export const studyGoalQuery = queryOptions({
  queryKey: ['me', 'study-goal'],
  queryFn: () => api.onboarding.getStudyGoal(),
  retry: false,
});

export const consentsQuery = queryOptions({
  queryKey: ['me', 'consents'],
  queryFn: () => api.consents.listConsents(),
});

export function useSaveOnboarding() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (request: OnboardingRequest) => api.onboarding.saveOnboarding({ onboardingRequest: request }),
    onSuccess: async (state) => {
      client.setQueryData(onboardingQuery.queryKey, state);
      if (state.status === 'COMPLETED') {
        // Route guards read the cached profile, so it must say COMPLETED before navigating home.
        await client.fetchQuery({ ...profileQuery, staleTime: 0 });
        await client.invalidateQueries({ queryKey: studyGoalQuery.queryKey });
      }
    },
  });
}

export function useRecordConsent() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ consentType, granted }: { consentType: ConsentType; granted: boolean }) =>
      api.consents.recordConsent({ consentType, consentRequest: { granted, source: ConsentRequestSourceEnum.Web } }),
    onSuccess: () => client.invalidateQueries({ queryKey: consentsQuery.queryKey }),
  });
}

export function useRequestExport() {
  return useMutation({
    mutationFn: () => api.privacy.requestDataExport({ idempotencyKey: crypto.randomUUID() }),
  });
}

export function useRequestDeletion() {
  return useMutation({
    mutationFn: (confirmation: string) =>
      api.privacy.requestAccountDeletion({
        idempotencyKey: crypto.randomUUID(),
        deletionRequest: { confirmation },
      }),
  });
}
