import { expect, test, type Page } from '@playwright/test';
import { expectNoSeriousViolations } from './flows';

// «AI → Evals» against a stubbed admin API of ai-gateway (docs/06-ai-agent.md §7.5); the API itself is covered by
// the component tests of ai-gateway.
const API = 'http://api.toefly.localhost:8080';

const KEY = 'writing.grade.discussion';

const run = (id: string, version: number, ranAt: string, passed: boolean, metrics: Record<string, number>) => ({
  id,
  promptKey: KEY,
  promptVersion: version,
  suite: 'writing-discussion',
  mode: 'FULL',
  passed,
  metrics,
  reportUrl: null,
  ranAt,
  receivedAt: ranAt,
});

const older = run('00000000-0000-4000-8000-0000000000e1', 1, '2026-11-15T10:00:00Z', true, {
  mae: 0.45,
  withinHalf: 0.78,
});
const newer = run('00000000-0000-4000-8000-0000000000e2', 2, '2026-11-17T10:00:00Z', false, {
  mae: 0.62,
  withinHalf: 0.74,
  needsHumanReview: 0.1,
});
const thresholds = { mae: { max: 0.6 }, withinHalf: { min: 0.7 } };

async function stubApi(page: Page) {
  await page.route(`${API}/**`, async (route) => {
    const url = new URL(route.request().url());
    const json = (body: unknown, status = 200) =>
      route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify(body),
        headers: {
          'access-control-allow-origin': route.request().headers()['origin'] ?? '*',
          'access-control-allow-credentials': 'true',
          'access-control-allow-headers': '*',
          'access-control-allow-methods': '*',
        },
      });
    if (route.request().method() === 'OPTIONS') return json({});
    if (url.pathname === '/auth/session') {
      return json({ authenticated: true, user: { id: 'u1', roles: ['ADMIN'], emailVerified: true }, csrfToken: 't' });
    }
    if (url.pathname === '/api/v1/me') {
      return json({
        id: 'u1',
        email: 'admin@toefly.test',
        displayName: 'Ada Admin',
        roles: ['ADMIN'],
        onboardingStatus: 'COMPLETED',
        createdAt: '2026-11-01T10:00:00Z',
      });
    }
    if (url.pathname === '/api/v1/admin/ai/prompts') {
      return json({
        items: [
          {
            key: KEY,
            useCase: 'WRITING_GRADING',
            description: 'WritingGrader, academic discussion',
            ownerService: 'assessment-service',
            activeVersion: 1,
            canaryVersion: null,
            canaryPercent: 0,
          },
        ],
      });
    }
    if (url.pathname === '/api/v1/admin/ai/eval-runs') return json({ items: [newer, older] });
    if (url.pathname === `/api/v1/admin/ai/eval-runs/${newer.id}`) {
      return json({
        ...newer,
        thresholds,
        cases: [
          { caseId: 'disc-001', split: 'test', gold: 4, predicted: 3, passes: [3, 3] },
          { caseId: 'disc-002', split: 'test', gold: 3, predicted: 3.5, passes: [3.5, 3.5] },
          { caseId: 'disc-003', split: 'dev', gold: 5, predicted: 3.5, passes: [3, 4] },
        ],
      });
    }
    if (url.pathname === `/api/v1/admin/ai/eval-runs/${older.id}`) {
      return json({
        ...older,
        thresholds,
        cases: [
          { caseId: 'disc-001', split: 'test', gold: 4, predicted: 4, passes: [4, 4] },
          { caseId: 'disc-002', split: 'test', gold: 3, predicted: 3, passes: [3, 3] },
          { caseId: 'disc-003', split: 'dev', gold: 5, predicted: 4, passes: [4, 4] },
        ],
      });
    }
    return json({ code: 'NOT_FOUND' }, 404);
  });
}

test.describe('phase 5b AI → Evals', () => {
  test('an administrator reads a run against its thresholds and filters the regressions', async ({ page }) => {
    await stubApi(page);
    await page.goto('/admin/ai');
    await page.getByRole('tab', { name: 'Evals' }).click();
    await expect(page.getByRole('cell', { name: KEY }).first()).toBeVisible();
    await expect(page.getByText('Failed')).toBeVisible();
    await expectNoSeriousViolations(page);

    await page.getByRole('button', { name: `Open the run of ${KEY} v2` }).click();
    await expect(page.getByRole('heading', { name: `${KEY} v2 · writing-discussion` })).toBeVisible();
    await expect(page.getByText(/Δ against the previous run: v1/)).toBeVisible();

    const metrics = page.getByRole('table', { name: 'Metrics' });
    const mae = metrics.getByRole('row').filter({ has: page.getByRole('rowheader', { name: 'mae' }) });
    await expect(mae.getByText('≤ 0.6')).toBeVisible();
    await expect(mae.getByText('Fail')).toBeVisible();
    await expect(mae.getByText('+0.17')).toBeVisible();
    const within = metrics.getByRole('row').filter({ has: page.getByRole('rowheader', { name: 'withinHalf' }) });
    await expect(within.getByText('Pass')).toBeVisible();
    await expect(within.getByText('−0.04')).toBeVisible();

    const cases = page.getByTestId('eval-case');
    await expect(cases).toHaveCount(3);
    await page.getByLabel(/Regressions only/).check();
    await expect(cases).toHaveCount(2);
    await expect(cases.filter({ hasText: 'disc-001' })).toBeVisible();
    await expect(cases.filter({ hasText: 'disc-003' })).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('the Evals section passes accessibility checks in the dark theme', async ({ page }) => {
    await stubApi(page);
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/admin/ai');
    await page.getByRole('tab', { name: 'Evals' }).click();
    await page.getByRole('button', { name: `Open the run of ${KEY} v2` }).click();
    await expect(page.getByTestId('eval-case')).toHaveCount(3);
    await expectNoSeriousViolations(page);
  });
});
