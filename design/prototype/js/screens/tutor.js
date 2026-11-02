/* Tutor workspace, landing, billing and the component catalog. */
(function () {
  const P = window.P;
  const $ = (r, s) => r.querySelector(s);
  const $$ = (r, s) => Array.from(r.querySelectorAll(s));
  const icons = () => window.lucide && window.lucide.createIcons();

  /* plans and prices */
  const PLANS = [
    { code: 'STUDENT_FREE', name: 'Student Free', price: '€0', per: '', note: 'Start without a card', feats: ['Diagnostic test', '1 week of tasks', '10 AI checks for Writing and Speaking, for the whole term'] },
    { code: 'STUDENT_PRO', name: 'Pro', price: '€12', per: '/ month', badge: '7 days free', note: 'For self-study', feats: ['Everything, no limits', 'A new week every week, built from your mistakes', 'Mock exams with adaptive sections', 'Toefly Tutor chat and weekly reviews'] },
    { code: 'TUTOR', name: 'Tutor', price: '€29', per: '/ month', note: 'Up to 10 students', feats: ['Tutor workspace', 'Week approval and your own materials', 'Your students get Pro while enrolled'] },
    { code: 'TUTOR_PLUS', name: 'Tutor Plus', price: '€59', per: '/ month', note: 'Up to 30 students', feats: ['Everything in Tutor', 'Up to 30 students', 'Your students get Pro while enrolled'] },
  ];
  const planCard = (p, o = {}) => `<div class="card lg bl-plan ${o.current ? 'current' : ''}" data-plan="${p.code}">
      <div class="row between gap-2"><div class="t-title-3">${p.name}</div>${o.current ? P.tag('accent', 'Current plan') : p.badge ? P.tag('accent', p.badge) : ''}</div>
      <div class="bl-price"><span class="t-title-1">${p.price}</span><span class="muted">${p.per}</span></div>
      <div class="t-caption muted">${p.note}</div>
      <ul class="bl-feats">${p.feats.map((f) => `<li>${P.i('check', 'ic-14')}<span>${f}</span></li>`).join('')}</ul>
      ${o.action ? `<div class="bl-act">${o.action}</div>` : ''}</div>`;

  const dlg = (title, body, foot) => `<div class="stack gap-4"><div class="row between"><h2 class="t-title-2">${title}</h2>${P.btn('Close', { v: 'icon', icon: 'x', size: 'sm', attrs: 'data-action="close"' })}</div>${body}${foot ? `<div class="row gap-2 wrap" style="justify-content:flex-end">${foot}</div>` : ''}</div>`;
  const field = (id, label, control) => `<div class="field"><label for="${id}">${label}</label>${control}</div>`;
  const selectOf = (id, opts, sel) => `<select class="input" id="${id}">${opts.map((o) => `<option${o === sel ? ' selected' : ''}>${o}</option>`).join('')}</select>`;

  /* ══════════════════════ 1. Landing ══════════════════════ */
  const TYPE_SECTION = {
    COMPLETE_THE_WORDS: 'READING', READ_DAILY_LIFE: 'READING', READ_ACADEMIC_PASSAGE: 'READING',
    LISTEN_CHOOSE_RESPONSE: 'LISTENING', LISTEN_CONVERSATION: 'LISTENING', LISTEN_ANNOUNCEMENT: 'LISTENING', LISTEN_ACADEMIC_TALK: 'LISTENING',
    BUILD_A_SENTENCE: 'WRITING', WRITE_EMAIL: 'WRITING', WRITE_ACADEMIC_DISCUSSION: 'WRITING',
    LISTEN_AND_REPEAT: 'SPEAKING', TAKE_AN_INTERVIEW: 'SPEAKING', GRAMMAR_EXERCISE: 'GRAMMAR', VOCAB_EXERCISE: 'VOCABULARY',
    LEGACY_READING: 'READING', LEGACY_LISTENING: 'LISTENING',
  };

  function landing() {
    const steps = [
      ['gauge', 'Diagnostic', 'A short test sets your starting band and shows your weak spots.'],
      ['calendar-days', 'Your weeks', 'Each week brings tasks in Reading, Listening, Writing and Speaking, plus grammar and vocabulary.'],
      ['message-square-text', 'Feedback', 'After every task you see what was right, what was wrong and why.'],
      ['clipboard-check', 'Mock exam', 'Full exams with adaptive sections, scored 1–6 and 0–120.'],
    ];
    const cloud = Object.keys(TYPE_SECTION).map((k) => P.tag(TYPE_SECTION[k], P.TASK_TYPES[k], k.startsWith('LEGACY') ? { mod: 'legacy' } : {})).join('');
    const faq = [
      ['Is TOEFLY the real TOEFL?', 'No. TOEFLY is a preparation platform built around the iBT format used from January 21, 2026. Mock exams follow the same structure, but scores are estimates marked "AI estimate".'],
      ['How does the weekly plan work?', 'Every week you get tasks for each section. After you finish, you get a weekly review and a new week built from your mistakes.'],
      ['Can I study with my own tutor?', 'Yes. A tutor invites you with a link or a code, approves your weeks and adds their own materials. You get Pro while you are enrolled.'],
      ['Can I cancel Pro at any time?', 'Yes. You keep Pro until the end of the paid period. Prices are in EUR.'],
    ];
    return `<div class="lp">
      <header class="lp-head glass">
        <a href="#/landing" class="logo" aria-label="TOEFLY home">TOE<b>FLY</b></a>
        <nav class="lp-links" aria-label="Sections"><a href="#/landing" data-scroll="features">Features</a><a href="#/landing" data-scroll="tutors">Tutors</a><a href="#/landing" data-scroll="pricing">Pricing</a></nav>
        <a class="btn btn-secondary sm" href="#/sign-in">Sign in</a>
      </header>
      <section class="lp-hero">${P.mesh('auth')}
        <div class="lp-wrap lp-hero-grid">
          <div class="stack gap-5 lp-copy">
            <h1 class="t-display lp-h1">Prepare for the new TOEFL iBT like you have a personal tutor.</h1>
            <p class="t-body-lg muted" style="max-width:520px">Weekly plans, instant feedback on every task, real mock exams with adaptive sections.</p>
            <div class="row gap-3 wrap">${P.btn('Start free', { size: 'lg', href: '/sign-in?mode=signup' })}${P.btn("I'm a tutor", { v: 'secondary', size: 'lg', href: '/sign-in?mode=signup&role=tutor' })}</div>
          </div>
          <div class="lp-shot-wrap" aria-hidden="true">
            <div class="lp-shot">
              ${P.cover('week-07-open-books', 'week')}
              <div class="lp-shot-body">
                <div class="row between"><div><div class="t-title-2">Week 7</div><div class="t-caption muted">Oct 11 – Oct 17, 2026</div></div>${P.ring({ value: 64, max: 100, size: 48, center: '64%', fontSize: 11, label: 'Total progress 64%' })}</div>
                <div class="row gap-1 wrap">${P.tag('READING', 'Reading')}${P.tag('LISTENING', 'Listening')}${P.tag('WRITING', 'Writing')}${P.tag('SPEAKING', 'Speaking')}${P.tag('VOCABULARY', 'Vocabulary')}${P.tag('GRAMMAR', 'Grammar')}</div>
                <div class="lp-rows">
                  <div class="lp-row">${P.check(true)}<span>Daily life: Campus Gym Email</span>${P.tag('READING', 'Reading')}</div>
                  <div class="lp-row">${P.check(false)}<span>Complete the Words</span>${P.tag('READING', 'Reading')}</div>
                  <div class="lp-row">${P.check(false)}<span>Academic talk: Biology</span>${P.tag('LISTENING', 'Listening')}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="lp-sec" id="features"><div class="lp-wrap">
        <h2 class="t-title-1">How it works</h2>
        <ol class="lp-steps">${steps.map(([ic, t, d], i) => `<li class="card lg"><span class="icon-box">${P.i(ic)}</span><div class="t-overline mt-3">Step ${i + 1}</div><div class="t-title-3">${t}</div><p class="muted t-caption mt-1">${d}</p></li>`).join('')}</ol>
      </div></section>

      <section class="lp-sec"><div class="lp-wrap">
        <h2 class="t-title-1">All 2026 task types</h2>
        <p class="muted mt-2" style="max-width:560px">Every task of the new format, plus grammar and vocabulary. Colors match the sections you see in your week.</p>
        <div class="lp-cloud mt-5">${cloud}</div>
      </div></section>

      <section class="lp-sec"><div class="lp-wrap lp-two">
        <div class="stack gap-3">
          <h2 class="t-title-1">Feedback you can act on</h2>
          <p class="muted">Comments sit right on your text. Speaking shows which words to fix. Your tutor can add their own notes next to the AI ones.</p>
          <ul class="bl-feats">${['Inline comments on essays and emails', 'Word-by-word pronunciation check', 'Attempt 2: rewrite and see the difference'].map((f) => `<li>${P.i('check', 'ic-14')}<span>${f}</span></li>`).join('')}</ul>
        </div>
        <div class="card lg lp-fb" aria-hidden="true">
          <div class="t-overline">Writing · Academic discussion</div>
          <p class="mt-2 lp-essay">I think remote work is good because people <mark class="lp-hl">is more productive</mark> at home. If I <mark class="lp-hl alt">will go</mark> to the office every day, I lose two hours.</p>
          <div class="lp-note"><span class="ai-badge">AI</span><span>Use <b>are</b> with the plural subject "people".</span></div>
          <div class="lp-note tutor">${P.tag('neutral', 'Anna K.', { mod: 'tutor' })}<span>After "if", use the present tense: "If I go".</span></div>
          <hr class="divider mt-3">
          <div class="t-overline mt-3">Speaking · Pronunciation</div>
          <p class="mt-2 t-body-lg">I <span class="lp-w ok">really</span> <span class="lp-w bad">think</span> that <span class="lp-w ok">students</span> <span class="lp-w bad">through</span> the year...</p>
        </div>
      </div></section>

      <section class="lp-sec" id="tutors"><div class="lp-wrap lp-two">
        <div class="card lg lp-tutor" aria-hidden="true">
          <div class="row between"><span class="t-strong">Students (8 of 10 seats)</span><span class="tag warn">${P.i('timer')}<span class="tx">Auto-publishes in 31:12:40</span></span></div>
          <div class="lp-trow"><span class="row gap-2">${P.avatar('MP', 'sm')}Maria P.</span><span class="grow">${P.bar(64, { thin: true, label: 'Progress' })}</span></div>
          <div class="lp-trow"><span class="row gap-2">${P.avatar('IS', 'sm')}Ivan S.</span><span class="grow">${P.bar(22, { thin: true, label: 'Progress' })}</span></div>
          <div class="lp-trow"><span class="row gap-2">${P.avatar('DK', 'sm')}Dmitri K.</span><span class="grow">${P.bar(71, { thin: true, label: 'Progress' })}</span></div>
        </div>
        <div class="stack gap-3">
          <h2 class="t-title-1">For tutors</h2>
          <p class="muted">Keep your students and your own books. Toefly Tutor drafts each week, you approve it or change it in 48 hours.</p>
          <ul class="bl-feats">${['A dashboard with progress, bands and who is falling behind', 'Week approval with drag-and-drop editing', 'Your own PDFs: assign page ranges or make them interactive'].map((f) => `<li>${P.i('check', 'ic-14')}<span>${f}</span></li>`).join('')}</ul>
          <div>${P.btn("I'm a tutor", { v: 'secondary', href: '/sign-in' })}</div>
        </div>
      </div></section>

      <section class="lp-sec" id="pricing"><div class="lp-wrap">
        <h2 class="t-title-1">Pricing</h2>
        <div class="lp-plans mt-5">${PLANS.map((p) => planCard(p, { action: P.btn(p.code === 'STUDENT_FREE' ? 'Start free' : p.code === 'STUDENT_PRO' ? 'Try Pro free' : 'Start as a tutor', { v: p.code === 'STUDENT_PRO' ? 'primary' : 'secondary', block: true, href: '/sign-in' }) })).join('')}</div>
        <p class="t-caption muted mt-3">Cancel anytime. Prices in EUR.</p>
      </div></section>

      <section class="lp-sec"><div class="lp-wrap lp-faq-wrap">
        <h2 class="t-title-1">FAQ</h2>
        <div class="lp-faq mt-4">${faq.map(([q, a]) => `<details><summary>${q}${P.i('chevron-down')}</summary><p class="muted">${a}</p></details>`).join('')}</div>
      </div></section>

      <footer class="lp-foot"><div class="lp-wrap stack gap-3">
        <div class="row gap-4 wrap"><a class="link" href="#/landing">Privacy</a><span class="muted">·</span><a class="link" href="#/landing">Terms</a><span class="muted">·</span><a class="link" href="#/landing">Contact</a><span class="muted">·</span><span class="muted">© 2026 TOEFLY</span></div>
        <p class="t-caption muted" style="max-width:640px">TOEFL and TOEFL iBT are registered trademarks of ETS. TOEFLY is not affiliated with or endorsed by ETS.</p>
      </div></footer>
    </div>`;
  }

  P.register('/landing', {
    title: 'Landing hero + pricing', num: 1, web: true, ios: false, layout: 'bare', fab: false,
    render: landing,
    mount(root) {
      const main = $(root, '#main');
      const on = (e) => {
        const a = e.target.closest('[data-scroll]');
        if (!a) return;
        e.preventDefault();
        const t = $(root, '#' + a.dataset.scroll);
        if (t) main.scrollTo({ top: t.offsetTop - 64, behavior: 'smooth' });
      };
      root.addEventListener('click', on);
      return () => root.removeEventListener('click', on);
    },
  });

  /* ══════════════════════ 2. Tutor dashboard ══════════════════════ */
  const STUDENTS = [
    { id: 'maria', name: 'Maria P.', ini: 'MP', week: 7, pct: 64, band: 4.5, target: 5.0, last: 'today', days: 0, flag: 'approve', subs: 1 },
    { id: 'ivan', name: 'Ivan S.', ini: 'IS', week: 3, pct: 22, band: 3.5, target: 4.5, last: '4 days ago', days: 4, flag: 'behind', subs: 0 },
    { id: 'dmitri', name: 'Dmitri K.', ini: 'DK', week: 5, pct: 71, band: 4.0, target: 5.0, last: 'yesterday', days: 1, flag: '', subs: 2 },
    { id: 'alina', name: 'Alina V.', ini: 'AV', week: 9, pct: 88, band: 5.0, target: 5.5, last: 'today', days: 0, flag: 'approve', subs: 0 },
    { id: 'pavel', name: 'Pavel R.', ini: 'PR', week: 2, pct: 35, band: 3.0, target: 4.0, last: '2 days ago', days: 2, flag: '', subs: 0 },
    { id: 'sofia', name: 'Sofia M.', ini: 'SM', week: 6, pct: 59, band: 4.5, target: 5.0, last: 'today', days: 0, flag: '', subs: 0 },
    { id: 'nikita', name: 'Nikita B.', ini: 'NB', week: 4, pct: 47, band: 3.5, target: 4.0, last: '3 days ago', days: 3, flag: 'behind', subs: 0 },
    { id: 'elena', name: 'Elena T.', ini: 'ET', week: 11, pct: 93, band: 5.5, target: 6.0, last: 'yesterday', days: 1, flag: '', subs: 0 },
  ];
  const FILTERS = [['all', 'All'], ['behind', 'Behind'], ['approve', 'Awaiting approval'], ['subs', 'New submissions']];
  const COLS = [['name', 'Name'], ['week', 'Week'], ['pct', 'Progress'], ['band', 'Band'], ['target', 'Target'], ['days', 'Last active']];

  function studentsTable(filter, sort) {
    let rows = STUDENTS.filter((s) => filter === 'all' || (filter === 'behind' && s.flag === 'behind') || (filter === 'approve' && s.flag === 'approve') || (filter === 'subs' && s.subs > 0));
    rows = rows.slice().sort((a, b) => {
      const k = sort.key;
      const r = typeof a[k] === 'string' ? a[k].localeCompare(b[k]) : a[k] - b[k];
      return sort.dir * r;
    });
    const flagCell = (s) => (s.flag === 'behind' ? `<span class="tag warn">${P.i('triangle-alert')}<span class="tx">Behind</span></span>`
      : s.flag === 'approve' ? `<span class="tag info">${P.i('clock')}<span class="tx">Week to approve</span></span>`
        : `<span class="muted" aria-hidden="true">—</span><span class="sr-only">No flags</span>`);
    const head = COLS.map(([k, label]) => `<th scope="col" aria-sort="${sort.key === k ? (sort.dir > 0 ? 'ascending' : 'descending') : 'none'}"><button type="button" class="st-sort" data-sort="${k}">${label}${P.i(sort.key === k ? (sort.dir > 0 ? 'arrow-up' : 'arrow-down') : 'arrow-up-down', 'ic-12')}</button></th>`).join('') + '<th scope="col">Flag</th>';
    const body = rows.length ? rows.map((s) => `<tr class="click" data-id="${s.id}">
        <td><span class="row gap-2">${P.avatar(s.ini, 'sm')}${s.id === 'maria' ? `<a class="t-strong" href="#/tutor/students/maria/weeks/8">${s.name}</a>` : `<span class="t-strong">${s.name}</span>`}</span></td>
        <td class="t-mono">${s.week}</td><td style="min-width:150px">${P.bar(s.pct, { thin: true, label: `${s.name} progress` })}</td>
        <td class="t-mono">${s.band.toFixed(1)}</td><td class="t-mono">${s.target.toFixed(1)}</td><td>${s.last}</td><td>${flagCell(s)}</td></tr>`).join('')
      : `<tr><td colspan="7">${P.empty({ title: 'No students match', text: 'Try another filter.' })}</td></tr>`;
    return `<table class="table st-table"><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`;
  }

  P.register('/tutor/students', {
    title: 'Tutor dashboard', num: 22, web: true, ios: false, role: 'tutor', nav: 'students', fab: false,
    render() {
      return `<div class="page wide">
        <div class="row between wrap gap-3">
          <h1 class="t-title-1">Students (8 of 10 seats)</h1>
          ${P.btn('Invite student', { icon: 'user-plus', attrs: 'data-invite' })}
        </div>
        <div class="row gap-2 wrap mt-4"><span class="t-overline">Needs attention</span>
          <a class="chip st-attn" href="#/tutor/students/maria/weeks/8">${P.i('clock')}2 weeks to approve</a>
          <a class="chip st-attn" href="#/review/writing">${P.i('pen-line')}3 essays to review</a></div>
        <div class="row gap-2 wrap mt-4" role="group" aria-label="Filter students">${FILTERS.map(([k, l], i) => `<button type="button" class="chip ${i === 0 ? 'on' : ''}" data-filter="${k}" aria-pressed="${i === 0}">${l}</button>`).join('')}</div>
        <div class="mt-4" id="st-wrap">${studentsTable('all', { key: 'name', dir: 1 })}</div>
      </div>`;
    },
    mount(root) {
      let filter = 'all';
      const sort = { key: 'name', dir: 1 };
      const wrap = $(root, '#st-wrap');
      const paint = () => { wrap.innerHTML = studentsTable(filter, sort); icons(); };
      const invite = () => P.openOverlay(dlg('Invite student',
        `<div class="stack gap-3">
          ${field('inv-link', 'Invite link', `<div class="row gap-2"><input class="input" id="inv-link" readonly value="app.toefly.example/join/K7F2-9QXA">${P.btn('Copy link', { v: 'secondary', icon: 'copy', attrs: 'data-copy="Link copied"' })}</div>`)}
          ${field('inv-code', 'Code', `<div class="row gap-2"><input class="input t-mono" id="inv-code" readonly value="K7F2-9QXA" style="letter-spacing:.08em;font-weight:600">${P.btn('Copy code', { v: 'secondary', icon: 'copy', attrs: 'data-copy="Code copied"' })}</div>`)}
          <div class="t-caption muted row gap-2">${P.i('clock', 'ic-14')}Expires in 7 days</div>
          <hr class="divider">
          ${field('inv-mail', 'Invite by email', `<div class="row gap-2"><input class="input" id="inv-mail" type="email" placeholder="student@example.com">${P.btn('Send invite', { attrs: 'data-send' })}</div>`)}
          <div class="stack gap-1"><div class="t-caption t-strong">8 of 10 seats used</div>${P.bar(80, { thin: true, pct: false, label: 'Seats used' })}</div>
        </div>`), {
        label: 'Invite student',
        mount(el) {
          const h = (e) => {
            const c = e.target.closest('[data-copy]');
            if (c) P.toast(c.dataset.copy);
            if (e.target.closest('[data-send]')) {
              const v = $(el, '#inv-mail').value.trim();
              if (!v) { $(el, '#inv-mail').focus(); return; }
              P.closeOverlay(); P.toast(`Invite sent to ${P.esc(v)}`);
            }
          };
          el.addEventListener('click', h);
          return () => el.removeEventListener('click', h);
        },
      });
      const h = (e) => {
        if (e.target.closest('[data-invite]')) return invite();
        const f = e.target.closest('[data-filter]');
        if (f) {
          filter = f.dataset.filter;
          $$(root, '[data-filter]').forEach((b) => { const on = b === f; b.classList.toggle('on', on); b.setAttribute('aria-pressed', on); });
          return paint();
        }
        const s = e.target.closest('[data-sort]');
        if (s) {
          const k = s.dataset.sort;
          if (sort.key === k) sort.dir *= -1; else { sort.key = k; sort.dir = 1; }
          return paint();
        }
        const tr = e.target.closest('tr[data-id]');
        if (tr && !e.target.closest('a')) {
          if (tr.dataset.id === 'maria') P.go('/tutor/students/maria/weeks/8');
          else P.toast(`${P.esc(STUDENTS.find((x) => x.id === tr.dataset.id).name)}: student card is not part of this prototype`, 'info');
        }
      };
      root.addEventListener('click', h);
      return () => root.removeEventListener('click', h);
    },
  });

  /* ══════════════════════ 3. Week editor ══════════════════════ */
  const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  const DAY_FULL = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
  const PLAN_MIN = 45;
  const SECTION_ORDER = ['READING', 'LISTENING', 'WRITING', 'SPEAKING', 'VOCABULARY', 'GRAMMAR', 'RESOURCES'];
  const WEEK_TASKS = () => [
    [0, 'Complete the Words', 'READING', 3], [0, 'Academic passage: Glaciers', 'READING', 9], [0, 'Academic talk: Astronomy', 'LISTENING', 9],
    [0, 'Build a Sentence: Practice 7', 'WRITING', 8], [0, 'Listen and Repeat: Campus tour', 'SPEAKING', 6], [0, '12 words to review', 'VOCABULARY', 5],
    [0, 'Collins Vocabulary Unit 8: Earth Science', 'VOCABULARY', 15, 'tutor'], [0, 'Collins Grammar Unit 8: Passive voice', 'GRAMMAR', 20, 'tutor'],
    [1, 'Academic passage: Urban Heat', 'READING', 9], [1, 'Conversation: Advisor Visit', 'LISTENING', 4], [1, 'Email: Late Assignment', 'WRITING', 7],
    [1, 'Take an Interview: Study habits', 'SPEAKING', 7], [1, '12 words to review', 'VOCABULARY', 5],
    [2, 'Build a Sentence: Practice 8', 'WRITING', 8], [2, 'Listen and Repeat: Library rules', 'SPEAKING', 6], [2, 'Round-Up Unit 8: Passive voice', 'GRAMMAR', 20, 'tutor'], [2, 'TSTP site', 'RESOURCES', 0, 'external'],
    [3, 'Read in Daily Life: Lab notice', 'READING', 4], [3, 'Announcement: Shuttle schedule', 'LISTENING', 3], [3, 'Academic discussion: Remote work', 'WRITING', 10],
    [3, 'Pronunciation drill: /θ/ sounds', 'SPEAKING', 8], [3, '12 words to review', 'VOCABULARY', 5],
    [4, 'Mock exam: Reading and Listening', 'RESOURCES', 55, 'exam'],
    [5, 'Mock exam: Writing and Speaking', 'RESOURCES', 50, 'exam'],
    [6, 'Weekly review with Toefly Tutor', 'RESOURCES', 10], [6, 'Attempt 2: Academic discussion', 'WRITING', 10],
  ].map(([day, title, sec, min, mod], i) => ({ id: 't' + i, day, title, sec, min, mod: mod || '' }));
  const BANK = [
    ['Build a Sentence: Practice 9', 'WRITING', 8], ['Listen and Repeat: Dormitory rules', 'SPEAKING', 6], ['Academic passage: Coral Reefs', 'READING', 9],
    ['Grammar exercise: Passive voice', 'GRAMMAR', 12], ['Vocabulary exercise: Life science', 'VOCABULARY', 8], ['Conversation: Office hours', 'LISTENING', 4],
  ];
  const REPLACE = { READING: 'Read in Daily Life: Bookstore Sale', LISTENING: 'Conversation: Study Group', WRITING: 'Email: Course Change', SPEAKING: 'Take an Interview: Campus life', VOCABULARY: '12 words to review', GRAMMAR: 'Grammar exercise: Articles', RESOURCES: 'Collins Voc Gramm' };

  const secTag = (t, o = {}) => (t.mod === 'exam' ? P.tag('RESOURCES', 'Mock exam', { mod: 'exam' }) : P.tag(t.sec, P.sec(t.sec).label, { mod: t.mod === 'tutor' || t.mod === 'external' ? t.mod : '', ...o }));
  const fmt = (s) => [Math.floor(s / 3600), Math.floor((s % 3600) / 60), s % 60].map((n) => String(n).padStart(2, '0')).join(':');

  P.register('/tutor/students/maria/weeks/8', {
    back: { label: 'Students', href: '/tutor/students' },
    title: 'Tutor week editor (48 h timer)', num: 22, web: true, ios: false, role: 'tutor', nav: 'plans', fab: false,
    render() {
      return `<div class="page wide we">
        <a class="we-back" href="#/tutor/students">${P.i('chevron-left', 'ic-14')}Students</a>
        <div class="row between wrap gap-3 mt-2">
          <div class="row gap-3 wrap"><h1 class="t-title-1">Maria P. · Week 8 draft</h1><span id="we-badges" class="row gap-2"></span></div>
          <span id="we-timer-slot"></span>
        </div>
        <div class="row gap-2 wrap mt-4" id="we-actions"></div>
        <div class="we-why card mt-4"><span class="icon-box">${P.i('sparkles')}</span><div><div class="t-strong">Why this week (AI)</div>
          <p class="muted t-caption mt-1" style="max-width:820px">Focus on Build a Sentence and /θ/ sounds. In Maria's last 10 sentences, 4 put "not" in the wrong place, and the pronunciation check flagged /θ/ in "think" and "through" twice. Week 8 is a mock exam week, so the exams sit on Friday and Saturday.</p></div></div>
        <div id="we-warn" class="mt-3"></div>
        <div class="row between wrap gap-3 mt-4"><div class="seg" role="group" aria-label="View"><button type="button" class="on" data-view="day" aria-pressed="true">By day</button><button type="button" data-view="table" aria-pressed="false">Table</button></div>
          <span class="t-caption muted">Drag the handle to another day, or use the task menu: Move to day.</span></div>
        <div class="mt-3" id="we-main"></div>
        <div class="row gap-2 wrap mt-5"><span class="t-strong">Add:</span>
          ${P.btn('From bank', { v: 'secondary', size: 'sm', icon: 'database', attrs: 'data-add="bank"' })}${P.btn('Create task', { v: 'secondary', size: 'sm', icon: 'square-pen', attrs: 'data-add="create"' })}${P.btn('From materials (page range)', { v: 'secondary', size: 'sm', icon: 'file-text', href: '/tutor/materials/1' })}${P.btn('Link', { v: 'secondary', size: 'sm', icon: 'link', attrs: 'data-add="link"' })}</div>
        <div class="sr-only" id="we-live" aria-live="polite"></div>
      </div>`;
    },
    mount(root) {
      let tasks = WEEK_TASKS();
      let view = 'day', published = false, secs = 31 * 3600 + 12 * 60 + 40, menu = null, seq = 100, dragOff = null;
      const main = $(root, '#we-main');
      const live = (t) => { $(root, '#we-live').textContent = t; };

      const taskCard = (t) => {
        const open = menu && menu.id === t.id;
        return `<div class="we-task ${t.mod === 'exam' ? 'exam' : ''}" data-id="${t.id}">
          <button type="button" class="we-grip" data-grip="${t.id}" aria-label="Drag ${P.esc(t.title)} to another day. Keyboard: open the task menu and choose Move to day">${P.i('grip-vertical', 'ic-14')}</button>
          <div class="we-tt"><div class="we-title">${P.esc(t.title)}</div><div class="row gap-1 wrap">${secTag(t)}${t.min ? `<span class="t-micro muted">${t.min} min</span>` : ''}</div></div>
          <button type="button" class="we-more" data-more="${t.id}" aria-haspopup="menu" aria-expanded="${!!open}" aria-label="Actions for ${P.esc(t.title)}">${P.i('ellipsis', 'ic-14')}</button>
          ${open ? taskMenu(t) : ''}</div>`;
      };
      const taskMenu = (t) => (menu.sub
        ? `<div class="we-menu" role="menu" aria-label="Move to day"><div class="we-menu-h">Move to day</div>${DAYS.map((d, i) => `<button type="button" role="menuitem" data-moveto="${i}" ${i === t.day ? 'disabled' : ''}>${DAY_FULL[i]}</button>`).join('')}</div>`
        : `<div class="we-menu" role="menu" aria-label="Task actions"><button type="button" role="menuitem" data-mact="replace">${P.i('repeat', 'ic-14')}Replace</button><button type="button" role="menuitem" data-mact="move">${P.i('calendar-days', 'ic-14')}Move to day</button><button type="button" role="menuitem" data-mact="remove" class="danger">${P.i('trash-2', 'ic-14')}Remove</button></div>`);

      const load = (d) => tasks.filter((t) => t.day === d && t.mod !== 'exam').reduce((s, t) => s + t.min, 0);
      const board = () => `<div class="we-board">${DAYS.map((d, i) => {
        const l = load(i), over = l > PLAN_MIN;
        const list = tasks.filter((t) => t.day === i);
        return `<section class="we-col" data-day="${i}" aria-label="${DAY_FULL[i]}"><header class="${over ? 'over' : ''}"><span class="t-strong">${d}</span><span class="t-micro">${over ? P.i('triangle-alert', 'ic-12') : ''}${l} min</span></header>
          <div class="we-list">${list.map(taskCard).join('') || '<div class="we-empty t-caption muted">No tasks</div>'}</div>
          <button type="button" class="we-addbtn" data-add="bank" data-day="${i}">${P.i('plus', 'ic-14')}Add task</button></section>`;
      }).join('')}</div>`;
      const table = () => `<div style="overflow:auto"><table class="table we-table"><thead><tr><th scope="col">#</th>${SECTION_ORDER.map((s) => `<th scope="col">${P.sec(s).label}</th>`).join('')}</tr></thead><tbody>${DAYS.map((d, i) => `<tr><th scope="row">${d}</th>${SECTION_ORDER.map((s) => `<td><div class="stack gap-1">${tasks.filter((t) => t.day === i && t.sec === s).map((t) => secTagCell(t)).join('')}</div></td>`).join('')}</tr>`).join('')}</tbody></table></div>`;
      const secTagCell = (t) => (t.mod === 'exam' ? P.tag('RESOURCES', t.title, { cell: true, mod: 'exam' }) : P.tag(t.sec, t.title, { cell: true, mod: t.mod === 'tutor' || t.mod === 'external' ? t.mod : '' }));

      const warn = () => {
        const bad = DAYS.map((d, i) => [i, load(i)]).filter(([, l]) => l > PLAN_MIN);
        $(root, '#we-warn').innerHTML = bad.length ? P.banner('warning', 'triangle-alert', bad.map(([i, l]) => `${DAY_FULL[i]}: ${l} min (student plans ${PLAN_MIN})`).join('<br>')) : '';
      };
      const paintMain = () => { main.innerHTML = view === 'day' ? board() : table(); warn(); icons(); };
      const paintHead = () => {
        const warnT = secs < 6 * 3600;
        $(root, '#we-badges').innerHTML = `${P.tag('RESOURCES', 'Mock exam', { mod: 'exam' })}${published ? P.tag('accent', 'Published', { done: true }) : P.tag('warn', 'Draft')}`;
        $(root, '#we-timer-slot').innerHTML = published ? '' : `<span class="timer ${warnT ? 'warning' : ''}" id="we-timer" role="timer">${P.i('timer')}<span class="tl">Auto-publishes in</span><span class="tv">${fmt(secs)}</span></span>`;
        $(root, '#we-actions').innerHTML = `${P.btn('Approve week', { icon: 'check', attrs: `data-act="approve" ${published ? 'disabled' : ''}` })}${P.btn('Save changes', { v: 'secondary', icon: 'save', attrs: 'data-act="save"' })}${P.btn('Regenerate…', { v: 'ghost', icon: 'refresh-cw', attrs: `data-act="regen" ${published ? 'disabled' : ''}` })}`;
        icons();
      };
      const publish = (auto) => { published = true; paintHead(); P.toast('Week 8 published'); if (auto) live('Week 8 auto-published'); };
      paintHead(); paintMain();

      const tick = setInterval(() => {
        if (published) return;
        secs = Math.max(0, secs - 1);
        const el = $(root, '#we-timer');
        if (el) { $(el, '.tv').textContent = fmt(secs); el.classList.toggle('warning', secs < 6 * 3600); }
        if (secs === 0) publish(true);
      }, 1000);

      const moveTask = (id, day) => {
        const t = tasks.find((x) => x.id === id); if (!t || t.day === day) return;
        t.day = day; menu = null; paintMain();
        P.toast(`Moved to ${DAY_FULL[day]}`); live(`${t.title} moved to ${DAY_FULL[day]}`);
      };
      const addTask = (title, sec, min, day, mod = '') => { tasks.push({ id: 't' + ++seq, day, title, sec, min, mod }); paintMain(); P.toast(`Added to ${DAY_FULL[day]}`); };

      const addDialog = (kind, day) => {
        const dayField = field('add-day', 'Day', selectOf('add-day', DAY_FULL, DAY_FULL[day]));
        const body = kind === 'link'
          ? `<div class="stack gap-3">${field('lk-url', 'URL', '<input class="input" id="lk-url" type="url" placeholder="https://">')}${field('lk-title', 'Title', '<input class="input" id="lk-title" placeholder="For example, TSTP site">')}${field('lk-sec', 'Section', selectOf('lk-sec', SECTION_ORDER.map((s) => P.sec(s).label), 'Resources'))}${dayField}</div>`
          : `<div class="stack gap-3">${field('bk-q', 'Search the bank', '<input class="input" id="bk-q" type="search" placeholder="Task type, topic or difficulty">')}<div class="list card" style="padding:0;max-height:220px;overflow:auto" id="bk-list"></div>${dayField}</div>`;
        P.openOverlay(dlg(kind === 'link' ? 'Add a link' : 'Add from bank', body, kind === 'link' ? `${P.btn('Cancel', { v: 'ghost', attrs: 'data-action="close"' })}${P.btn('Add link', { attrs: 'data-ok' })}` : ''), {
          label: kind === 'link' ? 'Add a link' : 'Add from bank', width: 520,
          mount(el) {
            const dayIdx = () => DAY_FULL.indexOf($(el, '#add-day').value);
            const list = () => {
              const q = $(el, '#bk-q').value.toLowerCase();
              $(el, '#bk-list').innerHTML = BANK.map((b, i) => [b, i]).filter(([b]) => b[0].toLowerCase().includes(q) || P.sec(b[1]).label.toLowerCase().includes(q))
                .map(([b, i]) => `<div class="list-row click" data-bank="${i}" tabindex="0" role="button">${P.tag(b[1], P.sec(b[1]).label)}<span class="grow">${b[0]}</span><span class="t-caption muted">${b[2]} min</span></div>`).join('') || '<div class="list-row muted">Nothing found</div>';
              icons();
            };
            if (kind !== 'link') { list(); $(el, '#bk-q').addEventListener('input', list); }
            const pick = (b) => { P.closeOverlay(); addTask(b[0], b[1], b[2], dayIdx()); };
            const h = (e) => {
              const r = e.target.closest('[data-bank]'); if (r) return pick(BANK[+r.dataset.bank]);
              if (e.target.closest('[data-ok]')) {
                const url = $(el, '#lk-url').value.trim(), title = $(el, '#lk-title').value.trim() || url;
                if (!url) return $(el, '#lk-url').focus();
                const sec = SECTION_ORDER.find((s) => P.sec(s).label === $(el, '#lk-sec').value) || 'RESOURCES';
                P.closeOverlay(); addTask(title, sec, 0, dayIdx(), 'external');
              }
            };
            const k = (e) => { if (e.key === 'Enter' && e.target.matches('[data-bank]')) pick(BANK[+e.target.dataset.bank]); };
            el.addEventListener('click', h); el.addEventListener('keydown', k);
            return () => { el.removeEventListener('click', h); el.removeEventListener('keydown', k); };
          },
        });
      };

      const regen = () => P.openOverlay(dlg('Regenerate Week 8',
        `<p class="muted">Toefly Tutor builds a new draft. Your current changes are replaced and the 48-hour timer starts again.</p>${field('rg-c', 'Comment for the AI', '<textarea class="input" id="rg-c" rows="4" placeholder="For example: fewer Grammar tasks on Monday, more Speaking"></textarea>')}`,
        `${P.btn('Cancel', { v: 'ghost', attrs: 'data-action="close"' })}${P.btn('Regenerate week', { icon: 'refresh-cw', attrs: 'data-ok' })}`), {
        label: 'Regenerate Week 8',
        mount(el) {
          const h = (e) => { if (e.target.closest('[data-ok]')) { P.closeOverlay(); tasks = WEEK_TASKS(); secs = 48 * 3600; menu = null; paintHead(); paintMain(); P.toast('Regenerating Week 8 with your comment', 'sparkles'); } };
          el.addEventListener('click', h); return () => el.removeEventListener('click', h);
        },
      });

      /* drag and drop between days (pointer); keyboard alternative is the task menu */
      const startDrag = (e, id) => {
        e.preventDefault();
        const card = $(root, `.we-task[data-id="${id}"]`); if (!card) return;
        const ar = root.getBoundingClientRect(), sc = ar.width / root.offsetWidth || 1, cr = card.getBoundingClientRect();
        const offX = e.clientX - cr.left, offY = e.clientY - cr.top, sx = e.clientX, sy = e.clientY;
        let ghost = null, over = null, started = false;
        const move = (ev) => {
          if (!started) {
            if (Math.hypot(ev.clientX - sx, ev.clientY - sy) < 5) return;
            started = true; menu = null;
            ghost = card.cloneNode(true); ghost.classList.add('we-ghost'); ghost.style.width = cr.width / sc + 'px'; root.appendChild(ghost); card.classList.add('dragging');
          }
          ghost.style.left = (ev.clientX - offX - ar.left) / sc + 'px'; ghost.style.top = (ev.clientY - offY - ar.top) / sc + 'px';
          const el = document.elementFromPoint(ev.clientX, ev.clientY), col = el && el.closest ? el.closest('.we-col') : null;
          if (col !== over) { if (over) over.classList.remove('drop'); over = col; if (over) over.classList.add('drop'); }
        };
        const up = () => {
          window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); window.removeEventListener('pointercancel', up); dragOff = null;
          if (ghost) ghost.remove(); card.classList.remove('dragging'); if (over) over.classList.remove('drop');
          if (started && over) moveTask(id, +over.dataset.day);
        };
        window.addEventListener('pointermove', move); window.addEventListener('pointerup', up); window.addEventListener('pointercancel', up);
        dragOff = () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); window.removeEventListener('pointercancel', up); if (ghost) ghost.remove(); };
      };

      const focusMenu = (id) => { const b = $(root, `.we-task[data-id="${id}"] .we-menu button:not([disabled])`); if (b) b.focus(); };
      const h = (e) => {
        const t = e.target;
        const more = t.closest('[data-more]');
        if (more) { const id = more.dataset.more; menu = menu && menu.id === id ? null : { id, sub: false }; paintMain(); if (menu) focusMenu(id); else { const b = $(root, `[data-more="${id}"]`); if (b) b.focus(); } return; }
        const ma = t.closest('[data-mact]');
        if (ma && menu) {
          const task = tasks.find((x) => x.id === menu.id);
          if (ma.dataset.mact === 'move') { menu.sub = true; paintMain(); return focusMenu(menu.id); }
          if (ma.dataset.mact === 'remove') { tasks = tasks.filter((x) => x !== task); menu = null; paintMain(); P.toast('Task removed'); return live(`${task.title} removed`); }
          if (ma.dataset.mact === 'replace') { task.title = REPLACE[task.sec]; task.mod = ''; menu = null; paintMain(); return P.toast('Task replaced from the bank'); }
        }
        const mt = t.closest('[data-moveto]');
        if (mt && menu) return moveTask(menu.id, +mt.dataset.moveto);
        if (menu && !t.closest('.we-menu')) { menu = null; paintMain(); }
        const v = t.closest('[data-view]');
        if (v) { view = v.dataset.view; $$(root, '[data-view]').forEach((b) => { b.classList.toggle('on', b === v); b.setAttribute('aria-pressed', b === v); }); return paintMain(); }
        const ad = t.closest('[data-add]');
        if (ad) { const k = ad.dataset.add; if (k === 'create') return P.toast('Task creator opens here (not part of this prototype)', 'info'); return addDialog(k, ad.dataset.day ? +ad.dataset.day : 0); }
        const a = t.closest('[data-act]');
        if (a) {
          if (a.dataset.act === 'approve') return publish(false);
          if (a.dataset.act === 'save') return P.toast('Draft saved. The timer keeps running.');
          if (a.dataset.act === 'regen') return regen();
        }
      };
      const kd = (e) => {
        if (!menu) return;
        if (e.key === 'Escape') { const id = menu.id; menu = null; paintMain(); const b = $(root, `[data-more="${id}"]`); if (b) b.focus(); e.stopPropagation(); }
        if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
          const items = $$(root, '.we-menu button:not([disabled])'); if (!items.length) return;
          const i = items.indexOf(document.activeElement); items[(i + (e.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length].focus(); e.preventDefault();
        }
      };
      const pd = (e) => { const g = e.target.closest('[data-grip]'); if (g && e.button === 0) startDrag(e, g.dataset.grip); };
      root.addEventListener('click', h); root.addEventListener('keydown', kd); root.addEventListener('pointerdown', pd);
      return () => { clearInterval(tick); if (dragOff) dragOff(); root.removeEventListener('click', h); root.removeEventListener('keydown', kd); root.removeEventListener('pointerdown', pd); };
    },
  });

  /* ══════════════════════ 4–5. Materials ══════════════════════ */
  const MATERIALS = [
    { id: 1, title: 'New Round-Up 5', pages: 214, size: '38 MB', date: 'Sep 2', kind: 0 },
    { id: 2, title: '100 Questions: Reading 2026', pages: 132, size: '21 MB', date: 'Sep 9', kind: 1 },
    { id: 3, title: 'Collins Vocabulary Unit Book', pages: 168, size: '27 MB', date: 'Sep 14', kind: 2 },
    { id: 4, title: 'Fill in the Letters 146', pages: 40, size: '6 MB', date: 'Sep 21', kind: 0 },
    { id: 5, title: 'Listening 2026 Transcripts', pages: 96, size: '14 MB', date: 'Oct 1', kind: 1 },
    { id: 6, title: 'Academic Word List Workbook', pages: 72, size: '9 MB', date: 'Oct 6', kind: 2 },
  ];
  /* a PDF page is always a light sheet with grey lines, in both themes */
  const widths = (n, k) => { let s = n * 9301 + 49297; return Array.from({ length: k }, () => { s = (s * 9301 + 49297) % 233280; return 55 + Math.floor((s / 233280) * 45); }); };
  const sheetMini = (n, kind = n % 3) => `<span class="sheet-mini k${kind}" aria-hidden="true"><i class="sh-h"></i>${widths(n, 7).map((w, i) => (kind === 0 && i === 4 ? '<b class="sh-block"></b>' : `<i style="width:${w}%"></i>`)).join('')}</span>`;

  P.register('/tutor/materials', {
    title: 'Materials library', num: 'plan', web: true, ios: false, role: 'tutor', nav: 'materials', fab: false,
    render() {
      return `<div class="page wide">
        <div class="row between wrap gap-3"><h1 class="t-title-1">Materials</h1>${P.btn('Upload PDF', { icon: 'upload', attrs: 'data-upload' })}</div>
        <div class="mat-grid mt-5" id="mat-grid">${MATERIALS.map((m) => `<a class="card mat-card click" href="#/tutor/materials/1" aria-label="${m.title}, ${m.pages} pages">
          <div class="mat-prev">${sheetMini(m.id * 7, m.kind)}</div><div class="t-strong mt-3">${m.title}</div><div class="t-caption muted">${m.pages} pages · ${m.size}</div><div class="t-caption subtle">Uploaded ${m.date}</div></a>`).join('')}</div>
        <div class="mat-drop mt-6" id="mat-drop" tabindex="0" role="button" aria-label="Upload PDF">
          ${P.i('file-up', 'ic-24')}<div class="t-title-3">Drop a PDF here</div><div class="muted t-caption">PDF up to 200 MB. Then add page ranges to any week.</div>${P.btn('Upload PDF', { v: 'secondary', size: 'sm', icon: 'upload' })}</div>
      </div>`;
    },
    mount(root) {
      const timers = [];
      const upload = () => {
        const grid = $(root, '#mat-grid');
        const id = 'up' + Date.now();
        grid.insertAdjacentHTML('afterbegin', `<div class="card mat-card" id="${id}"><div class="mat-prev"><div class="skel" style="height:100%;width:100%"></div></div><div class="t-strong mt-3">Round-Up Extra Practice.pdf</div><div class="t-caption muted" data-st>Uploading…</div>${P.bar(0, { thin: true, pct: false, label: 'Upload' })}</div>`);
        icons();
        const card = $(root, '#' + id), bar = $(card, '.bar i'), st = $(card, '[data-st]');
        let p = 0;
        const iv = setInterval(() => {
          p += 12; bar.style.width = Math.min(p, 100) + '%'; bar.style.animation = 'none';
          if (p >= 100) { clearInterval(iv); st.textContent = 'Processing pages…'; timers.push(setTimeout(() => { card.outerHTML = `<a class="card mat-card click" href="#/tutor/materials/1"><div class="mat-prev">${sheetMini(77, 1)}</div><div class="t-strong mt-3">Round-Up Extra Practice</div><div class="t-caption muted">64 pages · 11 MB</div><div class="t-caption subtle">Uploaded today</div></a>`; P.toast('Round-Up Extra Practice is ready'); }, 1400)); }
        }, 220);
        timers.push(iv);
      };
      const h = (e) => { if (e.target.closest('[data-upload]') || e.target.closest('#mat-drop')) upload(); };
      const k = (e) => { if (e.key === 'Enter' && e.target.id === 'mat-drop') upload(); };
      root.addEventListener('click', h); root.addEventListener('keydown', k);
      return () => { timers.forEach((t) => { clearTimeout(t); clearInterval(t); }); root.removeEventListener('click', h); root.removeEventListener('keydown', k); };
    },
  });

  const TOTAL_PAGES = 214;
  const bigPage = (n, hit) => {
    const kind = n % 3, w = widths(n + 5, 24);
    const lines = (a, b) => w.slice(a, b).map((x, i) => `<i style="width:${x}%" class="${hit && a + i === 3 ? 'hit' : ''}"></i>`).join('');
    return `<div class="sh-run"><span>New Round-Up 5</span><span>Unit ${Math.ceil(n / 12)}</span></div>
      <div class="sh-title"></div>
      ${kind === 1 ? `<div class="sh-par">${lines(0, 9)}</div><div class="sh-fig"></div><div class="sh-par">${lines(9, 14)}</div>`
        : `<div class="sh-par">${lines(0, 6)}</div><div class="sh-ex"><b>Exercise ${n % 9 + 1}</b><div class="sh-par">${lines(6, 9)}</div>
        ${[1, 2, 3, 4].map((q) => `<div class="sh-q"><span>${q}</span><i style="width:${w[10 + q]}%"></i><em></em></div>`).join('')}</div><div class="sh-par">${lines(14, 19)}</div>`}
      <div class="sh-num">${n}</div>`;
  };

  P.register('/tutor/materials/1', {
    back: { label: 'Materials', href: '/tutor/materials' },
    title: 'Materials: PDF viewer with page range', num: 23, web: true, ios: false, role: 'tutor', nav: 'materials', fab: false,
    render() {
      return `<div class="pv" data-page>
        <div class="pv-top">
          <div class="row gap-3"><div><div class="t-title-3">New Round-Up 5</div><div class="t-caption muted">${TOTAL_PAGES} pages · 38 MB</div></div></div>
          <div class="row gap-3 wrap">
            <div class="row gap-2"><label class="t-caption t-strong" for="pv-from">From</label><input class="input pv-num" id="pv-from" type="number" min="1" max="${TOTAL_PAGES}" value="12"><label class="t-caption t-strong" for="pv-to">To</label><input class="input pv-num" id="pv-to" type="number" min="1" max="${TOTAL_PAGES}" value="15"></div>
            <div class="row gap-1" role="group" aria-label="Zoom">${P.btn('Zoom out', { v: 'icon', icon: 'zoom-out', size: 'sm', attrs: 'data-zoom="-1"' })}<span class="t-caption t-strong t-mono pv-zv" id="pv-zv" style="min-width:42px;text-align:center">100%</span>${P.btn('Zoom in', { v: 'icon', icon: 'zoom-in', size: 'sm', attrs: 'data-zoom="1"' })}</div>
            <div class="pv-search">${P.i('search', 'ic-14')}<input class="input" id="pv-q" type="search" placeholder="Search in text" aria-label="Search in text"><span class="t-caption muted" id="pv-hits"></span></div>
          </div>
        </div>
        <div class="pv-body"><div class="pv-thumbs" id="pv-thumbs" role="group" aria-label="Pages. Click the first page, then Shift+click the last page to select a range">${Array.from({ length: TOTAL_PAGES }, (_, i) => `<button type="button" class="pv-thumb" data-p="${i + 1}" aria-label="Page ${i + 1}" aria-pressed="false">${sheetMini(i + 1)}<span class="pv-pn">${i + 1}</span></button>`).join('')}</div>
          <div class="pv-stage" id="pv-stage"><div class="pv-big" id="pv-big"></div></div></div>
        <div class="pv-panel card" id="pv-panel"></div>
      </div>`;
    },
    mount(root) {
      let from = 12, to = 15, cur = 12, anchor = 12, zoom = 1, mode = 'select', hits = [];
      const timers = [];
      const thumbs = $$(root, '.pv-thumb'), thumbBox = $(root, '#pv-thumbs');
      const clamp = (n) => Math.max(1, Math.min(TOTAL_PAGES, Math.round(n) || 1));
      const label = () => (from === to ? `Page ${from} selected` : `Pages ${from}–${to} selected`);
      const range = () => (from === to ? `page ${from}` : `pages ${from}–${to}`);

      const paintPanel = () => {
        const p = $(root, '#pv-panel');
        if (mode === 'progress') p.innerHTML = `<div class="row gap-3"><span class="icon-box">${P.i('loader-circle', 'pv-spin')}</span><div class="grow"><div class="t-strong">Finding exercises on ${range()}…</div>${P.bar(0, { thin: true, pct: false, label: 'Extraction progress' }).replace('<i ', '<i class="pv-prog" ')}</div></div>`;
        else if (mode === 'result') p.innerHTML = `<div class="row gap-3 wrap"><span class="icon-box">${P.i('circle-check')}</span><div class="grow"><div class="t-strong">Found 3 exercises on ${range()}</div><div class="t-caption muted">Exercise 4 · Multiple choice, 6 questions · Exercise 5 · Fill in the blanks, 8 · Exercise 6 · True or false, 5</div></div>${P.btn('Review exercises', { size: 'sm', attrs: 'data-act="review"' })}${P.btn('Done', { v: 'ghost', size: 'sm', attrs: 'data-act="done"' })}</div>`;
        else p.innerHTML = `<div class="row between wrap gap-3"><div class="row gap-3"><span class="icon-box">${P.i('files')}</span><div><div class="t-strong" id="pv-sel">${label()}</div><div class="t-caption muted">Click the first page, then Shift+click the last page.</div></div></div>
          <div class="row gap-2">${P.btn('Add to week…', { icon: 'calendar-plus', attrs: 'data-act="add"' })}${P.btn('Make interactive', { v: 'secondary', icon: 'wand-sparkles', attrs: 'data-act="interactive"' })}</div></div>`;
        icons();
      };
      const paintStage = () => {
        $(root, '#pv-big').innerHTML = bigPage(cur, hits.includes(cur));
        $(root, '#pv-big').style.setProperty('--z', zoom);
        $(root, '#pv-zv').textContent = Math.round(zoom * 100) + '%';
      };
      const paintThumbs = () => {
        thumbs.forEach((t, i) => {
          const n = i + 1, inr = n >= from && n <= to;
          t.classList.toggle('in', inr); t.classList.toggle('edge', n === from || n === to); t.classList.toggle('cur', n === cur); t.setAttribute('aria-pressed', inr);
        });
        $(root, '#pv-from').value = from; $(root, '#pv-to').value = to;
        const sel = $(root, '#pv-sel'); if (sel) sel.textContent = label();
      };
      const reveal = (n) => { const t = thumbs[n - 1]; thumbBox.scrollTop = Math.max(0, t.offsetTop - thumbBox.clientHeight / 2 + t.offsetHeight / 2); };
      const select = (a, b) => { from = Math.min(a, b); to = Math.max(a, b); paintThumbs(); };
      paintPanel(); paintThumbs(); paintStage(); reveal(12);

      const addDialog = () => P.openOverlay(dlg('Add to week', `<div class="stack gap-3">
          <div class="banner info">${P.i('files')}<div>${label().replace(' selected', '')} of New Round-Up 5</div></div>
          ${field('aw-st', 'Student', selectOf('aw-st', ['Maria P.', 'Ivan S.', 'Dmitri K.'], 'Maria P.'))}
          <div class="row gap-3"><div class="field grow"><label for="aw-wk">Week</label>${selectOf('aw-wk', ['Week 8 (draft)', 'Week 9', 'Week 10'], 'Week 8 (draft)')}</div><div class="field grow"><label for="aw-day">Day</label>${selectOf('aw-day', DAY_FULL, 'Tuesday')}</div></div>
          ${field('aw-sec', 'Section', selectOf('aw-sec', SECTION_ORDER.map((s) => P.sec(s).label), 'Reading'))}
          ${field('aw-in', 'Instruction for the student', '<textarea class="input" id="aw-in" rows="3">Read the text and write out all new words</textarea>')}</div>`,
        `${P.btn('Cancel', { v: 'ghost', attrs: 'data-action="close"' })}${P.btn('Add to week', { attrs: 'data-ok' })}`), {
        label: 'Add to week', width: 520,
        mount(el) {
          const h = (e) => { if (e.target.closest('[data-ok]')) { const st = $(el, '#aw-st').value, wk = $(el, '#aw-wk').value.replace(' (draft)', ''); P.closeOverlay(); P.toast(`${label().replace(' selected', '')} added to ${wk} of ${st}`); } };
          el.addEventListener('click', h); return () => el.removeEventListener('click', h);
        },
      });

      const h = (e) => {
        const t = e.target;
        const th = t.closest('.pv-thumb');
        if (th) {
          const n = +th.dataset.p; cur = n;
          if (e.shiftKey) select(anchor, n); else { anchor = n; select(n, n); }
          return paintStage();
        }
        const z = t.closest('[data-zoom]');
        if (z) { zoom = Math.max(0.5, Math.min(2, zoom + (+z.dataset.zoom) * 0.25)); return paintStage(); }
        const a = t.closest('[data-act]'); if (!a) return;
        const act = a.dataset.act;
        if (act === 'add') return addDialog();
        if (act === 'interactive') {
          mode = 'progress'; paintPanel();
          const bar = $(root, '.pv-prog'); if (bar) { bar.style.animation = 'none'; bar.style.width = '0%'; requestAnimationFrame(() => { bar.style.transition = 'width 2.4s linear'; bar.style.width = '100%'; }); }
          timers.push(setTimeout(() => { mode = 'result'; paintPanel(); }, 2500)); return;
        }
        if (act === 'review') return P.toast('Extraction review is not part of this prototype', 'info');
        if (act === 'done') { mode = 'select'; paintPanel(); }
      };
      const onNum = (e) => {
        if (e.target.id !== 'pv-from' && e.target.id !== 'pv-to') return;
        const a = clamp($(root, '#pv-from').value), b = clamp($(root, '#pv-to').value);
        anchor = a; cur = e.target.id === 'pv-from' ? a : b; select(a, b); paintStage(); reveal(cur);
      };
      const onSearch = (e) => {
        if (e.target.id !== 'pv-q') return;
        const q = e.target.value.trim();
        hits = q ? [12, 14, 31, 88] : [];
        $(root, '#pv-hits').textContent = q ? `${hits.length} matches` : '';
        paintStage();
      };
      const onKey = (e) => {
        if (e.target.id === 'pv-q' && e.key === 'Enter' && hits.length) { cur = hits[(Math.max(0, hits.indexOf(cur)) + (hits.includes(cur) ? 1 : 0)) % hits.length]; paintThumbs(); paintStage(); reveal(cur); }
      };
      root.addEventListener('click', h); root.addEventListener('change', onNum); root.addEventListener('input', onSearch); root.addEventListener('keydown', onKey);
      return () => { timers.forEach(clearTimeout); root.removeEventListener('click', h); root.removeEventListener('change', onNum); root.removeEventListener('input', onSearch); root.removeEventListener('keydown', onKey); };
    },
  });

  /* ══════════════════════ 6. Billing ══════════════════════ */
  const payRows = (rows) => `<div style="overflow:auto"><table class="table bl-table"><thead><tr><th scope="col">Date</th><th scope="col">Amount</th><th scope="col">Status</th><th scope="col"><span class="sr-only">Receipt</span></th></tr></thead><tbody>${rows.map(([d, a, s, k]) => `<tr><td>${d}</td><td class="t-mono">${a}</td><td>${P.tag(k, s)}</td><td style="text-align:right">${a === '€0.00' ? '' : P.btn('Receipt', { v: 'ghost', size: 'sm', icon: 'receipt', attrs: 'data-receipt' })}</td></tr>`).join('')}</tbody></table></div>`;

  function checkoutResult(ctx) {
    const unlocked = ['A new week every week, built from your mistakes', 'Unlimited AI checks for Writing and Speaking', 'Adaptive mock exams with 1–6 and 0–120 scores', 'Toefly Tutor chat and weekly reviews'];
    return `<div class="page narrow"><div class="bl-done card lg">
      <span class="bl-done-ic">${P.i('circle-check', 'ic-64')}</span>
      <h1 class="t-title-1">You're on Pro</h1>
      <p class="muted">Your payment went through. Here is what is unlocked:</p>
      <ul class="bl-feats" style="text-align:left;align-self:stretch">${unlocked.map((f) => `<li>${P.i('check', 'ic-14')}<span>${f}</span></li>`).join('')}</ul>
      ${P.btn('Continue', { size: 'lg', block: true, href: '/today' })}</div></div>`;
  }

  function studentBilling(ctx) {
    const free = ctx.query.plan === 'free';
    const actFor = (p) => {
      if (p.code === 'STUDENT_FREE') return `<span class="t-caption muted">${free ? 'Your plan' : 'Included'}</span>`;
      if (p.code === 'STUDENT_PRO') return free ? P.btn('Upgrade', { block: true, attrs: 'data-action="paywall" data-reason="UNLIMITED_WEEKS"' }) : '<span class="t-caption muted">Covered by Anna K.</span>';
      return P.btn('Become a tutor', { v: 'secondary', block: true, href: '/tutor/students' });
    };
    return `<div class="page">
      <h1 class="t-title-1">Billing and plans</h1>
      <div class="card lg mt-5 bl-current">
        <span class="icon-box">${P.i('crown')}</span>
        <div class="grow"><div class="t-overline">Current plan</div><div class="t-title-2">${free ? 'Student Free' : 'Pro via your tutor Anna K.'}</div>
          <p class="muted t-caption mt-1">${free ? 'Upgrade to unlock a new week every week and unlimited AI checks.' : 'Anna K. covers your subscription while you are enrolled. There is nothing to pay.'}</p></div>
        ${free ? P.btn('Upgrade', { attrs: 'data-action="paywall" data-reason="UNLIMITED_WEEKS"' }) : ''}
      </div>
      <div class="card mt-3"><div class="row between wrap gap-2"><span class="t-strong">${free ? 'AI checks: 3 of 10' : 'AI checks: unlimited'}</span><span class="t-caption muted">${free ? 'For the whole term' : '41 used this month'}</span></div>
        ${P.bar(free ? 30 : 100, { thin: true, pct: false, label: 'AI checks used' })}</div>
      <h2 class="t-title-2 mt-8">Plans</h2>
      <div class="bl-plans mt-4">${PLANS.map((p) => planCard(p, { current: free ? p.code === 'STUDENT_FREE' : p.code === 'STUDENT_PRO', action: actFor(p) })).join('')}</div>
      <h2 class="t-title-2 mt-8">Payment history</h2>
      <div class="mt-3">${payRows(free ? [['Aug 30, 2026', '€0.00', 'Trial', 'neutral']] : [['Aug 30, 2026', '€0.00', 'Trial', 'neutral'], ['Sep 6, 2026', '€12.00', 'Paid', 'accent'], ['Oct 1, 2026', '€0.00', 'Covered by tutor', 'info']])}</div>
    </div>`;
  }

  const receipts = (root) => { const h = (e) => { if (e.target.closest('[data-receipt]')) P.toast('Receipt opened in a new tab', 'receipt'); }; root.addEventListener('click', h); return () => root.removeEventListener('click', h); };

  P.register('/billing', {
    back: (ctx) => (ctx.ios ? { label: 'Profile', href: '/profile' } : { label: 'Settings', href: '/settings' }),
    title: 'Billing and plans', num: 'plan', web: true, ios: true, nav: 'settings', tab: 'profile',
    render(ctx) {
      if (ctx.query.checkout === 'success') return checkoutResult(ctx);
      if (ctx.query.role === 'tutor') return `<div class="page"><div class="skel" style="height:40px;width:260px"></div></div>`;
      return studentBilling(ctx);
    },
    mount(root, ctx) {
      if (ctx.query.role === 'tutor') { location.replace('#/tutor/billing'); return null; }
      return receipts(root);
    },
  });

  P.register('/tutor/billing', {
    title: 'Billing and plans (tutor)', num: 'plan', hidden: true, web: true, ios: false, role: 'tutor', nav: 'tutor-billing', fab: false,
    render() {
      return `<div class="page">
        <h1 class="t-title-1">Billing and plans</h1>
        <div class="card lg mt-5 bl-current"><span class="icon-box">${P.i('users')}</span>
          <div class="grow"><div class="t-overline">Current plan</div><div class="t-title-2">Tutor · 8 of 10 seats</div><p class="muted t-caption mt-1">€29 / month · renews Nov 1. Your students get Pro while enrolled.</p></div>
          ${P.btn('Upgrade to Tutor Plus', { attrs: 'data-up' })}</div>
        <div class="card mt-3"><div class="row between wrap gap-2"><span class="t-strong">Seats used</span><span class="t-caption muted">2 seats free</span></div>${P.bar(80, { thin: true, pct: false, label: 'Seats used' })}</div>
        <h2 class="t-title-2 mt-8">Plans</h2>
        <div class="bl-plans mt-4">${PLANS.filter((p) => p.code.startsWith('TUTOR')).map((p) => planCard(p, { current: p.code === 'TUTOR', action: p.code === 'TUTOR' ? '<span class="t-caption muted">Your plan</span>' : P.btn('Upgrade', { block: true, attrs: 'data-up' }) })).join('')}</div>
        <h2 class="t-title-2 mt-8">Payment history</h2>
        <div class="mt-3">${payRows([['Sep 1, 2026', '€29.00', 'Paid', 'accent'], ['Oct 1, 2026', '€29.00', 'Paid', 'accent'], ['Aug 1, 2026', '€29.00', 'Paid', 'accent']])}</div>
        <div class="mt-5">${P.btn('Cancel subscription', { v: 'ghost', attrs: 'data-cancel' })}</div>
      </div>`;
    },
    mount(root) {
      const off = receipts(root);
      const h = (e) => {
        if (e.target.closest('[data-up]')) {
          P.openOverlay(dlg('Upgrade to Tutor Plus', '<p class="muted">€59 / month, up to 30 students. The change applies from your next payment on Nov 1.</p>', `${P.btn('Not now', { v: 'ghost', attrs: 'data-action="close"' })}${P.btn('Confirm upgrade', { attrs: 'data-ok' })}`), {
            label: 'Upgrade to Tutor Plus',
            mount(el) { const c = (ev) => { if (ev.target.closest('[data-ok]')) { P.closeOverlay(); P.toast('You will move to Tutor Plus on Nov 1'); } }; el.addEventListener('click', c); return () => el.removeEventListener('click', c); },
          });
        }
        if (e.target.closest('[data-cancel]')) {
          P.openOverlay(dlg('Cancel subscription?', "<p class=\"muted\">You'll keep Tutor until Nov 1. After that your students move to Student Free.</p>", `${P.btn('Keep Tutor', { v: 'ghost', attrs: 'data-action="close"' })}${P.btn('Cancel subscription', { v: 'destructive', attrs: 'data-ok' })}`), {
            label: 'Cancel subscription',
            mount(el) { const c = (ev) => { if (ev.target.closest('[data-ok]')) { P.closeOverlay(); P.toast("Subscription cancelled. You'll keep Tutor until Nov 1"); } }; el.addEventListener('click', c); return () => el.removeEventListener('click', c); },
          });
        }
      };
      root.addEventListener('click', h);
      return () => { off(); root.removeEventListener('click', h); };
    },
  });

  /* ══════════════════════ 7. Component catalog ══════════════════════ */
  const BRAND = [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950].map((n) => '--brand-' + n);
  const NEUTRAL = [0, 25, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950].map((n) => '--neutral-' + n);
  const SEMANTIC = ['canvas', 'surface', 'surface-raised', 'surface-task', 'surface-sunken', 'ink', 'ink-muted', 'ink-subtle', 'ink-inverse', 'border', 'border-strong', 'accent', 'accent-soft', 'accent-on-soft', 'primary', 'primary-hover', 'link', 'link-hover', 'focus-ring', 'success', 'success-soft', 'warning', 'warning-soft', 'danger', 'danger-soft', 'danger-solid', 'info', 'info-soft', 'recording', 'overlay-scrim'].map((n) => '--' + n);
  const CONTRAST = [
    ['READING', 'tag-reading', '#FFF4C2 / #7A5A00', '5.77:1', '#2E2A12 / #F2D46B', '9.89:1'],
    ['LISTENING', 'tag-listening', '#DDF4F8 / #0B6378', '5.98:1', '#0E2A31 / #7FD3E6', '8.87:1'],
    ['WRITING', 'tag-writing', '#FBE3F1 / #9C2A6B', '5.87:1', '#33162A / #F0A3CF', '8.41:1'],
    ['SPEAKING', 'tag-speaking', '#E6F7EF / #106B44', '5.90:1', '#173A25 / #6FD9A4', '7.27:1'],
    ['VOCABULARY', 'tag-vocabulary', '#ECE9FB / #4B3FA6', '6.88:1', '#221E3D / #B8AEF5', '7.88:1'],
    ['GRAMMAR', 'tag-grammar', '#FDEBDD / #9A4A12', '5.38:1', '#33200F / #F5B583', '8.73:1'],
    ['RESOURCES', 'tag-resources', '#EEF2EF / #3C4C42', '8.06:1', '#1E2B24 / #C9D6CE', '9.81:1'],
  ];
  const WEEK_COVERS = ['week-01-campus', 'week-02-library', 'week-03-lecture-hall', 'week-04-exam-desk', 'week-05-headphones', 'week-06-microphone', 'week-07-open-books', 'week-08-letter', 'week-09-globe', 'week-10-laboratory', 'week-11-quad', 'week-12-dormitory', 'week-13-museum', 'week-14-observatory', 'week-15-cafeteria', 'week-16-graduation'];
  const SECTION_COVERS = ['section-reading', 'section-listening', 'section-writing', 'section-speaking', 'section-vocabulary', 'section-grammar'];

  function catalog() {
    const sec = (id, title, body, note = '') => `<section class="cat-sec" id="cat-${id}"><h2 class="t-title-2">${title}</h2>${note ? `<p class="muted t-caption mt-1">${note}</p>` : ''}<div class="mt-4">${body}</div></section>`;
    const lab = (t) => `<div class="cat-lab">${t}</div>`;
    const group = (t, inner, cls = '') => `<div class="cat-group">${lab(t)}<div class="row gap-3 wrap ${cls}">${inner}</div></div>`;

    const buttons = ['primary', 'secondary', 'ghost', 'destructive'].map((v) => group(v,
      `${P.btn('Label', { v, size: 'sm' })}${P.btn('Label', { v })}${P.btn('Label', { v, size: 'lg' })}${P.btn('With icon', { v, icon: 'plus' })}${P.btn('Disabled', { v, disabled: true })}${P.btn('Loading', { v, icon: 'loader-circle', cls: 'loading' })}`)).join('')
      + group('icon', `${P.btn('Settings', { v: 'icon', icon: 'settings', size: 'sm' })}${P.btn('Settings', { v: 'icon', icon: 'settings' })}${P.btn('Settings', { v: 'icon', icon: 'settings', size: 'lg' })}${P.btn('Settings', { v: 'icon', icon: 'settings', disabled: true })}${P.btn('Loading', { v: 'icon', icon: 'loader-circle', cls: 'loading' })}`)
      + `<div class="cat-group">${lab('block')}<div style="max-width:360px">${P.btn('Start', { block: true })}</div></div>`;

    const tags = group('sections', Object.keys(P.SECTIONS).map((s) => P.tag(s, P.SECTIONS[s].label)).join(''))
      + group('modifiers', `${P.tag('READING', 'Reading', { mod: 'legacy' })}${P.tag('WRITING', 'Email: Missing a Meeting', { mod: 'tutor' })}${P.tag('RESOURCES', 'TSTP site', { mod: 'external' })}${P.tag('RESOURCES', 'Mock exam', { mod: 'exam' })}${P.tag('SPEAKING', 'Done', { done: true })}${P.tag('LISTENING', 'Listening', { dot: true })}`)
      + group('cell (week table)', `<div style="width:190px">${P.tag('WRITING', 'Email: Missing a Meeting', { cell: true })}</div><div style="width:190px">${P.tag('READING', '100 questions Reading pp.12 (read the text and write out all new words)', { cell: true, mod: 'tutor' })}</div>`)
      + group('status', `${P.tag('neutral', 'Neutral')}${P.tag('accent', 'Accent')}${P.tag('info', 'Info')}${P.tag('warn', 'Warning')}${P.tag('danger', 'Danger')}`);

    const checks = group('checkbox', `<span class="row gap-2">${P.check(false, { label: 'Off' })}Off</span><span class="row gap-2">${P.check(true, { label: 'On' })}On</span><span class="row gap-2">${P.check(false, { half: true, label: 'Indeterminate' })}Indeterminate</span><span class="row gap-2">${P.check(true, { round: true, label: 'Round on' })}Round, on</span>`);

    const sectionCard = (id, cover, title, caption, done) => `<div class="section-card" style="width:250px">${P.cover(cover, 'section')}<div class="sc-body">${P.check(done, { label: 'Done' })}<div class="sc-ic">${P.i(P.SECTIONS[id].icon)}</div><div class="t-title-3">${title}</div><div class="t-caption muted">${caption}</div></div></div>`;
    const cards = group('card', `<div class="card" style="width:250px"><div class="card-title">Card</div><p class="muted t-caption mt-1">Surface, 1 px border, 12 px radius.</p></div><div class="card click" style="width:250px"><div class="card-title">Clickable card</div><p class="muted t-caption mt-1">Hover for elevation.</p></div>`)
      + group('section card', `${sectionCard('READING', 'section-reading', 'Reading', '4 of 4 done', true)}${sectionCard('LISTENING', 'section-listening', 'Listening', '2 of 4 done', false)}${sectionCard('GRAMMAR', 'section-grammar', 'Grammar', 'Round-Up Unit 7', false)}`);

    const wk = (o) => `<div class="week-card ${o.locked ? 'locked' : ''}" style="width:230px"><div class="wc-badge">${o.badge}</div>${P.cover(o.cover, 'week-card')}<div class="wc-body"><div class="t-title-3">Week ${o.n}</div><div class="t-caption muted">${o.dates}</div>${o.bar !== undefined ? `<div class="mt-2">${P.bar(o.bar, { thin: true, label: 'Week progress' })}</div>` : ''}</div></div>`;
    const weeks = group('week card', `${wk({ n: 7, dates: 'Oct 11 – Oct 17', cover: 'week-07-open-books', badge: P.tag('accent', 'Current'), bar: 64 })}${wk({ n: 6, dates: 'Oct 4 – Oct 10', cover: 'week-06-microphone', badge: P.tag('accent', 'Done', { done: true }), bar: 100 })}${wk({ n: 9, dates: 'Oct 25 – Oct 31', cover: 'week-09-globe', badge: P.tag('neutral', 'Upcoming'), bar: 0 })}${wk({ n: 8, dates: 'Oct 18 – Oct 24', cover: 'week-04-exam-desk', badge: P.tag('RESOURCES', 'Mock exam', { mod: 'exam' }), bar: 0 })}${wk({ n: 12, dates: 'Nov 15 – Nov 21', cover: 'week-12-dormitory', locked: true, badge: `<span class="tag neutral">${P.i('lock')}<span class="tx">Pro</span></span>` })}`);

    const progress = group('progress bar', `<div style="width:260px">${P.bar(64, { label: 'Total progress' })}</div><div style="width:260px">${P.bar(22, { thin: true, label: 'Thin' })}</div><div style="width:260px">${P.bar(100, { label: 'Complete' })}</div>`)
      + group('progress ring', `${P.ring({ value: 3, max: 4, size: 48, center: '3/4', label: 'Ring 48' })}${P.ring({ value: 18, max: 30, size: 74, center: '18', label: 'Ring 74' })}${P.ring({ value: 64, max: 100, size: 120, center: '64%', label: 'Ring 120' })}`, 'gap-6')
      + group('band ring', `<div class="band-card">${P.bandRing({ low: 4.5, high: 5.0 })}${P.bandText({ low: 4.5, high: 5.0, cefr: 'B2 → C1', total: 95 })}</div>
        <div class="band-card">${P.ring({ size: 120, dashed: true, center: P.i('gauge', 'ic-24'), label: 'Estimated band is not available yet' })}<div class="stack gap-1"><div class="t-title-3">Need more data</div><div class="muted t-caption" style="max-width:200px">Complete 3 more Reading tasks to see your band</div></div></div>
        ${P.miniRings({ R: 5.0, L: 4.5, W: 4.0, S: 4.5 })}`, 'gap-8');

    const timers = group('timer', `${P.timer('12:40', { label: 'Section time' })}${P.timer('00:48', { state: 'warning' })}${P.timer('00:09', { state: 'danger' })}${P.timer('+00:42', { state: 'warning', label: 'Over time' })}`)
      + `<p class="t-caption muted">Practice: neutral, counting down to the recommended time. Warning below 1 minute. Danger in the last seconds of an exam section. Overrun: counts up in warning color.</p>`;

    const opt = (cls, letter, text, verdict = '') => `<button type="button" class="mcq-opt ${cls}"><span class="radio"></span><span class="letter">${letter}</span><span>${text}</span>${verdict}</button>`;
    const mcq = `<div class="cat-cols">
      <div><div class="cat-lab">default and selected (exam)</div><div class="mcq">${opt('', 'A', 'The speaker is surprised by the result')}${opt('sel', 'B', 'The speaker disagrees with the professor')}</div></div>
      <div><div class="cat-lab">selected (practice)</div><div class="mcq practice">${opt('sel', 'B', 'The speaker disagrees with the professor')}${opt('', 'C', 'The speaker wants to change the topic')}</div></div>
      <div><div class="cat-lab">review: correct and wrong</div><div class="mcq">${opt('correct', 'C', 'The author compares two methods', `<span class="verdict">${P.i('circle-check')}Correct</span>`)}${opt('wrong', 'A', 'The author rejects both methods', `<span class="verdict">${P.i('circle-x')}Your answer</span>`)}</div></div></div>`;

    const wave = (rec) => `<div class="wave" aria-hidden="true">${Array.from({ length: 36 }, (_, i) => `<i style="height:${rec ? 6 + Math.round(Math.abs(Math.sin(i * 0.7)) * 28) : 4}px"></i>`).join('')}</div>`;
    const media = `<div class="cat-cols">
      <div><div class="cat-lab">audio player</div><div class="audio"><button class="play" aria-label="Play">${P.i('play')}</button><div class="grow">${P.bar(35, { thin: true, pct: false, label: 'Playback' })}</div><span class="time">0:14 / 0:41</span></div></div>
      <div><div class="cat-lab">recorder: idle</div><div class="recorder idle"><div class="rec-top"><span class="rec-label">Ready</span>${wave(false)}</div></div></div>
      <div><div class="cat-lab">recorder: recording</div><div class="recorder recording"><div class="rec-top"><span class="rec-dot"></span><span class="rec-label t-mono">00:23</span>${wave(true)}</div></div></div></div>`;

    const banners = ['info|info|Your tutor approved Week 7 on Oct 10.', 'warning|triangle-alert|Monday: 75 min (student plans 45)', 'success|circle-check|Week 8 published.', "danger|circle-alert|We couldn't save the recording. Try again."].map((s) => { const [k, i, t] = s.split('|'); return P.banner(k, i, t); }).join('');
    const toastEx = `<div class="stack gap-2" style="align-items:flex-start"><div class="toast">${P.i('circle-check')}<span>Week 8 published</span></div><div class="toast">${P.i('info')}<span>Draft saved. The timer keeps running.</span></div></div>`;

    const empties = `<div class="cat-cols">${[
      ['You\'re done for today', 'Tomorrow: 4 tasks, about 35 min.', 'Review today\'s mistakes'],
      ['Invite your first student', 'Share a link or a code. Students get Pro while enrolled.', 'Invite student'],
      ['Upload your first textbook', 'PDF up to 200 MB. Then add page ranges to any week.', 'Upload PDF'],
    ].map(([t, x, b]) => `<div class="card">${P.empty({ title: t, text: x, btn: P.btn(b, { v: 'secondary', size: 'sm' }) })}</div>`).join('')}</div>`;

    const skel = `<div class="cat-cols"><div class="stack gap-2"><div class="skel" style="height:90px"></div><div class="skel" style="height:14px;width:60%"></div><div class="skel" style="height:10px;width:40%"></div></div>
      <div class="row gap-3"><div class="skel" style="width:74px;height:74px;border-radius:50%"></div><div class="stack gap-2 grow"><div class="skel" style="height:14px"></div><div class="skel" style="height:10px;width:70%"></div></div></div>
      <div class="stack gap-2"><div class="skel" style="height:36px;width:70%"></div><div class="skel" style="height:56px;width:85%;align-self:flex-end"></div></div></div>`;

    const chat = `<div class="chat-log" style="max-width:520px">
      <div class="tool-line">${P.i('eye')}Looked at your last 3 essays</div>
      <div class="bubble-me">What should I focus on this week?</div>
      <div class="bubble-ai"><div class="who">${P.i('sparkles')}Toefly Tutor</div><p>Your weakest spot is <b>Build a Sentence</b>: 4 of your last 10 sentences put <code>not</code> in the wrong place.</p></div>
      <div class="bubble-ai"><div class="who">${P.i('sparkles')}Toefly Tutor</div><span class="thinking"><i></i><i></i><i></i>&nbsp;Thinking…</span></div></div>`;

    const controls = group('segmented control', `<div class="seg"><button class="on">By day</button><button>Table</button></div>`)
      + group('chips', `<button class="chip on">All</button><button class="chip">Behind</button><button class="chip">${P.i('filter')}Awaiting approval</button>`)
      + group('switch', `<span class="row gap-2"><button class="switch" aria-label="Off" data-toggle></button>Off</span><span class="row gap-2"><button class="switch on" aria-label="On" data-toggle></button>On</span>`)
      + group('input', `<div class="field" style="width:260px"><label for="cat-in">Label</label><input class="input" id="cat-in" placeholder="Placeholder"></div><div class="field" style="width:260px"><label for="cat-in2">With value</label><input class="input" id="cat-in2" value="maria.petrova@example.com"></div>`);

    const covers = `<div class="cat-lab">weeks, 16:9 (week-card)</div><div class="cat-covers">${WEEK_COVERS.map((id) => `<figure>${P.cover(id, 'week-card', 'cat-cv')}<figcaption class="t-caption muted">${id}</figcaption></figure>`).join('')}</div>
      <div class="cat-lab mt-5">sections, 16:7 (section)</div><div class="cat-covers">${SECTION_COVERS.map((id) => `<figure>${P.cover(id, 'section', 'cat-cv')}<figcaption class="t-caption muted">${id}</figcaption></figure>`).join('')}</div>`;

    const sw = (v) => `<div class="cat-sw"><i style="background:var(${v})"></i><b>${v}</b><code data-cv="${v}"></code></div>`;
    const tokens = `<div class="cat-lab">brand</div><div class="cat-sws">${BRAND.map(sw).join('')}</div>
      <div class="cat-lab mt-4">neutral</div><div class="cat-sws">${NEUTRAL.map(sw).join('')}</div>
      <div class="cat-lab mt-4">semantic (follows the current theme)</div><div class="cat-sws">${SEMANTIC.map(sw).join('')}</div>
      <div class="cat-lab mt-4">section tags and contrast</div>
      <div style="overflow:auto"><table class="table cat-table"><thead><tr><th scope="col">Section</th><th scope="col">Tag</th><th scope="col">Light bg / text</th><th scope="col">Contrast</th><th scope="col">Dark bg / text</th><th scope="col">Contrast</th></tr></thead><tbody>${CONTRAST.map(([s, tok, l, lc, d, dc]) => `<tr><td>${s}</td><td>${P.tag(s, P.SECTIONS[s].label)}</td><td class="t-mono">${l}</td><td class="t-mono">${lc}</td><td class="t-mono">${d}</td><td class="t-mono">${dc}</td></tr>`).join('')}</tbody></table></div>`;

    const parts = [
      ['buttons', 'Button', buttons, 'Variants, sizes sm 36, md 44, lg 52, and states.'], ['tags', 'Tag', tags], ['checks', 'Checkbox', checks], ['cards', 'Card and SectionCard', cards],
      ['weeks', 'WeekCard', weeks], ['progress', 'ProgressBar, ProgressRing, BandRing', progress], ['timers', 'Timer', timers], ['mcq', 'McqOption', mcq], ['media', 'AudioPlayer and Recorder', media],
      ['banners', 'Banners and Toast', `<div class="stack gap-2" style="max-width:520px">${banners}</div><div class="mt-4">${toastEx}</div>`], ['empty', 'EmptyState', empties], ['skel', 'Skeleton', skel],
      ['chat', 'ChatBubble', chat], ['controls', 'Segmented control, chips, switch, input', controls], ['covers', 'Covers', covers, '16 week covers and 6 section covers.'], ['tokens', 'Color tokens', tokens],
    ];
    return `<div class="page wide cat">
      <h1 class="t-title-1">Component catalog</h1>
      <p class="muted mt-2">Every variant and state of the shared components. Switch the theme in the toolbar to compare light and dark.</p>
      <nav class="row gap-2 wrap mt-4 cat-toc" aria-label="Catalog sections">${parts.map(([id, t]) => `<button type="button" class="chip" data-jump="cat-${id}">${t}</button>`).join('')}</nav>
      ${parts.map(([id, t, b, n]) => sec(id, t, b, n)).join('')}
    </div>`;
  }

  P.register('/catalog', {
    title: 'Component catalog', num: 24, web: true, ios: false, nav: null, fab: false,
    render: catalog,
    mount(root) {
      const main = $(root, '#main');
      const fill = () => { const cs = getComputedStyle(root); $$(root, '[data-cv]').forEach((el) => { el.textContent = cs.getPropertyValue(el.dataset.cv).trim(); }); };
      fill();
      const mo = new MutationObserver(fill);
      mo.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });
      const h = (e) => {
        const j = e.target.closest('[data-jump]');
        if (j) { const t = $(root, '#' + j.dataset.jump); if (t) main.scrollTo({ top: t.offsetTop - 12, behavior: 'smooth' }); }
        const pl = e.target.closest('.audio .play');
        if (pl) pl.classList.toggle('on');
      };
      root.addEventListener('click', h);
      return () => { mo.disconnect(); root.removeEventListener('click', h); };
    },
  });
})();
