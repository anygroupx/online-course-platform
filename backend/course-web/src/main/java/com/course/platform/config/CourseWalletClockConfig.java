package com.course.platform.config;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 协议时间使用应用时区，测试可注入固定时钟。 */
@Configuration
public class CourseWalletClockConfig {
    @Bean public Clock courseWalletClock() { return Clock.systemDefaultZone(); }
}
