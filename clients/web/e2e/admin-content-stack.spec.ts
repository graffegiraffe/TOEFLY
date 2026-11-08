import { expect, test } from '@playwright/test';
import { completeOnboarding, expectNoSeriousViolations, PASSWORD, signUp } from './flows';
import { grantRealmRole, totp } from './keycloak';

// Runs against the real stack: infra/compose, api-gateway, profile-service, content-service with the seed bundle
// imported, and `pnpm dev`. An administrator must set up TOTP at the first sign-in (realm flow of ADMIN).
test.describe('US-28.01 content administration on the real stack', () => {
  test('an administrator reads the extraction warnings and approves a unit and an item set in review', async ({
    page,
    browser,
  }) => {
    const email = `e2e-admin-${Date.now()}@toefly.test`;
    await signUp(page, email, 'Ada');
    await completeOnboarding(page);
    await grantRealmRole(email, 'ADMIN');

    const context = await browser.newContext();
    const admin = await context.newPage();
    await admin.goto('/');
    await admin.getByRole('link', { name: 'I already have an account' }).click();
    await admin.locator('#username').fill(email);
    await admin.locator('#password').fill(PASSWORD);
    await admin.locator('[type="submit"]').click();

    // First sign-in as an administrator: the authenticator setup page, with the secret shown as text.
    await admin.getByRole('link', { name: /unable to scan/i }).click();
    const secret = (await admin.locator('#kc-totp-secret-key').innerText()).trim();
    await admin.locator('#totp').fill(totp(secret));
    await admin.locator('[type="submit"]').first().click();

    await admin.goto('/admin/content');
    await expect(admin.getByRole('heading', { name: 'Content', level: 1 })).toBeVisible();

    // Units read from scans wait for review and show what the extractor warned about.
    await admin.getByRole('tab', { name: 'Units' }).click();
    const grammar = admin.getByRole('region', { name: /Grammar units/ });
    const pending = grammar
      .getByRole('row')
      .filter({ has: admin.getByText('In review') })
      .first();
    await expect(pending).toBeVisible();
    await expectNoSeriousViolations(admin);
    await pending.getByText(/extraction warning/).click();
    const notes = pending.getByRole('region', { name: 'Extraction' });
    await expect(notes.getByText(/^Read from /)).toBeVisible();
    await expect(notes.getByRole('listitem').first()).toBeVisible();
    const unitTitle = await pending.getByRole('cell').first().locator('div').first().innerText();
    await pending.getByRole('button', { name: 'Approve' }).click();
    await expect(
      grammar.getByRole('row').filter({ hasText: unitTitle }).getByText('Approved', { exact: true }),
    ).toBeVisible();

    // An exercise set of the same book: the dialog shows how it was extracted, then the set is approved.
    await admin.getByRole('tab', { name: 'Item sets' }).click();
    await admin.getByLabel('Status').selectOption('IN_REVIEW');
    await admin.getByLabel('Task type').selectOption('VOCAB_EXERCISE');
    const row = admin
      .getByRole('row')
      .filter({ has: admin.getByRole('button', { name: 'View' }) })
      .first();
    await row.getByRole('button', { name: 'View' }).click();
    const dialog = admin.getByRole('dialog');
    await expect(dialog.getByRole('heading', { name: 'Extraction' })).toBeVisible();
    await expect(dialog.getByText(/Compare the content and the answer keys/)).toBeVisible();
    await expectNoSeriousViolations(admin);
    await dialog.getByRole('button', { name: 'Close' }).click();
    const topic = await row.getByRole('cell').first().locator('div').first().innerText();
    await row.getByRole('button', { name: 'Approve' }).click();
    await admin.getByLabel('Status').selectOption('APPROVED');
    await expect(admin.getByText(topic).first()).toBeVisible();
    await context.close();
  });
});
