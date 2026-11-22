import { expect, test } from '@playwright/test';
import { stubBilling } from './billing-stub';
import { expectNoSeriousViolations } from './flows';

// Billing runs against a stubbed API and a stubbed hosted checkout (e2e/billing-stub.ts), so only `pnpm dev` is
// needed; billing-service and the providers' webhooks are covered by its component tests.

test.describe('EP-27 billing on the web', () => {
  test('phase 11a demo: a Free learner out of AI checks buys Pro with Stripe and gets the rights without re-login', async ({
    page,
  }) => {
    const state = await stubBilling(page);
    await page.goto('/billing');

    await expect(page.getByTestId('plan-headline')).toHaveText('Student Free');
    await expect(page.getByTestId('ai-usage')).toContainText('AI checks: 10 of 10');
    await expect(page.getByTestId('plan-STUDENT_PRO')).toContainText('€12');
    await expect(page.getByTestId('plan-TUTOR')).toHaveCount(0);
    await expectNoSeriousViolations(page);

    await page.getByRole('button', { name: 'Upgrade: Pro' }).click();
    const dialog = page.getByRole('dialog', { name: 'Pay for Pro' });
    await expect(dialog.getByRole('radio')).toHaveCount(2);
    await expect(dialog.getByRole('radio', { name: 'Card (Stripe)' })).toBeChecked();
    await expect(dialog.getByTestId('test-mode-badge')).toHaveText('Test mode — no real charges');
    await expectNoSeriousViolations(page);
    await dialog.getByRole('button', { name: 'Continue to payment' }).click();

    await expect(page).toHaveURL(/\/billing\/checkout\/result\?session=cs-1$/);
    await expect(page.getByRole('heading', { name: 'You’re on Pro' })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('unlocked')).toContainText('Full mock exams');
    expect(state.checkouts).toEqual([
      {
        planTierCode: 'STUDENT_PRO',
        provider: 'STRIPE',
        returnUrl: expect.stringMatching(/\/billing\/checkout\/result$/),
      },
    ]);
    await expectNoSeriousViolations(page);

    await page.goto('/billing');
    await expect(page.getByTestId('plan-headline')).toHaveText(/^Pro · renews/);
    await expect(page.getByTestId('ai-usage')).toHaveCount(0);
    await expect(page.getByTestId('payment-history')).toContainText('Test payment');
  });

  test('AC-27.03.5 a declined card shows the reason of the provider and lets the learner try again', async ({
    page,
  }) => {
    await stubBilling(page, { decline: true });
    await page.goto('/billing');
    await page.getByRole('button', { name: 'Upgrade: Pro' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Continue to payment' }).click();

    await expect(page.getByRole('heading', { name: 'Payment didn’t go through' })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('failure-message')).toHaveText('Your card was declined.');
    await expectNoSeriousViolations(page);
    await page.getByRole('link', { name: 'Choose another method' }).click();
    await expect(page.getByRole('dialog', { name: 'Pay for Pro' })).toBeVisible();
  });

  test('AC-27.05.2 cancelling keeps Pro until the end of the period; dark theme passes accessibility checks', async ({
    page,
  }) => {
    const state = await stubBilling(page, { subscribed: true });
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/billing');

    await expect(page.getByTestId('plan-headline')).toHaveText('Pro · renews Dec 21');
    await page.getByRole('button', { name: 'Cancel subscription' }).click();
    const dialog = page.getByRole('dialog', { name: 'Cancel your subscription?' });
    await expect(dialog).toContainText('You’ll keep Pro until Dec 21');
    await expectNoSeriousViolations(page);
    await dialog.getByRole('button', { name: 'Cancel subscription' }).click();

    await expect(page.getByTestId('plan-headline')).toHaveText('Pro · ends Dec 21');
    await expect(page.getByRole('button', { name: 'Resume subscription' })).toBeVisible();
    expect(state.cancellations).toBe(1);
    await expectNoSeriousViolations(page);
  });

  test('the Billing tab of Settings opens the billing page', async ({ page }) => {
    await stubBilling(page);
    await page.goto('/settings/profile');
    await page.getByRole('link', { name: 'Billing' }).click();
    await expect(page).toHaveURL(/\/billing$/);
  });
});
