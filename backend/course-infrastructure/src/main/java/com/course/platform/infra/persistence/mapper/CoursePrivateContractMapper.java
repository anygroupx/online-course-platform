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
    /** 有界运营查询只接收已验证的过滤值；本人拥有者由服务层固定，不使用客户端userId。 */
    @Select("SELECT * FROM course_private_contract WHERE (#{userId} IS NULL OR user_id=#{userId}) AND (#{platformId} IS NULL OR platform_id=#{platformId}) AND (#{status} IS NULL OR status=#{status}) ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    java.util.List<CoursePrivateContract> selectOperationsPage(@Param("userId") Long userId,@Param("platformId") Long platformId,@Param("status") String status,@Param("limit") int limit,@Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM course_private_contract WHERE (#{userId} IS NULL OR user_id=#{userId}) AND (#{platformId} IS NULL OR platform_id=#{platformId}) AND (#{status} IS NULL OR status=#{status})")
    Long countOperations(@Param("userId") Long userId,@Param("platformId") Long platformId,@Param("status") String status);
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

    /** 运营周期同步显式时间和当前权益指针，不覆盖资金拥有者或商品绑定。 */
    @Update("""
        UPDATE course_private_contract SET operations_mode=#{contract.operationsMode},current_term_id=#{contract.currentTermId},
          relation_generation=#{contract.relationGeneration},fixed_price=#{contract.fixedPrice},min_initial_deposit=#{contract.minInitialDeposit},
          status=#{contract.status},activated_at=#{contract.activatedAt},valid_from=#{contract.validFrom},valid_until=#{contract.validUntil},
          updated_by=#{contract.updatedBy},version=version+1,update_time=#{now}
        WHERE id=#{contract.id} AND version=#{expectedVersion}
        """)
    int updateOperations(@Param("contract") CoursePrivateContract contract,@Param("expectedVersion") Integer expectedVersion,@Param("now") java.time.LocalDateTime now);
    /** 兼容投影不递增旧配置版本，保留原接口成功响应和审计时间。 */
    @Update("UPDATE course_private_contract SET current_term_id=#{termId},operations_mode='LEGACY_PERMANENT',update_time=update_time WHERE id=#{id} AND version=#{expectedVersion} AND (operations_mode IS NULL OR operations_mode='LEGACY_PERMANENT')")
    int syncLegacyPointer(@Param("id") Long id,@Param("expectedVersion") Integer expectedVersion,@Param("termId") Long termId);

}
