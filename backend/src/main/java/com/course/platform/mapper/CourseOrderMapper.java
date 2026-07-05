package com.course.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
     */
    @Update("UPDATE course_order SET order_status = #{orderStatus}, progress = #{progress}, " +
            "remarks = #{remarks}, course_start_time = #{courseStartTime}, course_end_time = #{courseEndTime}, " +
            "exam_start_time = #{examStartTime}, exam_end_time = #{examEndTime}, update_time = NOW() " +
            "WHERE student_account = #{studentAccount} AND student_password = #{studentPassword} " +
            "AND course_name = #{courseName} AND third_order_id = #{thirdOrderId} AND api_provider_id = #{apiProviderId}")
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
