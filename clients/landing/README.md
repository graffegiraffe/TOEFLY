# TOEFLY landing

The public page of TOEFLY: what the product does for learners and tutors, how the weekly cycle works, Toefly Tutor,
pricing, screenshots of the web and iOS apps, where the study material comes from, and the privacy policy and terms.
Static [Astro](https://astro.build) site, English only, light and dark theme. The only client script is the theme
toggle.

## Run

Node 22.23.4 or newer (`.nvmrc` pins 24) and pnpm.

```bash
pnpm install
pnpm dev          # http://localhost:4321
pnpm build        # static site in dist/
pnpm preview      # serves dist/
pnpm lint && pnpm format:check && pnpm typecheck
```

## Configuration

Copy `.env.example` to `.env` to change the defaults. All values are read at build time.

| Variable        | Default                 | What it does                                                                                      |
| --------------- | ----------------------- | ------------------------------------------------------------------------------------------------- |
| `SITE_URL`      | `http://localhost:4321` | Public origin of this page: canonical URLs, Open Graph, `sitemap-index.xml`, `robots.txt`         |
| `APP_URL`       | `http://localhost:5173` | Base URL of the web app; Sign in goes to `/sign-in`, Start free to `/sign-up`                     |
| `CONTACT_EMAIL` | none                    | Public contact for privacy and rights-holder requests; without it the pages link to GitHub issues |

## Content

| What                    | Where                                                                     |
| ----------------------- | ------------------------------------------------------------------------- |
| Prices and plan details | `src/data/plans.ts` (a copy of the billing-service catalogue)             |
| Screenshots             | `src/assets/screenshots/` and `src/data/screenshots.ts`                   |
| Sections                | `src/components/sections/`                                                |
| Privacy policy, terms   | `src/pages/privacy.astro`, `src/pages/terms.astro`                        |
| Design tokens           | `src/styles/global.css` (same values as `design/prototype/css/theme.css`) |

### Replacing screenshots

Each screen has a light and a dark PNG: `<id>-light.png` and `<id>-dark.png` in `src/assets/screenshots/`. Overwrite the
files with new captures of the same aspect ratio and rebuild; the build makes AVIF and WebP in several widths. To add a
screen, add both files and an entry in `src/data/screenshots.ts`. After changing `hero-light.png`, run
`node scripts/make-og.mjs` to refresh the Open Graph image and the touch icon.

## Deploying to Cloudflare Pages

| Setting               | Value                                                                |
| --------------------- | -------------------------------------------------------------------- |
| Root directory        | `clients/landing`                                                    |
| Build command         | `pnpm install --frozen-lockfile && pnpm build`                       |
| Output directory      | `dist`                                                               |
| Environment variables | `NODE_VERSION=24`, `SITE_URL`, `APP_URL`, optionally `CONTACT_EMAIL` |
