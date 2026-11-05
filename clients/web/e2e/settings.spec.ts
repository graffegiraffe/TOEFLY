import { expect, test } from '@playwright/test';
import { completeOnboarding, expectNoSeriousViolations, signUp } from './flows';

test.describe('US-02.03 US-02.05 US-32.02 AC-01.05.4 settings', () => {
  test('a learner changes the time zone, the goal, the explanation language and the theme', async ({ page }) => {
    await signUp(page, `e2e-settings-${Date.now()}@toefly.test`);
    await completeOnboarding(page);

    await page.getByRole('link', { name: 'Settings' }).click();
    await expect(page).toHaveURL(/\/settings\/profile$/);
    await expect(page.getByRole('navigation', { name: 'Settings sections' })).toBeVisible();
    await expectNoSeriousViolations(page);

    // AC-02.03.3 a new time zone is saved (and published as preferences-updated).
    const save = page.getByRole('button', { name: 'Save changes' });
    await expect(save).toBeDisabled();
    await page.getByLabel('Time zone').selectOption('Asia/Tokyo');
    await page.getByLabel('Name', { exact: true }).fill('Kate E2E');
    await save.click();
    await expect(page.getByRole('status')).toHaveText('Saved');
    await page.reload();
    await expect(page.getByLabel('Time zone')).toHaveValue('Asia/Tokyo');
    await expect(page.getByLabel('Name', { exact: true })).toHaveValue('Kate E2E');

    // AC-02.05.1 a new exam date applies from next week.
    await page.getByRole('link', { name: 'Goal' }).click();
    await page.getByLabel('I haven’t booked the exam yet').uncheck();
    // The calendar offers five years ahead (AC-02.01.3): pick 15 March of the next year.
    const calendar = page.getByRole('group', { name: 'Exam date' });
    const year = String(new Date().getFullYear() + 1);
    await calendar.getByRole('combobox').nth(1).selectOption(year);
    await calendar.getByRole('combobox').nth(0).selectOption({ label: 'March' });
    await calendar.getByRole('button', { name: new RegExp(`15 March ${year}`) }).click();
    await expect(calendar.getByTestId('calendar-value')).toContainText(year);
    await page.getByRole('button', { name: 'Save changes' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Changes apply from next week' })).toBeVisible();

    // AC-32.02.1 explanations in Russian.
    await page.getByRole('link', { name: 'Learning' }).click();
    await page.getByLabel('Russian').check();
    await page.getByRole('button', { name: 'Save changes' }).click();
    await expect(page.getByRole('status')).toHaveText('Saved');
    await page.reload();
    await expect(page.getByLabel('Russian')).toBeChecked();

    // docs/07 §5.19 manual dark theme and reduced transparency.
    await page.getByRole('link', { name: 'Appearance' }).click();
    await page.getByRole('radio', { name: 'Dark' }).click();
    await expect(page.locator('html')).toHaveClass(/dark/);
    await page.getByRole('switch', { name: 'Reduce transparency' }).check();
    await expect(page.locator('html')).toHaveAttribute('data-transparency', 'reduced');
    await page.reload();
    await expect(page.locator('html')).toHaveClass(/dark/);
    await expectNoSeriousViolations(page);
  });

  test('AC-01.05.4 sign out of all devices ends the other sessions too', async ({ browser, page }) => {
    const email = `e2e-devices-${Date.now()}@toefly.test`;
    await signUp(page, email);
    await completeOnboarding(page);

    // A second device: another browser context signed in with the same account.
    const other = await browser.newContext();
    const second = await other.newPage();
    await second.goto('/');
    await second.getByRole('link', { name: 'I already have an account' }).click();
    await second.locator('#username').fill(email);
    await second.locator('#password').fill('Toefly-e2e-2026!');
    await second.locator('[type="submit"]').click();
    await expect(second.getByRole('heading', { name: 'Take your 25-minute diagnostic' })).toBeVisible();

    await page.goto('/settings/account');
    await page.getByRole('button', { name: 'Sign out of all devices' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Sign out of all devices' }).click();
    // This device returns to the Keycloak sign-in page.
    await expect(page.locator('#username')).toBeVisible();

    // The other device is signed out at its next request.
    await second.reload();
    await expect(second).toHaveURL(/\/sign-in|\/realms\/toefly\//);
    await other.close();
  });
});
