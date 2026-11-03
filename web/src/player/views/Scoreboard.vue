<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, getUser } from '../../shared/api.js'
import { formatShort, relativeTime, eventStatusType } from '../../shared/format.js'
import { subscribe } from '../../shared/realtime.js'

const user = getUser()
const staff = user && (user.role === 'ADMIN' || user.role === 'JUDGE')

const events = ref([])
const currentEventId = ref('')
const type = ref('USER')
const limit = ref(20)
const data = ref(null)
const recent = ref([])
const errorMessage = ref('')
const loading = ref(false)
let timer = null
let offRealtime = null

const isTeam = computed(() => data.value && data.value.type === 'TEAM')

function blocked(e) {
  return e.type === 'COMPETITION' && e.status === 'NOT_STARTED' && !staff
}

async function loadEvents() {
  events.value = await api('/events')
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  const params = new URLSearchParams()
  if (currentEventId.value) params.set('eventId', currentEventId.value)
  params.set('type', type.value)
  params.set('limit', String(limit.value))
  try {
    const [board, recentList] = await Promise.all([
      api('/scoreboard?' + params.toString()),
      api(
        '/scoreboard/recent?limit=12' +
          (currentEventId.value ? '&eventId=' + encodeURIComponent(currentEventId.value) : '')
      )
    ])
    data.value = board
    currentEventId.value = board.event.id
    recent.value = recentList
  } catch (err) {
    errorMessage.value = err.message + '（错误码 ' + (err.code || '—') + '）'
    data.value = null
    recent.value = []
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await loadEvents()
  await load()
  ElMessage.success('排行榜已刷新')
}

function bindRealtime() {
  if (offRealtime) {
    offRealtime()
    offRealtime = null
  }
  if (!currentEventId.value) return
  offRealtime = subscribe('scoreboard:' + currentEventId.value, () => {
    if (!document.hidden) load()
  })
}

watch([type, limit], () => load())
watch(currentEventId, async () => {
  await load()
  bindRealtime()
})

function medalClass(index) {
  return ['top1', 'top2', 'top3'][index] || ''
}

onMounted(async () => {
  try {
    await loadEvents()
    const preferred =
      events.value.find((e) => e.type === 'COMPETITION' && e.status === 'ONGOING') ||
      events.value.find((e) => e.type === 'PRACTICE') ||
      events.value[0]
    if (preferred) currentEventId.value = preferred.id
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  }
  bindRealtime()
  // 30s 轮询兜底：WebSocket 断线或事件丢失时仍可刷新
  timer = setInterval(() => {
    if (!document.hidden) load()
  }, 30000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (offRealtime) offRealtime()
  offRealtime = null
})
</script>

<template>
  <div>
    <div class="toolbar" style="margin-top: 0">
      <el-radio-group v-model="currentEventId" size="small">
        <el-radio-button
          v-for="e in events"
          :key="e.id"
          :value="e.id"
          :disabled="blocked(e)"
          :title="blocked(e) ? '赛事尚未开始，榜单暂不可见' : ''"
        >
          [{{ e.typeLabel }}] {{ e.name }} · {{ e.statusLabel }}
        </el-radio-button>
      </el-radio-group>
      <div class="spacer" />
      <el-button size="small" @click="refresh">刷新</el-button>
    </div>

    <el-alert
      v-if="data && data.event.blackoutMode && !staff"
      title="黑灯模式进行中：当前仅显示你自己的名次与得分，解题动态已隐藏。"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 12px"
    />
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" style="margin-bottom: 12px" />

    <div class="toolbar">
      <el-radio-group v-model="type" size="small">
        <el-radio-button value="USER">个人榜</el-radio-button>
        <el-radio-button value="TEAM">战队榜</el-radio-button>
      </el-radio-group>
      <el-select v-model="limit" size="small" style="width: 120px">
        <el-option :value="20" label="Top 20" />
        <el-option :value="50" label="Top 50" />
        <el-option :value="100" label="Top 100" />
        <el-option :value="200" label="Top 200" />
      </el-select>
      <div class="spacer" />
      <span v-if="data" class="muted small">
        {{ data.event.name }} · {{ data.event.typeLabel }} ·
        <el-tag :type="eventStatusType(data.event.status)" size="small">{{ data.event.statusLabel }}</el-tag>
        <span v-if="isTeam"> · 战队榜按成员得分求和，同题只计最高</span>
        <span v-else> · 按总分降序，同分按最后得分时间升序</span>
      </span>
    </div>

    <el-card v-loading="loading" shadow="never" style="margin-bottom: 16px">
      <div v-if="data && data.entries.length" style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px">
        <div
          v-for="(e, i) in data.entries.slice(0, 3)"
          :key="e.id"
          style="border: 1px solid var(--border); border-radius: 10px; padding: 16px; text-align: center"
        >
          <div class="muted small mono">RANK #{{ e.rank }}</div>
          <div style="font-size: 18px; font-weight: 700; color: var(--text-title); margin: 6px 0">
            {{ e.name }}
          </div>
          <div class="points" style="color: var(--color-primary); font-weight: 700; font-size: 20px">
            {{ e.totalPoints }} <small>PTS</small>
          </div>
          <div class="muted small" style="margin-top: 6px">
            {{ isTeam ? (e.subtitle || '') : e.teamName || '个人参赛' }} · {{ e.solveCount }} 题
          </div>
        </div>
      </div>
      <div v-else-if="!loading" class="muted" style="padding: 20px; text-align: center">
        该榜单暂无得分记录
      </div>
    </el-card>

    <el-card shadow="never" style="margin-bottom: 16px">
      <el-table :data="data ? data.entries : []" size="small" stripe>
        <el-table-column label="名次" width="90">
          <template #default="{ row }">
            <span :class="row.rank <= 3 ? 'mono' : 'mono muted'" style="font-weight: 700">
              #{{ row.rank }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="isTeam ? '战队' : '选手'" min-width="180">
          <template #default="{ row }">
            <div style="font-weight: 600; color: var(--text-title)">{{ row.name }}</div>
            <div v-if="row.subtitle" class="muted small mono">{{ row.subtitle }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="isTeam ? '成员' : '战队'" min-width="120">
          <template #default="{ row }">
            <span class="muted small">{{ isTeam ? row.memberCount + ' 人' : row.teamName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="solveCount" label="解题数" width="90" />
        <el-table-column label="总分" width="120">
          <template #default="{ row }">
            <span style="color: var(--color-primary); font-weight: 700">{{ row.totalPoints }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最后得分" width="120">
          <template #default="{ row }">
            <span class="muted small">{{ formatShort(row.lastSolveAt) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div
        v-if="data"
        style="margin-top: 14px; display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap"
      >
        <div>
          <div class="muted small">{{ isTeam ? '我的战队名次' : '我的名次' }}</div>
          <div style="font-weight: 600; color: var(--text-title); margin-top: 3px">
            {{ data.me ? data.me.name : isTeam ? '尚未加入战队' : '尚未获得积分' }}
          </div>
        </div>
        <div v-if="data.me" style="display: flex; gap: 28px; align-items: center">
          <div style="text-align: right">
            <div class="muted small">总分</div>
            <div class="mono" style="font-size: 18px; color: var(--color-primary)">
              {{ data.me.totalPoints }}
            </div>
          </div>
          <div style="text-align: right">
            <div class="muted small">解题</div>
            <div class="mono" style="font-size: 18px">{{ data.me.solveCount }}</div>
          </div>
          <div class="mono" style="font-size: 22px; font-weight: 700">#{{ data.me.rank }}</div>
        </div>
        <div v-else class="mono muted" style="font-size: 22px">—</div>
      </div>
    </el-card>

    <el-card shadow="never">
      <p class="section-title">最近解题动态</p>
      <div v-if="!recent.length" class="muted small">暂无解题动态。</div>
      <div v-for="(s, i) in recent" :key="i" class="timeline-item">
        <strong>
          {{ s.displayName }}
          <el-tag v-if="s.bloodRank && s.bloodRank !== 'NONE'" type="warning" size="small">
            {{ s.bloodRankLabel }}
          </el-tag>
        </strong>
        <span>
          解出 <span class="mono">{{ s.challengeName }}</span> · {{ s.points }} PTS ·
          {{ relativeTime(s.solvedAt) }}
        </span>
      </div>
    </el-card>
  </div>
</template>
