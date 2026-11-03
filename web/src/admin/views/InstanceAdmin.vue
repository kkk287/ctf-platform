<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../shared/api.js'
import { formatDate } from '../../shared/format.js'

const list = ref([])
const loading = ref(false)
const statusFilter = ref('')
const now = ref(Date.now())
let timer = null

const filtered = computed(() => {
  if (!statusFilter.value) return list.value
  return list.value.filter((i) => i.status === statusFilter.value)
})

const stats = computed(() => {
  const active = list.value.filter((i) => i.status === 'RUNNING' || i.status === 'PENDING').length
  const expired = list.value.filter((i) => i.status === 'EXPIRED').length
  return { total: list.value.length, active, expired }
})

function statusType(status) {
  if (status === 'RUNNING') return 'success'
  if (status === 'PENDING') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function remaining(row) {
  if (row.status !== 'RUNNING' && row.status !== 'PENDING') {
    return '—'
  }
  if (!row.expiresAt) return '—'
  const ms = new Date(row.expiresAt).getTime() - now.value
  if (ms <= 0) return '已到期'
  const total = Math.floor(ms / 1000)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const pad = (n) => String(n).padStart(2, '0')
  return (h > 0 ? h + ':' : '') + pad(m) + ':' + pad(s)
}

async function load() {
  loading.value = true
  try {
    list.value = await api('/admin/instances')
  } catch (err) {
    ElMessage.error(err.message)
    list.value = []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load()
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div>
    <div class="toolbar" style="margin-top: 0">
      <el-tag size="small" effect="plain">全量 {{ stats.total }}</el-tag>
      <el-tag size="small" type="success">活跃 {{ stats.active }}</el-tag>
      <el-tag size="small" type="info">已过期 {{ stats.expired }}</el-tag>
      <el-select v-model="statusFilter" size="small" style="width: 150px" placeholder="全部状态">
        <el-option value="" label="全部状态" />
        <el-option value="RUNNING" label="运行中" />
        <el-option value="PENDING" label="调度中" />
        <el-option value="STOPPED" label="已销毁" />
        <el-option value="EXPIRED" label="已过期" />
        <el-option value="FAILED" label="失败" />
      </el-select>
      <el-button size="small" @click="load">刷新</el-button>
      <div class="spacer" />
      <span class="muted small">TTL 到期由后端每分钟扫表回收，前端剩余时长仅供参考</span>
    </div>

    <el-table v-loading="loading" :data="filtered" size="small" stripe>
      <el-table-column prop="challengeName" label="题目" min-width="160" show-overflow-tooltip />
      <el-table-column prop="ownerUsername" label="选手" width="140" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="地址" width="180">
        <template #default="{ row }">
          <span v-if="row.host" class="mono small">{{ row.host }}:{{ row.port }}</span>
          <span v-else class="muted small">—</span>
        </template>
      </el-table-column>
      <el-table-column label="容器" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="mono small">{{ row.containerId || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="剩余时长" width="130">
        <template #default="{ row }">
          <span class="mono small">{{ remaining(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="启动时间" width="170">
        <template #default="{ row }">
          <span class="muted small">{{ formatDate(row.startedAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="到期时间" width="170">
        <template #default="{ row }">
          <span class="muted small">{{ formatDate(row.expiresAt) }}</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
