package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CoursePrivateTermSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 不可覆盖的逐次结算事实及幂等指纹。账户与协议锁由领域服务按全局顺序取得。 */
@Mapper
public interface CoursePrivateTermSettlementMapper extends BaseMapper<CoursePrivateTermSettlement> {
    @Select("SELECT * FROM course_private_term_settlement WHERE id=#{id} FOR UPDATE")
    CoursePrivateTermSettlement selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM course_private_term_settlement WHERE term_id=#{termId} AND operation_fingerprint=#{fingerprint}")
    CoursePrivateTermSettlement selectByFingerprint(@Param("termId") Long termId, @Param("fingerprint") String fingerprint);
    @Select("SELECT * FROM course_private_term_settlement WHERE term_id=#{termId} ORDER BY revision_no DESC LIMIT 1")
    CoursePrivateTermSettlement selectLatestByTerm(@Param("termId") Long termId);
}
