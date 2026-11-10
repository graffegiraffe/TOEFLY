import { expect, test, type Page } from '@playwright/test';
import { expectNoSeriousViolations } from './flows';
import { stubPractice } from './practice-stub';

// Audio starts by itself after the context line, as on the exam (docs/07-ux-ui.md §3.7).
test.use({ launchOptions: { args: ['--autoplay-policy=no-user-gesture-required'] } });

async function start(page: Page, taskType: string) {
  const stub = await stubPractice(page, taskType);
  await page.goto(`/practice?type=${taskType}`);
  await page.getByRole('button', { name: /^Start:/ }).click();
  await expect(page).toHaveURL(new RegExp(`/practice/${stub.attemptId}$`));
  return stub;
}

async function next(page: Page) {
  await page.getByRole('button', { name: 'Next' }).click();
}

async function check(page: Page, correct: number, total: number) {
  await page.getByRole('button', { name: 'Check answers' }).click();
  await expect(page).toHaveURL(/\/review\/attempts\//);
  await expect(page.getByRole('heading', { level: 1, name: `${correct} of ${total}` })).toBeVisible();
}

async function choose(page: Page, text: string | RegExp) {
  await page.getByRole('radio', { name: text }).click();
}

async function afterAudio(page: Page) {
  await expect(page.getByRole('button', { name: 'Replay' })).toBeVisible({ timeout: 10_000 });
}

test.describe('US-07 Reading players', () => {
  test('US-07.01 Complete the Words: letters go cell by cell, gap by gap', async ({ page }) => {
    const stub = await start(page, 'COMPLETE_THE_WORDS');
    await expect(page.getByText('Fill in the missing letters in the paragraph.')).toBeVisible();
    const first = page.getByLabel('Gap 1 of 10, word starts with “Ov”, letter 1 of 2');
    await expect(first).toHaveAttribute('spellcheck', 'false');
    await expect(first).toHaveAttribute('autocomplete', 'off');
    await first.click();
    // AC-07.01.2: the focus moves on by itself after the last cell of a gap
    await page.keyboard.type('erteraynd1vingndewleysvere');
    await expect(page.getByLabel('Gap 10 of 10, word starts with “tim”, letter 1 of 1')).toHaveValue('e');
    await expectNoSeriousViolations(page);
    await check(page, 10, 10);
    expect(stub.submissions).toHaveLength(1);
    expect(stub.lateHeartbeats).toBe(0);
    await expectNoSeriousViolations(page);
  });

  test('US-07.02 Read in Daily Life: questions with four options', async ({ page }) => {
    await start(page, 'READ_DAILY_LIFE');
    await expect(page.getByRole('heading', { name: 'Library hours' })).toBeVisible();
    await choose(page, /At 9 pm/);
    await next(page);
    await page.getByRole('radio', { name: /A new book/ }).focus();
    await page.keyboard.press('a');
    await expect(page.getByRole('radio', { name: /Opening hours/ })).toHaveAttribute('aria-checked', 'true');
    await expectNoSeriousViolations(page);
    await check(page, 2, 2);
  });

  test('US-07.03 Academic Passage: highlighted word and insert markers', async ({ page }) => {
    await start(page, 'READ_ACADEMIC_PASSAGE');
    await expect(page.locator('mark', { hasText: 'grow' })).toBeVisible();
    await choose(page, /increase/);
    await next(page);
    await page.getByRole('button', { name: 'Insert the sentence at position D' }).click();
    await expect(page.locator('article').getByText('This makes them fragile.')).toBeVisible();
    await expectNoSeriousViolations(page);
    await check(page, 2, 2);
    // AC-11.01.1: every distractor has its own explanation
    const first = page.getByRole('listitem').filter({ hasText: 'Question 1 · Correct' });
    await first.getByText('Question 1 · Correct').click();
    await expect(first.getByText('Why not A')).toBeVisible();
    await expect(page.getByText('A is wrong: shrink.')).toBeVisible();
    await expect(page.getByText('+0.12 to your Reading estimate')).toBeVisible();
  });
});

test.describe('US-08 Listening players', () => {
  for (const taskType of ['LISTEN_CONVERSATION', 'LISTEN_ANNOUNCEMENT', 'LISTEN_ACADEMIC_TALK']) {
    test(`US-08 ${taskType}: questions appear after the audio`, async ({ page }) => {
      await start(page, taskType);
      await expect(page.getByText('The questions appear after the audio.')).toBeVisible();
      await afterAudio(page);
      await choose(page, /Pollen/);
      await next(page);
      await choose(page, /Bees and plants/);
      await expectNoSeriousViolations(page);
      await check(page, 2, 2);
      await page.getByRole('button', { name: 'Show transcript' }).click();
      await expect(page.getByText('Bees help plants grow by carrying pollen.')).toBeVisible();
    });
  }

  test('US-08.01 Choose a Response: each reply plays before its options', async ({ page }) => {
    await start(page, 'LISTEN_CHOOSE_RESPONSE');
    await expect(page.getByText('Listen to the speaker.')).toBeVisible();
    await afterAudio(page);
    await choose(page, /Sure, at noon/);
    await next(page);
    await afterAudio(page);
    await choose(page, /Yes, thanks/);
    await check(page, 2, 2);
  });

  test('AC-08.05.2 audio that cannot be played is reported, the task can wait', async ({ page }) => {
    await stubPractice(page, 'LEGACY_LISTENING', { keepAudio: true });
    await page.goto('/practice?type=LEGACY_LISTENING');
    await page.getByRole('button', { name: /^Start:/ }).click();
    await expect(page.getByText('Audio is unavailable right now.')).toBeVisible();
    await expect(page.getByText('Old TOEFL format. Not counted in your band estimate.')).toBeVisible();
  });

  test('US-08 legacy listening with hosted audio', async ({ page }) => {
    await start(page, 'LEGACY_LISTENING');
    await afterAudio(page);
    await expect(page.getByLabel('Notes')).toBeVisible();
    await choose(page, /Where plates meet/);
    await check(page, 1, 1);
  });
});

test.describe('US-09.01 Build a Sentence', () => {
  test('fragments go in by tap and by keyboard', async ({ page }) => {
    await start(page, 'BUILD_A_SENTENCE');
    await expect(page.getByText('Did you finish the report?')).toBeVisible();
    await page.getByRole('button', { name: 'have', exact: true }).click();
    await page.getByRole('button', { name: 'not', exact: true }).click();
    // a tap on a placed fragment returns it
    await page.getByRole('button', { name: 'Slot 2: not' }).click();
    await page.getByRole('button', { name: 'not', exact: true }).focus();
    await page.keyboard.press('Enter');
    await page.getByRole('button', { name: 'finished', exact: true }).focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('button', { name: 'Slot 3: finished' })).toBeVisible();
    await expectNoSeriousViolations(page);
    await next(page);
    // Space picks a fragment up, arrows choose the slot, Enter drops it
    await page.getByRole('button', { name: 'at', exact: true }).focus();
    await page.keyboard.press('Space');
    await page.keyboard.press('ArrowRight');
    await page.keyboard.press('Enter');
    await page.getByRole('button', { name: 'It is', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Slot 1: It is' })).toBeVisible();
    await check(page, 2, 2);
    await page.getByText('Question 1 · Correct').click();
    await expect(page.getByText('I have not finished yet.').first()).toBeVisible();
  });
});

test.describe('Grammar and vocabulary exercises', () => {
  test('US-14 grammar: gap fill, choice, transformation, error correction', async ({ page }) => {
    await start(page, 'GRAMMAR_EXERCISE');
    await page.getByPlaceholder('Your answer').first().fill('easier');
    await choose(page, /taller/);
    await page.getByPlaceholder('Your answer').nth(1).fill('Tom is the tallest');
    await page.getByRole('button', { name: 'goed' }).click();
    await page.getByLabel('Correction').fill('went');
    await expectNoSeriousViolations(page);
    await check(page, 4, 4);
  });

  test('US-15 vocabulary: definition and word formation', async ({ page }) => {
    await start(page, 'VOCAB_EXERCISE');
    await choose(page, /bright and clear/);
    await page.getByPlaceholder('Your answer').fill('decision');
    await check(page, 2, 2);
  });

  test('legacy reading: a summary takes three answers', async ({ page }) => {
    await start(page, 'LEGACY_READING');
    await choose(page, /Rain/);
    await next(page);
    for (const idea of ['Idea A', 'Idea C', 'Idea E']) {
      await page.getByRole('checkbox', { name: new RegExp(idea) }).click();
    }
    await expect(page.getByText('3 selected')).toBeVisible();
    await check(page, 2, 2);
  });
});

test.describe('US-06.01 Practice mode', () => {
  test('AC-06.01.5 unanswered questions are confirmed and count as wrong', async ({ page }) => {
    await start(page, 'READ_DAILY_LIFE');
    await choose(page, /At 9 pm/);
    await next(page);
    await page.getByRole('button', { name: 'Check answers' }).click();
    await expect(page.getByText('1 question unanswered. Submit anyway?')).toBeVisible();
    await page.getByRole('button', { name: 'Submit', exact: true }).click();
    await expect(page.getByRole('heading', { level: 1, name: '1 of 2' })).toBeVisible();
    await expect(page.getByText('Question 2 · No answer')).toBeVisible();
  });

  test('AC-06.01.3 pause hides the task and stops the clock', async ({ page }) => {
    const stub = await start(page, 'READ_DAILY_LIFE');
    await page.getByRole('button', { name: 'Pause' }).click();
    await expect(page.getByRole('dialog', { name: 'Paused' })).toBeVisible();
    expect(stub.heartbeats.at(-1)?.state).toBe('PAUSED');
    await page.getByRole('dialog', { name: 'Paused' }).getByRole('button', { name: 'Resume' }).click();
    await expect(page.getByRole('dialog', { name: 'Paused' })).toBeHidden();
    expect(stub.heartbeats.at(-1)?.state).toBe('RESUMED');
  });

  test('AC-06.03.1 an answer is saved within two seconds with If-Match', async ({ page }) => {
    const stub = await start(page, 'READ_DAILY_LIFE');
    await choose(page, /At 9 pm/);
    await expect(page.getByText('Saved', { exact: true })).toBeVisible({ timeout: 4_000 });
    expect(stub.saved).toEqual([
      { itemId: expect.any(String), answer: { type: 'CHOICE', optionIds: ['B'] }, ifMatch: '"1"' },
    ]);
  });

  test('AC-16.01.2 a hidden tab is not counted', async ({ page }) => {
    await page.clock.install({ time: new Date('2026-11-10T10:00:00Z') });
    const stub = await start(page, 'READ_ACADEMIC_PASSAGE');
    const setVisibility = (state: 'hidden' | 'visible') =>
      page.evaluate((value) => {
        Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => value });
        document.dispatchEvent(new Event('visibilitychange'));
      }, state);
    const input = () => page.getByRole('heading', { level: 1 }).click();
    await input();
    await page.clock.runFor(10_000);
    await input();
    await setVisibility('hidden');
    await page.clock.runFor(90_000);
    await setVisibility('visible');
    await input();
    await page.clock.runFor(5_000);
    await input();
    await page.clock.runFor(15_000);
    const counted = () => stub.heartbeats.reduce((sum, beat) => sum + beat.activeSecondsDelta, 0);
    // 10 s before the tab was hidden and 5 s after it came back; the 90 hidden seconds are not counted
    await page.waitForTimeout(1000);
    await expect.poll(counted).toBeGreaterThanOrEqual(14);
    expect(counted()).toBeLessThanOrEqual(16);
    expect(stub.heartbeats.length).toBeGreaterThanOrEqual(2);
  });

  test('screens pass axe in the dark theme', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await start(page, 'READ_ACADEMIC_PASSAGE');
    await expectNoSeriousViolations(page);
    await choose(page, /increase/);
    await next(page);
    await choose(page, /Position D/);
    await check(page, 2, 2);
    await expectNoSeriousViolations(page);
  });
});
