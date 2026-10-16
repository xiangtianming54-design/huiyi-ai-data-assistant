package com.huiyi.assistant.service;

import java.util.OptionalDouble;

/**
 * 文本相似度（cosine，[0, 1]）。不可用时返回 empty，由调用方降级。
 *
 * @author huiyi
 */
@FunctionalInterface
public interface TextSimilarity {

    OptionalDouble score(String left, String right);
}
