<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { api, clearSession, getUser, getToken } from '../shared/api.js'

const route = useRoute()
const router = useRouter()
const user = ref(getUser())

const authed = computed(() => !!getToken() && !!user.value)
const staff = computed(() => user.value && (user.value.role === 'ADMIN' || user.value.role === 'JUDGE'))

const roleLabel = { ADMIN: '管理员', JUDGE: '裁判', TEACHER: '教师', OBSERVER: '观摩', USER: '选手' }

const navItems = [
  { path: '/', label: '题目' },
  { path: '/scoreboard', label: '排行榜' },
  { path: '/theory', label: '理论赛' },
  { path: '/writeups', label: 'Writeup' },
  { path: '/awd', label: 'AWD 攻防' },
  { path: '/profile', label: '我的战绩' }
]

function isActive(path) {
  return route.path === path || (path === '/' && route.path.startsWith('/challenge'))
}

function avatarText() {
  const u = user.value
  if (!u) return '??'
  const name = u.displayName || u.username || ''
  return name.slice(0, 2)
}

async function logout() {
  try {
    await api('/auth/logout', { method: 'POST', authRedirect: false })
  } catch (e) {
    /* 服务端吊销失败不阻塞本地登出 */
  }
  clearSession()
  user.value = null
  ElMessage.success('已退出登录')
  router.replace('/login')
}
</script>

<template>
  <el-config-provider :locale="zhCn">
  <div v-if="!authed">
    <router-view />
  </div>
  <div v-else>
    <header class="app-topbar">
      <router-link class="brand" to="/">
        <span class="brand-mark">./ctf</span>
        <span>CTF PLATFORM <small>SEC LAB</small></span>
      </router-link>
      <nav class="app-nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          :class="{ active: isActive(item.path) }"
        >
          {{ item.label }}
        </router-link>
        <a v-if="staff" href="/vue/admin.html">管理后台</a>
      </nav>
      <div style="display: flex; align-items: center; gap: 12px">
        <div style="text-align: right; line-height: 1.3">
          <div style="font-weight: 600; color: var(--text-title); font-size: 14px">
            {{ user.displayName }}
          </div>
          <div class="muted small">{{ user.teamName || roleLabel[user.role] }}</div>
        </div>
        <el-avatar :size="34" style="background: var(--color-primary)">{{ avatarText() }}</el-avatar>
        <el-button size="small" @click="logout">退出</el-button>
      </div>
    </header>
    <main class="page">
      <router-view />
    </main>
  </div>
  </el-config-provider>
</template>
