package com.huiyi.assistant.service;

import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.config.WebClientConfig;
import com.huiyi.assistant.model.dto.ParseResultDTO;
import com.huiyi.assistant.model.dto.SqlValidateResponseDTO;
import com.huiyi.assistant.model.dto.VectorSearchResponseDTO;
import com.huiyi.assistant.observability.ObservabilityContextHolder;
import com.huiyi.assistant.observability.ObservabilityMetrics;
import com.huiyi.assistant.observability.ObservabilityProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PythonServiceClient 测试：MockWebServer 打桩 Python Sidecar。
 *
 * @author huiyi
 */
class PythonServiceClientTest {

    private MockWebServer mockWebServer;

    private PythonServiceClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setMetricsEnabled(false);
        client = new PythonServiceClient(
                WebClient.builder().baseUrl(mockWebServer.url("/").toString())
                        .filter(WebClientConfig.observabilityHeaders())
                        .build(),
                new ObservabilityMetrics(properties, new SimpleMeterRegistry()));
    }

    @AfterEach
    void tearDown() throws IOException {
        ObservabilityContextHolder.clear();
        mockWebServer.shutdown();
    }

    @Test
    void searchVectorsSendsObservabilityHeaders() throws InterruptedException {
        ObservabilityContextHolder.setRequestId("req_abc123abc123abc123abc123abc123ab");
        ObservabilityContextHolder.setRunId("run_abc123abc123abc123abc123abc123ab");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"success\":true,\"hits\":[]}"));

        client.searchVectors("什么是有效订单？", 5);

        RecordedRequest recorded = mockWebServer.takeRequest();
        assertEquals("req_abc123abc123abc123abc123abc123ab", recorded.getHeader("X-Request-Id"));
        assertEquals("run_abc123abc123abc123abc123abc123ab", recorded.getHeader("X-Run-Id"));
    }

    @Test
    void parseDocumentSuccess() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""
                        {"filename":"a.md","chunk_count":1,"chunks":[{"id":"a-md-chunk-0000","text":"内容",
                        "metadata":{"filename":"a.md","chunk_index":0,"source_type":"md"}}]}
                        """));

        MockMultipartFile file = new MockMultipartFile("file", "a.md", "text/markdown", "内容".getBytes());
        ParseResultDTO result = client.parseDocument(file);

        assertNotNull(result);
        assertEquals("a.md", result.getFilename());
        assertEquals(1, result.getChunkCount());
        assertEquals("a-md-chunk-0000", result.getChunks().get(0).getId());
    }

    @Test
    void parseDocument4xxForwardsDetail() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"detail\":\"不支持的文件类型: .xlsx\"}"));

        MockMultipartFile file = new MockMultipartFile("file", "a.xlsx", "application/octet-stream", "x".getBytes());
        BizException exception = assertThrows(BizException.class, () -> client.parseDocument(file));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("不支持的文件类型"));
    }

    @Test
    void parseDocument5xxReportsUnavailable() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(502).setBody("{\"detail\":\"bad upstream\"}"));

        MockMultipartFile file = new MockMultipartFile("file", "a.md", "text/markdown", "内容".getBytes());
        BizException exception = assertThrows(BizException.class, () -> client.parseDocument(file));

        assertEquals(1001, exception.getCode());
    }

    @Test
    void searchVectorsSuccess() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""
                        {"success":true,"hits":[{"id":"c1","text":"有效订单定义",
                        "metadata":{"filename":"business-glossary.md"},"score":0.74}]}
                        """));

        VectorSearchResponseDTO result = client.searchVectors("什么是有效订单？", 5);

        assertNotNull(result);
        assertTrue(result.getSuccess());
        assertEquals(1, result.getHits().size());
        assertEquals(0.74, result.getHits().get(0).getScore(), 0.001);
    }

    @Test
    void validateSqlRejected() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"valid\":false,\"reason\":\"仅允许 SELECT 查询\"}"));

        SqlValidateResponseDTO result = client.validateSql("DELETE FROM sales_orders");

        assertNotNull(result);
        assertEquals(Boolean.FALSE, result.getValid());
        assertTrue(result.getReason().contains("SELECT"));
    }

    @Test
    void similaritySuccess() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"success\":true,\"score\":0.91}"));

        double score = client.similarity("什么是有效订单？", "有效订单的定义是什么？");

        assertEquals(0.91, score, 0.001);
    }
}
