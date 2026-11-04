<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { api, getToken } from '../shared/api.js'
import { countdown, relativeTime, formatShort, eventStatusType } from '../shared/format.js'
import { subscribe, onRealtimeStatus } from '../shared/realtime.js'

const events = ref([])
const currentEventId = ref('')
const type = ref('USER')
const board = ref(null)
const recent = ref([])
const loading = ref(false)
const lastUpdated = ref(null)
const clock = ref(new Date())
const announcements = ref([])
const announcementIndex = ref(0)
const realtimeStatus = ref('disconnected')
const awdBoard = ref(null)
const awdRoundId = ref('')
let offAwd = null
let pollTimer = null
let clockTimer = null
let announcementTimer = null
let offScoreboard = null
let offSolves = null
let offAnnouncement = null
let offStatus = null

const isTeam = computed(() => board.value && board.value.type === 'TEAM')
const currentAnnouncement = computed(() =>
  announcements.value.length ? announcements.value[announcementIndex.value % announcements.value.length] : null
)
const top3 = computed(() => (board.value ? board.value.entries.slice(0, 3) : []))
const rest = computed(() => (board.value ? board.value.entries.slice(3) : []))
const totalSolves = computed(() =>
  board.value ? board.value.entries.reduce((sum, e) => sum + (e.solveCount || 0), 0) : 0
)

/** AWD 剩余时间：RUNNING 用 roundEndsAt 本地倒计时，暂停/结算显示快照值 */
const awdRemaining = computed(() => {
  const b = awdBoard.value
  if (!b) return '—'
  if (b.status !== 'RUNNING' || !b.roundEndsAt) return b.remainingSeconds + 's'
  const diff = new Date(b.roundEndsAt).getTime() - clock.value.getTime()
  if (diff <= 0) return '00:00'
  const total = Math.floor(diff / 1000)
  const pad = (n) => String(n).padStart(2, '0')
  const h = Math.floor(total / 3600)
  return (h > 0 ? h + ':' : '') + pad(Math.floor((total % 3600) / 60)) + ':' + pad(total % 60)
})

const awdStatusType = (status) =>
  ({ RUNNING: 'success', PAUSED: 'warning', SCORING: 'primary', PREP: 'info', FINISHED: 'info' }[status] || 'info')

async function loadEvents() {
  events.value = await api('/events', { authRedirect: false })
}

async function loadBoard() {
  if (!currentEventId.value) return
  loading.value = true
  try {
    const params = new URLSearchParams()
    params.set('eventId', currentEventId.value)
    params.set('type', type.value)
    params.set('limit', '15')
    const [data, recentList] = await Promise.all([
      api('/scoreboard?' + params.toString(), { authRedirect: false }),
      api(
        '/scoreboard/recent?limit=15&eventId=' + encodeURIComponent(currentEventId.value),
        { authRedirect: false }
      )
    ])
    board.value = data
    recent.value = recentList
    lastUpdated.value = new Date()
  } catch (err) {
    board.value = null
    recent.value = []
  } finally {
    loading.value = false
  }
}

async function loadAnnouncements() {
  try {
    const list = await api('/announcements', { authRedirect: false })
    announcements.value = list || []
  } catch (err) {
    announcements.value = []
  }
}

async function loadAwd() {
  if (!currentEventId.value) return
  try {
    // 未登录大屏走公开只读接口（不含 flag/实例地址），且不建立 WebSocket 订阅
    if (!getToken()) {
      const publicBoard = await api(
        '/public/awd/rounds/current?eventId=' + encodeURIComponent(currentEventId.value),
        { authRedirect: false }
      )
      awdBoard.value = publicBoard || null
      if (awdRoundId.value) {
        awdRoundId.value = ''
        bindAwd()
      }
      return
    }
    const rounds = await api(
      '/awd/rounds?eventId=' + encodeURIComponent(currentEventId.value),
      { authRedirect: false }
    )
    const list = rounds || []
    const pick =
      list.find((r) => r.status === 'RUNNING') ||
      list.find((r) => r.status === 'PAUSED') ||
      list.find((r) => r.status === 'SCORING') ||
      list.find((r) => r.status === 'PREP') ||
      list[0]
    if (!pick) {
      awdRoundId.value = ''
      awdBoard.value = null
      bindAwd()
      return
    }
    if (awdRoundId.value !== pick.id) {
      awdRoundId.value = pick.id
      bindAwd()
    }
    awdBoard.value = await api('/awd/rounds/' + encodeURIComponent(pick.id) + '/scoreboard', {
      authRedirect: false
    })
  } catch (err) {
    awdBoard.value = null
  }
}

function bindAwd() {
  if (offAwd) {
    offAwd()
    offAwd = null
  }
  if (!awdRoundId.value) return
  offAwd = subscribe('awd:' + awdRoundId.value, () => {
    if (!document.hidden) loadAwd()
  })
}

function bindRealtime() {
  if (offScoreboard) {
    offScoreboard()
    offScoreboard = null
  }
  if (offSolves) {
    offSolves()
    offSolves = null
  }
  if (!currentEventId.value) return
  const eventId = currentEventId.value
  offScoreboard = subscribe('scoreboard:' + eventId, () => {
    if (!document.hidden) loadBoard()
  })
  offSolves = subscribe('solves:' + eventId, () => {
    if (!document.hidden) loadBoard()
  })
}

function bindAnnouncement() {
  if (offAnnouncement) return
  offAnnouncement = subscribe('announcement', () => {
    if (!document.hidden) loadAnnouncements()
  })
}

function schedule() {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = setInterval(() => {
    if (!document.hidden) {
      loadBoard()
      loadAnnouncements()
      loadAwd()
    }
  }, 30000)
  if (announcementTimer) clearInterval(announcementTimer)
  announcementTimer = setInterval(() => {
    if (announcements.value.length > 1) {
      announcementIndex.value = (announcementIndex.value + 1) % announcements.value.length
    }
  }, 8000)
}

onMounted(async () => {
  try {
    await loadEvents()
  } catch (err) {
    events.value = []
  }
  const preferred =
    events.value.find((e) => e.type === 'COMPETITION' && e.status === 'ONGOING') ||
    events.value.find((e) => e.type === 'PRACTICE') ||
    events.value[0]
  if (preferred) {
    currentEventId.value = preferred.id
    await loadBoard()
  }
  schedule()
  loadAnnouncements()
  loadAwd()
  bindRealtime()
  bindAnnouncement()
  offStatus = onRealtimeStatus((status) => (realtimeStatus.value = status))
  clockTimer = setInterval(() => (clock.value = new Date()), 1000)
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (clockTimer) clearInterval(clockTimer)
  if (announcementTimer) clearInterval(announcementTimer)
  if (offScoreboard) offScoreboard()
  if (offSolves) offSolves()
  if (offAnnouncement) offAnnouncement()
  if (offStatus) offStatus()
  if (offAwd) offAwd()
  offScoreboard = null
  offSolves = null
  offAnnouncement = null
  offStatus = null
  offAwd = null
})

function selectEvent(id) {
  currentEventId.value = id
  loadBoard()
  loadAwd()
  bindRealtime()
}

function selectType(value) {
  type.value = value
  loadBoard()
}

function rankClass(index) {
  return ['top1', 'top2', 'top3'][index] || ''
}
</script>

<template>
  <el-config-provider :locale="zhCn">
  <div class="screen-body" style="padding: 22px 28px 28px">
    <header style="display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 18px">
      <div style="display: flex; align-items: center; gap: 14px">
        <span class="brand-mark">./ctf</span>
        <div>
          <div style="font-size: 22px; font-weight: 700; color: #fff; letter-spacing: 2px">
            CTF 实时态势大屏
          </div>
          <div style="font-size: 12px; color: #7d90b8; letter-spacing: 3px">REAL-TIME SITUATION SCREEN</div>
        </div>
      </div>

      <div style="display: flex; align-items: center; gap: 12px">
        <span
          :title="realtimeStatus === 'connected' ? 'WebSocket 已连接，事件实时推送' : 'WebSocket 未连接，已回退 30s 轮询'"
          :style="{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            fontSize: '12px',
            color: realtimeStatus === 'connected' ? '#4ade80' : '#fbbf24'
          }"
        >
          <span
            :style="{
              width: '8px',
              height: '8px',
              borderRadius: '50%',
              background: realtimeStatus === 'connected' ? '#4ade80' : '#fbbf24'
            }"
          />
          {{ realtimeStatus === 'connected' ? '实时推送' : '轮询兜底' }}
        </span>
        <el-select
          :model-value="currentEventId"
          size="small"
          style="width: 240px"
          @change="selectEvent"
        >
          <el-option
            v-for="e in events"
            :key="e.id"
            :value="e.id"
            :label="'[' + e.typeLabel + '] ' + e.name"
          />
        </el-select>
        <el-radio-group :model-value="type" size="small" @change="selectType">
          <el-radio-button value="USER">个人榜</el-radio-button>
          <el-radio-button value="TEAM">战队榜</el-radio-button>
        </el-radio-group>
      </div>
    </header>

    <div
      v-if="board"
      style="display: flex; gap: 20px; align-items: center; flex-wrap: wrap; margin-bottom: 18px; color: #9fb2d8; font-size: 13px"
    >
      <span style="font-size: 17px; color: #fff; font-weight: 600">{{ board.event.name }}</span>
      <el-tag :type="eventStatusType(board.event.status)" size="small">{{ board.event.statusLabel }}</el-tag>
      <el-tag v-if="board.event.blackoutMode" type="warning" size="small" effect="dark">黑灯模式</el-tag>
      <span v-if="board.event.type === 'COMPETITION'">
        剩余 <strong style="color: #57e6c2">{{ countdown(board.event.endAt) }}</strong>
      </span>
      <span>参赛 {{ board.entries.length }} 人 · 累计解题 {{ totalSolves }}</span>
      <span>榜单更新时间 {{ lastUpdated ? formatShort(lastUpdated) : '—' }}</span>
      <span style="margin-left: auto" class="mono">{{ clock.toLocaleTimeString('zh-CN') }}</span>
    </div>

    <el-alert
      v-if="board && board.event.blackoutMode"
      title="黑灯模式进行中：对外仅个人可见自身得分，榜单与解题动态受限"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 16px; background: rgba(255, 125, 0, 0.12); border: 1px solid rgba(255, 125, 0, 0.4)"
    />

    <div
      v-if="currentAnnouncement"
      class="screen-panel"
      style="display: flex; align-items: center; gap: 14px; padding: 12px 18px; margin-bottom: 16px"
    >
      <el-tag type="warning" effect="dark" size="small">公告</el-tag>
      <div style="flex: 1; min-width: 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis">
        <strong style="color: #fff; margin-right: 10px">{{ currentAnnouncement.title }}</strong>
        <span style="color: #a9c1ff">{{ currentAnnouncement.content }}</span>
      </div>
      <span class="mono" style="color: #6d82ac; font-size: 12px">
        {{ announcementIndex % announcements.length + 1 }} / {{ announcements.length }}
      </span>
    </div>

    <div style="display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr); gap: 20px">
      <section class="screen-panel" v-loading="loading">
        <h2>{{ isTeam ? '战队榜 TOP' : '个人榜 TOP' }} / 总分实时排名</h2>

        <div
          v-if="top3.length"
          style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 16px"
        >
          <div
            v-for="(e, i) in top3"
            :key="e.id"
            class="rank-row"
            :class="rankClass(i)"
            style="display: block; text-align: center; border: 1px solid rgba(90, 130, 255, 0.3); border-radius: 10px; padding: 14px"
          >
            <div class="rank-no" style="font-size: 26px">#{{ e.rank }}</div>
            <div class="rank-name" style="margin: 6px 0">{{ e.name }}</div>
            <div class="rank-points" style="font-size: 22px">{{ e.totalPoints }}</div>
            <div style="font-size: 12px; color: #7d90b8; margin-top: 4px">
              {{ isTeam ? (e.memberCount || 0) + ' 人' : e.teamName || '个人参赛' }} ·
              {{ e.solveCount }} 题
            </div>
          </div>
        </div>

        <div v-if="!board || !board.entries.length" style="color: #7d90b8; text-align: center; padding: 28px 0">
          暂无得分数据
        </div>
        <div v-for="(e, i) in rest" :key="e.id" class="rank-row">
          <div class="rank-no">{{ e.rank }}</div>
          <div>
            <div class="rank-name">{{ e.name }}</div>
            <div style="font-size: 12px; color: #7d90b8">
              {{ isTeam ? (e.memberCount || 0) + ' 人' : e.teamName || '个人参赛' }} · {{ e.solveCount }} 题
            </div>
          </div>
          <div class="rank-points">{{ e.totalPoints }}</div>
        </div>
      </section>

      <section class="screen-panel" v-loading="loading">
        <h2>解题动态 / LIVE SOLVES</h2>
        <div v-if="!recent.length" style="color: #7d90b8; text-align: center; padding: 28px 0">
          暂无解题动态
        </div>
        <div
          v-for="(s, i) in recent"
          :key="i"
          style="display: flex; justify-content: space-between; gap: 10px; padding: 9px 0; border-bottom: 1px solid rgba(90, 130, 255, 0.14); font-size: 13px"
        >
          <div>
            <strong style="color: #fff">{{ s.displayName }}</strong>
            <span style="color: #a9c1ff"> 解出 </span>
            <span class="mono" style="color: #57e6c2">{{ s.challengeName }}</span>
            <el-tag v-if="s.bloodRank && s.bloodRank !== 'NONE'" type="warning" size="small" style="margin-left: 6px">
              {{ s.bloodRankLabel }}
            </el-tag>
          </div>
          <div style="color: #7d90b8; white-space: nowrap">
            {{ s.points }} PTS · {{ relativeTime(s.solvedAt) }}
          </div>
        </div>
      </section>
    </div>

    <section v-if="awdBoard" class="screen-panel" style="margin-top: 20px">
      <h2 style="display: flex; align-items: center; gap: 14px; flex-wrap: wrap">
        AWD 攻防 / 实时比分
        <el-tag :type="awdStatusType(awdBoard.status)" size="small" effect="dark">
          {{ awdBoard.statusLabel }}
        </el-tag>
        <span style="font-size: 13px; font-weight: 400; color: #9fb2d8">
          第 {{ awdBoard.currentRound }} / {{ awdBoard.totalRounds }} 轮 · 剩余
          <strong class="mono" style="color: #57e6c2; font-size: 17px">{{ awdRemaining }}</strong>
        </span>
        <span v-if="awdBoard.simulateChecks" style="font-size: 12px; color: #fbbf24">
          无 Docker：check 为模拟探活
        </span>
      </h2>
      <div v-if="!awdBoard.teams.length" style="color: #7d90b8; text-align: center; padding: 20px 0">
        暂无 AWD 队伍数据
      </div>
      <div
        v-for="t in awdBoard.teams.slice(0, 8)"
        :key="t.teamId"
        style="display: flex; align-items: center; gap: 14px; padding: 9px 0; border-bottom: 1px solid rgba(90, 130, 255, 0.14); font-size: 13px"
      >
        <span class="rank-no" style="font-size: 18px; min-width: 34px">#{{ t.rank }}</span>
        <strong style="color: #fff; min-width: 140px">{{ t.teamName }}</strong>
        <span style="color: #4ade80" class="mono">攻 +{{ t.attackGain }}</span>
        <span style="color: #f87171" class="mono">失 -{{ t.defenseLoss }}</span>
        <span style="color: #fbbf24" class="mono">宕 -{{ t.downtimeLoss }}</span>
        <span class="mono" style="margin-left: auto; color: #57e6c2; font-size: 16px">{{ t.totalScore }}</span>
      </div>
    </section>

    <footer style="margin-top: 18px; display: flex; gap: 24px; color: #6d82ac; font-size: 12px">
      <span>WebSocket 实时推送 · 断线自动重连 · 30 秒轮询兜底</span>
      <span style="margin-left: auto">CTF PLATFORM · SEC LAB</span>
    </footer>
  </div>
  </el-config-provider>
</template>
