package com.huiyi.assistant.quota;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.huiyi.assistant.api.v1.QuotaController;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.common.result.Result;
import com.huiyi.assistant.mapper.AssistantRunMapper;
import com.huiyi.assistant.mapper.LlmCallMapper;
import com.huiyi.assistant.mapper.TokenBoostRequestMapper;
import com.huiyi.assistant.mapper.UserMapper;
import com.huiyi.assistant.model.dto.BoostDecisionRequest;
import com.huiyi.assistant.model.dto.BoostSubmitRequest;
import com.huiyi.assistant.model.entity.AssistantRun;
import com.huiyi.assistant.model.entity.LlmCall;
import com.huiyi.assistant.model.entity.TokenBoostRequest;
import com.huiyi.assistant.model.entity.User;
import com.huiyi.assistant.security.AuthUser;
import com.huiyi.assistant.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 提额申请、审评，以及通过后同一上海日的上限。
 *
 * @author huiyi
 */
@ExtendWith(MockitoExtension.class)
class TokenBoostServiceTest {

    private static final LocalDate QUOTA_DATE = LocalDate.of(2026, 11, 9);
    private static final Instant SAME_DAY = OffsetDateTime.parse("2026-11-09T00:30+08:00").toInstant();
    private static final Instant NEXT_MIDNIGHT = OffsetDateTime.parse("2026-11-10T00:00+08:00").toInstant();
    private static final LocalDateTime DAY_START = QUOTA_DATE.atStartOfDay();
    private static final LocalDateTime INSIDE = LocalDateTime.of(2026, 11, 9, 12, 0);
    private static final Pattern CONDITION = Pattern.compile(
            "`?([A-Za-z0-9_]+)`?\\s*(>=|<=|<>|>|<|=)\\s*#\\{ew\\.paramNameValuePairs\\.([A-Za-z0-9_]+)\\}");

    private static final long ADA = 11L;
    private static final long MID = 12L;
    private static final long OTHER_TIER_THREE = 13L;
    private static final long BLANK = 16L;
    private static final long LIMIT = 17L;
    private static final long SIDE = 18L;

    @Mock UserMapper users;
    @Mock AssistantRunMapper runs;
    @Mock LlmCallMapper calls;
    @Mock TokenBoostRequestMapper boosts;

    private final List<User> people = new ArrayList<>();
    private final List<AssistantRun> runRows = new ArrayList<>();
    private final List<LlmCall> callRows = new ArrayList<>();
    private final List<TokenBoostRequest> requestRows = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong();
    private final AtomicReference<Instant> instant = new AtomicReference<>(SAME_DAY);

    private TokenBoostService service;
    private TokenQuotaService quota;

    @BeforeEach
    void setUp() {
        people.clear();
        runRows.clear();
        callRows.clear();
        requestRows.clear();
        ids.set(0);
        instant.set(SAME_DAY);
        remember(ADA, "ada", 1);
        remember(MID, "mid", 2);
        remember(OTHER_TIER_THREE, "other", 3);
        remember(BLANK, "blank", 3);
        remember(LIMIT, "limit", 2);
        remember(SIDE, "side", 2);
        lenient().when(users.selectById(any())).thenAnswer(invocation -> {
            Number id = invocation.getArgument(0);
            return people.stream().filter(user -> id.longValue() == user.getId()).findFirst().orElse(null);
        });
        lenient().when(runs.selectList(any())).thenAnswer(invocation -> filter(runRows, invocation.getArgument(0)));
        lenient().when(calls.selectList(any())).thenAnswer(invocation -> filter(callRows, invocation.getArgument(0)));
        lenient().when(boosts.selectList(any())).thenAnswer(invocation -> filter(requestRows, invocation.getArgument(0)));
        lenient().when(boosts.selectCount(any())).thenAnswer(invocation ->
                (long) filter(requestRows, invocation.getArgument(0)).size());
        lenient().when(boosts.selectById(any())).thenAnswer(invocation -> {
            Number id = invocation.getArgument(0);
            return requestRows.stream().filter(row -> id.longValue() == row.getId()).findFirst().orElse(null);
        });
        lenient().when(boosts.insert(any(TokenBoostRequest.class))).thenAnswer(invocation -> {
            TokenBoostRequest row = invocation.getArgument(0);
            if (row.getId() == null) {
                row.setId(ids.incrementAndGet());
            }
            requestRows.add(row);
            return 1;
        });
        lenient().when(boosts.updateById(any(TokenBoostRequest.class))).thenAnswer(invocation -> {
            TokenBoostRequest incoming = invocation.getArgument(0);
            TokenBoostRequest stored = requestRows.stream()
                    .filter(row -> incoming.getId().equals(row.getId()))
                    .findFirst()
                    .orElse(null);
            if (stored == null) {
                return 0;
            }
            if (stored != incoming) {
                copyReview(incoming, stored);
            }
            return 1;
        });
        Clock clock = new SettableClock(instant, ZoneOffset.UTC);
        service = new TokenBoostService(users, runs, calls, boosts, clock);
        quota = new TokenQuotaService(users, calls, boosts, clock);
    }

    @Test
    void tierThreeCannotSubmit() {
        BizException ex = assertThrows(BizException.class, () -> service.submit(BLANK, "run-heavy"));

        assertEquals(400, ex.getCode());
        assertEquals("第三级不能提额", ex.getMessage());
        verify(boosts, never()).insert(any(TokenBoostRequest.class));
    }

    @Test
    void rejectionAllowsAnotherSubmitUntilApproved() {
        seedAdaLedger();

        service.submit(ADA, "run-heavy");
        TokenBoostRequest first = latest(ADA);
        assertEquals("PENDING", first.getStatus());
        assertEquals("多（50）", first.getReason());
        assertEquals(QUOTA_DATE, first.getQuotaDate());

        service.decide(LIMIT, first.getId(), "REJECTED");
        assertEquals("REJECTED", first.getStatus());

        service.submit(ADA, "run-heavy");
        TokenBoostRequest second = latest(ADA);
        assertEquals("PENDING", second.getStatus());
        assertEquals(2, requestRows.size());

        service.decide(LIMIT, second.getId(), "APPROVED");
        assertEquals("APPROVED", second.getStatus());
        assertEquals(LIMIT, second.getReviewerUserId());
        assertEquals(LocalDateTime.of(2026, 11, 9, 0, 30), second.getReviewedAt());

        BizException ex = assertThrows(BizException.class, () -> service.submit(ADA, "run-heavy"));
        assertEquals(400, ex.getCode());
        assertEquals("今日提额已通过", ex.getMessage());
    }

    @Test
    void approvedBoostRaisesLimitUntilNextShanghaiMidnight() {
        seedAdaLedger();
        service.submit(ADA, "run-heavy");
        service.decide(LIMIT, latest(ADA).getId(), "APPROVED");

        QuotaSnapshot sameDay = quota.snapshot(ADA);
        instant.set(NEXT_MIDNIGHT);
        QuotaSnapshot nextDay = quota.snapshot(ADA);

        assertEquals(UserTierLimits.dailyLimit(1) + UserTierLimits.BOOST_TOKENS, sameDay.limitTokens());
        assertEquals(1_000_000, sameDay.limitTokens());
        assertEquals(UserTierLimits.dailyLimit(1), nextDay.limitTokens());
        assertEquals(500_000, nextDay.limitTokens());
        assertEquals(QUOTA_DATE, latest(ADA).getQuotaDate());
    }

    @Test
    void heaviestSentenceIsMarkedAndReviewCarriesTheStep() {
        seedAdaLedger();
        run(MID, "run-other", "别人", 999, INSIDE);
        call("run-other", "huge", 9_999, "SUCCESS");

        List<SentenceUsage> mine = service.sentences(ADA);
        SentenceUsage heavy = one(mine, "run-heavy");
        SentenceUsage light = one(mine, "run-light");
        SentenceUsage opening = one(mine, "run-open");

        assertEquals(3, mine.size());
        assertEquals("多", heavy.question());
        assertEquals(50, heavy.totalTokens());
        assertTrue(heavy.heaviest());
        assertFalse(light.heaviest());
        assertFalse(opening.heaviest());
        assertEquals(List.of("runId", "question", "totalTokens", "heaviest"), componentNames(SentenceUsage.class));
        assertEquals(List.of("别人"), service.sentences(MID).stream().map(SentenceUsage::question).toList());
        assertTrue(service.sentences(MID).get(0).heaviest());

        service.submit(ADA, "run-heavy");
        List<BoostReview> pending = service.pendingReviews(LIMIT);
        assertEquals(1, pending.size());
        BoostReview review = pending.get(0);
        assertEquals("ada", review.applicant());
        assertEquals("多（50）", review.reason());
        assertEquals("answer", review.stepName());
        assertEquals(40, review.stepTokens());
        assertEquals(List.of("id", "applicant", "reason", "stepName", "stepTokens"), componentNames(BoostReview.class));
    }

    @Test
    void limitCannotReviewAForgedRequestOutsideTierOne() {
        TokenBoostRequest forged = plant(MID, "PENDING");

        BizException ex = assertThrows(BizException.class, () -> service.decide(LIMIT, forged.getId(), "APPROVED"));

        assertEquals(403, ex.getCode());
        assertEquals("没有权限执行该操作", ex.getMessage());
        assertEquals("PENDING", forged.getStatus());
        assertEquals(null, forged.getReviewerUserId());
    }

    @Test
    void anotherTierTwoCannotReviewTierOne() {
        seedAdaLedger();
        service.submit(ADA, "run-heavy");
        long id = latest(ADA).getId();

        BizException ex = assertThrows(BizException.class, () -> service.decide(SIDE, id, "APPROVED"));

        assertEquals(403, ex.getCode());
        assertEquals("没有权限执行该操作", ex.getMessage());
        assertEquals("PENDING", latest(ADA).getStatus());
    }

    @Test
    void tierThreeCannotReviewTierOne() {
        seedAdaLedger();
        service.submit(ADA, "run-heavy");
        long id = latest(ADA).getId();

        BizException ex = assertThrows(BizException.class, () -> service.decide(BLANK, id, "REJECTED"));

        assertEquals(403, ex.getCode());
        assertEquals("没有权限执行该操作", ex.getMessage());
        assertEquals("PENDING", latest(ADA).getStatus());
    }

    @Test
    void pendingListShowsOnlyRequestsTheCallerMayDecide() {
        seedAdaLedger();
        run(MID, "run-mid", "第二级的一句", 20, INSIDE);
        call("run-mid", "rewrite", 20, "SUCCESS");
        service.submit(ADA, "run-heavy");
        service.submit(MID, "run-mid");

        assertEquals(List.of("ada"), names(service.pendingReviews(LIMIT)));
        assertEquals(List.of("mid"), names(service.pendingReviews(BLANK)));
        assertEquals(List.of("mid"), names(service.pendingReviews(OTHER_TIER_THREE)));
        assertEquals(List.of(), service.pendingReviews(SIDE));
        assertEquals(List.of(), service.pendingReviews(ADA));

        service.decide(LIMIT, latest(ADA).getId(), "REJECTED");
        assertEquals(List.of(), service.pendingReviews(LIMIT));
        assertEquals(List.of("mid"), names(service.pendingReviews(BLANK)));
    }

    @Test
    void anyTierThreeCanApproveTierTwo() {
        run(MID, "run-mid", "第二级的一句", 20, INSIDE);
        call("run-mid", "rewrite", 20, "SUCCESS");
        service.submit(MID, "run-mid");

        BizException denied = assertThrows(BizException.class,
                () -> service.decide(LIMIT, latest(MID).getId(), "APPROVED"));
        assertEquals(403, denied.getCode());
        assertEquals("没有权限执行该操作", denied.getMessage());

        service.decide(OTHER_TIER_THREE, latest(MID).getId(), "APPROVED");

        assertEquals("APPROVED", latest(MID).getStatus());
        assertEquals(OTHER_TIER_THREE, latest(MID).getReviewerUserId());
        assertEquals(UserTierLimits.dailyLimit(2) + UserTierLimits.BOOST_TOKENS, quota.snapshot(MID).limitTokens());
        assertEquals(2_000_000, quota.snapshot(MID).limitTokens());
    }

    @Test
    void controllerUsesTheSignedInUserAndDoesNotAsk() throws Exception {
        seedAdaLedger();
        QuotaController controller = new QuotaController(quota, service);
        for (var constructor : QuotaController.class.getConstructors()) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                assertFalse(ChatService.class.equals(parameter));
            }
        }
        assertEquals("/api/v1/me/quota", route(QuotaController.class, "mine", AuthUser.class));
        assertEquals("/api/v1/me/quota/sentences", route(QuotaController.class, "sentences", AuthUser.class));
        assertEquals("/api/v1/me/quota/boosts",
                route(QuotaController.class, "submit", AuthUser.class, BoostSubmitRequest.class));
        assertEquals("/api/v1/quota/boosts", route(QuotaController.class, "pending", AuthUser.class));
        assertEquals("/api/v1/quota/boosts/{id}/decision",
                route(QuotaController.class, "decide", AuthUser.class, long.class, BoostDecisionRequest.class));
        assertTrue(QuotaController.class.getMethod("mine", AuthUser.class)
                .getParameters()[0].isAnnotationPresent(AuthenticationPrincipal.class));
        assertEquals(List.of("runId"), fieldNames(BoostSubmitRequest.class));
        assertEquals(List.of("decision"), fieldNames(BoostDecisionRequest.class));

        AuthUser ada = new AuthUser(ADA, "ada", 1);
        Result<QuotaSnapshot> before = controller.mine(ada);
        assertEquals(0, before.getCode());
        assertEquals(500_000, before.getData().limitTokens());
        assertTrue(controller.sentences(ada).getData().stream()
                .filter(sentence -> "run-heavy".equals(sentence.runId()))
                .anyMatch(SentenceUsage::heaviest));

        BoostSubmitRequest submit = new BoostSubmitRequest();
        submit.setRunId("run-heavy");
        Result<Void> submitted = controller.submit(ada, submit);
        assertEquals(0, submitted.getCode());
        assertEquals("多（50）", latest(ADA).getReason());

        AuthUser limit = new AuthUser(LIMIT, "limit", 1);
        Result<List<BoostReview>> waiting = controller.pending(limit);
        assertEquals("answer", waiting.getData().get(0).stepName());
        assertEquals(List.of(), controller.pending(new AuthUser(BLANK, "blank", 1)).getData());

        BoostDecisionRequest decision = new BoostDecisionRequest();
        decision.setDecision("APPROVED");
        Result<Void> decided = controller.decide(limit, waiting.getData().get(0).id(), decision);
        assertEquals(0, decided.getCode());
        assertEquals(1_000_000, controller.mine(ada).getData().limitTokens());

        BizException again = assertThrows(BizException.class, () -> controller.submit(ada, submit));
        assertEquals("今日提额已通过", again.getMessage());
    }

    private void seedAdaLedger() {
        run(ADA, "run-open", "早", 3, DAY_START);
        run(ADA, "run-light", "少", 10, INSIDE);
        run(ADA, "run-heavy", "多", 50, INSIDE);
        run(ADA, "run-zero", "无", 0, INSIDE);
        run(ADA, "run-yesterday", "昨天", 80, LocalDateTime.of(2026, 11, 8, 12, 0));
        run(ADA, "run-tomorrow", "明天", 70, LocalDateTime.of(2026, 11, 10, 0, 0));
        call("run-heavy", "rewrite", 5, "SUCCESS");
        call("run-heavy", "answer", 40, "FAILED");
        call("run-heavy", "draft", 0, "SUCCESS");
    }

    private void remember(long id, String username, int tier) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setUserTier(tier);
        people.add(user);
    }

    private void run(long userId, String runId, String question, int totalTokens, LocalDateTime createdAt) {
        AssistantRun row = new AssistantRun();
        row.setUserId(userId);
        row.setRunId(runId);
        row.setQuestion(question);
        row.setTotalTokens(totalTokens);
        row.setCreatedAt(createdAt);
        runRows.add(row);
    }

    private void call(String runId, String purpose, int totalTokens, String status) {
        LlmCall row = new LlmCall();
        row.setRunId(runId);
        row.setPurpose(purpose);
        row.setTotalTokens(totalTokens);
        row.setStatus(status);
        callRows.add(row);
    }

    private TokenBoostRequest plant(long applicantId, String status) {
        TokenBoostRequest row = new TokenBoostRequest();
        row.setId(ids.incrementAndGet());
        row.setApplicantUserId(applicantId);
        row.setStatus(status);
        row.setReason("伪造");
        row.setStepName("forged");
        row.setStepTokens(1);
        row.setQuotaDate(QUOTA_DATE);
        requestRows.add(row);
        return row;
    }

    private TokenBoostRequest latest(long applicantId) {
        return requestRows.stream()
                .filter(row -> row.getApplicantUserId() == applicantId)
                .reduce((left, right) -> right)
                .orElseThrow();
    }

    private static SentenceUsage one(List<SentenceUsage> sentences, String runId) {
        return sentences.stream().filter(sentence -> runId.equals(sentence.runId())).findFirst().orElseThrow();
    }

    private static List<String> names(List<BoostReview> reviews) {
        return reviews.stream().map(BoostReview::applicant).toList();
    }

    private static void copyReview(TokenBoostRequest incoming, TokenBoostRequest stored) {
        if (incoming.getStatus() != null) {
            stored.setStatus(incoming.getStatus());
        }
        if (incoming.getReviewerUserId() != null) {
            stored.setReviewerUserId(incoming.getReviewerUserId());
        }
        if (incoming.getReviewedAt() != null) {
            stored.setReviewedAt(incoming.getReviewedAt());
        }
        if (incoming.getReason() != null) {
            stored.setReason(incoming.getReason());
        }
        if (incoming.getStepName() != null) {
            stored.setStepName(incoming.getStepName());
        }
        if (incoming.getStepTokens() != null) {
            stored.setStepTokens(incoming.getStepTokens());
        }
    }

    private static List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic() && !field.getName().startsWith("$"))
                .map(field -> field.getName())
                .toList();
    }

    private static String route(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getMethod(name, parameters);
        RequestMapping root = type.getAnnotation(RequestMapping.class);
        String prefix = root == null || root.value().length == 0 ? "" : root.value()[0];
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) {
            return prefix + get.value()[0];
        }
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) {
            return prefix + post.value()[0];
        }
        throw new AssertionError(name);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> filter(List<T> rows, Wrapper<T> wrapper) {
        String sql = wrapper.getSqlSegment();
        Map<String, Object> params = ((AbstractWrapper<T, ?, ?>) wrapper).getParamNameValuePairs();
        List<Object[]> conditions = new ArrayList<>();
        Matcher matcher = CONDITION.matcher(sql);
        while (matcher.find()) {
            conditions.add(new Object[] {matcher.group(1), matcher.group(2), params.get(matcher.group(3))});
        }
        if (!sql.isBlank() && conditions.isEmpty()) {
            throw new IllegalStateException("unparsed wrapper: " + sql);
        }
        List<T> matched = new ArrayList<>();
        for (T row : rows) {
            if (conditions.stream().allMatch(condition -> matches(row, condition))) {
                matched.add(row);
            }
        }
        return matched;
    }

    private static boolean matches(Object row, Object[] condition) {
        Object left = read(row, (String) condition[0]);
        String operator = (String) condition[1];
        Object right = condition[2];
        if (left == null || right == null) {
            return "=".equals(operator) && left == null && right == null;
        }
        if (left instanceof Number && right instanceof Number) {
            return compareNumbers(((Number) left).longValue(), operator, ((Number) right).longValue());
        }
        if (left instanceof Comparable<?> && left.getClass().isInstance(right)) {
            @SuppressWarnings("unchecked")
            int compared = ((Comparable<Object>) left).compareTo(right);
            return compareNumbers(compared, operator, 0);
        }
        if ("=".equals(operator)) {
            return left.equals(right);
        }
        if ("<>".equals(operator)) {
            return !left.equals(right);
        }
        return false;
    }

    private static boolean compareNumbers(long left, String operator, long right) {
        return switch (operator) {
            case "=" -> left == right;
            case ">" -> left > right;
            case ">=" -> left >= right;
            case "<" -> left < right;
            case "<=" -> left <= right;
            case "<>" -> left != right;
            default -> false;
        };
    }

    private static Object read(Object bean, String column) {
        StringBuilder camel = new StringBuilder();
        boolean upper = false;
        for (int i = 0; i < column.length(); i++) {
            char current = column.charAt(i);
            if (current == '_') {
                upper = true;
                continue;
            }
            camel.append(upper ? Character.toUpperCase(current) : current);
            upper = false;
        }
        String name = "get" + Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
        try {
            return bean.getClass().getMethod(name).invoke(bean);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(column, ex);
        }
    }

    private static final class SettableClock extends Clock {

        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        private SettableClock(AtomicReference<Instant> instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new SettableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
