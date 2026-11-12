import { expect, test } from '@playwright/test';
import { expectNoSeriousViolations } from './flows';
import { GRAMMAR_TASK, READING_TASK, stubPlanning, WEEK_ID } from './planning-stub';

test.describe('US-05 Today and Home', () => {
  test('US-05.02 Home shows Today instead of the diagnostic once a plan exists', async ({ page }) => {
    await stubPlanning(page);
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Today' })).toBeVisible();
    await expect(page.getByText('Take your 25-minute diagnostic')).toHaveCount(0);
    await expectNoSeriousViolations(page);
  });

  test('US-05.01 Today lists the tasks of the day and starts the first open one', async ({ page }) => {
    await stubPlanning(page);
    await page.goto('/today');
    await expect(page.getByRole('heading', { level: 1, name: 'Today' })).toBeVisible();
    await expect(page.getByText(/Week 1, day 1/)).toBeVisible();
    const list = page.getByTestId('today-list');
    await expect(list.getByTestId('task-row')).toHaveCount(3);
    await expect(list.getByText('Read in Daily Life: Library hours')).toBeVisible();
    await expectNoSeriousViolations(page);
    await page.getByRole('link', { name: 'Start', exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`/tasks/${READING_TASK}\\?week=${WEEK_ID}`));
  });

  test('US-04.04 an overdue task comes first on Today', async ({ page }) => {
    await stubPlanning(page, { overdue: true });
    await page.goto('/today');
    const overdue = page.getByRole('region', { name: 'Overdue' });
    await expect(overdue.getByText('Choose a Response: Campus life')).toBeVisible();
    await expect(overdue.getByText('1 day overdue')).toBeVisible();
    await expectNoSeriousViolations(page);
  });
});

test.describe('US-04 Weeks', () => {
  test('US-04.09 the gallery shows the current week, the upcoming ones and the mock exam week', async ({ page }) => {
    await stubPlanning(page);
    await page.goto('/weeks');
    await expect(page.getByRole('heading', { level: 1, name: 'Weeks' })).toBeVisible();
    await expect(page.getByText(/4-week plan · exam on/)).toBeVisible();
    const cards = page.getByTestId('week-card');
    await expect(cards).toHaveCount(4);
    await expect(cards.first()).toContainText('Current');
    await expect(cards.nth(1)).toContainText('Will be built from your progress');
    await expect(cards.nth(3)).toContainText('Mock exam');
    await expectNoSeriousViolations(page);
    await cards.first().click();
    await expect(page).toHaveURL(/\/weeks\/1$/);
  });

  test('US-04.02 the week page: cover, dates, progress, table and six sections', async ({ page }) => {
    await stubPlanning(page);
    await page.goto('/weeks/1');
    await expect(page.getByRole('heading', { level: 1, name: 'Week 1' })).toBeVisible();
    await expect(page.getByTestId('week-progress')).toHaveText('0%');
    await expect(page.getByRole('table')).toBeVisible();
    await expect(page.getByRole('columnheader', { name: 'Resources' })).toBeVisible();
    await expect(page.getByTestId('section-cards').getByRole('link')).toHaveCount(6);
    await expect(page.getByTestId('today-strip')).toContainText('Today: Read in Daily Life: Library hours');
    await expectNoSeriousViolations(page);
  });

  test('US-04.03 a resource is marked done from the table and the progress grows', async ({ page }) => {
    const stub = await stubPlanning(page);
    await page.goto('/weeks/1');
    const box = page.getByRole('checkbox', { name: 'Mark Reading section overview as done' });
    await box.click();
    await expect(box).toBeChecked();
    await expect(page.getByTestId('week-progress')).toHaveText('17%');
    expect(stub.completions).toHaveLength(1);
  });

  test('US-04.03 a grammar unit is marked done on its section page', async ({ page }) => {
    const stub = await stubPlanning(page);
    await page.goto('/weeks/1/grammar');
    await expect(page.getByRole('heading', { level: 1, name: 'Grammar · Week 1' })).toBeVisible();
    await expect(page.getByText('0 of 1 done')).toBeVisible();
    await expectNoSeriousViolations(page);
    await page.getByRole('button', { name: 'Mark as done' }).click();
    await expect(page.getByText('1 of 1 done')).toBeVisible();
    expect(stub.completions).toEqual([GRAMMAR_TASK]);
  });

  test('US-04.05 a rebalanced week names the tasks that left it', async ({ page }) => {
    await stubPlanning(page, { rebalanced: true });
    await page.goto('/weeks/1');
    await expect(page.getByText('We rebuilt the rest of this week so you can catch up.')).toBeVisible();
    await page.getByText('See what changed').click();
    await expect(page.getByRole('status').getByText('Complete the Words: Ocean currents')).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('US-04.03 a task of the week opens in the player and its submission counts for the week', async ({ page }) => {
    const stub = await stubPlanning(page);
    await page.goto('/weeks/1');
    await page.getByTestId('today-strip').getByRole('link', { name: 'Start' }).click();
    await expect(page.getByRole('heading', { name: 'Library hours' })).toBeVisible();
    await page.getByRole('radio', { name: /At 9 pm/ }).click();
    await page.getByRole('button', { name: 'Next' }).click();
    await page.getByRole('radio', { name: /Opening hours/ }).click();
    await page.getByRole('button', { name: 'Check answers' }).click();
    await expect(page).toHaveURL(/\/review\/attempts\//);
    await expect(page.getByRole('heading', { level: 1, name: '2 of 2' })).toBeVisible();
    expect(stub.practice.submissions).toHaveLength(1);
    await page.goto('/weeks/1');
    await expect(page.getByTestId('week-progress')).toHaveText('17%');
  });

  test('screens pass axe in the dark theme', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await stubPlanning(page, { rebalanced: true });
    for (const path of ['/today', '/weeks', '/weeks/1', '/weeks/1/reading']) {
      await page.goto(path);
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
      await expectNoSeriousViolations(page);
    }
  });
});
