package com.huiyi.assistant.quota;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.mapper.LlmCallMapper;
import com.huiyi.assistant.mapper.TokenBoostRequestMapper;
import com.huiyi.assistant.mapper.UserMapper;
import com.huiyi.assistant.model.entity.TokenBoostRequest;
import com.huiyi.assistant.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 今日额度：上海自然日窗口、等级上限、已通过提额、本地预估和开跑前拦截。
 *
 * @author huiyi
 */
@ExtendWith(MockitoExtension.class)
class TokenQuotaServiceTest {

    private static final LocalDate QUOTA_DATE = LocalDate.of(2026, 11, 9);
    private static final LocalDateTime DAY_START = QUOTA_DATE.atStartOfDay();
    private static final LocalDateTime DAY_END = QUOTA_DATE.plusDays(1).atStartOfDay();
    private static final String BOOST_MESSAGE = "今日 Token 额度不足，请提额后再问。";
    private static final String TIER_THREE_MESSAGE = "今日 Token 额度不足。";

    @Mock UserMapper users;
    @Mock LlmCallMapper calls;
    @Mock TokenBoostRequestMapper boosts;

    TokenQuotaService service;

    @BeforeEach
    void setUp() {
        // 同一瞬间是 2026-11-09T00:30+08:00。钟的时区用 UTC，窗口必须仍按上海自然日。
        Clock clock = Clock.fixed(
                OffsetDateTime.parse("2026-11-09T00:30+08:00").toInstant(),
                ZoneOffset.UTC);
        service = new TokenQuotaService(users, calls, boosts, clock);
    }

    @Test
    void tierOneWithinQuotaReportsRemainingPercentAndAllowsBoost() {
        stubQuota(11L, 1, 400_000, 0L);

        QuotaSnapshot snapshot = service.snapshot(11L);

        assertEquals(1, snapshot.tier());
        assertEquals(400_000, snapshot.usedTokens());
        assertEquals(500_000, snapshot.limitTokens());
        assertEquals(100_000, snapshot.remainingTokens());
        assertEquals(20, snapshot.remainingPercent());
        assertTrue(snapshot.boostAllowed());
        verify(calls).sumSuccessTokens(11L, DAY_START, DAY_END);
        assertApprovedBoostQuery(captureBoostQueries(1).get(0), 11L);
    }

    @Test
    void overQuotaKeepsNegativeRemainingAndZeroPercent() {
        stubQuota(11L, 1, 600_000, 0L);

        QuotaSnapshot snapshot = service.snapshot(11L);

        assertEquals(500_000, snapshot.limitTokens());
        assertEquals(-100_000, snapshot.remainingTokens());
        assertEquals(0, snapshot.remainingPercent());
        assertTrue(snapshot.boostAllowed());
    }

    @Test
    void tierTwoLimitIsOneMillionFiveHundredThousand() {
        stubQuota(12L, 2, 0, 0L);

        QuotaSnapshot snapshot = service.snapshot(12L);

        assertEquals(2, snapshot.tier());
        assertEquals(1_500_000, snapshot.limitTokens());
        assertEquals(1_500_000, snapshot.remainingTokens());
        assertEquals(100, snapshot.remainingPercent());
        assertTrue(snapshot.boostAllowed());
    }

    @Test
    void tierThreeDisallowsBoostAndUsesFiveMillionLimit() {
        stubQuota(13L, 3, 0, 0L);

        QuotaSnapshot snapshot = service.snapshot(13L);

        assertEquals(3, snapshot.tier());
        assertEquals(5_000_000, snapshot.limitTokens());
        assertEquals(5_000_000, snapshot.remainingTokens());
        assertEquals(100, snapshot.remainingPercent());
        assertFalse(snapshot.boostAllowed());
        verify(calls).sumSuccessTokens(13L, DAY_START, DAY_END);
    }

    @Test
    void approvedBoostRaisesTierOneLimitToOneMillion() {
        stubQuota(11L, 1, 400_000, 1L);

        QuotaSnapshot snapshot = service.snapshot(11L);

        assertEquals(1_000_000, snapshot.limitTokens());
        assertEquals(600_000, snapshot.remainingTokens());
        assertEquals(60, snapshot.remainingPercent());
        assertTrue(snapshot.boostAllowed());
        assertApprovedBoostQuery(captureBoostQueries(1).get(0), 11L);
    }

    @Test
    void halfPercentRoundsAwayFromZero() {
        stubQuota(11L, 1, 497_500, 0L);

        assertEquals(1, service.snapshot(11L).remainingPercent());
    }

    @Test
    void estimateSumsQuestionAndRecentCounts() {
        assertEquals(LocalTokenCounter.count("订单") + LocalTokenCounter.count("abcd"),
                service.estimate("订单", List.of("abcd")));
        verifyNoInteractions(users, calls, boosts);
    }

    @Test
    void estimateCountsEachTextOnItsOwn() {
        assertEquals(2, service.estimate("ab", List.of("cd")));
    }

    @Test
    void assertCanStartThrows4292WhenTierOneEstimateExceedsRemaining() {
        stubQuota(11L, 1, 499_999, 0L);

        BizException ex = assertThrows(BizException.class,
                () -> service.assertCanStart(11L, "a", List.of("bb")));

        assertEquals(4292, ex.getCode());
        assertEquals(BOOST_MESSAGE, ex.getMessage());
    }

    @Test
    void assertCanStartThrows4292WhenTierTwoEstimateExceedsRemaining() {
        stubQuota(12L, 2, 1_499_999, 0L);

        BizException ex = assertThrows(BizException.class,
                () -> service.assertCanStart(12L, "a", List.of("bb")));

        assertEquals(4292, ex.getCode());
        assertEquals(BOOST_MESSAGE, ex.getMessage());
    }

    @Test
    void assertCanStartThrows4293WhenTierThreeEstimateExceedsRemaining() {
        stubQuota(13L, 3, 4_999_999, 0L);

        BizException ex = assertThrows(BizException.class,
                () -> service.assertCanStart(13L, "a", List.of("bb")));

        assertEquals(4293, ex.getCode());
        assertEquals(TIER_THREE_MESSAGE, ex.getMessage());
    }

    @Test
    void assertCanStartAllowsWhenRemainingEqualsEstimate() {
        stubQuota(11L, 1, 499_999, 0L);

        assertEquals(1, service.snapshot(11L).remainingTokens());
        assertDoesNotThrow(() -> service.assertCanStart(11L, "abcd", List.of()));
    }

    @Test
    void anotherUsersUsageDoesNotChangeRemaining() {
        stubUser(11L, 1);
        stubUser(22L, 1);
        when(calls.sumSuccessTokens(11L, DAY_START, DAY_END)).thenReturn(400_000);
        when(calls.sumSuccessTokens(22L, DAY_START, DAY_END)).thenReturn(600_000);
        when(boosts.selectCount(any())).thenReturn(0L);

        QuotaSnapshot other = service.snapshot(22L);
        QuotaSnapshot mine = service.snapshot(11L);

        assertEquals(600_000, other.usedTokens());
        assertEquals(-100_000, other.remainingTokens());
        assertEquals(400_000, mine.usedTokens());
        assertEquals(100_000, mine.remainingTokens());
        assertEquals(20, mine.remainingPercent());
        verify(calls).sumSuccessTokens(11L, DAY_START, DAY_END);
        verify(calls).sumSuccessTokens(22L, DAY_START, DAY_END);
        List<Wrapper<TokenBoostRequest>> queries = captureBoostQueries(2);
        assertApprovedBoostQuery(queries.get(0), 22L);
        assertApprovedBoostQuery(queries.get(1), 11L);
    }

    @Test
    void unknownTierIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> UserTierLimits.dailyLimit(0));
        assertThrows(IllegalArgumentException.class, () -> UserTierLimits.dailyLimit(4));
        stubUser(14L, 4);

        assertThrows(IllegalArgumentException.class, () -> service.snapshot(14L));
    }

    private void stubQuota(long userId, int tier, int used, long approvedRows) {
        stubUser(userId, tier);
        when(calls.sumSuccessTokens(userId, DAY_START, DAY_END)).thenReturn(used);
        when(boosts.selectCount(any())).thenReturn(approvedRows);
    }

    private void stubUser(long userId, int tier) {
        User user = new User();
        user.setId(userId);
        user.setUserTier(tier);
        when(users.selectById(userId)).thenReturn(user);
    }

    @SuppressWarnings("unchecked")
    private List<Wrapper<TokenBoostRequest>> captureBoostQueries(int times) {
        ArgumentCaptor<Wrapper<TokenBoostRequest>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(boosts, times(times)).selectCount(captor.capture());
        return captor.getAllValues();
    }

    @SuppressWarnings("rawtypes")
    private static void assertApprovedBoostQuery(Wrapper<TokenBoostRequest> wrapper, long userId) {
        String sql = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("applicant_user_id"), sql);
        assertTrue(sql.contains("status"), sql);
        assertTrue(sql.contains("quota_date"), sql);
        assertFalse(sql.contains("workspace"), sql);
        Map<String, Object> params = ((AbstractWrapper) wrapper).getParamNameValuePairs();
        assertTrue(params.containsValue(userId), params.toString());
        assertTrue(params.containsValue("APPROVED"), params.toString());
        assertTrue(params.containsValue(QUOTA_DATE), params.toString());
    }
}
