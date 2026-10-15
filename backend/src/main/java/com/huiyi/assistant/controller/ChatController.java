package com.huiyi.assistant.controller;

import com.huiyi.assistant.common.result.Result;
import com.huiyi.assistant.model.dto.ChatRequestDTO;
import com.huiyi.assistant.model.vo.ChatResponseVO;
import com.huiyi.assistant.model.vo.IntentResponseVO;
import com.huiyi.assistant.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 智能问答接口。
 *
 * @author huiyi
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 非流式问答。
     *
     * @param request 问答请求
     * @return 回答、意图与引用来源
     */
    @PostMapping
    public Result<ChatResponseVO> chat(@Validated @RequestBody ChatRequestDTO request) {
        return Result.ok(chatService.chat(request));
    }

    /**
     * 意图预检：判断问题将走知识问答还是数据查询链路。
     *
     * @param request 问答请求（仅使用 question 字段）
     * @return 意图编码
     */
    @PostMapping("/intent")
    public Result<IntentResponseVO> detectIntent(@Validated @RequestBody ChatRequestDTO request) {
        return Result.ok(new IntentResponseVO(chatService.detectIntent(request.getQuestion()).getCode()));
    }

    /**
     * 流式问答：SSE 逐 token 推送，结束时推送 references。
     *
     * @param request 问答请求
     * @return SSE 流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Validated @RequestBody ChatRequestDTO request) {
        return chatService.chatStream(request);
    }
}
