<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../shared/api.js'
import { eventStatusType, eventTypeType } from '../../shared/format.js'

const events = ref([])
const currentEventId = ref('')
const tab = ref('paper')
const paper = ref(null)
const loading = ref(false)
const submitting = ref(false)
const answers = reactive({})
const result = ref(null)
const report = ref(null)

const questionCount = computed(() => (paper.value ? paper.value.questions.length : 0))
const answeredCount = computed(() =>
  paper.value ? paper.value.questions.filter((q) => answers[q.id] && answers[q.id].length).length : 0
)

function resetState() {
  paper.value = null
  report.value = null
  result.value = null
  Object.keys(answers).forEach((k) => delete answers[k])
}

async function loadEvents() {
  events.value = await api('/events')
}

async function loadPaper() {
  if (!currentEventId.value) return
  loading.value = true
  resetState()
  try {
    paper.value = await api('/events/' + encodeURIComponent(currentEventId.value) + '/theory/paper')
    for (const q of paper.value.questions) {
      answers[q.id] = q.answer ? [...q.answer] : []
    }
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

async function loadReport() {
  if (!currentEventId.value) return
  try {
    report.value = await api('/events/' + encodeURIComponent(currentEventId.value) + '/theory/report')
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function submit() {
  const unanswered = questionCount.value - answeredCount.value
  try {
    await ElMessageBox.confirm(
      answeredCount.value === 0
        ? '尚未作答任何题目，确认提交空卷？'
        : unanswered > 0
          ? '还有 ' + unanswered + ' 道题未作答，提交后不可修改，确认提交？'
          : '提交后不可修改，确认提交？',
      '提交试卷',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    const payload = {
      answers: paper.value.questions
        .filter((q) => answers[q.id] && answers[q.id].length)
        .map((q) => ({ questionId: q.id, answer: answers[q.id] }))
    }
    result.value = await api('/events/' + encodeURIComponent(currentEventId.value) + '/theory/submit', {
      method: 'POST',
      body: payload
    })
    ElMessage.success('提交成功，得分 ' + result.value.totalPoints + ' / ' + paper.value.totalPoints)
    tab.value = 'report'
    await loadReport()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    submitting.value = false
  }
}

function answerState(q) {
  if (!result.value) return ''
  const detail = result.value.details.find((d) => d.questionId === q.id)
  if (!detail) return 'unanswered'
  return detail.correct ? 'correct' : 'wrong'
}

watch(tab, (value) => {
  if (value === 'report') loadReport()
})
watch(currentEventId, () => loadPaper())

onMounted(async () => {
  try {
    await loadEvents()
    const preferred =
      events.value.find((e) => e.type === 'COMPETITION' && e.status === 'ONGOING') ||
      events.value.find((e) => e.type === 'PRACTICE') ||
      events.value[0]
    if (preferred) currentEventId.value = preferred.id
    await loadPaper()
  } catch (err) {
    ElMessage.error(err.message)
  }
})
</script>

<template>
  <div>
    <div class="toolbar" style="margin-top: 0">
      <el-select v-model="currentEventId" size="small" style="width: 280px">
        <el-option
          v-for="e in events"
          :key="e.id"
          :value="e.id"
          :label="'[' + e.typeLabel + '] ' + e.name"
        />
      </el-select>
      <el-button size="small" @click="loadPaper">重新加载试卷</el-button>
      <div class="spacer" />
      <span class="muted small">同人同卷：试卷按赛事配置随机抽题并固化，刷新不会换题</span>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="答题" name="paper">
        <el-card v-loading="loading" shadow="never">
          <div v-if="!paper" class="muted" style="text-align: center; padding: 30px">
            暂无试卷。请确认该赛事已配置理论赛题目与抽题规则。
          </div>
          <template v-else>
            <div style="display: flex; gap: 20px; flex-wrap: wrap; margin-bottom: 16px">
              <el-tag size="small" effect="plain">共 {{ questionCount }} 题</el-tag>
              <el-tag size="small" type="success">满分 {{ paper.totalPoints }}</el-tag>
              <el-tag size="small" type="info">已作答 {{ answeredCount }} / {{ questionCount }}</el-tag>
            </div>

            <el-alert
              v-if="result"
              type="success"
              :closable="false"
              show-icon
              :title="'已提交：得分 ' + result.totalPoints + '，答对 ' + result.correctCount + ' / ' + result.questionCount"
              style="margin-bottom: 14px"
            />

            <div v-for="(q, index) in paper.questions" :key="q.id" style="padding: 16px 0; border-bottom: 1px dashed var(--border)">
              <div style="display: flex; gap: 8px; margin-bottom: 8px; flex-wrap: wrap">
                <strong class="mono">Q{{ index + 1 }}</strong>
                <el-tag size="small" effect="plain">{{ q.kindLabel }}</el-tag>
                <el-tag size="small" type="info">{{ q.points }} 分</el-tag>
                <el-tag v-if="q.knowledgeTag" size="small" type="warning" effect="light">{{ q.knowledgeTag }}</el-tag>
                <el-tag v-if="answerState(q) === 'correct'" size="small" type="success">✓ 正确</el-tag>
                <el-tag v-if="answerState(q) === 'wrong'" size="small" type="danger">✗ 错误</el-tag>
              </div>
              <div style="white-space: pre-wrap; line-height: 1.7; color: var(--text-title); margin-bottom: 10px">
                {{ q.stem }}
              </div>

              <div v-if="q.kind === 'MULTI'">
                <el-checkbox-group v-model="answers[q.id]" :disabled="!!result">
                  <el-checkbox
                    v-for="(opt, i) in q.options"
                    :key="i"
                    :value="i"
                    style="display: block; margin: 6px 0"
                  >
                    <span class="mono">{{ String.fromCharCode(65 + i) }}.</span> {{ opt }}
                  </el-checkbox>
                </el-checkbox-group>
              </div>
              <div v-else>
                <el-radio-group v-model="answers[q.id][0]" :disabled="!!result">
                  <div v-for="(opt, i) in q.options" :key="i" style="margin: 6px 0">
                    <el-radio :value="i">
                      <span class="mono">{{ String.fromCharCode(65 + i) }}.</span> {{ opt }}
                    </el-radio>
                  </div>
                </el-radio-group>
              </div>
            </div>

            <div style="margin-top: 18px; display: flex; justify-content: flex-end; gap: 12px">
              <el-button @click="loadReport">查看能力报告</el-button>
              <el-button type="primary" :loading="submitting" :disabled="!!result" @click="submit">
                提交试卷
              </el-button>
            </div>
          </template>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="能力报告" name="report">
        <el-card shadow="never" v-loading="loading">
          <div v-if="!report" class="muted" style="text-align: center; padding: 30px">暂无报告数据</div>
          <template v-else>
            <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 16px">
              <el-card shadow="never">
                <div class="muted small">已得 / 满分</div>
                <div class="mono" style="font-size: 22px; color: var(--color-primary)">
                  {{ report.totalEarned }} / {{ report.totalPossible }}
                </div>
              </el-card>
              <el-card shadow="never">
                <div class="muted small">总正确率</div>
                <div class="mono" style="font-size: 22px">{{ (report.accuracy * 100).toFixed(1) }}%</div>
              </el-card>
              <el-card shadow="never">
                <div class="muted small">知识点数量</div>
                <div class="mono" style="font-size: 22px">{{ report.tags.length }}</div>
              </el-card>
            </div>

            <el-table :data="report.tags" size="small" stripe>
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
          </template>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
