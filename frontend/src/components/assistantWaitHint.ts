import type { ChatMessage } from '../stores/chat'

/** 复合问题占位气泡的文案，与 chat store 的占位消息保持一致。 */
export const COMPOUND_WAIT_HINT = '正在分解并并行回答多个问题…'

/**
 * 助手气泡的等待提示：流式且还没有内容时是「思考中」；内容是复合占位文案时是「分解中」。
 * 已有真实回答、消息结束、或带分析结果卡片时不提示。
 */
export function assistantWaitHint(message: ChatMessage): 'thinking' | 'compound' | null {
  if (!message.streaming || message.analysis) {
    return null
  }
  if (message.content === COMPOUND_WAIT_HINT) {
    return 'compound'
  }
  if (message.content === '') {
    return 'thinking'
  }
  return null
}
