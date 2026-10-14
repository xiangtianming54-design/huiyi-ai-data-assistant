package com.huiyi.assistant.controller;

import com.huiyi.assistant.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口。
 *
 * @author huiyi
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * 健康检查。
     *
     * @return {@code {"code":0,"message":"ok"}}
     */
    @GetMapping("/health")
    public Result<Void> health() {
        return Result.ok();
    }
}
