package com.huiyi.assistant.api.v1;

import com.huiyi.assistant.common.result.Result;
import com.huiyi.assistant.model.dto.BoostDecisionRequest;
import com.huiyi.assistant.model.dto.BoostSubmitRequest;
import com.huiyi.assistant.quota.BoostReview;
import com.huiyi.assistant.quota.QuotaSnapshot;
import com.huiyi.assistant.quota.SentenceUsage;
import com.huiyi.assistant.quota.TokenBoostService;
import com.huiyi.assistant.quota.TokenQuotaService;
import com.huiyi.assistant.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 本人额度、提额申请和审评。通过时不调用问答。
 *
 * @author huiyi
 */
@RestController
@RequestMapping("/api/v1")
public class QuotaController {

    private final TokenQuotaService quota;
    private final TokenBoostService boosts;

    public QuotaController(TokenQuotaService quota, TokenBoostService boosts) {
        this.quota = quota;
        this.boosts = boosts;
    }

    /** 当前登录用户的上海日额度。 */
    @GetMapping("/me/quota")
    public Result<QuotaSnapshot> mine(@AuthenticationPrincipal AuthUser user) {
        return Result.ok(quota.snapshot(user.userId()));
    }

    /** 申请人今天已记下 Token 的句子，不含步骤。 */
    @GetMapping("/me/quota/sentences")
    public Result<List<SentenceUsage>> sentences(@AuthenticationPrincipal AuthUser user) {
        return Result.ok(boosts.sentences(user.userId()));
    }

    /** 按运行编号提交提额。理由由服务端重算。 */
    @PostMapping("/me/quota/boosts")
    public Result<Void> submit(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody BoostSubmitRequest request) {
        boosts.submit(user.userId(), request.getRunId());
        return Result.ok();
    }

    /** 当前用户可以审的待审申请。 */
    @GetMapping("/quota/boosts")
    public Result<List<BoostReview>> pending(@AuthenticationPrincipal AuthUser user) {
        return Result.ok(boosts.pendingReviews(user.userId()));
    }

    /** 通过或拒绝。通过不触发问答。 */
    @PostMapping("/quota/boosts/{id}/decision")
    public Result<Void> decide(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
                               @Valid @RequestBody BoostDecisionRequest request) {
        boosts.decide(user.userId(), id, request.getDecision());
        return Result.ok();
    }
}
