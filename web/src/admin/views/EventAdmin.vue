<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../shared/api.js'
import { eventStatusType, eventTypeType, formatDate } from '../../shared/format.js'

const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)

const form = reactive({
  name: '',
  namespace: '',
  description: '',
  type: 'COMPETITION',
  startAt: null,
  endAt: null,
  blackoutMode: false
})

async function load() {
  loading.value = true
  try {
    list.value = await api('/admin/events')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    name: '',
    namespace: '',
    description: '',
    type: 'COMPETITION',
    startAt: null,
    endAt: null,
    blackoutMode: false
  })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    namespace: row.namespace || '',
    description: row.description || '',
    type: row.type,
    startAt: row.startAt,
    endAt: row.endAt,
    blackoutMode: !!row.blackoutMode
  })
  dialogVisible.value = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.error('名称不能为空')
    return
  }
  const ns = form.namespace.trim()
  if (ns && !/^[a-z0-9][a-z0-9-]{1,59}$/.test(ns)) {
    ElMessage.error('命名空间需以小写字母或数字开头，仅含小写字母/数字/中划线，长度 2-60')
    return
  }
  if (form.type === 'COMPETITION' && (!form.startAt || !form.endAt)) {
    ElMessage.error('比赛必须设置开始与结束时间')
    return
  }
  const payload = {
    name: form.name.trim(),
    namespace: ns || null,
    description: form.description,
    type: form.type,
    startAt: form.type === 'PRACTICE' ? null : form.startAt,
    endAt: form.type === 'PRACTICE' ? null : form.endAt,
    blackoutMode: form.blackoutMode
  }
  saving.value = true
  try {
    if (editingId.value) {
      await api('/admin/events/' + encodeURIComponent(editingId.value), { method: 'PUT', body: payload })
    } else {
      await api('/admin/events', { method: 'POST', body: payload })
    }
    ElMessage.success(editingId.value ? '赛事已保存' : '赛事已创建')
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
    await ElMessageBox.confirm('确认删除「' + row.name + '」？需先删除其下所有题目。', '删除赛事', {
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await api('/admin/events/' + encodeURIComponent(row.id), { method: 'DELETE' })
    ElMessage.success('已删除')
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
      <el-button type="primary" size="small" @click="openCreate">+ 新建赛事/练习区</el-button>
      <div class="spacer" />
      <span class="muted small">比赛到点截止；练习区长期开放且独立榜单</span>
    </div>

    <el-table v-loading="loading" :data="list" size="small" stripe>
      <el-table-column label="名称" min-width="240">
        <template #default="{ row }">
          <div style="font-weight: 600">{{ row.name }}</div>
          <div class="muted small">{{ row.description || '' }}</div>
          <div class="muted small mono">ns: {{ row.namespace || '—' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="eventTypeType(row.type)">{{ row.typeLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="eventStatusType(row.status)">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="黑灯" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.blackoutMode" size="small" type="warning">开启</el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="时间" min-width="180">
        <template #default="{ row }">
          <div v-if="row.type === 'PRACTICE'" class="muted small">长期开放</div>
          <div v-else class="mono small">
            {{ formatDate(row.startAt) }}<br />→ {{ formatDate(row.endAt) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="题目" width="110">
        <template #default="{ row }">
          <span class="mono">{{ row.challengeCount }}</span>
          <span class="muted small"> / 可见 {{ row.visibleChallengeCount }}</span>
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
      :title="editingId ? '编辑赛事/练习区' : '新建赛事/练习区'"
      width="620px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="名称">
          <el-input v-model="form.name" maxlength="80" />
        </el-form-item>
        <el-form-item label="命名空间（可选）">
          <el-input v-model="form.namespace" class="mono" maxlength="60" placeholder="留空默认与赛事 ID 相同" />
          <div class="muted small">
            小写字母 / 数字 / 中划线，全平台唯一；创建后不建议修改。
          </div>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option value="COMPETITION" label="赛事 COMPETITION（限时）" />
            <el-option value="PRACTICE" label="练习 Practice（长期开放）" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
        <div v-if="form.type === 'COMPETITION'" style="display: flex; gap: 12px">
          <el-form-item label="开始时间" style="flex: 1">
            <el-date-picker
              v-model="form.startAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="结束时间" style="flex: 1">
            <el-date-picker
              v-model="form.endAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
        </div>
        <div class="muted small" style="margin-bottom: 12px">
          {{
            form.type === 'PRACTICE'
              ? '练习区长期开放，不受时间限制，独立计分与独立榜单。'
              : '比赛必须设置开始/结束时间；未开始时题目对选手不可见，结束后只能查看不能提交。'
          }}
        </div>
        <el-form-item>
          <el-checkbox v-model="form.blackoutMode">开启黑灯模式</el-checkbox>
          <div class="muted small">
            开启后选手仅能看到自己的名次与得分，解题动态隐藏；管理员 / 裁判不受限制。
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">
          {{ editingId ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
