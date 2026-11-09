package com.huiyi.assistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 额度与准入注入用的时钟。单测自行构造时钟和假依赖。
 *
 * @author huiyi
 */
@Configuration
public class QuotaConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
