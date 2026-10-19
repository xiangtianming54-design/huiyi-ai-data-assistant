package com.huiyi.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.common.result.ResultCode;
import com.huiyi.assistant.model.dto.DataProfileResponseDTO;
import com.huiyi.assistant.model.dto.ParseChunkDTO;
import com.huiyi.assistant.model.dto.ParseResultDTO;
import com.huiyi.assistant.model.dto.SimilarityResponseDTO;
import com.huiyi.assistant.model.dto.SqlValidateResponseDTO;
import com.huiyi.assistant.model.dto.VectorSearchRequestDTO;
import com.huiyi.assistant.model.dto.VectorSearchResponseDTO;
import com.huiyi.assistant.model.dto.VectorUpsertRequestDTO;
import com.huiyi.assistant.observability.ObservabilityMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Python Sidecar 服务客户端：统一封装调用、超时与异常转换。
 *
 * @author huiyi
 */
@Slf4j
@Component
public class PythonServiceClient {

    /** 调用超时时间 */
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** 向量库 collection 名称（与 Python 端默认一致） */
    private static final String COLLECTION_NAME = "huiyi_knowledge";

    private final WebClient pythonServiceWebClient;
    private final ObservabilityMetrics observabilityMetrics;

    public PythonServiceClient(WebClient pythonServiceWebClient, ObservabilityMetrics observabilityMetrics) {
        this.pythonServiceWebClient = pythonServiceWebClient;
        this.observabilityMetrics = observabilityMetrics;
    }

    /**
     * 调用 /api/documents/parse 解析文档并分块。
     *
     * @param file 上传文件
     * @return 解析结果（文件名、分块数、分块列表）
     */
    public ParseResultDTO parseDocument(MultipartFile file) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            parts.add("file", resource);
        } catch (IOException e) {
            log.error("读取上传文件失败: {}", file.getOriginalFilename(), e);
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(), "读取上传文件失败");
        }

        return post("/api/documents/parse",
                spec -> spec.contentType(MediaType.MULTIPART_FORM_DATA).body(BodyInserters.fromMultipartData(parts)),
                ParseResultDTO.class);
    }

    /**
     * 调用 /api/vector/upsert 将分块写入向量库（幂等）。
     *
     * @param parseResult 解析结果（含分块列表）
     */
    public void upsertVectors(ParseResultDTO parseResult) {
        upsertVectors(parseResult, null, null);
    }

    public void upsertVectors(ParseResultDTO parseResult, Long workspaceId, Long docId) {
        upsertVectors(parseResult, workspaceId, docId, null);
    }

    public void upsertVectors(ParseResultDTO parseResult, Long workspaceId, Long docId, Long spaceId) {
        if (parseResult.getChunks() != null && workspaceId != null && docId != null) {
            String workspace = String.valueOf(workspaceId);
            String document = String.valueOf(docId);
            String space = spaceId == null ? null : String.valueOf(spaceId);
            parseResult.getChunks().forEach(chunk -> {
                if (chunk.getMetadata() == null) {
                    chunk.setMetadata(new ParseChunkDTO.Metadata());
                }
                chunk.getMetadata().setWorkspaceId(workspace);
                chunk.getMetadata().setDocId(document);
                if (space != null) {
                    chunk.getMetadata().setKnowledgeSpaceId(space);
                }
            });
        }
        VectorUpsertRequestDTO request = new VectorUpsertRequestDTO(COLLECTION_NAME, workspaceId, parseResult.getChunks());
        post("/api/vector/upsert", spec -> spec.contentType(MediaType.APPLICATION_JSON).bodyValue(request), String.class);
        log.info("向量写入完成: filename={}, chunks={}", parseResult.getFilename(), parseResult.getChunkCount());
    }

    /**
     * 调用 /api/vector/search 进行 Top-K 检索（默认知识库 collection）。
     */
    public VectorSearchResponseDTO searchVectors(String query, int topK) {
        return searchVectors(query, topK, COLLECTION_NAME, null);
    }

    public VectorSearchResponseDTO searchVectors(String query, int topK, Long workspaceId) {
        return searchVectors(query, topK, COLLECTION_NAME, workspaceId);
    }

    /**
     * 调用 /api/vector/search 进行 Top-K 检索（指定 collection）。
     *
     * @param query 查询文本
     * @param topK 返回条数
     * @param collectionName collection 名称
     * @return 检索响应（含命中列表）
     */
    public VectorSearchResponseDTO searchVectors(String query, int topK, String collectionName) {
        return searchVectors(query, topK, collectionName, null);
    }

    public VectorSearchResponseDTO searchVectors(String query, int topK, String collectionName, Long workspaceId) {
        return searchVectors(query, topK, collectionName, workspaceId, null);
    }

    public VectorSearchResponseDTO searchVectors(String query, int topK, Long workspaceId, List<Long> knowledgeSpaceIds) {
        return searchVectors(query, topK, COLLECTION_NAME, workspaceId, knowledgeSpaceIds);
    }

    public VectorSearchResponseDTO searchVectors(String query, int topK, String collectionName, Long workspaceId,
                                                 List<Long> knowledgeSpaceIds) {
        VectorSearchRequestDTO request = new VectorSearchRequestDTO(query, topK, collectionName, workspaceId,
                knowledgeSpaceIds);
        return post("/api/vector/search",
                spec -> spec.contentType(MediaType.APPLICATION_JSON).bodyValue(request),
                VectorSearchResponseDTO.class);
    }

    /**
     * 调用 /api/vector/upsert 写入通用分块（metadata 为自由 Map，供意图示例句等场景使用）。
     *
     * @param collectionName collection 名称
     * @param chunks 分块列表（每项含 id/text/metadata）
     */
    public void upsertChunks(String collectionName, List<Map<String, Object>> chunks) {
        Map<String, Object> body = Map.of("collection_name", collectionName, "chunks", chunks);
        post("/api/vector/upsert", spec -> spec.contentType(MediaType.APPLICATION_JSON).bodyValue(body), String.class);
    }

    /**
     * 调用 /api/vector/delete 按来源文件名删除全部分块向量。
     *
     * @param filename 来源文件名
     */
    public void deleteVectorsByFilename(String filename) {
        throw new BizException(ResultCode.PARAM_ERROR.getCode(), "禁止仅按文件名删除向量");
    }

    public void deleteVectorsByDoc(Long workspaceId, Long docId) {
        Map<String, Object> body = Map.of(
                "workspace_id", workspaceId,
                "doc_id", docId,
                "collection_name", COLLECTION_NAME);
        post("/api/vector/delete", spec -> spec.contentType(MediaType.APPLICATION_JSON).bodyValue(body), String.class);
        log.info("向量删除完成: workspaceId={}, docId={}", workspaceId, docId);
    }

    /**
     * 调用 /api/vector/similarity 计算两条文本的 embedding cosine 相似度。
     *
     * @param textA 文本 A
     * @param textB 文本 B
     * @return cosine 分数，范围 [0, 1]
     */
    public double similarity(String textA, String textB) {
        SimilarityResponseDTO response = post("/api/vector/similarity",
                spec -> spec.contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("text_a", textA, "text_b", textB)),
                SimilarityResponseDTO.class);
        if (response == null || response.getScore() == null) {
            throw new BizException(ResultCode.PYTHON_SERVICE_UNAVAILABLE);
        }
        return response.getScore();
    }

    /**
     * 调用 /api/sql/validate 进行 SQL 安全校验。
     *
     * @param sql 待校验 SQL
     * @return 校验结果（valid/sql/tables 或 reason）
     */
    public SqlValidateResponseDTO validateSql(String sql) {
        return post("/api/sql/validate",
                spec -> spec.contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("sql", sql)),
                SqlValidateResponseDTO.class);
    }

    /**
     * 调用 /api/data/profile 进行数据画像与图表推荐。
     *
     * @param rows 查询结果行集
     * @return 画像结果（row_count/columns/summary/chart_type/empty）
     */
    public DataProfileResponseDTO profileData(List<Map<String, Object>> rows) {
        return post("/api/data/profile",
                spec -> spec.contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("rows", rows)),
                DataProfileResponseDTO.class);
    }

    /**
     * 统一 POST 调用：超时控制 + 异常转换（4xx 透传 detail，5xx/网络异常报服务不可用）。
     */
    private <T> T post(String uri, java.util.function.Function<WebClient.RequestBodySpec, WebClient.RequestHeadersSpec<?>> requestBuilder,
                       Class<T> responseType) {
        long start = System.nanoTime();
        try {
            T result = requestBuilder.apply(pythonServiceWebClient.post().uri(uri))
                    .retrieve()
                    .bodyToMono(responseType)
                    .block(TIMEOUT);
            observabilityMetrics.python(true, false, false, Duration.ofNanos(System.nanoTime() - start));
            return result;
        } catch (WebClientResponseException e) {
            boolean parseFailed = uri.contains("/documents/parse");
            boolean embeddingFailed = uri.contains("/similarity") || uri.contains("/vector/");
            observabilityMetrics.python(false, parseFailed, embeddingFailed, Duration.ofNanos(System.nanoTime() - start));
            log.warn("Python 接口返回错误: uri={}, status={}, body={}", uri, e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().is4xxClientError()) {
                throw new BizException(ResultCode.PARAM_ERROR.getCode(), extractDetail(e.getResponseBodyAsString()));
            }
            throw new BizException(ResultCode.PYTHON_SERVICE_UNAVAILABLE);
        } catch (WebClientException e) {
            observabilityMetrics.python(false, false, false, Duration.ofNanos(System.nanoTime() - start));
            log.error("调用 Python 接口失败: uri={}, error={}", uri, e.getMessage());
            throw new BizException(ResultCode.PYTHON_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 从 Python 错误响应体中提取 detail 字段。
     */
    private String extractDetail(String responseBody) {
        try {
            String detail = new ObjectMapper().readTree(responseBody).path("detail").asText();
            return detail.isEmpty() ? "请求被数据处理服务拒绝" : detail;
        } catch (Exception e) {
            return "请求被数据处理服务拒绝";
        }
    }
}
