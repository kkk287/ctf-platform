/* 我的战绩：统计、解题记录、赛事积分、战队创建/加入/退出/成员管理 */
(function () {
  WO.renderHeader('profile');
  let profile = null;

  async function refreshUserCache() {
    try {
      const me = await WO.api('/auth/me');
      localStorage.setItem('ctf_user', JSON.stringify(me));
    } catch (e) { /* 忽略：下次请求会处理 401 */ }
  }

  function renderStats(p) {
    document.getElementById('stat-points').textContent = p.totalPoints;
    document.getElementById('stat-solves').textContent = p.solves.length;
    document.getElementById('stat-hint').textContent = p.hintCostTotal;
    document.getElementById('stat-team').textContent = p.team ? p.team.name : '未加入';
    document.getElementById('stat-role').textContent = p.user.roleLabel + ' · @' + p.user.username;
    document.getElementById('page-sub').textContent =
        `${p.user.displayName} 的战绩 · 个人积分与赛事榜/练习榜分别统计`;
  }

  function renderSolves(p) {
    const body = document.getElementById('solve-body');
    if (!p.solves.length) {
      body.innerHTML = '<tr><td colspan="6" class="muted" style="padding:28px;text-align:center">还没有解题记录，去题目列表拿下一血。</td></tr>';
      return;
    }
    body.innerHTML = p.solves.map(s => `
      <tr>
        <td><a href="/challenge.html?id=${encodeURIComponent(s.challengeId)}&eventId=${encodeURIComponent(s.eventId)}">
          ${WO.escapeHtml(s.challengeName)}</a>
          <span class="cat-tag ${WO.categoryClass(s.category)}" style="margin-left:6px">${WO.escapeHtml(s.categoryLabel)}</span></td>
        <td class="small">${WO.escapeHtml(s.eventName)}</td>
        <td>${WO.badge(s.eventTypeLabel, s.eventType === 'PRACTICE' ? 'badge-green' : 'badge-cyan')}</td>
        <td class="mono small">${s.basePoints} + ${s.bloodBonus} - ${s.hintCost}
          = <span style="color:var(--cyan)">${s.points}</span></td>
        <td>${s.bloodRank && s.bloodRank !== 'NONE' ? WO.badge(s.bloodRankLabel, 'badge-amber') : '<span class="muted">—</span>'}</td>
        <td class="muted small">${WO.formatDate(s.solvedAt, true)}</td>
      </tr>`).join('');
  }

  function renderEventStats(p) {
    const box = document.getElementById('event-stats');
    if (!p.events.length) {
      box.innerHTML = '<div class="muted small">暂无赛事积分。</div>';
      return;
    }
    box.innerHTML = p.events.map(e => `
      <div style="padding:10px 0;border-bottom:1px solid var(--border-soft)">
        <div style="display:flex;justify-content:space-between;gap:10px">
          <a href="/scoreboard.html?eventId=${encodeURIComponent(e.eventId)}">${WO.escapeHtml(e.eventName)}</a>
          <span class="points" style="font-size:14px">${e.points}</span>
        </div>
        <div class="muted small" style="margin-top:3px">
          ${WO.badge(e.eventTypeLabel, e.eventType === 'PRACTICE' ? 'badge-green' : 'badge-cyan')}
          · ${e.solveCount} 题 · 名次 #${e.rank || '—'}
        </div>
      </div>`).join('');
  }

  function renderAccount(p) {
    document.getElementById('account-box').innerHTML = `
      <div class="stat-line"><span>用户名</span><strong style="font-size:13px">${WO.escapeHtml(p.user.username)}</strong></div>
      <div class="stat-line" style="margin-top:8px"><span>昵称</span><strong style="font-size:13px">${WO.escapeHtml(p.user.displayName)}</strong></div>
      <div class="stat-line" style="margin-top:8px"><span>角色</span>
        <span>${WO.badge(p.user.roleLabel, p.user.role === 'ADMIN' ? 'badge-purple' : 'badge-cyan')}</span></div>
      <div class="stat-line" style="margin-top:8px"><span>账号状态</span>
        <span>${p.user.banned ? WO.badge('已封禁', 'badge-red') : WO.badge('正常', 'badge-green')}</span></div>`;
  }

  function renderTeam(p) {
    const box = document.getElementById('team-box');
    if (!p.team) {
      box.innerHTML = `
        <div class="tabs" id="team-mode" style="margin-bottom:14px">
          <button type="button" class="active" data-mode="create">创建战队</button>
          <button type="button" data-mode="join">加入战队</button>
        </div>
        <div id="team-create">
          <div class="field"><label>战队名称</label>
            <input class="input" id="team-name" maxlength="40" placeholder="2-40 个字符"></div>
          <button class="btn btn-primary btn-block" id="create-team-btn" type="button">创建并成为队长</button>
        </div>
        <div id="team-join" style="display:none">
          <div class="field"><label>邀请码</label>
            <input class="input mono" id="team-code" maxlength="16" placeholder="例如 0X00SEC1"></div>
          <button class="btn btn-primary btn-block" id="join-team-btn" type="button">加入战队</button>
        </div>
        <div class="muted small" style="margin-top:10px">演示邀请码：0X00SEC1（0x00sec）、BYTERAID（Byte Raiders）</div>`;
      document.getElementById('team-mode').addEventListener('click', e => {
        const btn = e.target.closest('button[data-mode]');
        if (!btn) return;
        box.querySelectorAll('#team-mode button').forEach(b => b.classList.toggle('active', b === btn));
        document.getElementById('team-create').style.display = btn.dataset.mode === 'create' ? '' : 'none';
        document.getElementById('team-join').style.display = btn.dataset.mode === 'join' ? '' : 'none';
      });
      document.getElementById('create-team-btn').addEventListener('click', async () => {
        const name = document.getElementById('team-name').value.trim();
        if (name.length < 2) return WO.toast('战队名至少 2 个字符', 'error');
        await teamAction(async () => WO.api('/teams', { method: 'POST', body: { name } }), '战队创建成功');
      });
      document.getElementById('join-team-btn').addEventListener('click', async () => {
        const inviteCode = document.getElementById('team-code').value.trim();
        if (!inviteCode) return WO.toast('请输入邀请码', 'error');
        await teamAction(async () => WO.api('/teams/join', { method: 'POST', body: { inviteCode } }), '已加入战队');
      });
      return;
    }

    const isCaptain = p.team.captainId === p.user.id;
    box.innerHTML = `
      <div style="display:flex;justify-content:space-between;gap:10px;align-items:flex-start">
        <div>
          <div style="font-size:17px;font-weight:700">${WO.escapeHtml(p.team.name)}</div>
          <div class="muted small">队长 ${WO.escapeHtml(p.team.captainName)} · ${p.team.memberCount} 名成员</div>
        </div>
        ${isCaptain ? WO.badge('我是队长', 'badge-amber') : ''}
      </div>
      <div class="attachment" style="margin-top:12px">
        <div class="file">
          <div class="file-icon">KEY</div>
          <div><div class="muted small">邀请码</div>
            <div class="mono" id="invite-code">${WO.escapeHtml(p.team.inviteCode)}</div></div>
        </div>
        <button class="btn btn-sm" id="copy-code-btn" type="button">复制</button>
      </div>
      <div style="margin-top:14px">
        <div class="muted small" style="margin-bottom:6px">成员</div>
        ${p.team.members.map(m => `
          <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;
                      padding:7px 0;border-bottom:1px solid var(--border-soft)">
            <div>
              <span style="font-weight:600">${WO.escapeHtml(m.displayName)}</span>
              <span class="muted small mono">@${WO.escapeHtml(m.username)}</span>
              ${m.captain ? WO.badge('队长', 'badge-amber') : ''}
            </div>
            ${isCaptain && !m.captain
          ? `<button class="btn btn-sm btn-danger" data-remove="${WO.escapeHtml(m.id)}" type="button">移除</button>` : ''}
          </div>`).join('')}
      </div>
      <button class="btn btn-danger btn-block" id="leave-team-btn" type="button" style="margin-top:14px">退出战队</button>`;

    document.getElementById('copy-code-btn').addEventListener('click', async () => {
      const code = p.team.inviteCode;
      try {
        await navigator.clipboard.writeText(code);
        WO.toast('邀请码已复制');
      } catch (e) {
        window.prompt('复制邀请码', code);
      }
    });
    document.getElementById('leave-team-btn').addEventListener('click', async () => {
      if (!window.confirm(isCaptain ? '队长退出后，队长将自动移交给最早加入的成员。确认退出？' : '确认退出当前战队？')) return;
      await teamAction(() => WO.api('/teams/leave', { method: 'POST' }), '已退出战队');
    });
    box.querySelectorAll('button[data-remove]').forEach(btn => {
      btn.addEventListener('click', async () => {
        if (!window.confirm('确认将该成员移出战队？')) return;
        await teamAction(() => WO.api('/teams/my/members/' + encodeURIComponent(btn.dataset.remove),
            { method: 'DELETE' }), '成员已移出');
      });
    });
  }

  async function teamAction(action, successMessage) {
    try {
      await action();
      WO.toast(successMessage, 'success');
      await refreshUserCache();
      await loadProfile();
      WO.renderHeader('profile');
    } catch (err) {
      WO.toast(err.message, 'error');
    }
  }

  async function loadProfile() {
    try {
      profile = await WO.api('/me/profile');
      renderStats(profile);
      renderSolves(profile);
      renderEventStats(profile);
      renderAccount(profile);
      renderTeam(profile);
    } catch (err) {
      WO.toast(err.message, 'error');
    }
  }

  document.getElementById('refresh-btn').addEventListener('click', () => {
    loadProfile();
    WO.toast('战绩已刷新');
  });

  (async function init() {
    await refreshUserCache();
    await loadProfile();
    WO.renderHeader('profile');
  })();
})();
