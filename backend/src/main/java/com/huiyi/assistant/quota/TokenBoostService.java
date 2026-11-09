package com.huiyi.assistant.quota;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.mapper.AssistantRunMapper;
import com.huiyi.assistant.mapper.LlmCallMapper;
import com.huiyi.assistant.mapper.TokenBoostRequestMapper;
import com.huiyi.assistant.mapper.UserMapper;
import com.huiyi.assistant.model.entity.AssistantRun;
import com.huiyi.assistant.model.entity.LlmCall;
import com.huiyi.assistant.model.entity.TokenBoostRequest;
import com.huiyi.assistant.model.entity.User;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 当日提额的申请、句子列表和审评。通过只改申请状态，不重新开跑。
 *
 * @author huiyi
 */
@Service
public class TokenBoostService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int STEP_NAME_LIMIT = 64;

    private final UserMapper users;
    private final AssistantRunMapper runs;
    private final LlmCallMapper calls;
    private final TokenBoostRequestMapper boosts;
    private final Clock clock;

    public TokenBoostService(UserMapper users, AssistantRunMapper runs, LlmCallMapper calls,
                             TokenBoostRequestMapper boosts, Clock clock) {
        this.users = users;
        this.runs = runs;
        this.calls = calls;
        this.boosts = boosts;
        this.clock = clock;
    }

    /**
     * 按上海日当天的运行重算理由和最重步骤。不读取客户端传来的 Token 数。
     *
     * @param applicantId 申请人
     * @param runId       选中的运行编号
     */
    public void submit(long applicantId, String runId) {
        User applicant = requireUser(applicantId);
        if (applicant.getUserTier() == 3) {
            throw new BizException(400, "第三级不能提额");
        }
        if (hasApprovedToday(applicantId)) {
            throw new BizException(400, "今日提额已通过");
        }
        AssistantRun run = runsOn(applicantId).stream()
                .filter(row -> runId != null && runId.equals(row.getRunId()))
                .findFirst()
                .orElseThrow(() -> new BizException(400, "这一句不在今日用量里"));
        LlmCall step = heaviestStep(run.getRunId());
        TokenBoostRequest request = new TokenBoostRequest();
        request.setApplicantUserId(applicantId);
        request.setStatus("PENDING");
        request.setReason(run.getQuestion() + "（" + run.getTotalTokens() + "）");
        request.setStepName(stepName(step));
        request.setStepTokens(step == null ? 0 : step.getTotalTokens());
        request.setQuotaDate(today());
        request.setCreatedAt(now());
        boosts.insert(request);
    }

    /**
     * 第一级只由用户名 {@code limit} 的第二级审。第二级由任意第三级审。
     *
     * @param reviewerId 审评人
     * @param requestId  申请
     * @param decision   {@code APPROVED} 或 {@code REJECTED}
     */
    public void decide(long reviewerId, long requestId, String decision) {
        TokenBoostRequest request = boosts.selectById(requestId);
        if (request == null) {
            throw new BizException(404, "提额申请不存在");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new BizException(400, "该申请已审评");
        }
        User reviewer = requireUser(reviewerId);
        User applicant = requireUser(request.getApplicantUserId());
        if (!canDecide(reviewer, applicant)) {
            throw new BizException(403, "没有权限执行该操作");
        }
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new BizException(400, "无效的审评结果");
        }
        request.setStatus(decision);
        request.setReviewerUserId(reviewerId);
        request.setReviewedAt(now());
        boosts.updateById(request);
    }

    /**
     * 申请人上海日当天 {@code total_tokens > 0} 的运行。最多的一句 {@code heaviest} 为 true。
     *
     * @param userId 用户
     * @return 句子列表
     */
    public List<SentenceUsage> sentences(long userId) {
        List<AssistantRun> rows = runsOn(userId);
        int max = 0;
        for (AssistantRun row : rows) {
            max = Math.max(max, row.getTotalTokens());
        }
        List<SentenceUsage> usages = new ArrayList<>();
        for (AssistantRun row : rows) {
            usages.add(new SentenceUsage(row.getRunId(), row.getQuestion(), row.getTotalTokens(),
                    row.getTotalTokens() == max));
        }
        return usages;
    }

    /**
     * 调用者能审的待审申请。{@code limit} 看见第一级，第三级看见第二级，其他人没有。
     *
     * @param reviewerId 审评人
     * @return 待审列表
     */
    public List<BoostReview> pendingReviews(long reviewerId) {
        User reviewer = requireUser(reviewerId);
        Integer applicantTier = applicantTierFor(reviewer);
        if (applicantTier == null) {
            return List.of();
        }
        List<TokenBoostRequest> pending = boosts.selectList(
                new QueryWrapper<TokenBoostRequest>().eq("status", "PENDING"));
        List<BoostReview> reviews = new ArrayList<>();
        for (TokenBoostRequest request : pending) {
            User applicant = users.selectById(request.getApplicantUserId());
            if (applicant == null || applicant.getUserTier() == null
                    || !applicantTier.equals(applicant.getUserTier())) {
                continue;
            }
            reviews.add(new BoostReview(request.getId(), applicant.getUsername(), request.getReason(),
                    request.getStepName(), request.getStepTokens()));
        }
        return reviews;
    }

    private boolean hasApprovedToday(long applicantId) {
        Long count = boosts.selectCount(new QueryWrapper<TokenBoostRequest>()
                .eq("applicant_user_id", applicantId)
                .eq("status", "APPROVED")
                .eq("quota_date", today()));
        return count != null && count > 0;
    }

    private boolean canDecide(User reviewer, User applicant) {
        if (applicant.getUserTier() == 1) {
            return reviewer.getUserTier() == 2 && "limit".equals(reviewer.getUsername());
        }
        if (applicant.getUserTier() == 2) {
            return reviewer.getUserTier() == 3;
        }
        return false;
    }

    /** 用户名 limit 的第二级审第一级；第三级审第二级。 */
    private Integer applicantTierFor(User reviewer) {
        if (reviewer.getUserTier() == 2 && "limit".equals(reviewer.getUsername())) {
            return 1;
        }
        if (reviewer.getUserTier() == 3) {
            return 2;
        }
        return null;
    }

    private List<AssistantRun> runsOn(long userId) {
        LocalDate quotaDate = today();
        LocalDateTime start = quotaDate.atStartOfDay();
        LocalDateTime end = quotaDate.plusDays(1).atStartOfDay();
        return runs.selectList(new QueryWrapper<AssistantRun>()
                .eq("user_id", userId)
                .gt("total_tokens", 0)
                .ge("created_at", start)
                .lt("created_at", end));
    }

    /** {@code total_tokens > 0} 里最大的一条。不按状态过滤。 */
    private LlmCall heaviestStep(String runId) {
        LlmCall heaviest = null;
        for (LlmCall call : calls.selectList(new QueryWrapper<LlmCall>().eq("run_id", runId))) {
            if (call.getTotalTokens() == null || call.getTotalTokens() <= 0) {
                continue;
            }
            if (heaviest == null || call.getTotalTokens() > heaviest.getTotalTokens()) {
                heaviest = call;
            }
        }
        return heaviest;
    }

    private static String stepName(LlmCall step) {
        if (step == null || step.getPurpose() == null) {
            return "";
        }
        String purpose = step.getPurpose();
        return purpose.length() <= STEP_NAME_LIMIT ? purpose : purpose.substring(0, STEP_NAME_LIMIT);
    }

    private User requireUser(long userId) {
        User user = users.selectById(userId);
        if (user == null || user.getUserTier() == null) {
            throw new BizException(404, "用户不存在");
        }
        return user;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(SHANGHAI));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(SHANGHAI));
    }
}
