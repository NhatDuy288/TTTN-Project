package com.tttn.qlnvl.shared.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class BusinessTimeConfig {
    @Bean
    Clock businessClock(@Value("${app.business-zone:Asia/Ho_Chi_Minh}") String businessZone) {
        return Clock.system(ZoneId.of(businessZone));
    }
}
