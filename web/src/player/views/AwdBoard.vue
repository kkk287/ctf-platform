<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../shared/api.js'
import { subscribe } from '../../shared/realtime.js'

const events = ref([])
const currentEventId = ref('')
const rounds = ref([])
const currentRoundId = ref('')
const board = ref(null)
const loading = ref(false)
const flagInput = ref('')
const submitting = ref(false)
const now = ref(Date.now())
let clockTimer = null
let pollTimer = null
let offRealtime = null

const currentRound = computed(() => rounds.value.find((r) => r.id === currentRoundId.value) || null)

const remainingText = computed(() => {
  if (!board.value) return '—'
  if (board.value.status !== 'RUNNING') {
    return board.value.remainingSeconds + 's'
  }
  const endsAt = board.value.roundEndsAt ? new Date(board.value.roundEndsAt).getTime() : 0
  const ms = endsAt ? endsAt - now.value : board.value.remainingSeconds * 1000
  if (ms <= 0) return '00:00'
  const total = Math.floor(ms / 1000)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const pad = (n) => String(n).padStart(2, '0')
  return (h > 0 ? h + ':' : '') + pad(m) + ':' + pad(s)
})

async function loadEvents() {
  events.value = await api('/events')
}

async function loadRounds() {
  if (!currentEventId.value) return
  try {
    rounds.value = await api('/awd/rounds?eventId=' + encodeURIComponent(currentEventId.value))
    if (!rounds.value.some((r) => r.id === currentRoundId.value)) {
      const preferred =
        rounds.value.find((r) => r.status === 'RUNNING') ||
        rounds.value.find((r) => r.status === 'PAUSED') ||
        rounds.value[0]
      currentRoundId.value = preferred ? preferred.id : ''
    }
  } catch (err) {
    ElMessage.error(err.message)
    rounds.value = []
  }
}

async function loadBoard() {
  if (!currentRoundId.value) {
    board.value = null
    return
  }
  loading.value = true
  try {
    board.value = await api('/awd/rounds/' + encodeURIComponent(currentRoundId.value) + '/scoreboard')
  } catch (err) {
    ElMessage.error(err.message)
    board.value = null
  } finally {
    loading.value = false
  }
}

function bindRealtime() {
  if (offRealtime) {
    offRealtime()
    offRealtime = null
  }
  if (!currentRoundId.value) return
  offRealtime = subscribe('awd:' + currentRoundId.value, () => {
    if (!document.hidden) loadBoard()
  })
}

async function submitFlag() {
  if (!board.value || !flagInput.value.trim()) {
    ElMessage.error('请输入 flag')
    return
  }
  submitting.value = true
  try {
    const result = await api('/awd/rounds/' + encodeURIComponent(currentRoundId.value) + '/attack', {
      method: 'POST',
      body: { flag: flagInput.value.trim() }
    })
    ElMessage.success('攻击成功，+' + result.points + ' 分（被攻击方：' + result.victimTeamName + '）')
    flagInput.value = ''
    await loadBoard()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    submitting.value = false
  }
}

async function copyFlag() {
  if (!board.value?.myFlag) return
  try {
    await navigator.clipboard.writeText(board.value.myFlag)
    ElMessage.success('本队 flag 已复制')
  } catch (e) {
    ElMessage.info(board.value.myFlag)
  }
}

const statusType = (status) =>
  ({ RUNNING: 'success', PAUSED: 'warning', SCORING: 'primary', PREP: 'info', FINISHED: 'info' }[status] || 'info')

function roundLabel(round) {
  return '第 ' + round.currentRound + '/' + round.totalRounds + ' 轮 · ' + round.statusLabel
}

watch(currentEventId, async () => {
  currentRoundId.value = ''
  await loadRounds()
  await loadBoard()
  bindRealtime()
})

watch(currentRoundId, async () => {
  await loadBoard()
  bindRealtime()
})

onMounted(async () => {
  try {
    await loadEvents()
    const preferred =
      events.value.find((e) => e.type === 'COMPETITION' && e.status === 'ONGOING') ||
      events.value.find((e) => e.type === 'PRACTICE') ||
      events.value[0]
    if (preferred) currentEventId.value = preferred.id
    await loadRounds()
    await loadBoard()
    bindRealtime()
  } catch (err) {
    ElMessage.error(err.message)
  }
  clockTimer = setInterval(() => (now.value = Date.now()), 1000)
  // 30s 轮询兜底
  pollTimer = setInterval(() => {
    if (!document.hidden) {
      loadRounds()
      loadBoard()
    }
  }, 30000)
})

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
  if (pollTimer) clearInterval(pollTimer)
  if (offRealtime) offRealtime()
  offRealtime = null
})
</script>

<template>
  <div class="page">
    <div class="toolbar" style="margin-top: 0">
      <el-select v-model="currentEventId" size="small" style="width: 260px">
        <el-option v-for="e in events" :key="e.id" :value="e.id" :label="'[' + e.typeLabel + '] ' + e.name" />
      </el-select>
      <el-select v-model="currentRoundId" size="small" style="width: 300px" placeholder="选择 AWD 轮次">
        <el-option v-for="r in rounds" :key="r.id" :value="r.id" :label="r.name + ' · ' + roundLabel(r)" />
      </el-select>
      <el-button size="small" @click="loadBoard">刷新</el-button>
      <div class="spacer" />
      <span class="muted small">实时比分经 WebSocket 推送，30 秒轮询兜底</span>
    </div>

    <el-empty v-if="!board" description="暂无可参与的 AWD 轮次" />

    <div v-else v-loading="loading">
      <el-card shadow="never" style="margin-bottom: 16px">
        <div style="display: flex; flex-wrap: wrap; gap: 14px; align-items: center">
          <div>
            <div class="section-title" style="margin: 0">{{ board.roundName }}</div>
            <div class="muted small">
              第 {{ board.currentRound }} / {{ board.totalRounds }} 轮 · 剩余
              <span class="mono" style="color: var(--color-primary); font-size: 18px">{{ remainingText }}</span>
            </div>
          </div>
          <el-tag size="small" :type="statusType(board.status)">{{ board.statusLabel }}</el-tag>
          <el-tag v-if="board.simulateChecks" size="small" type="warning" effect="plain">无 Docker：check 为模拟探活</el-tag>
          <div class="spacer" />
          <div class="muted small">我的队伍：{{ board.myTeamName || '未分配' }}</div>
        </div>

        <template v-if="board.myTeamId">
          <el-divider />
          <div style="display: flex; flex-wrap: wrap; gap: 12px; align-items: center">
            <span class="muted small">本队当前 flag（第 {{ board.myFlagRound }} 轮）</span>
            <span class="mono" style="font-size: 13px">{{ board.myFlag || '未下发' }}</span>
            <el-button size="small" @click="copyFlag" :disabled="!board.myFlag">复制</el-button>
          </div>
          <div style="display: flex; gap: 10px; margin-top: 14px">
            <el-input
              v-model="flagInput"
              placeholder="粘贴其他队伍的 flag，例：flag{awd_r1_xxxxxxxx_...}"
              :disabled="board.status !== 'RUNNING'"
              @keyup.enter="submitFlag"
            />
            <el-button
              type="primary"
              :loading="submitting"
              :disabled="board.status !== 'RUNNING'"
              @click="submitFlag"
            >
              提交 flag
            </el-button>
          </div>
          <div v-if="board.status !== 'RUNNING'" class="muted small" style="margin-top: 8px">
            当前轮次{{ board.statusLabel }}，无法提交 flag
          </div>
        </template>
      </el-card>

      <el-card shadow="never" style="margin-bottom: 16px">
        <p class="section-title">实时比分</p>
        <el-table :data="board.teams" size="small" stripe>
          <el-table-column label="#" width="60">
            <template #default="{ row }">{{ row.rank }}</template>
          </el-table-column>
          <el-table-column prop="teamName" label="队伍" min-width="160">
            <template #default="{ row }">
              <strong v-if="row.teamId === board.myTeamId">{{ row.teamName }}（我）</strong>
              <span v-else>{{ row.teamName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="攻击" width="100">
            <template #default="{ row }"><span class="mono" style="color: #16a34a">+{{ row.attackGain }}</span></template>
          </el-table-column>
          <el-table-column label="失守" width="100">
            <template #default="{ row }"><span class="mono" style="color: #dc2626">-{{ row.defenseLoss }}</span></template>
          </el-table-column>
          <el-table-column label="宕机" width="100">
            <template #default="{ row }"><span class="mono" style="color: #d97706">-{{ row.downtimeLoss }}</span></template>
          </el-table-column>
          <el-table-column label="总分" width="100">
            <template #default="{ row }"><strong class="mono">{{ row.totalScore }}</strong></template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never">
        <p class="section-title">轮次明细</p>
        <el-table :data="board.rounds" size="small" stripe max-height="360">
          <el-table-column prop="roundNo" label="轮次" width="80" />
          <el-table-column prop="teamName" label="队伍" min-width="160" />
          <el-table-column label="攻击" width="100">
            <template #default="{ row }"><span class="mono">+{{ row.attackGain }}</span></template>
          </el-table-column>
          <el-table-column label="失守" width="100">
            <template #default="{ row }"><span class="mono">-{{ row.defenseLoss }}</span></template>
          </el-table-column>
          <el-table-column label="宕机" width="100">
            <template #default="{ row }"><span class="mono">-{{ row.downtimeLoss }}</span></template>
          </el-table-column>
          <el-table-column label="本轮回合计分" width="120">
            <template #default="{ row }"><strong class="mono">{{ row.roundScore }}</strong></template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>
