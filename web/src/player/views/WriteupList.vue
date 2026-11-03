<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, getUser } from '../../shared/api.js'
import { formatDate } from '../../shared/format.js'

const me = getUser()
const events = ref([])
const currentEventId = ref('')
const challenges = ref([])
const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const form = reactive({ challengeId: '', title: '', markdown: '' })
const detailVisible = ref(false)
const detail = ref(null)

function statusType(status) {
  if (status === 'ACCEPTED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'SUBMITTED') return 'warning'
  return 'info'
}

async function loadEvents() {
  events.value = await api('/events')
}

async function loadChallenges() {
  if (!currentEventId.value) return
  try {
    const data = await api('/challenges?eventId=' + encodeURIComponent(currentEventId.value))
    challenges.value = data.challenges
  } catch (err) {
    challenges.value = []
  }
}

async function loadList() {
  if (!currentEventId.value) return
  loading.value = true
  try {
    list.value = await api('/events/' + encodeURIComponent(currentEventId.value) + '/writeups')
  } catch (err) {
    ElMessage.error(err.message)
    list.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { challengeId: challenges.value[0] ? challenges.value[0].id : '', title: '', markdown: '' })
  dialogVisible.value = true
}

async function openEdit(row) {
  try {
    const data = await api('/writeups/' + encodeURIComponent(row.id))
    editingId.value = row.id
    Object.assign(form, {
      challengeId: data.challengeId,
      title: data.title,
      markdown: data.markdown || ''
    })
    dialogVisible.value = true
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function save() {
  if (!form.challengeId) {
    ElMessage.error('请选择关联题目')
    return
  }
  if (!form.title.trim()) {
    ElMessage.error('标题不能为空')
    return
  }
  saving.value = true
  try {
    const body = { challengeId: form.challengeId, title: form.title.trim(), markdown: form.markdown }
    if (editingId.value) {
      await api('/writeups/' + encodeURIComponent(editingId.value), { method: 'PUT', body })
    } else {
      await api('/events/' + encodeURIComponent(currentEventId.value) + '/writeups', { method: 'POST', body })
    }
    ElMessage.success(editingId.value ? 'Writeup 已更新' : 'Writeup 已提交')
    dialogVisible.value = false
    await loadList()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('确认删除 Writeup「' + row.title + '」？', '删除 Writeup', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await api('/writeups/' + encodeURIComponent(row.id), { method: 'DELETE' })
    ElMessage.success('已删除')
    await loadList()
  } catch (err) {
    ElMessage.error(err.message)
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

watch(currentEventId, async () => {
  await loadChallenges()
  await loadList()
})

onMounted(async () => {
  try {
    await loadEvents()
    const preferred =
      events.value.find((e) => e.type === 'COMPETITION' && e.status === 'ENDED') ||
      events.value.find((e) => e.type === 'PRACTICE') ||
      events.value[0]
    if (preferred) currentEventId.value = preferred.id
    await loadChallenges()
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
      <el-button size="small" @click="loadList">刷新</el-button>
      <div class="spacer" />
      <span class="muted small">Writeup 提交后可编辑；赛后对外开放查看，管理员审核通过后标记为已采纳</span>
      <el-button type="primary" size="small" @click="openCreate">+ 提交 Writeup</el-button>
    </div>

    <el-table v-loading="loading" :data="list" size="small" stripe>
      <el-table-column prop="title" label="标题" min-width="220" />
      <el-table-column label="作者" width="140">
        <template #default="{ row }">
          <span class="small">{{ row.displayName }}</span>
          <span v-if="row.userId === me.id" class="muted small">（我）</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="160">
        <template #default="{ row }">
          <span class="muted small">{{ formatDate(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="view(row)">查看</el-button>
          <el-button v-if="row.userId === me.id" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.userId === me.id" size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑 Writeup' : '提交 Writeup'"
      width="680px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="关联题目">
          <el-select v-model="form.challengeId" style="width: 100%" :disabled="!!editingId">
            <el-option
              v-for="c in challenges"
              :key="c.id"
              :value="c.id"
              :label="'[' + c.categoryLabel + '] ' + c.name + (c.solved ? ' · 已解出' : '')"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="120" />
        </el-form-item>
        <el-form-item label="正文（Markdown）">
          <el-input v-model="form.markdown" type="textarea" :rows="14" placeholder="支持 Markdown，可附代码块与思路说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ editingId ? '保存' : '提交' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" :title="detail ? detail.title : 'Writeup'" width="720px">
      <div v-if="detail">
        <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px">
          <el-tag size="small" effect="plain">{{ detail.challengeName }}</el-tag>
          <el-tag size="small" :type="statusType(detail.status)">{{ detail.statusLabel }}</el-tag>
          <span class="muted small">{{ detail.displayName }} · {{ formatDate(detail.createdAt) }}</span>
        </div>
        <pre style="white-space: pre-wrap; line-height: 1.7; background: var(--bg-card); padding: 14px; border-radius: 8px; margin: 0">{{ detail.markdown }}</pre>
      </div>
    </el-dialog>
  </div>
</template>
