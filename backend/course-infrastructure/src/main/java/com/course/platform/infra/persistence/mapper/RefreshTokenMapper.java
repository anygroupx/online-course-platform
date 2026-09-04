package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** Refresh-session mapper. All rotation decisions use locking/conditional SQL. */
@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {

    @Select("SELECT * FROM refresh_token WHERE token_hash = #{tokenHash} LIMIT 1")
    RefreshToken selectByHash(@Param("tokenHash") String tokenHash);

    /** Lock every token in one family in deterministic primary-key order. */
    @Select("SELECT id FROM refresh_token WHERE token_family_id = #{familyId} ORDER BY id FOR UPDATE")
    java.util.List<Long> lockFamily(@Param("familyId") String familyId);

    @Update("""
            UPDATE refresh_token
            SET revoked_at = #{revokedAt}, revocation_reason = 'ROTATED',
                replaced_by = #{replacedBy}, last_used_ip = #{lastUsedIp}, update_time = NOW()
            WHERE id = #{id} AND revoked_at IS NULL AND expire_time > #{revokedAt}
            """)
    int rotateIfActive(@Param("id") Long id,
                       @Param("replacedBy") String replacedBy,
                       @Param("lastUsedIp") String lastUsedIp,
                       @Param("revokedAt") LocalDateTime revokedAt);

    @Update("""
            UPDATE refresh_token
            SET revoked_at = COALESCE(revoked_at, #{revokedAt}),
                revocation_reason = COALESCE(revocation_reason, #{reason}), update_time = NOW()
            WHERE token_family_id = #{familyId} AND revoked_at IS NULL
            """)
    int revokeFamily(@Param("familyId") String familyId,
                     @Param("reason") String reason,
                     @Param("revokedAt") LocalDateTime revokedAt);

    /** Lock all refresh rows for a user before bulk revocation. */
    @Select("SELECT id FROM refresh_token WHERE user_id = #{userId} ORDER BY id FOR UPDATE")
    java.util.List<Long> lockUserSessions(@Param("userId") Long userId);

    @Update("""
            UPDATE refresh_token
            SET revoked_at = #{revokedAt}, revocation_reason = #{reason}, update_time = NOW()
            WHERE user_id = #{userId} AND revoked_at IS NULL
            """)
    int revokeAllForUser(@Param("userId") Long userId,
                         @Param("reason") String reason,
                         @Param("revokedAt") LocalDateTime revokedAt);

    @Select("""
            SELECT COUNT(*) FROM refresh_token
            WHERE user_id = #{userId} AND token_family_id = #{familyId}
              AND revoked_at IS NULL AND expire_time > #{now}
            """)
    long countActiveFamily(@Param("userId") Long userId,
                           @Param("familyId") String familyId,
                           @Param("now") LocalDateTime now);
}
