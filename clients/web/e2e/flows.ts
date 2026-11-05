import AxeBuilder from '@axe-core/playwright';
import { expect, type Page } from '@playwright/test';
import { actionLinkFor } from './mailpit';

export const PASSWORD = 'Toefly-e2e-2026!';

export async function expectNoSeriousViolations(page: Page) {
  const results = await new AxeBuilder({ page }).analyze();
  const serious = results.violations.filter((violation) => ['serious', 'critical'].includes(violation.impact ?? ''));
  expect(
    serious.map(
      (violation) =>
        `${violation.id}: ${violation.nodes
          .map((node) => `${node.target.join(' ')} ${JSON.stringify(node.any[0]?.data ?? '')}`)
          .join(', ')}`,
    ),
  ).toEqual([]);
}

/** Registers a learner in Keycloak and opens the confirmation link from Mailpit; ends on the onboarding. */
export async function signUp(page: Page, email: string, firstName = 'E2E') {
  await page.goto('/');
  await page.getByRole('link', { name: 'Get started' }).click();
  await expect(page.getByRole('heading', { name: 'Create your account' })).toBeVisible();
  await page.locator('input[name="email"]').fill(email);
  await page.locator('input[name="firstName"]').fill(firstName);
  for (const name of ['termsAccepted', 'privacyAccepted', 'ageConfirmed']) {
    await page.locator(`input[type="checkbox"][name="${name}"]`).check();
  }
  await page.locator('#kc-register-form [type="submit"]').click();
  await page.locator('#password-new').fill(PASSWORD);
  await page.locator('#password-confirm').fill(PASSWORD);
  await page.locator('[type="submit"]').click();
  await expect(page.getByRole('heading', { name: 'Verify your email' })).toBeVisible();
  await page.goto(await actionLinkFor(email));
  await expect(page).toHaveURL(/\/onboarding\/goal$/);
}

/** Fills every onboarding step with defaults and ends on Home. */
export async function completeOnboarding(page: Page) {
  await page.getByRole('radio', { name: '5.0' }).click();
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByLabel('I haven’t booked the exam yet').check();
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByRole('button', { name: 'Continue to diagnostic' }).click();
  await expect(page.getByRole('heading', { name: 'Take your 25-minute diagnostic' })).toBeVisible();
}
