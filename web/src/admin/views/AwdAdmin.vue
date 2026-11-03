<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../shared/api.js'
import { formatDate } from '../../shared/format.js'

const events = ref([])
const rounds = ref([])
const loading = ref(false)
const detail = ref(null)
const detailLoading = ref(false)
const dialogVisible = ref(false)
const creating = ref(false)
const tab = ref('teams')
const teamDialog = ref(false)
const teamForm = reactive({ name: '', members: '' })

const form = reactive({
  eventId: '',
  name: '',
  totalRounds: 5,
  roundSeconds: 300,
  attackPoints: 100,
  defensePenalty: 50,
  downtimePenalty: 30,
  checkType: 'TCP',
  checkTarget: 'http://{host}:{port}/',
  image: 'ctf/awd-vuln:latest',
  containerPort: 80
})

const ACTIONS = {
  PREP: [{ action: 'START', label: '开轮', type: 'primary' }],
  RUNNING: [
    { action: 'PAUSE', label: '暂停', type: 'warning' },
    { action: 'NEXT', label: '下一轮', type: 'primary' },
    { action: 'STOP', label: '结算', type: 'danger' }
  ],
  PAUSED: [
    { action: 'RESUME', label: '恢复', type: 'success' },
    { action: 'STOP', label: '结算', type: 'danger' }
  ],
  SCORING: [{ action: 'FINISH', label: '结束', type: 'primary' }],
  FINISHED: []
}

const statusType = (status) =>
  ({ PREP: 'info', RUNNING: 'success', PAUSED: 'warning', SCORING: 'primary', FINISHED: 'info' }[status] || 'info')

function actionsOf(round) {
  return ACTIONS[round.status] || []
}

async function loadEvents() {
  events.value = await api('/events')
}

async function loadRounds() {
  loading.value = true
  try {
    rounds.value = await api('/admin/awd/rounds')
  } catch (err) {
    ElMessage.error(err.message)
    rounds.value = []
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  tab.value = 'teams'
  detail.value = null
  try {
    detail.value = await api('/admin/awd/rounds/' + encodeURIComponent(row.id))
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function refreshDetail() {
  if (!detail.value) return
  detailLoading.value = true
  try {
    detail.value = await api('/admin/awd/rounds/' + encodeURIComponent(detail.value.round.id))
    await loadRounds()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    detailLoading.value = false
  }
}

async function transition(round, action) {
  try {
    await ElMessageBox.confirm('确认执行「' + action + '」？', round.name, { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await api('/admin/awd/rounds/' + encodeURIComponent(round.id) + '/transition', {
      method: 'POST',
      body: { action }
    })
    ElMessage.success('已执行 ' + action)
    await loadRounds()
    if (detail.value && detail.value.round.id === round.id) await refreshDetail()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

function openCreate() {
  Object.assign(form, {
    eventId: events.value[0]?.id || '',
    name: '',
    totalRounds: 5,
    roundSeconds: 300,
    attackPoints: 100,
    defensePenalty: 50,
    downtimePenalty: 30,
    checkType: 'TCP',
    checkTarget: 'http://{host}:{port}/',
    image: 'ctf/awd-vuln:latest',
    containerPort: 80
  })
  dialogVisible.value = true
}

async function createRound() {
  if (!form.eventId || !form.name.trim()) {
    ElMessage.error('请选择赛事并填写名称')
    return
  }
  creating.value = true
  try {
    await api('/admin/awd/rounds', { method: 'POST', body: { ...form } })
    ElMessage.success('轮次已创建')
    dialogVisible.value = false
    await loadRounds()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    creating.value = false
  }
}

function openTeamDialog() {
  teamForm.name = ''
  teamForm.members = ''
  teamDialog.value = true
}

async function addTeam() {
  if (!teamForm.name.trim() || !teamForm.members.trim()) {
    ElMessage.error('请填写队伍名与成员（逗号分隔）')
    return
  }
  try {
    await api('/admin/awd/rounds/' + encodeURIComponent(detail.value.round.id) + '/teams', {
      method: 'POST',
      body: {
        name: teamForm.name.trim(),
        members: teamForm.members.split(',').map((m) => m.trim()).filter((m) => m)
      }
    })
    ElMessage.success('队伍已加入')
    teamDialog.value = false
    await refreshDetail()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function removeTeam(team) {
  try {
    await ElMessageBox.confirm('确认移除队伍「' + team.name + '」？', '移除队伍', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await api('/admin/awd/rounds/' + encodeURIComponent(detail.value.round.id) + '/teams/' + encodeURIComponent(team.id), {
      method: 'DELETE'
    })
    await refreshDetail()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function runChecks() {
  if (!detail.value) return
  try {
    await api('/admin/awd/rounds/' + encodeURIComponent(detail.value.round.id) + '/checks', { method: 'POST' })
    ElMessage.success('已执行一轮 check')
    await refreshDetail()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

const canEditTeams = computed(() => detail.value && detail.value.round.status === 'PREP')

onMounted(async () => {
  try {
    await loadEvents()
    await loadRounds()
  } catch (err) {
    ElMessage.error(err.message)
  }
})
</script>

<template>
  <div>
    <div class="toolbar" style="margin-top: 0">
      <el-button size="small" @click="loadRounds">刷新</el-button>
      <div class="spacer" />
      <span class="muted small">状态机：准备 → 进行 ⇄ 暂停 → 结算 → 结束；到期由后端定时推进</span>
      <el-button type="primary" size="small" @click="openCreate">+ 新建 AWD 轮次</el-button>
    </div>

    <el-table v-loading="loading" :data="rounds" size="small" stripe>
      <el-table-column label="轮次" min-width="180">
        <template #default="{ row }">
          <div><strong>{{ row.name }}</strong></div>
          <div class="muted small">{{ row.eventName }}</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="110">
        <template #default="{ row }">
          <span class="mono small">{{ row.currentRound }} / {{ row.totalRounds }} 轮</span>
        </template>
      </el-table-column>
      <el-table-column label="剩余/单轮" width="110">
        <template #default="{ row }">
          <span class="mono small">{{ row.remainingSeconds }}s / {{ row.roundSeconds }}s</span>
        </template>
      </el-table-column>
      <el-table-column label="队伍" width="70">
        <template #default="{ row }">{{ row.teamCount }}</template>
      </el-table-column>
      <el-table-column label="计分" min-width="200">
        <template #default="{ row }">
          <span class="muted small">攻击 +{{ row.attackPoints }} / 失守 -{{ row.defensePenalty }} / 宕机 -{{ row.downtimePenalty }}</span>
        </template>
      </el-table-column>
      <el-table-column label="check" width="100">
        <template #default="{ row }">
          <span class="mono small">{{ row.checkType }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDetail(row)">详情</el-button>
          <el-button
            v-for="a in actionsOf(row)"
            :key="a.action"
            size="small"
            :type="a.type"
            @click="transition(row, a.action)"
          >
            {{ a.label }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="新建 AWD 轮次" width="720px" :close-on-click-modal="false">
      <el-form label-position="top">
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 14px">
          <el-form-item label="所属赛事">
            <el-select v-model="form.eventId" style="width: 100%">
              <el-option v-for="e in events" :key="e.id" :value="e.id" :label="'[' + e.typeLabel + '] ' + e.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="轮次名称">
            <el-input v-model="form.name" placeholder="如：校赛 AWD 第一场" />
          </el-form-item>
          <el-form-item label="总轮数">
            <el-input-number v-model="form.totalRounds" :min="1" :max="50" style="width: 100%" />
          </el-form-item>
          <el-form-item label="单轮时长（秒）">
            <el-input-number v-model="form.roundSeconds" :min="30" :max="7200" style="width: 100%" />
          </el-form-item>
          <el-form-item label="攻击得分">
            <el-input-number v-model="form.attackPoints" :min="0" style="width: 100%" />
          </el-form-item>
          <el-form-item label="被攻破扣分">
            <el-input-number v-model="form.defensePenalty" :min="0" style="width: 100%" />
          </el-form-item>
          <el-form-item label="宕机扣分">
            <el-input-number v-model="form.downtimePenalty" :min="0" style="width: 100%" />
          </el-form-item>
          <el-form-item label="check 类型">
            <el-select v-model="form.checkType" style="width: 100%">
              <el-option value="TCP" label="TCP 探活" />
              <el-option value="HTTP" label="HTTP 探活" />
            </el-select>
          </el-form-item>
          <el-form-item label="HTTP check 地址模板">
            <el-input v-model="form.checkTarget" :disabled="form.checkType !== 'HTTP'" />
          </el-form-item>
          <el-form-item label="靶机镜像">
            <el-input v-model="form.image" placeholder="为空则跳过实例下发（check 判失败）" />
          </el-form-item>
          <el-form-item label="容器端口">
            <el-input-number v-model="form.containerPort" :min="1" :max="65535" style="width: 100%" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createRound">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog
      :model-value="!!detail"
      :title="detail ? detail.round.name : ''"
      width="1000px"
      @update:model-value="(v) => { if (!v) detail = null }"
    >
      <div v-if="detail" v-loading="detailLoading">
        <div style="display: flex; gap: 10px; flex-wrap: wrap; align-items: center; margin-bottom: 12px">
          <el-tag size="small" :type="statusType(detail.round.status)">{{ detail.round.statusLabel }}</el-tag>
          <el-tag size="small" effect="plain">第 {{ detail.round.currentRound }} / {{ detail.round.totalRounds }} 轮</el-tag>
          <el-tag size="small" type="info">剩余 {{ detail.round.remainingSeconds }}s</el-tag>
          <el-tag size="small" effect="plain">{{ detail.round.checkType }} check</el-tag>
          <div class="spacer" />
          <el-button size="small" @click="refreshDetail">刷新</el-button>
          <el-button size="small" type="primary" @click="runChecks">立即 check</el-button>
        </div>

        <el-tabs v-model="tab">
          <el-tab-pane label="队伍" name="teams">
            <div class="toolbar" style="margin-top: 0">
              <span class="muted small">开轮前可分配队伍（成员用逗号分隔）；开轮后自动下发实例与第 1 轮 flag</span>
              <div class="spacer" />
              <el-button v-if="canEditTeams" type="primary" size="small" @click="openTeamDialog">
                + 分配队伍
              </el-button>
            </div>
            <el-table :data="detail.teams" size="small" stripe>
              <el-table-column prop="name" label="队伍" width="160" />
              <el-table-column label="成员" min-width="200">
                <template #default="{ row }">
                  <span class="small">{{ (row.members || []).join('、') }}</span>
                </template>
              </el-table-column>
              <el-table-column label="实例" width="160">
                <template #default="{ row }">
                  <span v-if="row.host" class="mono small">{{ row.host }}:{{ row.port }}</span>
                  <span v-else class="muted small">未下发</span>
                </template>
              </el-table-column>
              <el-table-column label="当前 flag" min-width="260">
                <template #default="{ row }">
                  <span class="mono small">{{ row.flag || '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90">
                <template #default="{ row }">
                  <el-button v-if="canEditTeams || detail.round.status === 'PAUSED'" size="small" type="danger" @click="removeTeam(row)">
                    移除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="实时比分" name="score">
            <el-table :data="detail.scoreboard" size="small" stripe>
              <el-table-column label="#" width="60">
                <template #default="{ row }">{{ row.rank }}</template>
              </el-table-column>
              <el-table-column prop="teamName" label="队伍" min-width="160" />
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
          </el-tab-pane>

          <el-tab-pane label="check 结果" name="checks">
            <el-table :data="detail.checks" size="small" stripe max-height="420">
              <el-table-column prop="teamName" label="队伍" width="150" />
              <el-table-column label="结果" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.ok ? 'success' : 'danger'">{{ row.ok ? '正常' : '异常' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="耗时" width="90">
                <template #default="{ row }"><span class="mono small">{{ row.latencyMs }}ms</span></template>
              </el-table-column>
              <el-table-column prop="detail" label="详情" min-width="240" show-overflow-tooltip />
              <el-table-column label="时间" width="170">
                <template #default="{ row }"><span class="muted small">{{ formatDate(row.checkedAt) }}</span></template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="攻击记录" name="attacks">
            <el-table :data="detail.attacks" size="small" stripe>
              <el-table-column prop="attackerTeam" label="攻击方" width="160" />
              <el-table-column prop="victimTeam" label="被攻击方" width="160" />
              <el-table-column label="得分" width="90">
                <template #default="{ row }"><span class="mono">+{{ row.points }}</span></template>
              </el-table-column>
              <el-table-column prop="roundNo" label="轮次" width="80" />
              <el-table-column label="时间" width="170">
                <template #default="{ row }"><span class="muted small">{{ formatDate(row.createdAt) }}</span></template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>

    <el-dialog v-model="teamDialog" title="分配队伍" width="520px">
      <el-form label-position="top">
        <el-form-item label="队伍名称">
          <el-input v-model="teamForm.name" placeholder="如：0x00sec" />
        </el-form-item>
        <el-form-item label="成员账号（逗号分隔）">
          <el-input v-model="teamForm.members" type="textarea" :rows="2" placeholder="alice, bob" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="teamDialog = false">取消</el-button>
        <el-button type="primary" @click="addTeam">加入</el-button>
      </template>
    </el-dialog>
  </div>
</template>
