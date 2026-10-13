package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CoursePrivateContract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 协议查询及显式版本更新，不依赖全局乐观锁插件。 */
@Mapper
public interface CoursePrivateContractMapper extends BaseMapper<CoursePrivateContract> {
    @Select("SELECT * FROM course_private_contract WHERE user_id=#{userId} AND platform_id=#{platformId}")
    CoursePrivateContract selectByBinding(@Param("userId") Long userId, @Param("platformId") Long platformId);

    @Select("SELECT * FROM course_private_contract WHERE id=#{id} FOR UPDATE")
    CoursePrivateContract selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM course_private_contract WHERE platform_id=#{platformId}")
    int countByPlatform(@Param("platformId") Long platformId);

    // 所有可空时间显式写入，保证管理员可以清空有效期；版本冲突返回0。
    @Update("""
            UPDATE course_private_contract SET fixed_price=#{contract.fixedPrice},
              min_initial_deposit=#{contract.minInitialDeposit},status=#{contract.status},
              activated_at=#{contract.activatedAt},valid_from=#{contract.validFrom},valid_until=#{contract.validUntil},
              updated_by=#{contract.updatedBy},version=version+1,update_time=NOW()
            WHERE id=#{contract.id} AND version=#{expectedVersion}
            """)
    int updateConfig(@Param("contract") CoursePrivateContract contract, @Param("expectedVersion") Integer expectedVersion);
}
