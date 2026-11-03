/* 公共前端工具：无任何外部依赖 */
(function () {
  const TOKEN_KEY = 'ctf_token';
  const USER_KEY = 'ctf_user';

  class ApiError extends Error {
    constructor(code, message, status) {
      super(message);
      this.code = code;
      this.status = status;
    }
  }

  function getToken() {
    return localStorage.getItem(TOKEN_KEY);
  }

  function getUser() {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try { return JSON.parse(raw); } catch (e) { return null; }
  }

  function setSession(data) {
    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(USER_KEY, JSON.stringify(data.user));
  }

  function clearSession() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  async function api(path, options) {
    const opts = Object.assign({ method: 'GET', body: undefined, authRedirect: true }, options || {});
    const headers = { Accept: 'application/json' };
    if (opts.body !== undefined && !(opts.body instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
    }
    const token = getToken();
    if (token) headers.Authorization = 'Bearer ' + token;
    let response;
    try {
      response = await fetch('/api' + path, {
        method: opts.method,
        headers,
        body: opts.body === undefined ? undefined
            : (opts.body instanceof FormData ? opts.body : JSON.stringify(opts.body))
      });
    } catch (e) {
      throw new ApiError(5000, '网络请求失败，请检查服务是否已启动', 0);
    }
    let payload = null;
    try { payload = await response.json(); } catch (e) { /* ignore */ }
    if (response.status === 401 && opts.authRedirect) {
      clearSession();
      window.location.href = '/login.html';
      throw new ApiError(4010, '登录已过期', 401);
    }
    if (!response.ok || !payload || payload.code !== 0) {
      const code = payload && payload.code ? payload.code : response.status;
      const message = payload && payload.message ? payload.message : ('请求失败（HTTP ' + response.status + '）');
      throw new ApiError(code, message, response.status);
    }
    return payload.data;
  }

  async function download(path, filename) {
    const token = getToken();
    const response = await fetch('/api' + path, { headers: token ? { Authorization: 'Bearer ' + token } : {} });
    if (!response.ok) {
      let message = '附件下载失败';
      try { const p = await response.json(); if (p && p.message) message = p.message; } catch (e) { /* ignore */ }
      throw new ApiError(response.status, message, response.status);
    }
    const blob = await response.blob();
    let name = filename || 'attachment';
    const cd = response.headers.get('Content-Disposition') || '';
    const utf8 = cd.match(/filename\*=UTF-8''([^;]+)/i);
    if (utf8) name = decodeURIComponent(utf8[1]);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  }

  function toast(message, type) {
    let wrap = document.querySelector('.toast-wrap');
    if (!wrap) {
      wrap = document.createElement('div');
      wrap.className = 'toast-wrap';
      document.body.appendChild(wrap);
    }
    const el = document.createElement('div');
    el.className = 'toast ' + (type || '');
    el.textContent = message;
    wrap.appendChild(el);
    setTimeout(() => { el.style.opacity = '0'; el.style.transition = 'opacity .25s'; }, 3200);
    setTimeout(() => el.remove(), 3600);
  }

  function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatDate(value, withSeconds) {
    if (!value) return '—';
    const d = new Date(value);
    if (isNaN(d.getTime())) return String(value);
    const pad = n => String(n).padStart(2, '0');
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' '
        + pad(d.getHours()) + ':' + pad(d.getMinutes()) + (withSeconds ? ':' + pad(d.getSeconds()) : '');
  }

  function formatShort(value) {
    if (!value) return '—';
    const d = new Date(value);
    if (isNaN(d.getTime())) return String(value);
    const pad = n => String(n).padStart(2, '0');
    return pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
  }

  function relativeTime(value) {
    if (!value) return '—';
    const d = new Date(value);
    if (isNaN(d.getTime())) return String(value);
    const diff = Math.floor((Date.now() - d.getTime()) / 1000);
    if (diff < 60) return '刚刚';
    if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前';
    if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前';
    if (diff < 2592000) return Math.floor(diff / 86400) + ' 天前';
    return formatShort(value);
  }

  function countdown(endAt) {
    if (!endAt) return '';
    const diff = new Date(endAt).getTime() - Date.now();
    if (diff <= 0) return '已结束';
    const days = Math.floor(diff / 86400000);
    const hours = Math.floor((diff % 86400000) / 3600000);
    const mins = Math.floor((diff % 3600000) / 60000);
    if (days > 0) return days + ' 天 ' + hours + ' 小时';
    if (hours > 0) return hours + ' 小时 ' + mins + ' 分钟';
    return mins + ' 分钟';
  }

  const CATEGORY_CLASS = {
    WEB: 'cat-WEB', PWN: 'cat-PWN', CRYPTO: 'cat-CRYPTO', MISC: 'cat-MISC', REVERSE: 'cat-REVERSE'
  };

  function categoryClass(cat) {
    return CATEGORY_CLASS[cat] || 'badge';
  }

  function difficultyClass(difficulty) {
    if (difficulty === 'EASY') return 'badge-green';
    if (difficulty === 'HARD') return 'badge-red';
    return 'badge-amber';
  }

  function eventTypeClass(type) {
    return type === 'PRACTICE' ? 'badge-green' : 'badge-cyan';
  }

  function eventStatusClass(status) {
    if (status === 'ONGOING') return 'badge-green';
    if (status === 'ENDED') return 'badge-muted';
    return 'badge-amber';
  }

  function renderHeader(active) {
    const user = requireAuth();
    const topbar = document.getElementById('topbar');
    if (!topbar) return user;
    const staff = user.role === 'ADMIN' || user.role === 'JUDGE';
    const ROLE_LABEL = { ADMIN: '管理员', JUDGE: '裁判', TEACHER: '教师', OBSERVER: '观摩' };
    topbar.innerHTML = `
      <a class="brand" href="/index.html">
        <span class="brand-mark">./ctf</span>
        <span>CTF PLATFORM <small>SEC LAB</small></span>
      </a>
      <nav class="nav">
        <a href="/index.html" class="${active === 'challenges' ? 'active' : ''}">题目</a>
        <a href="/scoreboard.html" class="${active === 'scoreboard' ? 'active' : ''}">排行榜</a>
        <a href="/profile.html" class="${active === 'profile' ? 'active' : ''}">我的战绩</a>
        ${staff ? `<a href="/admin.html" class="${active === 'admin' ? 'active' : ''}">管理后台</a>` : ''}
      </nav>
      <div class="user-chip">
        <div class="avatar">${escapeHtml(user.displayName ? user.displayName.slice(0, 2) : user.username.slice(0, 2))}</div>
        <div class="who">
          <strong>${escapeHtml(user.displayName)}</strong>
          <span>${escapeHtml(user.teamName || ROLE_LABEL[user.role] || '未加入战队')}</span>
        </div>
        <button class="icon-btn" id="logout-btn">退出</button>
      </div>`;
    const logout = document.getElementById('logout-btn');
    if (logout) logout.addEventListener('click', async () => {
      try {
        await api('/auth/logout', { method: 'POST', authRedirect: false });
      } catch (e) { /* 服务端吊销失败不阻塞本地登出 */ }
      clearSession();
      window.location.href = '/login.html';
    });
    return user;
  }

  function requireAuth() {
    const token = getToken();
    const user = getUser();
    if (!token || !user) {
      window.location.href = '/login.html';
      throw new ApiError(4010, '未登录', 401);
    }
    return user;
  }

  function query(name) {
    return new URLSearchParams(window.location.search).get(name);
  }

  function debounce(fn, wait) {
    let timer = null;
    return function () {
      const args = arguments;
      clearTimeout(timer);
      timer = setTimeout(() => fn.apply(this, args), wait);
    };
  }

  function badge(text, cls) {
    return `<span class="badge ${cls || ''}">${escapeHtml(text)}</span>`;
  }

  window.WO = {
    ApiError, api, download, toast, escapeHtml, formatDate, formatShort, relativeTime, countdown,
    categoryClass, difficultyClass, eventTypeClass, eventStatusClass, renderHeader, requireAuth,
    getUser, getToken, setSession, clearSession, query, debounce, badge
  };
})();
