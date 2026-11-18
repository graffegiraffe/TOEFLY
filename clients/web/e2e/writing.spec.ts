import { expect, test } from '@playwright/test';
import {
  DRAFT_TEXT,
  pasteFromOutside,
  REVISED_TEXT,
  stubAssessment,
  SUBMISSION_ID,
  WEEK_ID,
  WRITING_TASK,
} from './assessment-stub';
import { expectNoSeriousViolations } from './flows';

// The Writing screens run against a stubbed API (e2e/assessment-stub.ts), so only `pnpm dev` is needed; the
// assessment-service side is covered by its component tests.

test.describe('US-09 Writing editor and US-11 Writing review', () => {
  test('US-09.03, US-11.03, US-11.05 an academic discussion from Practice: draft, review, Attempt 2', async ({
    page,
  }) => {
    const state = await stubAssessment(page);
    await page.goto('/practice?type=WRITE_ACADEMIC_DISCUSSION');
    await page.getByRole('button', { name: 'Start: free public transport' }).click();
    await expect(page).toHaveURL(new RegExp(`/write/${SUBMISSION_ID}$`));
    expect(state.creates).toHaveLength(1);

    // AC-09.03.1: the professor and two students with their names, the editor on the right.
    const task = page.getByTestId('task-card');
    await expect(task.getByText('Dr. Ortiz')).toBeVisible();
    await expect(task.getByText('Nora')).toBeVisible();
    await expect(task.getByText('Felix')).toBeVisible();
    await expect(page.getByRole('timer')).toHaveText(/10:00|09:5\d/);
    const editor = page.getByRole('textbox', { name: 'Your response' });
    await expect(editor).toHaveAttribute('spellcheck', 'false');
    await expect(editor).toHaveAttribute('autocorrect', 'off');
    await expect(editor).toHaveAttribute('autocomplete', 'off');
    await expect(page.getByTestId('word-count')).toHaveText('0/100');
    await expectNoSeriousViolations(page);

    // Paste from outside the editor is refused.
    await pasteFromOutside(page, '#essay', 'A text copied from the web');
    await expect(page.getByText('You can paste only text you copied from your response.')).toBeVisible();
    await expect(editor).toHaveValue('');

    await editor.fill(DRAFT_TEXT);
    await expect(page.getByTestId('word-count')).toHaveText(/^\d+\/100$/);
    await expect.poll(() => state.saves.at(-1)?.text).toBe(DRAFT_TEXT);
    expect(state.saves[0]?.ifMatch).toBe('"1"');
    await expect(page.getByText('Saved')).toBeVisible();

    await page.getByRole('button', { name: 'Submit', exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`/review/submissions/${SUBMISSION_ID}`));

    // AC-11.03.2: the score, the criteria, the summary and the inline comments arrive through the stream.
    const rubric = page.getByTestId('rubric-score');
    await expect(rubric.getByText('of 5')).toBeVisible();
    await expect(rubric.getByTestId('score-value')).toHaveText('3');
    await expect(rubric.getByText('Contribution')).toBeVisible();
    await expect(rubric.getByText('Task response')).toBeVisible();
    await expect(page.getByText('What worked')).toBeVisible();
    await expect(page.getByText('Check subject–verb agreement.')).toBeVisible();
    expect(state.streamRequests).toBeGreaterThan(0);

    const comments = page.getByRole('region', { name: 'Comments' });
    await expect(comments.getByRole('article')).toHaveCount(4);
    await expect(comments.getByText('Review: ROUNDUP3.U2 — Present simple')).toBeVisible();
    await expect(comments.getByRole('article', { name: 'General comment' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Explain in Russian' })).toHaveCount(0);
    await expect(page.getByTestId('essay-text').locator('[data-comment]')).toHaveCount(3);
    await page.getByRole('button', { name: 'Spelling', exact: true }).click();
    await expect(comments.getByRole('article')).toHaveCount(1);
    await expect(comments.getByText('better', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'All', exact: true }).click();
    await page.getByRole('button', { name: 'Open comment 1' }).click();
    await expect(comments.getByRole('article', { name: 'Comment 1' })).toBeFocused();

    // AC-11.03.5: the model answer with its source.
    await page.getByText('Model answer').click();
    await expect(page.getByTestId('model-answer')).toContainText('I agree with Felix that reliability matters');
    await expectNoSeriousViolations(page);

    // AC-11.05.1: Attempt 2 opens the editor with the text and the comments, without a timer.
    await page.getByRole('button', { name: 'Start Attempt 2' }).click();
    await expect(page).toHaveURL(new RegExp(`/write/${SUBMISSION_ID}$`));
    await expect(page.getByRole('heading', { name: /Attempt 2/ })).toBeVisible();
    await expect(page.getByRole('timer')).toHaveCount(0);
    await expect(page.getByRole('textbox', { name: 'Your response' })).toHaveValue(DRAFT_TEXT);
    await expect(page.getByRole('heading', { name: 'Comments on your Draft' })).toBeVisible();
    await expectNoSeriousViolations(page);
    await page.getByRole('textbox', { name: 'Your response' }).fill(REVISED_TEXT);
    await page.getByRole('button', { name: 'Submit', exact: true }).click();

    // AC-11.05.2: the new score, the change, the diff and the fate of the old comments.
    await expect(page.getByTestId('score-delta')).toHaveText('+1 since Draft');
    await expect(page.getByRole('link', { name: 'Attempt 2' })).toHaveAttribute('aria-current', 'page');
    const diff = page.getByTestId('version-diff');
    await expect(diff.locator('del')).toContainText(['lives']);
    await expect(diff.locator('ins')).toContainText(['live']);
    await expect(page.getByText('Fixed', { exact: true })).toHaveCount(2);
    await expect(page.getByText('Still present', { exact: true })).toHaveCount(1);
    await expect(page.getByRole('button', { name: 'Start Attempt 3' })).toBeVisible();
    await expectNoSeriousViolations(page);

    await page.getByRole('link', { name: 'Draft' }).click();
    await expect(page.getByTestId('score-value')).toHaveText('3');
    expect(state.newVersions).toBe(1);
  });

  test('US-09.02 an email from the week: To and Subject, the 7-minute timer and the short-response check', async ({
    page,
  }) => {
    const state = await stubAssessment(page, { taskType: 'WRITE_EMAIL', weekTask: true, afterSubmit: 'deferred' });
    await page.goto(`/tasks/${WRITING_TASK}?week=${WEEK_ID}`);
    await expect(page).toHaveURL(new RegExp(`/write/${SUBMISSION_ID}$`));
    expect(state.creates).toEqual([expect.objectContaining({ weekTaskId: WRITING_TASK, mode: 'PRACTICE' })]);

    await expect(page.getByTestId('email-to')).toHaveText('Maya Chen');
    await expect(page.getByTestId('email-subject')).toHaveText('Missed meeting yesterday');
    await expect(page.getByText('In your email, do the following:')).toBeVisible();
    await expect(page.getByText('Ask what was decided.')).toBeVisible();
    await expect(page.getByRole('timer')).toHaveText(/07:00|06:5\d/);
    await expect(page.getByTestId('word-count')).toHaveText('Word count: 0');
    await expectNoSeriousViolations(page);

    // Coming back to the task finds the same submission instead of creating another one.
    await page.goto(`/tasks/${WRITING_TASK}?week=${WEEK_ID}`);
    await expect(page).toHaveURL(new RegExp(`/write/${SUBMISSION_ID}$`));
    expect(state.creates).toHaveLength(1);

    await page.getByRole('textbox', { name: 'Your response' }).fill('Sorry, my train was cancelled.');
    await expect(page.getByTestId('word-count')).toHaveText('Word count: 5');
    await page.getByRole('button', { name: 'Submit', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: 'Your response is very short. Submit anyway?' });
    await expect(dialog).toBeVisible();
    await expectNoSeriousViolations(page);
    await dialog.getByRole('button', { name: 'Keep writing' }).click();
    expect(state.submits).toBe(0);
    await page.getByRole('button', { name: 'Submit', exact: true }).click();
    await dialog.getByRole('button', { name: 'Submit anyway' }).click();

    // AC-11.06.1: the AI is unavailable, the response waits for a later check.
    await expect(page).toHaveURL(new RegExp(`/review/submissions/${SUBMISSION_ID}`));
    await expect(
      page.getByText('AI feedback is delayed. Your response is saved and will be checked automatically'),
    ).toBeVisible();
    await expect(page.getByTestId('essay-text')).toHaveText('Sorry, my train was cancelled.');
    expect(state.saves.at(-1)?.text).toBe('Sorry, my train was cancelled.');
    await expectNoSeriousViolations(page);
  });

  test('AC-11.03.6 a slow check says so and polls when the stream is unavailable', async ({ page }) => {
    const state = await stubAssessment(page, { seed: 'evaluating', afterSubmit: 'evaluating', streamFails: true });
    await page.goto(`/review/submissions/${SUBMISSION_ID}`);
    await expect(page.getByTestId('rubric-evaluating')).toBeVisible();
    await expect(page.getByText('This takes longer than usual. We’ll notify you when it’s ready')).toBeVisible();
    await expect(page.getByTestId('essay-text')).toHaveText(DRAFT_TEXT);
    await expect.poll(() => state.polls, { timeout: 10_000 }).toBeGreaterThan(1);
    await expectNoSeriousViolations(page);
  });

  test('the Writing screens pass accessibility checks in the dark theme', async ({ page }) => {
    await stubAssessment(page, { seed: 'evaluated' });
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto(`/review/submissions/${SUBMISSION_ID}`);
    await expect(page.getByTestId('rubric-score')).toBeVisible();
    await page.getByText('Model answer').click();
    await expectNoSeriousViolations(page);
    await page.getByRole('button', { name: 'Start Attempt 2' }).click();
    await expect(page.getByRole('heading', { name: 'Comments on your Draft' })).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('the editor of a draft passes accessibility checks in the dark theme', async ({ page }) => {
    await stubAssessment(page);
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/practice?type=WRITE_ACADEMIC_DISCUSSION');
    await page.getByRole('button', { name: 'Start: free public transport' }).click();
    await page
      .getByRole('textbox', { name: 'Your response' })
      .fill(`${DRAFT_TEXT} ${DRAFT_TEXT} ${DRAFT_TEXT} ${DRAFT_TEXT}`);
    await expect(page.getByTestId('word-count')).toHaveText(/^1\d\d\/100$/);
    await expectNoSeriousViolations(page);
  });
});

test.describe('US-13.01 band estimate on Home', () => {
  test('AC-13.01.3 without enough data the ring asks for more and names what is missing', async ({ page }) => {
    await stubAssessment(page, { band: 'insufficient' });
    await page.goto('/');
    const ring = page.getByTestId('band-ring');
    await expect(ring.getByText('Need more data')).toBeVisible();
    await expect(ring.getByText('AI estimate')).toBeVisible();
    await expect(ring.getByText('Answer 8 more Listening questions to see your band.')).toBeVisible();
    await expect(ring.getByText('Write 2 more essays to see your Writing band.')).toBeVisible();
    await expect(ring.getByLabel('Reading: band 4.0–4.5')).toBeVisible();
    await expect(ring.getByLabel('Writing: need more data')).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('AC-13.01.1 with enough data the ring shows the range, CEFR and the 0–120 score', async ({ page }) => {
    await stubAssessment(page, { band: 'ready' });
    await page.goto('/');
    const ring = page.getByTestId('band-ring');
    await expect(ring.getByTestId('band-overall')).toHaveText('Estimated band 4.0–4.5');
    await expect(ring.getByText('B2 · ≈ 72–83 of 120')).toBeVisible();
    await ring.getByText('How is this calculated?').click();
    await expect(ring.getByText(/Rasch model/)).toBeVisible();
    await expect(ring.getByText(/not an official ETS score/)).toBeVisible();
    await expectNoSeriousViolations(page);
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.reload();
    await expect(page.getByTestId('band-overall')).toBeVisible();
    await expectNoSeriousViolations(page);
  });
});
