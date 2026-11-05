import { expect, test } from '@playwright/test';
import { expectNoSeriousViolations } from './flows';
import { actionLinkFor } from './mailpit';

const API = process.env.E2E_API_URL ?? 'http://api.toefly.localhost:8080';

test.describe('US-01.01 US-01.02 US-02.04 sign-up, email confirmation, onboarding, Home', () => {
  test('a new learner registers, confirms the email, completes the onboarding and sees Home', async ({
    page,
    context,
  }) => {
    const email = `e2e-${Date.now()}@toefly.test`;

    await page.goto('/');
    await expect(page).toHaveURL(/\/sign-in/);
    await page.getByRole('link', { name: 'Get started' }).click();

    // Keycloak registration page with the TOEFLY theme (AC-01.01.1, AC-01.01.4).
    await expect(page.getByRole('heading', { name: 'Create your account' })).toBeVisible();
    const submit = page.locator('#kc-register-form [type="submit"]');
    await page.locator('input[name="email"]').fill(email);
    await page.locator('input[name="firstName"]').fill('E2E');
    await expect(page.locator('input[name="accountType"][value="STUDENT"]')).toBeChecked();
    await expect(submit).toBeDisabled();
    for (const name of ['termsAccepted', 'privacyAccepted', 'ageConfirmed']) {
      await page.locator(`input[type="checkbox"][name="${name}"]`).check();
    }
    await expect(submit).toBeEnabled();
    await submit.click();

    // Keycloak asks for the password, then for the email confirmation (AC-01.01.2 policy applies here).
    await page.locator('#password-new').fill('Toefly-e2e-2026!');
    await page.locator('#password-confirm').fill('Toefly-e2e-2026!');
    await page.locator('[type="submit"]').click();
    await expect(page.getByRole('heading', { name: 'Verify your email' })).toBeVisible();

    // AC-01.02.1 the link from the email confirms the address and opens the onboarding.
    await page.goto(await actionLinkFor(email));
    await expect(page).toHaveURL(/\/onboarding\/goal$/);

    // AC-01.05.1 the browser holds only the HttpOnly session cookie, no tokens in storage.
    const cookie = (await context.cookies(API)).find((candidate) => candidate.name === 'toefly_sid');
    // Strict in clusters; Lax locally, where Keycloak on localhost:8180 is another site (docs/10 §4.2).
    const sameSite = process.env.E2E_COOKIE_SAME_SITE ?? 'Lax';
    expect(cookie).toMatchObject({ httpOnly: true, secure: true, sameSite });
    const stored = await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }));
    expect(stored).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/);
    await expectNoSeriousViolations(page);

    await page.getByRole('radio', { name: '5.0' }).click();
    await expect(page.getByTestId('band-hint')).toHaveText('5.0 ≈ C1 ≈ 95+');
    await page.getByRole('button', { name: 'Continue' }).click();

    await expect(page).toHaveURL(/\/onboarding\/exam-date$/);
    await page.getByLabel('I haven’t booked the exam yet').check();
    await page.getByRole('button', { name: 'Continue' }).click();

    await expect(page).toHaveURL(/\/onboarding\/schedule$/);
    await page.getByRole('radio', { name: '15', exact: true }).click();
    await expect(page.getByRole('status')).toHaveText(/15 minutes a day may not be enough/);
    await page.getByRole('radio', { name: '60', exact: true }).click();
    await page.getByRole('button', { name: 'Continue' }).click();

    await expect(page).toHaveURL(/\/onboarding\/explanation-language$/);
    await page.getByRole('button', { name: 'Continue' }).click();

    await expect(page).toHaveURL(/\/onboarding\/consents$/);
    await page.getByRole('button', { name: 'Continue' }).click();

    await expect(page).toHaveURL(/\/onboarding\/diagnostic-intro$/);
    await page.getByRole('button', { name: 'Continue to diagnostic' }).click();

    // AC-02.04.4 Home before the diagnostic: the diagnostic block, no plan yet.
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByRole('heading', { name: 'Welcome, E2E' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Take your 25-minute diagnostic' })).toBeVisible();
    await expect(page.getByTestId('home-goal')).toHaveText('5.0 ≈ C1 ≈ 95+');
    await expectNoSeriousViolations(page);

    // AC-02.04.3 the onboarding route redirects to Home once completed.
    await page.goto('/onboarding/goal');
    await expect(page).toHaveURL(/\/$/);

    // Dark theme by the system setting.
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.reload();
    await expect(page.locator('html')).toHaveClass(/dark/);
    await expectNoSeriousViolations(page);
  });
});
