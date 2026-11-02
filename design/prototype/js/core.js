/* Prototype core: screen registry and component helpers (names match the design system components).
   Every screen file calls P.register(path, def); helpers return HTML strings. */
(function () {
  const P = (window.P = {
    fx: {},
    screens: {},
    order: [],
    state: { theme: 'system', frame: 'web', reduced: false },
  });

  /* def: { title, num (position in the screen menu, or 'plan' for supporting screens), web, ios, layout: 'app'|'auth'|'player'|'bare',
           nav (sidebar id), tab (iOS tab id), role: 'student'|'tutor', fab, render(ctx), mount(root, ctx) } */
  P.register = function (path, def) {
    P.screens[path] = Object.assign({ web: true, ios: true, layout: 'app', role: 'student', fab: true }, def, { path });
    P.order.push(path);
  };

  P.esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  /* Lucide icon placeholder; replaced by lucide.createIcons() after each render */
  P.i = (name, cls = '') => `<i data-lucide="${name}" class="ic ${cls}" aria-hidden="true"></i>`;

  P.SECTIONS = {
    READING: { key: 'reading', label: 'Reading', icon: 'book-open' },
    LISTENING: { key: 'listening', label: 'Listening', icon: 'headphones' },
    WRITING: { key: 'writing', label: 'Writing', icon: 'pen-line' },
    SPEAKING: { key: 'speaking', label: 'Speaking', icon: 'mic' },
    VOCABULARY: { key: 'vocabulary', label: 'Vocabulary', icon: 'book-a' },
    GRAMMAR: { key: 'grammar', label: 'Grammar', icon: 'text' },
    RESOURCES: { key: 'resources', label: 'Resources', icon: 'link' },
  };
  P.sec = (s) => P.SECTIONS[s] || P.SECTIONS.RESOURCES;

  P.TASK_TYPES = {
    COMPLETE_THE_WORDS: 'Complete the Words', READ_DAILY_LIFE: 'Read in Daily Life', READ_ACADEMIC_PASSAGE: 'Read an Academic Passage',
    LISTEN_CHOOSE_RESPONSE: 'Listen and Choose a Response', LISTEN_CONVERSATION: 'Listen to a Conversation',
    LISTEN_ANNOUNCEMENT: 'Listen to an Announcement', LISTEN_ACADEMIC_TALK: 'Listen to an Academic Talk',
    BUILD_A_SENTENCE: 'Build a Sentence', WRITE_EMAIL: 'Write an Email', WRITE_ACADEMIC_DISCUSSION: 'Academic Discussion',
    LISTEN_AND_REPEAT: 'Listen and Repeat', TAKE_AN_INTERVIEW: 'Take an Interview',
    GRAMMAR_EXERCISE: 'Grammar exercise', VOCAB_EXERCISE: 'Vocabulary exercise',
    LEGACY_READING: 'Reading', LEGACY_LISTENING: 'Listening',
  };

  /* Button: v = primary | secondary | ghost | destructive | icon */
  P.btn = function (label, o = {}) {
    const v = o.v || 'primary';
    const cls = ['btn', 'btn-' + v, o.size || '', o.block ? 'block' : '', o.cls || ''].join(' ').trim();
    const ic = o.icon ? P.i(o.icon) : '';
    const inner = v === 'icon' ? ic : `${ic}<span>${label}</span>${o.iconRight ? P.i(o.iconRight) : ''}`;
    const aria = v === 'icon' ? ` aria-label="${P.esc(label)}" title="${P.esc(label)}"` : '';
    const attrs = (o.attrs || '') + aria + (o.disabled ? ' disabled' : '');
    if (o.href) return `<a class="${cls}" href="#${o.href}"${attrs}>${inner}</a>`;
    return `<button type="button" class="${cls}"${attrs}>${inner}</button>`;
  };

  /* Tag: section = WeekSection or a plain class (neutral, accent, warn, info, danger) */
  P.tag = function (section, text, o = {}) {
    const key = P.SECTIONS[section] ? P.SECTIONS[section].key : section;
    const cls = ['tag', key, o.cell ? 'cell' : '', o.mod || '', o.href ? 'link-tag' : ''].join(' ').trim();
    const pre = (o.done ? P.i('check') : '') + (o.mod === 'tutor' ? P.i('user-pen') : '') + (o.dot && !o.done ? '<span class="dot"></span>' : '');
    const post = o.mod === 'external' ? P.i('external-link') : '';
    const label = o.mod === 'legacy' ? `${text} · Legacy` : text;
    const title = o.title ? ` title="${P.esc(o.title)}"` : '';
    const body = `${pre}<span class="tx">${label}</span>${post}`;
    if (o.href) return `<a class="${cls}" href="#${o.href}"${title}>${body}</a>`;
    return `<span class="${cls}"${title}>${body}</span>`;
  };

  /* ProgressBar */
  P.bar = function (pct, o = {}) {
    const bar = `<div class="bar ${o.thin ? 'thin' : ''}" role="progressbar" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100" aria-label="${o.label || 'Progress'}"><i style="width:${pct}%"></i></div>`;
    return o.pct === false ? bar : `<div class="bar-row">${bar}<span class="pct">${pct}%</span></div>`;
  };

  /* ProgressRing / BandRing; ring-gradient #0F7D4D → #04AC6A */
  let ringSeq = 0;
  P.ring = function (o) {
    const size = o.size || 74;
    const sw = o.stroke || (size >= 120 ? 10 : size <= 48 ? 5 : 8);
    const r = (size - sw) / 2;
    const len = 2 * Math.PI * r;
    const frac = Math.max(0, Math.min(1, (o.value || 0) / (o.max || 1)));
    const id = 'rg' + ++ringSeq;
    const fs = o.fontSize || (size >= 120 ? 34 : size >= 74 ? 18 : 13);
    const center = o.center !== undefined ? o.center : '';
    return `<div class="ring ${o.dashed ? 'dashed' : ''}" style="width:${size}px;height:${size}px" role="img" aria-label="${P.esc(o.label || '')}">
      <svg viewBox="0 0 ${size} ${size}"><defs><linearGradient id="${id}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#0F7D4D"/><stop offset="1" stop-color="#04AC6A"/></linearGradient></defs>
      <circle class="ring-track" cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke-width="${sw}"/>
      ${o.dashed ? '' : `<circle class="ring-arc" style="--len:${len}" cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="url(#${id})" stroke-width="${sw}" stroke-linecap="round" stroke-dasharray="${len}" stroke-dashoffset="${len * (1 - frac)}" transform="rotate(-90 ${size / 2} ${size / 2})"/>`}</svg>
      <span class="ring-val" style="font-size:${fs}px">${center}</span></div>`;
  };

  P.bandRing = function (band, size = 120) {
    return P.ring({ value: band.low, max: 6, size, center: band.low.toFixed(1), label: `Estimated band ${band.low} to ${band.high}, AI estimate` });
  };

  /* BandRing card text block: "Estimated band 4.5–5.0 · B2 → C1 · ≈ 95 of 120 · AI estimate" */
  P.bandText = function (band, o = {}) {
    return `<div class="stack gap-1">
      <div class="${o.small ? 't-strong' : 't-title-3'}">Estimated band ${band.low.toFixed(1)}–${band.high.toFixed(1)}</div>
      <div class="muted ${o.small ? 't-caption' : ''}">${band.cefr} · ≈ ${band.total} of 120</div>
      <div class="row gap-2 mt-1"><span class="ai-badge">AI estimate</span>${o.extra || ''}</div></div>`;
  };

  P.miniRings = function (s, size = 48) {
    const names = { R: 'Reading', L: 'Listening', W: 'Writing', S: 'Speaking' };
    return `<div class="mini-rings">${Object.keys(names)
      .map((k) => `<div class="mr">${P.ring({ value: s[k], max: 6, size, center: s[k].toFixed(1), label: `${names[k]} ${s[k]}` })}<span>${k}</span></div>`)
      .join('')}</div>`;
  };

  /* Checkbox */
  P.check = function (on, o = {}) {
    const cls = ['check', on ? 'on' : '', o.round ? 'round' : '', o.half ? 'half' : '', o.cls || ''].join(' ').trim();
    const label = o.label ? ` aria-label="${P.esc(o.label)}"` : '';
    return `<button type="button" class="${cls}" role="checkbox" aria-checked="${on ? 'true' : 'false'}"${label} ${o.attrs || ''}><svg viewBox="0 0 24 24" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12l5 5 9-10"/></svg></button>`;
  };

  /* Cover: id from window.Covers; variant = week | week-card | section | section-header */
  P.cover = function (id, variant = 'week', cls = '') {
    const art = (window.Covers && window.Covers[id]) || '';
    return `<div class="cover ${variant} ${cls}" aria-hidden="true">${art}</div>`;
  };
  P.weekCoverId = function (n, examWeeks = [4, 8, 17]) {
    const ids = ['week-01-campus', 'week-02-library', 'week-03-lecture-hall', 'week-04-exam-desk', 'week-05-headphones', 'week-06-microphone',
      'week-07-open-books', 'week-08-letter', 'week-09-globe', 'week-10-laboratory', 'week-11-quad', 'week-12-dormitory',
      'week-13-museum', 'week-14-observatory', 'week-15-cafeteria', 'week-16-graduation'];
    if (examWeeks.includes(n)) return 'week-04-exam-desk';
    let idx = (n - 1) % 16;
    if (idx === 3) idx = 4;
    return ids[idx];
  };

  /* Timer */
  P.timer = (text, o = {}) =>
    `<span class="timer ${o.state || ''}" ${o.attrs || ''}>${P.i('timer')}<span class="tv">${text}</span>${o.label ? `<span class="tl">${o.label}</span>` : ''}</span>`;

  /* EmptyState */
  P.empty = (o) => `<div class="empty">
      <svg class="empty-art" viewBox="0 0 120 90" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${o.art || '<path d="M20 70h80M30 70V40l30-18 30 18v30M45 70V48M60 70V48M75 70V48"/><circle cx="96" cy="22" r="8"/>'}</svg>
      <div class="t-title-3">${o.title}</div>${o.text ? `<div class="muted" style="max-width:360px">${o.text}</div>` : ''}
      ${o.btn ? `<div class="mt-2">${o.btn}</div>` : ''}</div>`;

  P.banner = (kind, icon, html) => `<div class="banner ${kind}">${P.i(icon)}<div>${html}</div></div>`;

  P.logo = () => '<span class="logo">TOE<b>FLY</b></span>';

  P.avatar = (initials, size = '') => `<span class="avatar ${size}" aria-hidden="true">${initials}</span>`;

  P.iconBox = (icon) => `<span class="icon-box">${P.i(icon)}</span>`;
  P.sectionIcon = (section) => `<span class="icon-box">${P.i(P.sec(section).icon)}</span>`;

  /* Mesh backgrounds: kind = app | auth */
  P.mesh = (kind = 'app') => `<div class="mesh ${kind}-mesh" aria-hidden="true"><i class="b1"></i><i class="b2"></i><i class="b3"></i></div>`;

  P.go = (path) => { location.hash = '#' + path; };

  /* Fixtures (JSON): screen files push names into P.fixtureList; 'week-7' becomes P.fx.week7 */
  P.fixtureList = ['student', 'week-7', 'weeks'];
  P.loadFixtures = async function (names) {
    await Promise.all(names.map(async (n) => {
      const r = await fetch(`fixtures/${n}.json`);
      P.fx[n.replace(/-(\w)/g, (_, c) => c.toUpperCase())] = await r.json();
    }));
  };
})();
