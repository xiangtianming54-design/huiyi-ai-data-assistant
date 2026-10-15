package com.huiyi.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huiyi.assistant.mapper.ChatMessageMapper;
import com.huiyi.assistant.model.entity.ChatMessage;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import com.huiyi.assistant.model.vo.ReferenceVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会话服务：session_id 生成/复用，消息落库。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class ChatSessionService {

    /** 用户角色 */
    public static final String ROLE_USER = "user";

    /** 助手角色 */
    public static final String ROLE_ASSISTANT = "assistant";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatMessageMapper chatMessageMapper;

    public ChatSessionService(ChatMessageMapper chatMessageMapper) {
        this.chatMessageMapper = chatMessageMapper;
    }

    /**
     * 解析会话 ID：客户端未传时生成新 ID。
     *
     * @param sessionId 客户端携带的会话 ID
     * @return 有效会话 ID
     */
    public String resolveSessionId(String sessionId) {
        if (StringUtils.hasText(sessionId)) {
            return sessionId;
        }
        return "s-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 查询会话最近的消息（按时间正序）。
     *
     * @param sessionId 会话 ID
     * @param limit 最大条数
     * @return 消息列表（旧 → 新）
     */
    public List<ChatMessage> getRecentMessages(String sessionId, int limit) {
        List<ChatMessage> messages = new ArrayList<>(chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT " + limit)));
        Collections.reverse(messages);
        return messages;
    }

    /**
     * 保存用户提问。
     */
    public void saveUserMessage(String sessionId, String question, IntentTypeEnum intent) {
        save(sessionId, ROLE_USER, question, intent, null, null);
    }

    /**
     * 保存助手回答（含引用来源）。
     */
    public void saveAssistantMessage(String sessionId, String answer, IntentTypeEnum intent,
                                     String sqlText, List<ReferenceVO> references) {
        String metadataJson = null;
        if (references != null && !references.isEmpty()) {
            try {
                metadataJson = OBJECT_MAPPER.writeValueAsString(Map.of("references", references));
            } catch (JsonProcessingException e) {
                log.warn("引用来源序列化失败，metadata_json 置空: {}", e.getMessage());
            }
        }
        save(sessionId, ROLE_ASSISTANT, answer, intent, sqlText, metadataJson);
    }

    /**
     * 消息落库。
     */
    private void save(String sessionId, String role, String content, IntentTypeEnum intent,
                      String sqlText, String metadataJson) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setIntent(intent == null ? null : intent.getCode());
        message.setSqlText(sqlText);
        message.setMetadataJson(metadataJson);
        chatMessageMapper.insert(message);
    }
}
