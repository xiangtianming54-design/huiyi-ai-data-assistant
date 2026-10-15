<template>
  <div class="analysis-result-card">
    <!-- 总结 -->
    <div class="answer-meta">
      <el-tag v-if="result.summary" size="small" effect="plain">
        {{ result.summary.rowCount }} 行
      </el-tag>
      <el-tag
        v-if="result.summary && result.summary.chartType !== 'table'"
        size="small"
        type="success"
        effect="plain"
      >
        {{ chartTypeText }}
      </el-tag>
    </div>
    <MarkdownRenderer :content="result.answer" />

    <!-- SQL 折叠块 -->
    <el-collapse v-if="result.sql" class="sql-collapse">
      <el-collapse-item title="查看生成的 SQL" name="sql">
        <pre class="sql-block"><code>{{ result.sql }}</code></pre>
      </el-collapse-item>
    </el-collapse>

    <!-- 图表 -->
    <EChartsPanel
      v-if="showChart"
      :rows="result.rows"
      :chart-type="result.summary!.chartType"
      class="result-chart"
    />

    <!-- 明细表 -->
    <SqlResultTable v-if="result.rows.length" :rows="result.rows" class="result-table" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import EChartsPanel from './EChartsPanel.vue'
import MarkdownRenderer from './MarkdownRenderer.vue'
import SqlResultTable from './SqlResultTable.vue'
import type { AnalysisResult } from '../api'

const props = defineProps<{ result: AnalysisResult }>()

const chartTypeText = computed(() => {
  const map: Record<string, string> = { bar: '柱状图', line: '折线图', pie: '饼图', table: '表格' }
  return map[props.result.summary?.chartType ?? ''] ?? '图表'
})

const showChart = computed(
  () =>
    props.result.summary &&
    props.result.summary.chartType !== 'table' &&
    props.result.rows.length > 1
)
</script>

<style scoped>
.answer-meta {
  margin-bottom: 8px;
  display: flex;
  gap: 8px;
}

.sql-collapse {
  margin-top: 8px;
  border: none;
}

.sql-collapse :deep(.el-collapse-item__header) {
  font-size: 13px;
  color: #606266;
  border: none;
  height: 32px;
  line-height: 32px;
}

.sql-collapse :deep(.el-collapse-item__wrap) {
  border: none;
}

.sql-block {
  background: #282c34;
  color: #61afef;
  border-radius: 6px;
  padding: 10px 14px;
  margin: 0;
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.6;
}

.result-chart {
  margin-top: 12px;
}

.result-table {
  margin-top: 12px;
}
</style>
