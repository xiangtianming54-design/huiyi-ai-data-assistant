package com.huiyi.assistant.quota;

import com.huiyi.assistant.common.exception.BizException;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 每人过去 1000ms 内最多两次 {@code begin}。三个等级共用这一窗口。
 * 合法 {@code follow} 只把本人未使用、签发不超过 1000ms 的票标成已用，不占名额，也不释放名额。
 *
 * @author huiyi
 */
public class AskAdmission {

    private static final int RATE_LIMIT_CODE = 4291;
    private static final String RATE_LIMIT_MESSAGE = "请求过于频繁，请稍后再试";
    private static final long WINDOW_MILLIS = 1000L;
    private static final int MAX_BEGINS = 2;

    private final Clock clock;
    private final Map<Long, ArrayDeque<Long>> beginsByUser = new HashMap<>();
    private final Map<String, Ticket> tickets = new HashMap<>();

    public AskAdmission(Clock clock) {
        this.clock = clock;
    }

    /**
     * 记一次发问并返回票。该用户在过去 1000ms 内已经有两次时拒绝。
     */
    public synchronized String begin(long userId) {
        long now = clock.millis();
        pruneExpiredTickets(now);
        ArrayDeque<Long> window = beginsByUser.computeIfAbsent(userId, id -> new ArrayDeque<>());
        while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MILLIS) {
            window.removeFirst();
        }
        if (window.size() >= MAX_BEGINS) {
            throw new BizException(RATE_LIMIT_CODE, RATE_LIMIT_MESSAGE);
        }
        window.addLast(now);
        String ticket = UUID.randomUUID().toString();
        tickets.put(ticket, new Ticket(userId, now));
        return ticket;
    }

    /**
     * 票属于该用户、未使用、签发不超过 1000ms 时标记已用。否则按一次 {@code begin} 处理。
     */
    public synchronized void follow(long userId, String ticket) {
        long now = clock.millis();
        Ticket issued = liveTicket(ticket, now);
        if (issued != null && issued.userId == userId && !issued.used) {
            issued.used = true;
            return;
        }
        begin(userId);
    }

    private Ticket liveTicket(String ticket, long now) {
        if (ticket == null || ticket.isEmpty()) {
            return null;
        }
        Ticket issued = tickets.get(ticket);
        if (issued == null || now - issued.issuedAtMillis > WINDOW_MILLIS) {
            return null;
        }
        return issued;
    }

    private void pruneExpiredTickets(long now) {
        tickets.entrySet().removeIf(entry -> now - entry.getValue().issuedAtMillis > WINDOW_MILLIS);
    }

    private static final class Ticket {
        private final long userId;
        private final long issuedAtMillis;
        private boolean used;

        private Ticket(long userId, long issuedAtMillis) {
            this.userId = userId;
            this.issuedAtMillis = issuedAtMillis;
        }
    }
}
