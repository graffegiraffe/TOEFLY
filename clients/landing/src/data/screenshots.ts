import type { ImageMetadata } from 'astro';

/**
 * App screenshots used on the landing page. Each entry has a light and a dark capture of the same screen.
 *
 * To refresh them, replace the PNG files in src/assets/screenshots/ (same names, same aspect ratio) and rebuild:
 * the build converts them to AVIF and WebP. To add a screen, add both files and an entry here.
 */
export interface Screenshot {
  id: string;
  platform: 'web' | 'ios';
  alt: string;
  caption: string;
  light: ImageMetadata;
  dark: ImageMetadata;
}

const files = import.meta.glob<{ default: ImageMetadata }>('../assets/screenshots/*.png', { eager: true });

function pair(id: string): { light: ImageMetadata; dark: ImageMetadata } {
  const pick = (theme: 'light' | 'dark') => {
    const file = files[`../assets/screenshots/${id}-${theme}.png`];
    if (!file) throw new Error(`Missing screenshot src/assets/screenshots/${id}-${theme}.png`);
    return file.default;
  };
  return { light: pick('light'), dark: pick('dark') };
}

export const heroScreenshot: Screenshot = {
  id: 'hero',
  platform: 'web',
  alt: 'The Week 7 page in the TOEFLY web app next to the Today screen on iPhone: a cover banner, week progress at 64 percent and a table of tasks by section.',
  caption: 'Week 7 on the web and Today on iPhone',
  ...pair('hero'),
};

export const gallery: readonly Screenshot[] = [
  {
    id: 'ios-today',
    platform: 'ios',
    alt: 'The Today screen on iPhone with the estimated band and the list of tasks for the day.',
    caption: 'Today: what to do now',
    ...pair('ios-today'),
  },
  {
    id: 'ios-week',
    platform: 'ios',
    alt: 'The Week 3 page on iPhone with dates, total progress and the tasks of the week grouped by section.',
    caption: 'Your week, section by section',
    ...pair('ios-week'),
  },
  {
    id: 'ios-review',
    platform: 'ios',
    alt: 'The review of a Read in Daily Life task on iPhone, explaining why each wrong option is wrong.',
    caption: 'A review after every task',
    ...pair('ios-review'),
  },
  {
    id: 'web-progress',
    platform: 'web',
    alt: 'The Progress page in the web app with the predicted score, the study streak, band over time and weekly activity.',
    caption: 'Progress and the predicted band',
    ...pair('web-progress'),
  },
  {
    id: 'web-vocabulary-review',
    platform: 'web',
    alt: 'A vocabulary review card in the web app for the word "source" with the definition, translation and the Again, Hard, Good and Easy buttons.',
    caption: 'Spaced review of your own words',
    ...pair('web-vocabulary-review'),
  },
];
