/* Student screens: Today, Weeks, Week page, Section page, AI tutor chat, Practice hub, Profile,
   Settings, Notifications, Grammar, Mock exams. */
(function () {
  const P = window.P;
  const ORDER = ['READING', 'LISTENING', 'WRITING', 'SPEAKING', 'VOCABULARY', 'GRAMMAR', 'RESOURCES'];
  const DAY_NAMES = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

  const taskHref = (t) => t.href || '';
  const taskMeta = (t) => {
    if (t.type === 'LISTEN_ACADEMIC_TALK') return `${t.questions} questions`;
    if (t.type === 'TAKE_AN_INTERVIEW') return '4 × 45 s';
    if (t.kind === 'VOCABULARY_REVIEW') return 'FSRS';
    if (t.type === 'COMPLETE_THE_WORDS') return '10 gaps · 3 min';
    return t.minutes ? `${t.minutes} min` : '';
  };
  const statusCheck = (t) => (t.status === 'DONE' ? P.check(true, { round: true, label: 'Done' }) : t.status === 'IN_PROGRESS' ? P.check(false, { round: true, half: true, label: 'In progress' }) : P.check(false, { round: true, label: 'Not started' }));
  const tagOpts = (t, w, extra = {}) => Object.assign({
    cell: true, done: t.status === 'DONE', dot: true, title: w.days[t.day],
    mod: t.kind === 'TUTOR_MATERIAL' ? 'tutor' : t.kind === 'EXTERNAL_RESOURCE' ? 'external' : '',
    href: taskHref(t) || undefined,
  }, extra);

  /* TaskRow: section icon, title, section tag, time, status */
  function taskRow(t, ctx, o = {}) {
    const s = P.sec(t.section);
    const href = taskHref(t);
    const inner = `${o.icon === false ? '' : P.sectionIcon(t.section)}<div class="grow"><div class="t-strong tr-title">${t.title}</div><div class="t-caption muted">${s.label} · ${taskMeta(t)}${t.score ? ' · ' + t.score : ''}</div></div>
      ${ctx.ios ? '' : P.tag(t.section, s.label)}${statusCheck(t)}`;
    return href ? `<a class="task-row" href="#${href}">${inner}</a>` : `<div class="task-row">${inner}</div>`;
  }

  /* ───────── Today ───────── */
  P.register('/today', {
    title: 'Today', num: 5, nav: 'today', tab: 'today',
    tutorQuestion: 'What should I focus on this week?',
    render: (ctx) => {
      const s = P.fx.student, w = P.fx.week7;
      const today = w.tasks.filter((t) => ['r3', 'l3', 's2', 'v2'].includes(t.id));
      const goal = P.ring({ value: s.minutesToday, max: s.minutesGoal, size: 48, center: '', label: `Goal ${s.minutesToday} of ${s.minutesGoal} minutes` });
      const waiting = [
        ['pen-line', 'Waiting for you: Attempt 2 of "Longevity"', 'Your Draft scored 3/5. Fix 4 comments and resubmit.', '/review/writing'],
        ['message-square', 'From your tutor: comment on Email draft', 'Anna K. · 2 h ago', '/review/writing'],
        ['clipboard-check', 'Mock exam next week', 'Week 8 · about 1 h 30 min', '/exams'],
      ];
      return `<div class="page today">
        ${ctx.ios ? `<div class="ios-toolbar"><a class="circle-btn glass" href="#/notifications" aria-label="Notifications">${P.i('bell')}</a></div>` : ''}
        <div class="t-caption muted">${s.todayCaption}</div>
        <h1 class="t-title-1">Today</h1>
        <div class="today-top mt-5">
          <div class="glass glass-card band-card">${P.bandRing(s.band, ctx.ios ? 74 : 96)}${P.bandText(s.band, { small: ctx.ios, extra: ctx.ios ? `<span class="t-caption muted">${s.streak}-day streak</span>` : '' })}</div>
          ${ctx.ios ? '' : `<div class="card lg streak-card"><div class="row gap-3">${P.i('flame', 'ic-20 muted')}<div><div class="t-title-3">${s.streak} days</div><div class="t-caption muted">Streak</div></div></div>
            <div class="row gap-3 mt-4">${goal}<div><div class="t-strong">${s.minutesToday} / ${s.minutesGoal} min</div><div class="t-caption muted">Goal today</div></div></div></div>`}
        </div>
        <div class="row between mt-8"><h2 class="t-title-3">Today's tasks · about 18 min</h2>${ctx.ios ? '' : P.btn('Start', { href: '/tasks/ctw', iconRight: 'chevron-right' })}</div>
        <div class="task-list mt-3">${today.map((t) => taskRow(t, ctx)).join('')}</div>
        ${ctx.ios ? `<div class="mt-4">${P.btn('Start', { block: true, size: 'lg', href: '/tasks/ctw' })}</div>` : ''}
        <div class="stack gap-3 mt-8">
          ${waiting.map(([ic, t, sub, href]) => `<a class="card click row gap-3" href="#${href}">${P.iconBox(ic)}<div class="grow"><div class="t-strong">${t}</div><div class="t-caption muted">${sub}</div></div>${P.i('chevron-right', 'muted')}</a>`).join('')}
          ${P.banner('info', 'info', 'You missed Sunday. We moved two tasks to Thursday.')}
        </div></div>`;
    },
  });

  /* ───────── Weeks gallery ───────── */
  function weekCard(w) {
    const cur = w.status === 'IN_PROGRESS', done = w.status === 'COMPLETED', waiting = w.status === 'AWAITING_TUTOR_APPROVAL';
    const badge = cur ? P.tag('accent', 'Current') : done ? P.tag('accent', 'Done', { done: true }) : w.exam ? P.tag('neutral', 'Mock exam', { mod: 'exam' }) : P.tag('neutral', 'Upcoming');
    const href = cur ? '/weeks/7' : done && w.exam ? '/exams/1/result' : '';
    const sub = waiting ? 'Your tutor is reviewing this week' : w.dates;
    return `<${href ? `a href="#${href}"` : 'div data-week="' + w.n + '"'} class="week-card">${P.cover(P.weekCoverId(w.n), 'week-card')}
      <span class="wc-badge">${badge}</span>
      <div class="wc-body"><div class="row between"><span class="t-title-3">Week ${w.n}</span>${w.exam && (cur || done) ? P.tag('neutral', 'Mock exam', { mod: 'exam' }) : ''}</div>
      <div class="t-caption muted mt-1">${sub}</div><div class="mt-3">${P.bar(w.progress, { thin: true, label: 'Week progress' })}</div></div></${href ? 'a' : 'div'}>`;
  }
  P.register('/weeks', {
    title: 'Weeks gallery', num: 6, nav: 'weeks', tab: 'weeks',
    render: (ctx) => {
      const ws = P.fx.weeks.weeks;
      const extra = ctx.query.tab === 'extra';
      const cur = ws.filter((w) => w.status === 'IN_PROGRESS'), next = ws.filter((w) => !['IN_PROGRESS', 'COMPLETED'].includes(w.status)), past = ws.filter((w) => w.status === 'COMPLETED').reverse();
      const body = extra
        ? `${P.banner('info', 'archive', 'Old TOEFL format (2020). Useful extra practice for long passages and lectures. Not counted in your band estimate.')}
           <div class="card mt-4" style="padding:0"><div class="list">${[['READING', 'The Origins of Agriculture', 'Prose summary · 10 questions'], ['READING', 'Desert Formation', 'Table · 10 questions'], ['LISTENING', 'Lecture: Art History, Fresco Painting', '6 questions'], ['LISTENING', 'Lecture: Astronomy, Red Giants', '6 questions']]
              .map(([s, t, m]) => `<div class="list-row">${P.sectionIcon(s)}<div class="grow"><div class="t-strong">${t}</div><div class="t-caption muted">${m}</div></div>${P.tag(s, P.sec(s).label, { mod: 'legacy' })}</div>`).join('')}</div></div>`
        : `<div class="week-grid mt-2">${cur.concat(next).map(weekCard).join('')}</div>
           <details class="mt-8 completed-weeks"><summary class="row gap-2 t-title-3">${P.i('chevron-right', 'chev')}Completed weeks <span class="muted t-body">(${past.length})</span></summary>
           <div class="week-grid mt-4">${past.map(weekCard).join('')}</div></details>`;
      return `<div class="page">
        <div class="row between wrap gap-3"><div><h1 class="t-title-1">Weeks</h1><div class="muted mt-1">${P.fx.student.planCaption}</div></div>${ctx.ios ? '' : P.btn('Change exam date', { v: 'ghost', href: '/settings?tab=goal' })}</div>
        <div class="seg mt-5" role="tablist"><a role="tab" href="#/weeks" class="${extra ? '' : 'on'}">Weeks</a><a role="tab" href="#/weeks?tab=extra" class="${extra ? 'on' : ''}">Extra practice</a></div>
        <div class="mt-5">${body}</div></div>`;
    },
    mount(root) {
      root.querySelectorAll('[data-week]').forEach((c) => c.addEventListener('click', () => {
        const n = Number(c.dataset.week);
        P.toast(n === 8 ? 'Your tutor is reviewing Week 8. It will be ready by Mon, 10:00.' : `Week ${n} opens on its start date.`, 'info');
      }));
    },
  });

  /* ───────── Week page ───────── */
  function weekTable(w) {
    const head = ORDER.map((s) => `<th>${P.sec(s).label}</th>`).join('');
    const cells = ORDER.map((s) => `<td><div class="cell-tags">${w.tasks.filter((t) => t.section === s).sort((a, b) => a.day - b.day)
      .map((t) => (t.kind === 'EXTERNAL_RESOURCE'
        ? `<span class="ext">${P.check(t.status === 'DONE', { attrs: 'data-toggle', label: `Mark ${t.title} as done` })}${P.tag(s, t.title, tagOpts(t, w, { done: false, href: undefined }))}</span>`
        : P.tag(s, t.title, tagOpts(t, w)))).join('')}</div></td>`).join('');
    return `<div class="week-table-wrap"><table class="table week-table"><thead><tr><th>#</th>${head}</tr></thead><tbody><tr><td class="t-strong">${w.number}</td>${cells}</tr></tbody></table></div>`;
  }
  function weekGroups(w) {
    return `<div class="stack gap-4">${ORDER.map((s) => `<div class="wk-group"><div class="row gap-2">${P.tag(s, P.sec(s).label)}</div>
      <div class="cell-tags mt-2">${w.tasks.filter((t) => t.section === s).map((t) => P.tag(s, t.title, tagOpts(t, w))).join('')}</div></div>`).join('')}</div>`;
  }
  function sectionCards(w) {
    return ORDER.filter((s) => s !== 'RESOURCES').map((s) => {
      const ts = w.tasks.filter((t) => t.section === s);
      const done = ts.filter((t) => t.status === 'DONE').length;
      const manual = s === 'VOCABULARY' || s === 'GRAMMAR';
      const sub = manual ? w.units[s].split(':')[0] : `${done} of ${ts.length} done`;
      return `<a class="section-card" href="#/weeks/7/${P.sec(s).key}">${P.cover('section-' + P.sec(s).key, 'section')}
        <div class="sc-body">${P.check(done === ts.length, { attrs: manual ? 'data-toggle onclick="event.preventDefault()"' : 'disabled tabindex="-1"', label: `${P.sec(s).label} done` })}
        <div class="sc-ic">${P.i(P.sec(s).icon)}</div><div class="t-title-3" style="font-size:16px">${P.sec(s).label}</div><div class="t-caption muted">${sub}</div></div></a>`;
    }).join('');
  }
  P.register('/weeks/7', {
    back: { label: 'Weeks', href: '/weeks', ios: false },
    title: 'Week page: Week 7', num: 7, nav: 'weeks', tab: 'weeks',
    tutorContext: 'Week 7', tutorQuestion: 'What should I focus on this week?',
    render: (ctx) => {
      const w = P.fx.week7;
      const today = `<div class="glass today-strip"><div class="grow"><div class="t-strong">Today: ${w.today.title}</div><div class="t-caption muted">${P.sec(w.today.section).label} · about ${w.today.minutes} min · 4 tasks today</div></div>${P.btn('Start', { href: w.today.href })}</div>`;
      const props = ctx.ios
        ? `<div class="ios-props card mt-4" style="padding:0">
            <div class="list-row"><span class="muted">Date</span><span class="grow" style="text-align:right">${w.dates}</span></div>
            <div class="list-row"><span class="muted">Total progress</span><div class="grow">${P.bar(w.progress, { label: 'Total progress' })}</div></div>
            <div class="list-row"><span class="muted">Tutor</span><span class="grow" style="text-align:right">${w.tutor}</span></div></div>`
        : `<dl class="props mt-3"><dt>Date</dt><dd>${w.dates}</dd><dt>Total progress</dt><dd style="max-width:320px">${P.bar(w.progress, { label: 'Total progress' })}</dd><dt>Tutor</dt><dd class="row gap-2">${P.avatar('AK', 'xs')}${w.tutor}</dd></dl>`;
      return `<div class="page week-page ${ctx.ios ? 'under-cover' : ''}">
        ${ctx.ios ? `<div class="ios-cover">${P.cover(w.coverId, 'week')}<a class="circle-btn glass ios-cover-back" href="#/weeks" aria-label="Weeks">${P.i('chevron-left')}</a></div>` : P.cover(w.coverId, 'week')}
        <div class="row between mt-5 wrap gap-3"><h1 class="t-title-1">Week ${w.number}</h1>
          ${ctx.ios ? '' : `<div class="row gap-2">${P.btn('Ask tutor about this week', { v: 'secondary', icon: 'sparkles', attrs: 'data-action="tutor" data-context="Week 7"' })}${P.btn('More', { v: 'icon', icon: 'ellipsis' })}</div>`}</div>
        ${props}
        <div class="mt-6">${today}</div>
        <div class="mt-8">${P.banner('info', 'refresh-cw', `We rebuilt the rest of this week so you can catch up. <details class="inline-details"><summary class="link">See what changed</summary><ul class="mt-2"><li>"Conversation: Library Hours" moved from Tuesday to Friday</li><li>"Collins Grammar Unit 7" moved from Wednesday to Thursday</li></ul></details>`)}</div>
        <h2 class="t-title-3 mt-6">${ctx.ios ? 'This week' : 'Week table'}</h2>
        <div class="mt-3">${ctx.ios ? weekGroups(w) : weekTable(w)}</div>
        <h2 class="t-title-3 mt-8">Sections</h2>
        <div class="section-grid mt-3">${sectionCards(w)}</div>
        <a class="card click row gap-3 mt-8" href="#/weeks/7/review">${P.iconBox('notebook-pen')}<div class="grow"><div class="t-strong">Weekly review</div><div class="t-caption muted">Available on ${w.reviewAvailable} · open a preview</div></div>${P.i('chevron-right', 'muted')}</a>
      </div>`;
    },
  });

  /* ───────── Section pages ───────── */
  ORDER.forEach((S) => {
    const key = P.sec(S).key;
    P.register('/weeks/7/' + key, {
    back: { label: 'Week 7', href: '/weeks/7' },
      title: `Section page: ${P.sec(S).label}`, num: S === 'WRITING' ? 8 : 'plan', hidden: S !== 'WRITING', nav: 'weeks', tab: 'weeks',
      render: (ctx) => {
        const w = P.fx.week7;
        const ts = w.tasks.filter((t) => t.section === S);
        const done = ts.filter((t) => t.status === 'DONE').length;
        const days = [...new Set(ts.map((t) => t.day))].sort();
        const unit = w.units[S] ? `<a class="card click row gap-3 mt-5" href="#${S === 'GRAMMAR' ? '/grammar' : '/vocabulary'}">${P.sectionIcon(S)}<div class="grow"><div class="t-strong">${w.units[S]}</div><div class="t-caption muted">${S === 'GRAMMAR' ? 'Rule · Book exercises · Practice · Quiz' : '20 words · 8 learned'}</div></div>${P.i('chevron-right', 'muted')}</a>` : '';
        const row = (t) => {
          if (t.kind === 'TUTOR_MATERIAL') return `<div class="task-row">${P.iconBox('file-text')}<div class="grow"><div class="t-strong tr-title">${t.title}</div><div class="t-caption muted">From Anna K. · ${t.pages || 'pp. 14–17'}</div></div>
            ${ctx.ios ? '' : `${P.btn('Open pages', { v: 'ghost', size: 'sm' })}${t.status === 'DONE' ? '' : P.btn('Mark as done', { v: 'secondary', size: 'sm', attrs: 'data-action="toast" data-text="Marked as done"' })}`}${statusCheck(t)}</div>`;
          const type = t.type ? P.tag(S, P.TASK_TYPES[t.type]) : P.tag(S, t.kind === 'EXTERNAL_RESOURCE' ? 'Link' : P.sec(S).label, { mod: t.kind === 'EXTERNAL_RESOURCE' ? 'external' : '' });
          const href = taskHref(t);
          const inner = `${P.sectionIcon(S)}<div class="grow"><div class="t-strong tr-title">${t.title}</div><div class="row gap-2 wrap mt-1">${type}<span class="t-caption muted">${taskMeta(t)}${t.score ? ' · Last score ' + t.score : ''}</span></div></div>${statusCheck(t)}`;
          return href ? `<a class="task-row" href="#${href}">${inner}</a>` : `<div class="task-row">${inner}</div>`;
        };
        return `<div class="page narrow">
          ${P.cover(S === 'RESOURCES' ? w.coverId : 'section-' + key, 'section-header')}
          <div class="row between mt-5"><h1 class="t-title-1">${P.sec(S).label} · Week 7</h1>${P.check(done === ts.length, { label: 'Section done', attrs: S === 'VOCABULARY' || S === 'GRAMMAR' || S === 'RESOURCES' ? 'data-toggle' : 'disabled' })}</div>
          <div class="row gap-3 mt-2"><span class="muted">${done} of ${ts.length} done</span><div style="width:200px">${P.bar(Math.round((done / ts.length) * 100), { pct: false, label: 'Section progress' })}</div></div>
          ${unit}
          ${days.map((d) => `<div class="t-overline mt-8">${DAY_NAMES[d]} · ${w.days[d].split(', ')[1]}</div><div class="task-list mt-2">${ts.filter((t) => t.day === d).map(row).join('')}</div>`).join('')}
        </div>`;
      },
    });
  });

  /* ───────── AI tutor chat, full screen ───────── */
  P.register('/ai-tutor', {
    title: 'AI tutor chat', num: 20, nav: 'ai-tutor', tab: 'profile', fab: false,
    render: (ctx) => {
      const threads = [['Week 7 plan', 'Today'], ['Why is B wrong? · Biology talk', 'Yesterday'], ['Conditionals', 'Oct 9'], ['Email: Missing a Meeting', 'Oct 8']];
      return `<div class="chat-page">
        ${ctx.ios ? '' : `<aside class="threads"><div class="p-3">${P.btn('New chat', { v: 'secondary', icon: 'plus', block: true })}</div>
          <div class="list">${threads.map(([t, d], i) => `<a class="list-row click ${i === 0 ? 'on' : ''}" href="#/ai-tutor"><div class="grow"><div class="t-strong tr-title">${t}</div><div class="t-caption muted">${d}</div></div></a>`).join('')}</div></aside>`}
        <section class="chat-main">
          <header class="chat-head">${ctx.ios ? `<a class="ios-back" href="#/profile">${P.i('chevron-left')}Profile</a>` : ''}<div class="row gap-2">${P.i('sparkles', 'ic-18 accent-ic')}<span class="t-title-3">Toefly Tutor</span></div>
            <label class="row gap-2 t-caption muted">Explain in Russian<button class="switch" data-toggle aria-label="Explain in Russian"></button></label></header>
          <div class="chat-scroll" id="cs"><div class="chat-log chat-col">
            <div class="bubble-me">What should I focus on this week?</div>
            <div class="tool-line">${P.i('calendar-days')}Opened Week 7</div>
            <div class="tool-line">${P.i('eye')}Looked at your last 3 essays</div>
            <div class="bubble-ai"><div class="who">${P.i('sparkles')}Toefly Tutor</div><div class="ans" id="ans"></div>
              <div class="chat-actions" id="acts" hidden>${P.btn('Copy', { v: 'icon', icon: 'copy', size: 'sm' })}${P.btn('Helpful', { v: 'icon', icon: 'thumbs-up', size: 'sm' })}${P.btn('Not helpful', { v: 'icon', icon: 'thumbs-down', size: 'sm' })}${P.btn('Explain in Russian', { v: 'ghost', size: 'sm', icon: 'languages' })}</div></div>
          </div></div>
          <div class="chat-foot chat-col"><div class="row gap-2 wrap">${['What should I focus on this week?', 'Quiz me on Week 7 words', 'Explain my last Writing score'].map((c) => `<button class="chip">${c}</button>`).join('')}</div>
            <div class="t-caption muted mt-2">3 messages left today</div>
            <div class="chat-input mt-2"><textarea rows="1" placeholder="Message Toefly Tutor" aria-label="Message"></textarea><span class="gen-btn">${P.btn('Stop', { v: 'secondary', icon: 'square', size: 'sm' })}</span></div></div>
        </section></div>`;
    },
    mount(root) {
      const ans = "<p>Two things will move your band the most this week:</p><ul><li><b>Build a Sentence.</b> In 4 of your last 10 sentences <code>not</code> was in the wrong place. Do <b>Practice 6</b> on Wednesday.</li><li><b>Attempt 2 of Longevity.</b> Your Draft had 87 words; aim for 110 and fix the two comments on verb agreement.</li></ul><p>Your Listening is on track: 4.5 and rising.</p>";
      const gen = root.querySelector('.gen-btn');
      return P.stream(root.querySelector('#ans'), ans, () => {
        root.querySelector('#acts').hidden = false;
        gen.innerHTML = P.btn('Send', { icon: 'send', size: 'sm' });
        window.lucide.createIcons();
      });
    },
  });

  /* ───────── Practice hub (iOS tab, mobile web /practice) ───────── */
  const hubRow = (icon, t, sub, href) => `<a class="list-row click" href="#${href}"><span class="icon-box">${P.i(icon)}</span><div class="grow"><div class="t-strong">${t}</div><div class="t-caption muted">${sub}</div></div>${P.i('chevron-right', 'muted')}</a>`;
  P.register('/practice', {
    title: 'Practice hub', num: 'plan', nav: 'exams', tab: 'practice',
    render: () => `<div class="page narrow"><h1 class="t-title-1">Practice</h1>
      <div class="card mt-5" style="padding:0"><div class="list">
        ${hubRow('clipboard-check', 'Mock exams', 'Next: Week 8 · Mock exam 2', '/exams')}
        ${hubRow('book-a', 'Vocabulary review', '12 words due today', '/vocabulary')}
        ${hubRow('text', 'Grammar units', 'Round-Up Unit 7 in this week', '/grammar')}
        ${hubRow('archive', 'Legacy practice', 'Old TOEFL format, not counted in your band', '/weeks?tab=extra')}</div></div>
      <div class="t-overline mt-8">Practice by task type</div>
      <div class="card mt-2" style="padding:0"><div class="list">
        ${[['READING', 'Complete the Words', '/tasks/ctw'], ['LISTENING', 'Listen to an Academic Talk', '/tasks/listen'], ['WRITING', 'Build a Sentence', '/tasks/build'], ['SPEAKING', 'Listen and Repeat', '/tasks/repeat'], ['SPEAKING', 'Take an Interview', '/tasks/interview']]
          .map(([s, t, h]) => `<a class="list-row click" href="#${h}">${P.sectionIcon(s)}<div class="grow t-strong">${t}</div>${P.tag(s, P.sec(s).label)}</a>`).join('')}</div></div></div>`,
  });

  /* ───────── Profile (iOS tab) ───────── */
  P.register('/profile', {
    title: 'Profile', num: 'plan', nav: 'settings', tab: 'profile',
    render: () => {
      const s = P.fx.student;
      return `<div class="page narrow"><h1 class="t-title-1">Profile</h1>
        <div class="card lg mt-5 row gap-4">${P.avatar(s.initials, 'lg')}<div class="grow"><div class="t-title-3">${s.name}</div><div class="t-caption muted">Goal ${s.goal.toFixed(1)} · exam on ${s.examDate}</div></div></div>
        <div class="card mt-4" style="padding:0"><div class="list">
          ${hubRow('sparkles', 'Chats', 'Toefly Tutor', '/ai-tutor')}
          ${hubRow('bell', 'Notifications', `${s.unreadNotifications} unread`, '/notifications')}
          ${hubRow('target', 'Goal and schedule', '30 min a day · 7 days', '/settings?tab=goal')}
          ${hubRow('settings', 'Settings', 'Appearance, learning, privacy', '/settings')}
          ${hubRow('credit-card', 'Billing', s.plan, '/billing')}
          ${hubRow('users', 'Tutor workspace', 'For tutors: students, plans, materials', '/tutor/students')}</div></div>
        <div class="mt-6">${P.btn('Sign out', { v: 'ghost', icon: 'log-out', href: '/sign-in' })}</div></div>`;
    },
  });

  /* ───────── Settings; Appearance drives the prototype's own theme switches ───────── */
  const TABS = [['profile', 'Profile', 'circle-user'], ['goal', 'Goal', 'target'], ['schedule', 'Schedule', 'calendar-days'], ['notifications', 'Notifications', 'bell'],
    ['learning', 'Learning', 'graduation-cap'], ['appearance', 'Appearance', 'sun-moon'], ['privacy', 'Privacy', 'shield-check'], ['account', 'Account', 'key-round']];
  const toggleRow = (t, sub, on, attrs = '') => `<div class="list-row"><div class="grow"><div class="t-strong">${t}</div>${sub ? `<div class="t-caption muted">${sub}</div>` : ''}</div><button class="switch ${on ? 'on' : ''}" ${attrs || 'data-toggle'} aria-label="${t}"></button></div>`;
  P.register('/settings', {
    back: { label: 'Profile', href: '/profile', web: false },
    title: 'Settings', num: 'plan', nav: 'settings', tab: 'profile',
    render: (ctx) => {
      const tab = ctx.query.tab || 'appearance';
      const s = P.fx.student;
      const forms = {
        appearance: `<div class="field"><label>Theme</label><div class="seg" id="themeSeg">${['System', 'Light', 'Dark'].map((t) => `<button data-t="${t.toLowerCase()}" class="${P.state.theme === t.toLowerCase() ? 'on' : ''}">${t}</button>`).join('')}</div></div>
          <div class="card mt-5" style="padding:0">${toggleRow('Reduce transparency', 'Glass panels become solid', P.state.reduced, 'id="rtSwitch"')}${toggleRow('Keyboard shortcuts', 'A–D, Enter = Next, Space = play', true)}</div>
          <div class="field mt-5"><label>Task text size</label><div class="seg">${['Default', 'Large', 'Extra large'].map((t, i) => `<button class="${i === 0 ? 'on' : ''}">${t}</button>`).join('')}</div></div>`,
        profile: `<div class="stack gap-4" style="max-width:420px"><div class="field"><label>Name</label><input class="input" value="${s.name}"></div><div class="field"><label>Email</label><input class="input" value="${s.email}" disabled><span class="t-caption muted">Change in account settings</span></div><div class="field"><label>Time zone</label><input class="input" value="Europe/Minsk (detected)"></div></div>`,
        goal: `<div class="stack gap-4" style="max-width:420px"><div class="field"><label>Target score</label><input class="input" value="5.0"></div><div class="field"><label>Exam date</label><input class="input" value="${s.examDate}"></div>${P.banner('warning', 'triangle-alert', 'Changing your date rebuilds future weeks')}</div>`,
        learning: `<div class="card" style="padding:0">${toggleRow('Explanations language', 'English + Russian toggle', true)}${toggleRow('Show word translations', '', true)}${toggleRow('Add suggested words automatically', '', false)}${toggleRow('Extended time (×1.5)', 'Practice and mock exams', false)}${toggleRow('Show transcripts during Listening', 'Attempts are marked and do not change your estimate', false)}</div>`,
        privacy: `<div class="card" style="padding:0">${toggleRow('Check my answers and voice recordings', 'Agreed Aug 29, 2026', true)}${toggleRow('Product emails', 'Agreed Aug 29, 2026', false)}</div><div class="row gap-2 mt-5">${P.btn('Export my data', { v: 'secondary', icon: 'download' })}${P.btn('Delete my account', { v: 'destructive' })}</div>`,
      };
      return `<div class="page"><h1 class="t-title-1">Settings</h1>
        <div class="settings mt-6"><nav class="settings-nav">${TABS.map(([id, t, ic]) => `<a class="nav-item ${id === tab ? 'on' : ''}" href="#/settings?tab=${id}">${P.i(ic)}${t}</a>`).join('')}</nav>
        <section class="settings-body"><h2 class="t-title-2">${TABS.find((t) => t[0] === tab)[1]}</h2><div class="mt-5">${forms[tab] || `<div class="muted">This section is not filled in the prototype.</div>`}</div>
        <div class="mt-6">${P.btn('Save changes', { attrs: 'data-action="toast" data-text="Saved"' })}</div></section></div></div>`;
    },
    mount(root) {
      const seg = root.querySelector('#themeSeg');
      if (seg) seg.addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) document.querySelector(`.proto-seg [data-theme="${b.dataset.t}"]`).click(), P.render(); });
      const rt = root.querySelector('#rtSwitch');
      if (rt) rt.addEventListener('click', () => { const c = document.getElementById('reduced'); c.checked = !c.checked; c.dispatchEvent(new Event('change')); rt.classList.toggle('on', c.checked); });
    },
  });

  /* ───────── Notifications ───────── */
  P.register('/notifications', {
    back: { label: 'Today', href: '/today', web: false },
    title: 'Notifications', num: 'plan', nav: 'notifications', tab: 'today',
    render: (ctx) => {
      const groups = [['Today', [['calendar-check', 'Week 8 is ready for your tutor to review', '09:12', true, '/weeks'], ['pen-line', 'Your essay was checked: 3/5', '08:40', true, '/review/writing'], ['message-square', 'Anna commented on your Email', '07:55', true, '/review/writing']]],
        ['Yesterday', [['notebook-pen', 'Weekly review is ready', '18:00', false, '/weeks/7/review'], ['clipboard-check', 'Mock exam this week', '10:00', false, '/exams']]]];
      return `<div class="page narrow"><div class="row between"><h1 class="t-title-1">Notifications</h1>${P.btn('Mark all as read', { v: 'ghost', size: 'sm', attrs: 'data-action="toast" data-text="All caught up"' })}</div>
        ${groups.map(([g, items]) => `<div class="t-overline mt-6">${g}</div><div class="card mt-2" style="padding:0"><div class="list">${items.map(([ic, t, time, unread, href]) =>
          `<a class="list-row click" href="#${href}"><span class="icon-box">${P.i(ic)}</span><div class="grow ${unread ? 't-strong' : ''}">${t}</div><span class="t-caption muted">${time}</span>${unread ? '<span class="unread-dot" aria-label="Unread"></span>' : ''}</a>`).join('')}</div></div>`).join('')}</div>`;
    },
  });

  /* ───────── Grammar ───────── */
  P.register('/grammar', {
    back: { label: 'Practice', href: '/practice', web: false },
    title: 'Grammar', num: 'plan', nav: 'grammar', tab: 'practice',
    render: (ctx) => {
      const rup = ['Present tenses', 'Past tenses', 'Future forms', 'Present perfect', 'Past perfect', 'Modal verbs', 'Conditionals', 'Passive voice', 'Reported speech', 'Relative clauses'];
      const col = ['Articles', 'Countable nouns', 'Comparatives', 'Gerunds and infinitives', 'Phrasal verbs', 'Linking words', 'Conditionals', 'Word order'];
      const row = (n, t, p, extra) => `<div class="list-row">${P.ring({ value: p, max: 4, size: 36, stroke: 4, center: '', label: `${p} of 4 steps` })}<div class="grow"><div class="t-strong">Unit ${n} · ${t}</div><div class="row gap-2 mt-1">${extra || ''}</div></div>${P.i('chevron-right', 'muted')}</div>`;
      return `<div class="page"><h1 class="t-title-1">Grammar</h1>
        <div class="row gap-2 mt-4">${['All', 'In progress', 'Suggested'].map((c, i) => `<button class="chip ${i === 0 ? 'on' : ''}">${c}</button>`).join('')}</div>
        <div class="grid-2 mt-5"><div class="card" style="padding:0"><div class="p-4 t-title-3">New Round-Up 5</div><div class="list">${rup.map((t, i) => row(i + 1, t, i < 6 ? 4 : i === 6 ? 2 : 0, i === 6 ? P.tag('accent', 'In this week') + P.tag('warn', 'Review suggested') : '')).join('')}</div></div>
          <div class="card" style="padding:0"><div class="p-4 t-title-3">Collins Grammar</div><div class="list">${col.map((t, i) => row(i + 1, t, i < 5 ? 4 : i === 6 ? 1 : 0, i === 6 ? P.tag('accent', 'In this week') : '')).join('')}</div></div></div></div>`;
    },
  });

  /* ───────── Mock exams list ───────── */
  P.register('/exams', {
    back: { label: 'Practice', href: '/practice', web: false },
    title: 'Mock exams', num: 'plan', nav: 'exams', tab: 'practice',
    render: (ctx) => `<div class="page narrow"><div class="row between wrap gap-3"><h1 class="t-title-1">Mock exams</h1>${P.btn('Start a mock exam', { v: 'secondary', icon: 'clipboard-check' })}</div>
      <div class="stack gap-3 mt-6">
        <a class="card click row gap-4" href="#/exams/1/result">${P.ring({ value: 4.5, max: 6, size: 48, center: '4.5', label: 'Band 4.5' })}<div class="grow"><div class="t-strong">Week 4 · Mock exam 1</div><div class="t-caption muted">Sep 26 · 4.5 · B2 · ≈ 80 of 120</div></div>${P.i('chevron-right', 'muted')}</a>
        <div class="card row gap-4">${P.iconBox('calendar-clock')}<div class="grow"><div class="t-strong">Week 8 · Mock exam 2</div><div class="t-caption muted">Scheduled for Oct 24 · about 1 h 30 min</div></div>${P.tag('neutral', 'Mock exam', { mod: 'exam' })}</div>
        <div class="card row gap-4">${P.iconBox('calendar-clock')}<div class="grow"><div class="t-strong">Week 17 · Mock exam 3</div><div class="t-caption muted">Scheduled for Dec 26</div></div>${P.tag('neutral', 'Mock exam', { mod: 'exam' })}</div>
      </div></div>`,
  });

  P.register('/404', {
    title: 'Page not found', num: 'plan', hidden: true, nav: '', tab: '',
    render: () => `<div class="page">${P.empty({ title: 'Page not found', text: 'This screen is not part of the prototype yet.', btn: P.btn('Go to Today', { href: '/today' }) })}</div>`,
  });
})();
