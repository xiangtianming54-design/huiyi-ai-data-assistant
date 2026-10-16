package com.huiyi.assistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步执行配置：复合问题子任务并行执行池。
 *
 * @author huiyi
 */
@Configuration
public class AsyncConfig {

    /**
     * 复合问题并行执行池（固定 4 线程，覆盖 2 路 LLM 阻塞调用）。
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService chatExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
