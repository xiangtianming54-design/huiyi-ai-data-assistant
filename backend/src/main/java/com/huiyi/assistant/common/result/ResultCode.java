package com.huiyi.assistant.common.result;

import lombok.Getter;

/**
 * 统一返回码枚举。
 *
 * @author huiyi
 */
@Getter
public enum ResultCode {

    /** 成功 */
    SUCCESS(0, "ok"),

    /** 参数校验失败 */
    PARAM_ERROR(400, "参数校验失败"),

    /** 系统内部错误 */
    SYSTEM_ERROR(500, "系统内部错误"),

    /** Python Sidecar 服务不可用 */
    PYTHON_SERVICE_UNAVAILABLE(1001, "数据处理服务暂不可用，请稍后重试"),

    /** LLM 服务异常 */
    LLM_SERVICE_ERROR(1002, "AI 服务暂不可用，请稍后重试");

    private final int code;

    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
