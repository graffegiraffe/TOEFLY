// Settings tabs of docs/07-ux-ui.md §5.19. Notifications open with notification-service (phase 11b).

export const SETTINGS_TABS = ['profile', 'goal', 'schedule', 'learning', 'appearance', 'privacy', 'account'] as const;

export type SettingsTab = (typeof SETTINGS_TABS)[number];

const STUDENT_ONLY: readonly SettingsTab[] = ['goal', 'schedule'];

export function settingsTabsFor(roles: readonly string[]): SettingsTab[] {
  const student = roles.includes('STUDENT');
  return SETTINGS_TABS.filter((tab) => student || !STUDENT_ONLY.includes(tab));
}

export type ThemeChoice = 'system' | 'light' | 'dark';

const THEME_KEY = 'toefly.theme';
const TRANSPARENCY_KEY = 'toefly.reduceTransparency';

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null) {
  try {
    if (value === null) {
      localStorage.removeItem(key);
    } else {
      localStorage.setItem(key, value);
    }
  } catch {
    // Storage may be blocked; the choice then lasts until reload.
  }
}

export function storedTheme(): ThemeChoice {
  const value = read(THEME_KEY);
  return value === 'light' || value === 'dark' ? value : 'system';
}

export function prefersDark(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

/** Whether the choice renders dark; System follows the operating system. */
export function isDark(choice: ThemeChoice, systemDark: boolean): boolean {
  return choice === 'dark' || (choice === 'system' && systemDark);
}

/** Same rule as public/theme-init.js, which applies the choice before React renders. */
export function applyTheme(choice: ThemeChoice) {
  write(THEME_KEY, choice === 'system' ? null : choice);
  document.documentElement.classList.toggle('dark', isDark(choice, prefersDark()));
}

export function storedReduceTransparency(): boolean {
  return read(TRANSPARENCY_KEY) === 'true';
}

/** Solid surfaces instead of glass (docs/07-ux-ui.md §2.6). */
export function applyReduceTransparency(reduced: boolean) {
  write(TRANSPARENCY_KEY, reduced ? 'true' : null);
  if (reduced) {
    document.documentElement.dataset.transparency = 'reduced';
  } else {
    delete document.documentElement.dataset.transparency;
  }
}

/** "Changes apply from next week" when the exam date or the daily minutes change (AC-02.05.1). */
export function goalChangeAppliesNextWeek(
  before: { examDate?: string | null; minutesPerDay: number },
  after: { examDate?: string | null; minutesPerDay: number },
): boolean {
  return (before.examDate ?? null) !== (after.examDate ?? null) || before.minutesPerDay !== after.minutesPerDay;
}
