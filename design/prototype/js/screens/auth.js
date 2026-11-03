/* Sign in / sign up (Keycloak theme), onboarding, diagnostic intro, runner and result. */
(function () {
  const P = window.P;

  const googleMark = '<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path fill="currentColor" d="M21.6 12.23c0-.71-.06-1.4-.18-2.05H12v3.88h5.38a4.6 4.6 0 0 1-2 3.02v2.5h3.24c1.9-1.75 2.98-4.32 2.98-7.35zM12 22c2.7 0 4.96-.9 6.62-2.42l-3.24-2.5c-.9.6-2.04.95-3.38.95-2.6 0-4.8-1.75-5.59-4.11H3.07v2.59A10 10 0 0 0 12 22zm-5.59-8.08a6 6 0 0 1 0-3.84V7.49H3.07a10 10 0 0 0 0 9.02l3.34-2.59zM12 5.98c1.47 0 2.79.5 3.83 1.5l2.87-2.87A9.6 9.6 0 0 0 12 2a10 10 0 0 0-8.93 5.49l3.34 2.59C7.2 7.73 9.4 5.98 12 5.98z"/></svg>';
  const appleMark = '<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path fill="currentColor" d="M16.4 12.6c0-2.3 1.9-3.4 2-3.5a4.3 4.3 0 0 0-3.4-1.8c-1.4-.2-2.8.8-3.5.8s-1.8-.8-3-.8a4.5 4.5 0 0 0-3.8 2.3c-1.6 2.8-.4 7 1.2 9.3.8 1.1 1.7 2.4 2.9 2.3 1.2 0 1.6-.7 3-.7s1.8.7 3 .7c1.3 0 2.1-1.1 2.8-2.3a10 10 0 0 0 1.3-2.6 4 4 0 0 1-2.5-3.7zM14.2 5.8A4 4 0 0 0 15.1 3a4.1 4.1 0 0 0-2.7 1.4 3.9 3.9 0 0 0-1 2.8c1.1 0 2.1-.5 2.8-1.4z"/></svg>';

  function authCard(ctx) {
    const up = ctx.query.mode === 'signup';
    const tutor = ctx.query.role === 'tutor';
    return `<div class="auth-card glass">
      <div class="center">${P.logo()}</div>
      <div class="center mt-4"><div class="t-title-2">${up ? 'Create your account' : 'Welcome back'}</div>${tutor ? '<div class="muted mt-1">Tutor account</div>' : ''}</div>
      <div class="stack gap-2 mt-6">
        <button class="btn btn-secondary block">${googleMark}<span>Continue with Google</span></button>
        <button class="btn btn-secondary block">${appleMark}<span>Continue with Apple</span></button>
      </div>
      <div class="or"><span>or</span></div>
      <form class="stack gap-4" onsubmit="return false">
        ${up ? '<div class="field"><label for="nm">Name</label><input class="input" id="nm" autocomplete="name" value="Maria Petrova"></div>' : ''}
        <div class="field"><label for="em">Email</label><input class="input" id="em" type="email" autocomplete="email" value="maria.petrova@example.com"></div>
        <div class="field"><label for="pw">Password</label><div class="pw"><input class="input" id="pw" type="password" autocomplete="${up ? 'new-password' : 'current-password'}" value="prototype"><button type="button" class="btn btn-ghost sm" data-show-pw>Show</button></div></div>
        ${up ? `<label class="row gap-2 t-caption" style="align-items:flex-start">${P.check(true, { attrs: 'data-toggle', label: 'I agree to the Terms and Privacy Policy' })}<span>I agree to the <a class="link">Terms</a> and <a class="link">Privacy Policy</a></span></label>` : ''}
        ${P.btn(up ? 'Create account' : 'Sign in', { block: true, href: up ? '/onboarding/goal' : '/today' })}
      </form>
      <div class="stack gap-2 mt-5 center t-caption">
        ${up ? '<span class="muted">Already have an account? <a class="link" href="#/sign-in">Sign in</a></span>' : '<a class="link" href="javascript:void 0">Forgot password?</a><span class="muted">New here? <a class="link" href="#/sign-in?mode=signup">Create an account</a></span>'}
      </div>
    </div>`;
  }

  /* iOS WelcomeView before the web authentication session */
  function welcome() {
    return `<div class="page welcome">
      <div class="welcome-top">${P.logo()}<div class="t-title-1 mt-4">Prepare for the new TOEFL iBT like you have a personal tutor.</div>
        <div class="muted mt-3 t-body">Weekly plans, instant feedback on every task, real mock exams.</div></div>
      <div class="stack gap-3">
        <button class="btn btn-apple block lg">${appleMark}<span>Sign in with Apple</span></button>
        ${P.btn('Get started', { block: true, size: 'lg', href: '/sign-in?mode=signup' })}
        ${P.btn('I already have an account', { v: 'ghost', block: true, href: '/sign-in?mode=signin' })}
      </div></div>`;
  }

  P.register('/sign-in', {
    title: 'Sign in', num: 2, layout: 'auth', fab: false,
    render: (ctx) => (ctx.ios && !ctx.query.mode ? welcome() : `<div class="page auth-page">${authCard(ctx)}</div>`),
    mount(root) {
      const b = root.querySelector('[data-show-pw]');
      if (b) b.addEventListener('click', () => { const i = root.querySelector('#pw'); const t = i.type === 'password'; i.type = t ? 'text' : 'password'; b.textContent = t ? 'Hide' : 'Show'; });
    },
  });

  /* ───────── onboarding (6 steps) ───────── */
  const STEPS = ['goal', 'exam-date', 'schedule', 'explanation-language', 'consents', 'diagnostic-intro'];
  const stepHref = (s) => (s === 'diagnostic-intro' ? '/diagnostic' : '/onboarding/' + s);

  function shell(step, body, o = {}) {
    const i = STEPS.indexOf(step);
    const back = i > 0 ? stepHref(STEPS[i - 1]) : '/sign-in?mode=signup';
    const next = o.next || stepHref(STEPS[i + 1]);
    return `<div class="page auth-page"><div class="onb-card glass">
      <div class="row between t-caption muted"><span>Step ${i + 1} of 6</span>${P.logo()}</div>
      <div class="mt-2">${P.bar(Math.round(((i + 1) / 6) * 100), { pct: false, label: 'Onboarding progress' })}</div>
      <div class="t-title-2 mt-6">${o.title}</div>${o.sub ? `<div class="muted mt-1">${o.sub}</div>` : ''}
      <div class="mt-5">${body}</div>
      <div class="row between mt-8">${P.btn('Back', { v: 'ghost', href: back, icon: 'chevron-left' })}${o.cta || P.btn('Continue', { href: next, cls: 'onb-next' })}</div>
    </div></div>`;
  }

  const seg = (items, on, cls = '') => `<div class="choice ${cls}" role="radiogroup">${items.map((t) => `<button class="choice-btn ${t === on ? 'on' : ''}" role="radio" aria-checked="${t === on}">${t}</button>`).join('')}</div>`;
  const singleSelect = (root) => root.querySelectorAll('.choice:not(.multi)').forEach((g) => g.addEventListener('click', (e) => {
    const b = e.target.closest('.choice-btn'); if (!b) return;
    g.querySelectorAll('.choice-btn').forEach((x) => { x.classList.toggle('on', x === b); x.setAttribute('aria-checked', x === b); });
    g.dispatchEvent(new CustomEvent('pick', { detail: b.textContent }));
  }));

  P.register('/onboarding/goal', {
    title: 'Onboarding: goal, date, schedule', num: 3, layout: 'auth', fab: false,
    render: () => shell('goal', `${seg(['3.0', '3.5', '4.0', '4.5', '5.0', '5.5', '6.0'], '5.0', 'goal')}
      <div class="banner info mt-4">${P.i('graduation-cap')}<div id="cefr">5.0 ≈ C1 ≈ 95+</div></div>
      <a class="link t-caption mt-4" style="display:inline-block">I know my 0–120 target</a>`, { title: 'What score do you need?' }),
    mount(root) {
      singleSelect(root);
      const hint = { '3.0': 'B1 ≈ 50+', '3.5': 'B1 ≈ 60+', '4.0': 'B2 ≈ 70+', '4.5': 'B2 ≈ 80+', '5.0': 'C1 ≈ 95+', '5.5': 'C1 ≈ 105+', '6.0': 'C2 ≈ 115+' };
      root.querySelector('.choice').addEventListener('pick', (e) => { root.querySelector('#cefr').textContent = `${e.detail} ≈ ${hint[e.detail]}`; });
    },
  });

  function calendar() {
    const days = [];
    for (let i = 0; i < 2; i++) days.push('<span></span>'); /* Dec 1, 2026 is a Tuesday */
    for (let d = 1; d <= 31; d++) days.push(`<button class="cal-d ${d === 27 ? 'on' : ''}" aria-label="December ${d}">${d}</button>`);
    return `<div class="cal"><div class="row between"><button class="btn btn-icon sm" aria-label="Previous month">${P.i('chevron-left')}</button><span class="t-strong">December 2026</span><button class="btn btn-icon sm" aria-label="Next month">${P.i('chevron-right')}</button></div>
      <div class="cal-grid mt-3">${['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'].map((d) => `<span class="cal-h">${d}</span>`).join('')}${days.join('')}</div></div>`;
  }
  P.register('/onboarding/exam-date', {
    title: 'Onboarding: exam date', num: 'plan', hidden: true, layout: 'auth', fab: false,
    render: () => shell('exam-date', `${calendar()}<label class="row gap-3 mt-5"><button class="switch" data-toggle aria-label="I don't have a date yet"></button>I don't have a date yet</label>`, { title: 'When is your exam?', sub: 'Sunday, Dec 27, 2026 · 17 weeks from now' }),
    mount(root) {
      root.querySelector('.cal-grid').addEventListener('click', (e) => { const b = e.target.closest('.cal-d'); if (!b) return; root.querySelectorAll('.cal-d').forEach((x) => x.classList.toggle('on', x === b)); });
    },
  });

  P.register('/onboarding/schedule', {
    title: 'Onboarding: schedule', num: 'plan', hidden: true, layout: 'auth', fab: false,
    render: (ctx) => shell('schedule', `<div class="t-overline">Minutes a day</div><div class="mt-2">${seg(['15', '30', '45', '60', '90'], '30')}</div>
      <div class="t-overline mt-6">Study days</div><div class="choice multi mt-2">${['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((d) => `<button class="choice-btn on" aria-pressed="true">${d}</button>`).join('')}</div>
      <div class="field mt-6" style="max-width:220px"><label for="rt">Reminder time</label><input class="input" id="rt" type="time" value="19:00"></div>
      ${ctx.ios ? `<div class="banner info mt-5">${P.i('bell')}<div>We'll remind you at 19:00. Next, iOS will ask to allow notifications.</div></div>` : ''}`, { title: 'How much time can you study?' }),
    mount(root) {
      singleSelect(root);
      root.querySelector('.choice.multi').addEventListener('click', (e) => { const b = e.target.closest('.choice-btn'); if (!b) return; b.classList.toggle('on'); b.setAttribute('aria-pressed', b.classList.contains('on')); });
    },
  });

  P.register('/onboarding/explanation-language', {
    title: 'Onboarding: explanations language', num: 'plan', hidden: true, layout: 'auth', fab: false,
    render: () => shell('explanation-language', `<div class="stack gap-3 choice" role="radiogroup">
        <button class="choice-btn opt-card on" role="radio" aria-checked="true"><span class="t-strong">English</span><span class="muted t-caption">All explanations in English</span></button>
        <button class="choice-btn opt-card" role="radio" aria-checked="false"><span class="t-strong">English + Russian toggle</span><span class="muted t-caption">"Explain in Russian" under every explanation</span></button></div>`, { title: 'Explanations language' }),
    mount: singleSelect,
  });

  P.register('/onboarding/consents', {
    title: 'Onboarding: your data', num: 'plan', hidden: true, layout: 'auth', fab: false,
    render: () => shell('consents', `<div class="stack gap-4">
        <label class="consent">${P.check(false, { attrs: 'data-toggle id="req"', label: 'Required consent' })}<span><span class="t-strong">Check my answers and voice recordings</span><span class="muted t-caption">Required. Your answers and recordings are processed to score them and give feedback. Personal data is masked before any external AI service.</span></span></label>
        <label class="consent">${P.check(false, { attrs: 'data-toggle' })}<span><span class="t-strong">Product emails</span><span class="muted t-caption">Optional. Tips and weekly summaries.</span></span></label>
        <label class="consent">${P.check(false, { attrs: 'data-toggle' })}<span><span class="t-strong">Use my anonymised answers to improve scoring</span><span class="muted t-caption">Optional.</span></span></label></div>`, { title: 'Your data', cta: P.btn('Continue', { href: '/diagnostic', disabled: true, cls: 'onb-next' }) }),
    mount(root) {
      const next = root.querySelector('.onb-next');
      next.classList.add('disabled');
      root.querySelector('#req').addEventListener('click', (e) => setTimeout(() => next.classList.toggle('disabled', !e.currentTarget.classList.contains('on'))));
      root.querySelector('.consent').addEventListener('click', (e) => { if (!e.target.closest('.check')) root.querySelector('#req').click(); });
    },
  });

  /* ───────── diagnostic ───────── */
  P.register('/diagnostic', {
    title: 'Diagnostic: intro', num: 'plan', layout: 'auth', fab: false,
    render: () => shell('diagnostic-intro', `<div class="muted">About 25 minutes. Reading, Listening, Writing and Speaking, like the real test but shorter.</div>
      <div class="diag-list mt-5">${[['READING', 'Reading', '7 min'], ['LISTENING', 'Listening', '7 min'], ['WRITING', 'Writing', '6 min'], ['SPEAKING', 'Speaking', '5 min']]
        .map(([s, l, t]) => `<div class="row gap-3">${P.sectionIcon(s)}<span class="grow t-strong">${l}</span><span class="muted t-caption">${t}</span></div>`).join('')}</div>
      <div class="t-overline mt-6">You will need</div>
      <div class="row gap-2 wrap mt-2">${[['headphones', 'Headphones'], ['volume-x', 'A quiet place'], ['mic', 'Microphone for Speaking']].map(([i, t]) => `<span class="chip">${P.i(i)}${t}</span>`).join('')}
        <button class="btn btn-ghost sm" data-mic>${P.i('mic')}<span>Test microphone</span></button></div>`,
    { title: "Let's find your starting level", cta: `<div class="row gap-2">${P.btn('Skip for now', { v: 'ghost', href: '/today' })}${P.btn('Start diagnostic', { href: '/diagnostic/run' })}</div>` }),
    mount(root) {
      const b = root.querySelector('[data-mic]');
      b.addEventListener('click', () => { b.querySelector('span').textContent = 'Recording 3 s…'; setTimeout(() => { b.querySelector('span').textContent = 'Microphone works'; }, 1500); });
    },
  });

  const DIAG_Q = [
    { stim: { from: 'Campus Fitness Center', subject: 'New opening hours', body: 'Starting Monday, the fitness center will open at 6:00 a.m. on weekdays. Weekend hours stay the same. Lockers must be emptied every night; items left after closing will be taken to the front desk.' },
      q: 'What will happen to items left in lockers overnight?', opts: ['They will be thrown away.', 'They will be moved to the front desk.', 'They will stay in the lockers until Monday.', 'They will be returned by email request.'] },
    { stim: { from: 'Campus Fitness Center', subject: 'New opening hours', body: 'Starting Monday, the fitness center will open at 6:00 a.m. on weekdays. Weekend hours stay the same. Lockers must be emptied every night; items left after closing will be taken to the front desk.' },
      q: 'What is the main purpose of the email?', opts: ['To announce a change in the schedule', 'To advertise new equipment', 'To ask students to renew memberships', 'To explain a new fee for lockers'] },
  ];
  P.register('/diagnostic/run', {
    title: 'Diagnostic: question', num: 'plan', layout: 'player', fab: false,
    render: (ctx) => {
      const n = Number(ctx.query.q || 3);
      const d = DIAG_Q[n % 2];
      return `<div class="runner">
        <header class="runner-bar"><span class="t-strong">Diagnostic · Reading · ${n} of 8</span>
          <div class="row gap-2">${P.timer('05:42', { label: ctx.ios ? '' : 'Section time' })}
          <div class="menu-wrap">${P.btn('More', { v: 'icon', icon: 'ellipsis', attrs: 'data-menu' })}<div class="menu" hidden><button class="menu-item" data-exit>${P.i('log-out')}Exit</button></div></div></div></header>
        <div class="runner-body">
          <div class="stim-email"><div class="em-head"><div><span class="muted">From:</span> ${d.stim.from}</div><div><span class="muted">Subject:</span> ${d.stim.subject}</div></div><p class="t-body-lg">${d.stim.body}</p></div>
          <div class="runner-q"><div class="t-title-3">${d.q}</div>
            <div class="mcq mt-4" role="radiogroup">${d.opts.map((o, i) => `<button class="mcq-opt" role="radio" aria-checked="false"><span class="radio"></span><span class="letter">${'ABCD'[i]}</span><span>${o}</span></button>`).join('')}</div></div>
        </div>
        <footer class="runner-foot"><span class="muted t-caption">Exam mode: you can't go back</span>${P.btn('Next', { iconRight: 'chevron-right', href: n >= 8 ? '/diagnostic/result' : `/diagnostic/run?q=${n + 1}` })}</footer>
      </div>`;
    },
    mount(root) {
      root.querySelector('.mcq').addEventListener('click', (e) => {
        const o = e.target.closest('.mcq-opt'); if (!o) return;
        root.querySelectorAll('.mcq-opt').forEach((x) => { x.classList.toggle('sel', x === o); x.setAttribute('aria-checked', x === o); });
      });
      const m = root.querySelector('.menu');
      root.querySelector('[data-menu]').addEventListener('click', () => { m.hidden = !m.hidden; });
      root.querySelector('[data-exit]').addEventListener('click', () => {
        m.hidden = true;
        P.openOverlay(`<div class="stack gap-4"><div class="t-title-3">Exit the diagnostic?</div><div class="muted">Your progress in this section will be lost</div>
          <div class="row gap-2" style="justify-content:flex-end">${P.btn('Stay', { v: 'ghost', attrs: 'data-action="close"' })}${P.btn('Exit', { v: 'destructive', href: '/today' })}</div></div>`, { label: 'Exit' });
      });
    },
  });

  P.register('/diagnostic/result', {
    title: 'Diagnostic result + plan', num: 4,
    render: (ctx) => {
      const weeks = P.fx.weeks.weeks.slice(0, 8);
      return `<div class="page narrow">
        <div class="building card lg" id="building"><div class="t-title-3">Building your plan…</div>
          <div class="stack gap-3 mt-4">${['Scoring your answers', 'Finding your weak spots', 'Building Week 1'].map((t, i) => `<div class="row gap-3 bstep" data-i="${i}">${P.check(false, { round: true })}<span>${t}</span></div>`).join('')}</div></div>
        <div id="result" hidden>
          <div class="t-title-1">Your starting level</div>
          <div class="card lg mt-5"><div class="band-card">${P.bandRing({ low: 3.5, high: 4.0 }, ctx.ios ? 96 : 120)}${P.bandText({ low: 3.5, high: 4.0, cefr: 'B1 → B2', total: 60 })}</div>
            <div class="mt-5">${P.miniRings({ R: 4.0, L: 3.5, W: 3.0, S: 3.5 })}</div>
            <div class="grid-2 mt-5"><div><div class="t-overline">Strengths</div><div class="mt-1">Daily-life reading, academic vocabulary</div></div>
              <div><div class="t-overline">Focus first</div><div class="mt-1">Build a Sentence, Listen and Repeat</div></div></div></div>
          <div class="t-title-2 mt-8">Your plan: 17 weeks to 5.0 (Dec 27)</div>
          <div class="muted mt-1">Mock exams in weeks 4, 8 and 17</div>
          <div class="strip mt-4">${weeks.map((w) => `<div class="week-card mini">${P.cover(P.weekCoverId(w.n), 'week-card')}<div class="wc-body"><div class="t-strong">Week ${w.n}</div><div class="t-caption muted">${w.dates}</div>${w.exam ? `<div class="mt-1">${P.tag('neutral', 'Mock exam', { mod: 'exam' })}</div>` : ''}</div></div>`).join('')}</div>
          <div class="mt-8">${P.btn('Start Week 1', { size: 'lg', href: '/today', block: ctx.ios })}</div>
        </div></div>`;
    },
    mount(root) {
      const steps = root.querySelectorAll('.bstep .check');
      const timers = [0, 1, 2].map((i) => setTimeout(() => steps[i].classList.add('on'), 450 * (i + 1)));
      timers.push(setTimeout(() => { root.querySelector('#building').hidden = true; root.querySelector('#result').hidden = false; }, 1700));
      return () => timers.forEach(clearTimeout);
    },
  });
})();
