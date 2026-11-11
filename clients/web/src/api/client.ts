import {
  AccountApi,
  Configuration,
  ConsentsApi,
  OnboardingApi,
  PreferencesApi,
  PrivacyApi,
  ProfileApi,
  type Middleware,
} from './generated';
import {
  AdminApi as ContentAdminApi,
  AdminUnitsApi as ContentAdminUnitsApi,
  Configuration as ContentConfiguration,
  ItemSetsApi as ContentItemSetsApi,
  ReportsApi as ContentReportsApi,
  SourcesApi as ContentSourcesApi,
} from './generated/content';
import {
  AbilitiesApi as PracticeAbilitiesApi,
  AttemptsApi as PracticeAttemptsApi,
  Configuration as PracticeConfiguration,
} from './generated/practice';
import {
  Configuration as PlanningConfiguration,
  StudyPlansApi as PlanningStudyPlansApi,
  TodayApi as PlanningTodayApi,
  WeekTasksApi as PlanningWeekTasksApi,
  WeeksApi as PlanningWeeksApi,
} from './generated/planning';
import { API_URL } from './config';
import { currentCsrfToken } from './session';

const SAFE = new Set(['GET', 'HEAD', 'OPTIONS']);

/** Sends the session CSRF token with changing requests (docs/10-security-privacy.md §4.2). */
export const csrfMiddleware: Middleware = {
  pre: async ({ url, init }) => {
    const method = (init.method ?? 'GET').toUpperCase();
    const token = currentCsrfToken();
    if (SAFE.has(method) || !token) {
      return { url, init };
    }
    const headers = new Headers(init.headers);
    headers.set('X-CSRF-Token', token);
    headers.set('X-Client-Platform', 'web');
    return { url, init: { ...init, headers } };
  },
};

const configuration = new Configuration({
  basePath: API_URL,
  credentials: 'include',
  middleware: [csrfMiddleware],
});

const contentConfiguration = new ContentConfiguration({
  basePath: API_URL,
  credentials: 'include',
  middleware: [csrfMiddleware],
});

const practiceConfiguration = new PracticeConfiguration({
  basePath: API_URL,
  credentials: 'include',
  middleware: [csrfMiddleware],
});

// planning-service.yaml keeps its paths relative to the server /api/v1 (docs/05 §7.2).
const planningConfiguration = new PlanningConfiguration({
  basePath: `${API_URL}/api/v1`,
  credentials: 'include',
  middleware: [csrfMiddleware],
});

export const api = {
  studyPlans: new PlanningStudyPlansApi(planningConfiguration),
  weeks: new PlanningWeeksApi(planningConfiguration),
  today: new PlanningTodayApi(planningConfiguration),
  weekTasks: new PlanningWeekTasksApi(planningConfiguration),
  attempts: new PracticeAttemptsApi(practiceConfiguration),
  abilities: new PracticeAbilitiesApi(practiceConfiguration),
  contentItemSets: new ContentItemSetsApi(contentConfiguration),
  contentAdmin: new ContentAdminApi(contentConfiguration),
  contentAdminUnits: new ContentAdminUnitsApi(contentConfiguration),
  contentReports: new ContentReportsApi(contentConfiguration),
  contentSources: new ContentSourcesApi(contentConfiguration),
  profile: new ProfileApi(configuration),
  onboarding: new OnboardingApi(configuration),
  preferences: new PreferencesApi(configuration),
  consents: new ConsentsApi(configuration),
  privacy: new PrivacyApi(configuration),
  account: new AccountApi(configuration),
};
