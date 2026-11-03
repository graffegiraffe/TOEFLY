/* Task player screens. Practice mode, solid task zone, no glass.
   Prototype-only: ?t=<seconds> in the hash sets the Practice timer start (to see the +mm:ss overrun),
   speaking timers run faster than real time so the demo is watchable. */
(function () {
  const P = window.P;
  const icons = () => window.lucide && window.lucide.createIcons();
  const pad = (n) => String(n).padStart(2, '0');
  const mmss = (s) => `${pad(Math.floor(s / 60))}:${pad(s % 60)}`;
  const reduceMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const REVIEW = '/review/attempt';

  /* ───────── chrome ───────── */
  /* c: { section, title, timer (seconds, 0 = none), foot: 'qa' | 'submit' | 'speak', sub } */
  function chrome(ctx, c) {
    const tools = c.timer
      ? `${P.timer(mmss(c.timer), { attrs: 'data-pl="timer" aria-live="off"' })}
         <button type="button" class="btn btn-icon sm" data-pl="hide" aria-label="Hide timer" title="Hide timer">${P.i('eye-off')}</button>
         <button type="button" class="btn btn-secondary sm" data-pl="pause" aria-label="Pause">${P.i('pause')}<span>Pause</span></button>`
      : '<span></span>';
    const right = c.foot === 'speak'
      ? P.btn('Save and exit', { v: 'ghost', href: `/weeks/7/${c.section.toLowerCase()}` })
      : `${c.foot === 'qa' ? P.btn('Back', { v: 'ghost', attrs: 'data-pl="back"' }) : ''}${P.btn(c.foot === 'submit' ? 'Submit' : 'Next', { iconRight: c.foot === 'submit' ? 'send' : 'chevron-right', attrs: 'data-pl="next"' })}`;
    return `<div class="pl" data-pl-root>
      <header class="pl-head">
        <a class="pl-back" href="#/weeks/7/${c.section.toLowerCase()}" aria-label="Back to Week 7 · ${c.section}">${P.i('chevron-left')}<span>Week 7 · ${c.section}</span></a>
        <div class="pl-titles"><div class="pl-title">${c.title}</div><div class="pl-sub" data-pl="sub">${ctx.ios ? (c.sub || 'Question 1 of 1 · Set 1 of 1').replace(' · Set 1 of 1', '') : c.sub || 'Question 1 of 1 · Set 1 of 1'}</div></div>
        <div class="pl-tools">${tools}</div>
      </header>
      <div class="pl-main"><div class="pl-body" data-pl="body"></div>
        <div class="pl-paused" data-pl="paused" hidden>${P.i('pause', 'ic-24')}<div class="t-title-3">Paused</div><div class="muted">The timer is stopped.</div>${P.btn('Resume', { icon: 'play', attrs: 'data-pl="resume"' })}</div></div>
      <footer class="pl-foot">
        <div class="pl-foot-l"><button type="button" class="btn btn-ghost sm" data-pl="report" aria-label="Report">${P.i('flag')}<span>Report</span></button></div>
        <nav class="pl-dots" data-pl="dots" aria-label="Questions"></nav>
        <div class="pl-foot-r">${right}</div>
      </footer>
    </div>`;
  }

  function Player(root, ctx, c) {
    const $ = (s) => root.querySelector(s);
    const offs = [];
    const tms = [];
    let elapsed = 0;
    const h = { dot: null, back: null, next: null };
    const api = { root, h, body: $('[data-pl="body"]'), paused: false };

    api.after = (fn, ms) => { const id = setTimeout(fn, ms); tms.push(id); return id; };
    api.every = (fn, ms) => { const id = setInterval(fn, ms); tms.push(id); return id; };
    api.clear = (id) => { clearTimeout(id); clearInterval(id); };
    api.on = (t, ev, fn, o) => { t.addEventListener(ev, fn, o); offs.push(() => t.removeEventListener(ev, fn, o)); };
    api.sub = (t) => { $('[data-pl="sub"]').textContent = ctx.ios ? t.replace(' · Set 1 of 1', '') : t; };
    api.destroy = () => { tms.forEach((id) => { clearTimeout(id); clearInterval(id); }); offs.forEach((f) => f()); };

    api.dots = (n, cur, done, clickable = true) => {
      const el = $('[data-pl="dots"]');
      el.classList.toggle('static', !clickable);
      el.innerHTML = Array.from({ length: n }, (_, i) => {
        const d = done && done[i];
        return `<button type="button" class="pl-dot ${i === cur ? 'cur' : ''} ${d ? 'done' : ''}" data-i="${i}" aria-label="Question ${i + 1}${d ? ', answered' : ''}" ${i === cur ? 'aria-current="step"' : ''} ${clickable ? '' : 'disabled tabindex="-1"'}><i></i></button>`;
      }).join('');
    };
    api.next = (o) => {
      const b = $('[data-pl="next"]'); if (!b) return;
      if (o.label) { b.innerHTML = `<span>${o.label}</span>${P.i(o.icon || 'chevron-right')}`; icons(); }
      if (o.disabled !== undefined) b.disabled = !!o.disabled;
    };
    api.back = (o) => { const b = $('[data-pl="back"]'); if (b) b.disabled = !!o.disabled; };

    /* timer: counts down, then +mm:ss in warning color */
    if (c.timer) {
      const total = ctx.query.t !== undefined && ctx.query.t !== '' ? Math.max(0, +ctx.query.t || 0) : c.timer;
      const paint = () => {
        const el = $('[data-pl="timer"]'); if (!el) return;
        const left = total - elapsed;
        el.querySelector('.tv').textContent = left >= 0 ? mmss(left) : '+' + mmss(-left);
        el.classList.toggle('warning', left < 0);
      };
      paint();
      api.every(() => { if (!api.paused) { elapsed += 1; paint(); } }, 1000);
    }

    const setPaused = (p) => {
      api.paused = p;
      $('[data-pl="paused"]').hidden = !p;
      const b = $('[data-pl="pause"]');
      if (b) {
        b.innerHTML = `${P.i(p ? 'play' : 'pause')}<span>${p ? 'Resume' : 'Pause'}</span>`;
        b.setAttribute('aria-label', p ? 'Resume' : 'Pause');
        icons();
      }
    };

    api.on(root, 'click', (e) => {
      const dot = e.target.closest('.pl-dot');
      if (dot && h.dot) { h.dot(+dot.dataset.i); return; }
      const a = e.target.closest('[data-pl]');
      if (!a) return;
      const k = a.dataset.pl;
      if (k === 'pause') setPaused(!api.paused);
      else if (k === 'resume') setPaused(false);
      else if (k === 'hide') {
        const t = $('[data-pl="timer"]');
        t.hidden = !t.hidden;
        a.innerHTML = P.i(t.hidden ? 'eye' : 'eye-off');
        a.setAttribute('aria-label', t.hidden ? 'Show timer' : 'Hide timer');
        a.title = t.hidden ? 'Show timer' : 'Hide timer';
        icons();
      } else if (k === 'report') openReport();
      else if (k === 'back' && h.back) h.back();
      else if (k === 'next' && h.next) h.next();
    });

    /* McqOption keyboard: A-D choose, arrows move the choice inside the radiogroup */
    api.mcqKeys = (count, get, pick) => {
      api.on(document, 'keydown', (e) => {
        if (e.ctrlKey || e.metaKey || e.altKey || e.defaultPrevented || api.paused) return;
        const t = e.target;
        if (/^(input|textarea|select)$/i.test(t.tagName || '')) return;
        const ovEl = document.getElementById('ov');
        if (ovEl && !ovEl.hidden) return;
        const n = count();
        if (!n) return;
        const idx = 'abcd'.indexOf(e.key.toLowerCase());
        if (e.key.length === 1 && idx > -1 && idx < n) { e.preventDefault(); pick(idx, true); return; }
        const step = { ArrowDown: 1, ArrowRight: 1, ArrowUp: -1, ArrowLeft: -1 }[e.key];
        if (step && t.closest && t.closest('[role="radiogroup"]')) { e.preventDefault(); pick((Math.max(get(), 0) + step + n) % n, true); }
      });
    };
    api.mcqClicks = (pick) => {
      api.on(api.body, 'click', (e) => { const o = e.target.closest('.mcq-opt'); if (o) pick(+o.dataset.i, false); });
    };
    return api;
  }

  function mcqHtml(opts, sel, label) {
    return `<div class="mcq practice" role="radiogroup" aria-label="${label}">${opts.map((t, i) =>
      `<button type="button" class="mcq-opt ${sel === i ? 'sel' : ''}" role="radio" aria-checked="${sel === i}" tabindex="${sel === i || (sel < 0 && i === 0) ? 0 : -1}" data-i="${i}"><span class="radio"></span><span class="letter">${'ABCD'[i]}</span><span>${P.esc(t)}</span></button>`).join('')}</div>`;
  }
  function paintMcq(scope, sel, focus) {
    scope.querySelectorAll('.mcq-opt').forEach((b, i) => {
      const on = i === sel;
      b.classList.toggle('sel', on);
      b.setAttribute('aria-checked', on);
      b.tabIndex = on || (sel < 0 && i === 0) ? 0 : -1;
      if (on && focus) b.focus();
    });
  }
  const kbdHint = (ctx) => (ctx.web ? '<div class="pl-kbd">Keyboard shortcuts: <span class="kbd">A</span>–<span class="kbd">D</span> choose an answer, <span class="kbd">↑</span> <span class="kbd">↓</span> change the choice.</div>' : '');

  /* Report sheet: ItemReport */
  function openReport() {
    const reasons = ['Wrong answer key', 'Unclear question', 'Audio problem', 'Offensive content', 'Other'];
    P.openOverlay(`<div class="rp">
        <div class="t-title-2">Report a problem</div>
        <div class="mcq" role="radiogroup" aria-label="Reason">${reasons.map((r, i) =>
          `<button type="button" class="mcq-opt" role="radio" aria-checked="false" data-i="${i}"><span class="radio"></span><span>${r}</span></button>`).join('')}</div>
        <textarea class="input" rows="3" placeholder="Add a comment (optional)" aria-label="Comment" autocomplete="off"></textarea>
        <div class="rp-actions">${P.btn('Cancel', { v: 'ghost', attrs: 'data-action="close"' })}${P.btn('Send report', { icon: 'send', attrs: 'data-send disabled' })}</div>
      </div>`, {
      label: 'Report a problem',
      mount(el) {
        const send = el.querySelector('[data-send]');
        el.addEventListener('click', (e) => {
          const o = e.target.closest('.mcq-opt');
          if (o) {
            el.querySelectorAll('.mcq-opt').forEach((b) => { const on = b === o; b.classList.toggle('sel', on); b.setAttribute('aria-checked', on); });
            send.disabled = false;
          }
          if (e.target.closest('[data-send]')) { P.closeOverlay(); P.toast('Thanks. Your report was sent to the content team.', 'flag'); }
        });
      },
    });
  }

  /* ───────── line-art scenes ───────── */
  const svg = (inner) => `<svg viewBox="0 0 320 180" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${inner}</svg>`;
  const SCENE_LECTURE = svg(`<rect x="70" y="22" width="190" height="82" rx="6"/><circle cx="125" cy="63" r="22"/><circle cx="119" cy="58" r="7"/><path d="M137 70c5 6 11-3 17 2"/>
    <path d="M180 48h60M180 62h48M180 76h56"/><circle cx="40" cy="78" r="10"/><path d="M40 88v38M40 100l-16 14M40 100l18-8M40 126l-10 20M40 126l10 20"/>
    <rect x="86" y="130" width="52" height="18" rx="4"/><rect x="156" y="130" width="52" height="18" rx="4"/><rect x="226" y="130" width="52" height="18" rx="4"/>
    <circle cx="112" cy="116" r="8"/><circle cx="182" cy="116" r="8"/><circle cx="252" cy="116" r="8"/><path d="M20 160h280"/>
    <path d="M270 34a14 14 0 0 1 28 0"/><rect x="266" y="34" width="7" height="12" rx="3"/><rect x="295" y="34" width="7" height="12" rx="3"/>`);
  const SCENE_GYM = svg(`<rect x="24" y="20" width="96" height="70" rx="6"/><path d="M72 20v70M24 55h96"/><path d="M20 150h280"/>
    <path d="M170 150l22-62h46"/><path d="M238 88l16 62"/><path d="M160 150h104"/><rect x="226" y="76" width="30" height="12" rx="3"/>
    <circle cx="70" cy="118" r="10"/><path d="M70 128v18M70 134l-14 8M70 134l14 8"/>
    <path d="M276 126h22M276 142h22M272 122v24M302 122v24"/>`);

  /* ───────── 1. Complete the Words ───────── */
  const CTW_TEXT = 'Bees are among the most important pollinators in the world. Each spring, {wor|ker} bees {lea|ve} the hive to {coll|ect} nectar from flowering plants. While {feed|ing}, they pick up {pol|len} on their {bod|ies} and carry it from flower to flower. This transfer {all|ows} many crops to {prod|uce} fruit and seeds. In recent {deca|des}, scientists have {reco|rded} a steady decline in wild bee populations. Protecting meadows and limiting pesticides could help these insects recover. Their survival matters for every farm and every meal.';

  function ctwHtml() {
    let n = 0;
    const html = CTW_TEXT.replace(/\{(\w+)\|(\w+)\}/g, (_, pre, miss) => {
      n += 1;
      return `<span class="gap">${pre}<input class="gap-in" style="--n:${miss.length}" maxlength="${miss.length}" data-gap="${n}" type="text" inputmode="text" autocomplete="off" autocorrect="off" autocapitalize="off" spellcheck="false" aria-label="Gap ${n} of 10, word starts with '${pre}'"></span>`;
    });
    return `<div class="pl-zone"><div class="pl-instruction" style="text-align:center">Fill in the missing letters in the paragraph.</div><p class="cz">${html}</p></div>`;
  }

  P.register('/tasks/ctw', {
    title: 'Task player: Complete the Words', num: 9, web: true, ios: true, layout: 'player', fab: true,
    tutorHint: true, tutorContext: 'Complete the Words · Paragraph about bees',
    render: (ctx) => chrome(ctx, { section: 'Reading', title: 'Complete the Words', timer: 180, foot: 'qa' }),
    mount(root, ctx) {
      const pl = Player(root, ctx, { timer: 180 });
      pl.body.innerHTML = ctwHtml();
      pl.dots(1, 0, [false], false);
      pl.back({ disabled: true });
      pl.next({ label: 'Check answers', icon: 'check' });
      pl.h.next = () => P.go(REVIEW);
      const inputs = [...pl.body.querySelectorAll('.gap-in')];
      inputs.forEach((inp, i) => {
        inp.addEventListener('input', () => {
          inp.value = inp.value.replace(/[^a-zA-Z]/g, '');
          if (inp.value.length >= inp.maxLength && inputs[i + 1]) { inputs[i + 1].focus(); inputs[i + 1].setSelectionRange(0, 0); }
        });
        inp.addEventListener('keydown', (e) => {
          if (e.key === 'Backspace' && !inp.value && inputs[i - 1]) { e.preventDefault(); inputs[i - 1].focus(); }
        });
      });
      return pl.destroy;
    },
  });

  /* ───────── 2. Read an Academic Passage ───────── */
  const PASSAGE = [
    'Coral reefs cover less than one percent of the ocean floor, yet they support roughly a quarter of all marine species. This remarkable diversity arises from the structure of the reef itself. Coral polyps, tiny animals related to jellyfish, secrete limestone skeletons that accumulate over thousands of years, forming a complex three-dimensional habitat full of crevices where fish and invertebrates find food and shelter.',
    'Corals depend on a partnership with microscopic algae that live inside their tissues. The algae use sunlight to produce sugars, which they share with the coral, and in return the coral provides them with protection and nutrients. Because this relationship is so finely balanced, reefs are found only in warm, clear, shallow water. Even a small rise in temperature can cause corals to expel their algae, turning white in a process known as bleaching.',
    'Yet reefs are not helpless. Some species have proved surprisingly <span class="hl" data-hl>resilient</span>, recovering within a decade when conditions improve. {1} Researchers have found that reefs shaded by clouds or cooled by deep currents survive heat waves better than exposed ones. {2} Conservation groups now focus on protecting these natural refuges. {3} They hope that healthy reefs will eventually repopulate damaged areas nearby. {4}',
  ];
  const PQ = [
    { q: 'According to paragraph 1, why do coral reefs support so many species?', o: ['They cover most of the ocean floor.', 'Their limestone structure creates many places to live.', 'Jellyfish provide food for the fish nearby.', 'Their skeletons grow back within a few years.'] },
    { q: 'The word "resilient" in paragraph 3 is closest in meaning to', o: ['quick to recover', 'easy to damage', 'difficult to find', 'slow to grow'], hl: true },
    { q: 'Why does the author explain the relationship between corals and algae in paragraph 2?', o: ['To compare corals with other animals that share food', 'To show why reefs need very specific conditions', 'To argue that algae are more important than corals', 'To describe how sugars are produced in the ocean'] },
    { q: 'What can be inferred about bleached corals from paragraph 2?', o: ['They have lost the partner that supplies their sugars.', 'They have moved to cooler and deeper water.', 'They have grown a thicker limestone skeleton.', 'They no longer need sunlight to survive.'] },
    { insert: true, q: 'Look at the four squares [■] that indicate where the following sentence could be added to the passage.', s: 'This pattern suggests that some reefs may act as refuges while the oceans warm.', o: ['Square A', 'Square B', 'Square C', 'Square D'] },
  ];

  function passageHtml() {
    const body = PASSAGE.map((p) => `<p>${p.replace(/\{(\d)\}/g, (_, n) => `<button type="button" class="ins-mark" data-pos="${n - 1}" aria-pressed="false" aria-label="Insert here, position ${'ABCD'[n - 1]}">■<sup>${'ABCD'[n - 1]}</sup></button>`)}</p>`).join('');
    return `<h2>Coral Reefs</h2>${body}`;
  }

  P.register('/tasks/passage', {
    title: 'Task player: Read an Academic Passage', num: 10, web: true, ios: false, layout: 'player', fab: true,
    tutorHint: true, tutorContext: 'Question 2 · Academic passage: Coral Reefs',
    render: (ctx) => chrome(ctx, { section: 'Reading', title: 'Read an Academic Passage', timer: 540, foot: 'qa', sub: 'Question 1 of 5 · Set 1 of 1' }),
    mount(root, ctx) {
      const pl = Player(root, ctx, { timer: 540 });
      const sel = [-1, -1, -1, -1, -1];
      let q = 0;
      pl.body.innerHTML = `<div class="pp"><div class="pp-passage" data-passage tabindex="0" aria-label="Passage: Coral Reefs">${passageHtml()}</div><div class="pp-question" data-q></div></div>`;
      const passage = pl.body.querySelector('[data-passage]');
      const qbox = pl.body.querySelector('[data-q]');

      const scrollTo = (el) => { if (el) passage.scrollTo({ top: Math.max(0, el.offsetTop - passage.clientHeight / 3), behavior: 'smooth' }); };
      const syncMarks = () => passage.querySelectorAll('.ins-mark').forEach((m, i) => {
        const on = q === 4 && sel[4] === i;
        m.classList.toggle('sel', on);
        m.setAttribute('aria-pressed', on);
      });
      const pick = (i, focus) => {
        sel[q] = i;
        paintMcq(qbox, i, focus);
        syncMarks();
        pl.dots(5, q, sel.map((s) => s >= 0));
      };
      const go = (n) => {
        q = n;
        const it = PQ[q];
        passage.classList.toggle('insert', !!it.insert);
        passage.querySelector('[data-hl]').classList.toggle('on', !!it.hl);
        qbox.innerHTML = it.insert
          ? `<div class="pl-card insert-card"><div class="t-overline">Sentence to insert</div><p>${P.esc(it.s)}</p></div>
             <div class="q-text mt-4">${P.esc(it.q)}</div>
             <div class="mcq practice pos-row" role="radiogroup" aria-label="Position">${it.o.map((t, i) => `<button type="button" class="mcq-opt ${sel[q] === i ? 'sel' : ''}" role="radio" aria-checked="${sel[q] === i}" tabindex="${sel[q] === i || (sel[q] < 0 && i === 0) ? 0 : -1}" data-i="${i}"><span class="radio"></span><span class="letter">${'ABCD'[i]}</span><span>■</span></button>`).join('')}</div>
             <div class="pl-kbd">Click a square in the passage or choose a position here.</div>${kbdHint(ctx)}`
          : `<div class="q-text">${P.esc(it.q)}</div>${mcqHtml(it.o, sel[q], 'Answers')}${kbdHint(ctx)}`;
        syncMarks();
        pl.sub(`Question ${q + 1} of 5 · Set 1 of 1`);
        pl.dots(5, q, sel.map((s) => s >= 0));
        pl.back({ disabled: q === 0 });
        pl.next(q === 4 ? { label: 'Check answers', icon: 'check' } : { label: 'Next', icon: 'chevron-right' });
        qbox.scrollTop = 0;
        if (it.hl) scrollTo(passage.querySelector('[data-hl]'));
        else if (it.insert) scrollTo(passage.querySelector('.ins-mark'));
        else passage.scrollTo({ top: 0, behavior: 'smooth' });
        icons();
      };
      pl.mcqClicks(pick);
      pl.mcqKeys(() => 4, () => sel[q], pick);
      pl.on(passage, 'click', (e) => { const m = e.target.closest('.ins-mark'); if (m && q === 4) pick(+m.dataset.pos, false); });
      pl.h.dot = go;
      pl.h.back = () => q > 0 && go(q - 1);
      pl.h.next = () => (q < 4 ? go(q + 1) : P.go(REVIEW));
      go(0);
      return pl.destroy;
    },
  });

  /* ───────── 3. Listen to an Academic Talk ───────── */
  const LQ = [
    { q: 'What is the main purpose of the talk?', o: ['To compare plant cells with animal cells', 'To explain how cells produce usable energy', 'To describe a new microscope technique', 'To argue that cells need less energy than expected'] },
    { q: 'According to the professor, why are mitochondria often called the powerhouses of the cell?', o: ['They store the genetic information of the cell.', 'They protect the cell from damage.', 'They convert nutrients into a form of energy the cell can use.', 'They move the cell from place to place.'] },
    { q: 'Why does the professor mention muscle cells?', o: ['To show that some cells contain many mitochondria', 'To explain why exercise is harmful to cells', 'To compare muscle cells with plant cells', 'To introduce the topic of the next lecture'] },
    { q: 'What does the professor imply about cells with very few mitochondria?', o: ['They divide faster than other cells.', 'They are likely to have low energy needs.', 'They cannot be studied under a microscope.', 'They are found only in older organisms.'] },
  ];

  P.register('/tasks/listen', {
    title: 'Task player: Listen to an Academic Talk', num: 11, web: true, ios: true, layout: 'player', fab: true,
    tutorHint: true, tutorContext: 'Question 3 · Academic talk: Biology',
    render: (ctx) => chrome(ctx, { section: 'Listening', title: 'Listen to an Academic Talk', timer: 360, foot: 'qa', sub: 'Listening · Set 1 of 1' }),
    mount(root, ctx) {
      const pl = Player(root, ctx, { timer: 360 });
      const TOTAL = 118; // 1:58 of audio, simulated with ~10 s of playback
      const fmt = (s) => `${Math.floor(s / 60)}:${pad(Math.floor(s % 60))}`;
      const sel = [-1, -1, -1, -1];
      let stage = 'intro', qi = 0, frac = 0, playing = false, speed = 1, replays = 0;
      let tick = null, last = Date.now();
      /* progress follows the clock (about 10 s for the whole recording) so throttled tabs stay in sync */
      const advance = (rate) => { const now = Date.now(); frac = Math.min(1, frac + ((now - last) / 10000) * rate); last = now; };

      const paintAudio = () => {
        const bar = pl.body.querySelector('.audio .bar');
        if (!bar) return;
        bar.firstElementChild.style.width = frac * 100 + '%';
        bar.setAttribute('aria-valuenow', Math.round(frac * 100));
        pl.body.querySelector('.audio .time').textContent = `${fmt(frac * TOTAL)} / ${fmt(TOTAL)}`;
      };
      const bar = () => `<div class="bar" role="progressbar" aria-label="Audio progress" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0"><i style="width:0%"></i></div>`;

      const intro = () => {
        pl.sub('Listening · Set 1 of 1');
        pl.dots(4, -1, [], false);
        pl.back({ disabled: true });
        pl.next({ disabled: true });
        pl.body.innerHTML = `<div class="ls-zone stack gap-5">
          <h2 class="t-title-3 center">Listen to part of a talk in a biology class.</h2>
          <div class="scene" role="img" aria-label="Illustration of a lecture hall">${SCENE_LECTURE}</div>
          <div class="audio" data-audio><button type="button" class="play" aria-disabled="true" aria-label="Audio plays automatically">${P.i('headphones')}</button>${bar()}
            <span class="time">0:00 / ${fmt(TOTAL)}</span><span class="state" data-state>Starts in 2 seconds</span></div>
          <div class="ls-hint">The audio plays once. Questions appear after it ends.</div></div>`;
        icons();
        pl.after(() => {
          playing = true;
          const st = pl.body.querySelector('[data-state]');
          pl.body.querySelector('.play').innerHTML = P.i('pause');
          st.textContent = 'Playing';
          icons();
          last = Date.now();
          tick = pl.every(() => {
            if (!playing || pl.paused) { last = Date.now(); return; }
            advance(1);
            paintAudio();
            if (frac >= 1) {
              pl.clear(tick);
              playing = false;
              st.innerHTML = `${P.i('circle-check')}Audio finished`;
              pl.body.querySelector('.play').innerHTML = P.i('check');
              icons();
              pl.after(() => showQ(0), 1200);
            }
          }, 100);
        }, 2000);
      };

      /* replay strip: unlocked after the first listen (Practice) */
      const strip = () => {
        const state = playing ? 'Replaying' : frac > 0 && frac < 1 ? 'Paused' : 'Audio finished';
        return `<div class="ls-strip"><div class="audio" data-audio>
          <button type="button" class="play" data-play aria-label="${playing ? 'Pause' : 'Play'}">${P.i(playing ? 'pause' : 'play')}</button>
          <span class="state">${state === 'Audio finished' ? P.i('circle-check') : ''}${state}</span>${bar()}<span class="time">0:00 / ${fmt(TOTAL)}</span>
          ${P.btn('Replay', { v: 'ghost', size: 'sm', icon: 'rotate-ccw', attrs: 'data-replay' })}
          <div class="seg" role="group" aria-label="Playback speed">${[0.75, 1, 1.25].map((s) => `<button type="button" class="${s === speed ? 'on' : ''}" data-speed="${s}" aria-pressed="${s === speed}">${s}×</button>`).join('')}</div>
          ${replays ? `<span class="t-caption muted">Replayed ${replays} ${replays === 1 ? 'time' : 'times'}</span>` : ''}</div></div>`;
      };
      const redrawStrip = () => { const s = pl.body.querySelector('.ls-strip'); if (s) { s.outerHTML = strip(); icons(); paintAudio(); } };
      const startReplay = (fromStart) => {
        if (fromStart) { frac = 0; replays += 1; }
        playing = true;
        pl.clear(tick);
        last = Date.now();
        tick = pl.every(() => {
          if (!playing || pl.paused) { last = Date.now(); return; }
          advance(speed);
          paintAudio();
          if (frac >= 1) { pl.clear(tick); playing = false; redrawStrip(); }
        }, 100);
        redrawStrip();
      };

      const showQ = (n) => {
        stage = 'q'; qi = n;
        pl.clear(tick); playing = false; frac = 0;
        pl.sub(`Question ${qi + 1} of 4 · Set 1 of 1`);
        pl.dots(4, qi, sel.map((s) => s >= 0));
        pl.back({ disabled: qi === 0 });
        pl.next(qi === 3 ? { label: 'Check answers', icon: 'check', disabled: false } : { label: 'Next', icon: 'chevron-right', disabled: false });
        pl.body.innerHTML = `<div class="ls-zone">${strip()}<div class="q-text">${P.esc(LQ[qi].q)}</div>${mcqHtml(LQ[qi].o, sel[qi], 'Answers')}${kbdHint(ctx)}</div>`;
        icons();
      };

      const pick = (i, focus) => {
        if (stage !== 'q') return;
        sel[qi] = i;
        paintMcq(pl.body, i, focus);
        pl.dots(4, qi, sel.map((s) => s >= 0));
      };
      pl.mcqClicks(pick);
      pl.mcqKeys(() => (stage === 'q' ? 4 : 0), () => sel[qi], pick);
      pl.on(pl.body, 'click', (e) => {
        if (stage !== 'q') return;
        if (e.target.closest('[data-replay]')) startReplay(true);
        else if (e.target.closest('[data-play]')) {
          if (playing) { playing = false; redrawStrip(); } else startReplay(frac >= 1 || frac === 0);
        } else if (e.target.closest('[data-speed]')) { speed = +e.target.closest('[data-speed]').dataset.speed; redrawStrip(); }
        else if (e.target.closest('.audio .bar') && (playing || frac > 0)) {
          const r = e.target.closest('.audio .bar').getBoundingClientRect();
          frac = Math.max(0, Math.min(1, (e.clientX - r.left) / r.width));
          paintAudio();
        }
      });
      pl.h.dot = (i) => stage === 'q' && showQ(i);
      pl.h.back = () => stage === 'q' && qi > 0 && showQ(qi - 1);
      pl.h.next = () => { if (stage !== 'q') return; if (qi < 3) showQ(qi + 1); else P.go(REVIEW); };
      intro();
      return pl.destroy;
    },
  });

  /* ───────── 4. Build a Sentence ───────── */
  const BS_ITEMS = [
    { ctx: 'Why was the library closed yesterday?', tokens: ['The', { a: 'staff' }, { a: 'were' }, { a: 'attending' }, 'a', { a: 'training' }, { a: 'session' }, '.'], bank: ['session', 'were', 'attending', 'was', 'staff', 'training'] },
    { ctx: 'Could you tell me where the registrar\'s office is?', tokens: ['It', { a: 'is' }, { a: 'next' }, { a: 'to' }, 'the', { a: 'main' }, { a: 'entrance' }, '.'], bank: ['entrance', 'near', 'to', 'next', 'main', 'is'] },
    { ctx: 'Did you finish the report for Professor Lee?', tokens: ['I', { a: 'have' }, { a: 'not' }, { a: 'finished' }, 'it', { a: 'quite' }, { a: 'yet' }, '.'], bank: ['yet', 'have', 'quite', 'finished', 'already', 'not'] },
  ];
  const BS_COUNT = 10;
  const bsItem = (i) => BS_ITEMS[i % 3];
  const bsAnswer = (it) => it.tokens.filter((t) => typeof t !== 'string').map((t) => it.bank.indexOf(t.a));

  P.register('/tasks/build', {
    title: 'Task player: Build a Sentence', num: 12, web: true, ios: true, layout: 'player', fab: true,
    tutorHint: true, tutorContext: 'Sentence 3 · Build a Sentence: present perfect',
    render: (ctx) => chrome(ctx, { section: 'Writing', title: 'Build a Sentence', timer: 360, foot: 'qa', sub: 'Sentence 3 of 10 · Set 1 of 1' }),
    mount(root, ctx) {
      const pl = Player(root, ctx, { timer: 360 });
      let cur = 2;
      const st = Array.from({ length: BS_COUNT }, (_, i) => {
        const it = bsItem(i);
        return { slots: i < 2 ? bsAnswer(it) : Array(bsAnswer(it).length).fill(null) };
      });
      let drag = null; // pointer drag in progress
      let kb = null;   // keyboard pick-up in progress

      const full = (i) => st[i].slots.every((v) => v !== null);
      const chunk = (it, id, where, k) => `<button type="button" class="chunk" data-chunk="${id}" aria-label="${P.esc(it.bank[id])}, ${where === 'slot' ? 'placed in slot ' + (k + 1) : 'in the word bank'}">${P.i('grip-vertical')}<span>${P.esc(it.bank[id])}</span></button>`;
      const bs = () => pl.body.querySelector('[data-bs]');
      const live = (t) => { const l = pl.body.querySelector('[data-live]'); if (l) l.textContent = t; };

      const draw = (focusId) => {
        const it = bsItem(cur), s = st[cur];
        let k = -1;
        const line = it.tokens.map((t) => {
          if (typeof t === 'string') return `<span class="bs-fixed">${P.esc(t)}</span>`;
          k += 1;
          const id = s.slots[k];
          return `<span class="bs-slot ${id !== null ? 'filled' : ''}" data-slot="${k}" role="group" aria-label="Slot ${k + 1}, ${id !== null ? P.esc(it.bank[id]) : 'empty'}">${id !== null ? chunk(it, id, 'slot', k) : ''}</span>`;
        }).join('');
        const bank = it.bank.map((_, id) => (s.slots.includes(id) ? '' : chunk(it, id, 'bank'))).join('');
        pl.body.innerHTML = `<div class="bs" data-bs>
          <div class="t-overline">Sentence ${cur + 1} of ${BS_COUNT}</div>
          <div class="bs-ctx"><span class="avatar lg">${P.i('user')}</span><div class="bs-bubble">${P.esc(it.ctx)}</div></div>
          <div class="bs-line" aria-label="Sentence">${line}</div>
          <div class="bs-bank" aria-label="Word bank">${bank}</div>
          <div class="bs-actions">${P.btn('Reset', { v: 'ghost', icon: 'rotate-ccw', attrs: 'data-reset' })}<span class="t-caption muted">Drag a word, tap it, or press Space to pick it up.</span></div>
          <div class="sr-only" aria-live="assertive" data-live></div></div>`;
        icons();
        pl.sub(`Sentence ${cur + 1} of ${BS_COUNT} · Set 1 of 1`);
        pl.dots(BS_COUNT, cur, st.map((_, i) => full(i)));
        pl.back({ disabled: cur === 0 });
        pl.next(cur === BS_COUNT - 1 ? { label: 'Check answers', icon: 'check' } : { label: 'Next', icon: 'chevron-right' });
        if (focusId !== undefined && focusId !== null) { const el = pl.body.querySelector(`[data-chunk="${focusId}"]`); if (el) el.focus(); }
      };

      const place = (id, k, from) => {
        const s = st[cur];
        const occupant = s.slots[k];
        if (from >= 0) s.slots[from] = occupant;
        s.slots[k] = id;
      };
      const firstEmpty = () => st[cur].slots.indexOf(null);
      const markTarget = (k) => pl.body.querySelectorAll('.bs-slot').forEach((el, i) => el.classList.toggle('target', i === k));

      /* pointer drag-and-drop, tap alternatives */
      pl.on(pl.body, 'pointerdown', (e) => {
        const ch = e.target.closest('.chunk');
        if (!ch || e.button > 0 || kb) return;
        const slotEl = ch.closest('[data-slot]');
        drag = { id: +ch.dataset.chunk, from: slotEl ? +slotEl.dataset.slot : -1, x0: e.clientX, y0: e.clientY, el: ch, moved: false, ghost: null };
      });
      pl.on(window, 'pointermove', (e) => {
        if (!drag) return;
        if (!drag.moved) {
          if (Math.hypot(e.clientX - drag.x0, e.clientY - drag.y0) < 5) return;
          drag.moved = true;
          const box = bs().getBoundingClientRect();
          const r = drag.el.getBoundingClientRect();
          const scale = box.width / bs().offsetWidth;
          drag.scale = scale;
          drag.offX = (drag.x0 - r.left) / scale;
          drag.offY = (drag.y0 - r.top) / scale;
          drag.home = { left: (r.left - box.left) / scale, top: (r.top - box.top) / scale };
          const g = drag.el.cloneNode(true);
          g.classList.add('lifted');
          g.removeAttribute('data-chunk');
          g.setAttribute('aria-hidden', 'true');
          g.tabIndex = -1;
          bs().appendChild(g);
          drag.ghost = g;
          drag.el.classList.add('ghost');
        }
        const box = bs().getBoundingClientRect();
        drag.ghost.style.left = (e.clientX - box.left) / drag.scale - drag.offX + 'px';
        drag.ghost.style.top = (e.clientY - box.top) / drag.scale - drag.offY + 'px';
        const t = document.elementFromPoint(e.clientX, e.clientY);
        const slot = t && t.closest('[data-slot]');
        drag.target = slot && bs().contains(slot) ? +slot.dataset.slot : -1;
        markTarget(drag.target);
      });
      const endDrag = (e) => {
        if (!drag) return;
        const d = drag;
        drag = null;
        if (!d.moved) {
          if (e.type === 'pointercancel') return;
          if (d.from < 0) { const k = firstEmpty(); if (k >= 0) { st[cur].slots[k] = d.id; draw(); live(`Placed '${bsItem(cur).bank[d.id]}' in slot ${k + 1}`); } }
          else { st[cur].slots[d.from] = null; draw(); live(`Returned '${bsItem(cur).bank[d.id]}' to the word bank`); }
          return;
        }
        markTarget(-1);
        if (d.target >= 0 && e.type === 'pointerup') { place(d.id, d.target, d.from); draw(); live(`Placed '${bsItem(cur).bank[d.id]}' in slot ${d.target + 1}`); return; }
        d.ghost.classList.add('back');
        d.ghost.style.left = d.home.left + 'px';
        d.ghost.style.top = d.home.top + 'px';
        pl.after(() => { d.ghost.remove(); d.el.classList.remove('ghost'); }, 220);
      };
      pl.on(window, 'pointerup', endDrag);
      pl.on(window, 'pointercancel', endDrag);

      /* keyboard: Space/Enter pick up, arrows choose a slot, Enter drops, Esc cancels */
      pl.on(pl.body, 'keydown', (e) => {
        const it = bsItem(cur), s = st[cur], n = s.slots.length;
        if (kb) {
          const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[e.key];
          if (step) { e.preventDefault(); kb.slot = (kb.slot + step + n) % n; markTarget(kb.slot); live(`Slot ${kb.slot + 1} of ${n}, ${s.slots[kb.slot] === null ? 'empty' : 'holds ' + it.bank[s.slots[kb.slot]]}`); }
          else if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            const k = kb.slot, id = kb.id;
            place(id, k, kb.from);
            kb = null;
            draw(id);
            live(`Placed '${it.bank[id]}' in slot ${k + 1}`);
          } else if (e.key === 'Escape') { e.preventDefault(); const id = kb.id; kb = null; draw(id); live('Cancelled'); }
          return;
        }
        const ch = e.target.closest('.chunk');
        if (!ch) return;
        const id = +ch.dataset.chunk;
        const slotEl = ch.closest('[data-slot]');
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          kb = { id, from: slotEl ? +slotEl.dataset.slot : -1, slot: Math.max(firstEmpty(), 0) };
          ch.classList.add('picked');
          markTarget(kb.slot);
          live(`Picked up '${it.bank[id]}'. Use arrow keys to choose a slot, Enter to drop`);
        } else if ((e.key === 'Backspace' || e.key === 'Delete') && slotEl) {
          e.preventDefault();
          s.slots[+slotEl.dataset.slot] = null;
          draw(id);
          live(`Returned '${it.bank[id]}' to the word bank`);
        }
      });
      pl.on(pl.body, 'click', (e) => {
        if (e.target.closest('[data-reset]')) { st[cur].slots.fill(null); kb = null; draw(); live('All words returned to the word bank'); }
      });

      pl.h.dot = (i) => { kb = null; cur = i; draw(); };
      pl.h.back = () => { if (cur > 0) { kb = null; cur -= 1; draw(); } };
      pl.h.next = () => { if (cur < BS_COUNT - 1) { kb = null; cur += 1; draw(); } else P.go(REVIEW); };
      draw();
      return pl.destroy;
    },
  });

  /* ───────── Speaking: Recorder shared by Interview and Listen and Repeat ───────── */
  const RING = 175.93; // circumference of r = 28
  function speakRun(pl, host, o) {
    const calm = reduceMotion();
    host.innerHTML = `<div class="iv speaking" data-iv>
        <div class="iv-avatar" aria-hidden="true"><div class="iv-wave"><i></i><i></i><i></i><i></i><i></i></div></div>
        <div class="iv-label" data-iv-label>${o.speaking}</div></div>
      <div class="recorder listen" data-rec>
        <div class="rec-top">
          <div class="rec-ring" role="timer" aria-live="off"><svg viewBox="0 0 64 64" aria-hidden="true"><circle class="rt" cx="32" cy="32" r="28"/><circle class="ra" cx="32" cy="32" r="28"/></svg><span class="rec-num" data-num>${o.seconds}</span></div>
          <div class="stack"><div class="row gap-2"><span class="rec-dot" data-dot hidden></span><span class="rec-label" data-label>Listen</span></div></div>
          <div style="margin-left:auto">${P.btn('Stop early', { v: 'ghost', size: 'sm', attrs: 'data-stop hidden' })}</div>
        </div>
        <div class="wave" aria-hidden="true">${'<i></i>'.repeat(32)}</div>
        <div class="rec-note" data-note aria-live="polite">Listen carefully. Recording starts automatically.</div>
      </div>`;
    icons();
    const q = (s) => host.querySelector(s);
    const rec = q('[data-rec]'), num = q('[data-num]'), lab = q('[data-label]'), note = q('[data-note]'), dot = q('[data-dot]');
    const arc = q('.ra'), stop = q('[data-stop]'), iv = q('[data-iv]'), ivLabel = q('[data-iv-label]');
    const bars = [...host.querySelectorAll('.wave i')];
    const levels = new Array(32).fill(0.08);
    const paintBars = () => bars.forEach((b, i) => { b.style.height = 4 + levels[i] * 36 + 'px'; });
    const state = (cls, label, text) => { rec.className = 'recorder ' + cls; lab.textContent = label; note.textContent = text; };
    let timer = null, done = false;

    const finish = () => {
      if (done) return;
      done = true;
      pl.clear(timer);
      stop.hidden = true; dot.hidden = true;
      levels.fill(0.08); paintBars();
      state('idle', 'Saving your answer…', 'Saving your answer…');
      num.innerHTML = P.i('loader'); icons();
      pl.after(() => {
        state('idle', 'Answer saved', 'Saved. Moving on…');
        num.innerHTML = P.i('check'); icons();
        pl.after(o.onDone, 900);
      }, 1200);
    };
    const record = () => {
      state('recording', 'Recording', 'Speak now. Recording stops at 0.');
      dot.hidden = false; stop.hidden = false;
      num.textContent = o.seconds;
      const t0 = Date.now();
      let prev = 0.2;
      timer = pl.every(() => {
        const el = ((Date.now() - t0) / 1000) * o.speed;
        const left = Math.max(0, o.seconds - el);
        num.textContent = Math.ceil(left);
        arc.style.strokeDashoffset = RING * Math.min(1, el / o.seconds);
        if (!calm) {
          prev = 0.45 * prev + 0.55 * (0.12 + 0.88 * Math.random() * (0.55 + 0.45 * Math.sin(el * 2.3)));
          levels.shift(); levels.push(Math.max(0.05, Math.min(1, prev)));
          paintBars();
        }
        if (left <= 0) finish();
      }, 90);
      if (calm) { levels.forEach((_, i) => { levels[i] = 0.25 + 0.5 * Math.abs(Math.sin(i * 0.7)); }); paintBars(); }
    };
    const countdown = () => {
      iv.classList.remove('speaking');
      ivLabel.textContent = o.idle;
      state('countdown', 'Get ready', 'Recording starts automatically.');
      let n = 3;
      num.textContent = n;
      const cd = pl.every(() => {
        n -= 1;
        if (n <= 0) { pl.clear(cd); record(); } else num.textContent = n;
      }, 500);
    };
    q('[data-stop]').addEventListener('click', finish);
    pl.after(countdown, o.prompt);
  }

  const micMark = (name) => `<span class="done-mark" style="align-self:center">${P.i(name)}</span>`;

  /* ───────── 5. Take an Interview ───────── */
  P.register('/tasks/interview', {
    title: 'Task player: Take an Interview', num: 13, web: true, ios: true, layout: 'player', fab: true,
    tutorHint: true, tutorContext: 'Question 2 · Interview: technology in education',
    render: (ctx) => chrome(ctx, { section: 'Speaking', title: 'Take an Interview', timer: 0, foot: 'speak', sub: 'Interview · Set 1 of 1' }),
    mount(root, ctx) {
      const pl = Player(root, ctx, {});
      const N = 4;
      let qi = 0;
      const intro = () => {
        pl.dots(N, -1, [], false);
        pl.body.innerHTML = `<div class="pl-center center">${micMark('mic')}<h2 class="t-title-2">Take an Interview</h2>
          <p class="t-body-lg">You will take part in an interview about technology in education. Answer each question.</p>
          ${P.btn('Start', { size: 'lg', attrs: 'data-start' })}<p class="t-caption muted">Each answer is recorded once. There is no re-record.</p></div>`;
        icons();
      };
      const ask = () => {
        pl.sub(`Question ${qi + 1} of ${N} · Set 1 of 1`);
        pl.dots(N, qi, Array.from({ length: N }, (_, i) => i < qi), false);
        pl.body.innerHTML = `<div class="sp-zone"><div class="sp-count t-overline" data-count>Question ${qi + 1} of ${N}</div><div class="sp-host stack gap-5" style="align-self:stretch" data-host></div></div>`;
        speakRun(pl, pl.body.querySelector('[data-host]'), {
          speaking: 'Interviewer is speaking', idle: 'Interviewer', prompt: 3000, seconds: 45, speed: 5,
          onDone: () => { if (qi < N - 1) { qi += 1; ask(); } else finish(); },
        });
      };
      const finish = () => {
        pl.sub('Interview · Set 1 of 1');
        pl.dots(N, N, Array(N).fill(true), false);
        pl.body.innerHTML = `<div class="pl-center center">${micMark('check')}<h2 class="t-title-2">Interview complete</h2>
          <p class="t-body-lg">All ${N} answers are saved. Your review is ready.</p>${P.btn('See your review', { size: 'lg', iconRight: 'chevron-right', href: '/review/speaking' })}</div>`;
        icons();
      };
      pl.on(pl.body, 'click', (e) => { if (e.target.closest('[data-start]')) ask(); });
      intro();
      return pl.destroy;
    },
  });

  /* ───────── 6. Listen and Repeat ───────── */
  P.register('/tasks/repeat', {
    title: 'Task player: Listen and Repeat', num: 'plan', web: true, ios: true, layout: 'player', fab: true,
    tutorHint: true, tutorContext: 'Sentence 3 · Listen and Repeat: campus fitness center',
    render: (ctx) => chrome(ctx, { section: 'Speaking', title: 'Listen and Repeat', timer: 0, foot: 'speak', sub: 'Listen and Repeat · Set 1 of 1' }),
    mount(root, ctx) {
      const pl = Player(root, ctx, {});
      const N = 7;
      let si = 0;
      const intro = () => {
        pl.dots(N, -1, [], false);
        pl.body.innerHTML = `<div class="sp-zone"><div class="scene" role="img" aria-label="Illustration of a campus fitness center">${SCENE_GYM}</div>
          <p class="t-body-lg center">You are a new student at the campus fitness center. Listen to the staff member and repeat what they say.</p>
          ${P.btn('Start', { size: 'lg', attrs: 'data-start' })}</div>`;
        icons();
      };
      const sentence = () => {
        pl.sub(`Sentence ${si + 1} of ${N} · Set 1 of 1`);
        pl.dots(N, si, Array.from({ length: N }, (_, i) => i < si), false);
        pl.body.innerHTML = `<div class="sp-zone"><div class="sp-count t-overline">Sentence ${si + 1} of ${N}</div><div class="stack gap-5" style="align-self:stretch" data-host></div></div>`;
        speakRun(pl, pl.body.querySelector('[data-host]'), {
          speaking: 'Staff member is speaking', idle: 'Staff member', prompt: 2500, seconds: 10, speed: 3,
          onDone: () => { if (si < N - 1) { si += 1; sentence(); } else finish(); },
        });
      };
      const finish = () => {
        pl.sub('Listen and Repeat · Set 1 of 1');
        pl.dots(N, N, Array(N).fill(true), false);
        pl.body.innerHTML = `<div class="pl-center center">${micMark('check')}<h2 class="t-title-2">All sentences recorded</h2>
          <p class="t-body-lg">Your ${N} answers are saved. Your review is ready.</p>${P.btn('See your review', { size: 'lg', iconRight: 'chevron-right', href: '/review/speaking' })}</div>`;
        icons();
      };
      pl.on(pl.body, 'click', (e) => { if (e.target.closest('[data-start]')) sentence(); });
      intro();
      return pl.destroy;
    },
  });

  /* ───────── 7. Academic Discussion, Attempt 2 ───────── */
  const DRAFT = 'I think that living much longer is not always a good idea. First, if people lives longer, the cost of healthcare will grow very fast and governments may not be able to pay for it. Second, Paul is right that old people need more care, but Claire forget that many of them feel lonely. However, I agree with Claire that science can make life healthier. In conclusion, in my opinion we should focus on the quality of life and happiness, not only on the number of years.';
  const countWords = (t) => (t.trim() ? t.trim().split(/\s+/).length : 0);

  P.register('/tasks/discussion', {
    title: 'Writing: Academic Discussion, Attempt 2', num: 'plan', web: true, ios: true, layout: 'player', fab: true,
    tutorHint: true, tutorContext: 'Academic Discussion · Longevity · Attempt 2',
    render(ctx) {
      const comments = [['Grammar', 'danger', '"people lives longer": the subject is plural, so use "live".'], ['Grammar', 'danger', '"Claire forget": add -s for the third person, "forgets".'], ['Development', 'info', 'Add one specific example to support your second reason.']];
      return chrome(ctx, { section: 'Writing', title: 'Academic Discussion', timer: 600, foot: 'submit', sub: 'Attempt 2 · Question 1 of 1 · Set 1 of 1' })
        .replace('<div class="pl-body" data-pl="body"></div>', `<div class="pl-body" data-pl="body"><div class="dc">
          <section class="dc-task" aria-label="Task">
            <p class="pl-instruction">Your professor is teaching a class on health and society. Write a post responding to the professor's question.</p>
            <div class="pl-card">
              <div class="dc-prof"><span class="dc-av">${P.i('graduation-cap')}</span><div><div class="dc-name">Dr. Gupta</div>
                <p>Advances in medicine mean that people may soon routinely live well past 100. Should societies aim to extend human life as much as possible? Why or why not? In your response, express and support your personal opinion, and contribute to the discussion in your own words.</p></div></div>
              <div class="dc-post">${P.avatar('CL', 'lg')}<div><div class="dc-name">Claire</div><p>I think we should. Longer lives mean more time with family and more years to share knowledge and skills. If medicine can keep people healthy, why would we stop it?</p></div></div>
              <div class="dc-post">${P.avatar('PA', 'lg')}<div><div class="dc-name">Paul</div><p>I am not so sure. If people live much longer, housing and pensions will run short, and young people may find it harder to get jobs. We should improve health, not just add years.</p></div></div>
            </div>
            <details class="dc-comments" ${ctx.ios ? '' : 'open'}><summary>${P.i('message-square-text')}Comments from your Draft<span class="chev">${P.i('chevron-down')}</span></summary>
              <ul>${comments.map((c) => `<li>${P.tag(c[1], c[0])}<span>${c[2]}</span></li>`).join('')}</ul></details>
          </section>
          <section class="dc-edit" aria-label="Your response">
            <div class="ed">
              <div class="ed-tools" role="toolbar" aria-label="Editing tools">
                ${P.btn('Cut', { v: 'ghost', size: 'sm', icon: 'scissors', attrs: 'data-ed="cut"' })}${P.btn('Paste', { v: 'ghost', size: 'sm', icon: 'clipboard-paste', attrs: 'data-ed="paste"' })}${P.btn('Undo', { v: 'ghost', size: 'sm', icon: 'undo-2', attrs: 'data-ed="undo"' })}${P.btn('Redo', { v: 'ghost', size: 'sm', icon: 'redo-2', attrs: 'data-ed="redo"' })}
                <span class="ed-count" data-count>Word count: ${countWords(DRAFT)}</span></div>
              <textarea class="ed-area" data-area aria-label="Your response" spellcheck="false" autocomplete="off" autocorrect="off" autocapitalize="sentences">${P.esc(DRAFT)}</textarea>
            </div>
            <div class="ed-meta"><span class="ed-save" data-save>${P.i('check')}Saved</span><span class="ed-aim" data-aim>Aim for at least 100 words</span></div>
            <div class="sr-only" aria-live="polite" data-live></div>
          </section></div></div>`);
    },
    mount(root, ctx) {
      const pl = Player(root, ctx, { timer: 600 });
      pl.dots(1, 0, [false], false);
      const q = (s) => root.querySelector(s);
      const area = q('[data-area]'), count = q('[data-count]'), save = q('[data-save]'), aim = q('[data-aim]'), live = q('[data-live]');
      let clip = '', wc = 0, reached = false, tCount = null, tSave = null;

      const updateCount = () => {
        wc = countWords(area.value);
        count.textContent = `Word count: ${wc}`;
        const ok = wc >= 100;
        aim.classList.toggle('ok', ok);
        aim.innerHTML = `${ok ? P.i('check') : ''}Aim for at least 100 words`;
        icons();
        if (ok !== reached) { reached = ok; live.textContent = ok ? 'You have reached 100 words.' : 'Below 100 words.'; }
      };
      const setSave = (saving) => { save.innerHTML = saving ? `${P.i('loader')}Saving…` : `${P.i('check')}Saved`; icons(); };
      area.addEventListener('input', () => {
        clearTimeout(tCount); tCount = setTimeout(updateCount, 300);
        setSave(true);
        clearTimeout(tSave); tSave = setTimeout(() => setSave(false), 900);
      });
      area.addEventListener('blur', () => { clearTimeout(tSave); tSave = setTimeout(() => setSave(false), 300); });

      /* toolbar keeps the caret in the editor; paste only takes text cut or copied inside the editor */
      root.querySelectorAll('[data-ed]').forEach((b) => {
        b.addEventListener('mousedown', (e) => e.preventDefault());
        b.addEventListener('click', () => {
          area.focus();
          const k = b.dataset.ed;
          if (k === 'cut') { clip = area.value.slice(area.selectionStart, area.selectionEnd); if (clip) document.execCommand('delete'); }
          else if (k === 'paste') { if (clip) document.execCommand('insertText', false, clip); }
          else document.execCommand(k);
          area.dispatchEvent(new Event('input'));
        });
      });
      area.addEventListener('copy', () => { clip = area.value.slice(area.selectionStart, area.selectionEnd) || clip; });
      area.addEventListener('paste', (e) => { e.preventDefault(); if (clip) document.execCommand('insertText', false, clip); });

      pl.h.next = () => {
        P.go('/review/writing');
        setTimeout(() => P.toast('Attempt 2 submitted. Your essay is being checked.'), 150);
      };
      updateCount();
      return () => { clearTimeout(tCount); clearTimeout(tSave); pl.destroy(); };
    },
  });
})();
