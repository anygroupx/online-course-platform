package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CoursePrivateContractHistory;
import org.apache.ibatis.annotations.Mapper;

/** 协议审计快照仅通过受控服务追加与查询。 */
@Mapper
public interface CoursePrivateContractHistoryMapper extends BaseMapper<CoursePrivateContractHistory> {}
