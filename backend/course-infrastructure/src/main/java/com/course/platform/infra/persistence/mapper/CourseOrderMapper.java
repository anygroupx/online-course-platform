package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.common.constant.Constants;
import com.course.platform.domain.entity.CourseOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 课程订单Mapper接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Mapper
public interface CourseOrderMapper extends BaseMapper<CourseOrder> {
    /** 财务核验读取历史归档，普通订单查询仍使用逻辑删除边界。 */
    @org.apache.ibatis.annotations.Select("SELECT * FROM course_order WHERE id=#{id}")
    CourseOrder selectHistoricalById(@Param("id") Long id);
    @org.apache.ibatis.annotations.Select("SELECT * FROM course_order WHERE id=#{id} FOR UPDATE")
    CourseOrder selectHistoricalByIdForUpdate(@Param("id") Long id);

    /** 手动上游刷新使用原状态和原接口绑定CAS，不覆盖并发退款、取消或归档。 */
    @Update("""
      UPDATE course_order SET order_status=#{order.orderStatus},progress=#{order.progress},remarks=#{order.remarks},
        course_start_time=#{order.courseStartTime},course_end_time=#{order.courseEndTime},exam_start_time=#{order.examStartTime},exam_end_time=#{order.examEndTime},update_time=NOW()
      WHERE id=#{order.id} AND is_deleted=0 AND refunded_at IS NULL AND order_status NOT IN (3,4,6)
        AND (order_status=#{expectedStatus} OR order_status IS NULL AND #{expectedStatus} IS NULL)
        AND (api_provider_id=#{providerId} OR api_provider_id IS NULL AND #{providerId} IS NULL)
        AND (third_order_id=#{remoteId} OR third_order_id IS NULL AND #{remoteId} IS NULL)
      """)
    int updateUpstreamIfUnchanged(@Param("order") CourseOrder order,@Param("expectedStatus") Integer expectedStatus,@Param("providerId") Long providerId,@Param("remoteId") String remoteId);

    /** 上游确认只记录时间上界，真实时间由可信适配器提供；重开后不能沿用旧完成事实。 */
    @Update("""
      UPDATE course_order SET completion_confirmed_at=CASE WHEN #{replace}=1 OR completion_confirmed_at IS NULL THEN #{confirmedAt} ELSE completion_confirmed_at END,
        completion_confirmation_source='UPSTREAM_STATUS',
        fulfillment_source=CASE WHEN #{replace}=1 THEN CASE WHEN #{actualAt} IS NULL THEN NULL ELSE 'UPSTREAM_CONFIRMED' END WHEN fulfilled_at IS NULL AND #{actualAt} IS NOT NULL THEN 'UPSTREAM_CONFIRMED' ELSE fulfillment_source END,
        fulfilled_at=CASE WHEN #{replace}=1 THEN #{actualAt} WHEN fulfilled_at IS NULL THEN #{actualAt} ELSE fulfilled_at END
      WHERE id=#{id} AND private_term_id IS NOT NULL AND order_status=2 AND is_deleted=0 AND refunded_at IS NULL
        AND api_provider_id=#{providerId} AND third_order_id=#{remoteId}
        AND (#{replace}=1 OR completion_confirmed_at IS NULL OR fulfilled_at IS NULL AND #{actualAt} IS NOT NULL)
      """)
    int recordUpstreamCompletion(@Param("id") Long id,@Param("providerId") Long providerId,@Param("remoteId") String remoteId,@Param("confirmedAt") LocalDateTime confirmedAt,@Param("actualAt") LocalDateTime actualAt,@Param("replace") boolean replace);
    @Update("UPDATE course_order SET fulfilled_at=NULL,fulfillment_source=NULL,completion_confirmed_at=NULL,completion_confirmation_source=NULL WHERE id=#{id} AND private_term_id IS NOT NULL AND order_status=#{status} AND is_deleted=0 AND refunded_at IS NULL AND (fulfilled_at IS NOT NULL OR completion_confirmed_at IS NOT NULL)")
    int invalidateCompletion(@Param("id") Long id,@Param("status") Integer status);
    @Update("UPDATE course_order SET fulfilled_at=#{actualAt},fulfillment_source='ADMIN_VERIFIED' WHERE id=#{id} AND private_term_id IS NOT NULL AND order_status=2 AND refunded_at IS NULL")
    int recordVerifiedCompletion(@Param("id") Long id,@Param("actualAt") LocalDateTime actualAt);
    /** 补单次数作为CAS版本，取消或退款已成功时禁止重新履约。 */
    @Update("UPDATE course_order SET retry_count=COALESCE(retry_count,0)+1,order_status=#{processing},dock_status=#{dockPending},update_time=NOW() WHERE id=#{id} AND is_deleted=0 AND order_status=#{previousStatus} AND order_status<>#{cancelled} AND refunded_at IS NULL AND NOT (order_status=#{processing} AND dock_status=#{dockPending}) AND COALESCE(retry_count,0)=#{previousRetry} AND COALESCE(retry_count,0)<5")
    int claimRetry(@Param("id") Long id,@Param("previousStatus") int previousStatus,@Param("previousRetry") int previousRetry,@Param("processing") int processing,@Param("cancelled") int cancelled,@Param("dockPending") int dockPending);

    @Update("UPDATE course_order SET dock_status=#{dockStatus},third_order_id=COALESCE(#{remoteId},third_order_id),remarks=#{remark},update_time=NOW() WHERE id=#{id} AND is_deleted=0 AND order_status=#{processing} AND retry_count=#{retryCount} AND refunded_at IS NULL")
    int finishRetry(@Param("id") Long id,@Param("processing") int processing,@Param("retryCount") int retryCount,@Param("dockStatus") int dockStatus,@Param("remoteId") String remoteId,@Param("remark") String remark);

    /** 退款事务提交后才会留下标记，与余额和原流水共同回滚。 */
    @Update("UPDATE course_order SET refunded_at=COALESCE(refunded_at,NOW()) WHERE id=#{id}")
    int markRefunded(@Param("id") Long id);

    /** 提交前原子占用订单，与取消订单竞争同一行；不跨HTTP持锁。 */
    @Update("UPDATE course_order SET order_status=#{processing},update_time=NOW() WHERE id=#{id} AND is_deleted=0 AND order_status=#{pending} AND dock_status=#{dockPending}")
    int claimSubmission(@Param("id") Long id,@Param("pending") int pending,@Param("processing") int processing,@Param("dockPending") int dockPending);

    /** 不确定响应只更新对接标记，保留进行中状态，必须先核查上游。 */
    @Update("UPDATE course_order SET dock_status=#{resultDock},third_order_id=#{remoteId},remarks=#{remark},update_time=NOW() WHERE id=#{id} AND is_deleted=0 AND order_status=#{processing} AND dock_status=#{dockPending}")
    int finishSubmission(@Param("id") Long id,@Param("processing") int processing,@Param("dockPending") int dockPending,@Param("resultDock") int resultDock,@Param("remoteId") String remoteId,@Param("remark") String remark);

    /** 请求键保留归档记录，网络重试绝不能重新扣款。 */
    @org.apache.ibatis.annotations.Select("SELECT * FROM course_order WHERE user_id=#{userId} AND client_request_id=#{requestKey} LIMIT 1")
    CourseOrder selectByRequestKey(@Param("userId") Long userId, @Param("requestKey") String requestKey);

    /** 取消/归档在账户锁后读取最新订单，避免陈旧状态触发退款。 */
    @org.apache.ibatis.annotations.Select("SELECT * FROM course_order WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    CourseOrder selectByIdForUpdate(@Param("id") Long id);


    /**
     * 原子更新进度：仅当订单仍处于期望状态时才写入，避免用陈旧对象整行覆盖，
     * 把并发完成的订单（如倒计时自动完成）打回旧状态。
     *
     * @param expectedStatus 期望的当前状态（CAS 条件）
     * @return 受影响行数，0 表示状态已被其他流程改变，本次写入应放弃
     */
    @Update("UPDATE course_order SET progress = #{progress}, update_time = NOW() " +
            "WHERE id = #{orderId} AND order_status = #{expectedStatus}")
    int updateProgressIfStatus(@Param("orderId") Long orderId,
                               @Param("progress") String progress,
                               @Param("expectedStatus") Integer expectedStatus);

    /**
     * 原子更新备注：仅当订单仍处于期望状态时才写入，防止覆盖并发变更。
     */
    @Update("UPDATE course_order SET remarks = #{remarks}, update_time = NOW() " +
            "WHERE id = #{orderId} AND order_status = #{expectedStatus}")
    int updateRemarksIfStatus(@Param("orderId") Long orderId,
                              @Param("remarks") String remarks,
                              @Param("expectedStatus") Integer expectedStatus);

    /**
     * 原子更新备注与状态：状态从 expectedStatus 迁移到 newStatus 时才写入。
     */
    @Update("UPDATE course_order SET remarks = #{remarks}, order_status = #{newStatus}, update_time = NOW() " +
            "WHERE id = #{orderId} AND order_status = #{expectedStatus}")
    int updateRemarksAndStatusIfStatus(@Param("orderId") Long orderId,
                                       @Param("remarks") String remarks,
                                       @Param("newStatus") Integer newStatus,
                                       @Param("expectedStatus") Integer expectedStatus);

    /**
     * 原子更新进度、备注与状态：状态从 expectedStatus 迁移到 newStatus 时才写入。
     * 用于刷课完成时把状态推进到待考试，同时保证不会覆盖并发完成的订单。
     */
    @Update("UPDATE course_order SET progress = #{progress}, remarks = #{remarks}, " +
            "order_status = #{newStatus}, update_time = NOW() " +
            "WHERE id = #{orderId} AND order_status = #{expectedStatus}")
    int updateProgressRemarksAndStatusIfStatus(@Param("orderId") Long orderId,
                                               @Param("progress") String progress,
                                               @Param("remarks") String remarks,
                                               @Param("newStatus") Integer newStatus,
                                               @Param("expectedStatus") Integer expectedStatus);

    /**
     * 原子更新订单完成态：仅当订单仍处于期望状态时才写入，并返回受影响行数。
     * 用于倒计时自动完成，避免并发流程（如刷课任务）已改变状态后重复完成。
     */
    @Update("UPDATE course_order SET order_status = #{targetStatus}, dock_status = #{dockStatus}, " +
            "countdown_end_time = #{countdownEndTime}, progress = #{progress}, " +
            "finished_chapters = #{finishedChapters}, " +
            "fulfilled_at=CASE WHEN #{targetStatus}=2 AND is_self_operated=1 AND private_term_id IS NOT NULL THEN #{countdownEndTime} ELSE fulfilled_at END, " +
            "fulfillment_source=CASE WHEN #{targetStatus}=2 AND is_self_operated=1 AND private_term_id IS NOT NULL THEN 'SELF_OPERATED_COMPLETED' ELSE fulfillment_source END, update_time = NOW() " +
            "WHERE id = #{orderId} AND order_status = #{expectedStatus} AND is_deleted=0 AND refunded_at IS NULL")
    int completeIfStatus(@Param("orderId") Long orderId,
                         @Param("targetStatus") Integer targetStatus,
                         @Param("dockStatus") Integer dockStatus,
                         @Param("countdownEndTime") LocalDateTime countdownEndTime,
                         @Param("progress") String progress,
                         @Param("finishedChapters") Integer finishedChapters,
                         @Param("expectedStatus") Integer expectedStatus);

    /**
     * 更新订单进度（基础版，3字段匹配）
     * 使用学生账号、密码和API提供商ID匹配订单
     */
    @Update("UPDATE course_order SET order_status = #{orderStatus}, progress = #{progress}, " +
            "remark = #{remark}, course_start_time = #{courseStartTime}, course_end_time = #{courseEndTime}, " +
            "exam_start_time = #{examStartTime}, exam_end_time = #{examEndTime}, update_time = NOW() " +
            "WHERE student_account = #{studentAccount} AND student_password = #{studentPassword} " +
            "AND dock_api_id = #{apiProviderId}")
    int updateOrderProgress(@Param("studentAccount") String studentAccount,
                            @Param("studentPassword") String studentPassword,
                            @Param("apiProviderId") Long apiProviderId,
                            @Param("orderStatus") Integer orderStatus,
                            @Param("progress") String progress,
                            @Param("remark") String remark,
                            @Param("courseStartTime") LocalDateTime courseStartTime,
                            @Param("courseEndTime") LocalDateTime courseEndTime,
                            @Param("examStartTime") LocalDateTime examStartTime,
                            @Param("examEndTime") LocalDateTime examEndTime);

    /**
     * 使用完整匹配更新订单进度（参考 benztb.php 的5字段匹配）
     * 匹配字段：user + pass + kcname + third_order_id + apiProviderId
     * benztb.php 第56行的精确匹配逻辑
     *
     * <p>终态订单（已完成/已取消/失败/考试完成/等待退款）被排除：命中它们只会无条件刷新
     * {@code update_time}，使已完成订单一直显示为正在同步。没有真实进度变化的重复同步
     * 应当零行命中。</p>
     */
    @Update("UPDATE course_order SET order_status = #{orderStatus}, progress = #{progress}, " +
            "remarks = #{remarks}, course_start_time = #{courseStartTime}, course_end_time = #{courseEndTime}, " +
            "exam_start_time = #{examStartTime}, exam_end_time = #{examEndTime}, update_time = NOW() " +
            "WHERE student_account = #{studentAccount} AND student_password = #{studentPassword} " +
            "AND course_name = #{courseName} AND third_order_id = #{thirdOrderId} AND api_provider_id = #{apiProviderId} " +
            "AND is_deleted=0 AND refunded_at IS NULL AND order_status NOT IN ("
                    + Constants.TERMINAL_ORDER_STATUSES_CSV + ")")
    int updateOrderProgressByFullMatch(@Param("studentAccount") String studentAccount,
                                        @Param("studentPassword") String studentPassword,
                                        @Param("courseName") String courseName,
                                        @Param("thirdOrderId") String thirdOrderId,
                                        @Param("apiProviderId") Long apiProviderId,
                                        @Param("orderStatus") Integer orderStatus,
                                        @Param("progress") String progress,
                                        @Param("remarks") String remarks,
                                        @Param("courseStartTime") LocalDateTime courseStartTime,
                                        @Param("courseEndTime") LocalDateTime courseEndTime,
                                        @Param("examStartTime") LocalDateTime examStartTime,
                                        @Param("examEndTime") LocalDateTime examEndTime);
}
