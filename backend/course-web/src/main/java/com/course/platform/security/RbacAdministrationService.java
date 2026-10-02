package com.course.platform.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.course.platform.application.service.security.SecurityAuditService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecurityAuthorities;
import com.course.platform.common.util.PublicUidUtil;
import com.course.platform.domain.entity.User;
import com.course.platform.infra.persistence.mapper.UserAuthorityMapper;
import com.course.platform.infra.persistence.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Privileged, audited role administration. */
@Service
@RequiredArgsConstructor
public class RbacAdministrationService {
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final UserMapper userMapper;
    private final UserAuthorityMapper authorityMapper;
    private final SecurityAuditService securityAuditService;

    public List<String> listEnabledRoles() {
        SecurityUtils.requireAuthority(SecurityAuthorities.RBAC_MANAGE);
        return authorityMapper.findEnabledRoleCodes();
    }

    public List<String> getUserRoles(String userUid) {
        SecurityUtils.requireAuthority(SecurityAuthorities.RBAC_MANAGE);
        return authorityMapper.findRoleCodesByUserId(requireUser(userUid).getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public List<String> replaceUserRoles(String userUid, List<String> requestedRoles) {
        SecurityUtils.requireAuthority(SecurityAuthorities.RBAC_MANAGE);
        if (authorityMapper.lockSuperAdminRole() == null)
            throw new BusinessException(ResultCode.FORBIDDEN);
        User target = requireUser(userUid);
        target = userMapper.selectByIdForUpdate(target.getId());
        if (target == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        Set<String> normalized = normalize(requestedRoles);
        Set<String> enabled = new LinkedHashSet<>(authorityMapper.findEnabledRoleCodes());
        if (!enabled.containsAll(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "包含未知或已禁用角色");
        }

        List<String> currentRoles = authorityMapper.findRoleCodesByUserId(target.getId());
        boolean removesSuperAdmin = currentRoles.contains(SUPER_ADMIN) && !normalized.contains(SUPER_ADMIN);
        if (removesSuperAdmin && authorityMapper.countUsersWithRole(SUPER_ADMIN) <= 1) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "不能移除最后一个超级管理员");
        }

        authorityMapper.deleteRolesByUserId(target.getId());
        for (String role : normalized) {
            if (authorityMapper.assignRole(target.getId(), role) != 1) {
                throw new IllegalStateException("角色分配失败: " + role);
            }
        }
        authorityMapper.updateLegacyRole(target.getId(), legacyPrimaryRole(normalized));
        securityAuditService.recordRbacMutation(SecurityUtils.getCurrentUserId(), target.getUid(),
                currentRoles, List.copyOf(normalized));
        return List.copyOf(normalized);
    }

    public record RolePermissions(String code, List<String> permissions) {}

    public List<String> listPermissions() {
        SecurityUtils.requireAuthority(SecurityAuthorities.RBAC_MANAGE);
        return authorityMapper.findEnabledPermissionCodes();
    }

    public List<String> rolePermissions(String roleCode) {
        SecurityUtils.requireAuthority(SecurityAuthorities.RBAC_MANAGE);
        if (!authorityMapper.findEnabledRoleCodes().contains(roleCode))
            throw new BusinessException(ResultCode.PARAM_ERROR);
        return authorityMapper.findPermissionCodesByRole(roleCode);
    }

    public List<RolePermissions> matrix() {
        SecurityUtils.requireAuthority(SecurityAuthorities.RBAC_MANAGE);
        return authorityMapper.findEnabledRoleCodes().stream()
                .map(code -> new RolePermissions(code, authorityMapper.findPermissionCodesByRole(code))).toList();
    }

    private Set<String> normalize(List<String> roles) {
        if (roles == null || roles.isEmpty() || roles.size() > 6) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "至少指定一个且最多六个角色");
        }
        Set<String> result = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null || !role.matches("[A-Za-z][A-Za-z0-9_]{1,63}")) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "角色代码格式错误");
            }
            result.add(role.toUpperCase(Locale.ROOT));
        }
        return result;
    }

    private User requireUser(String uid) {
        if (!PublicUidUtil.isValid(uid)) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUid, PublicUidUtil.normalize(uid)).last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    private String legacyPrimaryRole(Set<String> roles) {
        if (roles.contains(SUPER_ADMIN)) return "ADMIN";
        if (roles.contains("FINANCE")) return "FINANCE";
        if (roles.contains("OPERATOR")) return "OPERATOR";
        if (roles.contains("CUSTOMER_SERVICE")) return "CS";
        if (roles.contains("AUDITOR")) return "AUDITOR";
        return "USER";
    }
}
