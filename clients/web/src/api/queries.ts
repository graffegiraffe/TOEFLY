import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ConsentRequestSourceEnum,
  type ConsentType,
  type OnboardingRequest,
  type Preferences,
  type StudyGoalRequest,
} from './generated';
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

/** A resource with the ETag that a change must send back as If-Match (docs/05-api-contracts.md §2.7). */
export interface Versioned<T> {
  data: T;
  etag: string;
}

async function versioned<T>(raw: Promise<{ raw: Response; value: () => Promise<T> }>): Promise<Versioned<T>> {
  const response = await raw;
  return { data: await response.value(), etag: response.raw.headers.get('ETag') ?? '' };
}

export const versionedProfileQuery = queryOptions({
  queryKey: ['me', 'versioned'],
  queryFn: () => versioned(api.profile.getMeRaw()),
});

export const preferencesQuery = queryOptions({
  queryKey: ['me', 'preferences'],
  queryFn: () => versioned(api.preferences.getPreferencesRaw()),
});

export const versionedStudyGoalQuery = queryOptions({
  queryKey: ['me', 'study-goal', 'versioned'],
  queryFn: () => versioned(api.onboarding.getStudyGoalRaw()),
});

export function useRenameProfile() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ displayName, etag }: { displayName: string; etag: string }) =>
      api.profile.updateMe({ ifMatch: etag, updateProfileRequest: { displayName } }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['me'] }),
  });
}

export function useUpdatePreferences() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ preferences, etag }: { preferences: Preferences; etag: string }) =>
      api.preferences.updatePreferences({ ifMatch: etag, preferences }),
    // Goal, profile and preferences share one version, so every versioned query is stale now.
    onSuccess: () => client.invalidateQueries({ queryKey: ['me'] }),
  });
}

export function useChangeStudyGoal() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ request, etag }: { request: StudyGoalRequest; etag: string }) =>
      api.onboarding.changeStudyGoal({ ifMatch: etag, studyGoalRequest: request }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['me'] }),
  });
}

/** Sign out of all devices (AC-01.05.4). */
export function useSignOutEverywhere() {
  return useMutation({ mutationFn: () => api.account.signOutEverywhere() });
}

export function useBecomeTutor() {
  return useMutation({
    mutationFn: () =>
      api.account.becomeTutor({ idempotencyKey: crypto.randomUUID(), becomeTutorRequest: { acceptTutorTerms: true } }),
  });
}
