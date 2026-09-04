package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 锁定资金账户行。所有余额变更必须先持有该锁，确保余额前后值和账本一致。
     */
    @Select("SELECT * FROM sys_user WHERE id = #{userId} FOR UPDATE")
    User selectByIdForUpdate(@Param("userId") Long userId);

    /**
     * 原子扣减余额（余额不足时返回 0）
     */
    @Update("""
            UPDATE sys_user
            SET balance = COALESCE(balance, 0) - #{amount},
                update_time = NOW()
            WHERE id = #{userId}
              AND COALESCE(balance, 0) >= #{amount}
            """)
    int decreaseBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 原子增加余额
     */
    @Update("""
            UPDATE sys_user
            SET balance = COALESCE(balance, 0) + #{amount},
                total_recharge = CASE
                    WHEN #{countRecharge} = 1 THEN COALESCE(total_recharge, 0) + #{amount}
                    ELSE COALESCE(total_recharge, 0)
                END,
                update_time = NOW()
            WHERE id = #{userId}
            """)
    int increaseBalance(@Param("userId") Long userId,
                        @Param("amount") BigDecimal amount,
                        @Param("countRecharge") int countRecharge);

    @Update("""
            UPDATE sys_user
            SET last_login_time = #{loginTime}, last_login_ip = #{loginIp},
                must_change_password = CASE WHEN #{forcePasswordChange} = 1 THEN 1 ELSE must_change_password END,
                update_time = NOW()
            WHERE id = #{userId}
            """)
    int updateLoginMetadata(@Param("userId") Long userId,
                            @Param("loginTime") LocalDateTime loginTime,
                            @Param("loginIp") String loginIp,
                            @Param("forcePasswordChange") int forcePasswordChange);

    @Update("""
            UPDATE sys_user
            SET api_key = NULL, api_key_hash = #{apiKeyHash}, api_key_prefix = #{apiKeyPrefix},
                api_key_scopes = #{apiKeyScopes}, api_key_expire_time = #{expireTime}, update_time = NOW()
            WHERE id = #{userId}
            """)
    int updateApiKey(@Param("userId") Long userId,
                     @Param("apiKeyHash") String apiKeyHash,
                     @Param("apiKeyPrefix") String apiKeyPrefix,
                     @Param("apiKeyScopes") String apiKeyScopes,
                     @Param("expireTime") LocalDateTime expireTime);

    @Update("""
            UPDATE sys_user SET invite_code = #{inviteCode}, invite_rate = #{inviteRate}, update_time = NOW()
            WHERE id = #{userId}
            """)
    int updateInviteSettings(@Param("userId") Long userId,
                             @Param("inviteCode") String inviteCode,
                             @Param("inviteRate") BigDecimal inviteRate);

    @Update("""
            <script>
            UPDATE sys_user
            <set>
              <if test="nickname != null">nickname = #{nickname},</if>
              <if test="rate != null">rate = #{rate},</if>
              <if test="status != null">status = #{status},</if>
              <if test="inviteRate != null">invite_rate = #{inviteRate},</if>
              update_time = NOW()
            </set>
            WHERE id = #{userId}
            </script>
            """)
    int updateProfileFields(@Param("userId") Long userId,
                            @Param("nickname") String nickname,
                            @Param("rate") BigDecimal rate,
                            @Param("status") Integer status,
                            @Param("inviteRate") BigDecimal inviteRate);

    @Update("""
            UPDATE sys_user
            SET password = #{password}, must_change_password = #{mustChangePassword},
                password_changed_at = #{changedAt}, update_time = NOW()
            WHERE id = #{userId}
            """)
    int updatePassword(@Param("userId") Long userId,
                       @Param("password") String password,
                       @Param("mustChangePassword") int mustChangePassword,
                       @Param("changedAt") LocalDateTime changedAt);

    @Update("UPDATE sys_user SET status = #{status}, update_time = NOW() WHERE id = #{userId}")
    int updateStatus(@Param("userId") Long userId, @Param("status") Integer status);

    @Update("""
            UPDATE sys_user SET mfa_enabled = 1, mfa_secret = #{secret},
                mfa_backup_codes_hash = #{backupCodesHash}, mfa_enabled_at = #{enabledAt}, update_time = NOW()
            WHERE id = #{userId}
            """)
    int enableMfa(@Param("userId") Long userId,
                  @Param("secret") String secret,
                  @Param("backupCodesHash") String backupCodesHash,
                  @Param("enabledAt") LocalDateTime enabledAt);

    @Update("""
            UPDATE sys_user SET mfa_enabled = 0, mfa_secret = NULL,
                mfa_backup_codes_hash = NULL, mfa_enabled_at = NULL, update_time = NOW()
            WHERE id = #{userId}
            """)
    int disableMfa(@Param("userId") Long userId);

    @Update("UPDATE sys_user SET mfa_backup_codes_hash = #{hashes}, update_time = NOW() WHERE id = #{userId}")
    int updateMfaBackupCodes(@Param("userId") Long userId, @Param("hashes") String hashes);
}
