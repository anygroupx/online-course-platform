package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.CourseReservedWallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/** 资金操作先按统一顺序锁普通账户，再锁专户，原子更新防止超扣与溢出。 */
@Mapper
public interface CourseReservedWalletMapper extends BaseMapper<CourseReservedWallet> {
    @Select("SELECT * FROM course_reserved_wallet WHERE user_id=#{userId} AND platform_id=#{platformId}")
    CourseReservedWallet selectByBinding(@Param("userId") Long userId, @Param("platformId") Long platformId);

    @Select("SELECT * FROM course_reserved_wallet WHERE id=#{id} FOR UPDATE")
    CourseReservedWallet selectByIdForUpdate(@Param("id") Long id);

    // 条件写入同时限制精度和上限，调用方必须验证返回值并与流水处于同一事务。
    @Update("""
            UPDATE course_reserved_wallet SET balance=balance+#{amount},version=version+1,update_time=NOW()
            WHERE id=#{id} AND #{amount}>0 AND #{amount}=ROUND(CAST(#{amount} AS DECIMAL(38,18)),2)
              AND #{amount}<=9999999999.99 AND balance<=9999999999.99-#{amount}
            """)
    int increaseBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Update("""
            UPDATE course_reserved_wallet SET balance=balance-#{amount},version=version+1,update_time=NOW()
            WHERE id=#{id} AND #{amount}>0 AND #{amount}=ROUND(CAST(#{amount} AS DECIMAL(38,18)),2)
              AND #{amount}<=9999999999.99 AND balance>=#{amount}
            """)
    int decreaseBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
