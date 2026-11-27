import { expect, test } from '@playwright/test';
import { expectNoSeriousViolations } from './flows';
import { stubPlanning, WEEK_ID } from './planning-stub';
import { stubPractice } from './practice-stub';
import { ACTION_ID, stubTutor, weeklyReview } from './tutor-stub';

// The chat and the weekly review run against stubbed APIs (e2e/tutor-stub.ts), so only `pnpm dev` is needed;
// tutor-agent-service itself is covered by its component tests.

test.describe('US-17 Toefly Tutor chat', () => {
  test('AC-17.01.1, AC-17.01.2, AC-17.01.4 Ask tutor from a review: context, streaming, resume, action', async ({
    page,
  }) => {
    const practice = await stubPractice(page, 'READ_DAILY_LIFE');
    const tutor = await stubTutor(page);
    await page.goto(`/review/attempts/${practice.attemptId}`);
    await page.getByRole('button', { name: 'Ask tutor about question 1' }).click();

    const panel = page.getByRole('dialog', { name: 'Toefly Tutor' });
    await expect(panel).toBeVisible();
    await expect(panel.getByTestId('context-chip')).toHaveText('Context: Question 1 · Read in Daily Life');
    await expect(page.getByTestId('tutor-fab')).toHaveCount(0);
    await expectNoSeriousViolations(page);

    const input = panel.getByRole('textbox', { name: 'Message' });
    await input.fill('Why is my answer wrong?');
    await expect(panel.getByTestId('message-counter')).toHaveText('23/2000');
    await input.press('Enter');

    // The first stream drops after two tokens; the client resumes it with Last-Event-ID.
    await expect(panel.getByText(/Then compare it with option B\./)).toBeVisible();
    await expect(panel.getByText('weekends', { exact: true })).toHaveJSProperty('tagName', 'STRONG');
    expect(tutor.resumes).toEqual(['5']);
    expect(tutor.threads[0]?.context).toMatchObject({ type: 'ATTEMPT_REVIEW', ref: practice.attemptId });
    expect(tutor.sent[0]?.body).toEqual({ text: 'Why is my answer wrong?' });
    await expect(panel.getByRole('button', { name: 'Stop' })).toHaveCount(0);

    const card = panel.getByTestId('action-card');
    await expect(card).toContainText('Notices practice, 6 min');
    await card.getByRole('button', { name: 'Add to my week' }).click();
    await expect(card.getByText('Added to your week')).toBeVisible();
    expect(tutor.confirmations).toEqual([ACTION_ID]);

    // AC-17.06: the explanation language of the thread.
    await panel.getByRole('button', { name: 'RU, explain in Russian' }).click();
    await expect.poll(() => tutor.languages).toEqual(['RU']);

    // AC-17.04.4: the second message is refused with 429.
    await input.fill('And the next one?');
    await input.press('Enter');
    await expect(panel.getByRole('alert')).toHaveText("You're sending messages too fast. Try again in a minute");
    await expectNoSeriousViolations(page);

    await panel.getByRole('button', { name: 'Close' }).click();
    await expect(panel).toBeHidden();
    await expect(page.getByTestId('tutor-fab')).toBeVisible();
  });

  test('AC-17.05.1 the AI Tutor button is hidden in an exam attempt', async ({ page }) => {
    const exam = await stubPractice(page, 'READ_DAILY_LIFE', { mode: 'EXAM' });
    await stubTutor(page);
    await page.goto(`/practice/${exam.attemptId}`);
    await expect(page.getByRole('heading', { name: 'Library hours' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'AI tutor' })).toHaveCount(0);
  });

  test('AC-17.01.5 without the right the button opens the Paywall', async ({ page }) => {
    await stubPlanning(page);
    await stubTutor(page, { entitlements: ['AI_WRITING_CHECK'] });
    await page.goto('/weeks/1');
    await page.getByRole('button', { name: 'AI tutor' }).click();
    await expect(page.getByRole('heading', { name: 'Chat with Toefly Tutor on Pro' })).toBeVisible();
    await expect(page.getByRole('dialog', { name: 'Toefly Tutor', exact: true })).toBeHidden();
  });
});

test.describe('US-18 Weekly review', () => {
  test('AC-18.03.1, AC-18.01.2 a closed week has a Review tab with every block of the review', async ({ page }) => {
    await stubPlanning(page, { completed: true });
    await stubTutor(page, { review: weeklyReview(WEEK_ID) });
    await page.goto('/weeks/1');
    await expect(page.getByTestId('weekly-review-card')).toContainText('Your Week 1 review is ready');
    await page.getByRole('navigation', { name: 'Week views' }).getByRole('link', { name: 'Review' }).click();
    await expect(page).toHaveURL(/\/weeks\/1\/review$/);

    await expect(page.getByRole('heading', { level: 1, name: 'Week 1 review' })).toBeVisible();
    await expect(page.getByTestId('review-headline')).toHaveText('Reading is up, articles still slip');
    await expect(page.getByTestId('review-done')).toHaveText('5 of 6 tasks · 2 h 5 min');
    await expect(page.getByRole('region', { name: 'Scores' }).getByText('AI estimate')).toBeVisible();
    await expect(page.getByTestId('band-delta')).toHaveText('+0.5 this week');
    await expect(page.getByTestId('top-mistakes').getByRole('listitem').filter({ hasText: 'times' })).toHaveCount(2);
    await expect(page.getByText('Review: ROUNDUP5.U3 — Articles')).toBeVisible();
    await expect(page.getByRole('region', { name: 'Strengths' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Focus for Week 2' })).toBeVisible();
    await expect(page.getByText('Week 2 is being prepared')).toBeVisible();
    await expectNoSeriousViolations(page);

    await page.getByRole('button', { name: 'Ask tutor' }).click();
    await expect(page.getByRole('dialog', { name: 'Toefly Tutor' }).getByTestId('context-chip')).toHaveText(
      'Context: Week 1 review',
    );
  });

  test('AC-18.01.5 a week with little activity gets the short review', async ({ page }) => {
    await stubPlanning(page, { completed: true });
    await stubTutor(page, {
      review: weeklyReview(WEEK_ID, { lowActivity: true, topMistakes: [], headline: 'A quiet week' }),
    });
    await page.goto('/weeks/1/review');
    await expect(page.getByTestId('low-activity')).toContainText('Not enough activity this week');
    await expect(page.getByRole('region', { name: 'Top recurring mistakes' })).toHaveCount(0);
  });

  test('the review is being prepared until tutor-agent-service has written it', async ({ page }) => {
    await stubPlanning(page, { completed: true });
    await stubTutor(page, { review: null });
    await page.goto('/weeks/1/review');
    await expect(page.getByTestId('review-preparing')).toContainText('Your review is being prepared');
    await expectNoSeriousViolations(page);
  });
});
