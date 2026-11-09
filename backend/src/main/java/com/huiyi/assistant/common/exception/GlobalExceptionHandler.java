package com.huiyi.assistant.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huiyi.assistant.common.result.Result;
import com.huiyi.assistant.common.result.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * 全局异常处理器，所有异常统一转换为 {@link Result}，避免堆栈直接暴露给前端。
 *
 * @author huiyi
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 业务异常：按约定返回码写 JSON。直接写响应，这样 {@code Accept: text/event-stream} 的流式入口被拒绝时仍是 {@code Result}。
     */
    @ExceptionHandler(BizException.class)
    public void handleBizException(BizException e, HttpServletResponse response) throws IOException {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(e.getCode(), e.getMessage())));
    }

    /**
     * 参数校验异常：拼接字段错误信息返回。
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return Result.error(ResultCode.PARAM_ERROR.getCode(), detail);
    }

    /**
     * 未知异常：打印堆栈，对外仅返回通用提示。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ResultCode.SYSTEM_ERROR);
    }
}
