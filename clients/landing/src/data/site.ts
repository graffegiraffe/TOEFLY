import { APP_URL, CONTACT_EMAIL } from 'astro:env/server';

export const site = {
  name: 'TOEFLY',
  title: 'TOEFLY: prepare for the new TOEFL iBT week by week',
  description:
    'Prepare for the 2026 TOEFL iBT with a weekly plan built from your exam date, feedback after every task, an AI tutor that remembers your mistakes and adaptive mock exams. Tools for tutors included.',
  repository: 'https://github.com/graffegiraffe/TOEFLY',
  trademark: 'TOEFL and TOEFL iBT are registered trademarks of ETS. TOEFLY is not affiliated with or endorsed by ETS.',
} as const;

/** A link into the web app, e.g. appLink('/sign-up?role=tutor'). */
export function appLink(path: string): string {
  return new URL(path, APP_URL).toString();
}

/** Where people reach the project: an email when configured, otherwise the GitHub issues page. */
export const contact = CONTACT_EMAIL
  ? { href: `mailto:${CONTACT_EMAIL}`, label: CONTACT_EMAIL }
  : { href: `${site.repository}/issues/new`, label: 'a GitHub issue' };
