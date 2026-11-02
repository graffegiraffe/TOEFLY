# TOEFLY clickable prototype

A static, clickable prototype of the key TOEFLY screens on the web and on iPhone. It fixes the visual language (palette, Liquid Glass chrome, buttons, week covers) and the navigation before the real clients are built. Plain HTML, CSS and JavaScript, no build step.

```bash
npx serve design/prototype
```

Open the printed URL. The toolbar at the top switches:

- **Web / iPhone** — a 1440 px browser window with the glass sidebar, or an iPhone 17 Pro with the glass tab bar and the floating tutor button;
- **System / Light / Dark** — theme;
- **Reduce transparency** — glass panels become solid;
- **Screens** — every screen in the prototype.

A good path to start with: Sign in → Create an account → onboarding → Today → Week 7 → Complete the Words → Check answers → the tutor button.

URL parameters open a fixed view, for example `?frame=ios&theme=dark#/today`; add `&shot=1` to hide the toolbar for screenshots.

## Layout

| Path | What |
|---|---|
| `css/theme.css` | Design tokens: brand and neutral scales, semantic colors for both themes, tags, glass, gradients, radii, motion |
| `css/app.css` | Frames, navigation and shared components |
| `css/screens-*.css`, `js/screens/*.js` | Screens |
| `js/core.js`, `js/shell.js` | Component helpers, router, overlays, toolbar |
| `js/covers.js` | 16 week covers and 6 section covers |
| `fixtures/*.json` | Sample student, plan and Week 7 data |
| `contrast-report.md` | WCAG contrast of token pairs; regenerate with `node design/prototype/scripts/contrast-report.mjs` |
