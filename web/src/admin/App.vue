<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { api, clearSession, getUser } from '../shared/api.js'

const route = useRoute()
const router = useRouter()
const user = getUser()
const allowed = computed(() => !!user && (user.role === 'ADMIN' || user.role === 'JUDGE'))
const isAdmin = computed(() => !!user && user.role === 'ADMIN')

const menus = [
  { path: '/challenges', label: '题目管理' },
  { path: '/events', label: '赛事/练习区' },
  { path: '/theory', label: '理论赛管理' },
  { path: '/writeups', label: 'Writeup 审核' },
  { path: '/instances', label: '靶机实例总览' },
  { path: '/awd', label: 'AWD 轮次管理' },
  { path: '/users', label: '用户与角色' },
  { path: '/submissions', label: '提交审计' },
  { path: '/anomalies', label: '异常告警' },
  { path: '/announcements', label: '公告管理' },
  { path: '/audit', label: '审计日志' }
]

async function logout() {
  try {
    await api('/auth/logout', { method: 'POST', authRedirect: false })
  } catch (e) {
    /* 忽略 */
  }
  clearSession()
  ElMessage.success('已退出登录')
  window.location.href = '/vue/player.html#/login'
}
</script>

<template>
  <el-config-provider :locale="zhCn">
  <div v-if="!allowed" class="page" style="padding-top: 80px; text-align: center">
    <el-empty description="管理后台仅对管理员 / 裁判开放">
      <el-button type="primary" @click="router.replace('/vue/player.html#/')">返回选手端</el-button>
    </el-empty>
  </div>

  <div v-else class="admin-shell">
    <aside class="admin-side">
      <div class="brand">
        <span class="brand-mark">./ctf</span>
        <span>管理后台</span>
      </div>
      <el-menu :default-active="route.path" router class="admin-menu" background-color="transparent" text-color="#b7c2d8">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          {{ m.label }}
        </el-menu-item>
      </el-menu>
      <div style="margin-top: 20px; padding: 0 8px">
        <a href="/vue/player.html" style="color: #8ea6d6; font-size: 13px">← 返回选手端</a>
      </div>
    </aside>

    <main class="admin-main">
      <div class="admin-title">
        <h2 style="font-size: 19px">{{ route.meta.title }}</h2>
        <div style="display: flex; align-items: center; gap: 12px">
          <span class="muted small">{{ user.displayName }} · {{ user.role }}</span>
          <el-tag v-if="isAdmin" type="danger" size="small">管理员</el-tag>
          <el-tag v-else type="warning" size="small">裁判</el-tag>
          <el-button size="small" @click="logout">退出</el-button>
        </div>
      </div>
      <router-view />
    </main>
  </div>
  </el-config-provider>
</template>
