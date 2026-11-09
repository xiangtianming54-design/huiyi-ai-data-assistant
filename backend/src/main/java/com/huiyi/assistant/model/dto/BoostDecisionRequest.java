package com.huiyi.assistant.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 审评结论。只接受通过或拒绝。
 *
 * @author huiyi
 */
@Data
public class BoostDecisionRequest {

    /** {@code APPROVED} 或 {@code REJECTED} */
    @NotBlank
    private String decision;
}
