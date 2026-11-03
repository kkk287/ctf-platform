<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../shared/api.js'
import { formatDate } from '../../shared/format.js'

const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await api('/admin/anomalies')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar" style="margin-top: 0">
      <el-button size="small" @click="load">刷新</el-button>
      <div class="spacer" />
      <span class="muted small">同一 IP 在 10 分钟内对同一题目出现 ≥3 个不同账号的错误提交，判定为疑似异常</span>
    </div>

    <el-alert
      v-if="!loading && !list.length"
      title="当前没有异常告警"
      type="success"
      show-icon
      :closable="false"
    />

    <el-table v-if="list.length" v-loading="loading" :data="list" size="small" stripe>
      <el-table-column label="题目" min-width="160">
        <template #default="{ row }">
          <div style="font-weight: 600">{{ row.challengeName }}</div>
          <div class="muted small mono">{{ row.challengeId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="来源 IP" width="140">
        <template #default="{ row }"><span class="mono">{{ row.ip }}</span></template>
      </el-table-column>
      <el-table-column label="涉及账号" width="100">
        <template #default="{ row }">
          <el-tag type="danger" size="small">{{ row.distinctUsers }} 个</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="账号" min-width="160">
        <template #default="{ row }">
          <span class="small">{{ (row.usernames || []).join(', ') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="时间范围" min-width="160">
        <template #default="{ row }">
          <div class="mono small">{{ formatDate(row.firstAt, true) }}</div>
          <div class="mono small muted">→ {{ formatDate(row.lastAt, true) }}</div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
