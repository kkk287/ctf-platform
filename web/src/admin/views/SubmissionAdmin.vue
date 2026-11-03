<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../shared/api.js'
import { formatDate } from '../../shared/format.js'

const page = ref(1)
const size = 20
const resultFilter = ref('')
const result = ref({ items: [], page: 0, total: 0, totalPages: 0 })
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    result.value = await api(
      '/admin/submissions?page=' + (page.value - 1) + '&size=' + size +
        (resultFilter.value ? '&result=' + resultFilter.value : '')
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
      <el-select v-model="resultFilter" size="small" style="width: 150px" @change="changeFilter">
        <el-option value="" label="全部结果" />
        <el-option value="CORRECT" label="正确 CORRECT" />
        <el-option value="WRONG" label="错误 WRONG" />
      </el-select>
      <el-button size="small" @click="load">刷新</el-button>
      <div class="spacer" />
      <span class="muted small">记录 IP / UA / 时间 / 判分结果，错误提交同样留痕</span>
    </div>

    <el-table v-loading="loading" :data="result.items" size="small" stripe>
      <el-table-column label="时间" width="160">
        <template #default="{ row }">
          <span class="muted small">{{ formatDate(row.createdAt, true) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="用户" width="120" />
      <el-table-column prop="challengeName" label="题目" min-width="150" />
      <el-table-column label="结果" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.result === 'CORRECT' ? 'success' : 'danger'">
            {{ row.resultLabel || row.result }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column prop="userAgent" label="User-Agent" min-width="200" show-overflow-tooltip />
      <el-table-column prop="submittedHashPrefix" label="摘要" width="120" />
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
