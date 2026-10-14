package com.huiyi.assistant.model.enums;

import lombok.Getter;

/**
 * 知识库文档状态枚举。
 *
 * @author huiyi
 */
@Getter
public enum DocStatusEnum {

    /** 已上传，待解析 */
    UPLOADED,

    /** 已解析分块，待写入向量库 */
    PARSED,

    /** 已写入向量库，可检索 */
    READY,

    /** 处理失败 */
    FAILED
}
