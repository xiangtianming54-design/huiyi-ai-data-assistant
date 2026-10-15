package com.huiyi.assistant.model.dto;

import lombok.Data;

import java.util.List;

/**
 * Python /api/sql/validate 响应体。
 *
 * @author huiyi
 */
@Data
public class SqlValidateResponseDTO {

    /** 是否通过校验 */
    private Boolean valid;

    /** 修正后的 SQL（通过时返回，可能追加了 LIMIT） */
    private String sql;

    /** 涉及的表 */
    private List<String> tables;

    /** 拒绝原因（未通过时返回） */
    private String reason;
}
