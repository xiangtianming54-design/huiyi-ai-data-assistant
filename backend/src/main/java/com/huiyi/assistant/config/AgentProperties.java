package com.huiyi.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "huiyi.agent")
public class AgentProperties {

    private long runTimeoutMs = 90_000L;
    private int promptMaxChars = 24_000;
}
