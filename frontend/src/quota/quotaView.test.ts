import assert from 'node:assert/strict'
import { test } from 'node:test'
import { quotaErrorKind, quotaPercent, tierChoiceAllowed } from './quotaView.ts'

test('negative remaining shows 0 percent', () => {
  assert.equal(quotaPercent(-1, 500000), 0)
})

test('rate limit is not a boost', () => {
  assert.equal(quotaErrorKind(4291), 'rate')
  assert.equal(quotaErrorKind(4292), 'quota')
  assert.equal(quotaErrorKind(4293), 'tier3')
})

test('tier choice accepts only 2 and 3', () => {
  assert.equal(tierChoiceAllowed(1), false)
  assert.equal(tierChoiceAllowed(2), true)
  assert.equal(tierChoiceAllowed(3), true)
})
