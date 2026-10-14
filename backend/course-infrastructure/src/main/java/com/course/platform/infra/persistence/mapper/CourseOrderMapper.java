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
            "finished_chapters = #{finishedChapters}, update_time = NOW() " +
            "WHERE id = #{orderId} AND order_status = #{expectedStatus}")
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
            "AND order_status NOT IN (" + Constants.TERMINAL_ORDER_STATUSES_CSV + ")")
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
