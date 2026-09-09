package com.course.platform.infra.projectcenter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.projectcenter.ProjectTicketTypes.*;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;

class Syyv5TicketGatewayTest {
    ApiHttpClient http;
    Syyv5ProjectGateway gateway;
    ApiProvider provider;
    Map<String, Object> row;
    final ObjectMapper json = new ObjectMapper();
    static final String CUSTOMER = "private-customer-key", OWNER = "private-owner-key";

    @BeforeEach
    void setup() {
        http = mock(ApiHttpClient.class);
        gateway = new Syyv5ProjectGateway(http, new ProviderUrlNormalizer(),
                new ProjectTicketImagePolicy(new com.course.platform.infra.projectclient.ProjectTicketImageCodec()));
        provider = new ApiProvider();
        provider.setProviderType("syyv5");
        provider.setApiUrl("https://supplier.example/openapi.php");
        provider.setApiKey(OWNER);
        row =
                new LinkedHashMap<>(
                        Map.of(
                                "id",
                                31,
                                "ticket_id",
                                31,
                                "project_id",
                                5,
                                "type",
                                "compensation",
                                "title",
                                "服务问题",
                                "description",
                                "请核实本人项目记录",
                                "compensation_amount",
                                "2.25",
                                "status",
                                "pending",
                                "review_result",
                                "",
                                "review_note",
                                ""));
        row.put("created_at", "2026-09-08 12:00:00");
        row.put("updated_at", "2026-09-08 12:00:00");
        row.put("image_data", "");
        row.put("replies", List.of());
    }

    String response() throws Exception {
        return json.writeValueAsString(Map.of("status", "success", "data", Map.of("ticket", row)));
    }

    SubmitForm form() {
        return new SubmitForm("compensation", "服务问题", "请核实本人项目记录", new BigDecimal("2.25"), true);
    }

    Map<String, Object> reply() {
        return new LinkedHashMap<>(
                Map.of(
                        "id",
                        45,
                        "ticket_id",
                        31,
                        "sender_type",
                        "customer",
                        "content",
                        "补充执行记录",
                        "image_data",
                        "",
                        "created_at",
                        "2026-09-08 12:05:00"));
    }

    @Test
    void submitUsesOnlyBoundCustomerKeyAndExplicitProjectWithoutUpstreamOwnerList()
            throws Exception {
        when(http.postForString(any(), anyString(), anyMap())).thenReturn(response());
        var receipt = gateway.submitTicket(provider, "5", CUSTOMER, form());
        assertEquals("31", receipt.id());
        verify(http)
                .postForString(
                        eq(provider),
                        eq(provider.getApiUrl()),
                        eq(
                                Map.of(
                                        "action",
                                        "submitCustomerTicket",
                                        "api_key",
                                        CUSTOMER,
                                        "project_id",
                                        "5",
                                        "type",
                                        "compensation",
                                        "title",
                                        "服务问题",
                                        "description",
                                        "请核实本人项目记录",
                                        "compensation_amount",
                                        "2.25")));
        verify(http, never()).getForString(any(), anyString(), anyMap());
        assertFalse(receipt.toString().contains(CUSTOMER));
    }

    @Test
    void detailFetchesOneBoundIdWithCustomerKeyAndNeverRequestsBroadList() throws Exception {
        when(http.getForString(any(), anyString(), anyMap())).thenReturn(response());
        gateway.ticket(provider, "5", CUSTOMER, "31");
        verify(http)
                .getForString(
                        eq(provider),
                        eq(provider.getApiUrl()),
                        eq(
                                Map.of(
                                        "action",
                                        "getCustomerTicketDetail",
                                        "api_key",
                                        CUSTOMER,
                                        "project_id",
                                        "5",
                                        "ticket_id",
                                        "31")));
        verify(http, never()).postForString(any(), anyString(), anyMap());
    }

    @Test
    void replyIsOnePostAndResponseMustContainTheSubmittedCustomerText() throws Exception {
        row.put("replies", List.of(reply()));
        row.put("status", "processing");
        when(http.postForString(any(), anyString(), anyMap())).thenReturn(response());
        assertEquals(
                "补充执行记录",
                gateway.replyTicket(provider, "5", CUSTOMER, "31", "补充执行记录")
                        .replies()
                        .get(0)
                        .content());
        verify(http)
                .postForString(
                        eq(provider),
                        eq(provider.getApiUrl()),
                        eq(
                                Map.of(
                                        "action",
                                        "replyCustomerTicket",
                                        "api_key",
                                        CUSTOMER,
                                        "project_id",
                                        "5",
                                        "ticket_id",
                                        "31",
                                        "content",
                                        "补充执行记录")));
    }

    @Test
    void reviewUsesOwnerKeyAndOnlyReturnsReviewStatusWithoutAnyBalanceCall() throws Exception {
        row.put("status", "resolved");
        row.put("review_result", "approved");
        row.put("review_note", "逐项核实后同意申请，但款项须独立核对");
        when(http.postForString(any(), anyString(), anyMap())).thenReturn(response());
        var r = gateway.reviewTicket(provider, "5", "31", "approved", "逐项核实后同意申请，但款项须独立核对");
        assertEquals("approved", r.reviewResult());
        verify(http, times(1))
                .postForString(
                        eq(provider),
                        eq(provider.getApiUrl()),
                        eq(
                                Map.of(
                                        "action",
                                        "reviewCompensationTicket",
                                        "api_key",
                                        OWNER,
                                        "ticket_id",
                                        "31",
                                        "review_result",
                                        "approved",
                                        "review_note",
                                        "逐项核实后同意申请，但款项须独立核对")));
        verify(http, never()).getForString(any(), anyString(), anyMap());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "ticket_id", "project_id"})
    void mismatchedIdentityNeverBecomesAUserTicket(String field) throws Exception {
        row.put(field, 99);
        when(http.getForString(any(), anyString(), anyMap())).thenReturn(response());
        assertThrows(
                ProviderRequestException.class,
                () -> gateway.ticket(provider, "5", CUSTOMER, "31"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing_id",
                "missing_type",
                "missing_status",
                "missing_replies",
                "bad_money",
                "unknown_type",
                "unknown_status",
                "wrong_reply",
                "duplicate_reply",
                "oversized_replies"
            })
    void invalidTicketContractsFailClosed(String scenario) throws Exception {
        switch (scenario) {
            case "missing_id" -> row.remove("id");
            case "missing_type" -> row.remove("type");
            case "missing_status" -> row.remove("status");
            case "missing_replies" -> row.remove("replies");
            case "bad_money" -> row.put("compensation_amount", "1e4");
            case "unknown_type" -> row.put("type", "credit");
            case "unknown_status" -> row.put("status", "success");
            case "wrong_reply" -> {
                var r = reply();
                r.put("ticket_id", 99);
                row.put("replies", List.of(r));
            }
            case "duplicate_reply" -> row.put("replies", List.of(reply(), reply()));
            case "oversized_replies" -> row.put("replies", Collections.nCopies(101, reply()));
        }
        when(http.getForString(any(), anyString(), anyMap())).thenReturn(response());
        assertThrows(
                ProviderRequestException.class,
                () -> gateway.ticket(provider, "5", CUSTOMER, "31"));
    }

    @Test
    void supplierMarkupRemainsPlainTextAndImageUrlsNeverEnterReceipt() throws Exception {
        var reply = reply();
        reply.put("content", CUSTOMER + " " + OWNER + " <img src=x onerror=alert(1)>");
        reply.put("image_data", "https://private.example/secret-image");
        row.put("replies", List.of(reply));
        when(http.getForString(any(), anyString(), anyMap())).thenReturn(response());
        var result = gateway.ticket(provider, "5", CUSTOMER, "31");
        assertEquals(
                "[REDACTED] [REDACTED] <img src=x onerror=alert(1)>",
                result.replies().get(0).content());
        assertTrue(result.replies().get(0).hasAttachment());
        assertFalse(json.writeValueAsString(result).contains("private.example"));
    }

    @Test
    void lossAndRejectedWritesAreNotRetriedAndProviderErrorsNeverEchoSecrets() {
        when(http.postForString(any(), anyString(), anyMap()))
                .thenReturn(
                        "{\"status\":\"error\",\"message\":\"" + CUSTOMER + " " + OWNER + "\"}");
        var ex =
                assertThrows(
                        ProviderRequestException.class,
                        () -> gateway.submitTicket(provider, "5", CUSTOMER, form()));
        assertFalse(ex.toString().contains(CUSTOMER));
        verify(http, times(1)).postForString(any(), anyString(), anyMap());
    }

    @Test
    void contradictoryDetailArraysAndWrongReviewReceiptsAreRejected() throws Exception {
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn(
                        json.writeValueAsString(
                                Map.of(
                                        "status",
                                        "success",
                                        "data",
                                        Map.of("ticket", row, "replies", List.of(reply())))));
        assertThrows(
                ProviderRequestException.class,
                () -> gateway.ticket(provider, "5", CUSTOMER, "31"));
        when(http.postForString(any(), anyString(), anyMap())).thenReturn(response());
        assertThrows(
                ProviderRequestException.class,
                () -> gateway.reviewTicket(provider, "5", "31", "approved", "已核实的补偿审核说明"));
    }

    @Test
    void duplicateJsonKeysAndMissingSubmittedReplyAreRejected() throws Exception {
        when(http.getForString(any(), anyString(), anyMap()))
                .thenReturn("{\"status\":\"success\",\"status\":\"success\"}");
        assertThrows(
                ProviderRequestException.class,
                () -> gateway.ticket(provider, "5", CUSTOMER, "31"));
        when(http.postForString(any(), anyString(), anyMap())).thenReturn(response());
        assertThrows(
                ProviderRequestException.class,
                () -> gateway.replyTicket(provider, "5", CUSTOMER, "31", "未返回的回复"));
    }
    private String raster(String format, int color) throws Exception {
        return com.course.platform.infra.projectclient.ProjectTicketImageCodecTest.data(format, color);
    }
    private String canonical(String raw) {
        return new ProjectTicketImagePolicy(new com.course.platform.infra.projectclient.ProjectTicketImageCodec()).outgoing(raw);
    }

    @Test void uploadUsesCanonicalPngInOneFormPostAndChecksImageEcho() throws Exception {
        String raw = raster("jpeg", 0x123456), clean = canonical(raw);
        row.put("image_data", clean);
        when(http.postForString(any(), anyString(), anyMap())).thenReturn(response());
        var f = form();
        var result = gateway.submitTicket(provider,"5",CUSTOMER,new SubmitForm(f.type(),f.title(),f.description(),f.compensationAmount(),true,raw));
        assertEquals(clean,result.imageData()); assertTrue(result.hasAttachment());
        verify(http,times(1)).postForString(eq(provider),eq(provider.getApiUrl()),argThat(v -> clean.equals(v.get("image_data")) && CUSTOMER.equals(v.get("api_key"))));
        verify(http,never()).getForString(any(),anyString(),anyMap());
    }

    @Test void missingOrDifferentUploadImageEchoNeverCountsAsAccepted() throws Exception {
        String raw=raster("png",0x123456);var f=form();
        for (String actual : List.of("",raster("png",0x654321),"https://outside.example/private.png")) {
            row.put("image_data",actual); when(http.postForString(any(),anyString(),anyMap())).thenReturn(response());
            assertThrows(ProviderRequestException.class,()->gateway.submitTicket(provider,"5",CUSTOMER,new SubmitForm(f.type(),f.title(),f.description(),f.compensationAmount(),true,raw)));
        }
        verify(http,times(3)).postForString(any(),anyString(),anyMap());
        verify(http,never()).getForString(any(),anyString(),anyMap());
    }

    @Test void imageOnlyReplyRequiresMatchingCustomerPixelsAndText() throws Exception {
        String raw=raster("png",0x123456), clean=canonical(raw);
        var r=reply();r.put("content","");r.put("image_data",clean);row.put("replies",List.of(r));
        when(http.postForString(any(),anyString(),anyMap())).thenReturn(response());
        assertEquals(clean,gateway.replyTicketWithImage(provider,"5",CUSTOMER,"31","",raw).replies().get(0).imageData());
        r.put("image_data",raster("png",0xFEDCBA));when(http.postForString(any(),anyString(),anyMap())).thenReturn(response());
        assertThrows(ProviderRequestException.class,()->gateway.replyTicketWithImage(provider,"5",CUSTOMER,"31","",raw));
        r.put("image_data",clean);r.put("sender_type","owner");when(http.postForString(any(),anyString(),anyMap())).thenReturn(response());
        assertThrows(ProviderRequestException.class,()->gateway.replyTicketWithImage(provider,"5",CUSTOMER,"31","",raw));
    }

    @Test void inlineDataIsNormalizedButRemoteUrlsVectorsAndMalformedImagesStayOpaque() throws Exception {
        row.put("image_data",raster("jpeg",0x123456));
        for (String source : List.of("https://outside.example/?key="+CUSTOMER,"data:image/svg+xml;base64,PHN2Zy8+","data:image/png;base64,broken")) {
            var r=reply();r.put("image_data",source);row.put("replies",List.of(r));
            when(http.getForString(any(),anyString(),anyMap())).thenReturn(response());
            var result=gateway.ticket(provider,"5",CUSTOMER,"31");
            assertTrue(result.imageData().startsWith("data:image/png;base64,"));
            assertTrue(result.replies().get(0).hasAttachment());assertNull(result.replies().get(0).imageData());
            assertFalse(json.writeValueAsString(result).contains(source));
        }
        verify(http,times(3)).getForString(eq(provider),eq(provider.getApiUrl()),anyMap());
        verifyNoMoreInteractions(http);
    }

    @Test void malformedOutgoingImagesAreRejectedBeforeAnyHttpCall() {
        var f=form();
        assertThrows(com.course.platform.common.exception.BusinessException.class,()->gateway.submitTicket(provider,"5",CUSTOMER,new SubmitForm(f.type(),f.title(),f.description(),f.compensationAmount(),true,"https://outside.example/x.png")));
        assertThrows(ProviderRequestException.class,()->gateway.replyTicketWithImage(provider,"5",CUSTOMER,"31","",null));
        verifyNoInteractions(http);
    }

    @Test void ticketResponseLimitDoesNotWidenCatalogueLimit() throws Exception {
        row.put("ignored","x".repeat(270000));when(http.getForString(any(),anyString(),anyMap())).thenReturn(response());
        assertNotNull(gateway.ticket(provider,"5",CUSTOMER,"31"));
        assertThrows(ProviderRequestException.class,()->gateway.projects(provider));
        when(http.getForString(any(),anyString(),anyMap())).thenReturn("x".repeat(8*1024*1024+1));
        assertThrows(ProviderRequestException.class,()->gateway.ticket(provider,"5",CUSTOMER,"31"));
    }

    @Test void normalizedImageBudgetBoundsManyCompressedSupplierAttachments() throws Exception {
        var image=new java.awt.image.BufferedImage(1024,512,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var random=new Random(7); for(int y=0;y<512;y++)for(int x=0;x<1024;x++)image.setRGB(x,y,random.nextInt());
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);image.flush();
        String data="data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray());
        var policy=new ProjectTicketImagePolicy(new com.course.platform.infra.projectclient.ProjectTicketImageCodec());
        var budget=policy.receipt();int copies=ProjectTicketImagePolicy.MAX_RECEIPT_IMAGE_CHARS/canonical(data).length();
        assertTrue(copies>=1 && copies<10);
        for(int i=0;i<copies;i++)assertNotNull(budget.image(data));
        assertThrows(com.course.platform.common.exception.BusinessException.class,()->budget.image(data));
    }

}
