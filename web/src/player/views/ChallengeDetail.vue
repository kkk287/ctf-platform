<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, download, getUser } from '../../shared/api.js'
import { formatDate, sizeText, relativeTime, difficultyType } from '../../shared/format.js'
import { subscribe } from '../../shared/realtime.js'

const route = useRoute()
const router = useRouter()
const id = route.params.id
const user = getUser()

const detail = ref(null)
const loading = ref(true)
const errorMessage = ref('')
const flag = ref('')
const submitting = ref(false)
const submitResult = ref(null)

const instance = ref(null)
const instanceBusy = ref(false)
const remaining = ref('')
let instanceTimer = null
let offRealtime = null
let pollTimer = null

const canSubmit = computed(() => {
  const c = detail.value
  if (!c || c.solved) return false
  if (c.event.type === 'COMPETITION' && c.event.status === 'ENDED') return false
  if (c.event.type === 'COMPETITION' && c.event.status === 'NOT_STARTED') return false
  if (user && user.role === 'OBSERVER') return false
  return true
})

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    detail.value = await api('/challenges/' + encodeURIComponent(id))
  } catch (err) {
    errorMessage.value = '无法访问该题目：' + err.message + '（错误码 ' + (err.code || '—') + '）'
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function downloadAttachment() {
  try {
    await download('/challenges/' + encodeURIComponent(id) + '/attachment', detail.value.attachmentName)
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function unlockHint(hint) {
  try {
    await ElMessageBox.confirm('解锁该提示将扣除 ' + hint.cost + ' 分，确认继续？', '解锁提示', {
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await api('/challenges/' + encodeURIComponent(id) + '/hints/' + encodeURIComponent(hint.id) + '/unlock', {
      method: 'POST'
    })
    ElMessage.success('提示已解锁')
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function submit() {
  const value = flag.value.trim()
  if (!value) {
    ElMessage.error('请输入 flag')
    return
  }
  submitting.value = true
  try {
    const data = await api('/challenges/' + encodeURIComponent(id) + '/submit', {
      method: 'POST',
      body: { flag: value }
    })
    submitResult.value = data
    ElMessage.success('提交正确，得分 +' + data.points)
    flag.value = ''
    setTimeout(load, 900)
  } catch (err) {
    submitResult.value = { error: err.message, code: err.code }
  } finally {
    submitting.value = false
  }
}

function back() {
  router.push({ path: '/', query: { eventId: detail.value ? detail.value.event.id : '' } })
}

/** 剩余时长：仅 RUNNING 且有 expiresAt 时显示 */
function tickRemaining() {
  if (!instance.value || !instance.value.expiresAt) {
    remaining.value = ''
    return
  }
  const diff = new Date(instance.value.expiresAt).getTime() - Date.now()
  if (diff <= 0) {
    remaining.value = '已到期'
    return
  }
  const h = Math.floor(diff / 3600000)
  const m = Math.floor((diff % 3600000) / 60000)
  const s = Math.floor((diff % 60000) / 1000)
  remaining.value = (h > 0 ? h + ' 时 ' : '') + m + ' 分 ' + String(s).padStart(2, '0') + ' 秒'
}

async function loadInstance() {
  try {
    instance.value = await api('/challenges/' + encodeURIComponent(id) + '/instance')
  } catch (err) {
    instance.value = null
  }
  tickRemaining()
}

async function startInstance() {
  instanceBusy.value = true
  try {
    instance.value = await api('/challenges/' + encodeURIComponent(id) + '/instance/start', { method: 'POST' })
    ElMessage.success('靶机已下发，请稍候等待状态变为运行中')
    await loadInstance()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    instanceBusy.value = false
  }
}

async function resetInstance() {
  try {
    await ElMessageBox.confirm('重置将销毁当前容器并按镜像重建，容器内数据会丢失，确认重置？', '重置靶机', {
      type: 'warning'
    })
  } catch (e) {
    return
  }
  instanceBusy.value = true
  try {
    instance.value = await api('/challenges/' + encodeURIComponent(id) + '/instance/reset', { method: 'POST' })
    ElMessage.success('靶机已重置')
    await loadInstance()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    instanceBusy.value = false
  }
}

async function stopInstance() {
  try {
    await ElMessageBox.confirm('确认销毁当前靶机实例？', '销毁靶机', { type: 'warning' })
  } catch (e) {
    return
  }
  instanceBusy.value = true
  try {
    await api('/challenges/' + encodeURIComponent(id) + '/instance', { method: 'DELETE' })
    ElMessage.success('靶机已销毁')
    instance.value = null
    remaining.value = ''
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    instanceBusy.value = false
  }
}

function bindRealtime() {
  if (offRealtime) {
    offRealtime()
    offRealtime = null
  }
  if (!detail.value) return
  offRealtime = subscribe('solves:' + detail.value.event.id, () => {
    if (!document.hidden) load()
  })
}

onMounted(async () => {
  await load()
  await loadInstance()
  bindRealtime()
  instanceTimer = setInterval(tickRemaining, 1000)
  // 30s 轮询兜底：解题动态与分值变化
  pollTimer = setInterval(() => {
    if (!document.hidden) load()
  }, 30000)
})

onBeforeUnmount(() => {
  if (instanceTimer) clearInterval(instanceTimer)
  if (pollTimer) clearInterval(pollTimer)
  if (offRealtime) offRealtime()
  offRealtime = null
})
</script>

<template>
  <div>
    <div style="margin-bottom: 14px">
      <el-button link @click="back">← 返回题目列表</el-button>
    </div>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <div v-if="detail" style="display: grid; grid-template-columns: minmax(0, 1fr) 360px; gap: 16px">
      <el-card shadow="never">
        <div style="display: flex; gap: 6px; margin-bottom: 10px">
          <el-tag size="small" effect="plain">{{ detail.categoryLabel }}</el-tag>
          <el-tag size="small" :type="difficultyType(detail.difficulty)">{{ detail.difficultyLabel }}</el-tag>
          <el-tag v-if="detail.solved" type="success" size="small">已解出</el-tag>
          <el-tag v-else type="info" size="small">未解出</el-tag>
        </div>
        <h1 style="font-size: 22px; margin-bottom: 8px">{{ detail.name }}</h1>
        <div class="muted small" style="margin-bottom: 18px">
          当前分值 {{ detail.currentPoints }} PTS · 初始 {{ detail.initialPoints }} / 最低
          {{ detail.minPoints }} / 每题衰减 {{ detail.decayPoints }} · {{ detail.solveCount }} 人解出
          <span v-if="detail.solvedAt"> · 解出于 {{ formatDate(detail.solvedAt) }}</span>
        </div>

        <p class="section-title">题目描述</p>
        <p style="white-space: pre-wrap; line-height: 1.7; margin-top: 0">
          {{ detail.description || '暂无描述' }}
        </p>

        <div v-if="detail.attachmentName" style="margin-top: 20px">
          <p class="section-title">附件</p>
          <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
            <div>
              <div class="mono">{{ detail.attachmentName }}</div>
              <div class="muted small">
                {{ sizeText(detail.attachmentSize) }} · 本地存储，随容器数据卷持久化
              </div>
            </div>
            <el-button size="small" @click="downloadAttachment">下载</el-button>
          </div>
        </div>

        <div style="margin-top: 22px">
          <p class="section-title">提示</p>
          <div v-if="!detail.hints || !detail.hints.length" class="muted small">本题暂无提示。</div>
          <div v-else>
            <div
              v-for="(h, i) in detail.hints"
              :key="h.id"
              style="padding: 10px 0; border-bottom: 1px dashed var(--border)"
            >
              <div style="display: flex; justify-content: space-between; gap: 12px; align-items: center">
                <strong>提示 {{ i + 1 }}</strong>
                <el-tag v-if="h.unlocked" type="success" size="small">已解锁</el-tag>
                <el-button v-else size="small" @click="unlockHint(h)">解锁 · 扣 {{ h.cost }} 分</el-button>
              </div>
              <div class="small" style="margin-top: 6px">
                <span v-if="h.unlocked">{{ h.content }}</span>
                <span v-else class="muted">● ● ● ● ● 解锁后显示（代价 {{ h.cost }} PTS）</span>
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <div style="display: flex; flex-direction: column; gap: 16px">
        <el-card shadow="never">
          <p class="section-title">提交 flag</p>

          <el-result
            v-if="detail.solved"
            icon="success"
            title="该题目已解出"
            :sub-title="'解出时间：' + formatDate(detail.solvedAt, true)"
          />
          <el-alert
            v-else-if="detail.event.type === 'COMPETITION' && detail.event.status === 'ENDED'"
            title="赛事已结束，当前只能查看题目，无法继续提交。"
            type="error"
            :closable="false"
          />
          <el-alert
            v-else-if="detail.event.type === 'COMPETITION' && detail.event.status === 'NOT_STARTED'"
            title="赛事尚未开始，题目暂不可答。"
            type="error"
            :closable="false"
          />
          <el-alert
            v-else-if="user && user.role === 'OBSERVER'"
            title="观摩账号仅可浏览，不可参与答题。"
            type="error"
            :closable="false"
          />
          <div v-else>
            <el-input
              v-model="flag"
              class="mono"
              maxlength="200"
              placeholder="flag{...}"
              @keyup.enter="submit"
            />
            <el-button type="primary" style="width: 100%; margin-top: 10px" :loading="submitting" @click="submit">
              提交
            </el-button>
            <div class="muted small" style="margin-top: 8px">
              错误提交有冷却与次数限制；正确提交后不可重复提交。
            </div>
          </div>

          <el-alert
            v-if="submitResult && !submitResult.error"
            type="success"
            :closable="false"
            style="margin-top: 12px"
            :title="'✓ ' + submitResult.message"
          >
            <div class="mono small">
              基础 {{ submitResult.basePoints }} + 血奖 {{ submitResult.bloodBonus }} - 提示
              {{ submitResult.hintCost }} = {{ submitResult.points }} PTS
            </div>
            <div class="small">
              赛事总分 {{ submitResult.totalPoints }} · 当前名次 #{{ submitResult.rank }} · 本题
              {{ submitResult.solveCount }} 解出
            </div>
          </el-alert>
          <el-alert
            v-if="submitResult && submitResult.error"
            type="error"
            :closable="false"
            style="margin-top: 12px"
            :title="'✗ ' + submitResult.error"
            :description="'code ' + (submitResult.code || '—')"
          />
        </el-card>

        <el-card shadow="never">
          <p class="section-title">动态靶机</p>

          <div v-if="!instance">
            <div class="muted small" style="margin-bottom: 10px">
              本题支持一键下发独立靶机实例（需管理员已配置镜像），实例到期自动回收。
            </div>
            <el-button type="primary" style="width: 100%" :loading="instanceBusy" @click="startInstance">
              申请靶机
            </el-button>
          </div>

          <div v-else>
            <div style="display: flex; justify-content: space-between; align-items: center; gap: 10px">
              <el-tag
                size="small"
                :type="instance.status === 'RUNNING' ? 'success' : instance.status === 'FAILED' ? 'danger' : 'warning'"
              >
                {{ instance.status }}
              </el-tag>
              <span v-if="instance.dynamicFlag" class="muted small">动态 flag</span>
            </div>
            <div class="stat-line">
              <span>访问地址</span>
              <strong class="mono">{{ instance.host ? instance.host + ':' + instance.port : '等待分配' }}</strong>
            </div>
            <div class="stat-line">
              <span>剩余时长</span>
              <strong class="mono">{{ remaining || '—' }}</strong>
            </div>
            <div class="stat-line">
              <span>到期时间</span>
              <strong class="small">{{ formatDate(instance.expiresAt) }}</strong>
            </div>
            <div style="display: flex; gap: 8px; margin-top: 12px">
              <el-button size="small" :loading="instanceBusy" @click="resetInstance">重置</el-button>
              <el-button size="small" type="danger" :loading="instanceBusy" @click="stopInstance">销毁</el-button>
            </div>
          </div>
        </el-card>

        <el-card shadow="never">
          <p class="section-title">最近解题动态</p>
          <div v-if="!detail.recentSolves || !detail.recentSolves.length" class="muted small">
            还没有人解出，你可以成为一血。
          </div>
          <div v-for="(s, i) in detail.recentSolves" :key="i" class="timeline-item">
            <strong>{{ s.displayName }}{{ s.teamName ? ' · ' + s.teamName : '' }}</strong>
            <el-tag v-if="s.bloodRankLabel" type="warning" size="small" style="margin-right: 4px">
              {{ s.bloodRankLabel }}
            </el-tag>
            <span>{{ s.points }} PTS · {{ relativeTime(s.solvedAt) }}</span>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>
