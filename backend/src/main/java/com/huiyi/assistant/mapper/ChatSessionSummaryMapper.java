package com.huiyi.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huiyi.assistant.model.entity.ChatSessionSummary;
import org.apache.ibatis.annotations.Mapper;

/**
 * chat_session_summaries 表 Mapper。
 *
 * @author huiyi
 */
@Mapper
public interface ChatSessionSummaryMapper extends BaseMapper<ChatSessionSummary> {
}
