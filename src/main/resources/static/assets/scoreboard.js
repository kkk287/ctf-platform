/* 排行榜：赛事/练习切换、个人榜/战队榜、Top N 与我的名次 */
(function () {
  const user = WO.renderHeader('scoreboard');
  const eventTabs = document.getElementById('event-tabs');
  const boardTabs = document.getElementById('board-tabs');
  const podium = document.getElementById('podium');
  const head = document.getElementById('board-head');
  const body = document.getElementById('board-body');
  const myRank = document.getElementById('my-rank');
  const timeline = document.getElementById('recent-timeline');
  const limitSelect = document.getElementById('limit-select');

  let events = [];
  let currentEventId = WO.query('eventId');
  let type = WO.query('type') === 'TEAM' ? 'TEAM' : 'USER';
  let limit = 20;
  let timer = null;

  function renderEventTabs() {
    eventTabs.innerHTML = events.map(e => {
      const blocked = e.type === 'COMPETITION' && e.status === 'NOT_STARTED' && user.role !== 'ADMIN';
      return `<button type="button" class="${e.id === currentEventId ? 'active' : ''}"
                data-event="${WO.escapeHtml(e.id)}"
                ${blocked ? 'disabled title="赛事尚未开始，榜单暂不可见"' : ''}>
          [${WO.escapeHtml(e.typeLabel)}] ${WO.escapeHtml(e.name)}
          <span style="opacity:.7">· ${WO.escapeHtml(e.statusLabel)}</span>
        </button>`;
    }).join('');
  }

  async function loadEvents() {
    events = await WO.api('/events');
    if (!currentEventId && events.length) {
      const ongoing = events.find(e => e.type === 'COMPETITION' && e.status === 'ONGOING');
      const practice = events.find(e => e.type === 'PRACTICE');
      currentEventId = (ongoing || practice || events[0]).id;
    }
    renderEventTabs();
  }

  function rankCell(rank) {
    if (rank <= 3) {
      return `<span class="rank-medal rank-${rank}">#${rank}</span>`;
    }
    return `<span class="muted mono">#${rank}</span>`;
  }

  function renderPodium(entries) {
    if (!entries || entries.length < 1) {
      podium.innerHTML = '';
      return;
    }
    const classes = ['first', 'second', 'third'];
    podium.innerHTML = entries.slice(0, 3).map((e, i) => `
      <div class="podium-item ${classes[i]}">
        <div class="place">RANK #${e.rank}</div>
        <div class="name">${WO.escapeHtml(e.name)}</div>
        <div class="pts">${e.totalPoints} PTS</div>
        <div class="tag-line">${type === 'TEAM'
      ? WO.escapeHtml(e.subtitle || '')
      : WO.escapeHtml(e.teamName || '个人参赛')} · ${e.solveCount} 题</div>
      </div>`).join('');
  }

  function renderTable(data) {
    const isTeam = data.type === 'TEAM';
    document.getElementById('table-title').textContent = isTeam ? '战队排行' : '个人排行';
    document.getElementById('page-sub').textContent =
        `${data.event.name} · ${data.event.typeLabel} · ${data.event.statusLabel}`
        + (isTeam ? ' · 战队榜按成员得分求和，同题只计最高' : ' · 按总分降序，同分按最后得分时间升序');

    head.innerHTML = isTeam
        ? '<tr><th>名次</th><th>战队</th><th>成员</th><th>解题数</th><th>总分</th><th>最后得分</th></tr>'
        : '<tr><th>名次</th><th>选手</th><th>战队</th><th>解题数</th><th>总分</th><th>最后得分</th></tr>';

    if (!data.entries.length) {
      body.innerHTML = '<tr><td colspan="6" class="muted" style="padding:28px;text-align:center">该榜单暂无得分记录</td></tr>';
    } else {
      body.innerHTML = data.entries.map(e => `
        <tr>
          <td>${rankCell(e.rank)}</td>
          <td>
            <div style="font-weight:600">${WO.escapeHtml(e.name)}</div>
            ${e.subtitle ? `<div class="muted small mono">${WO.escapeHtml(e.subtitle)}</div>` : ''}
          </td>
          <td class="muted small">${isTeam ? (e.memberCount + ' 人') : WO.escapeHtml(e.teamName || '—')}</td>
          <td class="mono">${e.solveCount}</td>
          <td><span class="points">${e.totalPoints}<small>PTS</small></span></td>
          <td class="muted small">${WO.formatShort(e.lastSolveAt)}</td>
        </tr>`).join('');
    }

    if (data.me) {
      myRank.innerHTML = `
        <div class="my-rank">
          <div>
            <div class="muted small">${isTeam ? '我的战队名次' : '我的名次'}</div>
            <div style="font-weight:600;margin-top:3px">${WO.escapeHtml(data.me.name)}</div>
          </div>
          <div style="display:flex;gap:28px;align-items:center">
            <div style="text-align:right">
              <div class="muted small">总分</div>
              <div class="mono" style="font-size:18px;color:var(--cyan)">${data.me.totalPoints}</div>
            </div>
            <div style="text-align:right">
              <div class="muted small">解题</div>
              <div class="mono" style="font-size:18px">${data.me.solveCount}</div>
            </div>
            <div class="rank-no">#${data.me.rank}</div>
          </div>
        </div>`;
    } else {
      myRank.innerHTML = `<div class="my-rank">
        <div><div class="muted small">${isTeam ? '我的战队名次' : '我的名次'}</div>
        <div style="font-weight:600;margin-top:3px">${isTeam ? '尚未加入战队' : '尚未获得积分'}</div></div>
        <div class="rank-no">—</div></div>`;
    }
  }

  function renderRecent(items) {
    if (!items || !items.length) {
      timeline.innerHTML = '<div class="muted small">暂无解题动态。</div>';
      return;
    }
    timeline.innerHTML = items.map(s => `
      <div class="timeline-item">
        <strong>${WO.escapeHtml(s.displayName)}
          ${s.bloodRank ? WO.badge(s.bloodRankLabel, 'badge-amber') : ''}</strong>
        <span>解出 <span class="mono">${WO.escapeHtml(s.challengeName)}</span>
          · ${s.points} PTS · ${WO.relativeTime(s.solvedAt)}</span>
      </div>`).join('');
  }

  async function load() {
    const params = new URLSearchParams();
    if (currentEventId) params.set('eventId', currentEventId);
    params.set('type', type);
    params.set('limit', String(limit));
    try {
      const [data, recent] = await Promise.all([
        WO.api('/scoreboard?' + params.toString()),
        WO.api('/scoreboard/recent?limit=12' + (currentEventId ? '&eventId=' + encodeURIComponent(currentEventId) : ''))
      ]);
      currentEventId = data.event.id;
      renderEventTabs();
      renderPodium(data.entries);
      renderTable(data);
      renderRecent(recent);
    } catch (err) {
      body.innerHTML = `<tr><td colspan="6" class="muted" style="padding:28px;text-align:center">
        ${WO.escapeHtml(err.message)} <span class="error-code">code ${err.code || '—'}</span></td></tr>`;
      podium.innerHTML = '';
      myRank.innerHTML = '';
      timeline.innerHTML = '';
    }
  }

  eventTabs.addEventListener('click', e => {
    const btn = e.target.closest('button[data-event]');
    if (!btn || btn.disabled) return;
    currentEventId = btn.dataset.event;
    const url = new URL(window.location.href);
    url.searchParams.set('eventId', currentEventId);
    history.replaceState(null, '', url);
    load();
  });

  boardTabs.addEventListener('click', e => {
    const btn = e.target.closest('button[data-type]');
    if (!btn) return;
    type = btn.dataset.type;
    boardTabs.querySelectorAll('button').forEach(b => b.classList.toggle('active', b === btn));
    const url = new URL(window.location.href);
    url.searchParams.set('type', type);
    history.replaceState(null, '', url);
    load();
  });

  limitSelect.addEventListener('change', () => {
    limit = Number(limitSelect.value);
    load();
  });

  document.getElementById('refresh-btn').addEventListener('click', () => {
    loadEvents().then(load);
    WO.toast('排行榜已刷新');
  });

  (async function init() {
    boardTabs.querySelectorAll('button').forEach(b => {
      b.classList.toggle('active', b.dataset.type === type);
    });
    try {
      await loadEvents();
      await load();
    } catch (err) {
      WO.toast(err.message, 'error');
    }
    timer = setInterval(() => {
      if (!document.hidden) load();
    }, 30000);
  })();

  window.addEventListener('beforeunload', () => timer && clearInterval(timer));
})();
