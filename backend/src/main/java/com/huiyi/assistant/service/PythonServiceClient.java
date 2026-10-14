package com.huiyi.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.common.result.ResultCode;
import com.huiyi.assistant.model.dto.ParseResultDTO;
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

    private final WebClient pythonServiceWebClient;

    public PythonServiceClient(WebClient pythonServiceWebClient) {
        this.pythonServiceWebClient = pythonServiceWebClient;
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

        try {
            return pythonServiceWebClient.post()
                    .uri("/api/documents/parse")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(parts))
                    .retrieve()
                    .bodyToMono(ParseResultDTO.class)
                    .block(TIMEOUT);
        } catch (WebClientResponseException e) {
            // Python 已响应但返回错误状态：4xx 透传 detail 提示，5xx 视为服务异常
            log.warn("Python 文档解析接口返回错误: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().is4xxClientError()) {
                throw new BizException(ResultCode.PARAM_ERROR.getCode(), extractDetail(e.getResponseBodyAsString()));
            }
            throw new BizException(ResultCode.PYTHON_SERVICE_UNAVAILABLE);
        } catch (WebClientException e) {
            log.error("调用 Python 文档解析接口失败: {}", e.getMessage());
            throw new BizException(ResultCode.PYTHON_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 从 Python 错误响应体中提取 detail 字段。
     */
    private String extractDetail(String responseBody) {
        try {
            String detail = new ObjectMapper().readTree(responseBody).path("detail").asText();
            return detail.isEmpty() ? "文档解析请求被拒绝" : detail;
        } catch (Exception e) {
            return "文档解析请求被拒绝";
        }
    }
}
