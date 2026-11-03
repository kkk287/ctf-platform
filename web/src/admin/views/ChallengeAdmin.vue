<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../shared/api.js'
import { CATEGORIES, DIFFICULTIES, difficultyType } from '../../shared/format.js'

const list = ref([])
const events = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const fileInput = ref(null)

const form = reactive({
  eventId: '',
  name: '',
  description: '',
  category: 'WEB',
  difficulty: 'MEDIUM',
  initialPoints: 100,
  minPoints: null,
  decayPoints: null,
  visible: true,
  flag: ''
})

const hints = ref([])
const hintForm = reactive({ content: '', cost: 10 })
const attachmentName = ref('')

async function loadEvents() {
  if (events.value.length) return events.value
  events.value = await api('/admin/events')
  return events.value
}

async function load() {
  loading.value = true
  try {
    list.value = await api('/admin/challenges')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    eventId: '',
    name: '',
    description: '',
    category: 'WEB',
    difficulty: 'MEDIUM',
    initialPoints: 100,
    minPoints: null,
    decayPoints: null,
    visible: true,
    flag: ''
  })
  hints.value = []
  hintForm.content = ''
  hintForm.cost = 10
  attachmentName.value = ''
}

async function openCreate() {
  try {
    await loadEvents()
  } catch (err) {
    ElMessage.error(err.message)
    return
  }
  editingId.value = null
  resetForm()
  if (events.value.length) form.eventId = events.value[0].id
  dialogVisible.value = true
}

async function openEdit(row) {
  try {
    await loadEvents()
  } catch (err) {
    ElMessage.error(err.message)
    return
  }
  editingId.value = row.id
  resetForm()
  Object.assign(form, {
    eventId: row.eventId,
    name: row.name,
    description: row.description || '',
    category: row.category,
    difficulty: row.difficulty,
    initialPoints: row.initialPoints,
    minPoints: row.minPoints,
    decayPoints: row.decayPoints,
    visible: row.visible
  })
  attachmentName.value = row.attachmentName || ''
  dialogVisible.value = true
  loadHints(row.id)
}

async function loadHints(challengeId) {
  try {
    const detail = await api('/challenges/' + encodeURIComponent(challengeId))
    hints.value = detail.hints || []
  } catch (err) {
    hints.value = []
  }
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.error('题目名称不能为空')
    return
  }
  if (!editingId.value && !form.flag.trim()) {
    ElMessage.error('新建题目必须设置 flag')
    return
  }
  if (!Number.isFinite(Number(form.initialPoints)) || Number(form.initialPoints) < 10) {
    ElMessage.error('初始分值至少为 10')
    return
  }
  const payload = {
    eventId: form.eventId,
    name: form.name.trim(),
    description: form.description,
    category: form.category,
    difficulty: form.difficulty,
    initialPoints: Number(form.initialPoints),
    minPoints: form.minPoints === null || form.minPoints === '' ? null : Number(form.minPoints),
    decayPoints: form.decayPoints === null || form.decayPoints === '' ? null : Number(form.decayPoints),
    visible: form.visible,
    flag: form.flag.trim() ? form.flag.trim() : null
  }
  saving.value = true
  try {
    const saved = editingId.value
      ? await api('/admin/challenges/' + encodeURIComponent(editingId.value), { method: 'PUT', body: payload })
      : await api('/admin/challenges', { method: 'POST', body: payload })

    const file = fileInput.value && fileInput.value.files ? fileInput.value.files[0] : null
    if (file) {
      const fd = new FormData()
      fd.append('file', file)
      await api('/admin/challenges/' + encodeURIComponent(saved.id) + '/attachment', {
        method: 'POST',
        body: fd
      })
    }
    ElMessage.success(editingId.value ? '题目已保存' : '题目已创建')
    dialogVisible.value = false
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('确认删除题目「' + row.name + '」？该操作不可恢复。', '删除题目', {
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await api('/admin/challenges/' + encodeURIComponent(row.id), { method: 'DELETE' })
    ElMessage.success('题目已删除')
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function addHint() {
  const text = hintForm.content.trim()
  if (!text) {
    ElMessage.error('提示内容不能为空')
    return
  }
  try {
    await api('/admin/challenges/' + encodeURIComponent(editingId.value) + '/hints', {
      method: 'POST',
      body: { content: text, cost: Number(hintForm.cost || 0) }
    })
    hintForm.content = ''
    ElMessage.success('提示已添加')
    await loadHints(editingId.value)
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function removeHint(hint) {
  try {
    await api(
      '/admin/challenges/' + encodeURIComponent(editingId.value) + '/hints/' + encodeURIComponent(hint.id),
      { method: 'DELETE' }
    )
    ElMessage.success('提示已删除')
    await loadHints(editingId.value)
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function removeAttachment() {
  try {
    await ElMessageBox.confirm('确认删除该题目的附件？', '删除附件', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await api('/admin/challenges/' + encodeURIComponent(editingId.value) + '/attachment', { method: 'DELETE' })
    ElMessage.success('附件已删除')
    attachmentName.value = ''
    dialogVisible.value = false
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar" style="margin-top: 0">
      <el-button type="primary" size="small" @click="openCreate">+ 新建题目</el-button>
      <div class="spacer" />
      <span class="muted small">flag 只保存加盐哈希，编辑时留空表示不修改</span>
    </div>

    <el-table v-loading="loading" :data="list" size="small" stripe>
      <el-table-column label="题目" min-width="200">
        <template #default="{ row }">
          <div style="font-weight: 600">{{ row.name }}</div>
          <div style="display: flex; gap: 6px; margin-top: 4px">
            <el-tag size="small" effect="plain">{{ row.categoryLabel }}</el-tag>
            <el-tag size="small" :type="difficultyType(row.difficulty)">{{ row.difficultyLabel }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="eventName" label="赛事/练习" min-width="140" />
      <el-table-column label="分值" width="100">
        <template #default="{ row }">
          <span class="mono">{{ row.currentPoints }}</span>
          <div class="muted small">初始 {{ row.initialPoints }}</div>
        </template>
      </el-table-column>
      <el-table-column label="可见" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.visible ? 'success' : 'info'">
            {{ row.visible ? '可见' : '隐藏' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Flag" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.flagConfigured ? 'primary' : 'danger'">
            {{ row.flagConfigured ? '已配置' : '未配置' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="solveCount" label="解出" width="70" />
      <el-table-column label="附件" min-width="140">
        <template #default="{ row }">
          <span v-if="row.attachmentName" class="small">{{ row.attachmentName }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑题目' : '新建题目'"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="所属赛事/练习">
          <el-select v-model="form.eventId" style="width: 100%">
            <el-option
              v-for="e in events"
              :key="e.id"
              :value="e.id"
              :label="'[' + e.typeLabel + '] ' + e.name"
            />
          </el-select>
        </el-form-item>
        <div style="display: flex; gap: 12px">
          <el-form-item label="分类" style="flex: 1">
            <el-select v-model="form.category" style="width: 100%">
              <el-option v-for="[v, l] in CATEGORIES" :key="v" :value="v" :label="l" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度" style="flex: 1">
            <el-select v-model="form.difficulty" style="width: 100%">
              <el-option v-for="[v, l] in DIFFICULTIES" :key="v" :value="v" :label="l" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="题目名称">
          <el-input v-model="form.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="题目描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="3000" />
        </el-form-item>
        <div style="display: flex; gap: 12px">
          <el-form-item label="初始分值" style="flex: 1">
            <el-input-number v-model="form.initialPoints" :min="10" :max="10000" style="width: 100%" />
          </el-form-item>
          <el-form-item label="最低分（可选）" style="flex: 1">
            <el-input-number v-model="form.minPoints" :min="0" style="width: 100%" />
          </el-form-item>
          <el-form-item label="衰减值（可选）" style="flex: 1">
            <el-input-number v-model="form.decayPoints" :min="0" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item :label="editingId ? 'flag（留空保持原值）' : 'flag（必填）'">
          <el-input
            v-model="form.flag"
            class="mono"
            maxlength="200"
            :placeholder="editingId ? '留空则不修改' : '例如 flag{demo_2026}'"
          />
        </el-form-item>
        <el-form-item label="附件（可选，最大 20MB）">
          <div style="width: 100%">
            <input ref="fileInput" type="file" />
            <div v-if="attachmentName" style="margin-top: 6px; display: flex; align-items: center; gap: 10px">
              <span class="muted small">当前：{{ attachmentName }}</span>
              <el-button size="small" type="danger" @click="removeAttachment">删除现有附件</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.visible">对选手可见</el-checkbox>
        </el-form-item>
      </el-form>

      <el-divider v-if="editingId" />
      <div v-if="editingId">
        <p class="section-title">提示管理</p>
        <div v-if="!hints.length" class="muted small">暂无提示。</div>
        <div
          v-for="h in hints"
          :key="h.id"
          style="display: flex; justify-content: space-between; align-items: center; gap: 10px; padding: 7px 0; border-bottom: 1px dashed var(--border)"
        >
          <span class="small">{{ h.content }} <span class="muted">（扣 {{ h.cost }} 分）</span></span>
          <el-button size="small" type="danger" @click="removeHint(h)">删除</el-button>
        </div>
        <div style="display: flex; gap: 10px; margin-top: 12px">
          <el-input v-model="hintForm.content" maxlength="500" placeholder="提示内容" />
          <el-input-number v-model="hintForm.cost" :min="0" style="width: 130px" />
          <el-button @click="addHint">添加提示</el-button>
        </div>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">
          {{ editingId ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
