package com.huiyi.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huiyi.assistant.model.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * chat_messages 表 Mapper。
 *
 * @author huiyi
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
