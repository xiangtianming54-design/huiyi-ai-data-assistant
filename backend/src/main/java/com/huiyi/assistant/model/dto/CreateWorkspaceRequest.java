package com.huiyi.assistant.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateWorkspaceRequest {

    @NotBlank
    private String name;
    @NotBlank
    private String code;
}
