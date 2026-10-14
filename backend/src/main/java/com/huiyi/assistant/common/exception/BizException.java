package com.huiyi.assistant.common.exception;

import com.huiyi.assistant.common.result.ResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常，携带统一返回码，由全局异常处理器转换为 {@code Result}。
 *
 * @author huiyi
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Integer code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
