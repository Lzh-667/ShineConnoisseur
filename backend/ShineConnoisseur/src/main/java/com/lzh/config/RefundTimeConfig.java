package com.lzh.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class RefundTimeConfig {
    @Bean
    public Clock refundClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
