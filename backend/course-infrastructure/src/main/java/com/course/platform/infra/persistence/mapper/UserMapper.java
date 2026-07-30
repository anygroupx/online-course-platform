package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 原子扣减余额（余额不足时返回 0）
     */
    @Update("""
            UPDATE sys_user
            SET balance = balance - #{amount},
                update_time = NOW()
            WHERE id = #{userId}
              AND balance >= #{amount}
            """)
    int decreaseBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 原子增加余额
     */
    @Update("""
            UPDATE sys_user
            SET balance = balance + #{amount},
                total_recharge = CASE WHEN #{countRecharge} = 1 THEN total_recharge + #{amount} ELSE total_recharge END,
                update_time = NOW()
            WHERE id = #{userId}
            """)
    int increaseBalance(@Param("userId") Long userId,
                        @Param("amount") BigDecimal amount,
                        @Param("countRecharge") int countRecharge);
}
