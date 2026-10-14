package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CourseWalletReleaseRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 申请不冻结本金，财务执行后才关联成功转回。账户与协议锁由领域服务按全局顺序取得。 */
@Mapper
public interface CourseWalletReleaseRequestMapper extends BaseMapper<CourseWalletReleaseRequest> {
    @Select("SELECT * FROM course_wallet_release_request WHERE id=#{id} FOR UPDATE")
    CourseWalletReleaseRequest selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM course_wallet_release_request WHERE user_id=#{userId} AND request_key=#{requestKey}")
    CourseWalletReleaseRequest selectByRequestKey(@Param("userId") Long userId, @Param("requestKey") String requestKey);
    @Update("UPDATE course_wallet_release_request SET status='EXECUTED',reviewed_by=#{operatorId},reviewed_at=#{now},review_reason=#{reason},executed_transfer_id=#{transferId},version=version+1,updated_at=#{now} WHERE id=#{id} AND status IN ('PENDING','APPROVED') AND version=#{expectedVersion}")
    int markExecuted(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion, @Param("operatorId") Long operatorId, @Param("reason") String reason, @Param("transferId") Long transferId, @Param("now") LocalDateTime now);

    /** 拒绝或撤销不涉及本金；状态CAS防止覆盖已经执行的财务决定。 */
    @Update("UPDATE course_wallet_release_request SET status=#{status},reviewed_by=#{operatorId},reviewed_at=#{now},review_reason=#{reason},version=version+1,updated_at=#{now} WHERE id=#{id} AND status='PENDING' AND version=#{expectedVersion}")
    int decideWithoutTransfer(@Param("id") Long id,@Param("expectedVersion") Integer expectedVersion,@Param("status") String status,@Param("operatorId") Long operatorId,@Param("reason") String reason,@Param("now") LocalDateTime now);
}
