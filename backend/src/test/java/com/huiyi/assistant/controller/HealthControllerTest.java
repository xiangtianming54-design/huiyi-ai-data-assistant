package com.huiyi.assistant.controller;

import com.huiyi.assistant.common.result.Result;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.reactive.function.client.WebClient;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void doesNotRequireNamedJdbcTemplateBean() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/huiyi/assistant/controller/HealthController.java"),
                StandardCharsets.UTF_8);
        assertFalse(source.contains("@Qualifier(\"jdbcTemplate\")"),
                "No jdbcTemplate bean exists; health must probe the @Primary DataSource");
        assertTrue(source.contains("DataSource"),
                "Health mysql probe must use the primary DataSource");
    }

    @Test
    void healthReturnsDependencyMapWithCodeZero() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);

        RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"));
        String pythonBase = mockWebServer.url("/").toString();
        WebClient webClient = WebClient.builder().baseUrl(pythonBase).build();

        HealthController controller = new HealthController(
                dataSource, redisConnectionFactory, webClient, pythonBase);

        Result<Map<String, String>> result = controller.health();

        assertEquals(0, result.getCode());
        assertEquals("ok", result.getMessage());
        assertEquals("up", result.getData().get("mysql"));
        assertEquals("up", result.getData().get("redis"));
        assertEquals("up", result.getData().get("python"));
    }
}
