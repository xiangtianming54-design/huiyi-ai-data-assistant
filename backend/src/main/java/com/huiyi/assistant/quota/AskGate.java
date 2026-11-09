package com.huiyi.assistant.quota;

import com.huiyi.assistant.model.entity.ChatMessage;
import com.huiyi.assistant.service.ChatSessionService;
import com.huiyi.assistant.service.QueryRewriteService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 提问入口闸门。先准入，再按近期原文检查今日额度。不调用意图、改写、检索或查数。
 *
 * @author huiyi
 */
@Service
public class AskGate {

    private final AskAdmission admission;
    private final TokenQuotaService quota;
    private final ChatSessionService sessions;

    public AskGate(AskAdmission admission, TokenQuotaService quota, ChatSessionService sessions) {
        this.admission = admission;
        this.quota = quota;
        this.sessions = sessions;
    }

    /**
     * 准入并确认额度足够开跑。
     *
     * @param userId    用户
     * @param question  这一句原文
     * @param sessionId 会话；为空则近期原文为空
     * @param ticket    已签发的票；{@code issuing} 为 false 时交给 {@code follow}
     * @param issuing   为 true 时签发新票，为 false 时核销已有票
     * @return 新票，或调用方带来的票
     */
    public String enter(long userId, String question, String sessionId, String ticket, boolean issuing) {
        String issued = issuing ? admission.begin(userId) : ticket;
        if (!issuing) {
            admission.follow(userId, ticket);
        }
        quota.assertCanStart(userId, question, recentTexts(sessionId));
        return issued;
    }

    private List<String> recentTexts(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return List.of();
        }
        return sessions.getRecentMessages(sessionId, QueryRewriteService.HISTORY_LIMIT).stream()
                .map(ChatMessage::getContent)
                .toList();
    }
}
