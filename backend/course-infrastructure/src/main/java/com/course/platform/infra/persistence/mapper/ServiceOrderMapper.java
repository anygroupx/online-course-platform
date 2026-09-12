package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.servicecommerce.ServiceOrder;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ServiceOrderMapper extends BaseMapper<ServiceOrder> {
    @Select("SELECT * FROM service_order WHERE id = #{id} FOR UPDATE")
    ServiceOrder lock(@Param("id") String id);

    @Select("""
            SELECT o.id FROM service_order o
            WHERE o.provider_type IN ('flash','heisha','jiguang','wuxin','sxdk_tw','appui','leidian','jingyu')
              AND o.status IN ('ACTIVE','PAUSED','ATTENTION','REFUND_REVIEW')
              AND NOT (o.provider_type='leidian' AND o.status='REFUND_REVIEW')
              AND o.pending_operation_id IS NULL AND o.external_order_no IS NOT NULL
              AND TRIM(o.external_order_no) <> ''
              AND EXISTS (SELECT 1 FROM sys_user u WHERE u.id=o.user_id AND u.status=1)
              AND (o.status_check_after IS NULL OR o.status_check_after <= #{now})
              AND (o.status_check_until IS NULL OR o.status_check_until <= #{now})
            ORDER BY COALESCE(o.status_check_after,o.create_time),o.id LIMIT #{limit}
            """)
    List<String> dueStatusChecks(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM sys_user WHERE id=#{userId} AND status=1")
    int activeStatusOwner(@Param("userId") Long userId);

    @Update("""
            UPDATE service_order SET status_check_token=#{token},status_check_until=#{until},
              status_check_attempt_at=#{now},status_check_after=#{next}
            WHERE id=#{id} AND (status_check_until IS NULL OR status_check_until <= #{now})
            """)
    int claimStatusCheck(@Param("id") String id, @Param("token") String token,
            @Param("now") LocalDateTime now, @Param("until") LocalDateTime until,
            @Param("next") LocalDateTime next);

    @Update("""
            UPDATE service_order SET status_checked_at=#{now},status_check_state='OK',
              status_check_after=#{next},status_check_failures=0,
              status_check_token=NULL,status_check_until=NULL
            WHERE id=#{id} AND status_check_token=#{token} AND status_check_until > #{now}
            """)
    int statusCheckSucceeded(@Param("id") String id, @Param("token") String token,
            @Param("now") LocalDateTime now,
            @Param("next") LocalDateTime next);

    @Update("""
            UPDATE service_order SET status_check_state='RETRY',status_check_failures=#{failures},
              status_check_after=#{next},status_check_token=NULL,status_check_until=NULL
            WHERE id=#{id} AND status_check_token=#{token} AND status_check_until > #{now}
            """)
    int statusCheckFailed(@Param("id") String id, @Param("token") String token,
            @Param("now") LocalDateTime now,
            @Param("next") LocalDateTime next, @Param("failures") int failures);

    @Update("""
            UPDATE service_order SET status_check_token=NULL,status_check_until=NULL,
              status_check_after=#{next} WHERE id=#{id} AND status_check_token=#{token}
            """)
    int releaseStatusCheck(@Param("id") String id, @Param("token") String token,
            @Param("next") LocalDateTime next);
}
