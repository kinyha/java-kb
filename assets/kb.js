/*
 * Java KB — общий скрипт. Контент страниц — обычный HTML, скрипт добавляет интерактив:
 * шапку с поиском, колонки тем на главной, урок и вопросы на странице темы, самооценку и прогресс.
 * Страница сообщает о себе через <body data-page="index|topic" data-root="../" data-topic="id">.
 */
(() => {
  'use strict';

  const MARKS = 'kb:v1';        // { "topic/qid": { c: 0|1|2, t: время оценки, n: сколько раз подряд «объясню», prev } }
  const PREFS = 'kb:prefs';     // { theme, level, due, path }
  const REPS = 'kb:rep';        // повторение тем целиком: { "topic": { q: когда поставлена в очередь, c, t, n } }

  const FREQ = { 3: 'часто', 2: 'иногда', 1: 'редко' };
  const CONF = ['не знаю', 'плаваю', 'объясню'];
  const CONF_TASK = ['не решил', 'с подсказкой', 'решил'];      // для задач: data-type="task"
  const CONF_TOPIC = ['не помню', 'плаваю', 'помню'];            // для темы целиком
  const KINDS = { 'задача': 'k-task', 'ревью': 'k-review' };
  const DAY = 864e5;

  // интервальное повторение: чем увереннее ответ, тем позже карточка вернётся
  const interval = m => m.c === 0 ? 1 : m.c === 1 ? 3 : [7, 21, 60][Math.min(m.n || 1, 3) - 1];
  const dueIn = m => Math.ceil((m.t + interval(m) * DAY - Date.now()) / DAY);
  const isDue = m => !m || dueIn(m) <= 0;
  const sameDay = t => new Date(t).toDateString() === new Date().toDateString();
  // тема в повторении: поставлена в очередь кнопкой или подошёл срок после оценки
  const topicDue = r => !!r && (!!r.q || (r.c != null && dueIn(r) <= 0));

  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
  const h = (tag, props = {}, ...kids) => {
    const n = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) {
      if (v == null || v === false) continue;
      if (k === 'class') n.className = v;
      else if (k === 'text') n.textContent = v;
      else if (k === 'html') n.innerHTML = v;
      else if (k.startsWith('on')) n.addEventListener(k.slice(2), v);
      else n.setAttribute(k, v === true ? '' : v);
    }
    for (const kid of kids.flat(Infinity)) if (kid != null && kid !== false) n.append(kid);
    return n;
  };
  const load = (k, d) => { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch (e) { return d; } };
  const save = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} };
  const plural = (n, one, few, many) => {
    const a = n % 10, b = n % 100;
    return n + ' ' + (a === 1 && b !== 11 ? one : a >= 2 && a <= 4 && (b < 12 || b > 14) ? few : many);
  };

  const body = document.body;
  const ROOT = body.dataset.root || '';
  const PAGE = body.dataset.page || 'topic';
  const TREE = window.KB_TREE || [];
  const SEARCH = window.KB_SEARCH || [];
  const ALL = [];
  // папка: { id, title, items }, тема: { id, freq, status, title, scope }; у темы в ALL есть path — папки от корня
  const walk = (nodes, path) => nodes.forEach(n => n.items ? walk(n.items, path.concat(n)) : ALL.push(Object.assign({ path }, n)));
  walk(TREE, []);
  const pathTitle = t => t.path.map(f => f.title).join(' › ');
  const indexHref = ids => ROOT + 'index.html' + (ids.length ? '#' + ids.join('/') : '');
  const BY_ID = new Map(ALL.map(t => [t.id, t]));
  const QS = SEARCH.filter(c => c.kind !== 'step');       // вопросы с собеседования
  const STEPS = SEARCH.filter(c => c.kind === 'step');    // шаги уроков
  const QCOUNT = QS.reduce((m, c) => { m[c.topic] = (m[c.topic] || 0) + 1; return m; }, {});
  const prefs = Object.assign({ theme: null, level: 'middle', due: false, path: null }, load(PREFS, {}));
  const marks = load(MARKS, {});
  const reps = load(REPS, {});

  const topicHref = id => ROOT + 'topics/' + id + '.html';
  const pbar = (k, total) => {
    const w = x => (total ? x / total * 100 : 0) + '%';
    return h('span', { class: 'pbar', 'aria-hidden': 'true' },
      h('i', { class: 'k2', style: 'width:' + w(k[2]) }), h('i', { class: 'k1', style: 'width:' + w(k[1]) }), h('i', { class: 'k0', style: 'width:' + w(k[0]) }));
  };
  const tally = id => {
    const k = [0, 0, 0];
    for (const [key, m] of Object.entries(marks)) if (key.startsWith(id + '/')) k[m.c]++;
    return k;
  };

  /* ---------- тема оформления ---------- */
  const applyTheme = () => {
    if (prefs.theme) document.documentElement.dataset.theme = prefs.theme;
    else delete document.documentElement.dataset.theme;
  };
  const isDark = () => prefs.theme ? prefs.theme === 'dark'
    : !!(window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches);
  applyTheme();
  const ICON_THEME = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"/></svg>';

  /* ---------- поиск по темам и вопросам ---------- */
  const searchBox = () => {
    const docs = [
      ...SEARCH.map(c => {
        const t = BY_ID.get(c.topic);
        return { kind: c.kind === 'step' ? 'урок' : 'вопрос', title: c.title, sub: t ? t.title : c.topic, href: topicHref(c.topic) + '#' + c.id,
          hay: (c.title + ' ' + c.text).toLowerCase() };
      }),
      ...ALL.map(t => ({ kind: 'тема', title: t.title, sub: pathTitle(t) + (t.status === 'empty' ? ' · пусто' : ''),
        href: t.status === 'empty' ? null : topicHref(t.id), hay: (t.title + ' ' + t.scope + ' ' + pathTitle(t)).toLowerCase() })),
    ];
    const input = h('input', { type: 'search', placeholder: 'Поиск по темам и вопросам', autocomplete: 'off', spellcheck: 'false',
      role: 'combobox', 'aria-label': 'Поиск по темам и вопросам', 'aria-expanded': 'false', 'aria-controls': 'kbResults', 'aria-autocomplete': 'list' });
    const list = h('ul', { class: 'results', id: 'kbResults', role: 'listbox', hidden: true });
    const box = h('div', { class: 'search' }, input, h('kbd', { text: '/' }), list);
    let items = [], active = -1;

    const find = q => {
      const words = q.toLowerCase().split(/\s+/).filter(Boolean);
      if (!words.length) return [];
      return docs
        .filter(d => words.every(w => d.hay.includes(w)))
        .map(d => ({ d, s: (words.every(w => d.title.toLowerCase().includes(w)) ? 4 : 0) + (d.href ? 2 : 0) + (d.kind === 'вопрос' ? 1 : 0) }))
        .sort((a, b) => b.s - a.s).slice(0, 12).map(x => x.d);
    };
    const show = open => { list.hidden = !open; input.setAttribute('aria-expanded', String(open)); };
    const render = () => {
      const q = input.value.trim();
      list.replaceChildren(...(items.length ? items.map((d, i) => h('li', { role: 'option', id: 'kbr' + i, 'aria-selected': String(i === active), class: d.href ? null : 'off' },
        h('a', { href: d.href, tabindex: '-1' }, h('span', { class: 'r-kind', text: d.kind }), h('span', { class: 'r-title', text: d.title }), h('span', { class: 'r-sub', text: d.sub }))))
        : [h('li', { class: 'r-none', text: 'Ничего не нашлось' })]));
      if (active >= 0) input.setAttribute('aria-activedescendant', 'kbr' + active); else input.removeAttribute('aria-activedescendant');
      show(!!q);
    };
    input.addEventListener('input', () => { items = find(input.value); active = items.length ? 0 : -1; render(); });
    input.addEventListener('keydown', e => {
      if (e.key === 'ArrowDown' && items.length) { active = (active + 1) % items.length; render(); e.preventDefault(); }
      else if (e.key === 'ArrowUp' && items.length) { active = (active - 1 + items.length) % items.length; render(); e.preventDefault(); }
      else if (e.key === 'Enter' && items[active] && items[active].href) { location.href = items[active].href; show(false); }
      else if (e.key === 'Escape') { input.value = ''; items = []; show(false); }
    });
    input.addEventListener('focus', () => { if (input.value.trim()) show(true); });
    list.addEventListener('click', () => show(false));
    document.addEventListener('click', e => { if (!box.contains(e.target)) show(false); });
    document.addEventListener('keydown', e => {
      if (e.key === '/' && !/^(input|textarea|select)$/i.test(document.activeElement.tagName)) { input.focus(); e.preventDefault(); }
    });
    return box;
  };

  /* ---------- шапка ---------- */
  const topbar = () => {
    body.prepend(h('div', { class: 'bar' },
      h('div', { class: 'wrap bar-in' },
        h('a', { class: 'brand', href: ROOT + 'index.html' }, h('span', { class: 'brand-mark', 'aria-hidden': 'true' }), 'Java KB'),
        searchBox(),
        h('button', {
          class: 'icon-btn', type: 'button', 'aria-label': 'Сменить тему оформления', title: 'Светлая / тёмная', html: ICON_THEME,
          onclick: () => { prefs.theme = isDark() ? 'light' : 'dark'; save(PREFS, prefs); applyTheme(); },
        }))));
  };

  /* ---------- пошаговая схема ---------- */
  // <figure class="fig play">: в SVG у слоёв data-s — на каких шагах слой виден ("2", "2-4", "3-"),
  // в <ol class="play-steps"> — подпись к каждому шагу. Без скрипта видны все слои и все подписи.
  const initPlay = fig => {
    const caps = $$('.play-steps > li', fig);
    if (!caps.length) return;
    const layers = $$('[data-s]', fig).map(el => {
      const [a, b] = el.dataset.s.split('-');
      return { el, from: +a, to: b === undefined ? +a : b === '' ? Infinity : +b };
    });
    let i = 1;
    const count = h('span', { class: 'play-n' });
    const prev = h('button', { type: 'button', class: 'play-btn', text: '← Назад' });
    const next = h('button', { type: 'button', class: 'play-btn primary' });
    const show = n => {
      i = Math.max(1, Math.min(caps.length, n));
      layers.forEach(l => l.el.classList.toggle('off', i < l.from || i > l.to));
      caps.forEach((c, k) => c.classList.toggle('on', k === i - 1));
      count.textContent = 'шаг ' + i + ' из ' + caps.length;
      prev.disabled = i === 1;
      next.textContent = i === caps.length ? 'Сначала' : 'Дальше →';
    };
    prev.addEventListener('click', () => show(i - 1));
    next.addEventListener('click', () => show(i === caps.length ? 1 : i + 1));
    fig.tabIndex = 0;
    fig.setAttribute('aria-label', 'Пошаговая схема, стрелки влево и вправо переключают шаги');
    fig.addEventListener('keydown', e => {
      if (e.target !== fig) return;
      if (e.key === 'ArrowRight') { show(i + 1); e.preventDefault(); }
      else if (e.key === 'ArrowLeft') { show(i - 1); e.preventDefault(); }
    });
    fig.classList.add('js');
    $('.play-steps', fig).after(h('div', { class: 'play-bar' }, prev, count, next));
    show(1);
  };

  /* ---------- код, таблицы, подсветка ---------- */
  const enhanceContent = () => {
    $$('figure.play').forEach(initPlay);
    $$('pre > code').forEach(code => {
      const pre = code.parentElement;
      if (pre.parentElement.classList.contains('code')) return;
      const lang = (code.className.match(/language-([\w-]+)/) || [])[1] || '';
      const box = h('div', { class: 'code' });
      pre.before(box);
      const copy = h('button', { class: 'copy', type: 'button', text: 'Копировать' });
      copy.addEventListener('click', async () => {
        try { await navigator.clipboard.writeText(code.textContent); copy.textContent = 'Скопировано'; }
        catch (e) { copy.textContent = 'Не вышло'; }
        setTimeout(() => { copy.textContent = 'Копировать'; }, 1400);
      });
      const label = lang && lang !== 'text' && lang !== 'plaintext' ? h('span', { class: 'lang', text: lang }) : null;
      box.append(pre, h('div', { class: 'code-tools' }, label, copy));
    });
    $$('table.mx').forEach(t => {
      if (t.parentElement.classList.contains('tbl')) return;
      const w = h('div', { class: 'tbl' });
      t.before(w); w.append(t);
    });
    try {
      if (window.hljs) {
        hljs.configure({ ignoreUnescapedHTML: true });
        $$('pre code[class*="language-"]').forEach(c => hljs.highlightElement(c));
      }
    } catch (e) {}
  };

  /* ---------- главная: библиотека колонками ---------- */
  // папки и темы колонками, как в Finder: колонка на каждый уровень пути, в конце превью темы.
  // Путь хранится в адресе: index.html#backend/messaging/kafka/kafka-dlq
  const initIndex = () => {
    const cols = $('#cols');
    const DUE = 'due';
    const isDueCard = c => { const m = marks[c.topic + '/' + c.id]; return !!m && dueIn(m) <= 0; };
    const dueCards = () => QS.filter(isDueCard);
    const dueTopics = () => ALL.filter(t => t.status !== 'empty' && topicDue(reps[t.id]));
    const dueCount = () => dueTopics().length + dueCards().length;
    const topicsIn = folder => ALL.filter(t => t.path.includes(folder));

    // путь из id в узлы; лишнее и несуществующее отрезается
    const nodesOf = ids => {
      if (ids[0] === DUE) return [DUE].concat(ids.slice(1, 2));   // в колонке «к повторению» выбор — ключ карточки
      const out = [];
      let level = TREE;
      for (const id of ids) {
        if (!level) break;
        const n = level.find(x => x.id === id);
        if (!n) break;
        out.push(n);
        level = n.items || null;
      }
      return out;
    };
    const valid = ids => nodesOf(ids).map(n => typeof n === 'string' ? n : n.id).filter((id, i, a) => a[0] !== DUE || i === 0);
    const fromHash = () => valid(decodeURIComponent(location.hash.slice(1)).split('/').filter(Boolean));
    let sel = fromHash();
    const restored = sel.length > 0 || valid(prefs.path || []).length > 0;
    if (!sel.length) sel = valid(prefs.path || []);
    const narrow = !!(window.matchMedia && matchMedia('(max-width: 760px)').matches);
    if (!sel.length && TREE[0] && !narrow) sel = [TREE[0].id];
    let view = 0;                                       // узкий экран: какая колонка видна
    const commit = () => {
      prefs.path = valid(sel); save(PREFS, prefs);
      history.replaceState(null, '', prefs.path.length ? '#' + prefs.path.join('/') : location.pathname + location.search);
    };

    const column = (c, title, kids, list, wide) => h('section', { class: 'col' + (wide ? ' col-pv' : ''), 'data-col': c, 'data-w': wide ? 'wide' : 'list' },
      h('header', { class: 'col-h' },
        c ? h('button', { class: 'col-back', type: 'button', 'aria-label': 'Назад', onclick: () => { view = c - 1; layout(); } }, '‹') : null,
        h('span', { text: title })),
      h('div', list ? { class: 'col-body', role: 'listbox', 'aria-label': title } : { class: 'col-body' }, kids));
    const blank = (c, wide) => h('section', { class: 'col col-blank' + (wide ? ' col-pv' : ''), 'data-col': c, 'data-w': wide ? 'wide' : 'list', 'aria-hidden': 'true' });
    const item = (c, key, o) => h(o.href ? 'a' : 'div', {
        class: 'it' + (o.dim ? ' dim' : '') + (o.cls ? ' ' + o.cls : ''), role: 'option', tabindex: '-1', href: o.href, title: o.title,
        'data-col': c, 'data-key': key, 'data-kind': o.kind || null, 'data-more': o.more || null, 'aria-selected': String(sel[c] === key) },
      o.dot || null,
      h('span', { class: 'it-main' }, h('span', { class: 'it-t', text: o.title }), o.sub ? h('span', { class: 'it-s', text: o.sub }) : null),
      o.meta ? h('span', { class: 'it-m' }, o.meta) : null);
    const freqBars = f => h('span', { class: 'freq', 'data-f': f, title: 'спрашивают ' + FREQ[f], 'aria-label': 'спрашивают ' + FREQ[f] }, h('i'), h('i'), h('i'));
    const cdot = m => h('i', { class: 'cdot', 'data-c': m ? m.c : null, 'aria-hidden': 'true' });
    const countMeta = topics => {
      const due = QS.filter(c => topics.some(t => t.id === c.topic) && isDueCard(c)).length
        + topics.filter(t => t.status !== 'empty' && topicDue(reps[t.id])).length;
      return [due ? h('b', { class: 'badge', title: 'к повторению: ' + due, text: String(due) }) : null, h('span', { text: String(topics.length) })];
    };
    const nodeItem = (c, n) => n.items
      ? item(c, n.id, { title: n.title, kind: 'folder', meta: countMeta(topicsIn(n)), more: true })
      : item(c, n.id, { title: n.title, kind: 'topic', dim: n.status === 'empty',
          dot: h('i', { class: 'sdot', 'data-s': n.status, title: n.status === 'empty' ? 'пусто' : n.status === 'draft' ? 'черновик' : 'готово' }),
          meta: [QCOUNT[n.id] ? h('span', { text: String(QCOUNT[n.id]) }) : null, freqBars(n.freq)] });

    const preview = t => {
      const cards = QS.filter(c => c.topic === t.id);
      const steps = STEPS.filter(c => c.topic === t.id);
      const k = tally(t.id);
      const empty = t.status === 'empty';
      const link = (c, kids) => h('a', { href: topicHref(t.id) + '#' + c.id }, kids);
      return [
        h('p', { class: 'pv-path', text: pathTitle(t) }),
        h('h2', { class: 'pv-title', text: t.title }),
        h('p', { class: 'pv-facts' }, 'спрашивают ' + FREQ[t.freq] + ' · ' + (empty ? 'пусто' : t.status === 'draft' ? 'черновик' : 'готово'),
          empty || !reps[t.id] ? null : [' · ', topicDue(reps[t.id]) ? h('b', { class: 'it-due', text: 'повторить тему' })
            : 'повторить тему через ' + plural(dueIn(reps[t.id]), 'день', 'дня', 'дней')]),
        h('p', { class: 'pv-scope', text: t.scope }),
        empty ? null : h('div', { class: 'pv-open' },
          h('a', { class: 'btn', href: topicHref(t.id), text: 'Открыть →' }),
          cards.length ? h('span', { class: 'prog' }, 'объясню ' + k[2] + ' из ' + cards.length, pbar(k, cards.length)) : null),
        steps.length ? h('section', { class: 'pv-sec' }, h('h3', { text: 'Урок' }),
          h('ol', { class: 'pv-steps' }, steps.map(c => h('li', {}, link(c, h('span', { text: c.title })))))) : null,
        cards.length ? h('section', { class: 'pv-sec' }, h('h3', { text: cards.every(c => c.kind === 'task') ? 'Задачи' : steps.length ? 'На собеседовании' : 'Вопросы' }),
          h('ol', { class: 'pv-qs' }, cards.map(c => {
            const m = marks[t.id + '/' + c.id];
            return h('li', {}, link(c, [cdot(m), h('span', { text: c.title }), m && dueIn(m) <= 0 ? h('b', { class: 'it-due', text: 'повторить' }) : null]));
          }))) : null,
        empty ? h('p', { class: 'pv-empty', text: 'Тема пока пустая. Чтобы заполнить, попроси агента в чате.' }) : null,
      ];
    };

    // колонка i по текущему пути: список детей выбранной папки, превью темы или ничего
    const buildCol = i => {
      const ns = nodesOf(sel);
      if (i === 0) {
        const due = dueCount();
        return column(0, 'Разделы', [
          item(0, DUE, { title: 'К повторению', meta: due ? h('b', { class: 'badge', text: String(due) }) : h('span', { text: '0' }), more: true, dim: !due }),
          h('hr', { class: 'col-sep' }),
          TREE.map(n => nodeItem(0, n)),
        ], true);
      }
      const parent = ns[i - 1];
      if (parent === undefined) return null;
      if (parent === DUE) {
        if (i !== 1) return null;
        const due = dueCards(), topics = dueTopics();
        return column(1, 'К повторению', due.length || topics.length ? [
          topics.map(t => item(1, t.id, { title: t.title, sub: 'тема · ' + pathTitle(t), href: topicHref(t.id), dot: cdot(reps[t.id].q ? null : reps[t.id]), cls: 'it-q' })),
          topics.length && due.length ? h('hr', { class: 'col-sep' }) : null,
          due.map(q => { const tp = BY_ID.get(q.topic); return item(1, q.topic + '/' + q.id,
              { title: q.title, sub: tp ? tp.title : q.topic, href: topicHref(q.topic) + '#' + q.id, dot: cdot(marks[q.topic + '/' + q.id]), cls: 'it-q' }); }),
        ] : h('p', { class: 'col-empty', text: 'Повторять нечего. Здесь появятся темы, которые ты добавил в повторение, и оценённые карточки, когда подойдёт срок.' }), true);
      }
      if (typeof parent === 'string') return null;
      if (parent.items) return column(i, parent.title, parent.items.map(n => nodeItem(i, n)), true);
      return column(i, 'Тема', preview(BY_ID.get(parent.id)), false, true);
    };
    const layout = () => {
      const list = $$('.col', cols);
      cols.style.setProperty('--tpl', list.map(col => col.dataset.w === 'wide' ? 'minmax(340px,1fr)'
        : col.dataset.col === '0' ? 'minmax(180px,220px)' : 'minmax(200px,260px)').join(' '));
      list.forEach(col => col.classList.toggle('on', +col.dataset.col === view));
      if (cols.scrollWidth > cols.clientWidth) cols.scrollLeft = cols.scrollWidth;
    };
    const renderFrom = c => {
      while (cols.children.length > c) cols.lastChild.remove();
      for (let i = c; ; i++) { const col = buildCol(i); if (!col) break; cols.append(col); }
      // справа добиваем пустыми колонками, последняя — под превью
      if (cols.lastChild.dataset.w !== 'wide') {
        while (cols.children.length < 3) cols.append(blank(cols.children.length, false));
        cols.append(blank(cols.children.length, true));
      }
      // в каждой колонке одна точка входа с Tab: выбранный пункт или первый
      $$('.col', cols).forEach(col => {
        const its = $$('.it', col);
        its.forEach(x => { x.tabIndex = -1; });
        const s = its.find(x => x.getAttribute('aria-selected') === 'true') || its[0];
        if (s) s.tabIndex = 0;
      });
      layout();
    };
    const hasNext = c => { const n = cols.children[c + 1]; return !!n && !n.classList.contains('col-blank'); };
    const choose = (el, focus) => {
      const c = +el.dataset.col;
      sel = sel.slice(0, c).concat(el.dataset.key);
      $$('.it', cols.children[c]).forEach(x => x.setAttribute('aria-selected', String(x === el)));
      renderFrom(c + 1); commit();
      if (focus) el.focus();
    };
    const back = c => {
      if (c < 1) return;
      sel = sel.slice(0, c);
      $$('.it', cols.children[c]).forEach(x => x.setAttribute('aria-selected', 'false'));
      renderFrom(c + 1); commit();
      const s = $('.it[aria-selected="true"]', cols.children[c - 1]);
      if (s) s.focus();
    };
    const deeper = c => {
      if (!hasNext(c)) return;
      const next = cols.children[c + 1];
      view = c + 1; layout();
      if (next.dataset.w === 'wide') { const f = $('a.btn', next); if (f) f.focus(); return; }
      const s = $('.it[aria-selected="true"]', next) || $('.it', next);
      if (s) s.tagName === 'A' ? s.focus() : choose(s, true);
    };
    const open = el => {
      if (el.tagName === 'A') { location.href = el.href; return; }
      const t = el.dataset.kind === 'topic' && BY_ID.get(el.dataset.key);
      if (t && t.status !== 'empty') location.href = topicHref(t.id);
      else deeper(+el.dataset.col);
    };

    cols.addEventListener('click', e => {
      const el = e.target.closest('.it');
      if (!el || el.tagName === 'A') return;
      choose(el, true);
      if (hasNext(+el.dataset.col)) { view = +el.dataset.col + 1; layout(); }
    });
    cols.addEventListener('dblclick', e => { const el = e.target.closest('.it'); if (el) open(el); });
    cols.addEventListener('keydown', e => {
      const el = e.target.closest && e.target.closest('.it');
      if (!el) return;
      const c = +el.dataset.col, its = $$('.it', cols.children[c]), i = its.indexOf(el);
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        const n = its[i + (e.key === 'ArrowDown' ? 1 : -1)];
        if (n) n.tagName === 'A' ? (its.forEach(x => x.setAttribute('aria-selected', String(x === n))), n.focus()) : choose(n, true);
      } else if (e.key === 'ArrowRight') deeper(c);
      else if (e.key === 'ArrowLeft') { view = Math.max(c - 1, 0); back(c); }
      else if (e.key === 'Enter') open(el);
      else return;
      e.preventDefault();
    });
    // активная колонка подсвечивает выбор ярче
    cols.addEventListener('focusin', e => $$('.col', cols).forEach(col => col.classList.toggle('active', col.contains(e.target))));
    cols.addEventListener('focusout', e => { if (!cols.contains(e.relatedTarget)) $$('.col', cols).forEach(col => col.classList.remove('active')); });

    const stats = () => {
      const known = QS.filter(c => { const m = marks[c.topic + '/' + c.id]; return m && m.c === 2; }).length;
      $('#stats').textContent = [plural(ALL.length, 'тема', 'темы', 'тем'), 'с материалом ' + ALL.filter(t => t.status !== 'empty').length,
        plural(QS.length, 'вопрос', 'вопроса', 'вопросов'), 'объясню ' + known, 'к повторению ' + dueCount()].join(' · ');
    };
    const all = () => { stats(); renderFrom(0); };
    // узкий экран открывается на самой глубокой колонке пути
    const deepest = () => { const real = $$('.col:not(.col-blank)', cols).length; return Math.min(sel.length, real - 1); };
    window.addEventListener('hashchange', () => { sel = fromHash(); all(); view = deepest(); layout(); });
    // вернулись кнопкой «назад» со страницы темы — оценки могли поменяться
    window.addEventListener('pageshow', e => {
      if (!e.persisted) return;
      Object.keys(marks).forEach(k => delete marks[k]);
      Object.assign(marks, load(MARKS, {}));
      Object.keys(reps).forEach(k => delete reps[k]);
      Object.assign(reps, load(REPS, {}));
      all();
    });
    all();
    if (restored) { view = deepest(); layout(); }
    $$('.it[aria-selected="true"]', cols).forEach(x => x.scrollIntoView({ block: 'nearest' }));
  };

  /* ---------- страница темы: урок, потом вопросы с собеседования ---------- */
  // вывод кода прячется: сначала предскажи, потом открой
  const hideOutputs = root => $$('pre.out', root).forEach(pre => {
    if (pre.closest('details')) return;
    const d = h('details', { class: 'predict' }, h('summary', { text: 'Что выведет? Сначала предскажи, потом открой' }));
    pre.before(d); d.append(pre);
  });

  // тело карточки: переключатель уровня ответа, скрытый вывод, метки заданий
  const buildCard = (card, bodyEl, topic, onLevel) => {
    const ans = $(':scope > .answer', bodyEl);
    if (ans) {
      const bar = h('div', { class: 'a-bar' });
      if ($(':scope > [data-level="senior"]', ans)) {
        bar.append(h('button', { type: 'button', 'data-level': 'middle', 'aria-pressed': 'false', text: 'Ответ · middle+' }),
          h('button', { type: 'button', 'data-level': 'senior', 'aria-pressed': 'false', text: '+ Senior' }));
        bar.addEventListener('click', e => { const b = e.target.closest('button'); if (b) onLevel(b.dataset.level); });
      } else bar.append(h('span', { class: 'a-label', text: 'Ответ · middle+' }));
      ans.prepend(bar);
    }
    hideOutputs(bodyEl);
    $$('.check > details[data-kind]', bodyEl).forEach(d => {
      const k = d.dataset.kind, s = $('summary', d);
      if (s) s.prepend(h('span', { class: 'kind ' + (KINDS[k] || ''), text: k }));
    });
    const qa = $(':scope > .qa', bodyEl);            // старый формат: «Твои вопросы» внутри карточки
    if (qa && !$('.qa-item', qa)) qa.remove();
  };

  const initTopic = () => {
    const id = body.dataset.topic || 'topic';
    const meta = BY_ID.get(id);
    const main = $('main');
    const cards = $$('article.q', main);
    const steps = $$('.lesson > section.step', main);
    const interview = $('section.interview', main);
    const dots = new Map();
    const here = meta ? indexHref(meta.path.map(f => f.id).concat(id)) : indexHref([]);

    const head = $('.topic-head', main);
    if (head && meta) {
      if (!$('.eyebrow', head)) head.prepend(h('p', { class: 'eyebrow' }, meta.path.map((f, i) =>
        [i ? ' › ' : null, h('a', { href: indexHref(meta.path.slice(0, i + 1).map(x => x.id)), text: f.title })])));
      if (!$('.scope', head)) head.append(h('p', { class: 'scope', text: meta.scope }));
    }
    const ivTitle = interview && $(':scope > h2', interview) ? $(':scope > h2', interview).textContent : 'Вопросы';
    const tasksOnly = cards.length > 0 && cards.every(c => c.dataset.type === 'task');
    const nCards = n => tasksOnly ? plural(n, 'задача', 'задачи', 'задач') : plural(n, 'вопрос', 'вопроса', 'вопросов');
    if (head && steps.length && interview) head.append(h('nav', { class: 'parts', 'aria-label': 'Части темы' },
      h('a', { href: '#' + steps[0].id }, h('b', { text: 'Урок' }), ' · ' + plural(steps.length, 'шаг', 'шага', 'шагов')),
      h('a', { href: '#interview' }, h('b', { text: ivTitle }), ' · ' + nCards(cards.length))));

    // повторение темы целиком: кнопка ставит тему в «К повторению», оценка назначает следующий раз
    const repBox = h('div', { class: 'rep', role: 'group', 'aria-label': 'Повторение темы' });
    const setRep = r => { if (r) reps[id] = r; else delete reps[id]; save(REPS, reps); paintRep(); };
    const paintRep = () => {
      const r = reps[id];
      const btn = (text, onclick, cls) => h('button', { type: 'button', class: cls || 'chip', text, onclick });
      if (!r) { repBox.replaceChildren(btn('Добавить в повторение', () => setRep({ q: Date.now() }))); return; }
      const due = topicDue(r);
      const conf = h('div', { class: 'conf', role: 'group', 'aria-label': 'Как помнишь тему' },
        CONF_TOPIC.map((label, c) => h('button', { type: 'button', 'data-c': c, 'aria-pressed': String(!r.q && r.c === c), text: label,
          onclick: () => setRep({ c, t: Date.now(), n: c === 2 ? (r.c === 2 ? (r.n || 1) + 1 : 1) : 0 }) })));
      repBox.replaceChildren(...[
        h('span', { class: 'rep-st' }, 'Повторение темы: ', due ? h('b', { class: 'due', text: 'пора повторить' })
          : 'через ' + plural(dueIn(r), 'день', 'дня', 'дней')),
        due ? h('span', { text: 'как помнишь?' }) : null, conf,
        due ? null : btn('Повторить сейчас', () => setRep(Object.assign({}, r, { q: Date.now() }))),
        btn('Убрать', () => setRep(null), 'rep-x')].filter(Boolean));
    };
    if (head && meta) { head.append(repBox); paintRep(); }

    const layout = h('div', { class: 'wrap layout' });
    const side = h('aside', { class: 'side' });
    main.before(layout);
    layout.append(side, main);

    const setOpen = (card, open) => {
      card.classList.toggle('open', open);
      $('.q-toggle', card).setAttribute('aria-expanded', String(open));
    };
    const applyLevel = () => $$('.answer', main).forEach(a => {
      a.dataset.show = prefs.level === 'senior' ? 'senior' : 'middle';
      $$('.a-bar button', a).forEach(b => b.setAttribute('aria-pressed', String(b.dataset.level === a.dataset.show)));
    });
    const onLevel = level => { prefs.level = level; save(PREFS, prefs); applyLevel(); };

    // вопрос свёрнут: сначала ответь сам, открытие карточки показывает ответ
    cards.forEach(card => {
      const title = $(':scope > h3', card);
      if (!title) return;
      const bodyEl = h('div', { class: 'q-body', id: card.id + '-body' });
      Array.from(card.childNodes).forEach(n => { if (n !== title) bodyEl.append(n); });
      const toggle = h('button', { class: 'q-toggle', type: 'button', 'aria-expanded': 'false', 'aria-controls': bodyEl.id });
      toggle.append(...Array.from(title.childNodes));
      title.append(toggle);
      toggle.addEventListener('click', () => setOpen(card, !card.classList.contains('open')));
      const task = card.dataset.type === 'task';
      const conf = h('div', { class: 'conf', role: 'group', 'aria-label': task ? 'Как решил' : 'Насколько уверенно ответил' },
        (task ? CONF_TASK : CONF).map((label, c) => h('button', { type: 'button', 'data-c': c, 'aria-pressed': 'false', text: label })));
      conf.addEventListener('click', e => {
        const b = e.target.closest('button[data-c]');
        if (!b) return;
        const key = id + '/' + card.id, c = +b.dataset.c, prev = marks[key];
        if (prev && prev.c === c && sameDay(prev.t)) {          // повторный клик в тот же день — отменить оценку
          if (prev.prev) marks[key] = prev.prev; else delete marks[key];
        } else {
          const base = prev ? { c: prev.c, t: prev.t, n: prev.n } : null;
          marks[key] = { c, t: Date.now(), n: c === 2 ? (prev && prev.c === 2 ? (prev.n || 1) + 1 : 1) : 0, prev: base };
        }
        save(MARKS, marks);
        paint(card); progress(); filter();
      });
      card.append(h('header', { class: 'q-head' }, title, h('p', { class: 'q-tags' }), conf), bodyEl);
      buildCard(card, bodyEl, id, onLevel);
    });
    hideOutputs($('.lesson', main) || h('div'));

    const paint = card => {
      const m = marks[id + '/' + card.id];
      const c = m ? m.c : null;
      if (c === null) delete card.dataset.c; else card.dataset.c = c;
      $$('.conf button', card).forEach(b => b.setAttribute('aria-pressed', String(+b.dataset.c === c)));
      const dot = dots.get(card.id);
      if (dot) { if (c === null) delete dot.dataset.c; else dot.dataset.c = c; }
      const parts = [];
      if (card.dataset.freq) parts.push(h('span', { text: 'спрашивают ' + FREQ[card.dataset.freq] }));
      if (!m) parts.push(h('span', { class: 'new', text: 'новый' }));
      else {
        const d = dueIn(m);
        parts.push(d <= 0 ? h('span', { class: 'due', text: 'пора повторить' }) : h('span', { text: 'повторить через ' + plural(d, 'день', 'дня', 'дней') }));
      }
      $('.q-tags', card).replaceChildren(...parts.flatMap((p, i) => i ? [' · ', p] : [p]));
    };

    // оглавление темы: шаги урока и вопросы с отметками
    const tocLink = (key, kids) => h('li', {}, h('a', { href: '#' + key, 'data-toc': key }, kids));
    const narrow = !!(window.matchMedia && matchMedia('(max-width: 960px)').matches);
    side.append(h('a', { class: 'toc-back', href: here, text: '← Все темы' }),
      h('details', { class: 'toc-box', open: !narrow }, h('summary', { text: 'Содержание' }),
        h('nav', { class: 'toc', 'aria-label': 'Содержание темы' },
          steps.length ? [h('p', { class: 'toc-h', text: 'Урок' }),
            h('ol', { class: 'toc-steps' }, steps.map(s => tocLink(s.id, h('span', { text: $('h2', s).textContent }))))] : null,
          cards.length ? [h('p', { class: 'toc-h', text: ivTitle }),
            h('ol', { class: 'toc-qs' }, cards.map(card => {
              const dot = h('i', { class: 'cdot', 'aria-hidden': 'true' });
              dots.set(card.id, dot);
              return tocLink(card.id, [dot, h('span', { text: $('.q-toggle', card).textContent })]);
            }))] : null)));

    // над вопросами: прогресс и фильтр «к повторению»
    const dueBtn = h('button', { class: 'chip', type: 'button', 'aria-pressed': String(!!prefs.due), text: 'К повторению',
      title: 'Показать только новые карточки и те, которые пора повторить' });
    const pText = h('span');
    const pBox = h('span', { class: 'prog' }, pText);
    const tools = h('div', { class: 'topic-tools' }, dueBtn, pBox);
    const ivHead = interview && $(':scope > h2', interview);
    if (cards.length) { if (ivHead) ivHead.after(tools); else if (head) head.after(tools); else main.prepend(tools); }
    dueBtn.addEventListener('click', () => {
      prefs.due = !prefs.due; save(PREFS, prefs);
      dueBtn.setAttribute('aria-pressed', String(prefs.due)); filter();
    });
    const filter = () => {
      cards.forEach(card => {
        card.hidden = !!prefs.due && !isDue(marks[id + '/' + card.id]);
        const a = $('a[data-toc="' + card.id + '"]', side);
        if (a) a.parentElement.hidden = card.hidden;
      });
    };
    const progress = () => {
      const k = [0, 0, 0];
      let due = 0;
      cards.forEach(card => { const m = marks[id + '/' + card.id]; if (m) k[m.c]++; if (isDue(m)) due++; });
      pText.textContent = 'объясню ' + k[2] + ' из ' + cards.length + (due ? ' · к повторению ' + due : '');
      const old = $('.pbar', pBox); if (old) old.remove();
      pBox.append(pbar(k, cards.length));
    };

    // «Твои вопросы»: ответы агента на вопросы по теме; пустой блок не показываем
    const qa = $(':scope > .qa', main);
    if (qa && !$('.qa-item', qa)) qa.remove();

    // листалка по темам с материалом
    if (meta) {
      const open = ALL.filter(t => t.status !== 'empty' || t.id === id);
      const i = open.findIndex(t => t.id === id);
      const cell = (t, cls, label) => t
        ? h('a', { class: cls, href: t.id + '.html' }, h('small', { text: label }), t.title)
        : h('span', { class: cls, 'aria-hidden': 'true' });
      main.append(h('nav', { class: 'pager', 'aria-label': 'Соседние темы' }, cell(open[i - 1], 'prev', '← Назад'), cell(open[i + 1], 'next', 'Дальше →')));
    }

    if ('IntersectionObserver' in window) {
      const links = $$('a[data-toc]', side);
      const io = new IntersectionObserver(entries => entries.forEach(en => {
        if (en.isIntersecting) links.forEach(a => a.classList.toggle('active', a.dataset.toc === en.target.id));
      }), { rootMargin: '-15% 0px -75% 0px' });
      steps.concat(cards).forEach(x => io.observe(x));
    }

    const openFromHash = () => {
      const target = location.hash.length > 1 && document.getElementById(decodeURIComponent(location.hash.slice(1)));
      const card = target && target.closest('article.q');
      if (card) { card.hidden = false; setOpen(card, true); }
      if (target) requestAnimationFrame(() => (card || target).scrollIntoView());
    };
    window.addEventListener('hashchange', openFromHash);

    applyLevel();
    cards.forEach(paint);
    if (cards.length) { progress(); filter(); }
    openFromHash();
  };

  /* ---------- запуск ---------- */
  topbar();
  if (PAGE === 'topic') initTopic();
  if (PAGE === 'index') initIndex();
  enhanceContent();
})();
