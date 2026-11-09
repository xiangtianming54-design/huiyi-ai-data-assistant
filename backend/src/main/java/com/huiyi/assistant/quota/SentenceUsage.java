package com.huiyi.assistant.quota;

/**
 * 申请人当天能看见的一句。不含步骤名和步骤 Token。
 *
 * @param runId       运行编号
 * @param question    这一句原文
 * @param totalTokens 这一句记上的 Token
 * @param heaviest    当天 Token 最多的一句
 * @author huiyi
 */
public record SentenceUsage(String runId, String question, int totalTokens, boolean heaviest) {
}
