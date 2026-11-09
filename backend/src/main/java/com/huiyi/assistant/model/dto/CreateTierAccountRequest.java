package com.huiyi.assistant.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 第三级新建第二级或第三级账号。显示名可空。
 *
 * @author huiyi
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTierAccountRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    private String displayName;

    private Integer tier;
}
