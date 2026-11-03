<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, getUser } from '../../shared/api.js'
import { ROLES, formatDate } from '../../shared/format.js'

const me = getUser()
const isAdmin = !!me && me.role === 'ADMIN'

const page = ref(1)
const size = 20
const keyword = ref('')
const result = ref({ items: [], page: 0, total: 0, totalPages: 0 })
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    result.value = await api(
      '/admin/users?page=' + (page.value - 1) + '&size=' + size +
        (keyword.value.trim() ? '&keyword=' + encodeURIComponent(keyword.value.trim()) : '')
    )
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

async function toggleBan(row) {
  if (!row.banned) {
    try {
      await ElMessageBox.confirm('确认封禁该用户？封禁后无法登录。', '封禁用户', { type: 'warning' })
    } catch (e) {
      return
    }
  }
  try {
    await api('/admin/users/' + encodeURIComponent(row.id) + '/ban', {
      method: 'PATCH',
      body: { banned: !row.banned }
    })
    ElMessage.success(row.banned ? '用户已解封' : '用户已封禁')
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function changeRole(row, role) {
  const label = (ROLES.find(([v]) => v === role) || [role, role])[1]
  try {
    await ElMessageBox.confirm('确认将该用户的角色调整为「' + label + '」？', '角色调整', {
      type: 'warning'
    })
  } catch (e) {
    await load()
    return
  }
  try {
    await api('/admin/users/' + encodeURIComponent(row.id) + '/role', { method: 'PATCH', body: { role } })
    ElMessage.success('角色已更新')
    await load()
  } catch (err) {
    ElMessage.error(err.message)
    await load()
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar" style="margin-top: 0">
      <el-input
        v-model="keyword"
        placeholder="搜索用户名或昵称…"
        size="small"
        clearable
        style="max-width: 280px"
        @keyup.enter="search"
      />
      <el-button size="small" @click="search">搜索</el-button>
      <div class="spacer" />
      <span class="muted small">封禁后账号无法登录，榜单自动排除</span>
    </div>

    <el-table v-loading="loading" :data="result.items" size="small" stripe>
      <el-table-column label="用户" min-width="160">
        <template #default="{ row }">
          <div style="font-weight: 600">{{ row.displayName }}</div>
          <div class="muted small mono">@{{ row.username }}</div>
        </template>
      </el-table-column>
      <el-table-column label="角色" width="150">
        <template #default="{ row }">
          <el-select
            v-if="isAdmin && row.role !== 'ADMIN' && row.id !== me.id"
            :model-value="row.role"
            size="small"
            style="width: 110px"
            @change="(v) => changeRole(row, v)"
          >
            <el-option v-for="[v, l] in ROLES" :key="v" :value="v" :label="l" />
          </el-select>
          <el-tag v-else size="small" :type="row.role === 'ADMIN' ? 'danger' : 'primary'">
            {{ row.roleLabel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="战队" min-width="120">
        <template #default="{ row }">
          <span class="small">{{ row.teamName || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="solveCount" label="解题" width="80" />
      <el-table-column prop="totalPoints" label="总分" width="90" />
      <el-table-column label="注册时间" width="140">
        <template #default="{ row }">
          <span class="muted small">{{ formatDate(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.banned ? 'danger' : 'success'">
            {{ row.banned ? '已封禁' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <span v-if="!isAdmin" class="muted small">仅管理员可操作</span>
          <span v-else-if="row.role === 'ADMIN'" class="muted small">管理员</span>
          <el-button
            v-else
            size="small"
            :type="row.banned ? 'success' : 'danger'"
            @click="toggleBan(row)"
          >
            {{ row.banned ? '解封' : '封禁' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      :page-size="size"
      :total="result.total"
      layout="total, prev, pager, next"
      style="margin-top: 14px; justify-content: flex-end"
      @current-change="load"
    />
  </div>
</template>
