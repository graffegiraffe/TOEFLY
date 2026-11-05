import { API_URL } from './config';

// BFF web session of api-gateway (docs/10-security-privacy.md §4.2): the browser holds only the HttpOnly cookie,
// the CSRF token lives in memory and goes with every changing request.

export interface WebSession {
  authenticated: boolean;
  user: { id: string; roles: string[]; emailVerified: boolean };
  csrfToken: string;
  expiresAt?: string;
}

let csrfToken: string | null = null;

export function currentCsrfToken(): string | null {
  return csrfToken;
}

export class UnauthenticatedError extends Error {
  constructor() {
    super('Not signed in');
  }
}

export async function fetchSession(): Promise<WebSession> {
  const response = await fetch(`${API_URL}/auth/session`, { credentials: 'include' });
  if (response.status === 401) {
    csrfToken = null;
    throw new UnauthenticatedError();
  }
  if (!response.ok) {
    throw new Error(`Session request failed: ${response.status}`);
  }
  const session = (await response.json()) as WebSession;
  csrfToken = session.csrfToken;
  return session;
}

/** Relative app path to return to after Keycloak; the gateway accepts nothing else. */
function returnParam(returnTo: string | undefined): string {
  return returnTo && returnTo.startsWith('/') && !returnTo.startsWith('//')
    ? `?returnTo=${encodeURIComponent(returnTo)}`
    : '';
}

export function loginUrl(returnTo?: string, options: { reauth?: boolean } = {}): string {
  const params = returnParam(returnTo);
  const reauth = options.reauth ? `${params ? '&' : '?'}reauth=true` : '';
  return `${API_URL}/auth/login${params}${reauth}`;
}

export function registerUrl(returnTo?: string): string {
  return `${API_URL}/auth/register${returnParam(returnTo)}`;
}

/** Ends the gateway session and returns the Keycloak logout page that ends the Keycloak session too. */
export async function logout(): Promise<string> {
  const response = await fetch(`${API_URL}/auth/logout`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'X-CSRF-Token': csrfToken ?? '' },
  });
  csrfToken = null;
  if (!response.ok) {
    return '/sign-in';
  }
  const body = (await response.json()) as { logoutUrl: string };
  return body.logoutUrl;
}
