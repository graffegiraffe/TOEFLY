/* Review, results, progress and vocabulary screens.
   Routes: /review/attempt, /review/writing, /review/speaking, /weeks/7/review, /exams/1/result, /progress, /vocabulary, /vocabulary/review. */
(function () {
  const P = window.P;
  const icons = () => window.lucide && window.lucide.createIcons();
  const $ = (r, s) => r.querySelector(s);
  const $$ = (r, s) => Array.from(r.querySelectorAll(s));
  const on = (el, ev, fn, o) => { el.addEventListener(ev, fn, o); return () => el.removeEventListener(ev, fn, o); };
  const ru = (text) => `<p class="rv-ru" lang="ru">${text}</p>`;

  /* MistakeCategory → tag class and label */
  const CAT = {
    GRAMMAR: ['grammar', 'Grammar'], VOCABULARY: ['vocabulary', 'Vocabulary'], SPELLING: ['danger', 'Spelling'], COHERENCE: ['info', 'Coherence'],
    TASK_RESPONSE: ['info', 'Task response'], PRONUNCIATION: ['speaking', 'Pronunciation'], FLUENCY: ['speaking', 'Fluency'],
    COMPREHENSION: ['listening', 'Comprehension'], TIME_MANAGEMENT: ['warn', 'Time management'],
  };
  const catTag = (c) => P.tag(CAT[c][0], CAT[c][1]);

  /* ───────── CommentThread, shared by Writing review and Weekly review ───────── */
  const THREADS = {
    t1: {
      quote: 'I agree with Paul that money is important',
      items: [
        { n: 'Anna K.', i: 'AK', r: 'Tutor', t: '2 h ago', x: 'Today, 10:42', text: 'Good move to answer Paul directly. Now say what you add: education, not only money.' },
        { n: 'Maria P.', i: 'MP', r: 'Student', t: '1 h ago', x: 'Today, 11:35', text: 'Thanks! Should I also quote his example about hospitals?' },
        { n: 'Anna K.', i: 'AK', r: 'Tutor', t: '40 min ago', x: 'Today, 11:58', text: 'One short example is enough. Keep the second half of the paragraph for your own idea.' },
      ],
    },
    wk: {
      quote: 'Week 7 review · From your tutor',
      items: [
        { n: 'Anna K.', i: 'AK', r: 'Tutor', t: 'Yesterday', x: 'Yesterday, 18:20', text: 'Your Reading jump is real. For Week 8, spend ten minutes a day on conditionals out loud; your essays still say "if I will".' },
        { n: 'Maria P.', i: 'MP', r: 'Student', t: '3 h ago', x: 'Today, 09:10', text: 'Understood. Can I swap one Listening task for extra Build a Sentence?' },
      ],
    },
  };
  const cm = (c, reply) => `<div class="cm ${reply ? 'reply' : ''}">${P.avatar(c.i, 'sm')}<div class="b"><div class="nm"><b>${c.n}</b><span class="rv-role ${c.r === 'Tutor' ? 'tutor' : ''}">${c.r}</span><span class="tm" title="${c.x}">${c.t}</span></div><p>${P.esc(c.text)}</p></div></div>`;
  const threadHtml = (key) => {
    const t = THREADS[key];
    return `<div class="rv-thread" data-thread="${key}">
      <div class="quote">${t.quote}</div>
      <div class="items">${t.items.map((c, i) => cm(c, i > 0)).join('')}</div>
      <div class="res-note">${P.i('circle-check')}<span>Resolved</span><button type="button" class="btn btn-ghost sm" data-reopen>Reopen</button></div>
      <div class="reply-box"><textarea class="input" rows="2" placeholder="Reply to Anna K." aria-label="Reply"></textarea>${P.btn('Send', { size: 'sm', icon: 'send', attrs: 'data-send' })}</div>
      <div class="row between"><span class="t-caption muted">Press ${/Mac/.test(navigator.platform) ? 'Cmd' : 'Ctrl'}+Enter to send</span>${P.btn('Mark as resolved', { v: 'ghost', size: 'sm', icon: 'check', attrs: 'data-resolve' })}</div></div>`;
  };
  function bindThread(el) {
    const send = (th) => {
      const ta = $(th, 'textarea');
      const text = ta.value.trim();
      if (!text) return;
      const item = { n: 'Maria P.', i: 'MP', r: 'Student', t: 'Just now', x: 'Just now', text };
      THREADS[th.dataset.thread].items.push(item);
      $(th, '.items').insertAdjacentHTML('beforeend', cm(item, true));
      ta.value = '';
      icons();
    };
    const click = (e) => {
      const th = e.target.closest('[data-thread]');
      if (!th) return;
      if (e.target.closest('[data-send]')) send(th);
      else if (e.target.closest('[data-resolve]')) th.classList.add('resolved');
      else if (e.target.closest('[data-reopen]')) th.classList.remove('resolved');
    };
    const key = (e) => { if (e.key === 'Enter' && (e.metaKey || e.ctrlKey) && e.target.matches('textarea')) { e.preventDefault(); send(e.target.closest('[data-thread]')); } };
    const offs = [on(el, 'click', click), on(el, 'keydown', key)];
    return () => offs.forEach((f) => f());
  }
  function openThread(key) {
    P.openOverlay(`<div class="stack gap-3"><div class="row between"><span class="t-title-3">Comment thread</span>${P.btn('Close', { v: 'icon', icon: 'x', size: 'sm', attrs: 'data-action="close"' })}</div>${threadHtml(key)}</div>`,
      { label: 'Comment thread', mount: (el) => bindThread(el) });
  }

  /* ═════════════ 14 · Review: closed task ═════════════ */
  const TRANSCRIPT = [
    'So why do cells need mitochondria at all?',
    'Many people call them the power plants of the cell, and that is true, but it is only half the story.',
    'Mitochondria also help the cell decide when to die.',
    'When a cell is damaged beyond repair, they release signals that start a controlled shutdown.',
    'Without that shutdown, damaged cells would keep multiplying, and that is one way tumours begin.',
    'Researchers often study this process in yeast, because yeast cells are simple and easy to grow.',
  ];
  const QS = [
    {
      n: 3, stem: `What is the professor's main point about mitochondria?`, c: 1, ch: 0, fr: [2, 3],
      o: ['They produce energy faster than any other part of the cell.', 'They have a second job: removing cells that are damaged.', 'They are the reason tumours form in the body.', 'They are found only in animal cells.'],
      why: 'The professor calls the power-plant idea "only half the story" and then adds a second function: starting a controlled shutdown of damaged cells. That second function is the point of the talk.',
      ru: 'Профессор называет идею «электростанции» лишь половиной истории и добавляет вторую функцию: запуск контролируемого отключения повреждённых клеток. Именно это и есть главная мысль лекции.',
      not: { A: 'Energy is the familiar idea the professor moves past. Speed is never compared.', C: 'Tumours are mentioned as a result when the shutdown fails, not as something mitochondria cause.', D: 'The talk never says which organisms have mitochondria.' },
    },
    {
      n: 7, stem: 'Why does the professor mention yeast cells?', c: 2, ch: 3, fr: [5],
      o: ['To show that all cells divide at the same speed.', 'To explain why scientists prefer very large samples.', 'To give a simple model for studying how cells shut down.', 'To compare plant cells with animal cells.'],
      why: 'Yeast is introduced as a place where researchers study the shutdown process because it is simple and easy to grow.',
      ru: 'Дрожжи упоминаются как удобная модель для изучения процесса отключения клетки: они просты и быстро растут.',
      not: { A: 'Division speed is not discussed.', B: 'Yeast cells are called simple, not large, and sample size is never mentioned.', D: 'No comparison with plants appears in the talk.' },
    },
    { n: 1, stem: 'What does the professor say about the "power plant" description of mitochondria?', c: 1, ch: 1, fr: [1], o: ['It is completely wrong.', 'It is correct but incomplete.', 'It was proposed recently.', 'It applies only to yeast.'], why: 'She says it is true but "only half the story".', ru: 'Она говорит, что это верно, но лишь «половина истории».' },
    { n: 2, stem: 'What happens to a cell that is damaged beyond repair?', c: 2, ch: 2, fr: [3], o: ['It divides faster.', 'It moves to another tissue.', 'It receives a signal to shut down.', 'It stops using energy.'], why: 'Mitochondria release signals that start a controlled shutdown.', ru: 'Митохондрии выпускают сигналы, запускающие контролируемое отключение.' },
    { n: 4, stem: 'What can happen if the shutdown does not work?', c: 1, ch: 1, fr: [4], o: ['Cells stop growing.', 'Tumours may begin.', 'Energy production rises.', 'Yeast cells die.'], why: 'Damaged cells keep multiplying, which is one way tumours begin.', ru: 'Повреждённые клетки продолжают делиться, и так могут начаться опухоли.' },
    { n: 5, stem: 'Why does the professor begin with a question?', c: 1, ch: 1, fr: [0], o: ['To test the students.', 'To introduce a topic she will then expand.', 'To correct a student.', 'To review last week.'], why: 'The question opens the topic, and the rest of the talk answers it.', ru: 'Вопрос открывает тему, а остальная лекция отвечает на него.' },
    { n: 6, stem: 'What does the professor imply about the power-plant comparison?', c: 1, ch: 1, fr: [1], o: ['Only beginners use it.', 'It is useful but leaves something out.', 'It comes from yeast research.', 'Textbooks should remove it.'], why: 'She accepts the comparison and then says there is more.', ru: 'Она принимает сравнение, но говорит, что есть и другая сторона.' },
    { n: 8, stem: 'How does the professor describe yeast cells?', c: 1, ch: 1, fr: [5], o: ['Complex and slow to grow.', 'Simple and easy to grow.', 'Larger than animal cells.', 'Impossible to study.'], why: 'The last sentence says they are simple and easy to grow.', ru: 'В последнем предложении сказано, что они простые и быстро растут.' },
    { n: 9, stem: 'How is the explanation organized?', c: 0, ch: 0, fr: [1, 2], o: ['A familiar idea first, then an addition to it.', 'A list of experiment steps.', 'A comparison of two theories.', 'A story about a student.'], why: 'The professor starts from a common idea and builds on it.', ru: 'Профессор идёт от общеизвестной идеи и дополняет её.' },
    { n: 10, stem: 'What will the professor probably discuss next?', c: 0, ch: 0, fr: [3], o: ['How cells decide to shut down.', 'Dietary advice.', 'The history of microscopes.', 'Plant cell walls.'], why: 'The talk ends by describing the shutdown signal, so the mechanism is the natural next step.', ru: 'Лекция заканчивается сигналом отключения, значит, дальше логично разобрать механизм.' },
  ];
  const SUGGESTED = [
    ['mitochondria', 'n', 'parts of a cell that make energy'], ['tumour', 'n', 'an abnormal growth of cells'], ['multiply', 'v', 'to increase in number'],
    ['release', 'v', 'to let something go or send it out'], ['signal', 'n', 'a sign that tells something to start'], ['damaged', 'adj', 'harmed so that it works badly'],
    ['controlled', 'adj', 'kept under careful management'], ['beyond repair', 'phr', 'too broken to be fixed'],
  ];

  const optHtml = (q) => `<div class="mcq" role="list">${q.o.map((t, i) => {
    const ok = i === q.c, bad = i === q.ch && !ok;
    return `<div class="mcq-opt ${ok ? 'correct' : ''} ${bad ? 'wrong' : ''}" role="listitem"><span class="letter">${'ABCD'[i]}</span><span>${t}</span>${ok ? `<span class="verdict">${P.i('circle-check')}Correct answer</span>` : bad ? `<span class="verdict">${P.i('circle-x')}Your answer</span>` : ''}</div>`;
  }).join('')}</div>`;

  const transcriptHtml = (q) => TRANSCRIPT.map((s, i) => (q.fr.includes(i) ? `<mark class="frag">${s}</mark>` : s)).join(' ');

  const questionHtml = (q, open) => {
    const ok = q.ch === q.c;
    const L = 'ABCD'[q.c];
    return `<div class="rv-q ${open ? 'open' : ''}" data-q="${q.n}">
      <button type="button" class="rv-q-h" aria-expanded="${open}">
        <span class="qn">Question ${q.n}</span><span class="qs">${q.stem}</span>
        ${ok ? P.tag('ok', 'Correct', { done: true }) : P.tag('danger', 'Missed')}
        ${P.i('chevron-down', 'chev')}</button>
      <div class="rv-q-b">
        <details class="rv-det"><summary>${P.i('headphones')}Stimulus · Academic talk, 1:48${P.i('chevron-down', 'chev')}</summary>
          <div class="in"><div class="audio"><button class="play" type="button" aria-label="Play audio" data-fake-play>${P.i('play')}</button><div class="bar thin" style="flex:1"><i style="width:0%"></i></div><span class="time">0:00 / 1:48</span></div>
          <div class="t-caption muted">You replayed the audio 1 time.</div></div></details>
        <div class="stem">${q.stem}</div>
        ${optHtml(q)}
        <div class="rv-why">
          <div class="t">${P.i('circle-check')}Why ${L} is correct</div><div>${q.why}</div>${ru(q.ru)}
        </div>
        ${q.not ? `<ul class="rv-whynot">${Object.keys(q.not).map((k) => `<li><b>Why not ${k}:</b> ${q.not[k]}</li>`).join('')}</ul>` : ''}
        <details class="rv-det" style="margin-top:14px"><summary>${P.i('file-text')}Show transcript${P.i('chevron-down', 'chev')}</summary><div class="in">${transcriptHtml(q)}</div></details>
        <div class="rv-q-actions">
          ${P.btn('Ask tutor', { v: 'secondary', size: 'sm', icon: 'sparkles', attrs: `data-action="tutor" data-context="Question ${q.n} · Academic talk: Biology"` })}
          ${P.btn('Explain in Russian', { v: 'ghost', size: 'sm', icon: 'languages', attrs: 'data-ru-btn aria-pressed="false"' })}
        </div>
      </div></div>`;
  };

  P.register('/review/attempt', {
    back: { label: 'Week 7 · Listening', href: '/weeks/7/listening' },
    title: 'Review: closed task', num: 14, nav: 'weeks', tab: 'weeks', web: true, ios: true,
    tutorContext: 'Question 3 · Academic talk: Biology', tutorHint: true,
    render(ctx) {
      const ordered = QS.slice().sort((a, b) => (a.ch === a.c) - (b.ch === b.c) || a.n - b.n);
      return `<div class="page narrow">
        <div class="rv-head"><div><h1 class="t-title-1">Academic talk: Biology</h1><div class="sub">Listening · closed set · reviewed Oct 13</div></div>${P.tag('listening', 'Listening')}</div>
        <section class="card lg rv-summary" aria-label="Result">
          <div class="ring-wrap">${P.ring({ value: 8, max: 10, size: 74, center: '8', label: '8 of 10 correct' })}<div class="metric"><span class="t-caption muted">Score</span><span class="v">8 of 10</span></div></div>
          <div class="metric"><span class="t-caption muted">Time</span><span class="v">3:12</span><span class="t-caption muted">(recommended 3:00)</span></div>
          <div class="metric"><span class="t-caption muted">Estimate</span><span class="v up">${P.i('trending-up')}+0.1</span><span class="t-caption muted">to your Listening estimate</span></div>
        </section>
        <h2 class="rv-section-title">Questions <span class="muted t-caption" style="font-weight:500">· missed first</span></h2>
        <div id="qs">${ordered.map((q, i) => questionHtml(q, i === 0)).join('')}</div>
        <h2 class="rv-section-title">Add new words</h2>
        <div class="card lg">
          <div class="muted t-caption" style="margin-bottom:12px">Suggested from this talk. Pick the ones you want to keep.</div>
          <div class="rv-words" id="words">${SUGGESTED.map(([w, p, d], i) => `<div class="rv-word ${i < 6 ? 'on' : ''}" role="checkbox" aria-checked="${i < 6}" tabindex="0">${P.check(i < 6, { round: false, attrs: 'tabindex="-1" aria-hidden="true"' })}<div><div><span class="w">${w}</span> <span class="t-caption muted">${p}</span></div><div class="d">${d}</div></div></div>`).join('')}</div>
        </div>
        <div class="rv-foot">
          ${P.btn('Add 6 words', { icon: 'plus', cls: 'add-words', attrs: 'id="addWords"' })}
          ${P.btn('Try a similar set', { v: 'secondary', icon: 'refresh-cw', href: '/tasks/listen' })}
          ${P.btn('Back to Week 7', { v: 'ghost', href: '/weeks/7' })}
        </div></div>`;
    },
    mount(root) {
      const offs = [];
      const timers = [];
      offs.push(on(root, 'click', (e) => {
        const h = e.target.closest('.rv-q-h');
        if (h) { const q = h.closest('.rv-q'); q.classList.toggle('open'); h.setAttribute('aria-expanded', q.classList.contains('open')); return; }
        const rb = e.target.closest('[data-ru-btn]');
        if (rb) { const q = rb.closest('.rv-q'); const t = $(q, '.rv-ru').classList.toggle('on'); rb.setAttribute('aria-pressed', t); return; }
        const pl = e.target.closest('[data-fake-play]');
        if (pl) {
          const wrap = pl.closest('.audio'); const bar = $(wrap, '.bar i'); const t = $(wrap, '.time');
          let p = 0; timers.push(setInterval(() => { p = Math.min(100, p + 4); bar.style.width = p + '%'; const s = Math.round(1.08 * p); t.textContent = `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')} / 1:48`; }, 200));
          return;
        }
        const w = e.target.closest('.rv-word');
        if (w) toggleWord(w);
      }));
      offs.push(on(root, 'keydown', (e) => { const w = e.target.closest && e.target.closest('.rv-word'); if (w && (e.key === ' ' || e.key === 'Enter')) { e.preventDefault(); toggleWord(w); } }));
      function toggleWord(w) {
        const v = !w.classList.contains('on');
        w.classList.toggle('on', v); w.setAttribute('aria-checked', v);
        const c = $(w, '.check'); c.classList.toggle('on', v); c.setAttribute('aria-checked', v);
        const n = $$(root, '.rv-word.on').length;
        const b = $(root, '#addWords');
        $(b, 'span').textContent = n === 1 ? 'Add 1 word' : `Add ${n} words`;
        b.disabled = n === 0;
      }
      offs.push(on($(root, '#addWords'), 'click', () => {
        const n = $$(root, '.rv-word.on').length;
        P.toast(`${n} ${n === 1 ? 'word' : 'words'} added to Vocabulary`);
      }));
      return () => { offs.forEach((f) => f()); timers.forEach(clearInterval); };
    },
  });

  /* ═════════════ 15 · Review: Writing ═════════════ */
  const WCARDS = {
    c1: { kind: 'ai', cat: 'GRAMMAR', text: 'The verb must agree with its subject. After <i>people</i> and <i>they</i> use the base form.', sugg: '<del>lives</del> <ins>live</ins> · <del>they walks</del> <ins>they walk</ins>', unit: 'Collins Grammar Unit 7', ru: 'Глагол должен согласоваться с подлежащим. После <i>people</i> и <i>they</i> используется базовая форма без -s.' },
    c5: { kind: 'ai', cat: 'SPELLING', text: 'Three spelling slips. Spellcheck is off in the exam, so these only show up in review.', sugg: '<del>healty</del> <ins>healthy</ins> · <del>goverment</del> <ins>government</ins> · <del>ilnesses</del> <ins>illnesses</ins>', ru: 'Три орфографические ошибки. В экзамене проверка орфографии выключена, поэтому их видно только в разборе.' },
    c3: { kind: 'ai', cat: 'VOCABULARY', text: '<i>Good</i> is vague here. A precise adjective makes the claim stronger.', sugg: '<del>good healthcare</del> <ins>high-quality healthcare</ins>', ru: 'Слово <i>good</i> здесь слишком общее. Точное прилагательное делает мысль убедительнее.' },
    t1: { kind: 'tutor', cat: 'TUTOR', thread: 't1' },
    c7: { kind: 'ai', cat: 'GRAMMAR', text: 'After <i>if</i>, use the present simple in the condition part. <i>Will</i> belongs in the result part.', sugg: '<del>If people will understand</del> <ins>If people understand</ins>', unit: 'Round-Up Unit 7: Conditionals', ru: 'После <i>if</i> в условной части используется Present Simple. <i>Will</i> ставится только в главной части.' },
    c8: { kind: 'ai', cat: 'COHERENCE', text: 'This sentence jumps from personal habits to whole countries, and <i>However</i> promises a contrast that is not there.', sugg: '<del>However, rich countries</del> <ins>At the same time, rich countries</ins>', ru: 'Предложение переходит от личных привычек к целым странам, а <i>However</i> обещает противопоставление, которого нет.' },
    t2: { kind: 'tutor', cat: 'TUTOR', text: 'A clear closing sentence. Next time, name the two things you combine so it is more than a summary.' },
  };
  const ESSAY = [
    'I think people in some countries ', { m: 'm1', c: 'c1', t: 'lives' }, ' longer mainly because of ', { m: 'm2', c: 'c5', t: 'healty' }, ' food and ', { m: 'm3', c: 'c3', t: 'good healthcare' },
    '. In Japan, for example, many people eat fish and vegetables every day, and ', { m: 'm4', c: 'c1', t: 'they walks' }, ' a lot. Also, the ', { m: 'm5', c: 'c5', t: 'goverment' },
    ' pays for regular check-ups, so doctors can find ', { m: 'm6', c: 'c5', t: 'ilnesses' }, ' early. ', { m: 'm7', c: 't1', t: 'I agree with Paul that money is important' },
    ', but I would add that education matters too. ', { m: 'm8', c: 'c7', t: 'If people will understand' }, ' how sleep and sport affect health, they will make better choices. ', { m: 'm9', c: 'c8', t: 'However' },
    ', rich countries have problems like stress and fast food, so a long life is not automatic. ', { m: 'm10', c: 't2', t: 'In conclusion, I believe the combination of habits and medicine is the key factor.' },
  ];
  const ESSAY_WORDS = ESSAY.map((s) => (typeof s === 'string' ? s : s.t)).join('').trim().split(/\s+/).length;
  const CARD_ORDER = ['c1', 'c5', 'c3', 't1', 'c7', 'c8', 't2'];
  const FILTERS = [['all', 'All'], ['TUTOR', 'Tutor'], ['GRAMMAR', 'Grammar'], ['VOCABULARY', 'Vocabulary'], ['SPELLING', 'Spelling'], ['COHERENCE', 'Coherence']];

  const markHtml = (s) => {
    const card = WCARDS[s.c];
    const cls = card.kind === 'tutor' ? 'tutor' : card.cat.toLowerCase();
    const label = card.kind === 'tutor' ? 'Tutor comment' : `${CAT[card.cat][1]} comment`;
    const ic = card.kind === 'tutor' ? P.avatar('AK', 'xs') : P.i('sparkles');
    return `<span class="rv-m m-${cls}" data-m="${s.m}" data-c="${s.c}" tabindex="0" role="button" aria-label="${label}">${s.t}<span class="mk-ic">${ic}</span><span class="mk-num">${CARD_ORDER.indexOf(s.c) + 1}</span></span>`;
  };
  const cardHtml = (id) => {
    const c = WCARDS[id];
    const n = CARD_ORDER.indexOf(id) + 1;
    const cat = c.kind === 'tutor' ? 'TUTOR' : c.cat;
    if (c.kind === 'tutor') {
      const th = c.thread ? THREADS[c.thread] : null;
      return `<div class="rv-card c-tutor" data-card="${id}" data-cat="TUTOR" tabindex="0"><span class="rv-stem" hidden></span>
        <div class="ch"><span class="cn">${n}</span>${P.avatar('AK', 'xs')}<span class="who">Anna K.</span><span class="rv-role tutor">Tutor</span></div>
        <div>${th ? P.esc(th.items[0].text) : c.text}</div>
        <div class="acts">${c.thread ? `${P.btn('Reply', { v: 'ghost', icon: 'message-circle', attrs: `data-reply="${c.thread}"` })}<span class="t-caption muted">${th.items.length - 1} replies</span>` : ''}</div></div>`;
    }
    return `<div class="rv-card c-${cat.toLowerCase()}" data-card="${id}" data-cat="${cat}" tabindex="0"><span class="rv-stem" hidden></span>
      <div class="ch"><span class="cn">${n}</span><span class="who">${P.i('sparkles')}Toefly Tutor</span>${catTag(cat)}</div>
      <div>${c.text}</div>
      <div class="rv-sugg"><div class="l">Suggestion</div><div>${c.sugg}</div></div>
      ${ru(c.ru)}
      <div class="acts">${P.btn('Explain in Russian', { v: 'ghost', icon: 'languages', attrs: 'data-ru-btn aria-pressed="false"' })}${c.unit ? `<a class="gl" href="#/grammar">${P.i('book-open')}${c.unit}</a>` : ''}</div></div>`;
  };

  const RUBRIC_ROWS = [
    [5, 'Fully answers the question and responds to others. Ideas are developed with clear examples. Language is varied, with only rare slips.'],
    [4, 'Answers the question and adds to the discussion. Mostly clear development. A few small language errors that never block meaning.'],
    [3, 'Answers the question, but ideas are thin or only partly linked. Noticeable errors, though the meaning is still clear.'],
    [2, 'Only partly relevant. Ideas are not developed. Frequent errors sometimes make meaning hard to follow.'],
    [1, 'Barely related to the topic. Very limited language, with errors in most sentences.'],
    [0, 'Blank, off topic or not in English.'],
  ];
  const dots = (n, label) => `<span class="rv-dots" role="img" aria-label="${label}: ${n} of 5">${[1, 2, 3, 4, 5].map((i) => `<i class="${i <= n ? 'on' : ''}"></i>`).join('')}</span>`;

  P.register('/review/writing', {
    back: { label: 'Week 7 · Writing', href: '/weeks/7/writing' },
    title: 'Review: Writing', num: 15, nav: 'weeks', tab: 'weeks', web: true, ios: true,
    tutorContext: 'Academic discussion: Longevity', tutorQuestion: 'Explain my last Writing score',
    render(ctx) {
      const essay = ESSAY.map((s) => (typeof s === 'string' ? s : markHtml(s))).join('');
      return `<div class="page">
        <div class="rv-head"><div><h1 class="t-title-1">Academic discussion: Longevity</h1><div class="sub">Writing · submitted Oct 12</div></div>
          <div class="rv-vers"><span class="lab">Version:</span><div class="seg" role="tablist" aria-label="Version"><button role="tab" class="on" data-ver="draft" aria-selected="true">Draft</button><button role="tab" data-ver="a2" aria-selected="false">Attempt 2</button></div></div></div>
        <section class="card lg rv-rubric" aria-label="Score">
          <div class="score"><span class="t-caption muted">Writing score</span><div class="big"><span class="t-score">3</span><span class="of">of 5</span></div>
            <div class="rv-segbar" role="img" aria-label="Score 3 on a scale from 0 to 5">${[0, 1, 2, 3, 4, 5].map((i) => `<i class="${i === 3 ? 'on' : ''}"></i>`).join('')}</div>
            <div class="rv-segbar-lbl">${[0, 1, 2, 3, 4, 5].map((i) => `<span>${i}</span>`).join('')}</div>
            <div class="mt-2"><span class="ai-badge">AI estimate</span></div></div>
          <div class="rv-crit">
            <div class="r"><span class="n">Task response</span>${dots(3, 'Task response')}<span class="t">Adequate</span></div>
            <div class="r"><span class="n">Organization</span>${dots(4, 'Organization')}<span class="t">Clear</span></div>
            <div class="r"><span class="n">Language use</span>${dots(3, 'Language use')}<span class="t">Some errors</span></div>
            <div class="mt-1">${P.btn('See the rubric', { v: 'ghost', size: 'sm', icon: 'list-checks', attrs: 'id="rubricBtn"' })}</div></div>
        </section>
        <div data-pane="draft">
          <div class="rv-filters" role="group" aria-label="Filter comments">${FILTERS.map(([k, l], i) => `<button type="button" class="chip ${i === 0 ? 'on' : ''}" data-filter="${k}" aria-pressed="${i === 0}">${l}</button>`).join('')}</div>
          <div class="rv-wgrid">
            <div class="rv-essay" id="essay"><div class="meta"><span>Draft · Oct 12</span><span>Word count: ${ESSAY_WORDS}</span></div><div>${essay}</div></div>
            <aside class="rv-comments" id="comments" aria-label="Comments">${CARD_ORDER.map(cardHtml).join('')}</aside>
          </div>
          <div class="rv-spell">${P.i('triangle-alert')}<div><b>Spelling: 3 mistakes (spellcheck was off)</b><div class="t-caption muted">healty, goverment, ilnesses. Spellcheck is off in the real exam, so check these words by eye.</div></div></div>
          <details class="rv-det rv-sample" style="margin-top:12px"><summary>${P.i('award')}Strong sample answer${P.i('chevron-down', 'chev')}</summary><div class="in">
            <p>Life expectancy is higher in some countries mostly because of everyday habits and strong public health. In Japan, for instance, people eat a lot of fish and vegetables and walk daily, while the state pays for regular screening, so doctors catch illnesses early. I agree with Paul that money matters, but education matters as well: people who understand how sleep and exercise affect health make better choices. Rich countries have their own problems, such as stress and processed food, so wealth alone does not guarantee a long life. Overall, long lives come from habits and medicine working together.</p></div></details>
        </div>
        <div data-pane="a2" hidden>
          <div class="card lg" style="margin-top:20px">${P.empty({ art: '<path d="M30 70h60M40 56l32-32 10 10-32 32-14 4z"/><path d="M66 30l10 10"/>', title: 'Attempt 2 is in progress', text: 'Draft saved on this device · 64 words · started 10 min ago. Your Draft and its comments stay read-only on the right while you write.', btn: P.btn('Continue Attempt 2', { href: '/tasks/discussion', icon: 'pen-line' }) })}</div>
        </div>
        <div class="rv-foot">
          ${P.btn('Start Attempt 2', { href: '/tasks/discussion', icon: 'pen-line' })}
          ${P.btn('Ask tutor', { v: 'secondary', icon: 'sparkles', attrs: 'data-action="tutor" data-context="Academic discussion: Longevity"' })}
        </div></div>`;
    },
    mount(root) {
      const offs = [];
      const essay = $(root, '#essay'), col = $(root, '#comments');
      let filter = 'all';
      const cards = () => $$(col, '.rv-card');
      const marksOf = (c) => $$(root, `.rv-m[data-c="${c}"]`);

      function layout() {
        if ($(root, '[data-pane="draft"]').hidden) return;
        const compact = root.clientWidth < 768;
        const vis = cards().filter((c) => !c.classList.contains('hidden'));
        if (compact) {
          col.style.height = '';
          cards().forEach((c) => { c.style.top = ''; $(c, '.rv-stem').hidden = true; });
          // numbered list under the essay in the order of the numbers
          vis.sort((a, b) => CARD_ORDER.indexOf(a.dataset.card) - CARD_ORDER.indexOf(b.dataset.card)).forEach((c) => col.appendChild(c));
          return;
        }
        let y = 0;
        vis.forEach((c) => {
          const mk = marksOf(c.dataset.card)[0];
          const my = mk.offsetTop + 16;
          const top = Math.max(my - 18, y);
          c.style.top = top + 'px';
          const stem = $(c, '.rv-stem');
          if (top - (my - 18) > 6) { stem.hidden = false; stem.style.top = (my - top) + 'px'; stem.style.height = (18 - (my - top)) + 'px'; } else stem.hidden = true;
          y = top + c.offsetHeight + 10;
        });
        col.style.height = Math.max(y - 10, 0) + 'px';
      }

      function focus(id, from) {
        $$(root, '.rv-card.focus, .rv-m.focus').forEach((e) => e.classList.remove('focus'));
        const card = $(col, `[data-card="${id}"]`);
        if (!card) return;
        card.classList.add('focus');
        marksOf(id).forEach((m) => m.classList.add('focus'));
        if (from === 'mark') card.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
        else marksOf(id)[0].scrollIntoView({ block: 'nearest', behavior: 'smooth' });
      }

      function applyFilter() {
        cards().forEach((c) => c.classList.toggle('hidden', !(filter === 'all' || c.dataset.cat === filter)));
        $$(root, '.rv-m').forEach((m) => m.classList.toggle('off', $(col, `[data-card="${m.dataset.c}"]`).classList.contains('hidden')));
        $$(root, '[data-filter]').forEach((b) => { const o = b.dataset.filter === filter; b.classList.toggle('on', o); b.setAttribute('aria-pressed', o); });
        layout();
      }

      offs.push(on(root, 'click', (e) => {
        const f = e.target.closest('[data-filter]');
        if (f) { filter = f.dataset.filter; applyFilter(); return; }
        const v = e.target.closest('[data-ver]');
        if (v) {
          $$(root, '[data-ver]').forEach((b) => { const o = b === v; b.classList.toggle('on', o); b.setAttribute('aria-selected', o); });
          $$(root, '[data-pane]').forEach((p) => { p.hidden = p.dataset.pane !== v.dataset.ver; });
          layout(); return;
        }
        if (e.target.closest('#rubricBtn')) { openRubric(); return; }
        const rp = e.target.closest('[data-reply]');
        if (rp) { openThread(rp.dataset.reply); return; }
        const rb = e.target.closest('[data-ru-btn]');
        if (rb) { const t = $(rb.closest('.rv-card'), '.rv-ru').classList.toggle('on'); rb.setAttribute('aria-pressed', t); layout(); return; }
        if (e.target.closest('a, button')) return;
        const m = e.target.closest('.rv-m');
        if (m && !m.classList.contains('off')) { focus(m.dataset.c, 'mark'); return; }
        const c = e.target.closest('.rv-card');
        if (c) focus(c.dataset.card, 'card');
      }));
      offs.push(on(root, 'keydown', (e) => {
        if (e.key !== 'Enter' && e.key !== ' ') return;
        const m = e.target.closest && e.target.closest('.rv-m');
        if (m && !m.classList.contains('off')) { e.preventDefault(); focus(m.dataset.c, 'mark'); }
        else if (e.target.matches && e.target.matches('.rv-card')) { e.preventDefault(); focus(e.target.dataset.card, 'card'); }
      }));

      function openRubric() {
        P.openOverlay(`<div class="stack gap-3"><div class="row between"><span class="t-title-3">Writing rubric · Academic Discussion</span>${P.btn('Close', { v: 'icon', icon: 'x', size: 'sm', attrs: 'data-action="close"' })}</div>
          <div class="t-caption muted">Scores run from 0 to 5. Your Draft is a 3.</div>
          <table class="table"><thead><tr><th>Score</th><th>What it looks like</th></tr></thead><tbody>${RUBRIC_ROWS.map(([s, d]) => `<tr ${s === 3 ? 'style="background:var(--accent-soft)"' : ''}><td><b>${s}</b>${s === 3 ? ' (you)' : ''}</td><td>${d}</td></tr>`).join('')}</tbody></table>
          <div class="t-caption muted">Criteria: Task response, Organization, Language use.</div></div>`, { label: 'Writing rubric', large: true, width: 560 });
      }

      const ro = new ResizeObserver(() => layout());
      ro.observe(root);
      offs.push(() => ro.disconnect());
      if (document.fonts && document.fonts.ready) document.fonts.ready.then(layout);
      requestAnimationFrame(layout);
      return () => offs.forEach((f) => f());
    },
  });

  /* ═════════════ 16 · Review: Speaking (Listen and Repeat) ═════════════ */
  /* DSL: word@score:from>to, -word = omitted, +word = inserted */
  const SENT = [
    { s: 'Please return@72:r>w the books to the front desk.', sc: 4 },
    { s: 'Professor Lee thought@54:θ>s the lecture was thoroughly@58:θ>t prepared.', sc: 3 },
    { s: 'We need to finish the lab report before Thursday@66:θ>t.', sc: 4 },
    { s: 'The students gathered near the river to study.', sc: 5 },
    { s: 'She has -already registered@71:r>l for the research seminar.', sc: 3 },
    { s: 'Could you explain how the +really experiment@77:ɪ>i works?', sc: 4 },
    { s: 'The museum is open every Friday afternoon.', sc: 5 },
  ];
  function pwords(str, row) {
    return str.split(' ').map((tok, i) => {
      const id = `w${row}_${i}`;
      if (tok[0] === '-') return `<span class="pw omit">${tok.slice(1)}<span class="sr-only"> (missed word)</span></span>`;
      if (tok[0] === '+') return `<span class="pw ins">${tok.slice(1)}<span class="sr-only"> (extra word)</span></span>`;
      const m = tok.match(/^([^@]+)@(\d+)(?::(.+))?$/);
      if (!m) return `<span class="pw">${tok}</span>`;
      const score = +m[2];
      const cls = score >= 80 ? '' : score >= 60 ? 'weak' : 'bad';
      const ph = m[3] ? m[3].split('>').map((p) => `/${p}/`).join(' → ') : '';
      return `<button type="button" class="pw ${cls}" data-w="${P.esc(m[1].replace(/[.?,]/g, ''))}" data-s="${score}" data-ph="${P.esc(ph)}" aria-haspopup="dialog">${m[1]}<span class="sr-only"> (accuracy ${score})</span></button>`;
    }).join(' ');
  }

  P.register('/review/speaking', {
    back: { label: 'Week 7 · Speaking', href: '/weeks/7/speaking' },
    title: 'Review: Speaking', num: 16, nav: 'weeks', tab: 'weeks', web: true, ios: true,
    tutorContext: 'Listen and Repeat practice 7', tutorQuestion: 'How do I say /θ/ correctly?',
    render(ctx) {
      const minis = [['Accuracy', 82], ['Fluency', 88], ['Completeness', 91], ['Prosody', 76]];
      const total = SENT.reduce((a, b) => a + b.sc, 0);
      return `<div class="page narrow">
        <div class="rv-head"><div><h1 class="t-title-1">Listen and Repeat practice 7</h1><div class="sub">Speaking · 7 sentences · reviewed Oct 12</div></div><div class="row gap-2">${P.tag('speaking', 'Speaking')}<span class="ai-badge">AI estimate</span></div></div>
        <section class="card lg" aria-label="Summary">
          <div class="rv-minis">${minis.map(([n, v]) => `<div class="rv-mini"><div class="top"><span>${n}</span><b>${v}</b></div>${P.bar(v, { pct: false, label: n })}</div>`).join('')}</div>
          <div class="rv-legend" aria-label="Legend">
            <span><span class="pw">word</span> 80 or more</span><span><span class="pw weak">word</span> 60 to 79</span><span><span class="pw bad">word</span> below 60</span>
            <span><span class="pw omit">word</span> missed</span><span><span class="pw ins">word</span> extra</span></div>
        </section>
        <div class="card" style="padding:0;margin-top:16px;overflow:visible">
          ${SENT.map((r, i) => `<div class="rv-srow" data-row="${i}"><span class="no">${i + 1}</span><div class="sent">${pwords(r.s, i)}</div>
            <div class="play">${P.btn('Model', { v: 'secondary', icon: 'volume-2', attrs: 'data-play aria-label="Play model, sentence ' + (i + 1) + '"' })}${P.btn('You', { v: 'secondary', icon: 'play', attrs: 'data-play aria-label="Play your recording, sentence ' + (i + 1) + '"' })}</div>
            <div class="sc"><b>${r.sc}</b><span> / 5</span></div></div>`).join('')}
          <div class="rv-stotal"><span>Total</span><span>${total} of 35 · Speaking 4/5</span></div>
        </div>
        <div class="card lg mt-4 rv-sounds"><div><div class="t-caption muted">Sounds to practice</div><div class="ipa">Sounds to practice: /θ/, /r/</div></div>${P.btn('Practice these sounds', { icon: 'mic', attrs: 'data-action="toast" data-text="Mini-set created: 6 sentences with /θ/ and /r/"' })}</div>
        <div class="rv-foot">${P.btn('Ask tutor', { v: 'secondary', icon: 'sparkles', attrs: 'data-action="tutor" data-context="Listen and Repeat practice 7"' })}${P.btn('Back to Week 7', { v: 'ghost', href: '/weeks/7' })}</div></div>`;
    },
    mount(root) {
      const offs = [], timers = [];
      let pop = null, selBtn = null;
      const closePop = () => { if (pop) pop.remove(); if (selBtn) selBtn.classList.remove('sel'); pop = selBtn = null; };
      function fake(btn, ms = 1800) {
        if (btn._t) return;
        const h = btn.innerHTML;
        const label = btn.querySelector('span') ? btn.querySelector('span').textContent : 'Playing';
        btn.classList.add('playing');
        btn.innerHTML = `${P.i('pause')}<span>${label}</span>`;
        icons();
        btn._t = setTimeout(() => { btn.classList.remove('playing'); btn.innerHTML = h; btn._t = null; icons(); }, ms);
        timers.push(btn._t);
      }
      offs.push(on(root, 'click', (e) => {
        const pl = e.target.closest('[data-play]');
        if (pl) { fake(pl); return; }
        const w = e.target.closest('.pw.weak, .pw.bad');
        if (w) {
          e.stopPropagation();
          const same = selBtn === w;
          closePop();
          if (same) return;
          const row = w.closest('.rv-srow');
          selBtn = w; w.classList.add('sel');
          pop = document.createElement('div');
          pop.className = 'rv-pop'; pop.setAttribute('role', 'dialog'); pop.setAttribute('aria-label', `Pronunciation of ${w.dataset.w}`);
          pop.innerHTML = `<div class="w">${w.dataset.w}</div><div class="t-caption muted">accuracy ${w.dataset.s}</div>${w.dataset.ph ? `<div class="ipa">${w.dataset.ph}</div>` : ''}
            <div class="row2">${P.btn('Listen to model', { v: 'secondary', icon: 'volume-2', attrs: 'data-play' })}${P.btn('Listen to you', { v: 'secondary', icon: 'play', attrs: 'data-play' })}</div>`;
          row.appendChild(pop);
          pop.style.top = (w.offsetTop + w.offsetHeight + 6) + 'px';
          pop.style.left = Math.max(8, Math.min(w.offsetLeft, row.clientWidth - 266)) + 'px';
          icons();
          return;
        }
        if (pop && !e.target.closest('.rv-pop')) closePop();
      }));
      offs.push(on(document, 'keydown', (e) => { if (e.key === 'Escape') closePop(); }));
      return () => { offs.forEach((f) => f()); timers.forEach(clearTimeout); };
    },
  });

  /* ═════════════ 17 · Weekly review ═════════════ */
  const SPARK = { R: [3.5, 4.0, 4.0, 4.5], L: [4.0, 4.0, 4.5, 4.5], W: [3.5, 3.5, 3.5, 4.0], S: [4.0, 4.0, 4.0, 4.5] };
  const spark = (vals, cls) => {
    const W = 120, H = 44, min = 3, max = 5;
    const pts = vals.map((v, i) => [8 + i * ((W - 16) / (vals.length - 1)), H - 6 - ((v - min) / (max - min)) * (H - 12)]);
    const last = pts[pts.length - 1];
    return `<svg viewBox="0 0 ${W} ${H}" class="rv-chart ${cls}" role="img" aria-label="Weeks 4 to 7: ${vals.join(', ')}"><path class="ln ${cls}" d="M${pts.map((p) => p.join(' ')).join(' L')}"/>${pts.map((p, i) => `<circle class="mk ${cls} ${i === pts.length - 1 ? 'mock' : ''}" cx="${p[0]}" cy="${p[1]}" r="${i === pts.length - 1 ? 4 : 3}"/>`).join('')}<circle class="halo ${cls}" cx="${last[0]}" cy="${last[1]}" r="8"/></svg>`;
  };
  const MISTAKES = [
    ['GRAMMAR', 'If people will understand how sleep affects health…', 'Use the present simple after <i>if</i>: "If people understand…".', 'Round-Up Unit 7: Conditionals', '/grammar',
      'После if используйте Present Simple: «If people understand…».'],
    ['GRAMMAR', 'many people eat fish and they walks a lot', 'Base form after <i>they</i>: "they walk".', 'Collins Grammar Unit 7', '/grammar', 'После they глагол без -s: «they walk».'],
    ['SPELLING', 'goverment, ilnesses, healty', 'Three slips in one essay. Check the endings -ment and -ness.', 'Spelling drill', '/vocabulary', 'Три ошибки в одном эссе. Проверяйте окончания -ment и -ness.'],
    ['PRONUNCIATION', 'thought → "sought", thoroughly → "toroughly"', 'Put the tongue tip between the teeth for /θ/.', 'Practice /θ/', '/review/speaking', 'Для /θ/ кончик языка между зубами.'],
    ['COMPREHENSION', 'Question 3 · Academic talk: Biology (chose the energy detail, not the main point)', 'Listen for the sentence after "but" or "also": it often carries the main idea.', 'Review the talk', '/review/attempt', 'Слушайте предложение после «but» или «also»: в нём часто главная мысль.'],
  ];
  const DONE_BY_SECTION = [['READING', 3, 4], ['LISTENING', 3, 4], ['WRITING', 2, 3], ['SPEAKING', 1, 2], ['VOCABULARY', 2, 2], ['GRAMMAR', 3, 3]];

  P.register('/weeks/7/review', {
    back: { label: 'Week 7', href: '/weeks/7' },
    title: 'Weekly review', num: 17, nav: 'weeks', tab: 'weeks', web: true, ios: true,
    tutorContext: 'Week 7 review', tutorQuestion: 'Explain my Week 7 review',
    render(ctx) {
      const w = P.fx.week7;
      return `<div class="page">
        <div class="rv-col">
          ${P.cover(w.coverId, 'section-header')}
          <div class="rv-head" style="margin:8px 0 0"><div><h1 class="t-title-1">Week 7 review</h1><div class="sub">${w.dates} · written by Toefly Tutor</div></div>
            <label class="rv-lbl">Explain in Russian<button type="button" class="switch" role="switch" aria-checked="false" aria-label="Explain in Russian" id="ruSwitch"></button></label></div>

          <section class="card lg" aria-labelledby="h-did"><h2 id="h-did">What you did</h2>
            <div class="t-title-2">14 of 18 tasks · 4 h 10 min</div>
            <div class="rv-bars">${DONE_BY_SECTION.map(([s, d, t]) => `<div class="r">${P.tag(s, P.sec(s).label)}${P.bar(Math.round((d / t) * 100), { pct: false, thin: true, label: P.sec(s).label })}<span class="c">${d} of ${t}</span></div>`).join('')}</div>
            ${ru('Вы выполнили 14 из 18 заданий за 4 часа 10 минут. Лучше всего вышли словарь и грамматика, чаще всего пропускали Speaking.')}</section>

          <section class="card lg" aria-labelledby="h-sc"><h2 id="h-sc">Scores</h2>
            <div class="row gap-2 wrap" style="margin-bottom:12px"><span class="t-title-3">Reading 4.0 → 4.5</span><span class="ai-badge">AI estimate</span></div>
            <div class="rv-sparks">${[['R', 'Reading', 's-r'], ['L', 'Listening', 's-l'], ['W', 'Writing', 's-w'], ['S', 'Speaking', 's-s']].map(([k, n, c]) => {
              const v = SPARK[k]; const a = v[2], b = v[3];
              return `<div class="rv-spark ${c}"><div class="row between"><span class="t-strong">${n}</span>${P.tag(a === b ? 'neutral' : 'ok', a === b ? 'No change' : '+' + (b - a).toFixed(1))}</div>${spark(v, c)}<div class="v">${a.toFixed(1)} → ${b.toFixed(1)}</div><div class="s">Weeks 4 to 7</div></div>`;
            }).join('')}</div>
            <div class="t-caption muted mt-3">Practice score per section on the 1–6 scale. The dot is this week.</div>
            ${ru('Оценка по практике: Reading вырос с 4.0 до 4.5, Speaking тоже подрос, Listening остался на месте.')}</section>

          <section class="card lg" aria-labelledby="h-mi"><h2 id="h-mi">Top 5 recurring mistakes</h2>
            <ol class="rv-mlist">${MISTAKES.map(([c, q, tip, link, href, r], i) => `<li><span class="n">${i + 1}</span><div><div class="row gap-2 wrap">${catTag(c)}</div><div class="q">${P.esc(q)}</div><div>${tip}</div>${ru(r)}<a class="link t-caption" href="#${href}" style="display:inline-block;margin-top:6px">Practice this</a></div></li>`).join('')}</ol></section>

          <section class="card lg" aria-labelledby="h-st"><h2 id="h-st">Strengths</h2>
            <ul class="rv-ticks">
              <li>${P.i('check')}<div>Reading: you find the main idea fast. 3 of 3 on the Campus Gym email.</div></li>
              <li>${P.i('check')}<div>Vocabulary: all 12 reviews done on time, and 86 words learned in total.</div></li>
              <li>${P.i('check')}<div>Consistency: a 12-day streak with sessions every day this week.</div></li></ul>
            ${ru('Сильные стороны: быстрый поиск главной мысли в Reading, все повторения слов в срок и серия из 12 дней.')}</section>

          <section class="card lg" aria-labelledby="h-fo"><h2 id="h-fo">Focus for Week 8</h2>
            <ul class="rv-ticks num">
              <li><div>Conditionals out loud: ten minutes a day, "if + present, will + base".</div></li>
              <li><div>Build a Sentence: finish both practice sets, watch where <i>not</i> goes.</div></li>
              <li><div>Listening main-idea questions: read the stem first, then listen for contrast words.</div></li></ul>
            ${ru('Фокус недели 8: условные предложения вслух, Build a Sentence и вопросы на главную мысль в Listening.')}</section>

          <section class="card lg" aria-labelledby="h-tu"><h2 id="h-tu">From your tutor</h2>
            <div class="row gap-3" style="align-items:flex-start;margin-bottom:14px">${P.avatar('AK', 'lg')}<div><div class="t-strong">Anna K. <span class="rv-role tutor">Tutor</span></div><p style="margin-top:4px">Great week, Maria. The Reading jump is real and the Longevity essay shows your ideas are ready. Now let's clean up the small grammar slips so the score follows.</p></div></div>
            ${threadHtml('wk')}</section>

          <section class="card lg rv-wait" aria-labelledby="h-w8">
            <span class="icon-box" style="width:48px;height:48px;border-radius:50%">${P.i('hourglass', 'ic-24')}</span>
            <div class="grow"><h2 id="h-w8" style="margin:0">Week 8 is being prepared</h2><div class="muted">Week 8 awaits approval from Anna K. It will be ready by Sat, Oct 17.</div></div>
            ${P.btn('Remind me when it is ready', { v: 'secondary', icon: 'bell', attrs: 'data-action="toast" data-text="We will notify you when Week 8 is published"' })}</section>
        </div></div>`;
    },
    mount(root) {
      const offs = [];
      const sw = $(root, '#ruSwitch');
      offs.push(on(sw, 'click', () => { const v = sw.classList.toggle('on'); sw.setAttribute('aria-checked', v); $(root, '.rv-col').classList.toggle('ru-on', v); }));
      offs.push(bindThread($(root, '.rv-col')));
      return () => offs.forEach((f) => f());
    },
  });

  /* ═════════════ 18 · Mock exam results ═════════════ */
  P.register('/exams/1/result', {
    back: { label: 'Mock exams', href: '/exams' },
    title: 'Mock exam results', num: 18, nav: 'exams', tab: 'progress', web: true, ios: true,
    tutorContext: 'Mock exam 1 · Sep 26', tutorQuestion: 'Explain my Writing score',
    render(ctx) {
      const secs = [['READING', '5.0', 'C1', '31/35'], ['LISTENING', '4.5', 'B2', '28/35'], ['WRITING', '4.0', 'B2', 'AI 4/5, 3/5'], ['SPEAKING', '4.5', 'B2', 'AI 4/5']];
      return `<div class="page narrow">
        <div class="rv-head"><h1 class="t-title-1">Mock exam 1 · Sep 26</h1>${P.tag('exam', 'Week 4', { mod: 'exam' })}</div>
        <section class="card lg rv-hero" aria-label="Overall">
          ${P.ring({ value: 4.5, max: 6, size: 120, center: '4.5', label: 'Overall band 4.5, AI estimate' })}
          <div class="stack gap-2"><div class="big">Overall 4.5 · CEFR B2 · ≈ 80 of 120</div>
            <div class="row gap-2 wrap"><span class="ai-badge">AI estimate</span><span class="muted t-caption">comparable 0–120 shown until 2028</span></div></div>
        </section>
        <div class="rv-secs">${secs.map(([s, b, c, d]) => `<div class="rv-sec"><div class="top">${P.tag(s, P.sec(s).label)}${P.ring({ value: +b, max: 6, size: 48, center: '', label: `${P.sec(s).label} band ${b}` })}</div>
          <div><span class="band">${b}</span> <span class="cefr">${c}</span></div><div class="det">${d}</div><span class="ai-badge" style="align-self:flex-start">AI estimate</span></div>`).join('')}</div>
        <div class="rv-compare">${P.i('trending-up')}<span>Compared with diagnostic: +1.0</span></div>
        <div class="rv-foot">${P.btn('Review answers', { href: '/review/attempt', icon: 'list-checks' })}${P.btn('Ask tutor', { v: 'secondary', icon: 'sparkles', attrs: 'data-action="tutor" data-context="Mock exam 1 · Sep 26"' })}${P.btn('Back to Week 4', { v: 'ghost', href: '/weeks' })}</div></div>`;
    },
  });

  /* ═════════════ 19 · Progress dashboard ═════════════ */
  const SERIES = [
    { k: 'R', name: 'Reading', cls: 's-r', shape: 'circle', dx: -4.5, v: [4.0, 4.0, 4.5, 5.0, 5.0, 5.0, 5.0] },
    { k: 'L', name: 'Listening', cls: 's-l', shape: 'square', dx: -1.5, v: [3.5, 4.0, 4.0, 4.5, 4.5, 4.5, 4.5] },
    { k: 'W', name: 'Writing', cls: 's-w', shape: 'triangle', dx: 1.5, v: [3.0, 3.5, 3.5, 4.0, 4.0, 4.0, 4.0] },
    { k: 'S', name: 'Speaking', cls: 's-s', shape: 'diamond', dx: 4.5, v: [3.5, 3.5, 4.0, 4.5, 4.0, 4.5, 4.5] },
  ];
  const MOCK_WEEK = 4;
  const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  const ACT = [
    { R: 40, L: 35, W: 15, S: 10, V: 20 }, { R: 30, L: 30, W: 25, S: 12, V: 15 }, { R: 10, V: 8 }, {}, {}, {}, {},
  ];
  const ACT_SECS = [['R', 'Reading', 's-r'], ['L', 'Listening', 's-l'], ['W', 'Writing', 's-w'], ['S', 'Speaking', 's-s'], ['V', 'Vocabulary', 's-v']];

  function shapeSvg(kind, x, y, r, cls) {
    if (kind === 'circle') return `<circle class="${cls}" cx="${x}" cy="${y}" r="${r}"/>`;
    if (kind === 'square') return `<rect class="${cls}" x="${x - r}" y="${y - r}" width="${2 * r}" height="${2 * r}" rx="1.5"/>`;
    if (kind === 'triangle') return `<path class="${cls}" d="M${x} ${y - r - 1.5} L${x + r + 1.5} ${y + r} L${x - r - 1.5} ${y + r} Z"/>`;
    return `<path class="${cls}" d="M${x} ${y - r - 2.5} L${x + r + 2.5} ${y} L${x} ${y + r + 2.5} L${x - r - 2.5} ${y} Z"/>`;
  }

  function lineChart(weeks, ios) {
    const W = ios ? 350 : 640, H = ios ? 270 : 300, L = ios ? 30 : 42, R = 14, T = 26, B = 34, yMin = 2.5, yMax = 5.5;
    const x = (i) => L + 14 + (weeks.length === 1 ? 0 : (i * (W - L - R - 28)) / (weeks.length - 1));
    const y = (v) => T + ((yMax - v) / (yMax - yMin)) * (H - T - B);
    let g = '';
    [3, 3.5, 4, 4.5, 5].forEach((t) => { g += `<line class="gl" x1="${L}" x2="${W - R}" y1="${y(t)}" y2="${y(t)}"/><text class="ax e" x="${L - 8}" y="${y(t) + 4}">${t.toFixed(1)}</text>`; });
    weeks.forEach((wk, i) => { g += `<text class="ax c" x="${x(i)}" y="${H - 10}">${ios ? wk : 'Week ' + wk}</text>`; });
    const mi = weeks.indexOf(MOCK_WEEK);
    if (mi >= 0) g += `<line class="mockline" x1="${x(mi)}" x2="${x(mi)}" y1="${T - 6}" y2="${H - B}"/><text class="ax c" x="${x(mi)}" y="${T - 12}">Mock exam 1</text>`;
    SERIES.forEach((s) => {
      const vals = weeks.map((w) => s.v[w - 1]);
      g += `<path class="ln ${s.cls}" d="M${vals.map((v, i) => `${(x(i) + s.dx).toFixed(1)} ${y(v).toFixed(1)}`).join(' L')}"/>`;
    });
    SERIES.forEach((s) => {
      weeks.forEach((w, i) => {
        const v = s.v[w - 1], mock = w === MOCK_WEEK, cx = x(i) + s.dx, cy = y(v);
        if (mock) g += `<circle class="halo ${s.cls}" cx="${cx}" cy="${cy}" r="10"/>`;
        g += shapeSvg(s.shape, cx, cy, mock ? 4.5 : 3.5, `mk ${mock ? 'mock' : ''} ${s.cls}`);
      });
    });
    const label = 'Band by section over time. ' + SERIES.map((s) => `${s.name} ${s.v[weeks[0] - 1]} to ${s.v[weeks[weeks.length - 1] - 1]}`).join('; ');
    return `<svg class="rv-chart" viewBox="0 0 ${W} ${H}" role="img" aria-label="${label}">${g}</svg>
      <div class="rv-leg">${SERIES.map((s) => `<span><svg viewBox="0 0 14 14" class="${s.cls}">${shapeSvg(s.shape, 7, 7, 4, 'shape')}</svg>${s.name}</span>`).join('')}<span><svg viewBox="0 0 14 14" class="s-r">${shapeSvg('circle', 7, 7, 4.5, 'shape f')}</svg>Mock exam (filled, ringed)</span></div>`;
  }
  const lineTable = (weeks) => `<table class="table"><caption class="sr-only">Band by section and week</caption><thead><tr><th>Week</th>${SERIES.map((s) => `<th>${s.name}</th>`).join('')}</tr></thead><tbody>${weeks.map((w) => `<tr><td>Week ${w}${w === MOCK_WEEK ? ' · Mock exam 1' : ''}</td>${SERIES.map((s) => `<td class="t-mono">${s.v[w - 1].toFixed(1)}</td>`).join('')}</tr>`).join('')}</tbody></table>`;

  function activityChart(ios) {
    const W = ios ? 350 : 640, H = ios ? 250 : 260, L = ios ? 34 : 42, R = 10, T = 12, B = 30, max = 120;
    const y = (v) => T + (1 - v / max) * (H - T - B);
    const slot = (W - L - R) / 7, bw = Math.min(40, slot * 0.6);
    let g = '';
    [0, 30, 60, 90, 120].forEach((t) => { g += `<line class="gl" x1="${L}" x2="${W - R}" y1="${y(t)}" y2="${y(t)}"/><text class="ax e" x="${L - 8}" y="${y(t) + 4}">${t}</text>`; });
    g += `<line class="goal" x1="${L}" x2="${W - R}" y1="${y(30)}" y2="${y(30)}"/>`;
    ACT.forEach((d, i) => {
      const cx = L + slot * i + slot / 2;
      let base = 0;
      ACT_SECS.forEach(([k, n, cls]) => {
        if (!d[k]) return;
        const y1 = y(base + d[k]), y0 = y(base);
        g += `<rect class="bar-seg ${cls}" x="${cx - bw / 2}" y="${y1}" width="${bw}" height="${y0 - y1}" rx="2" style="stroke:var(--surface);stroke-width:1.5"><title>${DAYS[i]}: ${n} ${d[k]} min</title></rect>`;
        base += d[k];
      });
      g += `<text class="ax c" x="${cx}" y="${H - 10}">${DAYS[i]}</text>`;
      if (base) g += `<text class="ax c" x="${cx}" y="${y(base) - 6}">${base}</text>`;
    });
    return `<svg class="rv-chart" viewBox="0 0 ${W} ${H}" role="img" aria-label="Minutes per day this week: Sunday 120, Monday 112, Tuesday 18, total 4 h 10 min. Dashed line is the 30 minute goal.">${g}</svg>
      <div class="rv-leg">${ACT_SECS.map(([, n, cls]) => `<span class="${cls}"><i class="sw"></i>${n}</span>`).join('')}<span><svg viewBox="0 0 14 14"><line x1="0" x2="14" y1="7" y2="7" stroke="var(--ink-subtle)" stroke-width="1.5" stroke-dasharray="4 3"/></svg>Goal 30 min</span></div>`;
  }
  const activityTable = () => `<table class="table"><caption class="sr-only">Minutes per day by section</caption><thead><tr><th>Day</th>${ACT_SECS.map(([, n]) => `<th>${n}</th>`).join('')}<th>Total</th></tr></thead><tbody>${ACT.map((d, i) => `<tr><td>${DAYS[i]}</td>${ACT_SECS.map(([k]) => `<td class="t-mono">${d[k] || '–'}</td>`).join('')}<td class="t-mono"><b>${Object.values(d).reduce((a, b) => a + b, 0) || '–'}</b></td></tr>`).join('')}</tbody></table>`;

  const NORMS = [['Reading', 'R', 100, 90, '1:40', '1:30', 'per question'], ['Listening', 'L', 38, 40, '0:38', '0:40', 'per question'], ['Build a Sentence', 'W', 55, 60, '0:55', '1:00', 'per sentence']];
  const TOPM = [['GRAMMAR', 'Conditionals: "if" + will', 7, 'Improving', 'trending-down'], ['GRAMMAR', 'Subject-verb agreement', 6, 'Stable', 'minus'], ['SPELLING', 'Endings -ment and -ness', 5, 'Getting worse', 'trending-up'],
    ['PRONUNCIATION', '/θ/ in thought, thorough', 4, 'Improving', 'trending-down'], ['COMPREHENSION', 'Main idea vs. detail in talks', 3, 'Stable', 'minus']];

  const chartCard = (id, title, sub, chart, table, extra = '') => `<section class="card lg rv-chart-card" id="${id}" aria-labelledby="${id}-h">
    <div class="rv-chart-h"><div><h2 class="t-title-3" id="${id}-h">${title}</h2><div class="t-caption muted">${sub}</div></div><div class="row gap-2">${extra}${P.btn('Show as table', { v: 'ghost', icon: 'table', attrs: 'data-tbl aria-pressed="false"' })}</div></div>
    <div data-view="chart">${chart}</div><div data-view="table" hidden style="overflow:auto">${table}</div></section>`;

  P.register('/progress', {
    title: 'Progress dashboard', num: 19, nav: 'progress', tab: 'progress', web: true, ios: true,
    tutorContext: 'Progress dashboard', tutorQuestion: 'Am I on track for 5.0?',
    render(ctx) {
      const s = P.fx.student;
      const ios = ctx.ios;
      return `<div class="page">
        <div class="rv-head"><h1 class="t-title-1">Progress</h1><div class="seg" role="group" aria-label="Period"><button data-range="4">4 weeks</button><button data-range="8" class="on" aria-pressed="true">8 weeks</button><button data-range="all">All</button></div></div>
        <section class="card lg rv-ptop" aria-label="Estimated band">
          ${P.bandRing(s.band, 120)}
          <div class="stack gap-3 grow">
            ${P.bandText(s.band)}
            <div class="row gap-2 wrap"><span class="t-strong">Target ${s.goal.toFixed(1)} by Dec 27</span><span class="tag ok">${P.i('check')}<span class="tx">On track</span></span></div>
            ${P.miniRings(s.sections)}
          </div></section>
        ${chartCard('lineCard', 'Band over time', 'AI estimate by section, weekly. Mock exams are filled and ringed.', '<div id="lineChart"></div>', '<div id="lineTable"></div>')}
        ${chartCard('actCard', 'Weekly activity', 'Minutes per day, Week 7 · colored by section', activityChart(ios), activityTable())}
        <div class="rv-tiles">
          <div class="rv-tile"><div class="t-caption muted">Streak</div><div class="v">12 <small>days</small></div></div>
          <div class="rv-tile"><div class="t-caption muted">Time this week</div><div class="v">4 h 10 m</div></div>
          <div class="rv-tile"><div class="t-caption muted">Tasks done</div><div class="v">14 <small>/ 18</small></div></div>
          <div class="rv-tile"><div class="t-caption muted">Words learned</div><div class="v">86</div></div></div>
        <div class="rv-two">
          <section class="card lg" aria-labelledby="norm-h"><h2 class="t-title-3" id="norm-h">Time vs ETS norms</h2><div class="t-caption muted" style="margin-bottom:12px">Reading 1:40 per question (norm 1:30)</div>
            <div class="rv-norm">${NORMS.map(([n, k, v, nm, vt, nt, u]) => { const mx = Math.max(v, nm) * 1.25; const diff = v - nm; return `<div class="r s-${k.toLowerCase()}"><div class="h"><span><b>${n}</b> ${vt} ${u}</span><span class="muted">norm ${nt} · ${diff > 0 ? diff + ' s slower' : diff < 0 ? -diff + ' s faster' : 'on pace'}</span></div><div class="rv-track" role="img" aria-label="${n}: ${vt}, norm ${nt}"><i style="width:${(v / mx) * 100}%"></i><b style="left:${(nm / mx) * 100}%" title="Norm ${nt}"></b></div></div>`; }).join('')}</div></section>
          <section class="card lg" aria-labelledby="tm-h"><h2 class="t-title-3" id="tm-h">Top mistakes</h2><div class="t-caption muted" style="margin-bottom:6px">From your learner model, last 30 days</div>
            <ul class="rv-mrows">${TOPM.map(([c, t, n, tr, ic]) => `<li>${catTag(c)}<span class="tx">${t}</span><span class="tr">${P.i(ic)}${tr} · ${n}×</span></li>`).join('')}</ul></section>
        </div>
        <details class="rv-det" style="margin-top:16px"><summary>${P.i('history')}Legacy practice stats${P.i('chevron-down', 'chev')}</summary><div class="in muted">Practice from the pre-2026 Reading and Listening formats is counted separately: 6 sets, average 71%. It does not change your estimate.</div></details>
      </div>`;
    },
    mount(root, ctx) {
      const offs = [];
      let range = '8';
      const draw = () => {
        const weeks = range === '4' ? [4, 5, 6, 7] : [1, 2, 3, 4, 5, 6, 7];
        $(root, '#lineChart').innerHTML = lineChart(weeks, ctx.ios);
        $(root, '#lineTable').innerHTML = lineTable(weeks);
        $$(root, '[data-range]').forEach((b) => { const o = b.dataset.range === range; b.classList.toggle('on', o); b.setAttribute('aria-pressed', o); });
        icons();
      };
      draw();
      offs.push(on(root, 'click', (e) => {
        const r = e.target.closest('[data-range]');
        if (r) { range = r.dataset.range; draw(); return; }
        const t = e.target.closest('[data-tbl]');
        if (t) {
          const card = t.closest('.rv-chart-card');
          const showTable = $(card, '[data-view="table"]').hidden;
          $(card, '[data-view="table"]').hidden = !showTable;
          $(card, '[data-view="chart"]').hidden = showTable;
          $(t, 'span').textContent = showTable ? 'Show as chart' : 'Show as table';
          t.setAttribute('aria-pressed', showTable);
        }
      }));
      return () => offs.forEach((f) => f());
    },
  });

  /* ═════════════ 20 · Vocabulary list ═════════════ */
  /* [word, pos, definition, stability 0–4, source, origin, due] */
  const WORDS = [
    ['longevity', 'noun', 'the fact of living for a long time', 2, 'from: Longevity essay', 'mine', true],
    ['nutrient', 'noun', 'a substance that living things need to grow', 3, 'Collins Unit 7', 'collins', true],
    ['metabolism', 'noun', 'the chemical processes that turn food into energy', 1, 'Collins Unit 7', 'collins', true],
    ['sedentary', 'adjective', 'involving a lot of sitting and little exercise', 1, 'from: Longevity essay', 'mine', true],
    ['prevalence', 'noun', 'how common something is in a group', 0, 'Collins Unit 7', 'collins', true],
    ['mitigate', 'verb', 'to make something harmful less severe', 2, 'from: Academic talk: Biology', 'mine', true],
    ['chronic', 'adjective', 'lasting a long time or often returning', 2, 'Collins Unit 7', 'collins', true],
    ['resilient', 'adjective', 'able to recover quickly after difficulty', 1, 'Collins Unit 7', 'collins', true],
    ['deteriorate', 'verb', 'to become worse over time', 2, 'from: Daily life: Campus Gym Email', 'mine', true],
    ['sustain', 'verb', 'to keep something going over time', 3, 'Collins Unit 7', 'collins', true],
    ['intake', 'noun', 'the amount of something you take into your body', 1, 'from: Longevity essay', 'mine', true],
    ['inhibit', 'verb', 'to slow down or prevent a process', 0, 'Collins Unit 7', 'collins', true],
    ['habitat', 'noun', 'the natural home of a plant or animal', 4, 'Collins Unit 6', 'collins', false],
    ['ecosystem', 'noun', 'all the living things in an area and how they connect', 4, 'Collins Unit 6', 'collins', false],
    ['contribute', 'verb', 'to help cause or produce a result', 3, 'added by you', 'mine', false],
    ['vital', 'adjective', 'absolutely necessary for life or success', 4, 'Collins Unit 5', 'collins', false],
  ];
  const UNITS = [['Unit 5 · Education', 20, 20, ''], ['Unit 6 · Environment', 20, 14, ''], ['Unit 7 · Life Science', 20, 8, 'now'], ['Unit 8 · Technology', 20, 0, 'next']];
  const rowHtml = ([w, pos, def, st, src, , due]) => `<div class="rv-vrow" role="listitem"><div class="wd"><span class="t-title-3">${w}</span><span class="pos">${pos}</span></div>
    <div class="def-col"><div class="def">${def}</div><div class="src">${src}</div></div>
    <div class="fsrs"><span class="rv-fs" role="img" aria-label="Memory stability ${st} of 4">${[1, 2, 3, 4].map((i) => `<i class="${i <= st ? 'on' : ''}"></i>`).join('')}</span><span>${due ? 'Due today' : st === 4 ? 'Learned' : 'Not due'}</span></div></div>`;

  P.register('/vocabulary', {
    back: { label: 'Practice', href: '/practice', web: false },
    title: 'Vocabulary', num: 'plan', nav: 'vocabulary', tab: 'practice', web: true, ios: true,
    tutorContext: 'Vocabulary', tutorQuestion: 'Quiz me on Week 7 words',
    render() {
      return `<div class="page">
        <div class="rv-head"><div><h1 class="t-title-1">Vocabulary</h1><div class="sub">86 words learned · 12 due today</div></div>${P.btn('Review 12 words', { href: '/vocabulary/review', icon: 'layers' })}</div>
        <div class="seg" role="tablist" aria-label="View" style="margin-bottom:16px"><button role="tab" class="on" data-tab="words" aria-selected="true">Words</button><button role="tab" data-tab="units" aria-selected="false">Collins units</button></div>
        <div data-pane="words">
          <div class="rv-vtools"><label class="rv-search">${P.i('search')}<input class="input" type="search" placeholder="Search words" aria-label="Search words" id="vq"></label>
            <div class="row gap-2 wrap" role="group" aria-label="Filter">${[['due', 'Due today (12)'], ['collins', 'Collins units'], ['mine', 'My words'], ['learned', 'Learned']].map(([k, l]) => `<button type="button" class="chip" data-chip="${k}" aria-pressed="false">${l}</button>`).join('')}</div></div>
          <div class="rv-vlist" role="list" id="vlist"></div>
          <div class="t-caption muted mt-3">Dots show how stable a word is in your memory (FSRS): more dots, longer until the next review.</div>
        </div>
        <div data-pane="units" hidden><div class="rv-units">${UNITS.map(([n, t, l, st]) => `<button type="button" class="rv-unit ${st === 'now' ? 'now' : ''}" data-unit="${n}"><div class="row between"><span class="n">${n}</span>${st === 'now' ? P.tag('accent', 'This week') : st === 'next' ? P.tag('neutral', 'Upcoming') : ''}</div>
          <div class="muted">${t} words · ${l} learned</div>${P.bar(Math.round((l / t) * 100), { pct: false, thin: true, label: n })}</button>`).join('')}</div></div>
      </div>`;
    },
    mount(root) {
      const offs = [];
      let chip = null, q = '';
      const list = $(root, '#vlist');
      const draw = () => {
        const rows = WORDS.filter((r) => (!q || (r[0] + ' ' + r[2]).toLowerCase().includes(q)) && (!chip || (chip === 'due' ? r[6] : chip === 'learned' ? r[3] === 4 : r[5] === chip)));
        list.innerHTML = rows.length ? rows.map(rowHtml).join('') : P.empty({ title: 'No words found', text: q ? `Nothing matches "${P.esc(q)}".` : 'Nothing here yet.' });
        $$(root, '[data-chip]').forEach((b) => { const o = b.dataset.chip === chip; b.classList.toggle('on', o); b.setAttribute('aria-pressed', o); });
        icons();
      };
      draw();
      offs.push(on($(root, '#vq'), 'input', (e) => { q = e.target.value.trim().toLowerCase(); draw(); }));
      offs.push(on(root, 'click', (e) => {
        const c = e.target.closest('[data-chip]');
        if (c) { chip = chip === c.dataset.chip ? null : c.dataset.chip; draw(); return; }
        const t = e.target.closest('[data-tab]');
        if (t) { $$(root, '[data-tab]').forEach((b) => { const o = b === t; b.classList.toggle('on', o); b.setAttribute('aria-selected', o); }); $$(root, '[data-pane]').forEach((p) => { p.hidden = p.dataset.pane !== t.dataset.tab; }); return; }
        const u = e.target.closest('[data-unit]');
        if (u) {
          chip = 'collins'; $$(root, '[data-tab]').forEach((b) => { const o = b.dataset.tab === 'words'; b.classList.toggle('on', o); b.setAttribute('aria-selected', o); });
          $$(root, '[data-pane]').forEach((p) => { p.hidden = p.dataset.pane !== 'words'; }); draw();
        }
      }));
      return () => offs.forEach((f) => f());
    },
  });

  /* ═════════════ 21 · Vocabulary review (FSRS) ═════════════ */
  const CARDS = [
    { w: 'mitigate', ex: 'Planting trees along the river helps to ___ the damage caused by floods.', def: 'to make something harmful less severe', tr: 'смягчать, уменьшать', ipa: '/ˈmɪtɪɡeɪt/' },
    { w: 'sustain', ex: 'A balanced diet can ___ your energy through a long day of study.', def: 'to keep something going over time', tr: 'поддерживать', ipa: '/səˈsteɪn/' },
    { w: 'chronic', ex: 'He has a ___ back problem, so he stretches every morning.', def: 'lasting a long time or often returning', tr: 'хронический', ipa: '/ˈkrɒnɪk/' },
    { w: 'resilient', ex: 'Young trees are surprisingly ___ and often recover after a storm.', def: 'able to recover quickly after difficulty', tr: 'устойчивый, жизнестойкий', ipa: '/rɪˈzɪliənt/' },
    { w: 'deteriorate', ex: 'Without regular care, the old laboratory equipment will ___ quickly.', def: 'to become worse over time', tr: 'ухудшаться', ipa: '/dɪˈtɪəriəreɪt/' },
    { w: 'sedentary', ex: 'A ___ lifestyle, with many hours at a desk, can affect your health.', def: 'involving a lot of sitting and little physical activity', tr: 'малоподвижный', ipa: '/ˈsednt(ə)ri/' },
  ];
  const GRADES = [['again', 'Again', '10m'], ['hard', 'Hard', '1d'], ['good', 'Good', '3d'], ['easy', 'Easy', '8d']];

  P.register('/vocabulary/review', {
    back: { label: 'Vocabulary', href: '/vocabulary' },
    title: 'Vocabulary review (FSRS)', num: 'plan', nav: 'vocabulary', tab: 'practice', web: true, ios: true,
    tutorContext: 'Vocabulary review', tutorQuestion: 'Explain this word',
    render(ctx) {
      return `<div class="page">
        <div class="rv-fs-page" id="fs"></div></div>`;
    },
    mount(root, ctx) {
      const offs = [], timers = [];
      const host = $(root, '#fs');
      const START = 7, TOTAL = 12;
      let n = START, shown = false;
      const card = () => CARDS[n - START];
      function draw() {
        if (n > TOTAL) {
          host.innerHTML = `<div class="rv-fc"><div class="rv-done">${P.i('circle-check', 'ic-64')}<div class="t-title-2">${TOTAL} reviewed · next in 6 h</div><div class="muted">Nice work. Words you rated Again will come back first.</div>${P.btn('Back to Today', { href: '/today', icon: 'sun' })}</div></div>`;
          icons();
          return;
        }
        const c = card();
        const pct = Math.round(((n - 1) / TOTAL) * 100);
        const sentence = shown ? c.ex.replace('___', `<span class="filled">${c.w}</span>`) : c.ex.replace('___', '<span class="rv-gap" aria-label="gap"></span>');
        host.innerHTML = `<div class="rv-fs-top">${P.btn('Close', { v: 'icon', icon: 'x', size: 'sm', attrs: 'data-close' })}<div class="grow">${P.bar(pct, { pct: false, thin: true, label: 'Session progress' })}</div><span class="pr">${n} of ${TOTAL}</span></div>
          <div class="rv-fc" id="fc"><div class="t-overline">${shown ? 'Answer' : 'Fill the gap'}</div><div class="word">${c.w}</div><div class="ex">${sentence}</div>
            ${shown ? `<div class="back"><div class="def">${c.def}</div><div class="tr" lang="ru">${c.tr}</div><div class="pron"><span>${c.ipa}</span>${P.btn('Pronunciation', { v: 'icon', icon: 'volume-2', size: 'sm', attrs: 'data-say' })}</div></div>` : ''}
            ${shown && ctx.ios ? '<div class="hint">Swipe right for Good, left for Again</div>' : ''}</div>
          ${shown ? `<div class="rv-grades" role="group" aria-label="How well did you remember?">${GRADES.map(([k, l, iv], i) => `<button type="button" class="rv-grade ${k}" data-grade="${i + 1}">${l}<small>${iv}</small>${ctx.ios ? '' : `<span class="kbd">${i + 1}</span>`}</button>`).join('')}</div>`
            : `<div class="rv-grades" style="grid-template-columns:1fr">${P.btn('Show answer', { size: 'lg', block: true, attrs: 'data-show' })}${ctx.ios ? '' : '<div class="t-caption muted center mt-2">Press <span class="kbd">Space</span></div>'}</div>`}`;
        icons();
        if (shown && ctx.ios) bindSwipe();
      }
      const grade = (g) => { if (!shown) return; n++; shown = false; draw(); };
      const reveal = () => { if (shown || n > TOTAL) return; shown = true; draw(); };
      function bindSwipe() {
        const fc = $(host, '#fc');
        let x0 = null, dx = 0;
        fc.addEventListener('pointerdown', (e) => { x0 = e.clientX; dx = 0; fc.setPointerCapture(e.pointerId); fc.style.transition = 'none'; });
        fc.addEventListener('pointermove', (e) => { if (x0 === null) return; dx = e.clientX - x0; fc.style.transform = `translateX(${dx}px) rotate(${dx / 25}deg)`; fc.style.opacity = String(1 - Math.min(Math.abs(dx) / 400, 0.4)); });
        const end = () => {
          if (x0 === null) return;
          x0 = null;
          if (Math.abs(dx) > 90) {
            fc.style.transition = 'transform .2s, opacity .2s'; fc.style.transform = `translateX(${dx > 0 ? 500 : -500}px)`; fc.style.opacity = '0';
            timers.push(setTimeout(() => grade(dx > 0 ? 3 : 1), 180));
          } else { fc.style.transition = 'transform .2s'; fc.style.transform = ''; fc.style.opacity = ''; }
        };
        fc.addEventListener('pointerup', end); fc.addEventListener('pointercancel', end);
      }
      offs.push(on(host, 'click', (e) => {
        if (e.target.closest('[data-show]')) reveal();
        const g = e.target.closest('[data-grade]'); if (g) grade(+g.dataset.grade);
        if (e.target.closest('[data-close]')) P.go('/today');
        if (e.target.closest('[data-say]')) P.toast('Playing pronunciation', 'volume-2');
      }));
      offs.push(on(document, 'keydown', (e) => {
        if (e.target.matches && e.target.matches('input, textarea')) return;
        if (document.getElementById('ov') && !document.getElementById('ov').hidden) return;
        if (e.code === 'Space') { if (!shown && n <= TOTAL) { e.preventDefault(); reveal(); } }
        else if (/^[1-4]$/.test(e.key)) grade(+e.key);
      }));
      draw();
      return () => { offs.forEach((f) => f()); timers.forEach(clearTimeout); };
    },
  });
})();
