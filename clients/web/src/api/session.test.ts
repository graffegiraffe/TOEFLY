import { csrfMiddleware } from './client';
import { currentCsrfToken, fetchSession, loginUrl, logout, registerUrl, UnauthenticatedError } from './session';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('web session', () => {
  afterEach(() => vi.restoreAllMocks());

  it('ADR-0023 sign-in returns only to relative app paths', () => {
    expect(loginUrl('/onboarding/goal')).toBe(
      'http://api.toefly.localhost:8080/auth/login?returnTo=%2Fonboarding%2Fgoal',
    );
    expect(loginUrl('//evil.example')).toBe('http://api.toefly.localhost:8080/auth/login');
    expect(registerUrl('https://evil.example')).toBe('http://api.toefly.localhost:8080/auth/register');
  });

  it('AC-31.04.1 re-authentication asks Keycloak for the password again', () => {
    expect(loginUrl('/settings/privacy', { reauth: true })).toBe(
      'http://api.toefly.localhost:8080/auth/login?returnTo=%2Fsettings%2Fprivacy&reauth=true',
    );
    expect(loginUrl(undefined, { reauth: true })).toBe('http://api.toefly.localhost:8080/auth/login?reauth=true');
  });

  it('AC-01.05.1 keeps the CSRF token in memory and sends it with changing requests only', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(200, { authenticated: true, user: { id: 'u', roles: [], emailVerified: true }, csrfToken: 'c1' }),
    );

    await fetchSession();

    expect(currentCsrfToken()).toBe('c1');
    const put = await csrfMiddleware.pre!({ fetch, url: '/x', init: { method: 'PUT' } });
    expect(new Headers(put!.init.headers).get('X-CSRF-Token')).toBe('c1');
    const get = await csrfMiddleware.pre!({ fetch, url: '/x', init: { method: 'GET' } });
    expect(new Headers(get!.init.headers).get('X-CSRF-Token')).toBeNull();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it('turns 401 into UnauthenticatedError and forgets the token', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(401, {}));

    await expect(fetchSession()).rejects.toBeInstanceOf(UnauthenticatedError);
    expect(currentCsrfToken()).toBeNull();
  });

  it('AC-01.05.3 sign-out returns the Keycloak logout page', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse(200, { logoutUrl: 'https://kc/logout' }));

    await expect(logout()).resolves.toBe('https://kc/logout');
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('POST');
  });
});
