package com.course.platform.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecurityAuthorities;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.UserAuthorityMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RbacAdministrationServiceTest {
    private static final String UID = "550e8400-e29b-41d4-a716-446655440000";

    @Mock UserMapper userMapper;
    @Mock UserAuthorityMapper authorityMapper;
    @Mock SecurityAuditService securityAuditService;
    RbacAdministrationService service;

    @BeforeEach
    void setUp() {
        service = new RbacAdministrationService(userMapper, authorityMapper, securityAuditService);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String... authorities) {
        var granted = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, granted));
    }

    private void targetUser() {
        User user = new User();
        user.setId(2L);
        user.setUid(UID);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
    }

    @Test
    void userUpdatePermissionAloneCannotAdministerRoles() {
        authenticate(SecurityAuthorities.USER_UPDATE);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.replaceUserRoles(UID, List.of("FINANCE")));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        verifyNoInteractions(userMapper, authorityMapper);
    }

    @Test
    void refusesToRemoveLastSuperAdmin() {
        authenticate(SecurityAuthorities.RBAC_MANAGE);
        targetUser();
        when(authorityMapper.findEnabledRoleCodes()).thenReturn(List.of("SUPER_ADMIN", "USER"));
        when(authorityMapper.findRoleCodesByUserId(2L)).thenReturn(List.of("SUPER_ADMIN"));
        when(authorityMapper.countUsersWithRole("SUPER_ADMIN")).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.replaceUserRoles(UID, List.of("USER")));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        verify(authorityMapper, never()).deleteRolesByUserId(anyLong());
    }

    @Test
    void replacesRolesAndAuditsChange() {
        authenticate(SecurityAuthorities.RBAC_MANAGE);
        targetUser();
        when(authorityMapper.findEnabledRoleCodes()).thenReturn(List.of("FINANCE", "AUDITOR", "USER"));
        when(authorityMapper.findRoleCodesByUserId(2L)).thenReturn(List.of("USER"));
        when(authorityMapper.assignRole(eq(2L), anyString())).thenReturn(1);

        List<String> result = service.replaceUserRoles(UID, List.of("finance", "AUDITOR"));

        assertEquals(List.of("FINANCE", "AUDITOR"), result);
        verify(authorityMapper).deleteRolesByUserId(2L);
        verify(authorityMapper).assignRole(2L, "FINANCE");
        verify(authorityMapper).assignRole(2L, "AUDITOR");
        verify(authorityMapper).updateLegacyRole(2L, "FINANCE");
        verify(securityAuditService).record(eq("RBAC_ROLE_CHANGED"), eq("WARN"), eq(1L), isNull(),
                contains("/admin/rbac/users/"), eq("PUT"), anyString(), contains("FINANCE"));
    }
}
