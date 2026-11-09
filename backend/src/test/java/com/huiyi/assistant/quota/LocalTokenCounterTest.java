package com.huiyi.assistant.quota;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * LocalTokenCounter 单元测试：空串为 0，ASCII 按 4 字一组，每个非 ASCII 码位为 1。
 *
 * @author huiyi
 */
class LocalTokenCounterTest {

    @Test
    void blankIsZero() {
        assertEquals(0, LocalTokenCounter.count(null));
        assertEquals(0, LocalTokenCounter.count(""));
    }

    @Test
    void asciiGroupsByFourAndCjkIsOneEach() {
        assertEquals(1, LocalTokenCounter.count("ab"));
        assertEquals(2, LocalTokenCounter.count("abcdefg"));
        assertEquals(2, LocalTokenCounter.count("订单"));
        assertEquals(LocalTokenCounter.count("订单 abc"), LocalTokenCounter.count("订单 abc"));
    }
}
