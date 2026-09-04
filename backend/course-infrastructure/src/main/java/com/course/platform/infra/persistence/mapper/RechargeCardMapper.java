package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.RechargeCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 充值卡密Mapper接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
@Mapper
public interface RechargeCardMapper extends BaseMapper<RechargeCard> {

    @Select("SELECT * FROM recharge_card WHERE card_no = #{cardNo} LIMIT 1")
    RechargeCard selectByCardNo(@Param("cardNo") String cardNo);

    @Update("""
            UPDATE recharge_card
            SET status = #{usedStatus}, used_by = #{userId}, used_time = #{usedTime}, update_time = NOW()
            WHERE id = #{cardId} AND status = #{unusedStatus}
            """)
    int claimUnusedCard(@Param("cardId") Long cardId,
                        @Param("userId") Long userId,
                        @Param("usedTime") LocalDateTime usedTime,
                        @Param("unusedStatus") Integer unusedStatus,
                        @Param("usedStatus") Integer usedStatus);

    @Update("""
            UPDATE recharge_card
            SET status = #{disabledStatus}, update_time = NOW()
            WHERE id = #{cardId} AND status = #{unusedStatus}
            """)
    int disableIfUnused(@Param("cardId") Long cardId,
                        @Param("unusedStatus") Integer unusedStatus,
                        @Param("disabledStatus") Integer disabledStatus);
}
