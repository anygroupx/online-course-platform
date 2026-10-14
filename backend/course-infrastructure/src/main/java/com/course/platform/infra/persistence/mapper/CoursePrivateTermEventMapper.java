package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CoursePrivateTermEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 只新增的权益操作与退款资格复核事件。账户与协议锁由领域服务按全局顺序取得。 */
@Mapper
public interface CoursePrivateTermEventMapper extends BaseMapper<CoursePrivateTermEvent> {
    @Select("SELECT * FROM course_private_term_event WHERE id=#{id} FOR UPDATE")
    CoursePrivateTermEvent selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM course_private_term_event WHERE biz_no=#{bizNo}")
    CoursePrivateTermEvent selectByBizNo(@Param("bizNo") String bizNo);
    @Select("SELECT * FROM course_private_term_event WHERE term_id=#{termId} ORDER BY id DESC LIMIT #{limit}")
    List<CoursePrivateTermEvent> selectRecentByTerm(@Param("termId") Long termId,@Param("limit") int limit);
}
