<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, setSession, getToken } from '../../shared/api.js'
import { formatDate, CATEGORIES } from '../../shared/format.js'

const router = useRouter()
const profile = ref(null)
const loading = ref(false)
const teamMode = ref('create')
const teamName = ref('')
const inviteCode = ref('')

const isCaptain = computed(
  () => profile.value && profile.value.team && profile.value.team.captainId === profile.value.user.id
)

async function refreshUserCache() {
  try {
    const me = await api('/auth/me')
    const token = getToken()
    if (token) setSession({ token, user: me })
  } catch (e) {
    /* 忽略：下次请求会处理 401 */
  }
}

async function loadProfile() {
  loading.value = true
  try {
    profile.value = await api('/me/profile')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

async function teamAction(action, successMessage) {
  try {
    await action()
    ElMessage.success(successMessage)
    await refreshUserCache()
    await loadProfile()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function createTeam() {
  const name = teamName.value.trim()
  if (name.length < 2) {
    ElMessage.error('战队名至少 2 个字符')
    return
  }
  await teamAction(() => api('/teams', { method: 'POST', body: { name } }), '战队创建成功')
  teamName.value = ''
}

async function joinTeam() {
  const code = inviteCode.value.trim()
  if (!code) {
    ElMessage.error('请输入邀请码')
    return
  }
  await teamAction(() => api('/teams/join', { method: 'POST', body: { inviteCode: code } }), '已加入战队')
  inviteCode.value = ''
}

async function leaveTeam() {
  const text = isCaptain.value
    ? '队长退出后，队长将自动移交给最早加入的成员。确认退出？'
    : '确认退出当前战队？'
  try {
    await ElMessageBox.confirm(text, '退出战队', { type: 'warning' })
  } catch (e) {
    return
  }
  await teamAction(() => api('/teams/leave', { method: 'POST' }), '已退出战队')
}

async function removeMember(member) {
  try {
    await ElMessageBox.confirm('确认将 ' + member.displayName + ' 移出战队？', '移除成员', { type: 'warning' })
  } catch (e) {
    return
  }
  await teamAction(
    () => api('/teams/my/members/' + encodeURIComponent(member.id), { method: 'DELETE' }),
    '成员已移出'
  )
}

async function copyInviteCode() {
  const code = profile.value.team.inviteCode
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success('邀请码已复制')
  } catch (e) {
    ElMessage.info('邀请码：' + code)
  }
}

function openChallenge(row) {
  router.push({ path: '/challenge/' + row.challengeId, query: { eventId: row.eventId } })
}

function openScoreboard(eventId) {
  router.push({ path: '/scoreboard', query: { eventId } })
}

onMounted(async () => {
  await refreshUserCache()
  await loadProfile()
})
</script>

<template>
  <div v-loading="loading">
    <div class="toolbar" style="margin-top: 0">
      <h2 style="font-size: 20px">{{ profile ? profile.user.displayName + ' 的战绩' : '我的战绩' }}</h2>
      <div class="spacer" />
      <span class="muted small">个人积分与赛事榜/练习榜分别统计</span>
      <el-button size="small" @click="loadProfile">刷新</el-button>
    </div>

    <div v-if="profile" style="display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 16px">
      <div>
        <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px">
          <el-card shadow="never">
            <div class="muted small">总积分</div>
            <div class="mono" style="font-size: 22px; color: var(--color-primary); font-weight: 700">
              {{ profile.totalPoints }}
            </div>
          </el-card>
          <el-card shadow="never">
            <div class="muted small">解题数</div>
            <div class="mono" style="font-size: 22px; font-weight: 700">{{ profile.solves.length }}</div>
          </el-card>
          <el-card shadow="never">
            <div class="muted small">提示消耗</div>
            <div class="mono" style="font-size: 22px; font-weight: 700">{{ profile.hintCostTotal }}</div>
          </el-card>
          <el-card shadow="never">
            <div class="muted small">角色</div>
            <div style="font-size: 15px; font-weight: 600; margin-top: 4px">{{ profile.user.roleLabel }}</div>
          </el-card>
        </div>

        <el-card shadow="never" style="margin-bottom: 16px">
          <p class="section-title">解题记录</p>
          <el-table :data="profile.solves" size="small" stripe>
            <el-table-column label="题目" min-width="180">
              <template #default="{ row }">
                <el-link type="primary" @click="openChallenge(row)">{{ row.challengeName }}</el-link>
                <span class="muted small"> · {{ row.categoryLabel }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="eventName" label="赛事/练习" min-width="130" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.eventType === 'PRACTICE' ? 'success' : 'primary'">
                  {{ row.eventTypeLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="得分" width="160">
              <template #default="{ row }">
                <span class="mono small">
                  {{ row.basePoints }} + {{ row.bloodBonus }} - {{ row.hintCost }} =
                  <span style="color: var(--color-primary)">{{ row.points }}</span>
                </span>
              </template>
            </el-table-column>
            <el-table-column label="血奖" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.bloodRank && row.bloodRank !== 'NONE'" type="warning" size="small">
                  {{ row.bloodRankLabel }}
                </el-tag>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="解出时间" width="150">
              <template #default="{ row }">
                <span class="muted small">{{ formatDate(row.solvedAt, true) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>

      <div style="display: flex; flex-direction: column; gap: 16px">
        <el-card shadow="never">
          <p class="section-title">赛事积分</p>
          <div v-if="!profile.events.length" class="muted small">暂无赛事积分。</div>
          <div
            v-for="e in profile.events"
            :key="e.eventId"
            style="padding: 10px 0; border-bottom: 1px dashed var(--border)"
          >
            <div style="display: flex; justify-content: space-between; gap: 10px">
              <el-link type="primary" @click="openScoreboard(e.eventId)">{{ e.eventName }}</el-link>
              <span class="mono">{{ e.points }}</span>
            </div>
            <div class="muted small" style="margin-top: 3px">
              {{ e.eventTypeLabel }} · {{ e.solveCount }} 题 · 名次 #{{ e.rank || '—' }}
            </div>
          </div>
        </el-card>

        <el-card shadow="never">
          <p class="section-title">账号信息</p>
          <div class="stat-line"><span>用户名</span><strong>@{{ profile.user.username }}</strong></div>
          <div class="stat-line"><span>昵称</span><strong>{{ profile.user.displayName }}</strong></div>
          <div class="stat-line">
            <span>角色</span>
            <el-tag size="small" :type="profile.user.role === 'ADMIN' ? 'danger' : 'primary'">
              {{ profile.user.roleLabel }}
            </el-tag>
          </div>
          <div class="stat-line">
            <span>账号状态</span>
            <el-tag size="small" :type="profile.user.banned ? 'danger' : 'success'">
              {{ profile.user.banned ? '已封禁' : '正常' }}
            </el-tag>
          </div>
        </el-card>

        <el-card shadow="never">
          <p class="section-title">战队</p>

          <template v-if="!profile.team">
            <el-radio-group v-model="teamMode" size="small" style="margin-bottom: 12px">
              <el-radio-button value="create">创建战队</el-radio-button>
              <el-radio-button value="join">加入战队</el-radio-button>
            </el-radio-group>

            <div v-if="teamMode === 'create'">
              <el-input v-model="teamName" maxlength="40" placeholder="战队名称（2-40 个字符）" />
              <el-button type="primary" style="width: 100%; margin-top: 10px" @click="createTeam">
                创建并成为队长
              </el-button>
            </div>
            <div v-else>
              <el-input v-model="inviteCode" class="mono" maxlength="16" placeholder="邀请码，例如 0X00SEC1" />
              <el-button type="primary" style="width: 100%; margin-top: 10px" @click="joinTeam">
                加入战队
              </el-button>
            </div>
            <div class="muted small" style="margin-top: 10px">
              演示邀请码：0X00SEC1（0x00sec）、BYTERAID（Byte Raiders）
            </div>
          </template>

          <template v-else>
            <div style="display: flex; justify-content: space-between; gap: 10px; align-items: flex-start">
              <div>
                <div style="font-size: 17px; font-weight: 700; color: var(--text-title)">
                  {{ profile.team.name }}
                </div>
                <div class="muted small">
                  队长 {{ profile.team.captainName }} · {{ profile.team.memberCount }} 名成员
                </div>
              </div>
              <el-tag v-if="isCaptain" type="warning" size="small">我是队长</el-tag>
            </div>

            <div
              style="margin-top: 12px; display: flex; justify-content: space-between; align-items: center; gap: 10px"
            >
              <div>
                <div class="muted small">邀请码</div>
                <div class="mono">{{ profile.team.inviteCode }}</div>
              </div>
              <el-button size="small" @click="copyInviteCode">复制</el-button>
            </div>

            <div style="margin-top: 14px">
              <div class="muted small" style="margin-bottom: 6px">成员</div>
              <div
                v-for="m in profile.team.members"
                :key="m.id"
                style="display: flex; justify-content: space-between; align-items: center; gap: 8px; padding: 7px 0; border-bottom: 1px dashed var(--border)"
              >
                <div>
                  <span style="font-weight: 600">{{ m.displayName }}</span>
                  <span class="muted small mono">@{{ m.username }}</span>
                  <el-tag v-if="m.captain" type="warning" size="small" style="margin-left: 4px">队长</el-tag>
                </div>
                <el-button
                  v-if="isCaptain && !m.captain"
                  size="small"
                  type="danger"
                  @click="removeMember(m)"
                >
                  移除
                </el-button>
              </div>
            </div>

            <el-button type="danger" style="width: 100%; margin-top: 14px" @click="leaveTeam">
              退出战队
            </el-button>
          </template>
        </el-card>
      </div>
    </div>
  </div>
</template>
