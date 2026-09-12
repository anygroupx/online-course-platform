package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.servicecommerce.ServiceOrder;
import com.course.platform.domain.servicecommerce.ServiceOrderFilter;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.OrderView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

/** Real SQL pagination and ownership checks; every external dependency is a local mock. */
class ServiceOrderSearchTest {
    private final ServiceCommerceTransactionTest fixture = new ServiceCommerceTransactionTest();
    private ServiceOrder seed;
    private List<?> beforeRead;
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 9, 12, 12, 0);

    @BeforeEach
    void setup() throws Exception {
        fixture.setup();
        seed = fixture.orders.selectById(fixture.create().orderId());
        fixture.orders.deleteById(seed.getId());
    }

    @AfterEach
    void cleanup() {
        try {
            if (beforeRead != null) {
                assertEquals(beforeRead, footprint(), "search must not change orders, operations or money");
                verifyNoInteractions(fixture.gateway, fixture.catalog);
                if (fixture.internshipHttp != null) verifyNoInteractions(fixture.internshipHttp);
            }
        } finally { fixture.cleanup(); }
    }

    private List<?> footprint() {
        return List.of(fixture.jdbc.queryForList("SELECT * FROM service_order ORDER BY id"),
                fixture.jdbc.queryForList("SELECT * FROM service_order_operation ORDER BY id"),
                fixture.jdbc.queryForList("SELECT * FROM account_ledger ORDER BY id"),
                fixture.jdbc.queryForList("SELECT * FROM sys_user ORDER BY id"));
    }

    private void seal() {
        beforeRead = footprint();
        clearInvocations(fixture.gateway, fixture.catalog);
        if (fixture.internshipHttp != null) clearInvocations(fixture.internshipHttp);
    }

    private String row(int n, long owner, String type, String state, String title, String label,
                       LocalDateTime created, String pending) {
        ServiceOrder order = new ServiceOrder();
        BeanUtils.copyProperties(seed, order);
        String id = "00000000-0000-0000-0000-" + String.format("%012d", n);
        order.setId(id); order.setExternalOrderNo("OFFLINE-" + n); order.setUserId(owner);
        order.setProviderType(type); order.setStatus(state); order.setTitle(title); order.setAccountLabel(label);
        order.setCreateTime(created); order.setUpdateTime(created); order.setPendingOperationId(pending);
        if ("ssbenz_xbd".equals(type)) {
            order.setQuantity(1);
            order.setScheduleJson("""
                    {"typeCode":"0","totalDistance":"2.00","schoolName":"测试大学","startTime":"07:00","endTime":"08:00","weekdays":[1,3,5]}
                    """);
        }
        fixture.orders.insert(order);
        return id;
    }

    private String row(int n, long owner, String title, String label) {
        return row(n, owner, "jiguang", "ACTIVE", title, label, CREATED, null);
    }

    private ServiceOrderFilter keyword(String text) {
        return new ServiceOrderFilter(text, null, null, null, null, null, null);
    }

    private List<String> ids(ServiceOrderFilter filter) {
        return fixture.service.orders(1, 20, false, filter).getRecords().stream().map(OrderView::id).toList();
    }

    @Test
    void keywordOrClausesNeverEscapeOwnerScopeAndUnknownFocusRevealsNothing() {
        String own = row(1, 7, "晨跑 needle", "13***07");
        row(2, 8, "needle", "other"); row(3, 8, "普通服务", "needle");
        String otherId = row(4, 8, "他人的服务", "13***08");
        seal();
        assertEquals(List.of(own), ids(keyword("needle")));
        assertTrue(ids(keyword(otherId)).isEmpty());
        assertTrue(ids(new ServiceOrderFilter(null, null, null, null, otherId, null, null)).isEmpty());
        assertEquals(List.of(own), ids(keyword("13***07")));
    }

    @Test
    void wildcardsEscapeCharactersAndSqlLikeTextAreSearchedLiterally() {
        String percent = row(1, 7, "服务100%", "one");
        String underscore = row(2, 7, "服务_a", "two");
        String escape = row(3, 7, "服务!", "three");
        String injection = row(4, 7, "' OR 1=1 --", "four");
        String mixed = row(5, 7, "slash\\配额!%_", "five");
        row(6, 7, "服务1000", "six"); row(7, 8, "服务!%_", "other");
        seal();
        assertEquals(List.of(mixed, percent), ids(keyword("%")));
        assertEquals(List.of(mixed, underscore), ids(keyword("_")));
        assertEquals(List.of(mixed, escape), ids(keyword("!")));
        assertEquals(List.of(injection), ids(keyword("' OR 1=1 --")));
        assertEquals(List.of(mixed), ids(keyword("!%_")));
        assertEquals(List.of(mixed), ids(keyword("slash\\")));
        assertEquals(List.of(percent), ids(keyword("  服务100%  ")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"flash", "heisha", "jiguang", "wuxin", "ssbenz_xbd"})
    void typeFiltersMatchSavedServiceFamily(String type) {
        String match = row(1, 7, type, "ACTIVE", "测试服务", "13***07", CREATED, null);
        row(2, 7, "jiguang".equals(type) ? "flash" : "jiguang", "ACTIVE", "测试服务", "13***07", CREATED, null);
        row(3, 8, type, "ACTIVE", "测试服务", "13***08", CREATED, null);
        seal();
        assertEquals(List.of(match), ids(new ServiceOrderFilter(null, type, null, null, null, null, null)));
    }

    @Test
    void internshipSearchUsesItsSavedScheduleWithoutReadingTheProvider() throws Exception {
        Long product = fixture.setupInternship();
        String match = fixture.service.confirm(fixture.service.quote(product, fixture.internshipForm(2)).id()).orderId();
        row(1, 7, "其他服务", "13***07");
        seal();
        var records = fixture.service.orders(1, 20, false,
                new ServiceOrderFilter(null, "sxdk_tw", null, null, null, null, null)).getRecords();
        assertEquals(List.of(match), records.stream().map(OrderView::id).toList());
        assertNotNull(records.get(0).schedule());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "PAUSED", "COMPLETED", "ATTENTION", "REFUND_REVIEW", "SUBMITTING",
            "SUBMITTED", "SUBMISSION_REVIEW", "REFUNDED", "CANCELLED"})
    void stateFilterExcludesRowsWhosePendingOperationChangesTheDisplayedState(String state) {
        String match = row(1, 7, "jiguang", state, "测试服务", "13***07", CREATED, null);
        row(2, 7, "jiguang", state, "测试服务", "13***07", CREATED, UUID.randomUUID().toString());
        row(3, 8, "jiguang", state, "测试服务", "13***08", CREATED, null);
        seal();
        var data = fixture.service.orders(1, 20, false,
                new ServiceOrderFilter(null, null, state, null, null, null, null));
        assertEquals(List.of(match), data.getRecords().stream().map(OrderView::id).toList());
        assertTrue(data.getRecords().stream().allMatch(o -> state.equals(o.status())));
    }

    @Test
    void confirmingIncludesPendingAndPersistedStateButStillAppliesOwnerAndKeyword() {
        String pending = row(1, 7, "flash", "ACTIVE", "匹配", "13***07", CREATED, UUID.randomUUID().toString());
        String stored = row(2, 7, "flash", "CONFIRMING", "匹配", "13***07", CREATED, null);
        row(3, 8, "flash", "CONFIRMING", "匹配", "13***08", CREATED, null);
        row(4, 7, "flash", "CONFIRMING", "其他", "13***07", CREATED, null);
        row(5, 7, "jiguang", "CONFIRMING", "匹配", "13***07", CREATED, null);
        seal();
        var found = fixture.service.orders(1, 20, false,
                new ServiceOrderFilter("匹配", "flash", "CONFIRMING", null, null, null, null));
        assertEquals(List.of(stored, pending), found.getRecords().stream().map(OrderView::id).toList());
        assertTrue(found.getRecords().stream().allMatch(o -> "CONFIRMING".equals(o.status())));
    }

    @Test
    void datesUseInclusiveBeijingStartAndExclusiveFollowingMidnight() {
        row(1, 7, "jiguang", "ACTIVE", "服务", "a", CREATED.toLocalDate().atStartOfDay().minusSeconds(1), null);
        String start = row(2, 7, "jiguang", "ACTIVE", "服务", "b", CREATED.toLocalDate().atStartOfDay(), null);
        String end = row(3, 7, "jiguang", "ACTIVE", "服务", "c", CREATED.toLocalDate().atTime(23, 59, 59), null);
        row(4, 7, "jiguang", "ACTIVE", "服务", "d", CREATED.toLocalDate().plusDays(1).atStartOfDay(), null);
        seal();
        assertEquals(List.of(end, start), ids(new ServiceOrderFilter(null, null, null, null, null, "2026-09-12", "2026-09-12")));
        assertEquals(3, ids(new ServiceOrderFilter(null, null, null, null, null, "2026-09-12", null)).size());
        assertEquals(3, ids(new ServiceOrderFilter(null, null, null, null, null, null, "2026-09-12")).size());
        assertTrue(ids(new ServiceOrderFilter(null, null, null, null, null, "2024-02-29", "2024-02-29")).isEmpty());
    }

    @Test
    void deterministicTieBreakAndExactFocusFindAnOrderBeyondTheFirstPage() {
        List<String> expected = new ArrayList<>();
        for (int i = 1; i <= 43; i++) expected.add(row(i, 7, "服务", "13***07"));
        Collections.reverse(expected); seal();
        List<String> actual = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            IPage<OrderView> data = fixture.service.orders(page, 20, false);
            assertEquals(43, data.getTotal()); assertEquals(page, data.getCurrent()); assertEquals(20, data.getSize());
            actual.addAll(data.getRecords().stream().map(OrderView::id).toList());
        }
        assertEquals(expected, actual);
        String focus = expected.get(42);
        assertEquals(List.of(focus), ids(new ServiceOrderFilter(null, null, null, null, focus, null, null)));
        assertTrue(fixture.service.orders(4, 20, false).getRecords().isEmpty());
        assertTrue(fixture.service.orders(10000, 100, false).getRecords().isEmpty());
    }

    @Test
    void administrativeOwnerScopeRequiresPermissionAndCannotBroadenTheUserEndpoint() {
        String own = row(1, 7, "服务", "13***07"), other = row(2, 8, "服务", "13***08");
        seal();
        var filter = new ServiceOrderFilter(null, null, null, 8L, null, null, null);
        assertThrows(BusinessException.class, () -> fixture.service.orders(1, 20, true, filter));
        assertThrows(BusinessException.class, () -> fixture.service.orders(1, 20, false, filter));
        assertThrows(BusinessException.class, () -> fixture.service.orders(1, 20, false,
                new ServiceOrderFilter(null, null, null, 7L, null, null, null)));
        fixture.auth(7, "api-provider:update");
        assertEquals(List.of(other), fixture.service.orders(1, 20, true, filter).getRecords().stream().map(OrderView::id).toList());
        assertEquals(2, fixture.service.orders(1, 20, true).getTotal());
        assertEquals(List.of(own), ids(ServiceOrderFilter.empty()), "admin on the user URL still sees only their own orders");
        assertEquals(0, fixture.service.orders(1, 20, true,
                new ServiceOrderFilter(null, null, null, Long.MAX_VALUE, null, null, null)).getTotal());
    }

    @Test
    void directServiceValidationRejectsMalformedFiltersAndBounds() {
        seal();
        List<ServiceOrderFilter> invalid = new ArrayList<>(List.of(keyword("x".repeat(101)), keyword("line\nfeed"), keyword("bad\u0085")));
        invalid.add(new ServiceOrderFilter(null, "unknown", null, null, null, null, null));
        invalid.add(new ServiceOrderFilter(null, null, "active", null, null, null, null));
        invalid.add(new ServiceOrderFilter(null, null, null, null, "not-a-uuid", null, null));
        for (String date : List.of("2026-02-29", "2026-09-31", "2026-9-01", "2026-01-01Z", "0999-12-31", "9999-01-01"))
            invalid.add(new ServiceOrderFilter(null, null, null, null, null, date, null));
        invalid.add(new ServiceOrderFilter(null, null, null, null, null, "2026-09-13", "2026-09-12"));
        for (var filter : invalid) assertThrows(BusinessException.class, () -> fixture.service.orders(1, 20, false, filter));
        for (int[] bounds : List.of(new int[]{0, 20}, new int[]{10001, 20}, new int[]{1, 0}, new int[]{1, 101}))
            assertThrows(BusinessException.class, () -> fixture.service.orders(bounds[0], bounds[1], false));
        fixture.auth(7, "api-provider:update");
        assertThrows(BusinessException.class, () -> fixture.service.orders(1, 20, true,
                new ServiceOrderFilter(null, null, null, 0L, null, null, null)));
        SecurityContextHolder.clearContext();
        assertThrows(BusinessException.class, () -> fixture.service.orders(1, 20, false));
    }

    @Test
    void searchesOnlyVisibleLabelsWithoutDecryptingOrReturningCredentials() throws Exception {
        String own = row(1, 7, "服务", "st***nt");
        fixture.jdbc.update("UPDATE service_order_operation SET payload_encrypted='not-a-valid-ciphertext'");
        ReflectionTestUtils.setField(fixture.service, "cryptoSecret", "");
        seal();
        assertEquals(List.of(own), ids(keyword("st***nt")));
        assertTrue(ids(keyword("sensitive-student")).isEmpty());
        assertTrue(ids(keyword("sensitive-password")).isEmpty());
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(fixture.service.orders(1, 20, false));
        for (String field : List.of("payloadEncrypted", "password", "providerIdentity", "not-a-valid-ciphertext")) assertFalse(json.contains(field));
        assertEquals("ServiceOrderFilter[REDACTED]", keyword("private search").toString());
    }
}
