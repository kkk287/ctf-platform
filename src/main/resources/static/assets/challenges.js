/* 题目列表页：赛事/练习切换、分类筛选、搜索、已解出状态 */
(function () {
  const user = WO.renderHeader('challenges');
  const eventTabs = document.getElementById('event-tabs');
  const grid = document.getElementById('challenge-grid');
  const searchInput = document.getElementById('search-input');
  const solvedFilter = document.getElementById('solved-filter');
  const categoryTabs = document.getElementById('category-tabs');
  const announcementSlot = document.getElementById('announcement-slot');

  let events = [];
  let currentEventId = WO.query('eventId');
  let category = '';
  let onlyUnsolved = false;
  let keyword = '';
  let currentEvent = null;
  let countdownTimer = null;

  function renderEventTabs() {
    eventTabs.innerHTML = events.map(e => {
      const blocked = e.type === 'COMPETITION' && e.status === 'NOT_STARTED' && user.role !== 'ADMIN';
      return `<button type="button" class="${e.id === currentEventId ? 'active' : ''}"
                data-event="${WO.escapeHtml(e.id)}"
                ${blocked ? 'disabled title="赛事尚未开始，题目暂不可见"' : ''}>
          <span class="mono" style="font-size:10.5px;opacity:.75">[${WO.escapeHtml(e.typeLabel)}]</span>
          ${WO.escapeHtml(e.name)}
          <span style="opacity:.7">· ${WO.escapeHtml(e.statusLabel)}</span>
        </button>`;
    }).join('');
  }

  async function loadEvents() {
    try {
      events = await WO.api('/events');
      if (!currentEventId && events.length) {
        const ongoing = events.find(e => e.type === 'COMPETITION' && e.status === 'ONGOING');
        const practice = events.find(e => e.type === 'PRACTICE');
        currentEventId = (ongoing || practice || events[0]).id;
      }
      renderEventTabs();
    } catch (err) {
      eventTabs.innerHTML = '';
      WO.toast(err.message, 'error');
    }
  }

  function renderAnnouncements(items) {
    if (!items || !items.length) {
      announcementSlot.innerHTML = '';
      return;
    }
    announcementSlot.innerHTML = items.slice(0, 2).map(a => `
      <div class="announcement">
        <span class="tag">NOTICE</span>
        <div><strong>${WO.escapeHtml(a.title)}</strong> · ${WO.escapeHtml(a.content)}
          <span class="muted small">（${WO.formatShort(a.createdAt)}）</span></div>
      </div>`).join('');
  }

  async function loadAnnouncements() {
    try {
      renderAnnouncements(await WO.api('/announcements'));
    } catch (e) {
      announcementSlot.innerHTML = '';
    }
  }

  function renderHero(event, challenges, board) {
    currentEvent = event;
    document.getElementById('hero-title').textContent = event.name;
    document.getElementById('hero-desc').textContent = event.description || '暂无赛事描述。';
    document.getElementById('hero-badges').innerHTML = [
      WO.badge(event.typeLabel, WO.eventTypeClass(event.type)),
      WO.badge(event.statusLabel, WO.eventStatusClass(event.status)),
      event.type === 'COMPETITION'
          ? WO.badge('限时计分', 'badge-purple')
          : WO.badge('长期开放 · 独立榜单', 'badge-green')
    ].join(' ');

    const total = challenges.length;
    const solved = challenges.filter(c => c.solved).length;
    const me = board && board.me;
    const points = me ? me.totalPoints : 0;
    const rank = me ? me.rank : 0;
    const deadline = event.type === 'PRACTICE' ? '长期开放'
        : event.status === 'ENDED' ? '已结束' : WO.countdown(event.endAt);

    document.getElementById('hero-meta').innerHTML = `
      <span class="muted small mono">EVENT_ID ${WO.escapeHtml(event.id.slice(0, 8))}</span>
      ${event.startAt ? `<span class="muted small">开始 ${WO.formatShort(event.startAt)}</span>` : ''}
      ${event.endAt ? `<span class="muted small">结束 ${WO.formatShort(event.endAt)}</span>` : ''}`;

    document.getElementById('hero-stats').innerHTML = `
      <div class="stat-line"><span>题目数量</span><strong>${total}</strong></div>
      <div class="stat-line"><span>我的进度</span><strong>${solved} / ${total}</strong></div>
      <div class="stat-line"><span>我的积分</span><strong>${points}</strong></div>
      <div class="stat-line"><span>${event.type === 'PRACTICE' ? '开放状态' : '截止时间'}</span><strong id="countdown-value">${deadline}</strong></div>
      ${rank ? `<div class="stat-line"><span>当前名次</span><strong>#${rank}</strong></div>` : ''}`;

    if (countdownTimer) clearInterval(countdownTimer);
    if (event.type === 'COMPETITION' && event.status === 'ONGOING') {
      countdownTimer = setInterval(() => {
        const el = document.getElementById('countdown-value');
        if (el) el.textContent = WO.countdown(event.endAt);
      }, 30000);
    }
  }

  function renderChallenges(items) {
    if (!items.length) {
      grid.innerHTML = `<div class="empty" style="grid-column:1/-1">
        当前筛选条件下没有题目。<br><span class="small">试试切换赛事、分类或清空搜索关键词。</span></div>`;
      return;
    }
    grid.innerHTML = items.map(c => `
      <article class="challenge-card ${c.solved ? 'solved' : ''}" data-id="${WO.escapeHtml(c.id)}">
        <div class="challenge-top">
          <span class="cat-tag ${WO.categoryClass(c.category)}">${WO.escapeHtml(c.categoryLabel)}</span>
          <span class="badge ${WO.difficultyClass(c.difficulty)}">${WO.escapeHtml(c.difficultyLabel)}</span>
        </div>
        <div class="challenge-name">${WO.escapeHtml(c.name)}</div>
        <div class="challenge-desc">${WO.escapeHtml(c.description || '暂无描述')}</div>
        <div class="challenge-bottom">
          <span class="points">${c.currentPoints}<small>PTS</small></span>
          <span class="muted small">${c.solveCount} 解出${c.hintCount ? ' · ' + c.hintCount + ' 提示' : ''}${c.hasAttachment ? ' · 附件' : ''}</span>
          ${c.solved ? '<span class="solved-mark">✓ 已解出</span>' : ''}
        </div>
      </article>`).join('');
    grid.querySelectorAll('.challenge-card').forEach(card => {
      card.addEventListener('click', () => {
        window.location.href = '/challenge.html?id=' + encodeURIComponent(card.dataset.id)
            + '&eventId=' + encodeURIComponent(currentEventId);
      });
    });
  }

  async function loadChallenges() {
    grid.innerHTML = '<div class="loading">加载题目</div>';
    const params = new URLSearchParams();
    if (currentEventId) params.set('eventId', currentEventId);
    if (category) params.set('category', category);
    if (onlyUnsolved) params.set('solved', 'false');
    if (keyword) params.set('keyword', keyword);
    const qs = params.toString() ? '?' + params.toString() : '';

    try {
      const boardQuery = '/scoreboard?type=USER&limit=1'
          + (currentEventId ? '&eventId=' + encodeURIComponent(currentEventId) : '');
      const [data, board] = await Promise.all([
        WO.api('/challenges' + qs),
        WO.api(boardQuery).catch(() => null)
      ]);
      currentEventId = data.event.id;
      renderEventTabs();
      renderHero(data.event, data.challenges, board);
      renderChallenges(data.challenges);
    } catch (err) {
      grid.innerHTML = `<div class="empty" style="grid-column:1/-1">
        ${WO.escapeHtml(err.message)}
        <div class="small" style="margin-top:8px">错误码 ${err.code || '—'}；未开始的赛事对选手不可见。</div></div>`;
    }
  }

  eventTabs.addEventListener('click', e => {
    const btn = e.target.closest('button[data-event]');
    if (!btn || btn.disabled) return;
    currentEventId = btn.dataset.event;
    const url = new URL(window.location.href);
    url.searchParams.set('eventId', currentEventId);
    history.replaceState(null, '', url);
    loadChallenges();
  });

  categoryTabs.addEventListener('click', e => {
    const btn = e.target.closest('button[data-cat]');
    if (!btn) return;
    category = btn.dataset.cat;
    categoryTabs.querySelectorAll('button').forEach(b => b.classList.toggle('active', b === btn));
    loadChallenges();
  });

  solvedFilter.addEventListener('change', () => {
    onlyUnsolved = solvedFilter.checked;
    loadChallenges();
  });

  searchInput.addEventListener('input', WO.debounce(() => {
    keyword = searchInput.value.trim();
    loadChallenges();
  }, 350));

  document.getElementById('refresh-btn').addEventListener('click', () => {
    loadEvents().then(loadChallenges);
    loadAnnouncements();
    WO.toast('已刷新');
  });

  (async function init() {
    await loadEvents();
    await loadChallenges();
    loadAnnouncements();
  })();
})();
