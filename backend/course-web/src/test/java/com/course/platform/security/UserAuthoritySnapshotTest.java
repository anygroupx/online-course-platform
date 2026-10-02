package com.course.platform.security;

import com.course.platform.infra.persistence.mapper.UserAuthorityMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAuthoritySnapshotTest {
    @Test
    void projectsTheDatabaseUnionWithoutRolePrefixesOrDuplicatePermissions() {
        var mapper = mock(UserAuthorityMapper.class);
        when(mapper.findAuthoritiesByUserId(7L)).thenReturn(List.of(
                "ROLE_OPERATOR", "ROLE_FINANCE", "service-order:read",
                "service-order:refund", "service-order:read", "service-order:fulfill"));
        var snapshot = new UserAuthorityService(mapper).loadSnapshot(7L);
        assertEquals(List.of("FINANCE", "OPERATOR"), snapshot.roles());
        assertEquals(List.of("service-order:fulfill", "service-order:read", "service-order:refund"), snapshot.permissions());
        verify(mapper, times(1)).findAuthoritiesByUserId(7L);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.permissions().add("rbac:manage"));
    }

    @Test
    void removalTakesEffectOnTheNextProjectionWithoutCaching() {
        var mapper = mock(UserAuthorityMapper.class);
        when(mapper.findAuthoritiesByUserId(7L)).thenReturn(
                List.of("ROLE_OPERATOR", "service-order:fulfill"), List.of("ROLE_USER"), List.of());
        var service = new UserAuthorityService(mapper);
        assertTrue(service.loadSnapshot(7L).permissions().contains("service-order:fulfill"));
        assertEquals(List.of(), service.loadSnapshot(7L).permissions());
        assertEquals(List.of(), service.loadSnapshot(7L).roles());
    }
}
