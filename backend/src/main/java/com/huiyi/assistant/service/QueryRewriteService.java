package com.huiyi.assistant.service;

import com.huiyi.assistant.agent.ContextBudget;
import com.huiyi.assistant.agent.PromptBudgetExceededException;
import com.huiyi.assistant.common.util.PromptTemplateLoader;
import com.huiyi.assistant.model.entity.ChatMessage;
import com.huiyi.assistant.model.entity.ChatSessionSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 查询改写服务：多轮对话中，将依赖上下文的追问改写为独立问题（Conversational RAG 关键步骤）。
 * 无历史时原样透传（零开销）；改写失败回退原问题（不影响主链路）。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class QueryRewriteService {

    /** 改写 Prompt 模板位置 */
    private static final String CONDENSE_PROMPT = "prompts/condense-question.md";

    /** 近 10 轮原文（约 20 条） */
    private static final int HISTORY_LIMIT = 20;

    private static final String SUMMARIZE_PROMPT = "prompts/history-summarize.md";

    private final ChatSessionService chatSessionService;

    private final LlmService llmService;

    private final PromptTemplateLoader promptTemplateLoader;

    public QueryRewriteService(ChatSessionService chatSessionService, LlmService llmService,
                               PromptTemplateLoader promptTemplateLoader) {
        this.chatSessionService = chatSessionService;
        this.llmService = llmService;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    /**
     * 结合会话历史，将问题改写为独立问题。
     *
     * @param sessionId 会话 ID
     * @param question 用户原始问题
     * @return 独立问题（无历史或改写失败时返回原问题）
     */
    public String rewrite(String sessionId, String question) {
        List<ChatMessage> history = chatSessionService.getRecentMessages(sessionId, HISTORY_LIMIT);
        if (history.isEmpty()) {
            return question;
        }
        try {
            String summary = refreshSummary(sessionId, history);
            String historyText = formatHistory(summary, history);
            ContextBudget budget = new ContextBudget();
            historyText = budget.trimHistory(historyText);
            budget.assertFits("", "", "", historyText, question);
            String prompt = promptTemplateLoader.render(CONDENSE_PROMPT,
                    Map.of("history", historyText, "question", question));
            String rewritten = llmService.chat(prompt);
            if (rewritten == null || rewritten.isBlank()) {
                return question;
            }
            String condensed = rewritten.trim();
            log.info("问题改写: 「{}」→「{}」", question, condensed);
            return condensed;
        } catch (PromptBudgetExceededException e) {
            throw e;
        } catch (Exception e) {
            log.warn("问题改写失败，回退原问题: {}", e.getMessage());
            return question;
        }
    }

    private String refreshSummary(String sessionId, List<ChatMessage> recent) {
        if (recent.get(0).getId() == null) {
            return "";
        }
        ChatSessionSummary existing = chatSessionService.findSummary(sessionId);
        String existingText = existing == null ? "" : existing.getSummary();
        Long covered = existing == null ? 0L : existing.getCoveredUntilId();
        List<ChatMessage> overflow = chatSessionService.getOverflowMessages(
                sessionId, recent.get(0).getId(), covered);
        if (overflow.isEmpty()) {
            return existingText;
        }
        try {
            String overflowText = overflow.stream()
                    .map(this::lineOf)
                    .collect(Collectors.joining("\n"));
            String output = llmService.chat(promptTemplateLoader.render(SUMMARIZE_PROMPT, Map.of(
                    "existing_summary", existingText,
                    "overflow", overflowText)));
            if (output == null || output.isBlank()) {
                return existingText;
            }
            String summary = output.trim();
            Long lastId = overflow.get(overflow.size() - 1).getId();
            chatSessionService.saveSummary(sessionId, summary, lastId);
            return summary;
        } catch (Exception e) {
            log.warn("会话摘要失败，仅用近 10 轮原文: {}", e.getMessage());
            return existingText;
        }
    }

    private String formatHistory(String summary, List<ChatMessage> recent) {
        String recentText = recent.stream().map(this::lineOf).collect(Collectors.joining("\n"));
        if (summary == null || summary.isBlank()) {
            return recentText;
        }
        return "【更早对话摘要】\n" + summary + "\n【最近对话】\n" + recentText;
    }

    private String lineOf(ChatMessage message) {
        String prefix = ChatSessionService.ROLE_USER.equals(message.getRole()) ? "用户: " : "助手: ";
        return prefix + message.getContent();
    }
}
