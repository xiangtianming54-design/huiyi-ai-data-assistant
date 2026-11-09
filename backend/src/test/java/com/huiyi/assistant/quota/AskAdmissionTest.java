package com.huiyi.assistant.quota;

import com.huiyi.assistant.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 每人每 1000ms 最多两次 {@code begin}。合法 {@code follow} 不占名额，也不释放名额。
 *
 * @author huiyi
 */
class AskAdmissionTest {

    private static final String RATE_MESSAGE = "请求过于频繁，请稍后再试";
    private static final long USER = 7L;
    private static final long OTHER = 8L;

    private ManualClock clock;
    private AskAdmission admission;

    @BeforeEach
    void setUp() {
        clock = new ManualClock(Instant.parse("2026-11-09T00:30:00Z"));
        admission = new AskAdmission(clock);
    }

    @Test
    void thirdBeginInsideOneSecondThrowsAndBeginWorksAfter1001Millis() {
        assertNotNull(admission.begin(USER));
        assertNotNull(admission.begin(USER));
        assertRateLimited(() -> admission.begin(USER));

        clock.advance(Duration.ofMillis(1001));

        assertNotNull(admission.begin(USER));
    }

    @Test
    void beginAtExactly1000MillisStillUsesTheSameWindow() {
        admission.begin(USER);
        admission.begin(USER);

        clock.advance(Duration.ofMillis(1000));

        assertRateLimited(() -> admission.begin(USER));
    }

    @Test
    void otherUserHasASeparateWindow() {
        admission.begin(USER);
        admission.begin(USER);
        assertRateLimited(() -> admission.begin(USER));

        assertNotNull(admission.begin(OTHER));
        assertNotNull(admission.begin(OTHER));
        assertRateLimited(() -> admission.begin(OTHER));
    }

    @Test
    void legalFollowDoesNotConsumeOrFreeAPermit() {
        String ticket = admission.begin(USER);
        admission.follow(USER, ticket);

        assertDoesNotThrow(() -> admission.begin(USER));
        assertRateLimited(() -> admission.begin(USER));
    }

    @Test
    void usedTicketIsAnotherBegin() {
        String ticket = admission.begin(USER);
        admission.follow(USER, ticket);

        admission.follow(USER, ticket);

        assertRateLimited(() -> admission.begin(USER));
    }

    @Test
    void anotherUsersTicketIsABeginForTheCaller() {
        String ticket = admission.begin(USER);

        admission.follow(OTHER, ticket);
        assertNotNull(admission.begin(OTHER));
        assertRateLimited(() -> admission.begin(OTHER));

        admission.follow(USER, ticket);
        assertNotNull(admission.begin(USER));
        assertRateLimited(() -> admission.begin(USER));
    }

    @Test
    void nullOrEmptyTicketIsABegin() {
        admission.follow(USER, null);
        assertNotNull(admission.begin(USER));
        assertRateLimited(() -> admission.begin(USER));

        admission.follow(OTHER, "");
        assertNotNull(admission.begin(OTHER));
        assertRateLimited(() -> admission.begin(OTHER));
    }

    @Test
    void ticketOlderThan1000MillisIsABegin() {
        String ticket = admission.begin(USER);
        clock.advance(Duration.ofMillis(1001));

        admission.follow(USER, ticket);
        assertNotNull(admission.begin(USER));
        assertRateLimited(() -> admission.begin(USER));
    }

    private static void assertRateLimited(org.junit.jupiter.api.function.Executable call) {
        BizException ex = assertThrows(BizException.class, call);
        assertEquals(4291, ex.getCode());
        assertEquals(RATE_MESSAGE, ex.getMessage());
    }

    private static final class ManualClock extends Clock {

        private final ZoneId zone = ZoneOffset.UTC;
        private Instant instant;

        private ManualClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
