package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.AccountLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AccountLedgerMapper extends BaseMapper<AccountLedger> {

    @Select("""
            SELECT *
            FROM account_ledger
            WHERE user_id = #{userId}
              AND biz_type = #{bizType}
              AND biz_no = #{bizNo}
              AND direction = #{direction}
            LIMIT 1
            FOR UPDATE
            """)
    AccountLedger selectByBizKey(@Param("userId") Long userId,
                                 @Param("bizType") String bizType,
                                 @Param("bizNo") String bizNo,
                                 @Param("direction") int direction);
}
