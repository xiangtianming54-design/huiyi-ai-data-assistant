<template>
  <div class="message-list">
    <div
      v-for="(message, index) in messages"
      :key="index"
      class="message-row"
      :class="message.role"
    >
      <div class="message-bubble" :class="{ 'analysis-bubble': message.analysis }">
        <AnalysisResultCard v-if="message.analysis" :result="message.analysis" />
        <MarkdownRenderer v-else-if="message.role === 'assistant'" :content="message.content" />
        <template v-else>{{ message.content }}</template>
        <span v-if="message.streaming" class="cursor-blink">▍</span>
        <div v-if="message.references?.length" class="message-references">
          <span class="references-label">引用来源：</span>
          <el-tag
            v-for="reference in message.references"
            :key="reference.source"
            size="small"
            effect="plain"
            class="reference-tag"
          >
            {{ reference.source }} ({{ reference.score.toFixed(2) }})
          </el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import AnalysisResultCard from './AnalysisResultCard.vue'
import MarkdownRenderer from './MarkdownRenderer.vue'
import type { ChatMessage } from '../stores/chat'

defineProps<{ messages: ChatMessage[] }>()
</script>

<style scoped>
.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-row {
  display: flex;
}

.message-row.user {
  justify-content: flex-end;
}

.message-row.assistant {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: 10px;
  line-height: 1.7;
  word-break: break-word;
}

/* 数据查询富结果需要更大展示空间 */
.message-bubble.analysis-bubble {
  max-width: 92%;
  min-width: 60%;
}

.message-row.user .message-bubble {
  background: #1890ff;
  color: #fff;
}

.message-row.assistant .message-bubble {
  background: #fff;
  color: #303133;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.cursor-blink {
  animation: blink 0.8s step-end infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.message-references {
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed #e4e7ed;
}

.references-label {
  font-size: 12px;
  color: #909399;
}

.reference-tag {
  margin-left: 6px;
}
</style>
