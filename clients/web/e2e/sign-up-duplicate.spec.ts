import { expect, test } from '@playwright/test';
import { signUp } from './flows';

const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8025';

async function subjectsTo(address: string): Promise<string[]> {
  const search = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${address}`)}`);
  const { messages } = (await search.json()) as { messages: { Subject: string }[] };
  return messages.map((message) => message.Subject);
}

test.describe('AC-01.01.3 sign-up with a registered email', () => {
  test('the form answers neutrally and the owner gets a notice without a link', async ({ page, browser }) => {
    const email = `e2e-dup-${Date.now()}@toefly.test`;
    await signUp(page, email);

    const other = await browser.newContext();
    const guest = await other.newPage();
    await guest.goto('/');
    await guest.getByRole('link', { name: 'Get started' }).click();
    await guest.locator('input[name="email"]').fill(email);
    await guest.locator('input[name="firstName"]').fill('Someone');
    for (const name of ['termsAccepted', 'privacyAccepted', 'ageConfirmed']) {
      await guest.locator(`input[type="checkbox"][name="${name}"]`).check();
    }
    await guest.locator('#kc-register-form [type="submit"]').click();
    await expect(
      guest
        .getByText('If this email is new, we’ve sent a confirmation link.')
        .or(guest.getByText("If this email is new, we've sent a confirmation link.")),
    ).toBeVisible();
    await expect(guest.getByText(/already exists/i)).toHaveCount(0);

    await expect
      .poll(() => subjectsTo(email), { timeout: 20_000 })
      .toContain('Someone tried to sign up with your email');
    await other.close();
  });
});
