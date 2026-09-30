package com.course.platform.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.infra.persistence.mapper.ServiceOrderFulfillmentAssetMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** Local-only security and isolation checks, with no provider rows or network transport. */
class ProviderlessSelfOperatedSecurityTest {
    private ServiceCommerceTransactionTest fixture;
    private ServiceCommerceServiceImpl service;
    private JdbcTemplate jdbc;

    @BeforeEach void setup() throws Exception {
        fixture = new ServiceCommerceTransactionTest();
        fixture.setup();
        service = fixture.service;
        jdbc = fixture.jdbc;
    }

    @AfterEach void cleanup() { fixture.cleanup(); }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2", "3", "4"})
    void everySkuCanBeSoldAndFulfilledWithNoProviderConfigurationOrCalls(String sku) throws Exception {
        jdbc.update("DELETE FROM service_product");
        jdbc.update("DELETE FROM api_provider");
        clearInvocations(fixture.providerService, fixture.providerMapper, fixture.catalog,
                fixture.gateway, fixture.commerceAccounts);
        fixture.auth(7, "api-provider:update");
        var product = service.saveProduct(null, new ProductCommand(null, "default", sku,
                "黑鲨商品 " + sku, "", new BigDecimal("0.25"), true, "SELF_OPERATED", null, null, "heisha"));
        assertTrue(product.available());
        assertNull(product.providerId());
        assertEquals(List.of(), product.capabilities());
        fixture.auth(7, "ROLE_USER");
        assertTrue(service.products(1, 20, false).getRecords().get(0).available());
        boolean face = List.of("3", "4").contains(sku);
        String draft = face ? service.createMaterialDraft(new MaterialDraftForm(product.id(), 0L,
                fixture.pngDataUrl(), true)).id() : null;
        var quote = service.quote(product.id(), new OrderForm(10, new BigDecimal("2"),
                Map.of("phone", "13800138000", "password", "local-password-secret", "run_time", "08:00"),
                List.of(), true, null, null, face, draft));
        assertEquals("5.00", quote.amount());
        var confirmed = service.confirm(quote.id());
        assertEquals("SUCCEEDED", service.confirm(quote.id()).state());
        var order = service.order(confirmed.orderId());
        assertEquals("PENDING", order.status());
        assertEquals("PENDING", service.sync(order.id()).status());
        assertEquals("PENDING", order.verificationStatus());
        var stored = fixture.orders.selectById(order.id());
        assertNull(stored.getProviderId());
        assertNull(stored.getProviderVersion());
        assertNull(stored.getProviderIdentity());
        assertNull(stored.getExternalOrderNo());
        assertNull(stored.getPendingOperationId());
        assertNull(fixture.operations.selectById(quote.id()).getProviderVersion());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_order_fulfillment", Integer.class));
        assertEquals(face ? 1 : 0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order_fulfillment_asset", Integer.class));
        if (face) assertNull(jdbc.queryForObject(
                "SELECT content_encrypted FROM service_fulfillment_material_draft WHERE id=?", String.class, draft));
        fixture.auth(8, "service-order:fulfill");
        assertThrows(BusinessException.class, () -> service.manageFulfillment(order.id(),
                new LocalFulfillmentForm("START", 0L, null, null)));
        service.verifyFulfillment(order.id(), new VerificationForm("VERIFY", 0L, 0L, "资格已核实", null, null, null, null));
        service.manageFulfillment(order.id(), new LocalFulfillmentForm("START", 0L, null, null));
        assertEquals("COMPLETED", service.manageFulfillment(order.id(),
                new LocalFulfillmentForm("COMPLETE", 1L, null, null)).status());
        verifyNoInteractions(fixture.providerService, fixture.providerMapper, fixture.catalog,
                fixture.gateway, fixture.commerceAccounts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local-password-secret", "13800138000", "changed-private-password",
            "data:image/png;base64,c2VjcmV0", "faceToken=synthetic-secret", "runPreflightToken=synthetic-secret"})
    void supplementCannotCopySensitiveValuesIntoAuditNotes(String secret) {
        var created = service.confirm(service.quote(fixture.heishaProduct("SELF_OPERATED"), fixture.localForm()).id());
        fixture.auth(8, "service-order:fulfill");
        service.verifyFulfillment(created.orderId(), new VerificationForm("NEEDS_INFO", 0L, 0L,
                "请核对资料", null, null, null, null));
        fixture.auth(7, "ROLE_USER");
        var failure = assertThrows(BusinessException.class, () -> service.updateFulfillmentMaterials(created.orderId(),
                new MaterialUpdateForm(0L, 1L, "changed-private-password", null, false, "说明：" + secret)));
        assertFalse(failure.toString().contains(secret));
        assertEquals("NEEDS_INFO", service.order(created.orderId()).verificationStatus());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order_operation WHERE action='LOCAL_MATERIAL_UPDATED'", Integer.class));
    }

    @Test void supplementClearsPreviousVerificationReasonAndPreservesExactPassword() {
        var created = service.confirm(service.quote(fixture.heishaProduct("SELF_OPERATED"), fixture.localForm()).id());
        fixture.auth(8, "service-order:fulfill");
        service.verifyFulfillment(created.orderId(), new VerificationForm("NEEDS_INFO", 0L, 0L,
                "请核对资料", null, null, null, null));
        fixture.auth(7, "ROLE_USER");
        service.updateFulfillmentMaterials(created.orderId(),
                new MaterialUpdateForm(0L, 1L, "  exact-password  ", null, false, "已核对资料"));
        assertNull(jdbc.queryForObject("SELECT verification_note FROM service_order_fulfillment WHERE order_id=?",
                String.class, created.orderId()));
        fixture.auth(8, "service-order:fulfill");
        assertEquals("  exact-password  ", service.fulfillmentAdmin(created.orderId()).fields().get("password"));
    }

    @Test void draftExpiryAndTerminalPurgeWorkEvenWhenSalesAreDisabled() {
        Long productId = fixture.heishaFaceProduct();
        String abandoned = service.createMaterialDraft(new MaterialDraftForm(productId, 0L, fixture.pngDataUrl(), true)).id();
        String used = service.createMaterialDraft(new MaterialDraftForm(productId, 0L, fixture.pngDataUrl(), true)).id();
        var created = service.confirm(service.quote(productId, fixture.faceLocalForm(used)).id());
        jdbc.update("UPDATE service_fulfillment_material_draft SET expires_at=? WHERE id=?", ServiceTime.now().minusSeconds(1), abandoned);
        jdbc.update("UPDATE service_order SET status='COMPLETED',update_time=? WHERE id=?", ServiceTime.now().minusHours(25), created.orderId());
        ReflectionTestUtils.setField(service, "enabled", false);
        ReflectionTestUtils.setField(service, "fulfillmentAssetRetentionHours", 24L);
        service.purgeFulfillmentMaterials();
        assertNull(jdbc.queryForObject("SELECT content_encrypted FROM service_fulfillment_material_draft WHERE id=?", String.class, abandoned));
        assertEquals("EXPIRED", jdbc.queryForObject("SELECT state FROM service_fulfillment_material_draft WHERE id=?", String.class, abandoned));
        assertNull(jdbc.queryForObject("SELECT content_encrypted FROM service_order_fulfillment_asset WHERE order_id=?", String.class, created.orderId()));
        assertNotNull(jdbc.queryForObject("SELECT purged_at FROM service_order_fulfillment_asset WHERE order_id=?", Object.class, created.orderId()));
    }

    @Test void concurrentQualificationHasOnlyOneWinner() throws Exception {
        var created = service.confirm(service.quote(fixture.heishaProduct("SELF_OPERATED"), fixture.localForm()).id());
        var gate = new CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> attempt = () -> {
            fixture.auth(8, "service-order:fulfill");
            gate.await();
            try {
                service.verifyFulfillment(created.orderId(), new VerificationForm("VERIFY", 0L, 0L,
                        "资格已核实", null, null, null, null));
                return true;
            } catch (BusinessException expected) { return false; }
        };
        var first = fixture.threads.submit(attempt);
        var second = fixture.threads.submit(attempt);
        gate.countDown();
        assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_order_operation WHERE action='LOCAL_VERIFY'", Integer.class));
    }

    @Test void manualResolvedRulesAreEncryptedAndNeverChangeFrozenCharges() {
        var created = service.confirm(service.quote(fixture.heishaProduct("SELF_OPERATED"), fixture.localForm()).id());
        fixture.auth(8, "service-order:fulfill");
        var before = fixture.orders.selectById(created.orderId());
        service.verifyFulfillment(created.orderId(), new VerificationForm("VERIFY", 0L, 0L, "资格已核实",
                "manual-plan", "已核实计划", "manual-fence", "已核实区域", new BigDecimal("1"), new BigDecimal("3")));
        var after = fixture.orders.selectById(created.orderId());
        assertEquals(before.getUnitCharge(), after.getUnitCharge());
        assertEquals(before.getPaidAmount(), after.getPaidAmount());
        assertEquals(before.getDistance(), after.getDistance());
        assertEquals(before.getQuantity(), after.getQuantity());
        var details = service.fulfillmentAdmin(created.orderId());
        assertEquals("manual-plan", details.fields().get("plan_option_id"));
        assertEquals("1", details.fields().get("single_min_distance_km"));
        assertEquals("3", details.fields().get("single_max_distance_km"));
        assertFalse(jdbc.queryForObject("SELECT payload_encrypted FROM service_order_fulfillment WHERE order_id=?",
                String.class, created.orderId()).contains("manual-plan"));
    }

    @Test void faceDraftCannotBeReusedAndReadIsBoundToOrderAndIntegrity() {
        Long id = fixture.heishaFaceProduct();
        String draft = service.createMaterialDraft(new MaterialDraftForm(id, 0L, fixture.pngDataUrl(), true)).id();
        var first = service.quote(id, fixture.faceLocalForm(draft));
        var second = service.quote(id, fixture.faceLocalForm(draft));
        var created = service.confirm(first.id());
        assertThrows(BusinessException.class, () -> service.confirm(second.id()));
        fixture.money("95");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM service_order", Integer.class));
        String asset = jdbc.queryForObject("SELECT id FROM service_order_fulfillment_asset WHERE order_id=?", String.class, created.orderId());
        fixture.auth(8, "service-order:fulfill", "service-order:biometric");
        assertThrows(BusinessException.class, () -> service.fulfillmentAsset(java.util.UUID.randomUUID().toString(), asset));
        jdbc.update("UPDATE service_order_fulfillment_asset SET byte_size=byte_size+1 WHERE id=?", asset);
        assertThrows(BusinessException.class, () -> service.fulfillmentAsset(created.orderId(), asset));
        jdbc.update("UPDATE service_order_fulfillment_asset SET byte_size=byte_size-1,sha256=? WHERE id=?", "0".repeat(64), asset);
        assertThrows(BusinessException.class, () -> service.fulfillmentAsset(created.orderId(), asset));
    }

    @Test void ordinaryProjectionsAndEntityStringsNeverExposeBiometricContent() throws Exception {
        Long id = fixture.heishaFaceProduct();
        String image = fixture.pngDataUrl();
        String draft = service.createMaterialDraft(new MaterialDraftForm(id, 0L, image, true)).id();
        var quote = service.quote(id, fixture.faceLocalForm(draft));
        var created = service.confirm(quote.id());
        var json = new ObjectMapper().findAndRegisterModules();
        String publicJson = json.writeValueAsString(List.of(service.order(created.orderId()),
                service.products(1, 20, false).getRecords(), service.events(created.orderId())));
        fixture.auth(8, "api-provider:update", "payment:reconcile", "service-order:fulfill");
        publicJson += json.writeValueAsString(service.audit(created.orderId()));
        publicJson += json.writeValueAsString(service.fulfillmentAdmin(created.orderId()).assets());
        for (String secret : List.of("local-password-secret", image.substring(image.indexOf(',') + 1),
                "payloadEncrypted", "contentEncrypted", "ENC:v1:")) assertFalse(publicJson.contains(secret), secret);
        String encrypted = jdbc.queryForObject("SELECT content_encrypted FROM service_order_fulfillment_asset WHERE order_id=?", String.class, created.orderId());
        assertTrue(SecretCrypto.isEncrypted(encrypted));
        assertFalse(encrypted.contains(image.substring(image.indexOf(',') + 1)));
        var assets = (ServiceOrderFulfillmentAssetMapper) ReflectionTestUtils.getField(service, "fulfillmentAssetMapper");
        var entity = assets.selectList(null).get(0);
        assertFalse(json.writeValueAsString(entity).contains(encrypted));
        assertFalse(entity.toString().contains(encrypted));
    }

    @Test void faceDraftConsumptionRollsBackWithDebitWhenAssetInsertFails() {
        Long productId = fixture.heishaFaceProduct();
        String draft = service.createMaterialDraft(new MaterialDraftForm(productId, 0L, fixture.pngDataUrl(), true)).id();
        var quote = service.quote(productId, fixture.faceLocalForm(draft));
        var mapper = mock(ServiceOrderFulfillmentAssetMapper.class);
        when(mapper.insert(any(com.course.platform.domain.servicecommerce.ServiceOrderFulfillmentAsset.class)))
                .thenThrow(new IllegalStateException("synthetic persistence failure"));
        ReflectionTestUtils.setField(service, "fulfillmentAssetMapper", mapper);
        assertThrows(IllegalStateException.class, () -> service.confirm(quote.id()));
        fixture.money("100");
        assertEquals("READY", jdbc.queryForObject("SELECT state FROM service_fulfillment_material_draft WHERE id=?", String.class, draft));
        assertNotNull(jdbc.queryForObject("SELECT content_encrypted FROM service_fulfillment_material_draft WHERE id=?", String.class, draft));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM service_order", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger", Integer.class));
    }
}
