import { expect, test, type Page } from '@playwright/test';
import { expectNoSeriousViolations } from './flows';

// The screen is exercised against a stubbed API, so the scenario needs only `pnpm dev`: the admin API of ai-gateway
// behind api-gateway is covered by the component tests of ai-gateway (AiGatewayComponentIT).
const API = 'http://api.toefly.localhost:8080';

const prompt = {
  key: 'tutor.hint',
  useCase: 'HINT',
  description: 'A hint without the answer',
  ownerService: 'tutor-agent-service',
  activeVersion: 2,
  canaryVersion: null,
  canaryPercent: 0,
};

const evalRun = (version: number, passed: boolean) => ({
  id: `00000000-0000-0000-0000-00000000000${version}`,
  promptKey: 'tutor.hint',
  promptVersion: version,
  suite: 'hint-quality',
  mode: 'FULL',
  passed,
  metrics: { accuracy: 0.92 },
  reportUrl: null,
  ranAt: '2026-11-02T10:00:00Z',
  receivedAt: '2026-11-02T10:00:05Z',
});

const version = (no: number, status: string, latestEval: unknown = null) => ({
  key: 'tutor.hint',
  version: no,
  useCase: 'HINT',
  description: `Hint prompt, revision ${no}`,
  system: 'system',
  user: 'user',
  params: {},
  maxInputTokens: 4000,
  untrusted: ['question'],
  guardPolicy: 'CHAT',
  evalSuite: 'hint-quality',
  status,
  active: status === 'ACTIVE',
  latestEval,
  createdBy: 'ada',
  createdAt: `2026-11-0${no}T10:00:00Z`,
});

const call = {
  id: 'call-1',
  callerService: 'tutor-agent-service',
  useCase: 'HINT',
  feature: 'hint',
  priority: 'INTERACTIVE',
  userId: null,
  promptKey: 'tutor.hint',
  promptVersion: 2,
  provider: 'GROQ',
  model: 'llama-3.3-70b-versatile',
  fallbackDepth: 0,
  degraded: false,
  cacheHit: false,
  requestMasked: 'Student [PERSON_1] asks about paragraph 2',
  responseMasked: 'Look at the second sentence again.',
  inputTokens: 812,
  outputTokens: 96,
  audioSeconds: 0,
  characters: 0,
  latencyMs: 640,
  shadowCostMicroUsd: 2350,
  guardVerdict: 'SAFE',
  outcome: 'SUCCESS',
  errorCode: null,
  traceId: 'trace-1',
  createdAt: '2026-11-10T09:30:00Z',
};

async function stubApi(page: Page, roles: string[]) {
  const versions = [
    version(3, 'DRAFT'),
    version(2, 'ACTIVE', evalRun(2, true)),
    version(1, 'ARCHIVED', evalRun(1, true)),
  ];
  const activations: string[] = [];
  await page.route(`${API}/**`, async (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();
    const json = (body: unknown, status = 200, contentType = 'application/json') =>
      route.fulfill({
        status,
        contentType,
        body: JSON.stringify(body),
        headers: {
          'access-control-allow-origin': route.request().headers()['origin'] ?? '*',
          'access-control-allow-credentials': 'true',
          'access-control-allow-headers': '*',
          'access-control-allow-methods': '*',
        },
      });
    if (method === 'OPTIONS') return json({});
    if (url.pathname === '/auth/session') {
      return json({ authenticated: true, user: { id: 'u1', roles, emailVerified: true }, csrfToken: 't' });
    }
    if (url.pathname === '/api/v1/me') {
      return json({
        id: 'u1',
        email: 'admin@toefly.test',
        displayName: 'Ada Admin',
        roles,
        onboardingStatus: 'COMPLETED',
        createdAt: '2026-11-01T10:00:00Z',
      });
    }
    if (url.pathname === '/api/v1/admin/ai/prompts') return json({ items: [prompt] });
    if (url.pathname === '/api/v1/admin/ai/prompts/tutor.hint/versions') return json({ items: versions });
    const activation = url.pathname.match(/^\/api\/v1\/admin\/ai\/prompts\/tutor\.hint\/versions\/(\d+)\/activation$/);
    if (activation && method === 'POST') {
      const no = Number(activation[1]);
      activations.push(`${no}`);
      const target = versions.find((v) => v.version === no)!;
      if (!target.latestEval) {
        return json(
          { type: 'about:blank', title: 'Conflict', status: 409, code: 'EVALS_NOT_PASSED', promptKey: 'tutor.hint' },
          409,
          'application/problem+json',
        );
      }
      for (const v of versions) {
        if (v.status === 'ACTIVE') Object.assign(v, { status: 'ARCHIVED', active: false });
      }
      Object.assign(target, { status: 'ACTIVE', active: true });
      return json(target);
    }
    if (url.pathname === '/api/v1/admin/ai/calls') {
      const outcome = url.searchParams.get('outcome');
      return json({ items: outcome && outcome !== 'SUCCESS' ? [] : [call], page: { hasMore: false, limit: 20 } });
    }
    if (url.pathname === '/api/v1/admin/ai/usage') {
      return json({
        from: url.searchParams.get('from'),
        to: url.searchParams.get('to'),
        rows: [
          {
            day: '2026-11-10',
            provider: 'GROQ',
            model: 'llama-3.3-70b-versatile',
            useCase: 'HINT',
            feature: null,
            calls: 120,
            cacheHits: 30,
            fallbacks: 4,
            inputTokens: 90000,
            outputTokens: 10000,
            audioSeconds: 0,
            characters: 0,
            shadowCostMicroUsd: 1250000,
          },
          {
            day: '2026-11-11',
            provider: 'AZURE_SPEECH',
            model: 'pronunciation-assessment',
            useCase: 'PRONUNCIATION',
            feature: null,
            calls: 10,
            cacheHits: 0,
            fallbacks: 0,
            inputTokens: 0,
            outputTokens: 0,
            audioSeconds: 300,
            characters: 0,
            shadowCostMicroUsd: 250000,
          },
        ],
      });
    }
    if (url.pathname === '/api/v1/admin/ai/providers') {
      return json({
        items: [
          {
            provider: 'GROQ',
            model: 'llama-3.3-70b-versatile',
            circuit: 'CLOSED',
            dimensions: [
              { dimension: 'RPD', capacity: 1000, remaining: 50 },
              { dimension: 'TPM', capacity: 6000, remaining: 5400 },
            ],
          },
          {
            provider: 'AZURE_SPEECH',
            model: 'pronunciation-assessment',
            circuit: 'OPEN',
            dimensions: [{ dimension: 'MONTHLY_AUDIO_SECONDS', capacity: 18000, remaining: 0 }],
          },
        ],
      });
    }
    return json({ code: 'NOT_FOUND' }, 404);
  });
  return activations;
}

test.describe('phase 5a AI administration', () => {
  test('an administrator rolls a prompt back, reads the call log, the usage and the providers', async ({ page }) => {
    const activations = await stubApi(page, ['ADMIN']);
    await page.goto('/admin/ai');

    await expect(page.getByRole('heading', { name: 'AI', level: 1 })).toBeVisible();
    await expect(page.getByRole('link', { name: 'AI', exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Content' })).toBeVisible();
    await page.getByRole('button', { name: 'Versions: tutor.hint' }).click();
    const active = page.getByRole('listitem', { name: 'Version 2' });
    await expect(active.getByText('Active now', { exact: true })).toBeVisible();
    await expect(active.getByText(/hint-quality · FULL/)).toBeVisible();
    await expectNoSeriousViolations(page);

    // A draft without a passed eval run cannot go live.
    const draft = page.getByRole('listitem', { name: 'Version 3' });
    await draft.getByRole('button', { name: 'Activate' }).click();
    await expect(draft.getByRole('alert')).toContainText('it has no passed eval run');

    // Rollback: the archived version 1 becomes active again.
    page.once('dialog', (dialog) => void dialog.accept());
    const archived = page.getByRole('listitem', { name: 'Version 1' });
    await archived.getByRole('button', { name: 'Roll back to this version' }).click();
    await expect(archived.getByText('Active now', { exact: true })).toBeVisible();
    await expect(active.getByText('Archived')).toBeVisible();
    expect(activations).toEqual(['3', '1']);

    await page.getByRole('tab', { name: 'Calls' }).click();
    await expect(page.getByText('tutor.hint@2')).toBeVisible();
    await expectNoSeriousViolations(page);
    await page.getByRole('button', { name: /Details of the call at/ }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('Student [PERSON_1] asks about paragraph 2')).toBeVisible();
    await expectNoSeriousViolations(page);
    await dialog.getByRole('button', { name: 'Close' }).click();
    await page.getByLabel('Outcome').selectOption('PROVIDER_ERROR');
    await expect(page.getByText('No calls match these filters.')).toBeVisible();

    await page.getByRole('tab', { name: 'Usage' }).click();
    const total = page.getByRole('row').filter({ has: page.getByRole('rowheader', { name: 'Total' }) });
    await expect(total.getByText('$1.50')).toBeVisible();
    await expectNoSeriousViolations(page);

    await page.getByRole('tab', { name: 'Providers' }).click();
    await expect(page.getByText('Circuit open')).toBeVisible();
    await expect(page.getByText('Exhausted')).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('the screen also passes accessibility checks in the dark theme', async ({ page }) => {
    await stubApi(page, ['ADMIN']);
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/admin/ai');
    await page.getByRole('button', { name: 'Versions: tutor.hint' }).click();
    await expect(
      page.getByRole('listitem', { name: 'Version 2' }).getByText('Active now', { exact: true }),
    ).toBeVisible();
    await expectNoSeriousViolations(page);
    await page.getByRole('tab', { name: 'Providers' }).click();
    await expect(page.getByText('Circuit open')).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('a learner does not see the AI administration', async ({ page }) => {
    await stubApi(page, ['STUDENT']);
    await page.goto('/admin/ai');
    await expect(page.getByRole('heading', { name: 'AI', level: 1 })).toBeHidden();
    await expect(page.getByRole('link', { name: 'AI', exact: true })).toBeHidden();
  });
});
