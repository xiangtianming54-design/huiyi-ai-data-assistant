<template>
  <!-- markdown-it 默认转义 HTML（html: false），XSS 安全 -->
  <div class="markdown-body" v-html="rendered"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'

const props = defineProps<{ content: string }>()

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const rendered = computed(() => md.render(props.content || ''))
</script>

<style scoped>
.markdown-body :deep(p) {
  margin: 4px 0;
  line-height: 1.7;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 4px 0;
  padding-left: 20px;
}

.markdown-body :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  border-radius: 3px;
  padding: 1px 4px;
  font-size: 13px;
}

.markdown-body :deep(pre) {
  background: #282c34;
  color: #abb2bf;
  border-radius: 6px;
  padding: 12px;
  overflow-x: auto;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 4px 10px;
}
</style>
