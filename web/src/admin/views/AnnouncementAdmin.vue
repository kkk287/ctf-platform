<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../shared/api.js'
import { formatDate } from '../../shared/format.js'

const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const form = reactive({ title: '', content: '' })

async function load() {
  loading.value = true
  try {
    list.value = await api('/admin/announcements')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.title = ''
  form.content = ''
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.title = row.title
  form.content = row.content
  dialogVisible.value = true
}

async function save() {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.error('标题与内容不能为空')
    return
  }
  saving.value = true
  try {
    const body = { title: form.title.trim(), content: form.content.trim() }
    if (editingId.value) {
      await api('/admin/announcements/' + encodeURIComponent(editingId.value), { method: 'PUT', body })
    } else {
      await api('/admin/announcements', { method: 'POST', body })
    }
    ElMessage.success(editingId.value ? '公告已更新' : '公告已发布')
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
    await ElMessageBox.confirm('确认删除公告「' + row.title + '」？', '删除公告', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await api('/admin/announcements/' + encodeURIComponent(row.id), { method: 'DELETE' })
    ElMessage.success('公告已删除')
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
      <el-button type="primary" size="small" @click="openCreate">+ 发布公告</el-button>
      <el-button size="small" @click="load">刷新</el-button>
      <div class="spacer" />
      <span class="muted small">公告在选手端题目页顶部展示，按发布时间倒序</span>
    </div>

    <el-table v-loading="loading" :data="list" size="small" stripe>
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
      <el-table-column label="发布时间" width="170">
        <template #default="{ row }">
          <span class="muted small">{{ formatDate(row.createdAt, true) }}</span>
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
      :title="editingId ? '编辑公告' : '发布公告'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="100" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="6" maxlength="2000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">
          {{ editingId ? '保存' : '发布' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
