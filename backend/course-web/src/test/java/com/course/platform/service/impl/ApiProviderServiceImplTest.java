package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.course.platform.application.service.platform.docking.PlatformDockingStrategy;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.infra.docking.PlatformDockingStrategyFactory;
import com.course.platform.infra.http.*;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApiProviderServiceImplTest {
    private ApiProviderMapper mapper;
    private SsrfGuard guard;
    private PlatformDockingStrategy strategy;
    private ApiProviderServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ApiProviderMapper.class);
        guard = mock(SsrfGuard.class);
        strategy = mock(PlatformDockingStrategy.class);
        PlatformDockingStrategyFactory strategies = mock(PlatformDockingStrategyFactory.class);
        when(strategies.getStrategy("Daytime")).thenReturn(strategy);
        when(strategies.getStrategy("29")).thenReturn(strategy);
        ProviderUrlNormalizer normalizer = new ProviderUrlNormalizer();
        service = new ApiProviderServiceImpl(mapper, guard,
                new ProviderOutboundPolicyFactory(new OutboundSecurityProperties(), normalizer), normalizer, strategies);
        when(mapper.update(any(), any())).thenReturn(1);
        when(mapper.insert(any(ApiProvider.class))).thenAnswer(call -> {
            ((ApiProvider) call.getArgument(0)).setId(9L);
            return 1;
        });
    }

    private ApiProvider stored(int status) {
        ApiProvider provider = new ApiProvider();
        provider.setId(9L);
        provider.setName("Test provider");
        provider.setProviderType("Daytime");
        provider.setApiUrl("https://provider.example");
        provider.setUsername("stored-user");
        provider.setStatus(status);
        provider.setConfigVersion(7L);
        when(mapper.selectById(9L)).thenReturn(provider);
        return provider;
    }

    private ApiProvider verified(int status) {
        ApiProvider provider = stored(status);
        provider.setVerifiedAt(LocalDateTime.of(2026, 9, 1, 12, 0));
        provider.setVerifiedBy(2L);
        provider.setLastCheckReason("SUCCESS");
        return provider;
    }

    private ApiProvider edit() {
        ApiProvider input = new ApiProvider();
        input.setId(9L);
        input.setName("Renamed provider");
        return input;
    }

    @SuppressWarnings("unchecked")
    private UpdateWrapper<ApiProvider> capturedUpdate() {
        ArgumentCaptor<UpdateWrapper<ApiProvider>> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(any(), captor.capture());
        return captor.getValue();
    }

    private Object setValue(UpdateWrapper<ApiProvider> wrapper, String column) {
        var matcher = Pattern.compile(column + "=#\\{ew.paramNameValuePairs.(\\w+)\\}").matcher(wrapper.getSqlSet());
        assertTrue(matcher.find(), "missing SET " + column + " in " + wrapper.getSqlSet());
        return wrapper.getParamNameValuePairs().get(matcher.group(1));
    }

    @Test
    void loadDecryptedReturnsCopyAndNeverMutatesCiphertextRecord() {
        String key = "unit-test-master-key";
        ReflectionTestUtils.setField(service, "cryptoSecret", key);
        ApiProvider encrypted = stored(ApiProvider.STATUS_ACTIVE);
        encrypted.setApiKey(SecretCrypto.encrypt("plain-api-key", key));
        encrypted.setPassword(SecretCrypto.encrypt("plain-password", key));

        ApiProvider decrypted = service.loadDecrypted(9L);

        assertNotSame(encrypted, decrypted);
        assertEquals("plain-api-key", decrypted.getApiKey());
        assertEquals("plain-password", decrypted.getPassword());
        assertTrue(SecretCrypto.isEncrypted(encrypted.getApiKey()));
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void encryptedCredentialsWithoutMasterKeyAreNeverSent() {
        stored(ApiProvider.STATUS_PENDING).setApiKey(SecretCrypto.encrypt("plain-api-key", "unit-master-key"));
        assertThrows(BusinessException.class, () -> service.loadDecrypted(9L));
        verifyNoInteractions(strategy);
    }

    @Test
    void blankCredentialsKeepOriginalEncryptedValuesAndVerification() {
        String key = "unit-test-master-key";
        ReflectionTestUtils.setField(service, "cryptoSecret", key);
        ApiProvider existing = verified(ApiProvider.STATUS_ACTIVE);
        existing.setApiKey(SecretCrypto.encrypt("stored-key", key));
        existing.setPassword(SecretCrypto.encrypt("stored-password", key));
        existing.setToken(SecretCrypto.encrypt("stored-token", key));
        existing.setCookie(SecretCrypto.encrypt("stored-cookie", key));
        ApiProvider input = edit();
        input.setUsername("");
        input.setApiKey("");
        input.setPassword("");
        input.setToken("");
        input.setCookie("");

        service.updateApiProvider(input);

        ArgumentCaptor<ApiProvider> captor = ArgumentCaptor.forClass(ApiProvider.class);
        verify(mapper).update(captor.capture(), any());
        ApiProvider saved = captor.getValue();
        assertEquals(existing.getUsername(), saved.getUsername());
        assertEquals(existing.getPassword(), saved.getPassword());
        assertEquals(existing.getApiKey(), saved.getApiKey());
        assertEquals(existing.getToken(), saved.getToken());
        assertEquals(existing.getCookie(), saved.getCookie());
        assertEquals(ApiProvider.STATUS_ACTIVE, saved.getStatus());
        assertEquals(8L, saved.getConfigVersion());
        assertFalse(String.valueOf(capturedUpdate().getSqlSet()).contains("verified_at"));
    }

    @Test
    void createCanonicalizesDaytimeAndCannotMassAssignApprovalOrBalance() {
        ApiProvider input = new ApiProvider();
        input.setId(500L);
        input.setName("New provider");
        input.setProviderType("Daytime");
        input.setApiUrl(" HTTPS://New-Provider.Example:443/a/../openapi/api.php/ ");
        input.setStatus(ApiProvider.STATUS_ACTIVE);
        input.setVerifiedAt(LocalDateTime.now());
        input.setVerifiedBy(500L);
        input.setLastCheckReason("SUCCESS");
        input.setConfigVersion(999L);
        input.setBalance(java.math.BigDecimal.TEN);

        assertEquals(9L, service.createApiProvider(input));

        ArgumentCaptor<ApiProvider> captor = ArgumentCaptor.forClass(ApiProvider.class);
        verify(mapper).insert(captor.capture());
        ApiProvider saved = captor.getValue();
        assertEquals("https://new-provider.example/openapi", saved.getApiUrl());
        assertEquals(ApiProvider.STATUS_PENDING, saved.getStatus());
        assertEquals(0L, saved.getConfigVersion());
        assertNull(saved.getVerifiedAt());
        assertNull(saved.getVerifiedBy());
        assertNull(saved.getLastCheckReason());
        assertNull(saved.getBalance());
        ArgumentCaptor<OutboundRequestPolicy> policy = ArgumentCaptor.forClass(OutboundRequestPolicy.class);
        verify(guard).validate(any(), policy.capture());
        assertEquals(java.util.Set.of("new-provider.example"), policy.getValue().allowedHosts());
    }

    @Test
    void createRejectedDnsTargetIsClassifiedAndNeverPersisted() {
        ApiProvider input = stored(ApiProvider.STATUS_PENDING);
        when(guard.validate(any(), any())).thenThrow(new SafeHttpException(SafeHttpException.Reason.PRIVATE_ADDRESS));
        ProviderRequestException error = assertThrows(ProviderRequestException.class, () -> service.createApiProvider(input));
        assertEquals(ProviderRequestException.Reason.PRIVATE_ADDRESS, error.getReason());
        verify(mapper, never()).insert(any(ApiProvider.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"url", "type", "username", "password", "apiKey", "token", "cookie"})
    void securityRelevantEditsAlwaysClearVerificationAndDeactivate(String field) {
        verified(ApiProvider.STATUS_ACTIVE);
        ApiProvider input = edit();
        input.setStatus(ApiProvider.STATUS_ACTIVE);
        switch (field) {
            case "url" -> input.setApiUrl("https://other.example");
            case "type" -> input.setProviderType("29");
            case "username" -> input.setUsername("new-user");
            case "password" -> input.setPassword("new-password");
            case "apiKey" -> input.setApiKey("new-key");
            case "token" -> input.setToken("new-token");
            case "cookie" -> input.setCookie("new-cookie");
            default -> fail();
        }
        service.updateApiProvider(input);

        ArgumentCaptor<ApiProvider> captor = ArgumentCaptor.forClass(ApiProvider.class);
        verify(mapper).update(captor.capture(), any());
        assertEquals(ApiProvider.STATUS_PENDING, captor.getValue().getStatus());
        UpdateWrapper<ApiProvider> update = capturedUpdate();
        assertNull(setValue(update, "verified_at"));
        assertNull(setValue(update, "verified_by"));
        assertNull(setValue(update, "last_check_reason"));
        assertTrue(update.getSqlSegment().contains("config_version"));
    }

    @Test
    void equivalentDaytimeUrlDoesNotInvalidateApproval() {
        verified(ApiProvider.STATUS_ACTIVE);
        ApiProvider input = edit();
        input.setApiUrl("HTTPS://PROVIDER.EXAMPLE:443/api.php/");
        service.updateApiProvider(input);
        assertFalse(String.valueOf(capturedUpdate().getSqlSet()).contains("verified_at"));
    }

    @Test
    void cannotEnableUnverifiedProviderViaStatusOrEdit() {
        stored(ApiProvider.STATUS_PENDING);
        assertThrows(BusinessException.class, () -> service.updateStatus(9L, ApiProvider.STATUS_ACTIVE));
        ApiProvider edit = edit();
        edit.setStatus(ApiProvider.STATUS_ACTIVE);
        assertThrows(BusinessException.class, () -> service.updateApiProvider(edit));
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void failedHealthCheckPreventsReactivationUntilSuccessfulTest() {
        verified(ApiProvider.STATUS_DISABLED).setLastCheckReason("TIMEOUT");
        assertThrows(BusinessException.class, () -> service.updateStatus(9L, ApiProvider.STATUS_ACTIVE));
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void enablingRequiresVerificationAndRechecksDestinationWithoutWritingCredentials() {
        verified(ApiProvider.STATUS_PENDING);
        service.updateStatus(9L, ApiProvider.STATUS_ACTIVE);
        verify(guard).validate(any(), any());
        UpdateWrapper<ApiProvider> update = capturedUpdate();
        assertEquals(ApiProvider.STATUS_ACTIVE, setValue(update, "status"));
        assertEquals(8L, setValue(update, "config_version"));
        assertFalse(update.getSqlSet().contains("api_key"));
    }

    @Test
    void disablingDoesNotRequireWorkingDnsOrAValidLegacyUrl() {
        stored(ApiProvider.STATUS_ACTIVE).setApiUrl("http://127.0.0.1");
        service.updateStatus(9L, ApiProvider.STATUS_DISABLED);
        verifyNoInteractions(guard, strategy);
        assertEquals(ApiProvider.STATUS_DISABLED, setValue(capturedUpdate(), "status"));
    }

    @Test
    void successfulTestUsesDecryptedCopyAndRecordsActorWithoutActivating() {
        String key = "test-master-key";
        ReflectionTestUtils.setField(service, "cryptoSecret", key);
        ApiProvider stored = stored(ApiProvider.STATUS_PENDING);
        stored.setApiKey(SecretCrypto.encrypt("plain-key", key));

        var result = service.testConnection(9L, 42L);

        ArgumentCaptor<ApiProvider> probe = ArgumentCaptor.forClass(ApiProvider.class);
        verify(strategy).testConnection(probe.capture());
        assertEquals("plain-key", probe.getValue().getApiKey());
        assertEquals(ApiProvider.STATUS_ACTIVE, probe.getValue().getStatus());
        assertEquals(ApiProvider.STATUS_PENDING, stored.getStatus());
        assertTrue(SecretCrypto.isEncrypted(stored.getApiKey()));
        assertEquals("provider.example", result.normalizedHost());
        assertEquals(42L, result.verifiedBy());
        assertEquals(ApiProvider.STATUS_PENDING, result.status());
        assertNotNull(result.verifiedAt());
        assertEquals(42L, setValue(capturedUpdate(), "verified_by"));
        assertFalse(capturedUpdate().getSqlSet().contains("status="));
    }

    @Test
    void testCannotAuthorizeAConfigurationEditedWhileRequestWasRunning() {
        stored(ApiProvider.STATUS_PENDING);
        when(mapper.update(any(), any())).thenReturn(0);
        BusinessException error = assertThrows(BusinessException.class, () -> service.testConnection(9L, 42L));
        assertTrue(error.getMessage().contains("配置已变化"));
        assertTrue(capturedUpdate().getSqlSegment().contains("config_version"));
    }

    @Test
    void failedTestRecordsOnlySafeReasonAndNeverSetsVerification() {
        stored(ApiProvider.STATUS_PENDING);
        var failure = new ProviderRequestException(ProviderRequestException.Reason.TIMEOUT);
        doThrow(failure).when(strategy).testConnection(any());
        assertSame(failure, assertThrows(ProviderRequestException.class, () -> service.testConnection(9L, 42L)));
        UpdateWrapper<ApiProvider> update = capturedUpdate();
        assertEquals("TIMEOUT", setValue(update, "last_check_reason"));
        assertEquals(failure.getErrorId(), setValue(update, "last_check_error_id"));
        assertFalse(update.getSqlSet().contains("verified_at"));
        assertFalse(update.getSqlSet().contains("status="));
    }

    @Test
    void unsafeParserExceptionDoesNotEscapeProbe() {
        stored(ApiProvider.STATUS_PENDING);
        doThrow(new IllegalArgumentException("password=upstream-secret 10.0.0.1"))
                .when(strategy).testConnection(any());
        var failure = assertThrows(ProviderRequestException.class, () -> service.testConnection(9L, 42L));
        assertEquals(ProviderRequestException.Reason.INVALID_RESPONSE, failure.getReason());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("upstream-secret"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void healthCheckSkipsInactiveProviders(int status) {
        stored(status);
        service.checkHealth(9L);
        verifyNoInteractions(strategy, guard);
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void healthCheckOnlyUpdatesHealthAndCannotApproveOrActivate() {
        stored(ApiProvider.STATUS_ACTIVE);
        service.checkHealth(9L);
        verify(strategy).testConnection(any());
        UpdateWrapper<ApiProvider> update = capturedUpdate();
        assertEquals("SUCCESS", setValue(update, "last_check_reason"));
        assertFalse(update.getSqlSet().contains("verified_"));
        assertFalse(update.getSqlSet().contains("status="));
    }

    @Test
    void healthFailureIsRecordedWithoutDisablingProvider() {
        ApiProvider stored = verified(ApiProvider.STATUS_ACTIVE);
        doThrow(new ProviderRequestException(ProviderRequestException.Reason.DNS_FAILURE)).when(strategy).testConnection(any());
        service.checkHealth(9L);
        assertEquals("DNS_FAILURE", setValue(capturedUpdate(), "last_check_reason"));
        assertEquals(ApiProvider.STATUS_ACTIVE, stored.getStatus());
        assertNotNull(stored.getVerifiedAt());
    }
}
