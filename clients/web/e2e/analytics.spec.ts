import { expect, test } from '@playwright/test';
import { stubAnalytics } from './analytics-stub';
import { expectNoSeriousViolations } from './flows';

// Home, Progress and the admin metrics run against a stubbed API (e2e/analytics-stub.ts), so only `pnpm dev` is
// needed; analytics-service is covered by its component tests.

test.describe('EP-05, EP-13, EP-16, EP-19 analytics screens of the learner', () => {
  test('US-05.02, US-19.01 Home shows the streak, the goal ring and the time of the week', async ({ page }) => {
    await stubAnalytics(page);
    await page.goto('/');

    const card = page.getByTestId('streak-card');
    await expect(card.getByTestId('streak-current')).toHaveText('3 days');
    await expect(card.getByTestId('today-done')).toBeVisible();
    await expect(card.getByRole('progressbar', { name: 'Daily goal' })).toHaveAttribute('aria-valuenow', '100');
    await expect(page.getByTestId('home-time')).toContainText('4 h 10 m');
    await expect(page.getByTestId('home-time').getByTestId('freshness')).toHaveText('Updated 2 minutes ago');
    await page.getByRole('link', { name: 'Open Progress' }).click();
    await expect(page).toHaveURL(/\/progress$/);
  });

  test('US-13.02, US-16.02, US-19.01 Progress: band over weeks, activity, streak calendar and ETS pace', async ({
    page,
  }) => {
    await stubAnalytics(page);
    await page.goto('/progress');

    await expect(page.getByRole('heading', { name: 'Progress', level: 1 })).toBeVisible();
    await expect(page.getByTestId('tile-time')).toContainText('4 h 10 m');
    await expect(page.getByTestId('tile-tasks')).toContainText('8 / 18');
    await expect(page.getByTestId('tile-exam')).toContainText('38 days');
    await expect(page.getByRole('list', { name: 'Last 30 days' }).getByRole('listitem')).toHaveCount(30);

    // AC-13.02.1: target line, switchable sections; AC-13.02.3: table alternative.
    const band = page.getByTestId('band-chart');
    await expect(band.getByTestId('band-target')).toBeAttached();
    await band.getByRole('button', { name: 'Listening' }).click();
    await expect(band.getByRole('button', { name: 'Listening' })).toHaveAttribute('aria-pressed', 'true');
    await band.getByRole('button', { name: 'Show as table' }).click();
    await expect(band.getByRole('row').nth(2)).toContainText('4.0–5.0');

    // AC-16.02.1: a bar for each of the seven days.
    const activity = page.getByTestId('activity-chart');
    await expect(activity.getByRole('list', { name: 'Weekly activity' }).getByRole('listitem')).toHaveCount(7);

    // AC-16.02.2: the slow type is highlighted.
    await expect(page.getByTestId('pace-table').getByText('45% slower')).toBeVisible();
    await expect(page.getByTestId('time-by-section')).toContainText('3 h 38 m');
    await expectNoSeriousViolations(page);
  });

  test('Progress also passes accessibility checks in the dark theme', async ({ page }) => {
    await stubAnalytics(page);
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/progress');
    await expect(page.getByTestId('pace-table')).toBeVisible();
    await expectNoSeriousViolations(page);
  });
});

test.describe('docs/01 §7 admin metrics', () => {
  test('an admin sees the North Star, funnel, retention, AI spend and rebuilds a read model', async ({ page }) => {
    const state = await stubAnalytics(page, ['ADMIN']);
    await page.goto('/admin/metrics');

    await expect(page.getByTestId('metric-sdr')).toContainText('61%');
    await expect(page.getByTestId('metric-week-completion')).toContainText('71%');
    await expect(page.getByTestId('metric-ai-per-learner')).toContainText('$0.0062');
    await expect(page.getByTestId('funnel')).toContainText('Activated in 48 h');
    await expect(page.getByTestId('retention')).toContainText('7 of 14');
    await expectNoSeriousViolations(page);

    const row = page.getByTestId('rebuild').getByRole('listitem').filter({ hasText: 'Activity and streak' });
    await row.getByRole('button', { name: 'Rebuild' }).click();
    await expect(row.getByRole('status')).toHaveText('Rebuilt from 448 events');
    expect(state.rebuilds).toEqual(['activity']);

    await page.emulateMedia({ colorScheme: 'dark' });
    await expectNoSeriousViolations(page);
  });
});
