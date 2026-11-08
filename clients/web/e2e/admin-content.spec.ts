import { expect, test, type Page } from '@playwright/test';
import { expectNoSeriousViolations } from './flows';

// The screen is exercised against a stubbed API, so the scenario needs only `pnpm dev`: the admin API
// behind the gateway is covered by the component tests of content-service and api-gateway.
const API = 'http://api.toefly.localhost:8080';

const source = { code: 'demo', title: 'Demo reading', attribution: 'Synthetic fixture', legacy: false };
const row = (id: string, status: string, over: Record<string, unknown> = {}) => ({
  id,
  version: 1,
  taskType: 'READ_DAILY_LIFE',
  examFormat: 'TOEFL_2026',
  cefr: 'B1',
  topic: `Campus library ${id}`,
  status,
  provenance: 'EXTRACTED',
  sourceCode: 'demo',
  itemCount: 2,
  openReports: 0,
  ...over,
});

async function stubApi(page: Page, roles: string[]) {
  const sets = [row('s1', 'APPROVED', { openReports: 1 }), row('s2', 'HIDDEN', { hiddenReason: 'REPORT' })];
  const patches: unknown[] = [];
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
    if (url.pathname === '/api/v1/sources') return json({ items: [source] });
    if (url.pathname === '/api/v1/admin/item-sets' && route.request().method() === 'GET') {
      const status = url.searchParams.get('status');
      return json({ items: sets.filter((s) => !status || s.status === status), page: { hasMore: false } });
    }
    if (url.pathname.startsWith('/api/v1/admin/item-sets/') && route.request().method() === 'PATCH') {
      patches.push(route.request().postDataJSON());
      const id = url.pathname.split('/').pop()!;
      const target = sets.find((s) => s.id === id)!;
      target.status = route.request().postDataJSON().status;
      return json(target);
    }
    if (url.pathname === '/api/v1/admin/item-reports') {
      return json({
        items: [
          {
            id: 'r1',
            itemSetId: 's1',
            reason: 'TYPO',
            comment: 'Typo in option B',
            status: 'OPEN',
            createdAt: '2026-11-06T10:00:00Z',
          },
        ],
      });
    }
    return json({ code: 'NOT_FOUND' }, 404);
  });
  return patches;
}

test.describe('US-28.01 content administration', () => {
  test('an administrator filters, hides and restores item sets and reads the reports', async ({ page }) => {
    const patches = await stubApi(page, ['ADMIN']);
    await page.goto('/admin/content');

    await expect(page.getByRole('heading', { name: 'Content', level: 1 })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Content' })).toBeVisible();
    await expect(page.getByText('Campus library s1')).toBeVisible();
    await expectNoSeriousViolations(page);

    await page.getByLabel('Status').selectOption('HIDDEN');
    await expect(page.getByText('Campus library s1')).toBeHidden();
    await page.getByRole('button', { name: 'Restore' }).click();
    await expect.poll(() => patches).toEqual([{ status: 'APPROVED' }]);

    await page.getByRole('tab', { name: 'Reports' }).click();
    await expect(page.getByText('Typo in option B')).toBeVisible();
    await expectNoSeriousViolations(page);

    await page.getByRole('tab', { name: 'Sources' }).click();
    await expect(page.getByText('Synthetic fixture')).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('the screen also passes accessibility checks in the dark theme', async ({ page }) => {
    await stubApi(page, ['ADMIN']);
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/admin/content');
    await expect(page.getByText('Campus library s1')).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('a learner does not see the administration', async ({ page }) => {
    await stubApi(page, ['STUDENT']);
    await page.goto('/admin/content');
    await expect(page.getByRole('heading', { name: 'Content', level: 1 })).toBeHidden();
    await expect(page.getByRole('link', { name: 'Content' })).toBeHidden();
  });
});
