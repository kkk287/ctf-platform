<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../shared/api.js'

const events = ref([])
const currentEventId = ref('')
const tab = ref('questions')

const questions = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)

const JUDGE_OPTIONS = ['正确', '错误']
const KINDS = [
  { value: 'SINGLE', label: '单选' },
  { value: 'MULTI', label: '多选' },
  { value: 'JUDGE', label: '判断' }
]
const DIFFICULTIES = [
  { value: 'EASY', label: '简单' },
  { value: 'MEDIUM', label: '中等' },
  { value: 'HARD', label: '困难' }
]

const form = reactive({
  kind: 'SINGLE',
  stem: '',
  options: ['', ''],
  answer: [],
  knowledgeTag: '',
  difficulty: 'MEDIUM'
})

const config = ref(null)
const configForm = reactive({
  singleCount: 0,
  singlePoints: 0,
  multiCount: 0,
  multiPoints: 0,
  judgeCount: 0,
  judgePoints: 0
})
const configSaving = ref(false)

const overview = ref(null)
const overviewLoading = ref(false)

const isJudge = computed(() => form.kind === 'JUDGE')
const isMulti = computed(() => form.kind === 'MULTI')
const effectiveOptions = computed(() => (isJudge.value ? JUDGE_OPTIONS : form.options))

async function loadEvents() {
  events.value = await api('/events')
}

async function loadQuestions() {
  if (!currentEventId.value) return
  loading.value = true
  try {
    questions.value = await api('/admin/theory/questions?eventId=' + encodeURIComponent(currentEventId.value))
  } catch (err) {
    ElMessage.error(err.message)
    questions.value = []
  } finally {
    loading.value = false
  }
}

async function loadConfig() {
  if (!currentEventId.value) return
  try {
    const data = await api('/admin/theory/config?eventId=' + encodeURIComponent(currentEventId.value))
    config.value = data
    Object.assign(configForm, {
      singleCount: data.singleCount,
      singlePoints: data.singlePoints,
      multiCount: data.multiCount,
      multiPoints: data.multiPoints,
      judgeCount: data.judgeCount,
      judgePoints: data.judgePoints
    })
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function loadOverview() {
  if (!currentEventId.value) return
  overviewLoading.value = true
  try {
    overview.value = await api('/admin/theory/report?eventId=' + encodeURIComponent(currentEventId.value))
  } catch (err) {
    ElMessage.error(err.message)
    overview.value = null
  } finally {
    overviewLoading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    kind: 'SINGLE',
    stem: '',
    options: ['', ''],
    answer: [],
    knowledgeTag: '',
    difficulty: 'MEDIUM'
  })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    kind: row.kind,
    stem: row.stem,
    options: row.kind === 'JUDGE' ? ['', ''] : [...(row.options || [])],
    answer: [...(row.answer || [])],
    knowledgeTag: row.knowledgeTag || '',
    difficulty: row.difficulty || 'MEDIUM'
  })
  dialogVisible.value = true
}

function addOption() {
  form.options.push('')
}

function removeOption(index) {
  form.options.splice(index, 1)
  form.answer = form.answer.filter((i) => i !== index).map((i) => (i > index ? i - 1 : i))
}

function onKindChange() {
  form.answer = []
  if (form.kind !== 'JUDGE' && form.options.length < 2) {
    form.options = ['', '']
  }
}

function toggleAnswer(index) {
  if (isMulti.value) {
    form.answer = form.answer.includes(index)
      ? form.answer.filter((i) => i !== index)
      : [...form.answer, index].sort((a, b) => a - b)
  } else {
    form.answer = [index]
  }
}

async function saveQuestion() {
  if (!form.stem.trim()) {
    ElMessage.error('题干不能为空')
    return
  }
  const options = isJudge.value ? JUDGE_OPTIONS : form.options.map((o) => o.trim()).filter((o) => o)
  if (!isJudge.value && options.length < 2) {
    ElMessage.error('至少需要两个选项')
    return
  }
  if (!form.answer.length) {
    ElMessage.error('请设置正确答案')
    return
  }
  saving.value = true
  const body = {
    eventId: currentEventId.value,
    kind: form.kind,
    stem: form.stem.trim(),
    options: isJudge.value ? [] : options,
    answer: form.answer,
    knowledgeTag: form.knowledgeTag.trim() || null,
    difficulty: form.difficulty
  }
  try {
    if (editingId.value) {
      await api('/admin/theory/questions/' + encodeURIComponent(editingId.value), { method: 'PUT', body })
    } else {
      await api('/admin/theory/questions', { method: 'POST', body })
    }
    ElMessage.success(editingId.value ? '题目已更新' : '题目已新增')
    dialogVisible.value = false
    await Promise.all([loadQuestions(), loadConfig()])
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}

async function removeQuestion(row) {
  try {
    await ElMessageBox.confirm('确认删除该题目？', '删除题目', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await api('/admin/theory/questions/' + encodeURIComponent(row.id), { method: 'DELETE' })
    ElMessage.success('已删除')
    await Promise.all([loadQuestions(), loadConfig()])
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function saveConfig() {
  configSaving.value = true
  try {
    const body = { eventId: currentEventId.value, ...configForm }
    config.value = await api('/admin/theory/config', { method: 'PUT', body })
    ElMessage.success('组卷规则已保存')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    configSaving.value = false
  }
}

function answerLabel(row) {
  const opts = row.options || []
  return (row.answer || [])
    .map((i) => String.fromCharCode(65 + i) + (opts[i] ? '.' + opts[i] : ''))
    .join(' / ')
}

watch(currentEventId, async () => {
  await Promise.all([loadQuestions(), loadConfig()])
  overview.value = null
})

watch(tab, (value) => {
  if (value === 'report') loadOverview()
})

onMounted(async () => {
  try {
    await loadEvents()
    const preferred =
      events.value.find((e) => e.type === 'COMPETITION' && e.status === 'ONGOING') ||
      events.value.find((e) => e.type === 'PRACTICE') ||
      events.value[0]
    if (preferred) currentEventId.value = preferred.id
    await Promise.all([loadQuestions(), loadConfig()])
  } catch (err) {
    ElMessage.error(err.message)
  }
})
</script>

<template>
  <div>
    <div class="toolbar" style="margin-top: 0">
      <el-select v-model="currentEventId" size="small" style="width: 280px">
        <el-option v-for="e in events" :key="e.id" :value="e.id" :label="'[' + e.typeLabel + '] ' + e.name" />
      </el-select>
      <div class="spacer" />
      <span class="muted small">理论赛：题库出题 → 组卷规则抽题 → 全员知识点报告</span>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="题库" name="questions">
        <div class="toolbar" style="margin-top: 0">
          <el-button size="small" @click="loadQuestions">刷新</el-button>
          <div class="spacer" />
          <el-button type="primary" size="small" @click="openCreate">+ 新增题目</el-button>
        </div>
        <el-table v-loading="loading" :data="questions" size="small" stripe>
          <el-table-column label="题型" width="80">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.kindLabel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="stem" label="题干" min-width="260" show-overflow-tooltip />
          <el-table-column label="选项" width="70">
            <template #default="{ row }">
              <span class="muted small">{{ (row.options || []).length }} 项</span>
            </template>
          </el-table-column>
          <el-table-column label="答案" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="mono small">{{ answerLabel(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="知识点" width="130">
            <template #default="{ row }">
              <span class="muted small">{{ row.knowledgeTag || '未标注' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="难度" width="80">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ row.difficultyLabel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="removeQuestion(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="组卷规则" name="config">
        <el-card shadow="never">
          <div v-if="config" style="display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 16px">
            <el-tag size="small" effect="plain">题库总量 {{ config.poolSize }}</el-tag>
            <el-tag size="small" type="success">抽题总数 {{ config.totalQuestions }}</el-tag>
            <el-tag size="small" type="warning">试卷满分 {{ config.totalPoints }}</el-tag>
          </div>
          <el-form label-position="top" style="max-width: 760px">
            <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px">
              <el-form-item label="单选题数量">
                <el-input-number v-model="configForm.singleCount" :min="0" :max="200" style="width: 100%" />
              </el-form-item>
              <el-form-item label="单选题分值">
                <el-input-number v-model="configForm.singlePoints" :min="0" :max="1000" style="width: 100%" />
              </el-form-item>
              <el-form-item label="单选小计">
                <span class="mono">{{ configForm.singleCount * configForm.singlePoints }}</span>
              </el-form-item>
              <el-form-item label="多选题数量">
                <el-input-number v-model="configForm.multiCount" :min="0" :max="200" style="width: 100%" />
              </el-form-item>
              <el-form-item label="多选题分值">
                <el-input-number v-model="configForm.multiPoints" :min="0" :max="1000" style="width: 100%" />
              </el-form-item>
              <el-form-item label="多选小计">
                <span class="mono">{{ configForm.multiCount * configForm.multiPoints }}</span>
              </el-form-item>
              <el-form-item label="判断题数量">
                <el-input-number v-model="configForm.judgeCount" :min="0" :max="200" style="width: 100%" />
              </el-form-item>
              <el-form-item label="判断题分值">
                <el-input-number v-model="configForm.judgePoints" :min="0" :max="1000" style="width: 100%" />
              </el-form-item>
              <el-form-item label="判断小计">
                <span class="mono">{{ configForm.judgeCount * configForm.judgePoints }}</span>
              </el-form-item>
            </div>
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="抽题规则保存后，选手首次取卷即按 (赛事, 用户) 固定抽题并物化；调整规则只影响尚未取卷的用户。"
            />
            <div style="margin-top: 16px">
              <el-button type="primary" :loading="configSaving" @click="saveConfig">保存组卷规则</el-button>
            </div>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="能力报告" name="report">
        <div class="toolbar" style="margin-top: 0">
          <el-button size="small" @click="loadOverview">刷新</el-button>
          <div class="spacer" />
          <span v-if="overview" class="muted small">已取卷人数 {{ overview.userCount }}</span>
        </div>
        <el-card v-loading="overviewLoading" shadow="never">
          <div v-if="!overview" class="muted" style="text-align: center; padding: 30px">
            暂无数据（需先配置理论赛并有选手取卷）
          </div>
          <template v-else>
            <p class="section-title">知识点掌握情况</p>
            <el-table :data="overview.tags" size="small" stripe>
              <el-table-column prop="knowledgeTag" label="知识点" min-width="160" />
              <el-table-column prop="questionCount" label="题数" width="80" />
              <el-table-column prop="correctCount" label="答对" width="80" />
              <el-table-column label="得分" width="120">
                <template #default="{ row }">
                  <span class="mono">{{ row.pointsEarned }} / {{ row.pointsPossible }}</span>
                </template>
              </el-table-column>
              <el-table-column label="正确率" min-width="200">
                <template #default="{ row }">
                  <el-progress :percentage="Math.round(row.accuracy * 100)" :stroke-width="10" />
                </template>
              </el-table-column>
            </el-table>

            <p class="section-title" style="margin-top: 22px">选手得分</p>
            <el-table :data="overview.users" size="small" stripe>
              <el-table-column label="#" width="60">
                <template #default="{ $index }">{{ $index + 1 }}</template>
              </el-table-column>
              <el-table-column prop="displayName" label="选手" min-width="140" />
              <el-table-column prop="username" label="账号" min-width="140" />
              <el-table-column label="得分" width="140">
                <template #default="{ row }">
                  <span class="mono">{{ row.earned }} / {{ row.possible }}</span>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑题目' : '新增题目'"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 14px">
          <el-form-item label="题型">
            <el-select v-model="form.kind" style="width: 100%" @change="onKindChange">
              <el-option v-for="k in KINDS" :key="k.value" :value="k.value" :label="k.label" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="form.difficulty" style="width: 100%">
              <el-option v-for="d in DIFFICULTIES" :key="d.value" :value="d.value" :label="d.label" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="题干">
          <el-input v-model="form.stem" type="textarea" :rows="3" maxlength="2000" show-word-limit />
        </el-form-item>

        <el-form-item label="选项">
          <div v-if="isJudge" class="muted small">判断题固定为「正确 / 错误」两个选项</div>
          <div v-else style="width: 100%">
            <div v-for="(opt, i) in form.options" :key="i" style="display: flex; gap: 8px; margin-bottom: 8px">
              <el-input v-model="form.options[i]" :placeholder="'选项 ' + String.fromCharCode(65 + i)" />
              <el-button :disabled="form.options.length <= 2" @click="removeOption(i)">删除</el-button>
            </div>
            <el-button size="small" @click="addOption">+ 添加选项</el-button>
          </div>
        </el-form-item>

        <el-form-item :label="isMulti ? '正确答案（可多选）' : '正确答案'">
          <div style="display: flex; gap: 10px; flex-wrap: wrap">
            <el-checkbox
              v-for="(opt, i) in effectiveOptions"
              :key="i"
              :model-value="form.answer.includes(i)"
              @change="toggleAnswer(i)"
            >
              <span class="mono">{{ String.fromCharCode(65 + i) }}.</span> {{ opt || '（待填写）' }}
            </el-checkbox>
          </div>
        </el-form-item>

        <el-form-item label="知识点">
          <el-input v-model="form.knowledgeTag" placeholder="如：Web 安全 / 密码学 / 逆向，用于能力报告聚合" maxlength="60" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveQuestion">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
