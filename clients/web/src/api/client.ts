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
  ReportsApi as ContentReportsApi,
  SourcesApi as ContentSourcesApi,
} from './generated/content';
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

export const api = {
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
