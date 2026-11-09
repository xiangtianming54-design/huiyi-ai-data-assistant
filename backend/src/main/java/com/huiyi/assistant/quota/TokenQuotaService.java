package com.huiyi.assistant.quota;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.mapper.LlmCallMapper;
import com.huiyi.assistant.mapper.TokenBoostRequestMapper;
import com.huiyi.assistant.mapper.UserMapper;
import com.huiyi.assistant.model.entity.TokenBoostRequest;
import com.huiyi.assistant.model.entity.User;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 按人、按上海自然日计算今日 Token 额度。预估只做本地计数，不调用模型。
 *
 * @author huiyi
 */
public class TokenQuotaService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final UserMapper users;
    private final LlmCallMapper calls;
    private final TokenBoostRequestMapper boosts;
    private final Clock clock;

    public TokenQuotaService(UserMapper users, LlmCallMapper calls, TokenBoostRequestMapper boosts, Clock clock) {
        this.users = users;
        this.calls = calls;
        this.boosts = boosts;
        this.clock = clock;
    }

    /**
     * 当前上海日的额度。已用只计该用户 {@code SUCCESS} 调用，不按工作区过滤。
     *
     * @param userId 用户
     * @return 额度快照
     */
    public QuotaSnapshot snapshot(long userId) {
        User user = users.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        int tier = user.getUserTier();
        int limit = UserTierLimits.dailyLimit(tier);
        LocalDate quotaDate = LocalDate.now(clock.withZone(SHANGHAI));
        LocalDateTime start = quotaDate.atStartOfDay();
        LocalDateTime end = quotaDate.plusDays(1).atStartOfDay();
        int used = calls.sumSuccessTokens(userId, start, end);
        if (hasApprovedBoost(userId, quotaDate)) {
            limit += UserTierLimits.BOOST_TOKENS;
        }
        int remaining = limit - used;
        int remainingPercent = remaining < 0 ? 0 : (int) Math.round(remaining * 100.0 / limit);
        return new QuotaSnapshot(tier, used, limit, remaining, remainingPercent, tier != 3);
    }

    /**
     * 问题和每段近期原文各自本地计数后相加。
     *
     * @param question    这一句原文
     * @param recentTexts 近期原文
     * @return 预估 Token
     */
    public int estimate(String question, List<String> recentTexts) {
        int total = LocalTokenCounter.count(question);
        for (String text : recentTexts) {
            total += LocalTokenCounter.count(text);
        }
        return total;
    }

    /**
     * 剩余小于预估时不允许开跑。等级 1 和 2 为 4292，等级 3 为 4293。
     *
     * @param userId      用户
     * @param question    这一句原文
     * @param recentTexts 近期原文
     */
    public void assertCanStart(long userId, String question, List<String> recentTexts) {
        QuotaSnapshot snapshot = snapshot(userId);
        if (snapshot.remainingTokens() < estimate(question, recentTexts)) {
            if (snapshot.tier() == 3) {
                throw new BizException(4293, "今日 Token 额度不足。");
            }
            throw new BizException(4292, "今日 Token 额度不足，请提额后再问。");
        }
    }

    /** 该申请人在这个上海日有一条已通过的提额。 */
    private boolean hasApprovedBoost(long userId, LocalDate quotaDate) {
        Long count = boosts.selectCount(new QueryWrapper<TokenBoostRequest>()
                .eq("applicant_user_id", userId)
                .eq("status", "APPROVED")
                .eq("quota_date", quotaDate));
        return count != null && count > 0;
    }
}
