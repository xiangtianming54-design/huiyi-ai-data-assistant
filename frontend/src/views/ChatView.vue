<template>
  <div class="chat-view">
    <el-card class="chat-card" shadow="never">
      <template #header>
        <div class="chat-header">
          <span>智能问答</span>
          <el-button size="small" text type="primary" @click="store.reset()">新会话</el-button>
        </div>
      </template>

      <div ref="scrollRef" class="chat-messages">
        <el-empty v-if="!store.messages.length" description="开始提问吧">
          <div class="example-questions">
            <el-button
              v-for="example in exampleQuestions"
              :key="example"
              size="small"
              round
              @click="askExample(example)"
            >
              {{ example }}
            </el-button>
          </div>
        </el-empty>
        <MessageList v-else :messages="store.messages" />
      </div>

      <div class="chat-input">
        <el-input
          v-model="question"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="输入业务口径问题（例如：什么是有效订单？），Enter 发送"
          @keydown.enter.exact.prevent="send"
        />
        <div class="chat-actions">
          <el-button v-if="store.streaming" type="danger" plain @click="store.stop()">停止生成</el-button>
          <el-button
            v-else
            type="primary"
            :disabled="!question.trim()"
            @click="send"
          >
            发送
          </el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import MessageList from '../components/MessageList.vue'
import { useChatStore } from '../stores/chat'

const store = useChatStore()
const question = ref('')
const scrollRef = ref<HTMLElement>()

const exampleQuestions = [
  '什么是有效订单？',
  '销售额的统计口径是什么？',
  '公司年假有多少天？'
]

function send() {
  const text = question.value.trim()
  if (!text || store.streaming) return
  question.value = ''
  store.ask(text)
}

function askExample(text: string) {
  if (store.streaming) return
  store.ask(text)
}

// 消息变化时自动滚动到底部
watch(
  () => store.messages.map((m) => m.content).join(''),
  async () => {
    await nextTick()
    if (scrollRef.value) {
      scrollRef.value.scrollTop = scrollRef.value.scrollHeight
    }
  }
)
</script>

<style scoped>
.chat-view {
  max-width: 960px;
  margin: 0 auto;
  height: calc(100vh - 40px);
}

.chat-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chat-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 8px 4px;
}

.example-questions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}

.chat-input {
  border-top: 1px solid #e4e7ed;
  padding-top: 12px;
}

.chat-actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}
</style>
