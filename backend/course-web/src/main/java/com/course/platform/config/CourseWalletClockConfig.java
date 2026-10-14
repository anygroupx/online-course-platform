package com.course.platform.config;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 协议时间使用应用时区，测试可注入固定时钟。 */
@Configuration
public class CourseWalletClockConfig {
    // 明确业务时区，避免不同服务器默认时区改变权益边界。
    @Bean public Clock courseWalletClock(@Value("${course.private-operations.time-zone:Asia/Shanghai}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
