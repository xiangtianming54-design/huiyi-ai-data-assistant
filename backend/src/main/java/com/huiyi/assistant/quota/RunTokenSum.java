package com.huiyi.assistant.quota;

/**
 * 一次运行下 SUCCESS 调用的 prompt 与 completion 合计。没有行时两个都是 0。
 */
public record RunTokenSum(int promptTokens, int completionTokens) {
}
