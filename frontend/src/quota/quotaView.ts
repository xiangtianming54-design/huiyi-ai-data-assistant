/** 剩余占上限的百分比。剩余为负或上限非正时为 0。 */
export function quotaPercent(remaining: number, limit: number): number {
  if (remaining < 0 || limit <= 0) {
    return 0
  }
  return Math.round(remaining * 100 / limit)
}

/** 新建账号的等级只能是 2 或 3。 */
export function tierChoiceAllowed(tier: number): boolean {
  return tier === 2 || tier === 3
}

/** 4291 限流、4292 可提额、4293 第三级额度用尽。其他码不是这三种。 */
export function quotaErrorKind(code: number): 'rate' | 'quota' | 'tier3' | null {
  if (code === 4291) return 'rate'
  if (code === 4292) return 'quota'
  if (code === 4293) return 'tier3'
  return null
}
