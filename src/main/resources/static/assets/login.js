/* 登录 / 注册页 */
(function () {
  const form = document.getElementById('auth-form');
  const username = document.getElementById('username');
  const password = document.getElementById('password');
  const displayName = document.getElementById('displayName');
  const displayField = document.getElementById('display-field');
  const title = document.getElementById('form-title');
  const sub = document.getElementById('form-sub');
  const submitBtn = document.getElementById('submit-btn');
  const passwordHint = document.getElementById('password-hint');
  const tabs = document.getElementById('mode-tabs');
  let mode = 'login';

  function setMode(next) {
    mode = next;
    tabs.querySelectorAll('button').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.mode === mode);
    });
    const isRegister = mode === 'register';
    displayField.style.display = isRegister ? '' : 'none';
    title.textContent = isRegister ? '注册选手账号' : '登录 CTF 平台';
    sub.textContent = isRegister
        ? '注册后自动登录，可创建或加入战队。'
        : '使用演示账号或注册新选手账号进入平台。';
    submitBtn.textContent = isRegister ? '注册并进入' : '登录';
    passwordHint.textContent = isRegister ? '密码长度需为 8-64 位。' : '登录演示账号可直接使用下方凭据。';
    password.autocomplete = isRegister ? 'new-password' : 'current-password';
  }

  tabs.addEventListener('click', e => {
    const btn = e.target.closest('button[data-mode]');
    if (btn) setMode(btn.dataset.mode);
  });

  document.querySelectorAll('.demo-item').forEach(item => {
    item.addEventListener('click', () => {
      setMode('login');
      username.value = item.dataset.username;
      password.value = item.dataset.password;
      password.focus();
    });
  });

  form.addEventListener('submit', async e => {
    e.preventDefault();
    const payload = {
      username: username.value.trim(),
      password: password.value
    };
    if (mode === 'register' && displayName.value.trim()) {
      payload.displayName = displayName.value.trim();
    }
    submitBtn.disabled = true;
    submitBtn.textContent = mode === 'register' ? '注册中…' : '登录中…';
    try {
      const data = await WO.api(mode === 'register' ? '/auth/register' : '/auth/login', {
        method: 'POST',
        body: payload,
        authRedirect: false
      });
      WO.setSession(data);
      window.location.href = '/index.html';
    } catch (err) {
      WO.toast(err.message || '登录失败', 'error');
      submitBtn.disabled = false;
      submitBtn.textContent = mode === 'register' ? '注册并进入' : '登录';
    }
  });
})();
