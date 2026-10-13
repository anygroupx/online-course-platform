package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CourseReservedTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 请求幂等命名空间是代理用户，跨钱包或类型重用请求键均冲突。 */
@Mapper
public interface CourseReservedTransferMapper extends BaseMapper<CourseReservedTransfer> {
    @Select("SELECT * FROM course_reserved_transfer WHERE user_id=#{userId} AND request_key=#{requestKey} LIMIT 1 FOR UPDATE")
    CourseReservedTransfer selectByRequestKey(@Param("userId") Long userId, @Param("requestKey") String requestKey);
}
