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
        <p v-else-if="hintOf(message) === 'thinking'" class="wait-hint" role="status">
          正在思考<span class="wait-dots" aria-hidden="true"><i>.</i><i>.</i><i>.</i></span>
        </p>
        <p v-else-if="hintOf(message) === 'compound'" class="wait-hint" role="status">
          {{ message.content }}
        </p>
        <MarkdownRenderer v-else-if="message.role === 'assistant'" :content="message.content" />
        <template v-else>{{ message.content }}</template>
        <span v-if="message.streaming && !hintOf(message)" class="cursor-blink">▍</span>
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
import { assistantWaitHint } from './assistantWaitHint'
import type { ChatMessage } from '../stores/chat'

defineProps<{ messages: ChatMessage[] }>()

function hintOf(message: ChatMessage) {
  return assistantWaitHint(message)
}
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
  background: #fff;
  color: var(--huiyi-ink);
  border: 1px solid var(--huiyi-line);
  border-radius: 10px 10px 3px 10px;
}

.message-row.assistant .message-bubble {
  background: var(--huiyi-card);
  color: var(--huiyi-ink);
  border-radius: 10px 10px 10px 3px;
}

.wait-hint {
  margin: 0;
  color: var(--huiyi-muted);
  font-size: 13px;
  line-height: 1.7;
}

.wait-dots i {
  font-style: normal;
  animation: wait-dot 1.4s infinite;
}

.wait-dots i:nth-child(2) {
  animation-delay: 0.2s;
}

.wait-dots i:nth-child(3) {
  animation-delay: 0.4s;
}

.cursor-blink {
  animation: blink 0.8s step-end infinite;
}

@keyframes wait-dot {
  0%,
  80%,
  100% {
    opacity: 0.2;
  }

  40% {
    opacity: 1;
  }
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .wait-dots i {
    animation: none;
    opacity: 1;
  }
}

.message-references {
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px solid var(--huiyi-line);
}

.references-label {
  font-size: 12px;
  color: var(--huiyi-muted);
}

.reference-tag {
  margin-left: 6px;
}
</style>
