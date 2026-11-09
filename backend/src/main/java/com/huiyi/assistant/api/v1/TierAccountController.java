package com.huiyi.assistant.api.v1;

import com.huiyi.assistant.common.result.Result;
import com.huiyi.assistant.model.dto.CreateTierAccountRequest;
import com.huiyi.assistant.model.vo.CreatedUserVO;
import com.huiyi.assistant.quota.CreateTierAccountService;
import com.huiyi.assistant.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第三级新建账号。不登录新用户。
 *
 * @author huiyi
 */
@RestController
@RequestMapping("/api/v1/workspaces")
public class TierAccountController {

    private final CreateTierAccountService accounts;

    public TierAccountController(CreateTierAccountService accounts) {
        this.accounts = accounts;
    }

    /** 在路径上的工作区创建第二级或第三级账号。 */
    @PostMapping("/{workspaceId}/users")
    public Result<CreatedUserVO> create(@AuthenticationPrincipal AuthUser user, @PathVariable long workspaceId,
                                         @Valid @RequestBody CreateTierAccountRequest request) {
        return Result.ok(accounts.create(user, workspaceId, request));
    }
}
