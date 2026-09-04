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
            ORDER BY FIELD(r.role_code, 'SUPER_ADMIN', 'FINANCE', 'OPERATOR', 'CUSTOMER_SERVICE', 'AUDITOR', 'USER')
            LIMIT 1
            """)
    String findPrimaryRoleByUserId(@Param("userId") Long userId);
}
