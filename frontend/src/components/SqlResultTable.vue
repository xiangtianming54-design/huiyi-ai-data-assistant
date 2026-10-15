<template>
  <div class="sql-result-table">
    <el-table :data="pagedRows" border stripe size="small" max-height="360">
      <el-table-column
        v-for="column in columns"
        :key="column"
        :prop="column"
        :label="column"
        show-overflow-tooltip
      />
    </el-table>
    <el-pagination
      v-if="rows.length > pageSize"
      v-model:current-page="currentPage"
      :page-size="pageSize"
      :total="rows.length"
      layout="total, prev, pager, next"
      class="table-pagination"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{ rows: Record<string, unknown>[] }>()

const currentPage = ref(1)
const pageSize = 10

const columns = computed(() => (props.rows.length > 0 ? Object.keys(props.rows[0]) : []))

const pagedRows = computed(() =>
  props.rows.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize)
)
</script>

<style scoped>
.table-pagination {
  margin-top: 8px;
  justify-content: flex-end;
}
</style>
