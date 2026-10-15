<template>
  <div ref="chartRef" class="echarts-panel"></div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  rows: Record<string, unknown>[]
  chartType: string
}>()

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

onMounted(() => {
  chart = echarts.init(chartRef.value!)
  renderChart()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})

watch(() => [props.rows, props.chartType], renderChart, { deep: true })

function resize() {
  chart?.resize()
}

/** 推断维度列（日期/字符串）与数值列 */
function inferColumns() {
  const keys = Object.keys(props.rows[0] ?? {})
  const first = props.rows[0] ?? {}
  const dateKey = keys.find((k) => /date/i.test(k))
  const categoryKey =
    dateKey ?? keys.find((k) => typeof first[k] === 'string') ?? keys[0]
  const numericKey = keys.find((k) => typeof first[k] === 'number')
  return { categoryKey, numericKey }
}

function renderChart() {
  if (!chart || !props.rows.length || props.chartType === 'table') return
  const { categoryKey, numericKey } = inferColumns()
  if (!categoryKey || !numericKey) return

  const categories = props.rows.map((row) => String(row[categoryKey] ?? ''))
  const values = props.rows.map((row) => Number(row[numericKey] ?? 0))

  if (props.chartType === 'pie') {
    chart.setOption({
      tooltip: { trigger: 'item' },
      series: [
        {
          type: 'pie',
          radius: ['35%', '65%'],
          data: categories.map((name, index) => ({ name, value: values[index] })),
          label: { formatter: '{b}: {d}%' }
        }
      ]
    })
    return
  }

  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 60, right: 24, top: 32, bottom: 60 },
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: { rotate: categories.some((c) => c.length > 6) ? 30 : 0 }
    },
    yAxis: { type: 'value' },
    series: [
      {
        type: props.chartType === 'line' ? 'line' : 'bar',
        data: values,
        smooth: true,
        label: { show: true, position: 'top' }
      }
    ]
  }, true)
}
</script>

<style scoped>
.echarts-panel {
  width: 100%;
  height: 360px;
}
</style>
