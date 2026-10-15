package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CoursePrivateCostVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 人工核验且可追溯的真实单位成本版本。账户与协议锁由领域服务按全局顺序取得。 */
@Mapper
public interface CoursePrivateCostVersionMapper extends BaseMapper<CoursePrivateCostVersion> {
    @Select("SELECT * FROM course_private_cost_version WHERE id=#{id} FOR UPDATE")
    CoursePrivateCostVersion selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM course_private_cost_version WHERE platform_id=#{platformId} AND verified_at<=#{now} AND (valid_from IS NULL OR valid_from<=#{now}) AND (valid_until IS NULL OR valid_until>#{now}) ORDER BY verified_at DESC,id DESC LIMIT 1")
    CoursePrivateCostVersion selectLatestVerified(@Param("platformId") Long platformId, @Param("now") LocalDateTime now);
}
