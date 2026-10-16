package com.huiyi.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huiyi.assistant.model.entity.AuditEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditEventMapper extends BaseMapper<AuditEvent> {
}
