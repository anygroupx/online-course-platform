package com.course.platform.infra.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** Reads effective server-side authorities from normalized RBAC tables. */
@Mapper
public interface UserAuthorityMapper {

    // Serialize role replacements, including concurrent removal of the last administrator.
    @Select("SELECT id FROM sys_role WHERE role_code = 'SUPER_ADMIN' FOR UPDATE")
    Long lockSuperAdminRole();

    @Select("SELECT permission_code FROM sys_permission WHERE enabled = 1 ORDER BY permission_code")
    List<String> findEnabledPermissionCodes();

    @Select("""
            SELECT p.permission_code FROM sys_role r
            JOIN sys_role_permission rp ON rp.role_id = r.id
            JOIN sys_permission p ON p.id = rp.permission_id AND p.enabled = 1
            WHERE r.role_code = #{roleCode} AND r.enabled = 1 ORDER BY p.permission_code
            """)
    List<String> findPermissionCodesByRole(@Param("roleCode") String roleCode);

    @Select("""
            SELECT authority FROM (
                SELECT DISTINCT CONCAT('ROLE_', r.role_code) AS authority
                FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id AND r.enabled = 1
                WHERE ur.user_id = #{userId}
                UNION
                SELECT DISTINCT p.permission_code AS authority
                FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id AND r.enabled = 1
                JOIN sys_role_permission rp ON rp.role_id = r.id
                JOIN sys_permission p ON p.id = rp.permission_id AND p.enabled = 1
                WHERE ur.user_id = #{userId}
            ) effective_authority
            ORDER BY authority
            """)
    List<String> findAuthoritiesByUserId(@Param("userId") Long userId);

    @Insert("""
            INSERT IGNORE INTO sys_user_role (user_id, role_id)
            SELECT #{userId}, id FROM sys_role WHERE role_code = #{roleCode} AND enabled = 1
            """)
    int assignRole(@Param("userId") Long userId, @Param("roleCode") String roleCode);

    @Select("""
            SELECT r.role_code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.enabled = 1
            WHERE ur.user_id = #{userId}
            ORDER BY r.role_code
            """)
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);

    @Select("SELECT role_code FROM sys_role WHERE enabled = 1 ORDER BY role_code")
    List<String> findEnabledRoleCodes();

    @Select("""
            SELECT COUNT(*) FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE r.role_code = #{roleCode} AND r.enabled = 1
            """)
    long countUsersWithRole(@Param("roleCode") String roleCode);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteRolesByUserId(@Param("userId") Long userId);

    @Update("UPDATE sys_user SET role = #{legacyRole} WHERE id = #{userId}")
    int updateLegacyRole(@Param("userId") Long userId, @Param("legacyRole") String legacyRole);

    @Select("""
            SELECT r.role_code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.enabled = 1
            WHERE ur.user_id = #{userId}
            ORDER BY CASE r.role_code WHEN 'SUPER_ADMIN' THEN 1 WHEN 'FINANCE' THEN 2
                WHEN 'OPERATOR' THEN 3 WHEN 'CUSTOMER_SERVICE' THEN 4 WHEN 'AUDITOR' THEN 5
                WHEN 'USER' THEN 6 ELSE 7 END
            LIMIT 1
            """)
    String findPrimaryRoleByUserId(@Param("userId") Long userId);
}
