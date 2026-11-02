/* 题目详情：描述、附件、提示、提交 flag、解题动态 */
(function () {
  WO.renderHeader('challenges');
  const id = WO.query('id');
  if (!id) {
    window.location.href = '/index.html';
    return;
  }

  const backLink = document.getElementById('back-link');
  let detail = null;

  function sizeText(bytes) {
    if (!bytes) return '';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1024 / 1024).toFixed(1) + ' MB';
  }

  function render() {
    const c = detail;
    backLink.href = '/index.html?eventId=' + encodeURIComponent(c.event.id);
    document.getElementById('detail-title').innerHTML =
        `<span class="hash">#</span>${WO.escapeHtml(c.name)}`;
    document.getElementById('detail-sub').textContent =
        `${c.event.name} · ${c.event.typeLabel} · ${c.event.statusLabel}`;
    document.getElementById('footer-event').textContent = c.event.name;

    document.getElementById('detail-badges').innerHTML = [
      WO.badge(c.categoryLabel, WO.categoryClass(c.category)),
      WO.badge(c.difficultyLabel, WO.difficultyClass(c.difficulty)),
      c.solved ? WO.badge('已解出', 'badge-green') : WO.badge('未解出', 'badge-muted')
    ].join(' ');

    document.getElementById('detail-name').textContent = c.name;
    document.getElementById('detail-meta').textContent =
        `当前分值 ${c.currentPoints} PTS · 初始 ${c.initialPoints} / 最低 ${c.minPoints} / 每题衰减 ${c.decayPoints} · ${c.solveCount} 人解出`
        + (c.solvedAt ? ` · 解出于 ${WO.formatDate(c.solvedAt)}` : '');
    document.getElementById('detail-description').textContent = c.description || '暂无描述';

    const attach = document.getElementById('attachment-section');
    if (c.attachmentName) {
      attach.style.display = '';
      document.getElementById('attachment-name').textContent = c.attachmentName;
      document.getElementById('attachment-size').textContent =
          sizeText(c.attachmentSize) + ' · 本地存储，随容器数据卷持久化';
      document.getElementById('download-btn').onclick = () => {
        WO.download('/challenges/' + encodeURIComponent(id) + '/attachment', c.attachmentName)
            .catch(err => WO.toast(err.message, 'error'));
      };
    } else {
      attach.style.display = 'none';
    }

    renderHints(c);
    renderTimeline(c.recentSolves);
    renderSubmit(c);
  }

  function renderHints(c) {
    const list = document.getElementById('hint-list');
    if (!c.hints || !c.hints.length) {
      list.innerHTML = '<div class="muted small">本题暂无提示。</div>';
      return;
    }
    list.innerHTML = c.hints.map((h, i) => `
      <div class="hint ${h.unlocked ? '' : 'locked'}">
        <div class="hint-head">
          <strong>提示 ${i + 1}</strong>
          ${h.unlocked
      ? `<span class="badge badge-green">已解锁</span>`
      : `<button class="btn btn-sm" data-hint="${WO.escapeHtml(h.id)}" data-cost="${h.cost}">
                   解锁 · 扣 ${h.cost} 分</button>`}
        </div>
        <div class="hint-content">${h.unlocked
      ? WO.escapeHtml(h.content || '')
      : `● ● ● ● ● 解锁后显示（代价 ${h.cost} PTS）`}</div>
      </div>`).join('');

    list.querySelectorAll('button[data-hint]').forEach(btn => {
      btn.addEventListener('click', async () => {
        if (!window.confirm(`解锁该提示将扣除 ${btn.dataset.cost} 分，确认继续？`)) return;
        btn.disabled = true;
        try {
          await WO.api('/challenges/' + encodeURIComponent(id) + '/hints/'
              + encodeURIComponent(btn.dataset.hint) + '/unlock', { method: 'POST' });
          WO.toast('提示已解锁', 'success');
          await load();
        } catch (err) {
          btn.disabled = false;
          WO.toast(err.message, 'error');
        }
      });
    });
  }

  function renderTimeline(solves) {
    const box = document.getElementById('solve-timeline');
    if (!solves || !solves.length) {
      box.innerHTML = '<div class="muted small">还没有人解出，你可以成为一血。</div>';
      return;
    }
    box.innerHTML = solves.map(s => `
      <div class="timeline-item">
        <strong>${WO.escapeHtml(s.displayName)}${s.teamName ? ' · ' + WO.escapeHtml(s.teamName) : ''}</strong>
        <span>${s.bloodRankLabel ? WO.badge(s.bloodRankLabel, 'badge-amber') + ' ' : ''}
          ${s.points} PTS · ${WO.relativeTime(s.solvedAt)}</span>
      </div>`).join('');
  }

  function renderSubmit(c) {
    const area = document.getElementById('submit-area');
    const result = document.getElementById('submit-result');

    if (c.solved) {
      area.innerHTML = `<div class="result-box result-ok">✓ 该题目已解出，无需重复提交。<br>
        <span class="small">解出时间：${WO.formatDate(c.solvedAt, true)}</span></div>`;
      result.innerHTML = '';
      return;
    }
    if (c.event.type === 'COMPETITION' && c.event.status === 'ENDED') {
      area.innerHTML = `<div class="result-box result-bad">赛事已结束，当前只能查看题目，无法继续提交。</div>`;
      result.innerHTML = '';
      return;
    }
    if (c.event.type === 'COMPETITION' && c.event.status === 'NOT_STARTED') {
      area.innerHTML = `<div class="result-box result-bad">赛事尚未开始，题目暂不可答。</div>`;
      result.innerHTML = '';
      return;
    }

    area.innerHTML = `
      <form id="submit-form">
        <input class="input flag-input" id="flag-input" maxlength="200"
               placeholder="flag{...}" autocomplete="off" spellcheck="false">
        <button class="btn btn-primary btn-block" id="submit-btn" type="submit"
                style="margin-top:10px">提交</button>
      </form>
      <div class="muted small" style="margin-top:8px">
        错误提交有冷却与次数限制；正确提交后不可重复提交。
      </div>`;
    result.innerHTML = '';

    document.getElementById('submit-form').addEventListener('submit', async e => {
      e.preventDefault();
      const input = document.getElementById('flag-input');
      const btn = document.getElementById('submit-btn');
      const flag = input.value.trim();
      if (!flag) {
        WO.toast('请输入 flag', 'error');
        return;
      }
      btn.disabled = true;
      btn.textContent = '判分中…';
      try {
        const data = await WO.api('/challenges/' + encodeURIComponent(id) + '/submit', {
          method: 'POST',
          body: { flag }
        });
        result.innerHTML = `<div class="result-box result-ok">
          ✓ ${WO.escapeHtml(data.message)}<br>
          <span class="mono">基础 ${data.basePoints} + 血奖 ${data.bloodBonus} - 提示 ${data.hintCost} = ${data.points} PTS</span><br>
          <span class="small">赛事总分 ${data.totalPoints} · 当前名次 #${data.rank} · 本题 ${data.solveCount} 解出</span>
        </div>`;
        WO.toast('提交正确，得分 +' + data.points, 'success');
        setTimeout(load, 900);
      } catch (err) {
        result.innerHTML = `<div class="result-box result-bad">
          ✗ ${WO.escapeHtml(err.message)}
          <span class="error-code">code ${err.code || '—'}</span>
        </div>`;
        btn.disabled = false;
        btn.textContent = '提交';
        if (err.code === 4004 || err.code === 4291) input.select();
      }
    });
  }

  async function load() {
    try {
      detail = await WO.api('/challenges/' + encodeURIComponent(id));
      render();
    } catch (err) {
      document.getElementById('detail-title').textContent = '无法访问该题目';
      document.getElementById('detail-sub').textContent = err.message;
      document.getElementById('detail-description').textContent =
          `错误码 ${err.code || '—'}。${err.code === 4031 ? '赛事尚未开始。' : ''}`;
    }
  }

  load();
})();
