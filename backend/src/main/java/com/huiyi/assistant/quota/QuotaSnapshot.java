package com.huiyi.assistant.quota;

/**
 * 某人上海自然日的额度快照。剩余可以为负；剩余小于 0 时百分比为 0。
 *
 * @param tier             用户等级
 * @param usedTokens       当日已成功调用的 Token
 * @param limitTokens      当日上限，含已通过的提额
 * @param remainingTokens  上限减已用
 * @param remainingPercent 剩余占上限的百分比；剩余小于 0 时为 0
 * @param boostAllowed     等级不是 3 时可以提额
 * @author huiyi
 */
public record QuotaSnapshot(int tier, int usedTokens, int limitTokens, int remainingTokens, int remainingPercent,
                            boolean boostAllowed) {
}
