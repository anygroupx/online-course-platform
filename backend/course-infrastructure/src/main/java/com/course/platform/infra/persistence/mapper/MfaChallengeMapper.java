package com.course.platform.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.course.platform.domain.entity.MfaChallenge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface MfaChallengeMapper extends BaseMapper<MfaChallenge> {
    @Update("""
            UPDATE mfa_challenge SET consumed = 1, update_time = NOW()
            WHERE challenge_id = #{challengeId} AND consumed = 0 AND expire_time > #{now}
            """)
    int consumeIfActive(@Param("challengeId") String challengeId, @Param("now") LocalDateTime now);
}
