<script setup>
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../shared/api.js'
import { formatDate } from '../../shared/format.js'

const events = ref([])
const currentEventId = ref('')
const list = ref([])
const loading = ref(false)
const statusFilter = ref('')
const detailVisible = ref(false)
const detail = ref(null)

function statusType(status) {
  if (status === 'ACCEPTED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'SUBMITTED') return 'warning'
  return 'info'
}

const filtered = () => {
  if (!statusFilter.value) return list.value
  return list.value.filter((w) => w.status === statusFilter.value)
}

async function loadEvents() {
  events.value = await api('/events')
}

async function loadList() {
  if (!currentEventId.value) return
  loading.value = true
  try {
    list.value = await api('/admin/writeups?eventId=' + encodeURIComponent(currentEventId.value))
  } catch (err) {
    ElMessage.error(err.message)
    list.value = []
  } finally {
    loading.value = false
  }
}

async function view(row) {
  try {
    detail.value = await api('/writeups/' + encodeURIComponent(row.id))
    detailVisible.value = true
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function moderate(row, status) {
  const action = status === 'ACCEPTED' ? '通过' : status === 'REJECTED' ? '驳回' : '重置为待审核'
  let note = ''
  if (status === 'REJECTED') {
    try {
      const res = await ElMessageBox.prompt('请填写驳回理由（可选）', '驳回 Writeup', {
        confirmButtonText: '确认驳回',
        cancelButtonText: '取消',
        inputPlaceholder: '如：内容不完整 / 与题目不符',
        inputType: 'textarea'
      })
      note = res.value || ''
    } catch (e) {
      return
    }
  } else {
    try {
      await ElMessageBox.confirm('确认' + action + '「' + row.title + '」？', action + ' Writeup', { type: 'warning' })
    } catch (e) {
      return
    }
  }
  try {
    await api('/admin/writeups/' + encodeURIComponent(row.id) + '/moderate', {
      method: 'PUT',
      body: { status, note }
    })
    ElMessage.success('已' + action)
    detailVisible.value = false
    await loadList()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

watch(currentEventId, () => {
  statusFilter.value = ''
  loadList()
})

onMounted(async () => {
  try {
    await loadEvents()
    const preferred =
      events.value.find((e) => e.type === 'COMPETITION' && e.status === 'ENDED') ||
      events.value.find((e) => e.type === 'PRACTICE') ||
      events.value[0]
    if (preferred) currentEventId.value = preferred.id
    await loadList()
  } catch (err) {
    ElMessage.error(err.message)
  }
})
</script>

<template>
  <div>
    <div class="toolbar" style="margin-top: 0">
      <el-select v-model="currentEventId" size="small" style="width: 280px">
        <el-option v-for="e in events" :key="e.id" :value="e.id" :label="'[' + e.typeLabel + '] ' + e.name" />
      </el-select>
      <el-select v-model="statusFilter" size="small" style="width: 150px" placeholder="全部状态">
        <el-option value="" label="全部状态" />
        <el-option value="SUBMITTED" label="待审核" />
        <el-option value="ACCEPTED" label="已通过" />
        <el-option value="REJECTED" label="已驳回" />
      </el-select>
      <el-button size="small" @click="loadList">刷新</el-button>
      <div class="spacer" />
      <span class="muted small">审核结果影响赛后公开可见性与「已采纳」标记</span>
    </div>

    <el-table v-loading="loading" :data="filtered()" size="small" stripe>
      <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
      <el-table-column label="作者" width="160">
        <template #default="{ row }">
          <span class="small">{{ row.displayName }}</span>
          <span class="muted small"> · {{ row.username }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">
          <span class="muted small">{{ formatDate(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="270" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="view(row)">查看</el-button>
          <el-button size="small" type="success" :disabled="row.status === 'ACCEPTED'" @click="moderate(row, 'ACCEPTED')">
            通过
          </el-button>
          <el-button size="small" type="danger" :disabled="row.status === 'REJECTED'" @click="moderate(row, 'REJECTED')">
            驳回
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailVisible" :title="detail ? detail.title : 'Writeup'" width="760px">
      <div v-if="detail">
        <div style="display: flex; gap: 8px; flex-wrap: wrap; align-items: center; margin-bottom: 12px">
          <el-tag size="small" effect="plain">{{ detail.challengeName }}</el-tag>
          <el-tag size="small" :type="statusType(detail.status)">{{ detail.statusLabel }}</el-tag>
          <span class="muted small">{{ detail.displayName }} · {{ formatDate(detail.createdAt) }}</span>
        </div>
        <pre style="white-space: pre-wrap; line-height: 1.7; background: var(--bg-card); padding: 14px; border-radius: 8px; margin: 0; max-height: 60vh; overflow: auto">{{ detail.markdown }}</pre>
        <div style="margin-top: 16px; display: flex; gap: 10px; justify-content: flex-end">
          <el-button v-if="detail.status !== 'ACCEPTED'" type="success" @click="moderate(detail, 'ACCEPTED')">通过</el-button>
          <el-button v-if="detail.status !== 'REJECTED'" type="danger" @click="moderate(detail, 'REJECTED')">驳回</el-button>
          <el-button v-if="detail.status !== 'SUBMITTED'" @click="moderate(detail, 'SUBMITTED')">重置待审核</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>
