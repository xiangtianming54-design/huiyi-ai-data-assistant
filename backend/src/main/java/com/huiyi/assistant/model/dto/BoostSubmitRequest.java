package com.huiyi.assistant.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提额申请只收运行编号。理由和 Token 由服务端按当时的账重算。
 *
 * @author huiyi
 */
@Data
public class BoostSubmitRequest {

    /** 申请人选中的运行编号 */
    @NotBlank
    private String runId;
}
