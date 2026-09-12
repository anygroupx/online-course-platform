package com.course.platform.infra.docking.impl;

import com.course.platform.domain.entity.*;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.infra.external.ApiHttpClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BenzReceiptIdentityTest {
    ApiHttpClient http;
    BenzOrderReceiptGateway gateway;
    BenzDockingStrategy strategy;
    CourseOrder order;
    CoursePlatform platform;
    ApiProvider provider;
    static final String ROW = "{\"id\":\"receipt-9\",\"cid\":\"product-2\",\"user\":\"student\",\"pass\":\"private-password\",\"kcname\":\"exact-course\",\"status\":\"已完成\",\"process\":\"100%\"}";
    @BeforeEach void setup() {
        http = mock(ApiHttpClient.class); gateway = new BenzOrderReceiptGateway(http); strategy = new BenzDockingStrategy(http);
        provider = new ApiProvider(); provider.setProviderType("27"); provider.setUsername("saved-user"); provider.setApiKey("saved-key"); provider.setApiUrl("https://receipt.invalid");
        order = new CourseOrder(); order.setStudentAccount("student"); order.setStudentPassword("private-password"); order.setCourseName("exact-course"); order.setSchoolName("school"); order.setCourseId("student-course-3");
        platform = new CoursePlatform(); platform.setDockParam("product-2");
    }
    void respond(String body) { when(http.postForString(any(), anyString(), anyMap())).thenReturn(body); }
    @Test void onlyFullIdentityAndExplicitReceiptProduceSafeProofWithOneRead() {
        respond("{\"code\":1,\"data\":[" + ROW.replace("receipt-9", "other") + "," + ROW + "]}");
        assertEquals("receipt-9", gateway.verify(provider, platform, order, "receipt-9").receiptId());
        verify(http, times(1)).postForString(eq(provider), eq("https://receipt.invalid/api.php?act=chadan"), argThat(p -> p.size()==6 && p.get("user").equals("student") && p.get("pass").equals("private-password") && p.get("key").equals("saved-key")));
        verifyNoMoreInteractions(http);
    }
    @ParameterizedTest @ValueSource(strings={"cid","user","pass","kcname"})
    void missingIdentityCannotAuthorizeRecovery(String field) {
        String row = ROW.replaceAll("\\\""+field+"\\\":\\\"[^\\\"]*\\\",?", "").replace(",}", "}");
        respond("{\"code\":1,\"data\":["+row+"]}");
        var error = assertThrows(ProviderRequestException.class, ()->gateway.verify(provider,platform,order,"receipt-9"));
        assertNull(error.getCause()); assertFalse(error.toString().contains("private-password"));
    }
    @ParameterizedTest @ValueSource(strings={"student","private-password","exact-course","product-2"})
    void anyIdentityMismatchIncludingProductNotStudentCourseFails(String text) {
        respond("{\"code\":1,\"data\":["+ROW.replace(text,"different")+"]}");
        assertThrows(ProviderRequestException.class, ()->gateway.verify(provider,platform,order,"receipt-9"));
    }
    @ParameterizedTest @ValueSource(strings={"{\"code\":4294967297,\"data\":[]}","{\"code\":1.0,\"data\":[]}","{\"code\":1,\"data\":[]}","{\"code\":0,\"data\":[]}","{\"code\":\"1\",\"data\":[]}","{\"code\":1,\"data\":[null]}","{\"code\":1,\"data\":{}}"})
    void failureAndPartialResponsesDoNotAuthorize(String body) {
        respond(body); assertThrows(ProviderRequestException.class, ()->gateway.verify(provider,platform,order,"receipt-9"));
    }
    @Test void duplicateReceiptsAndConflictingIdsAreNotFirstRowWins() {
        for (String row : new String[]{ROW+","+ROW, ROW.replace("\"cid\"", "\"yid\":\"different\",\"cid\""), ROW.replace("\"cid\"", "\"user\":\"other\",\"cid\"")}) {
            respond("{\"code\":1,\"data\":["+row+"]}");
            assertThrows(ProviderRequestException.class,()->gateway.verify(provider,platform,order,"receipt-9"));
        }
    }
    @Test void knownReceiptQueryMustSelectExactIdAndRejectContradictoryIdentity() {
        order.setThirdOrderId("receipt-9");
        respond("{\"code\":1,\"data\":["+ROW.replace("receipt-9","another").replace("100%","20%")+","+ROW+"]}");
        assertEquals("100%",strategy.queryOrderProgress(order,platform,provider).getProgress());
        respond("{\"code\":1,\"data\":["+ROW.replace("student","other")+"]}");
        assertThrows(ProviderRequestException.class,()->strategy.queryOrderProgress(order,platform,provider));
    }
    @Test void missingReceiptCannotTriggerReadThenImplicitRetryWrite() {
        assertFalse(strategy.retryOrder(order,platform,provider).isSuccess());
        assertThrows(ProviderRequestException.class,()->strategy.queryOrderProgress(order,platform,provider));
        verifyNoInteractions(http);
    }
    @Test void acceptedOrderMessageNumbersAndAmbiguousStructuredIdsStayUnbound() {
        for (String body : new String[]{"{\"code\":1,\"id\":\"a\",\"id\":\"b\"}","{\"code\":1,\"msg\":\"成功，余额120.50，1门课\"}","{\"code\":0,\"data\":[{\"id\":\"a\"},{\"id\":\"b\"}]}","{\"code\":1,\"id\":\"a\",\"data\":{\"id\":\"b\"}}"}) {
            respond(body); var result=strategy.dockOrder(order,platform,provider);
            assertTrue(result.isSuccess()); assertNull(result.getThirdOrderId());
        }
    }
    @Test void acceptedOrderWithOneConsistentStructuredReceiptRemainsSupported() {
        for (String body : new String[]{"{\"code\":1,\"id\":\"receipt-9\"}","{\"code\":0,\"data\":[{\"id\":\"receipt-9\"}]}","{\"code\":1,\"id\":\"receipt-9\",\"data\":{\"id\":\"receipt-9\"}}"}) {
            respond(body); assertEquals("receipt-9",strategy.dockOrder(order,platform,provider).getThirdOrderId());
        }
    }
    @Test void concatenatedJsonCannotProveIdentityOrSupplyAnUnambiguousCreatedReceipt() {
        for (String suffix : new String[]{"{}", "[]", "null", "1", "{\"code\":0}"}) {
            respond("{\"code\":1,\"data\":[" + ROW + "]}" + suffix);
            var error = assertThrows(ProviderRequestException.class,
                    () -> gateway.verify(provider, platform, order, "receipt-9"));
            assertNull(error.getCause());
            assertFalse(error.toString().contains("private-password"));
            assertNull(BenzReceiptMatcher.createdId("{\"code\":1,\"id\":\"receipt-9\"}" + suffix));
        }
        respond("{\"code\":1,\"data\":[" + ROW + "]} \r\n\t");
        assertEquals("receipt-9", gateway.verify(provider, platform, order, "receipt-9").receiptId());
    }
    @Test void optionalReturnedSchoolOrStudentCourseCannotContradictOwnedOrder() {
        for (String field: new String[]{"school","kcid"}) {
            respond("{\"code\":1,\"data\":["+ROW.replace("\"cid\"", "\""+field+"\":\"other\",\"cid\"")+"]}");
            assertThrows(ProviderRequestException.class,()->gateway.verify(provider,platform,order,"receipt-9"));
        }
    }

    @Test void candidateReadReturnsOnlyExactlyMatchedIdsWithoutPickingFirstOrLeakingPrivateRows() {
        respond("{\"code\":1,\"data\":[" + ROW.replace("receipt-9", "someone-else").replace("student", "other")
                + "," + ROW.replace("receipt-9", "candidate-2") + "," + ROW + "]}");
        assertEquals(java.util.List.of("candidate-2", "receipt-9"), gateway.findCandidates(provider, platform, order)
                .stream().map(com.course.platform.domain.orderreceipt.OrderReceiptTypes.Verified::receiptId).toList());
        verify(http, times(1)).postForString(eq(provider), eq("https://receipt.invalid/api.php?act=chadan"),
                argThat(p -> p.size() == 6 && p.get("user").equals("student") && p.get("pass").equals("private-password")
                        && p.get("uid").equals("saved-user") && p.get("key").equals("saved-key")));
        verifyNoMoreInteractions(http);
    }
    @ParameterizedTest @ValueSource(strings={"cid", "user", "pass", "kcname"})
    void missingCandidateIdentityIsNeverEvidenceEvenWhenRequestWasFiltered(String field) {
        for (String replacement : new String[]{"", "\"" + field + "\":null,"}) {
            String row = ROW.replaceAll("\\\"" + field + "\\\":\\\"[^\\\"]*\\\",?", replacement).replace(",}", "}");
            respond("{\"code\":1,\"data\":[" + row + "]}");
            assertTrue(gateway.findCandidates(provider, platform, order).isEmpty());
        }
    }
    @ParameterizedTest @ValueSource(strings={"student", "private-password", "exact-course", "product-2"})
    void candidateIdentityMismatchIsExcludedRatherThanAssumedToBeOwned(String value) {
        respond("{\"code\":1,\"data\":[" + ROW.replace(value, value + " ") + "]}");
        assertTrue(gateway.findCandidates(provider, platform, order).isEmpty());
    }
    @Test void optionalCandidateIdentityMustNotContradictAndNumericProductIdentifiersRemainExact() {
        for (String field : new String[]{"school", "kcid"}) {
            respond("{\"code\":1,\"data\":[" + ROW.replace("\"cid\"", "\"" + field + "\":\"other\",\"cid\"") + "]}");
            assertTrue(gateway.findCandidates(provider, platform, order).isEmpty());
        }
        platform.setDockParam("2");
        respond("{\"code\":1,\"data\":[" + ROW.replace("\"product-2\"", "2").replace("\"receipt-9\"", "123") + "]}");
        assertEquals("123", gateway.findCandidates(provider, platform, order).get(0).receiptId());
        platform.setDockParam("02");
        assertTrue(gateway.findCandidates(provider, platform, order).isEmpty());
    }
    @Test void candidateDuplicatesConflictingIdsAndMalformedRowsFailTheWholeRead() {
        for (String rows : new String[]{ROW + "," + ROW.replace("student", "other"),
                ROW.replace("\"cid\"", "\"yid\":\"different\",\"cid\""), ROW + ",null",
                ROW.replace("\"student\"", "{}"), ROW.replace("\"cid\"", "\"school\":[],\"cid\""),
                ROW.replace("receipt-9", "../id"), ROW.replace("\"cid\"", "\"user\":\"duplicate-key\",\"cid\"")}) {
            respond("{\"code\":1,\"data\":[" + rows + "]}");
            var error = assertThrows(ProviderRequestException.class, () -> gateway.findCandidates(provider, platform, order));
            assertNull(error.getCause()); assertFalse(error.toString().contains("private-password"));
        }
    }
    @ParameterizedTest @ValueSource(strings={"", "null", "[]", "{\"code\":0,\"data\":[]}", "{\"code\":\"1\",\"data\":[]}",
            "{\"code\":4294967297,\"data\":[]}", "{\"code\":1.0,\"data\":[]}", "{\"code\":1,\"data\":{}}", "{\"code\":1}"})
    void failedOrUnusableCandidateEnvelopeIsNotAnEmptySuccess(String response) {
        respond(response);
        assertThrows(ProviderRequestException.class, () -> gateway.findCandidates(provider, platform, order));
    }
    @Test void boundedCandidateSetRejectsOverflowInsteadOfSilentlyTruncatingOrPaginating() {
        String twenty = java.util.stream.IntStream.range(0, 20).mapToObj(i -> ROW.replace("receipt-9", "receipt-" + i))
                .collect(java.util.stream.Collectors.joining(","));
        respond("{\"code\":1,\"data\":[" + twenty + "]}");
        assertEquals(20, gateway.findCandidates(provider, platform, order).size());
        respond("{\"code\":1,\"data\":[" + twenty + "," + ROW.replace("receipt-9", "receipt-20") + "]}");
        assertThrows(ProviderRequestException.class, () -> gateway.findCandidates(provider, platform, order));
        verify(http, times(2)).postForString(any(), anyString(), anyMap()); verifyNoMoreInteractions(http);
    }
    @Test void candidateParserCapsResponseRowsAndBodyAndRejectsConcatenatedDocuments() {
        String thousand = java.util.stream.IntStream.range(0, 1000)
                .mapToObj(i -> ROW.replace("receipt-9", "receipt-" + i).replace("student", "other"))
                .collect(java.util.stream.Collectors.joining(","));
        respond("{\"code\":1,\"data\":[" + thousand + "]}");
        assertTrue(gateway.findCandidates(provider, platform, order).isEmpty());
        for (String response : new String[]{"{\"code\":1,\"data\":[" + thousand + "," + ROW + "]}",
                "{\"code\":1,\"data\":[" + ROW + "]}null", " ".repeat(1024 * 1024 + 1),
                "{\"code\":1,\"data\":[" + ROW.replace("private-password", "a".repeat(8193)) + "]}"}) {
            respond(response);
            assertThrows(ProviderRequestException.class, () -> gateway.findCandidates(provider, platform, order));
        }
    }
    @Test void emptyCandidateReadIsScopedAndUnsupportedProviderNeverMakesHttp() {
        respond("{\"code\":1,\"data\":[]}");
        assertTrue(gateway.findCandidates(provider, platform, order).isEmpty());
        clearInvocations(http); provider.setProviderType("flash");
        assertThrows(ProviderRequestException.class, () -> gateway.findCandidates(provider, platform, order));
        verifyNoInteractions(http);
    }
}
