package com.huiyi.assistant.controller;

import com.huiyi.assistant.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class HealthController {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final WebClient pythonServiceWebClient;
    private final String pythonBaseUrl;

    public HealthController(DataSource dataSource,
                            RedisConnectionFactory redisConnectionFactory,
                            @Qualifier("pythonServiceWebClient") WebClient pythonServiceWebClient,
                            @Value("${python-service.url}") String pythonBaseUrl) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.pythonServiceWebClient = pythonServiceWebClient;
        this.pythonBaseUrl = pythonBaseUrl;
    }

    @GetMapping("/health")
    public Result<Map<String, String>> health() {
        Map<String, String> dependencies = new LinkedHashMap<>();
        dependencies.put("mysql", probeMysql());
        dependencies.put("redis", probeRedis());
        dependencies.put("python", probePython());
        return Result.ok(dependencies);
    }

    private String probeMysql() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            return resultSet.next() ? "up" : "down";
        } catch (Exception e) {
            log.warn("health mysql down: {}", e.getMessage());
            return "down";
        }
    }

    private String probeRedis() {
        try (var connection = redisConnectionFactory.getConnection()) {
            connection.ping();
            return "up";
        } catch (RuntimeException e) {
            log.warn("health redis down: {}", e.getMessage());
            return "down";
        }
    }

    private String probePython() {
        try {
            pythonServiceWebClient.get()
                    .uri(pythonBaseUrl.endsWith("/") ? "/health" : "/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(2));
            return "up";
        } catch (RuntimeException e) {
            log.warn("health python down: {}", e.getMessage());
            return "down";
        }
    }
}
