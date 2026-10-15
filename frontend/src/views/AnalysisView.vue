<template>
  <div class="analysis-view">
    <el-card shadow="never">
      <template #header>
        <span class="view-title">数据分析（Text-to-SQL）</span>
      </template>

      <div class="query-input">
        <el-input
          v-model="question"
          placeholder="输入数据分析问题，例如：上个月销售额最高的 10 个商品是什么？"
          clearable
          @keydown.enter.prevent="runQuery"
        >
          <template #append>
            <el-button type="primary" :loading="loading" @click="runQuery">分析</el-button>
          </template>
        </el-input>
        <div class="example-questions">
          <span class="example-label">示例：</span>
          <el-button
            v-for="example in exampleQuestions"
            :key="example"
            size="small"
            text
            type="primary"
            @click="askExample(example)"
          >
            {{ example }}
          </el-button>
        </div>
      </div>

      <template v-if="result">
        <!-- 总结卡片 -->
        <el-card class="answer-card" shadow="never">
          <div class="answer-meta">
            <el-tag v-if="result.summary" size="small" effect="plain">
              {{ result.summary.rowCount }} 行
            </el-tag>
            <el-tag v-if="result.summary && result.summary.chartType !== 'table'" size="small" type="success" effect="plain">
              {{ chartTypeText }}
            </el-tag>
          </div>
          <MarkdownRenderer :content="result.answer" />
        </el-card>

        <!-- SQL 块 -->
        <el-card v-if="result.sql" class="sql-card" shadow="never">
          <template #header>
            <span class="block-title">生成的 SQL</span>
          </template>
          <pre class="sql-block"><code>{{ result.sql }}</code></pre>
        </el-card>

        <!-- 图表（table 类型不展示） -->
        <el-card v-if="showChart" class="chart-card" shadow="never">
          <template #header>
            <span class="block-title">图表</span>
          </template>
          <EChartsPanel :rows="result.rows" :chart-type="result.summary!.chartType" />
        </el-card>

        <!-- 数据表格 -->
        <el-card v-if="result.rows.length" class="table-card" shadow="never">
          <template #header>
            <span class="block-title">数据明细</span>
          </template>
          <SqlResultTable :rows="result.rows" />
        </el-card>
      </template>

      <el-empty v-else description="输入问题开始分析" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import EChartsPanel from '../components/EChartsPanel.vue'
import MarkdownRenderer from '../components/MarkdownRenderer.vue'
import SqlResultTable from '../components/SqlResultTable.vue'
import { analysisApi, type AnalysisResult } from '../api'

const question = ref('')
const loading = ref(false)
const result = ref<AnalysisResult | null>(null)
const sessionId = ref<string>()

const exampleQuestions = [
  '上个月销售额最高的 10 个商品是什么？',
  '华东地区各渠道的有效订单量是多少？',
  '删除所有销售订单。'
]

const chartTypeText = computed(() => {
  const map: Record<string, string> = { bar: '柱状图', line: '折线图', pie: '饼图', table: '表格' }
  return map[result.value?.summary?.chartType ?? ''] ?? '图表'
})

const showChart = computed(
  () =>
    result.value?.summary &&
    result.value.summary.chartType !== 'table' &&
    result.value.rows.length > 1
)

async function runQuery() {
  const text = question.value.trim()
  if (!text || loading.value) return
  loading.value = true
  try {
    const response = await analysisApi.query(text, sessionId.value)
    result.value = response.data.data
    sessionId.value = result.value.sessionId
  } catch {
    // 错误提示已由拦截器统一处理
  } finally {
    loading.value = false
  }
}

function askExample(text: string) {
  question.value = text
  runQuery()
}
</script>

<style scoped>
.analysis-view {
  max-width: 960px;
  margin: 0 auto;
}

.view-title {
  font-weight: 600;
}

.example-questions {
  margin-top: 8px;
}

.example-label {
  font-size: 12px;
  color: #909399;
}

.answer-card,
.sql-card,
.chart-card,
.table-card {
  margin-top: 16px;
}

.answer-meta {
  margin-bottom: 8px;
  display: flex;
  gap: 8px;
}

.block-title {
  font-weight: 600;
  font-size: 14px;
}

.sql-block {
  background: #282c34;
  color: #61afef;
  border-radius: 6px;
  padding: 12px 16px;
  margin: 0;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
}
</style>
