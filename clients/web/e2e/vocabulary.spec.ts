import { expect, test, type Page } from '@playwright/test';
import { expectNoSeriousViolations } from './flows';
import { stubPlanning } from './planning-stub';
import { UNIT_ID, stubVocabulary } from './vocabulary-stub';

// Vocabulary runs against stubbed APIs (e2e/vocabulary-stub.ts on top of the practice and planning stubs), so only
// `pnpm dev` is needed; vocabulary-service, FSRS and ai-gateway are covered by the service's component tests.

/** Selects a word of the reviewed text the way a double click does and releases the mouse over it. */
async function selectWord(page: Page, word: string) {
  await page.evaluate((target) => {
    const area = document.querySelector('[data-testid="reading-text"]');
    if (!area) throw new Error('no reading text');
    const walker = document.createTreeWalker(area, NodeFilter.SHOW_TEXT);
    for (let node = walker.nextNode() as Text | null; node; node = walker.nextNode() as Text | null) {
      const at = node.data.indexOf(target);
      if (at >= 0) {
        const range = document.createRange();
        range.setStart(node, at);
        range.setEnd(node, at + target.length);
        const selection = window.getSelection();
        selection?.removeAllRanges();
        selection?.addRange(range);
        node.parentElement?.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
        return;
      }
    }
    throw new Error(`no "${target}" in the text`);
  }, word);
}

test.describe('EP-15 vocabulary on the web', () => {
  test('phase 8 demo: a word from a Reading text is saved and reviewed from Today', async ({ page }) => {
    const planning = await stubPlanning(page);
    const vocabulary = await stubVocabulary(page);
    await page.goto(`/review/attempts/${planning.practice.attemptId}`);

    await page.getByText('Text: Library hours').click();
    await selectWord(page, 'campus');
    const card = page.getByRole('dialog', { name: 'campus' });
    await expect(card).toContainText('the buildings and land of a university or college');
    await expect(card).toContainText('кампус');
    await expect(card).toContainText('noun · /ˈkæm.pəs/');
    await expectNoSeriousViolations(page);
    await card.getByRole('button', { name: 'Save word' }).click();
    await expect(card.getByRole('status')).toHaveText(/Saved to your dictionary/);
    expect(vocabulary.saved[0]).toMatchObject({
      term: 'campus',
      context: 'The campus library closes at 9 pm on Fridays during exam week.',
      sourceRef: { label: 'Library hours' },
    });
    await page.keyboard.press('Escape');

    // AC-15.01.3: the same word again
    await selectWord(page, 'campus');
    await expect(page.getByRole('dialog', { name: 'campus' }).getByRole('status')).toHaveText(
      /Already in your dictionary/,
    );
    await page.keyboard.press('Escape');

    // AC-15.02.1, AC-15.02.2: key words leave out the saved word
    const keyWords = page.getByTestId('key-words');
    await expect(keyWords.getByRole('checkbox')).toHaveCount(4);
    await keyWords.getByRole('checkbox', { name: /century/ }).uncheck();
    await keyWords.getByRole('button', { name: 'Add 3 words' }).click();
    await expect(keyWords.getByRole('status')).toHaveText('Added 3 words');

    // the next day: the words wait in Today
    await page.goto('/today');
    const today = page.getByTestId('today-vocabulary');
    await expect(today).toContainText('0 to review · 4 new');
    await expectNoSeriousViolations(page);
    await today.getByRole('link', { name: 'Review 4 words' }).click();

    await expect(page).toHaveURL(/\/vocabulary\/review$/);
    await expect(page.getByTestId('review-progress')).toHaveText('1 of 4');
    await expect(page.getByTestId('card-front')).toHaveText(/campus/);
    await page.keyboard.press('Space');
    await expect(page.getByTestId('card-back')).toContainText('the buildings and land of a university or college');
    await expect(page.getByRole('button', { name: /Again\s*1m/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /Easy\s*16d/ })).toBeVisible();
    await expectNoSeriousViolations(page);
    await page.keyboard.press('1');

    // AC-15.03.5: Again brings campus back after the other cards
    for (let shown = 0; shown < 3; shown++) {
      await expect(page.getByRole('button', { name: 'Show answer' })).toBeVisible();
      await page.keyboard.press('Space');
      await page.keyboard.press('4');
    }
    await expect(page.getByTestId('card-front')).toHaveText(/campus/);
    await page.getByRole('button', { name: 'Show answer' }).click();
    // Good would keep it in learning for 10 more minutes; Easy graduates it
    await page.getByRole('button', { name: /Easy/ }).click();

    await expect(page.getByTestId('review-summary')).toHaveText('4 reviewed · next in 6 h');
    expect(vocabulary.grades.map((grade) => grade.sequenceNo)).toEqual([0, 1, 2, 3, 4]);
    expect(vocabulary.grades[0]?.rating).toBe('AGAIN');
    expect(vocabulary.completed).toBe(1);
    await expectNoSeriousViolations(page);
  });

  test('US-15.06 My words: search, filters, edit, suspend and delete; dark theme passes axe', async ({ page }) => {
    await stubPlanning(page);
    await stubVocabulary(page, {
      seed: [
        { lemma: 'allocate', state: 'REVIEW', stability: 12, source: 'Urban forests', dueInDays: 0 },
        { lemma: 'allot', state: 'REVIEW', stability: 120, source: 'Budgets', dueInDays: 30 },
        { lemma: 'canopy', source: 'Urban forests' },
      ],
    });
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/vocabulary');

    await expect(page.getByTestId('word-list').getByRole('listitem')).toHaveCount(3);
    await expect(page.getByRole('button', { name: 'Due today (1)' })).toBeVisible();
    await expect(page.getByTestId('review-button')).toHaveText('Review 2 words');
    await expect(page.getByRole('img', { name: 'Memory strength 4 of 4' })).toBeVisible();
    await expectNoSeriousViolations(page);

    await page.getByRole('searchbox', { name: 'Search words' }).fill('all');
    await expect(page.getByTestId('word-list').getByRole('listitem')).toHaveCount(2);
    await page.getByRole('searchbox', { name: 'Search words' }).fill('');
    await page.getByRole('button', { name: 'Learned' }).click();
    await expect(page.getByTestId('word-list')).toContainText('allot');
    await expect(page.getByTestId('word-list').getByRole('listitem')).toHaveCount(1);
    await page.getByRole('button', { name: 'All words' }).click();

    await page.getByRole('link', { name: /allocate/ }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'allocate' })).toBeVisible();
    await expect(page.getByText('from: Urban forests')).toBeVisible();
    await expectNoSeriousViolations(page);

    await page.getByRole('button', { name: 'Edit' }).click();
    await page.getByLabel('Translation (Russian)').fill('выделять');
    await page.getByLabel('Note').fill('budgets');
    await page.getByRole('button', { name: 'Save changes' }).click();
    await expect(page.getByTestId('translation')).toHaveText('выделять');
    await expect(page.getByText('Note: budgets')).toBeVisible();

    await page.getByRole('button', { name: 'Suspend' }).click();
    await expect(page.getByRole('button', { name: 'Resume reviews' })).toBeVisible();

    await page.getByRole('button', { name: 'Delete' }).click();
    const confirm = page.getByRole('dialog', { name: 'Delete “allocate”?' });
    await expect(confirm).toContainText('This cannot be undone.');
    await expectNoSeriousViolations(page);
    await confirm.getByRole('button', { name: 'Delete' }).click();
    await expect(page).toHaveURL(/\/vocabulary$/);
    await expect(page.getByTestId('word-list').getByRole('listitem')).toHaveCount(2);
  });

  test('US-15.04 a Collins unit shows its words and adds the missing ones once', async ({ page }) => {
    await stubPlanning(page);
    await stubVocabulary(page, { seed: [{ lemma: 'reef' }] });
    await page.goto('/vocabulary?filter=units');

    const unit = page.getByTestId('word-sets').getByRole('link');
    await expect(unit).toContainText('Life science');
    await expect(unit).toContainText('3 words · 0 learned');
    await expectNoSeriousViolations(page);
    await unit.click();

    await expect(page).toHaveURL(new RegExp(`/vocabulary/units/${UNIT_ID}$`));
    await expect(page.getByRole('table')).toContainText('The wetland is a habitat for many birds.');
    await expectNoSeriousViolations(page);
    await page.getByRole('button', { name: 'Add 2 words to my dictionary' }).click();
    await expect(page.getByRole('status')).toHaveText('Added 2 words to your dictionary');

    await page.goto('/vocabulary');
    await expect(page.getByTestId('word-list')).toContainText('from: Collins Vocabulary Unit 1');
  });
});
