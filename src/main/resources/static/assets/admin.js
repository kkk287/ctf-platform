/* 管理后台：题目/赛事/用户与角色/提交审计/审计日志/异常/公告 */
(function () {
  const user = WO.renderHeader('admin');
  const tabs = document.getElementById('admin-tabs');
  const content = document.getElementById('admin-content');
  const mask = document.getElementById('modal-mask');
  const modalTitle = document.getElementById('modal-title');
  const modalBody = document.getElementById('modal-body');
  const modalFoot = document.getElementById('modal-foot');
  const CATEGORIES = [['WEB', 'Web'], ['PWN', 'Pwn'], ['CRYPTO', 'Crypto'], ['MISC', 'Misc'], ['REVERSE', 'Reverse']];
  const DIFFICULTIES = [['EASY', '简单'], ['MEDIUM', '中等'], ['HARD', '困难']];
  const ROLES = [['USER', '选手'], ['JUDGE', '裁判'], ['TEACHER', '教师'], ['OBSERVER', '观摩'], ['ADMIN', '管理员']];

  let currentTab = 'challenges';
  let eventsCache = [];
  let userPage = 0;
  let userKeyword = '';
  let subPage = 0;
  let subResult = '';
  let auditPage = 0;
  let auditAction = '';

  const isAdmin = user && user.role === 'ADMIN';
  if (!user || (user.role !== 'ADMIN' && user.role !== 'JUDGE')) {
    tabs.style.display = 'none';
    content.innerHTML = `<div class="empty">管理后台仅对管理员 / 裁判开放。<br>
      <a href="/index.html" style="display:inline-block;margin-top:10px">返回题目列表</a></div>`;
    return;
  }

  function openModal(title, body, foot) {
    modalTitle.textContent = title;
    modalBody.innerHTML = body;
    modalFoot.innerHTML = foot || '';
    mask.classList.add('show');
  }

  function closeModal() {
    mask.classList.remove('show');
    modalBody.innerHTML = '';
    modalFoot.innerHTML = '';
  }

  document.getElementById('modal-close').addEventListener('click', closeModal);
  mask.addEventListener('mousedown', e => { if (e.target === mask) closeModal(); });
  document.addEventListener('keydown', e => { if (e.key === 'Escape') closeModal(); });

  function cancelButton() {
    const btn = document.getElementById('modal-cancel');
    if (btn) btn.addEventListener('click', closeModal);
  }

  function pager(container, result, go) {
    if (!container) return;
    if (result.totalPages <= 1) {
      container.innerHTML = `<span>共 ${result.total} 条</span>`;
      return;
    }
    container.innerHTML = `
      <span>第 ${result.page + 1} / ${result.totalPages} 页 · 共 ${result.total} 条</span>
      <div class="btns">
        <button class="btn btn-sm" type="button" id="page-prev" ${result.page <= 0 ? 'disabled' : ''}>上一页</button>
        <button class="btn btn-sm" type="button" id="page-next"
          ${result.page >= result.totalPages - 1 ? 'disabled' : ''}>下一页</button>
      </div>`;
    const prev = container.querySelector('#page-prev');
    const next = container.querySelector('#page-next');
    if (prev) prev.addEventListener('click', () => go(result.page - 1));
    if (next) next.addEventListener('click', () => go(result.page + 1));
  }

  function toLocalInput(value) {
    if (!value) return '';
    return String(value).slice(0, 16);
  }

  async function ensureEvents() {
    eventsCache = await WO.api('/admin/events');
    return eventsCache;
  }

  function eventOptions(selectedId) {
    return eventsCache.map(e =>
        `<option value="${WO.escapeHtml(e.id)}" ${e.id === selectedId ? 'selected' : ''}>
          [${WO.escapeHtml(e.typeLabel)}] ${WO.escapeHtml(e.name)}</option>`).join('');
  }

  // ---------- 题目 ----------

  async function renderChallenges() {
    content.innerHTML = `
      <div class="toolbar" style="margin-top:0">
        <button class="btn btn-primary btn-sm" id="new-challenge" type="button">+ 新建题目</button>
        <div class="spacer"></div>
        <span class="muted small">flag 只保存加盐哈希，编辑时留空表示不修改</span>
      </div>
      <div id="challenge-table"><div class="loading">加载题目</div></div>`;
    document.getElementById('new-challenge').addEventListener('click', () => openChallengeModal(null));
    try {
      const list = await WO.api('/admin/challenges');
      if (!list.length) {
        document.getElementById('challenge-table').innerHTML = '<div class="empty">还没有题目，点击“新建题目”开始。</div>';
        return;
      }
      document.getElementById('challenge-table').innerHTML = `
        <div class="table-wrap">
          <table class="data">
            <thead><tr>
              <th>题目</th><th>赛事/练习</th><th>分值</th><th>可见</th><th>Flag</th>
              <th>解出</th><th>附件</th><th>操作</th>
            </tr></thead>
            <tbody>${list.map(c => `
              <tr>
                <td>
                  <div style="font-weight:600">${WO.escapeHtml(c.name)}</div>
                  <div class="small">
                    <span class="cat-tag ${WO.categoryClass(c.category)}">${WO.escapeHtml(c.categoryLabel)}</span>
                    <span class="badge ${WO.difficultyClass(c.difficulty)}">${WO.escapeHtml(c.difficultyLabel)}</span>
                  </div>
                </td>
                <td class="small">${WO.escapeHtml(c.eventName)}</td>
                <td class="mono">${c.currentPoints}<div class="muted small">初始 ${c.initialPoints}</div></td>
                <td>${c.visible ? WO.badge('可见', 'badge-green') : WO.badge('隐藏', 'badge-muted')}</td>
                <td>${c.flagConfigured ? WO.badge('已配置', 'badge-cyan') : WO.badge('未配置', 'badge-red')}</td>
                <td class="mono">${c.solveCount}</td>
                <td class="small">${c.attachmentName ? WO.escapeHtml(c.attachmentName) : '<span class="muted">—</span>'}</td>
                <td class="nowrap">
                  <button class="btn btn-sm" type="button" data-edit="${WO.escapeHtml(c.id)}">编辑</button>
                  <button class="btn btn-sm btn-danger" type="button" data-del="${WO.escapeHtml(c.id)}">删除</button>
                </td>
              </tr>`).join('')}</tbody>
          </table>
        </div>`;
      document.getElementById('challenge-table').querySelectorAll('button[data-edit]').forEach(btn => {
        btn.addEventListener('click', () => {
          const target = list.find(c => c.id === btn.dataset.edit);
          openChallengeModal(target);
        });
      });
      document.getElementById('challenge-table').querySelectorAll('button[data-del]').forEach(btn => {
        btn.addEventListener('click', async () => {
          const target = list.find(c => c.id === btn.dataset.del);
          if (!window.confirm('确认删除题目「' + target.name + '」？该操作不可恢复。')) return;
          try {
            await WO.api('/admin/challenges/' + encodeURIComponent(target.id), { method: 'DELETE' });
            WO.toast('题目已删除', 'success');
            renderChallenges();
          } catch (err) {
            WO.toast(err.message, 'error');
          }
        });
      });
    } catch (err) {
      document.getElementById('challenge-table').innerHTML =
          `<div class="empty">${WO.escapeHtml(err.message)} <span class="error-code">code ${err.code || '—'}</span></div>`;
    }
  }

  async function openChallengeModal(challenge) {
    try {
      await ensureEvents();
    } catch (err) {
      WO.toast(err.message, 'error');
      return;
    }
    const isEdit = !!challenge;
    const c = challenge || {};
    const body = `
      <div class="form-row">
        <div class="field">
          <label>所属赛事/练习</label>
          <select class="select" id="ch-event">${eventOptions(c.eventId)}</select>
        </div>
        <div class="field">
          <label>分类</label>
          <select class="select" id="ch-category">
            ${CATEGORIES.map(([v, l]) => `<option value="${v}" ${c.category === v ? 'selected' : ''}>${l}</option>`).join('')}
          </select>
        </div>
        <div class="field">
          <label>难度</label>
          <select class="select" id="ch-difficulty">
            ${DIFFICULTIES.map(([v, l]) => `<option value="${v}" ${c.difficulty === v ? 'selected' : ''}>${l}</option>`).join('')}
          </select>
        </div>
      </div>
      <div class="field">
        <label>题目名称</label>
        <input class="input" id="ch-name" maxlength="100" value="${WO.escapeHtml(c.name || '')}">
      </div>
      <div class="field">
        <label>题目描述</label>
        <textarea class="textarea" id="ch-description" maxlength="3000">${WO.escapeHtml(c.description || '')}</textarea>
      </div>
      <div class="form-row">
        <div class="field"><label>初始分值</label>
          <input class="input" id="ch-initial" type="number" min="10" max="10000" value="${c.initialPoints || 100}"></div>
        <div class="field"><label>最低分（可选）</label>
          <input class="input" id="ch-min" type="number" min="0" value="${c.minPoints ?? ''}" placeholder="默认初始分 10%"></div>
        <div class="field"><label>衰减值（可选）</label>
          <input class="input" id="ch-decay" type="number" min="0" value="${c.decayPoints ?? ''}" placeholder="默认 5"></div>
      </div>
      <div class="field">
        <label>flag ${isEdit ? '（留空保持原值）' : '（必填）'}</label>
        <input class="input mono" id="ch-flag" maxlength="200" placeholder="${isEdit ? '留空则不修改' : '例如 flag{demo_2026}'}" autocomplete="off">
        <div class="hint">提交时服务端使用加盐 SHA-256 比对，不保存明文。</div>
      </div>
      <div class="field">
        <label>附件（可选，最大 20MB）</label>
        <div class="inline-form">
          <input class="input" id="ch-file" type="file" style="flex:1;min-width:200px">
          ${c.attachmentName ? `<button class="btn btn-sm btn-danger" id="ch-del-attachment" type="button">删除现有附件</button>` : ''}
        </div>
        <div class="hint">${c.attachmentName ? '当前：' + WO.escapeHtml(c.attachmentName) : '附件保存在容器内上传目录，随数据卷持久化。'}</div>
      </div>
      <div class="field">
        <label class="checkbox"><input type="checkbox" id="ch-visible" ${c.visible === false ? '' : 'checked'}> 对选手可见</label>
      </div>
      ${isEdit ? `<div style="border-top:1px solid var(--border-soft);margin-top:8px;padding-top:14px">
        <p class="section-title">提示管理</p>
        <div id="ch-hints"><div class="muted small">加载提示…</div></div>
        <div class="inline-form" style="margin-top:10px">
          <input class="input" id="hint-content" maxlength="500" placeholder="提示内容" style="flex:1;min-width:180px">
          <input class="input" id="hint-cost" type="number" min="0" value="10" style="width:90px">
          <button class="btn btn-sm" id="add-hint" type="button">添加提示</button>
        </div>
      </div>` : ''}`;

    openModal(isEdit ? '编辑题目' : '新建题目', body,
        `<button class="btn" id="modal-cancel" type="button">取消</button>
         <button class="btn btn-primary" id="modal-save" type="button">${isEdit ? '保存' : '创建'}</button>`);
    cancelButton();

    async function refreshHints() {
      if (!isEdit) return;
      try {
        const detail = await WO.api('/challenges/' + encodeURIComponent(challenge.id));
        const box = document.getElementById('ch-hints');
        if (!box) return;
        if (!detail.hints || !detail.hints.length) {
          box.innerHTML = '<div class="muted small">暂无提示。</div>';
        } else {
          box.innerHTML = detail.hints.map(h => `
            <div class="hint" style="margin-bottom:8px">
              <div class="hint-head">
                <span>${WO.escapeHtml(h.content || '')} <span class="muted small">（扣 ${h.cost} 分）</span></span>
                <button class="btn btn-sm btn-danger" type="button" data-del-hint="${WO.escapeHtml(h.id)}">删除</button>
              </div>
            </div>`).join('');
          box.querySelectorAll('button[data-del-hint]').forEach(btn => {
            btn.addEventListener('click', async () => {
              try {
                await WO.api('/admin/challenges/' + encodeURIComponent(challenge.id)
                    + '/hints/' + encodeURIComponent(btn.dataset.delHint), { method: 'DELETE' });
                WO.toast('提示已删除', 'success');
                refreshHints();
              } catch (err) {
                WO.toast(err.message, 'error');
              }
            });
          });
        }
      } catch (err) {
        const box = document.getElementById('ch-hints');
        if (box) box.innerHTML = `<div class="muted small">${WO.escapeHtml(err.message)}</div>`;
      }
    }

    if (isEdit) {
      refreshHints();
      const addHint = document.getElementById('add-hint');
      addHint.addEventListener('click', async () => {
        const text = document.getElementById('hint-content').value.trim();
        const cost = Number(document.getElementById('hint-cost').value || 0);
        if (!text) return WO.toast('提示内容不能为空', 'error');
        try {
          await WO.api('/admin/challenges/' + encodeURIComponent(challenge.id) + '/hints', {
            method: 'POST', body: { content: text, cost }
          });
          document.getElementById('hint-content').value = '';
          WO.toast('提示已添加', 'success');
          refreshHints();
        } catch (err) {
          WO.toast(err.message, 'error');
        }
      });
      const delAttachment = document.getElementById('ch-del-attachment');
      if (delAttachment) {
        delAttachment.addEventListener('click', async () => {
          if (!window.confirm('确认删除该题目的附件？')) return;
          try {
            await WO.api('/admin/challenges/' + encodeURIComponent(challenge.id) + '/attachment',
                { method: 'DELETE' });
            WO.toast('附件已删除', 'success');
            closeModal();
            renderChallenges();
          } catch (err) {
            WO.toast(err.message, 'error');
          }
        });
      }
    }

    document.getElementById('modal-save').addEventListener('click', async () => {
      const name = document.getElementById('ch-name').value.trim();
      const initial = Number(document.getElementById('ch-initial').value);
      const flag = document.getElementById('ch-flag').value;
      const minRaw = document.getElementById('ch-min').value;
      const decayRaw = document.getElementById('ch-decay').value;
      if (!name) return WO.toast('题目名称不能为空', 'error');
      if (!isEdit && !flag.trim()) return WO.toast('新建题目必须设置 flag', 'error');
      if (!Number.isFinite(initial) || initial < 10) return WO.toast('初始分值至少为 10', 'error');

      const payload = {
        eventId: document.getElementById('ch-event').value,
        name,
        description: document.getElementById('ch-description').value,
        category: document.getElementById('ch-category').value,
        difficulty: document.getElementById('ch-difficulty').value,
        initialPoints: initial,
        minPoints: minRaw === '' ? null : Number(minRaw),
        decayPoints: decayRaw === '' ? null : Number(decayRaw),
        visible: document.getElementById('ch-visible').checked,
        flag: flag.trim() ? flag.trim() : null
      };
      const btn = document.getElementById('modal-save');
      btn.disabled = true;
      try {
        const saved = isEdit
            ? await WO.api('/admin/challenges/' + encodeURIComponent(challenge.id),
                { method: 'PUT', body: payload })
            : await WO.api('/admin/challenges', { method: 'POST', body: payload });
        const file = document.getElementById('ch-file').files[0];
        if (file) {
          const fd = new FormData();
          fd.append('file', file);
          await WO.api('/admin/challenges/' + encodeURIComponent(saved.id) + '/attachment',
              { method: 'POST', body: fd });
        }
        WO.toast(isEdit ? '题目已保存' : '题目已创建', 'success');
        closeModal();
        renderChallenges();
      } catch (err) {
        btn.disabled = false;
        WO.toast(err.message, 'error');
      }
    });
  }

  // ---------- 赛事 ----------

  async function renderEvents() {
    content.innerHTML = `
      <div class="toolbar" style="margin-top:0">
        <button class="btn btn-primary btn-sm" id="new-event" type="button">+ 新建赛事/练习区</button>
        <div class="spacer"></div>
        <span class="muted small">比赛到点截止；练习区长期开放且独立榜单</span>
      </div>
      <div id="event-table"><div class="loading">加载赛事</div></div>`;
    document.getElementById('new-event').addEventListener('click', () => openEventModal(null));
    try {
      const list = await WO.api('/admin/events');
      eventsCache = list;
      if (!list.length) {
        document.getElementById('event-table').innerHTML = '<div class="empty">还没有赛事或练习区。</div>';
        return;
      }
      document.getElementById('event-table').innerHTML = `
        <div class="table-wrap">
          <table class="data">
            <thead><tr><th>名称</th><th>类型</th><th>状态</th><th>时间</th><th>题目</th><th>操作</th></tr></thead>
            <tbody>${list.map(e => `
              <tr>
                <td><div style="font-weight:600">${WO.escapeHtml(e.name)}</div>
                  <div class="muted small">${WO.escapeHtml(e.description || '')}</div>
                  <div class="muted small mono">ns: ${WO.escapeHtml(e.namespace || '—')}</div></td>
                <td>${WO.badge(e.typeLabel, WO.eventTypeClass(e.type))}</td>
                <td>${WO.badge(e.statusLabel, WO.eventStatusClass(e.status))}</td>
                <td class="small mono">${e.type === 'PRACTICE' ? '长期开放'
          : WO.formatDate(e.startAt) + '<br>→ ' + WO.formatDate(e.endAt)}</td>
                <td class="mono">${e.challengeCount}<span class="muted small"> / 可见 ${e.visibleChallengeCount}</span></td>
                <td class="nowrap">
                  <button class="btn btn-sm" type="button" data-edit="${WO.escapeHtml(e.id)}">编辑</button>
                  <button class="btn btn-sm btn-danger" type="button" data-del="${WO.escapeHtml(e.id)}">删除</button>
                </td>
              </tr>`).join('')}</tbody>
          </table>
        </div>`;
      document.getElementById('event-table').querySelectorAll('button[data-edit]').forEach(btn => {
        btn.addEventListener('click', () => openEventModal(list.find(e => e.id === btn.dataset.edit)));
      });
      document.getElementById('event-table').querySelectorAll('button[data-del]').forEach(btn => {
        btn.addEventListener('click', async () => {
          const target = list.find(e => e.id === btn.dataset.del);
          if (!window.confirm('确认删除「' + target.name + '」？需先删除其下所有题目。')) return;
          try {
            await WO.api('/admin/events/' + encodeURIComponent(target.id), { method: 'DELETE' });
            WO.toast('已删除', 'success');
            renderEvents();
          } catch (err) {
            WO.toast(err.message, 'error');
          }
        });
      });
    } catch (err) {
      document.getElementById('event-table').innerHTML =
          `<div class="empty">${WO.escapeHtml(err.message)}</div>`;
    }
  }

  function openEventModal(event) {
    const isEdit = !!event;
    const e = event || {};
    openModal(isEdit ? '编辑赛事/练习区' : '新建赛事/练习区', `
      <div class="field"><label>名称</label>
        <input class="input" id="ev-name" maxlength="80" value="${WO.escapeHtml(e.name || '')}"></div>
      <div class="field"><label>命名空间（可选）</label>
        <input class="input mono" id="ev-namespace" maxlength="60" placeholder="留空默认与赛事 ID 相同"
               value="${WO.escapeHtml(e.namespace || '')}">
        <div class="hint">小写字母 / 数字 / 中划线，全平台唯一；用于动态靶机等外部系统对接，创建后不建议修改。</div></div>
      <div class="field"><label>类型</label>
        <select class="select" id="ev-type">
          <option value="COMPETITION" ${e.type === 'PRACTICE' ? '' : 'selected'}>赛事 COMPETITION（限时）</option>
          <option value="PRACTICE" ${e.type === 'PRACTICE' ? 'selected' : ''}>练习 Practice（长期开放）</option>
        </select></div>
      <div class="field"><label>描述</label>
        <textarea class="textarea" id="ev-description" maxlength="1000" style="min-height:90px">${WO.escapeHtml(e.description || '')}</textarea></div>
      <div class="form-row" id="ev-time-row">
        <div class="field"><label>开始时间</label>
          <input class="input" id="ev-start" type="datetime-local" value="${toLocalInput(e.startAt)}"></div>
        <div class="field"><label>结束时间</label>
          <input class="input" id="ev-end" type="datetime-local" value="${toLocalInput(e.endAt)}"></div>
      </div>
      <div class="hint" id="ev-time-hint">比赛必须设置开始/结束时间；未开始时题目对选手不可见，结束后只能查看不能提交。</div>`,
        `<button class="btn" id="modal-cancel" type="button">取消</button>
         <button class="btn btn-primary" id="modal-save" type="button">${isEdit ? '保存' : '创建'}</button>`);
    cancelButton();

    const typeSelect = document.getElementById('ev-type');
    const timeRow = document.getElementById('ev-time-row');
    const timeHint = document.getElementById('ev-time-hint');
    function syncType() {
      const practice = typeSelect.value === 'PRACTICE';
      timeRow.style.display = practice ? 'none' : '';
      timeHint.textContent = practice
          ? '练习区长期开放，不受时间限制，独立计分与独立榜单。'
          : '比赛必须设置开始/结束时间；未开始时题目对选手不可见，结束后只能查看不能提交。';
    }
    typeSelect.addEventListener('change', syncType);
    syncType();

    document.getElementById('modal-save').addEventListener('click', async () => {
      const type = typeSelect.value;
      const ns = document.getElementById('ev-namespace').value.trim();
      if (ns && !/^[a-z0-9][a-z0-9-]{1,59}$/.test(ns)) {
        return WO.toast('命名空间需以小写字母或数字开头，仅含小写字母/数字/中划线，长度 2-60', 'error');
      }
      const payload = {
        name: document.getElementById('ev-name').value.trim(),
        namespace: ns || null,
        description: document.getElementById('ev-description').value,
        type,
        startAt: type === 'PRACTICE' ? null : document.getElementById('ev-start').value,
        endAt: type === 'PRACTICE' ? null : document.getElementById('ev-end').value
      };
      if (!payload.name) return WO.toast('名称不能为空', 'error');
      if (type === 'COMPETITION' && (!payload.startAt || !payload.endAt)) {
        return WO.toast('比赛必须设置开始与结束时间', 'error');
      }
      const btn = document.getElementById('modal-save');
      btn.disabled = true;
      try {
        if (isEdit) {
          await WO.api('/admin/events/' + encodeURIComponent(event.id), { method: 'PUT', body: payload });
        } else {
          await WO.api('/admin/events', { method: 'POST', body: payload });
        }
        WO.toast(isEdit ? '赛事已保存' : '赛事已创建', 'success');
        closeModal();
        renderEvents();
      } catch (err) {
        btn.disabled = false;
        WO.toast(err.message, 'error');
      }
    });
  }

  // ---------- 用户 ----------

  async function renderUsers() {
    content.innerHTML = `
      <div class="toolbar" style="margin-top:0">
        <input class="input" id="user-search" style="max-width:280px" placeholder="搜索用户名或昵称…" value="${WO.escapeHtml(userKeyword)}">
        <button class="btn btn-sm" id="user-search-btn" type="button">搜索</button>
        <div class="spacer"></div>
        <span class="muted small">封禁后账号无法登录，榜单自动排除</span>
      </div>
      <div id="user-table"><div class="loading">加载用户</div></div>
      <div class="pagination" id="user-pager"></div>`;
    const search = document.getElementById('user-search');
    const doSearch = () => { userKeyword = search.value.trim(); userPage = 0; renderUsers(); };
    document.getElementById('user-search-btn').addEventListener('click', doSearch);
    search.addEventListener('keydown', e => { if (e.key === 'Enter') doSearch(); });
    try {
      const result = await WO.api('/admin/users?page=' + userPage + '&size=20'
          + (userKeyword ? '&keyword=' + encodeURIComponent(userKeyword) : ''));
      document.getElementById('user-table').innerHTML = `
        <div class="table-wrap"><table class="data">
          <thead><tr><th>用户</th><th>角色</th><th>战队</th><th>解题</th><th>总分</th><th>注册时间</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>${result.items.map(u => `
            <tr>
              <td><div style="font-weight:600">${WO.escapeHtml(u.displayName)}</div>
                <div class="muted small mono">@${WO.escapeHtml(u.username)}</div></td>
              <td>${isAdmin && u.role !== 'ADMIN' && u.id !== user.id
          ? `<select class="select" style="width:auto;padding:4px 10px" data-role="${WO.escapeHtml(u.id)}">
              ${ROLES.map(([v, l]) => `<option value="${v}" ${u.role === v ? 'selected' : ''}>${l}</option>`).join('')}
            </select>`
          : WO.badge(u.roleLabel, u.role === 'ADMIN' ? 'badge-purple' : 'badge-cyan')}</td>
              <td class="small">${WO.escapeHtml(u.teamName || '—')}</td>
              <td class="mono">${u.solveCount}</td>
              <td class="mono">${u.totalPoints}</td>
              <td class="muted small">${WO.formatDate(u.createdAt)}</td>
              <td>${u.banned ? WO.badge('已封禁', 'badge-red') : WO.badge('正常', 'badge-green')}</td>
              <td>${!isAdmin ? '<span class="muted small">仅管理员可操作</span>'
          : u.role === 'ADMIN' ? '<span class="muted small">管理员</span>'
          : `<button class="btn btn-sm ${u.banned ? '' : 'btn-danger'}" type="button" data-ban="${WO.escapeHtml(u.id)}" data-banned="${u.banned}">
                  ${u.banned ? '解封' : '封禁'}</button>`}</td>
            </tr>`).join('')}</tbody>
        </table></div>`;
      pager(document.getElementById('user-pager'), result, page => { userPage = page; renderUsers(); });
      document.getElementById('user-table').querySelectorAll('button[data-ban]').forEach(btn => {
        btn.addEventListener('click', async () => {
          const banned = btn.dataset.banned === 'true';
          if (!banned && !window.confirm('确认封禁该用户？封禁后无法登录。')) return;
          try {
            await WO.api('/admin/users/' + encodeURIComponent(btn.dataset.ban) + '/ban', {
              method: 'PATCH', body: { banned: !banned }
            });
            WO.toast(!banned ? '用户已封禁' : '用户已解封', 'success');
            renderUsers();
          } catch (err) {
            WO.toast(err.message, 'error');
          }
        });
      });
      document.getElementById('user-table').querySelectorAll('select[data-role]').forEach(sel => {
        sel.addEventListener('change', async () => {
          const found = ROLES.find(([v]) => v === sel.value);
          const label = found ? found[1] : sel.value;
          if (!window.confirm('确认将该用户的角色调整为「' + label + '」？')) {
            renderUsers();
            return;
          }
          try {
            await WO.api('/admin/users/' + encodeURIComponent(sel.dataset.role) + '/role', {
              method: 'PATCH', body: { role: sel.value }
            });
            WO.toast('角色已更新', 'success');
          } catch (err) {
            WO.toast(err.message, 'error');
          }
          renderUsers();
        });
      });
    } catch (err) {
      document.getElementById('user-table').innerHTML =
          `<div class="empty">${WO.escapeHtml(err.message)}</div>`;
    }
  }

  // ---------- 提交审计 ----------

  async function renderSubmissions() {
    content.innerHTML = `
      <div class="toolbar" style="margin-top:0">
        <select class="select" id="sub-result" style="width:auto">
          <option value="">全部结果</option>
          <option value="CORRECT" ${subResult === 'CORRECT' ? 'selected' : ''}>正确 CORRECT</option>
          <option value="WRONG" ${subResult === 'WRONG' ? 'selected' : ''}>错误 WRONG</option>
        </select>
        <button class="btn btn-sm" id="sub-refresh" type="button">刷新</button>
        <div class="spacer"></div>
        <span class="muted small">记录 IP / UA / 时间 / 判分结果，错误提交同样留痕</span>
      </div>
      <div id="sub-table"><div class="loading">加载提交日志</div></div>
      <div class="pagination" id="sub-pager"></div>`;
    document.getElementById('sub-result').addEventListener('change', e => {
      subResult = e.target.value;
      subPage = 0;
      renderSubmissions();
    });
    document.getElementById('sub-refresh').addEventListener('click', renderSubmissions);
    try {
      const result = await WO.api('/admin/submissions?page=' + subPage + '&size=20'
          + (subResult ? '&result=' + subResult : ''));
      document.getElementById('sub-table').innerHTML = `
        <div class="table-wrap"><table class="data">
          <thead><tr><th>时间</th><th>用户</th><th>题目</th><th>结果</th><th>IP</th><th>User-Agent</th><th>摘要</th></tr></thead>
          <tbody>${result.items.map(s => `
            <tr>
              <td class="muted small nowrap">${WO.formatDate(s.createdAt, true)}</td>
              <td class="mono">${WO.escapeHtml(s.username)}</td>
              <td class="small">${WO.escapeHtml(s.challengeName)}</td>
              <td>${s.result === 'CORRECT' ? WO.badge('正确', 'badge-green') : WO.badge('错误', 'badge-red')}</td>
              <td class="mono small">${WO.escapeHtml(s.ip || '—')}</td>
              <td class="small" style="max-width:260px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap"
                  title="${WO.escapeHtml(s.userAgent || '')}">${WO.escapeHtml(s.userAgent || '—')}</td>
              <td class="mono small">${WO.escapeHtml(s.submittedHashPrefix || '—')}</td>
            </tr>`).join('')}</tbody>
        </table></div>`;
      pager(document.getElementById('sub-pager'), result, page => { subPage = page; renderSubmissions(); });
    } catch (err) {
      document.getElementById('sub-table').innerHTML = `<div class="empty">${WO.escapeHtml(err.message)}</div>`;
    }
  }

  // ---------- 异常告警 ----------

  async function renderAnomalies() {
    content.innerHTML = `
      <div class="toolbar" style="margin-top:0">
        <button class="btn btn-sm" id="anomaly-refresh" type="button">刷新</button>
        <div class="spacer"></div>
        <span class="muted small">同一题目 + 同一 IP 被 2 个及以上账号正确提交时标记</span>
      </div>
      <div id="anomaly-table"><div class="loading">加载异常告警</div></div>`;
    document.getElementById('anomaly-refresh').addEventListener('click', renderAnomalies);
    try {
      const list = await WO.api('/admin/anomalies');
      if (!list.length) {
        document.getElementById('anomaly-table').innerHTML =
            '<div class="empty">当前没有异常告警。此功能为轻量提示，不替代完整风控。</div>';
        return;
      }
      document.getElementById('anomaly-table').innerHTML = `
        <div class="table-wrap"><table class="data">
          <thead><tr><th>题目</th><th>IP</th><th>关联账号数</th><th>账号</th><th>首次</th><th>最近</th></tr></thead>
          <tbody>${list.map(a => `
            <tr>
              <td>${WO.escapeHtml(a.challengeName)}</td>
              <td class="mono">${WO.escapeHtml(a.ip)}</td>
              <td>${WO.badge(a.distinctUsers + ' 个账号', 'badge-red')}</td>
              <td class="small mono">${a.usernames.map(u => WO.escapeHtml(u)).join(', ')}</td>
              <td class="muted small">${WO.formatDate(a.firstAt, true)}</td>
              <td class="muted small">${WO.formatDate(a.lastAt, true)}</td>
            </tr>`).join('')}</tbody>
        </table></div>`;
    } catch (err) {
      document.getElementById('anomaly-table').innerHTML = `<div class="empty">${WO.escapeHtml(err.message)}</div>`;
    }
  }

  // ---------- 公告 ----------

  async function renderAnnouncements() {
    content.innerHTML = `
      <div class="toolbar" style="margin-top:0">
        <button class="btn btn-primary btn-sm" id="new-announcement" type="button">+ 发布公告</button>
        <div class="spacer"></div>
      </div>
      <div id="announcement-table"><div class="loading">加载公告</div></div>`;
    document.getElementById('new-announcement').addEventListener('click', () => openAnnouncementModal(null));
    try {
      const list = await WO.api('/admin/announcements');
      if (!list.length) {
        document.getElementById('announcement-table').innerHTML = '<div class="empty">暂无公告。</div>';
        return;
      }
      document.getElementById('announcement-table').innerHTML = `
        <div class="table-wrap"><table class="data">
          <thead><tr><th>标题</th><th>内容</th><th>发布时间</th><th>操作</th></tr></thead>
          <tbody>${list.map(a => `
            <tr>
              <td style="font-weight:600">${WO.escapeHtml(a.title)}</td>
              <td class="small" style="max-width:420px">${WO.escapeHtml(a.content)}</td>
              <td class="muted small nowrap">${WO.formatDate(a.createdAt)}</td>
              <td class="nowrap">
                <button class="btn btn-sm" type="button" data-edit="${WO.escapeHtml(a.id)}">编辑</button>
                <button class="btn btn-sm btn-danger" type="button" data-del="${WO.escapeHtml(a.id)}">删除</button>
              </td>
            </tr>`).join('')}</tbody>
        </table></div>`;
      document.getElementById('announcement-table').querySelectorAll('button[data-edit]').forEach(btn => {
        btn.addEventListener('click', () => openAnnouncementModal(list.find(a => a.id === btn.dataset.edit)));
      });
      document.getElementById('announcement-table').querySelectorAll('button[data-del]').forEach(btn => {
        btn.addEventListener('click', async () => {
          if (!window.confirm('确认删除该公告？')) return;
          try {
            await WO.api('/admin/announcements/' + encodeURIComponent(btn.dataset.del), { method: 'DELETE' });
            WO.toast('公告已删除', 'success');
            renderAnnouncements();
          } catch (err) {
            WO.toast(err.message, 'error');
          }
        });
      });
    } catch (err) {
      document.getElementById('announcement-table').innerHTML = `<div class="empty">${WO.escapeHtml(err.message)}</div>`;
    }
  }

  function openAnnouncementModal(announcement) {
    const isEdit = !!announcement;
    const a = announcement || {};
    openModal(isEdit ? '编辑公告' : '发布公告', `
      <div class="field"><label>标题</label>
        <input class="input" id="an-title" maxlength="100" value="${WO.escapeHtml(a.title || '')}"></div>
      <div class="field"><label>内容</label>
        <textarea class="textarea" id="an-content" maxlength="2000" style="min-height:140px">${WO.escapeHtml(a.content || '')}</textarea></div>`,
        `<button class="btn" id="modal-cancel" type="button">取消</button>
         <button class="btn btn-primary" id="modal-save" type="button">${isEdit ? '保存' : '发布'}</button>`);
    cancelButton();
    document.getElementById('modal-save').addEventListener('click', async () => {
      const title = document.getElementById('an-title').value.trim();
      const body = document.getElementById('an-content').value.trim();
      if (!title || !body) return WO.toast('标题与内容均不能为空', 'error');
      const btn = document.getElementById('modal-save');
      btn.disabled = true;
      try {
        if (isEdit) {
          await WO.api('/admin/announcements/' + encodeURIComponent(announcement.id),
              { method: 'PUT', body: { title, content: body } });
        } else {
          await WO.api('/admin/announcements', { method: 'POST', body: { title, content: body } });
        }
        WO.toast(isEdit ? '公告已保存' : '公告已发布', 'success');
        closeModal();
        renderAnnouncements();
      } catch (err) {
        btn.disabled = false;
        WO.toast(err.message, 'error');
      }
    });
  }

  // ---------- 审计日志 ----------

  const AUDIT_ACTIONS = [
    ['USER_BAN', '用户封禁'], ['USER_UNBAN', '用户解封'], ['USER_ROLE_CHANGE', '角色调整'],
    ['EVENT_CREATE', '赛事创建'], ['EVENT_UPDATE', '赛事更新'], ['EVENT_DELETE', '赛事删除'],
    ['CHALLENGE_CREATE', '题目创建'], ['CHALLENGE_UPDATE', '题目更新'], ['CHALLENGE_DELETE', '题目删除'],
    ['HINT_CREATE', '提示创建'], ['HINT_UPDATE', '提示更新'], ['HINT_DELETE', '提示删除'],
    ['ANNOUNCEMENT_CREATE', '公告发布'], ['ANNOUNCEMENT_UPDATE', '公告更新'], ['ANNOUNCEMENT_DELETE', '公告删除']
  ];

  function actionLabel(action) {
    const found = AUDIT_ACTIONS.find(([v]) => v === action);
    return found ? found[1] : action;
  }

  async function renderAuditLogs() {
    content.innerHTML = `
      <div class="toolbar" style="margin-top:0">
        <select class="select" id="audit-action" style="width:auto">
          <option value="">全部操作</option>
          ${AUDIT_ACTIONS.map(([v, l]) => `<option value="${v}" ${auditAction === v ? 'selected' : ''}>${l}</option>`).join('')}
        </select>
        <button class="btn btn-sm" id="audit-refresh" type="button">刷新</button>
        <div class="spacer"></div>
        <span class="muted small">管理端关键操作留痕；MONGO_ENABLED=true 时同步镜像到 MongoDB op_logs</span>
      </div>
      <div id="audit-table"><div class="loading">加载审计日志</div></div>
      <div class="pagination" id="audit-pager"></div>`;
    document.getElementById('audit-action').addEventListener('change', e => {
      auditAction = e.target.value;
      auditPage = 0;
      renderAuditLogs();
    });
    document.getElementById('audit-refresh').addEventListener('click', renderAuditLogs);
    try {
      const result = await WO.api('/admin/audit-logs?page=' + auditPage + '&size=20'
          + (auditAction ? '&action=' + encodeURIComponent(auditAction) : ''));
      if (!result.items.length) {
        document.getElementById('audit-table').innerHTML = '<div class="empty">暂无审计日志。</div>';
        return;
      }
      document.getElementById('audit-table').innerHTML = `
        <div class="table-wrap"><table class="data">
          <thead><tr><th>时间</th><th>操作</th><th>操作者</th><th>对象</th><th>详情</th><th>IP</th></tr></thead>
          <tbody>${result.items.map(a => `
            <tr>
              <td class="muted small nowrap">${WO.formatDate(a.createdAt, true)}</td>
              <td>${WO.badge(actionLabel(a.action), 'badge-cyan')}</td>
              <td class="small">${WO.escapeHtml(a.actorName || 'system')}
                <span class="muted small">${WO.escapeHtml(a.actorRole || '')}</span></td>
              <td class="small mono">${WO.escapeHtml(a.target || '—')}</td>
              <td class="small" style="max-width:320px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap"
                  title="${WO.escapeHtml(a.detail || '')}">${WO.escapeHtml(a.detail || '—')}</td>
              <td class="mono small">${WO.escapeHtml(a.ip || '—')}</td>
            </tr>`).join('')}</tbody>
        </table></div>`;
      pager(document.getElementById('audit-pager'), result, page => { auditPage = page; renderAuditLogs(); });
    } catch (err) {
      document.getElementById('audit-table').innerHTML = `<div class="empty">${WO.escapeHtml(err.message)}</div>`;
    }
  }

  // ---------- 切换 ----------

  function renderTab(tab) {
    if (tab === 'challenges') renderChallenges();
    else if (tab === 'events') renderEvents();
    else if (tab === 'users') renderUsers();
    else if (tab === 'submissions') renderSubmissions();
    else if (tab === 'audit') renderAuditLogs();
    else if (tab === 'anomalies') renderAnomalies();
    else if (tab === 'announcements') renderAnnouncements();
  }

  tabs.addEventListener('click', e => {
    const btn = e.target.closest('button[data-tab]');
    if (!btn) return;
    currentTab = btn.dataset.tab;
    tabs.querySelectorAll('button').forEach(b => b.classList.toggle('active', b === btn));
    content.classList.add('panel-pad');
    renderTab(currentTab);
  });

  renderTab(currentTab);
})();
