package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import com.course.platform.infra.http.*;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiProviderServiceImplTest {
    private ApiProviderServiceImpl service(ApiProviderMapper mapper) {
        OutboundSecurityProperties properties = new OutboundSecurityProperties();
        properties.setProviderAllowedHosts(List.of("provider.example"));
        return new ApiProviderServiceImpl(mapper, mock(SsrfGuard.class), new OutboundPolicyRegistry(properties));
    }


    @Test
    @DisplayName("运行时加载应解密数据库中的 API Key")
    void loadDecrypted_shouldReturnPlainSecrets() {
        String masterKey = "unit-test-master-key";
        ApiProviderMapper mapper = mock(ApiProviderMapper.class);
        ApiProviderServiceImpl service = service(mapper);
        ReflectionTestUtils.setField(service, "cryptoSecret", masterKey);

        ApiProvider encrypted = new ApiProvider();
        encrypted.setId(1L);
        encrypted.setApiKey(SecretCrypto.encrypt("plain-api-key", masterKey));
        encrypted.setPassword(SecretCrypto.encrypt("plain-password", masterKey));
        assertTrue(encrypted.getApiKey().startsWith("ENC:v1:"));
        when(mapper.selectById(1L)).thenReturn(encrypted);

        ApiProvider decrypted = service.loadDecrypted(1L);

        assertEquals("plain-api-key", decrypted.getApiKey());
        assertEquals("plain-password", decrypted.getPassword());
    }

    @Test
    @DisplayName("缺少解密密钥时不得把加密凭据发送给第三方")
    void loadDecrypted_encryptedSecretsWithoutMasterKeyShouldFail() {
        String masterKey = "unit-test-master-key";
        ApiProviderMapper mapper = mock(ApiProviderMapper.class);
        ApiProviderServiceImpl service = service(mapper);

        ApiProvider encrypted = new ApiProvider();
        encrypted.setId(3L);
        encrypted.setApiKey(SecretCrypto.encrypt("plain-api-key", masterKey));
        when(mapper.selectById(3L)).thenReturn(encrypted);

        assertThrows(BusinessException.class, () -> service.loadDecrypted(3L));
    }

    @Test
    @DisplayName("编辑时留空应保留原账号和加密凭据")
    void updateApiProvider_blankCredentialsShouldKeepStoredValues() {
        String masterKey = "unit-test-master-key";
        ApiProviderMapper mapper = mock(ApiProviderMapper.class);
        ApiProviderServiceImpl service = service(mapper);
        ReflectionTestUtils.setField(service, "cryptoSecret", masterKey);

        ApiProvider existing = new ApiProvider();
        existing.setId(2L);
        existing.setApiUrl("https://provider.example/api.php");
        existing.setUsername("stored-user");
        existing.setPassword(SecretCrypto.encrypt("stored-password", masterKey));
        existing.setApiKey(SecretCrypto.encrypt("stored-api-key", masterKey));
        existing.setToken(SecretCrypto.encrypt("stored-token", masterKey));
        existing.setCookie(SecretCrypto.encrypt("stored-cookie", masterKey));
        when(mapper.selectById(2L)).thenReturn(existing);

        ApiProvider update = new ApiProvider();
        update.setId(2L);
        update.setName("Daytime provider");
        update.setUsername("");
        update.setPassword("");
        update.setApiKey("");
        update.setToken("");
        update.setCookie("");

        service.updateApiProvider(update);

        ArgumentCaptor<ApiProvider> captor = ArgumentCaptor.forClass(ApiProvider.class);
        verify(mapper).updateById(captor.capture());
        ApiProvider saved = captor.getValue();
        assertEquals("stored-user", saved.getUsername());
        assertEquals(existing.getPassword(), saved.getPassword());
        assertEquals(existing.getApiKey(), saved.getApiKey());
        assertEquals(existing.getToken(), saved.getToken());
        assertEquals(existing.getCookie(), saved.getCookie());
    }
}
