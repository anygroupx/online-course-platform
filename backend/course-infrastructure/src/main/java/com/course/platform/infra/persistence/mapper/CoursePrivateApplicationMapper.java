package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CoursePrivateApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 公开套餐开通续期升级和重新签约申请。账户与协议锁由领域服务按全局顺序取得。 */
@Mapper
public interface CoursePrivateApplicationMapper extends BaseMapper<CoursePrivateApplication> {
    @Select("SELECT * FROM course_private_application WHERE id=#{id} FOR UPDATE")
    CoursePrivateApplication selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM course_private_application WHERE user_id=#{userId} AND request_key=#{requestKey}")
    CoursePrivateApplication selectByRequestKey(@Param("userId") Long userId, @Param("requestKey") String requestKey);
    @Update("UPDATE course_private_application SET status=#{status},reviewed_by=#{operatorId},reviewed_at=#{now},review_reason=#{reason},contract_id=#{contractId},version=version+1 WHERE id=#{id} AND status='PENDING' AND version=#{expectedVersion}")
    int review(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion, @Param("status") String status, @Param("operatorId") Long operatorId, @Param("reason") String reason, @Param("contractId") Long contractId, @Param("now") LocalDateTime now);
}
