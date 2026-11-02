/* Prototype shell: hash router, web/iPhone frames, navigation chrome, overlays, prototype toolbar. */
(function () {
  const P = window.P;
  const STORE = 'toefly-prototype';
  let cleanup = null;

  try { Object.assign(P.state, JSON.parse(localStorage.getItem(STORE) || '{}')); } catch (e) { /* storage unavailable */ }
  const save = () => { if (pinned) return; try { localStorage.setItem(STORE, JSON.stringify(P.state)); } catch (e) { /* ignore */ } };

  /* ?frame=ios&theme=dark&reduced=1 open a fixed view; &shot=1 hides the toolbar and renders the frame 1:1 for screenshots */
  const params = new URLSearchParams(location.search);
  const pinned = ['frame', 'theme', 'reduced'].some((k) => params.has(k));
  if (params.has('frame')) P.state.frame = params.get('frame');
  if (params.has('theme')) P.state.theme = params.get('theme');
  if (params.has('reduced')) P.state.reduced = params.get('reduced') === '1';
  const shot = params.get('shot') === '1';
  if (shot) document.documentElement.classList.add('shot');

  /* ───────── theme and transparency ───────── */
  const mq = window.matchMedia('(prefers-color-scheme: dark)');
  function applyTheme() {
    const dark = P.state.theme === 'dark' || (P.state.theme === 'system' && mq.matches);
    document.documentElement.classList.toggle('dark', dark);
    if (P.state.reduced) document.documentElement.setAttribute('data-transparency', 'reduced');
    else document.documentElement.removeAttribute('data-transparency');
  }
  mq.addEventListener('change', applyTheme);
  applyTheme();

  /* ───────── navigation chrome ───────── */
  const STUDENT_NAV = [
    ['today', 'Today', 'sun', '/today'], ['weeks', 'Weeks', 'calendar-days', '/weeks'], ['exams', 'Mock exams', 'clipboard-check', '/exams'],
    ['grammar', 'Grammar', 'text', '/grammar'], ['vocabulary', 'Vocabulary', 'book-a', '/vocabulary'], ['progress', 'Progress', 'chart-column', '/progress'],
    ['ai-tutor', 'AI tutor', 'sparkles', '/ai-tutor'],
  ];
  const TUTOR_NAV = [
    ['students', 'Students', 'users', '/tutor/students'], ['plans', 'Plans', 'calendar-check', '/tutor/students/maria/weeks/8'],
    ['materials', 'Materials', 'library', '/tutor/materials'], ['tutor-billing', 'Billing', 'credit-card', '/billing?role=tutor'],
  ];
  /* iOS tabs: SF Symbols sun.max, calendar, play.fill, chart.bar, person.crop.circle */
  const TABS = [['today', 'Today', 'sun', '/today'], ['weeks', 'Weeks', 'calendar', '/weeks'], ['practice', 'Practice', 'play', '/practice'],
    ['progress', 'Progress', 'chart-column', '/progress'], ['profile', 'Profile', 'circle-user', '/profile']];

  function sidebar(def) {
    const s = P.fx.student;
    const item = ([id, label, icon, href], badge) =>
      `<a class="nav-item ${def.nav === id ? 'on' : ''}" href="#${href}" ${def.nav === id ? 'aria-current="page"' : ''}>${P.i(icon)}<span>${label}</span>${badge ? `<span class="badge">${badge}</span>` : ''}</a>`;
    const tutor = def.role === 'tutor';
    const who = tutor ? { n: 'Anna K.', i: 'AK', r: 'Tutor' } : { n: s.short, i: s.initials, r: 'Student' };
    return `<nav class="sidebar glass" aria-label="Main">
      <a href="#${tutor ? '/tutor/students' : '/today'}" class="logo">TOE<b>FLY</b></a>
      ${tutor ? `<div class="nav-group">Tutor</div>${TUTOR_NAV.map((n) => item(n)).join('')}` : STUDENT_NAV.map((n) => item(n)).join('')}
      <div class="nav-spacer"></div>
      ${item(['notifications', 'Notifications', 'bell', '/notifications'], tutor ? 2 : s.unreadNotifications)}
      ${item(['settings', 'Settings', 'settings', '/settings'])}
      <div class="nav-user">${P.avatar(who.i)}<div class="stack"><span class="t-strong" style="font-size:13px">${who.n}</span><span class="muted" style="font-size:12px">${who.r}</span></div>
        <a class="btn btn-icon sm" style="margin-left:auto" href="#${tutor ? '/today' : '/tutor/students'}" title="Switch to ${tutor ? 'student' : 'tutor'} view" aria-label="Switch to ${tutor ? 'student' : 'tutor'} view">${P.i('repeat-2')}</a></div>
    </nav>`;
  }

  function tabbar(def) {
    return `<nav class="tabbar glass" aria-label="Tabs">${TABS.map(([id, label, icon, href]) =>
      `<a class="tab ${def.tab === id ? 'on' : ''}" href="#${href}">${P.i(icon)}<span>${label}</span></a>`).join('')}</nav>`;
  }

  const fab = () => `<button class="fab glass" data-action="tutor" aria-label="AI tutor">${P.i('sparkles')}</button>`;

  /* ───────── router ───────── */
  function parse() {
    const raw = location.hash.slice(1) || '/sign-in';
    const [path, qs] = raw.split('?');
    return { path, query: Object.fromEntries(new URLSearchParams(qs || '')) };
  }

  function render() {
    if (cleanup) { try { cleanup(); } catch (e) { console.error(e); } cleanup = null; }
    const { path, query } = parse();
    const ios = P.state.frame === 'ios';
    const def = P.screens[path] || P.screens['/404'];
    const ctx = { ios, web: !ios, fx: P.fx, query, def, path };
    P.ctx = ctx;

    let content;
    if (ios && !def.ios) {
      content = `<div class="page" style="padding-top:120px">${P.empty({ title: 'Web-only screen', text: `"${def.title}" is part of the web prototype only.`, btn: '<button class="btn btn-primary" data-proto-frame="web">Open in web frame</button>' })}</div>`;
    } else {
      try { content = def.render(ctx); } catch (e) { console.error(e); content = `<div class="page"><div class="banner danger">${P.esc(e.message)}</div></div>`; }
    }

    const layout = ios && !def.ios ? 'app' : def.layout;
    const showFab = def.fab && (layout === 'app' || layout === 'player');
    const chrome = layout === 'app' ? (ios ? '' : sidebar(def)) : '';
    const html = `<div class="app ${ios ? 'ios' : ''} ${layout === 'player' || layout === 'bare' ? 'app-plain' : ''}" data-layout="${layout}">
      ${P.mesh(layout === 'auth' ? 'auth' : 'app')}
      <div class="shell">${chrome}<main class="main" id="main">${content}</main></div>
      ${layout === 'app' && ios ? tabbar(def) : ''}
      ${showFab ? fab() : ''}
      <div class="overlay-root" id="ov" hidden></div>
      <div class="toast-root" id="toasts" role="status"></div>
    </div>`;

    const host = document.getElementById(ios ? 'ios-host' : 'web-host');
    host.innerHTML = html;
    if (!ios) document.getElementById('url').textContent = 'localhost:5173' + (path === '/landing' ? '/' : path);
    /* Back link to the parent screen, same place on every nested page */
    const back = typeof def.back === 'function' ? def.back(ctx) : def.back;
    const page = host.querySelector('.main > .page, .main > [data-page]');
    if (back && page && back[ios ? 'ios' : 'web'] !== false) {
      page.insertAdjacentHTML('afterbegin', `<a class="back-link" href="#${back.href}" aria-label="Back to ${P.esc(back.label)}">${P.i('chevron-left')}<span>${back.label}</span></a>`);
    }
    window.lucide && window.lucide.createIcons();
    const appEl = host.querySelector('.app');
    if (def.mount && !(ios && !def.ios)) {
      try { cleanup = def.mount(appEl, ctx) || null; } catch (e) { console.error(e); }
    }
    syncToolbar(def);
    document.title = `${def.title} · TOEFLY prototype`;
  }
  P.render = render;

  /* ───────── frames ───────── */
  function buildStage() {
    const ios = P.state.frame === 'ios';
    document.getElementById('stage').innerHTML = ios
      ? `<div class="frame-holder" id="holder"><div class="frame-ios" id="frame"><div class="phone"><div class="phone-screen">
           <div class="statusbar"><span>9:41</span><span class="sb-r">${P.i('signal')}${P.i('wifi')}${P.i('battery-full')}</span></div>
           <div class="island"></div><div id="ios-host"></div><div class="home-ind"></div></div></div></div></div>`
      : `<div class="frame-holder" id="holder"><div class="frame-web" id="frame"><div class="browser-bar"><span class="dots"><i></i><i></i><i></i></span>
           <span class="url">${P.i('lock')}<span id="url"></span></span><span style="width:52px"></span></div>
           <div class="web-viewport" id="web-host"></div></div></div>`;
    fit();
  }
  function fit() {
    const ios = P.state.frame === 'ios';
    const W = ios ? 426 : 1440, H = ios ? 898 : 940;
    const availW = window.innerWidth - 32, availH = window.innerHeight - 80;
    const s = shot ? 1 : Math.min(availW / W, availH / H, 1);
    const holder = document.getElementById('holder'), frame = document.getElementById('frame');
    if (!holder) return;
    frame.style.transform = `scale(${s})`;
    holder.style.width = W * s + 'px';
    holder.style.height = (ios ? 898 : 900) * s + 'px';
  }
  window.addEventListener('resize', fit);

  /* ───────── overlays ───────── */
  function ov() { return document.getElementById('ov'); }
  P.closeOverlay = function () {
    const o = ov();
    if (!o || o.hidden) return;
    o.classList.remove('open');
    if (o._cleanup) { o._cleanup(); o._cleanup = null; }
    setTimeout(() => { o.hidden = true; o.innerHTML = ''; }, 360);
  };
  /* kind: dialog (web glass dialog / iOS sheet) | panel (web side panel / iOS large sheet) | sheet */
  P.openOverlay = function (inner, o = {}) {
    const el = ov();
    const ios = P.state.frame === 'ios';
    let box;
    if (o.kind === 'panel') box = ios ? `<div class="sheet large glass" role="dialog" aria-modal="true" aria-label="${o.label || ''}"><div class="grabber"></div>${inner}</div>` : `<aside class="side-panel glass" role="dialog" aria-label="${o.label || ''}">${inner}</aside>`;
    else box = ios ? `<div class="sheet glass ${o.large ? 'large' : ''}" role="dialog" aria-modal="true" aria-label="${o.label || ''}"><div class="grabber"></div>${inner}</div>` : `<div class="dialog glass" role="dialog" aria-modal="true" aria-label="${o.label || ''}" style="${o.width ? 'width:' + o.width + 'px' : ''}">${inner}</div>`;
    el.innerHTML = `${o.kind === 'panel' && !ios ? '<div class="scrim" data-action="close" style="background:transparent"></div>' : '<div class="scrim" data-action="close"></div>'}${box}`;
    el.hidden = false;
    window.lucide && window.lucide.createIcons();
    requestAnimationFrame(() => requestAnimationFrame(() => el.classList.add('open')));
    if (o.mount) el._cleanup = o.mount(el) || null;
    return el;
  };

  P.toast = function (text, icon = 'circle-check') {
    const root = document.getElementById('toasts');
    if (!root) return;
    const t = document.createElement('div');
    t.className = 'toast';
    t.innerHTML = `${P.i(icon)}<span>${text}</span>`;
    root.appendChild(t);
    window.lucide && window.lucide.createIcons();
    setTimeout(() => t.remove(), 5000);
  };

  /* Streaming imitation: "Thinking…" dots, then word-by-word text with a caret */
  P.stream = function (el, html, done) {
    el.innerHTML = '<span class="thinking"><i></i><i></i><i></i>&nbsp;Thinking…</span>';
    const tokens = html.split(/(\s+|<[^>]+>)/).filter(Boolean);
    let n = 0, timer;
    const start = setTimeout(() => {
      timer = setInterval(() => {
        n += 2;
        el.innerHTML = tokens.slice(0, n).join('') + (n < tokens.length ? '<span class="caret"></span>' : '');
        if (n >= tokens.length) { clearInterval(timer); done && done(); }
      }, 45);
    }, 800);
    return () => { clearTimeout(start); clearInterval(timer); };
  };

  /* AI tutor contextual panel */
  P.openTutor = function (contextLabel) {
    const def = P.ctx.def;
    const label = contextLabel || def.tutorContext || def.title;
    const hint = def.tutorHint;
    const q = hint ? 'Why is B wrong?' : (def.tutorQuestion || 'What should I focus on this week?');
    const a = hint
      ? "<p>I won't give the answer yet. What do you think the speaker's main point is?</p><p>Listen again to the part about <b>mitochondria</b>: the speaker contrasts two ideas. Which option keeps that contrast?</p>"
      : (def.tutorAnswer || "<p>This week your weakest spot is <b>Build a Sentence</b>: 4 of your last 10 sentences put <code>not</code> in the wrong place.</p><ul><li>Finish <b>Build a sentence: Practice 6</b> on Wednesday.</li><li>Review <b>Round-Up Unit 7: Conditionals</b>, you wrote <i>\"If I will go…\"</i> twice.</li><li>Do Attempt 2 of <b>Longevity</b>: aim for 110 words.</li></ul>");
    const chips = hint ? ['Why is B wrong?', 'Give me a similar question', 'Explain the grammar rule'] : ['What should I focus on this week?', 'Quiz me on Week 7 words', 'Explain my last Writing score'];
    const inner = `<div class="row between" style="padding:16px 18px 10px">
        <div class="row gap-2">${P.i('sparkles', 'ic-18')}<span class="t-title-3">Toefly Tutor</span></div>
        <div class="row gap-2"><span class="t-caption muted">Explain in Russian</span><button class="switch" data-toggle aria-label="Explain in Russian"></button>${P.btn('Close', { v: 'icon', icon: 'x', size: 'sm', attrs: 'data-action="close"' })}</div></div>
      <div style="padding:0 18px 10px"><span class="ctx-chip">${P.i('map-pin')}${P.esc(label)}</span></div>
      <div class="grow" style="overflow:auto;padding:6px 18px 12px" id="tlog"><div class="chat-log">
        <div class="tool-line">${P.i('eye')}Looked at your last 3 essays</div>
        <div class="bubble-me">${q}</div>
        <div class="bubble-ai"><div class="who">${P.i('sparkles')}Toefly Tutor</div><div class="ans"></div>
          <div class="chat-actions" hidden>${P.btn('Copy', { v: 'icon', icon: 'copy', size: 'sm' })}${P.btn('Helpful', { v: 'icon', icon: 'thumbs-up', size: 'sm' })}${P.btn('Not helpful', { v: 'icon', icon: 'thumbs-down', size: 'sm' })}${P.btn('Explain in Russian', { v: 'ghost', size: 'sm', icon: 'languages' })}</div></div>
      </div></div>
      <div style="padding:10px 18px 18px" class="stack gap-2">
        <div class="row gap-2 wrap">${chips.map((c) => `<button class="chip" data-ask="${c}">${c}</button>`).join('')}</div>
        <div class="t-caption muted">3 messages left today</div>
        <div class="chat-input"><textarea rows="1" placeholder="Ask anything about your task" aria-label="Message"></textarea>${P.btn('Send', { v: 'primary', icon: 'send', size: 'sm', cls: 'send' })}</div>
      </div>`;
    P.openOverlay(`<div class="stack" style="height:100%">${inner}</div>`, {
      kind: 'panel', label: 'AI tutor',
      mount(el) {
        let stop = [];
        const run = (ans, actions) => stop.push(P.stream(ans, a, () => { actions.hidden = false; window.lucide.createIcons(); }));
        run(el.querySelector('.ans'), el.querySelector('.chat-actions'));
        const ask = (text) => {
          if (!text.trim()) return;
          const log = el.querySelector('.chat-log');
          log.insertAdjacentHTML('beforeend', `<div class="bubble-me">${P.esc(text)}</div><div class="bubble-ai"><div class="who">${P.i('sparkles')}Toefly Tutor</div><div class="ans"></div><div class="chat-actions" hidden></div></div>`);
          window.lucide.createIcons();
          const last = log.lastElementChild;
          stop.push(P.stream(last.querySelector('.ans'), "<p>Good question. Let's look at it step by step: first find the sentence in the passage that answers it, then compare each option with that sentence. Which one says the same thing in different words?</p>", () => {}));
          const sc = el.querySelector('#tlog'); sc.scrollTop = sc.scrollHeight;
        };
        el.addEventListener('click', (e) => {
          const c = e.target.closest('[data-ask]'); if (c) ask(c.dataset.ask);
          if (e.target.closest('.send')) { const t = el.querySelector('textarea'); ask(t.value); t.value = ''; }
        });
        el.querySelector('textarea').addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); ask(e.target.value); e.target.value = ''; } });
        return () => stop.forEach((f) => f());
      },
    });
  };

  /* PaywallSheet */
  const PAYWALL = {
    UNLIMITED_WEEKS: 'Unlock unlimited weeks', AI_WRITING_CHECK: 'Get AI writing checks', MOCK_EXAM: 'Mock exams are part of Pro', AI_TUTOR_CHAT: 'Chat with Toefly Tutor without limits',
  };
  P.openPaywall = function (reason = 'UNLIMITED_WEEKS') {
    const perks = ['A new week every week, built from your mistakes', 'Unlimited AI checks for Writing and Speaking', 'Adaptive mock exams with 1–6 and 0–120 scores', 'Toefly Tutor chat and weekly reviews'];
    P.openOverlay(`<div class="stack gap-4" style="align-items:center;text-align:center">
        <span class="icon-box" style="width:56px;height:56px;border-radius:50%">${P.i('crown', 'ic-24')}</span>
        <div class="t-title-2">${PAYWALL[reason] || PAYWALL.UNLIMITED_WEEKS}</div>
        <ul class="stack gap-2" style="list-style:none;padding:0;text-align:left;align-self:stretch">${perks.map((p) => `<li class="row gap-2" style="align-items:flex-start"><span style="color:var(--accent)">${P.i('check', 'ic-18')}</span>${p}</li>`).join('')}</ul>
        <div class="stack gap-1"><div class="t-title-2">€12 / month</div><div class="muted t-caption">7 days free after sign-up · 2 of 10 free AI checks left</div></div>
        <div class="stack gap-2" style="align-self:stretch">${P.btn('Upgrade to Pro', { block: true, attrs: 'data-action="checkout"' })}${P.btn('Not now', { v: 'ghost', block: true, attrs: 'data-action="close"' })}</div>
        <div class="t-caption muted">Cancel anytime. Prices in EUR.</div></div>`, { label: 'Upgrade to Pro' });
  };

  /* ───────── global events ───────── */
  document.addEventListener('click', (e) => {
    const a = e.target.closest('[data-action]');
    if (a) {
      const act = a.dataset.action;
      if (act === 'close') P.closeOverlay();
      else if (act === 'tutor') P.openTutor(a.dataset.context);
      else if (act === 'paywall') P.openPaywall(a.dataset.reason);
      else if (act === 'checkout') { P.closeOverlay(); P.go('/billing?checkout=success'); }
      else if (act === 'toast') P.toast(a.dataset.text || 'Saved');
    }
    const sw = e.target.closest('.switch[data-toggle], .switch.toggle');
    if (sw) sw.classList.toggle('on');
    const ck = e.target.closest('.check[data-toggle]');
    if (ck) { ck.classList.toggle('on'); ck.setAttribute('aria-checked', ck.classList.contains('on')); }
    const pf = e.target.closest('[data-proto-frame]');
    if (pf) setFrame(pf.dataset.protoFrame);
  });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') { P.closeOverlay(); menu(false); } });

  /* ───────── prototype toolbar ───────── */
  const GROUPS = [
    ['Key screens', (d) => typeof d.num === 'number'],
    ['Supporting screens', (d) => typeof d.num !== 'number' && !d.hidden],
  ];
  function buildToolbar() {
    document.getElementById('proto').innerHTML = `
      <span class="pb-logo">TOE<b>FLY</b> prototype</span>
      <div class="proto-seg" role="group" aria-label="Frame"><button data-frame="web">Web</button><button data-frame="ios">iPhone</button></div>
      <div class="proto-seg" role="group" aria-label="Theme"><button data-theme="system">System</button><button data-theme="light">Light</button><button data-theme="dark">Dark</button></div>
      <label class="proto-toggle"><input type="checkbox" id="reduced"> Reduce transparency</label>
      <button class="proto-btn" id="menuBtn">${P.i('layout-grid')}Screens</button>`;
    document.querySelectorAll('[data-frame]').forEach((b) => b.addEventListener('click', () => setFrame(b.dataset.frame)));
    document.querySelectorAll('[data-theme]').forEach((b) => b.addEventListener('click', () => { P.state.theme = b.dataset.theme; save(); applyTheme(); syncToolbar(); }));
    const rd = document.getElementById('reduced');
    rd.addEventListener('change', () => { P.state.reduced = rd.checked; save(); applyTheme(); });
    document.getElementById('menuBtn').addEventListener('click', () => menu());
  }
  function menu(force) {
    const m = document.getElementById('pmenu');
    const open = force === undefined ? !m.classList.contains('open') : force;
    m.classList.toggle('open', open);
    if (!open) return;
    const cur = parse().path;
    const defs = P.order.map((p) => P.screens[p]);
    m.innerHTML = GROUPS.map(([title, f]) => `<h3>${title}</h3><div class="pm-grid">${defs.filter(f)
      .sort((a, b) => (typeof a.num === 'number' && typeof b.num === 'number' ? a.num - b.num : 0))
      .map((d) => `<a href="#${d.path}" class="${d.path === cur ? 'cur' : ''}"><span class="n">${typeof d.num === 'number' ? d.num : '·'}</span><span>${d.title}</span><span class="f">${[d.web ? 'Web' : '', d.ios ? 'iPhone' : ''].filter(Boolean).join(' · ')}</span></a>`).join('')}</div>`).join('') +
      `<h3>Overlays</h3><div class="pm-grid"><a href="javascript:void 0" data-overlay="tutor"><span class="n">20</span><span>AI tutor panel (streaming)</span><span class="f">Web · iPhone</span></a><a href="javascript:void 0" data-overlay="paywall"><span class="n">21</span><span>Paywall sheet</span><span class="f">Web · iPhone</span></a></div>
       <p>Demo path: Sign in → Onboarding → Today → Week 7 → Complete the Words → Review → Ask tutor. Every screen is reachable by clicking inside the product too.</p>`;
    m.querySelectorAll('a[href^="#"]').forEach((a) => a.addEventListener('click', () => menu(false)));
    m.querySelectorAll('[data-overlay]').forEach((a) => a.addEventListener('click', () => { menu(false); a.dataset.overlay === 'tutor' ? P.openTutor() : P.openPaywall(); }));
    window.lucide.createIcons();
  }
  function syncToolbar() {
    document.querySelectorAll('[data-frame]').forEach((b) => b.setAttribute('aria-pressed', b.dataset.frame === P.state.frame));
    document.querySelectorAll('[data-theme]').forEach((b) => b.setAttribute('aria-pressed', b.dataset.theme === P.state.theme));
    const rd = document.getElementById('reduced'); if (rd) rd.checked = !!P.state.reduced;
  }
  function setFrame(f) { P.state.frame = f; save(); buildStage(); render(); }

  /* ───────── boot ───────── */
  window.addEventListener('hashchange', () => { P.closeOverlay(); render(); });
  P.boot = async function () {
    await P.loadFixtures(P.fixtureList);
    buildToolbar();
    buildStage();
    render();
  };
})();
