package com.huiyi.assistant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class KnowledgeParseExecutorConfig {

    @Bean(name = "knowledgeParseExecutor")
    public TaskExecutor knowledgeParseExecutor(
            @Value("${huiyi.knowledge.parse-pool-size:2}") int poolSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("doc-parse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
