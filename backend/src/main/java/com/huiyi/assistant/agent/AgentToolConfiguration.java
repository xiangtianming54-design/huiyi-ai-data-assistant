package com.huiyi.assistant.agent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AgentToolConfiguration {

    @Bean
    public ToolRegistry toolRegistry(List<AgentTool> tools) {
        ToolRegistry registry = new ToolRegistry();
        for (AgentTool tool : tools) {
            registry.register(tool);
        }
        return registry;
    }

    @Bean
    public ToolExecutor toolExecutor(ToolRegistry registry) {
        return new ToolExecutor(registry);
    }
}
