<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, getUser } from '../../shared/api.js'
import { subscribe } from '../../shared/realtime.js'
import {
  countdown,
  eventStatusType,
  eventTypeType,
  formatShort,
  difficultyType,
  CATEGORIES
} from '../../shared/format.js'

const router = useRouter()
const user = getUser()
const staff = user && (user.role === 'ADMIN' || user.role === 'JUDGE')

const events = ref([])
const currentEventId = ref('')
const category = ref('')
const onlyUnsolved = ref(false)
const keyword = ref('')
const challenges = ref([])
const event = ref(null)
const board = ref(null)
const announcements = ref([])
const loading = ref(false)
const errorMessage = ref('')
let announcementTimer = null
let offAnnouncement = null
let offSolves = null

const filtered = challenges

const solvedCount = computed(() => challenges.value.filter((c) => c.solved).length)
const deadline = computed(() => {
  const e = event.value
  if (!e) return '—'
  if (e.type === 'PRACTICE') return '长期开放'
  if (e.status === 'ENDED') return '已结束'
  return countdown(e.endAt)
})

function blocked(e) {
  return e.type === 'COMPETITION' && e.status === 'NOT_STARTED' && !staff
}

async function loadEvents() {
  try {
    events.value = await api('/events')
  } catch (err) {
    events.value = []
    ElMessage.error(err.message)
  }
}

async function loadAnnouncements() {
  try {
    const list = await api('/announcements')
    announcements.value = list || []
  } catch (e) {
    announcements.value = []
  }
}

async function loadChallenges() {
  loading.value = true
  errorMessage.value = ''
  const params = new URLSearchParams()
  if (currentEventId.value) params.set('eventId', currentEventId.value)
  if (category.value) params.set('category', category.value)
  if (onlyUnsolved.value) params.set('solved', 'false')
  if (keyword.value.trim()) params.set('keyword', keyword.value.trim())
  const qs = params.toString() ? '?' + params.toString() : ''
  const boardQuery =
    '/scoreboard?type=USER&limit=1' +
    (currentEventId.value ? '&eventId=' + encodeURIComponent(currentEventId.value) : '')
  try {
    const [data, boardData] = await Promise.all([
      api('/challenges' + qs),
      api(boardQuery).catch(() => null)
    ])
    event.value = data.event
    currentEventId.value = data.event.id
    challenges.value = data.challenges
    board.value = boardData
  } catch (err) {
    errorMessage.value = err.message + '（错误码 ' + (err.code || '—') + '）'
    challenges.value = []
    event.value = null
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await loadEvents()
  await loadChallenges()
  loadAnnouncements()
  ElMessage.success('已刷新')
}

function bindRealtime() {
  if (offSolves) {
    offSolves()
    offSolves = null
  }
  if (!currentEventId.value) return
  offSolves = subscribe('solves:' + currentEventId.value, () => {
    if (!document.hidden) loadChallenges()
  })
}

function bindAnnouncement() {
  if (offAnnouncement) return
  offAnnouncement = subscribe('announcement', () => {
    if (!document.hidden) loadAnnouncements()
  })
}

let keywordTimer = null
watch([category, onlyUnsolved], () => loadChallenges())
watch(keyword, () => {
  clearTimeout(keywordTimer)
  keywordTimer = setTimeout(loadChallenges, 350)
})
watch(currentEventId, () => bindRealtime())

function openDetail(challenge) {
  router.push({ path: '/challenge/' + challenge.id, query: { eventId: currentEventId.value } })
}

onMounted(async () => {
  await loadEvents()
  const preferred =
    events.value.find((e) => e.type === 'COMPETITION' && e.status === 'ONGOING') ||
    events.value.find((e) => e.type === 'PRACTICE') ||
    events.value[0]
  if (preferred) currentEventId.value = preferred.id
  await loadChallenges()
  loadAnnouncements()
  bindRealtime()
  bindAnnouncement()
  // 30s 轮询兜底：公告与 WebSocket 断线时仍能更新
  announcementTimer = setInterval(() => {
    if (!document.hidden) loadAnnouncements()
  }, 30000)
})

onBeforeUnmount(() => {
  if (announcementTimer) clearInterval(announcementTimer)
  if (offAnnouncement) offAnnouncement()
  if (offSolves) offSolves()
  offAnnouncement = null
  offSolves = null
})
</script>

<template>
  <div>
    <el-card shadow="never" style="margin-bottom: 16px">
      <div style="display: flex; justify-content: space-between; gap: 24px; flex-wrap: wrap">
        <div style="flex: 1; min-width: 260px">
          <div v-if="event" style="display: flex; gap: 6px; margin-bottom: 10px">
            <el-tag :type="eventTypeType(event.type)" size="small">{{ event.typeLabel }}</el-tag>
            <el-tag :type="eventStatusType(event.status)" size="small">{{ event.statusLabel }}</el-tag>
            <el-tag v-if="event.type === 'COMPETITION'" size="small" effect="plain">限时计分</el-tag>
            <el-tag v-else type="success" size="small" effect="plain">长期开放 · 独立榜单</el-tag>
            <el-tag v-if="event.blackoutMode" type="warning" size="small" effect="dark">黑灯模式</el-tag>
          </div>
          <h1 style="font-size: 24px; margin-bottom: 8px">{{ event ? event.name : '加载中…' }}</h1>
          <p class="muted small" style="margin: 0 0 12px">
            {{ event && event.description ? event.description : '暂无赛事描述。' }}
          </p>
          <div v-if="event" class="muted small" style="display: flex; gap: 16px; flex-wrap: wrap">
            <span class="mono">EVENT_ID {{ event.id.slice(0, 8) }}</span>
            <span v-if="event.startAt">开始 {{ formatShort(event.startAt) }}</span>
            <span v-if="event.endAt">结束 {{ formatShort(event.endAt) }}</span>
          </div>
        </div>
        <div style="width: 260px">
          <div class="stat-line"><span>题目数量</span><strong>{{ challenges.length }}</strong></div>
          <div class="stat-line">
            <span>我的进度</span><strong>{{ solvedCount }} / {{ challenges.length }}</strong>
          </div>
          <div class="stat-line">
            <span>我的积分</span><strong>{{ board && board.me ? board.me.totalPoints : 0 }}</strong>
          </div>
          <div class="stat-line">
            <span>{{ event && event.type === 'PRACTICE' ? '开放状态' : '截止时间' }}</span>
            <strong>{{ deadline }}</strong>
          </div>
          <div v-if="board && board.me" class="stat-line">
            <span>当前名次</span><strong>#{{ board.me.rank }}</strong>
          </div>
        </div>
      </div>
    </el-card>

    <el-alert
      v-for="a in announcements"
      :key="a.id"
      :title="a.title"
      :description="a.content + '（' + formatShort(a.createdAt) + '）'"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 10px"
    />

    <el-card shadow="never">
      <div class="toolbar" style="margin-top: 0">
        <el-radio-group v-model="currentEventId" size="small" @change="loadChallenges">
          <el-radio-button
            v-for="e in events"
            :key="e.id"
            :value="e.id"
            :disabled="blocked(e)"
            :title="blocked(e) ? '赛事尚未开始，题目暂不可见' : ''"
          >
            [{{ e.typeLabel }}] {{ e.name }} · {{ e.statusLabel }}
          </el-radio-button>
        </el-radio-group>
      </div>

      <div class="toolbar">
        <el-radio-group v-model="category" size="small">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button v-for="[value, label] in CATEGORIES" :key="value" :value="value">
            {{ label }}
          </el-radio-button>
        </el-radio-group>
        <div class="spacer" />
        <el-input
          v-model="keyword"
          placeholder="搜索题目名称或描述…"
          size="small"
          clearable
          style="max-width: 260px"
        />
        <el-checkbox v-model="onlyUnsolved" size="small">只看未解出</el-checkbox>
        <el-button size="small" @click="refresh">刷新</el-button>
      </div>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />

      <div v-loading="loading">
        <div v-if="!filtered.length && !loading" class="muted" style="padding: 30px; text-align: center">
          当前筛选条件下没有题目。<br />
          <span class="small">试试切换赛事、分类或清空搜索关键词。</span>
        </div>
        <div v-else class="challenge-grid">
          <div
            v-for="c in filtered"
            :key="c.id"
            class="challenge-card"
            :class="{ solved: c.solved }"
            @click="openDetail(c)"
          >
            <div style="display: flex; gap: 6px">
              <el-tag size="small" effect="plain">{{ c.categoryLabel }}</el-tag>
              <el-tag size="small" :type="difficultyType(c.difficulty)" effect="light">
                {{ c.difficultyLabel }}
              </el-tag>
            </div>
            <h3>{{ c.name }}</h3>
            <div class="muted small" style="min-height: 36px">{{ c.description || '暂无描述' }}</div>
            <div style="display: flex; align-items: center; justify-content: space-between">
              <span class="points">{{ c.currentPoints }}<small> PTS</small></span>
              <span class="muted small">
                {{ c.solveCount }} 解出{{ c.hintCount ? ' · ' + c.hintCount + ' 提示' : ''
                }}{{ c.hasAttachment ? ' · 附件' : '' }}
              </span>
            </div>
            <el-tag v-if="c.solved" type="success" size="small" effect="dark">✓ 已解出</el-tag>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>
