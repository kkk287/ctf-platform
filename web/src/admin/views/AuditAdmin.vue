<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../shared/api.js'
import { AUDIT_ACTIONS, auditActionLabel, formatDate } from '../../shared/format.js'

const page = ref(1)
const size = 20
const action = ref('')
const result = ref({ items: [], page: 0, total: 0, totalPages: 0 })
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    result.value = await api(
      '/admin/audit-logs?page=' + (page.value - 1) + '&size=' + size +
        (action.value ? '&action=' + encodeURIComponent(action.value) : '')
    )
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

function changeFilter() {
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar" style="margin-top: 0">
      <el-select v-model="action" size="small" style="width: 170px" @change="changeFilter">
        <el-option value="" label="全部操作" />
        <el-option v-for="[v, l] in AUDIT_ACTIONS" :key="v" :value="v" :label="l" />
      </el-select>
      <el-button size="small" @click="load">刷新</el-button>
      <div class="spacer" />
      <span class="muted small">管理端关键操作留痕；MONGO_ENABLED=true 时同步镜像到 MongoDB op_logs</span>
    </div>

    <el-table v-loading="loading" :data="result.items" size="small" stripe>
      <el-table-column label="时间" width="160">
        <template #default="{ row }">
          <span class="muted small">{{ formatDate(row.createdAt, true) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-tag size="small" type="primary">{{ auditActionLabel(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作者" width="130">
        <template #default="{ row }">
          <span class="small">{{ row.actorName || 'system' }}</span>
          <span class="muted small">{{ row.actorRole || '' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="对象" min-width="140">
        <template #default="{ row }">
          <span class="mono small">{{ row.target || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="详情" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="small">{{ row.detail || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="130" />
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
