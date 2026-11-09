import assert from 'node:assert/strict'
import { test } from 'node:test'
import { COMPOUND_WAIT_HINT, assistantWaitHint } from './assistantWaitHint.ts'

test('streaming assistant message with empty content is thinking', () => {
  assert.equal(assistantWaitHint({ role: 'assistant', content: '', streaming: true }), 'thinking')
})

test('compound placeholder content is compound', () => {
  assert.equal(
    assistantWaitHint({ role: 'assistant', content: COMPOUND_WAIT_HINT, streaming: true }),
    'compound'
  )
})

test('real answer text streaming gives no hint', () => {
  assert.equal(assistantWaitHint({ role: 'assistant', content: '有效订单是……', streaming: true }), null)
})

test('finished empty message gives no hint', () => {
  assert.equal(assistantWaitHint({ role: 'assistant', content: '' }), null)
})

test('message with analysis gives no hint', () => {
  const analysis = {
    sessionId: 's1',
    question: 'q',
    sql: null,
    rows: [],
    summary: null,
    answer: ''
  }
  assert.equal(assistantWaitHint({ role: 'assistant', content: '', streaming: true, analysis }), null)
})
